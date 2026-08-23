#!/usr/bin/env bash

set -euo pipefail

# One-time, idempotent technician migration. It authenticates through the
# ordinary Firebase client API as the configured ADMIN, so Firestore Security
# Rules remain the authority. No Admin SDK or service credential is used.

readonly EXPECTED_PROJECT_ID="techfix-mobile-app"
readonly EXPECTED_SOURCE_COUNT=6
readonly PROPERTIES_FILE="local.properties"
readonly GOOGLE_SERVICES_FILE="app/google-services.json"

for required_command in curl jq sed tail mktemp find; do
  command -v "$required_command" >/dev/null || {
    echo "Missing required command: $required_command" >&2
    exit 1
  }
done

test -f "$PROPERTIES_FILE" || { echo "local.properties is missing" >&2; exit 1; }
test -f "$GOOGLE_SERVICES_FILE" || { echo "app/google-services.json is missing" >&2; exit 1; }

read_property() {
  sed -n "s/^$1=//p" "$PROPERTIES_FILE" | tail -n 1
}

supabase_url=$(read_property "SUPABASE_URL")
supabase_key=$(read_property "SUPABASE_ANON_KEY")
admin_email=$(read_property "SEED_ADMIN_EMAIL")
admin_password=$(read_property "SEED_ADMIN_PASSWORD")
firebase_api_key=$(jq -r '.client[0].api_key[0].current_key // empty' "$GOOGLE_SERVICES_FILE")
firebase_project_id=$(jq -r '.project_info.project_id // empty' "$GOOGLE_SERVICES_FILE")

test -n "$supabase_url" && test -n "$supabase_key" || {
  echo "Supabase migration configuration is missing" >&2
  exit 1
}
test -n "$admin_email" && test -n "$admin_password" || {
  echo "SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD are missing" >&2
  exit 1
}
test -n "$firebase_api_key" || { echo "Firebase API key is missing" >&2; exit 1; }
test "$firebase_project_id" = "$EXPECTED_PROJECT_ID" || {
  echo "Refusing to migrate into unexpected Firebase project: $firebase_project_id" >&2
  exit 1
}

migration_tmp=$(mktemp -d "${TMPDIR:-/tmp}/techfix-technician-migration.XXXXXX")
trap 'find "$migration_tmp" -depth -delete' EXIT

auth_payload=$(jq -nc \
  --arg email "$admin_email" \
  --arg password "$admin_password" \
  '{email:$email,password:$password,returnSecureToken:true}')
auth_response=$(curl --silent --show-error --fail \
  --connect-timeout 10 --max-time 30 \
  -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$firebase_api_key" \
  -H 'Content-Type: application/json' \
  --data "$auth_payload") || {
    echo "Firebase ADMIN authentication failed" >&2
    exit 1
  }
firebase_id_token=$(printf '%s' "$auth_response" | jq -r '.idToken // empty')
test -n "$firebase_id_token" || {
  echo "Firebase authentication returned no ID token" >&2
  exit 1
}

source_rows=$(curl --silent --show-error --fail \
  --connect-timeout 10 --max-time 30 \
  "$supabase_url/rest/v1/technicians?select=id,name,branch_id,category_skills,available,created_at&order=created_at.asc" \
  -H "apikey: $supabase_key" \
  -H "Authorization: Bearer $supabase_key") || {
    echo "Unable to read Supabase technicians" >&2
    exit 1
  }

source_count=$(printf '%s' "$source_rows" | jq 'length')
test "$source_count" -eq "$EXPECTED_SOURCE_COUNT" || {
  echo "Expected $EXPECTED_SOURCE_COUNT Supabase technicians, found $source_count; nothing was written" >&2
  exit 1
}

printf '%s' "$source_rows" | jq -e '
  all(.[ ];
    (.id | type == "string" and test("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) and
    (.name | type == "string" and length > 0) and
    (.branch_id == "colombo" or .branch_id == "galle") and
    (.category_skills | type == "array" and length > 0 and all(.[]; IN("MOBILE", "LAPTOP", "DESKTOP", "TABLET"))) and
    (.available | type == "boolean") and
    (.created_at | type == "string" and length > 0)
  )
' >/dev/null || {
  echo "Supabase technician data failed validation; nothing was written" >&2
  exit 1
}

readonly firestore_collection_url="https://firestore.googleapis.com/v1/projects/$EXPECTED_PROJECT_ID/databases/(default)/documents/technicians"

fetch_document() {
  local technician_id="$1"
  firestore_status=$(curl --silent --show-error \
    --connect-timeout 10 --max-time 30 \
    -o "$migration_tmp/firestore-response.json" \
    -w '%{http_code}' \
    "$firestore_collection_url/$technician_id" \
    -H "Authorization: Bearer $firebase_id_token")
}

expected_record() {
  printf '%s' "$1" | jq -c '{
    id,
    name,
    branchId: .branch_id,
    categorySkills: (.category_skills | sort),
    available
  }'
}

persisted_record() {
  jq -c '{
    id: .fields.id.stringValue,
    name: .fields.name.stringValue,
    branchId: .fields.branchId.stringValue,
    categorySkills: ([.fields.categorySkills.arrayValue.values[]?.stringValue] | sort),
    available: .fields.available.booleanValue
  }' "$1"
}

document_matches() {
  local response_file="$1"
  local expected="$2"
  test "$(persisted_record "$response_file")" = "$expected"
}

write_outcome() {
  printf '%s' "$2" > "$migration_tmp/outcome.$1"
}

migrated=0
skipped=0
conflicted=0
failed=0

while IFS= read -r technician; do
  technician_id=$(printf '%s' "$technician" | jq -r '.id')
  expected=$(expected_record "$technician")
  fetch_document "$technician_id"

  if test "$firestore_status" = "200"; then
    if document_matches "$migration_tmp/firestore-response.json" "$expected"; then
      skipped=$((skipped + 1))
      write_outcome "$technician_id" "skipped"
    else
      conflicted=$((conflicted + 1))
      write_outcome "$technician_id" "conflicted"
      echo "Conflict: $technician_id already exists with different technician data" >&2
    fi
    continue
  fi

  if test "$firestore_status" != "404"; then
    failed=$((failed + 1))
    write_outcome "$technician_id" "failed"
    echo "Failed: unable to inspect $technician_id (HTTP $firestore_status)" >&2
    continue
  fi

  technician_name=$(printf '%s' "$technician" | jq -r '.name')
  branch_id=$(printf '%s' "$technician" | jq -r '.branch_id')
  available=$(printf '%s' "$technician" | jq '.available')
  skills=$(printf '%s' "$technician" | jq -c '.category_skills')
  created_at=$(printf '%s' "$technician" | jq -r '.created_at')
  firestore_payload=$(jq -nc \
    --arg id "$technician_id" \
    --arg name "$technician_name" \
    --arg branchId "$branch_id" \
    --argjson skills "$skills" \
    --argjson available "$available" \
    --arg createdAt "$created_at" \
    '{fields:{
      id:{stringValue:$id},
      name:{stringValue:$name},
      branchId:{stringValue:$branchId},
      categorySkills:{arrayValue:{values:($skills | map({stringValue:.}))}},
      available:{booleanValue:$available},
      createdAt:{timestampValue:$createdAt},
      updatedAt:{timestampValue:$createdAt}
    }}')

  create_status=$(curl --silent --show-error \
    --connect-timeout 10 --max-time 30 \
    -o "$migration_tmp/create-response.json" \
    -w '%{http_code}' \
    -X POST "$firestore_collection_url?documentId=$technician_id" \
    -H "Authorization: Bearer $firebase_id_token" \
    -H 'Content-Type: application/json' \
    --data "$firestore_payload")

  if test "$create_status" = "200" && \
      document_matches "$migration_tmp/create-response.json" "$expected"; then
    migrated=$((migrated + 1))
    write_outcome "$technician_id" "migrated"
  elif test "$create_status" = "409"; then
    fetch_document "$technician_id"
    if test "$firestore_status" = "200" && \
        document_matches "$migration_tmp/firestore-response.json" "$expected"; then
      skipped=$((skipped + 1))
      write_outcome "$technician_id" "skipped"
    else
      conflicted=$((conflicted + 1))
      write_outcome "$technician_id" "conflicted"
      echo "Conflict: $technician_id was created concurrently with different data" >&2
    fi
  else
    failed=$((failed + 1))
    write_outcome "$technician_id" "failed"
    echo "Failed: unable to create $technician_id (HTTP $create_status)" >&2
  fi
done < <(printf '%s' "$source_rows" | jq -c '.[]')

# Re-read every source row independently. A migration is complete only when
# all six live Firestore documents exactly match the Supabase source fields.
verified=0
while IFS= read -r technician; do
  technician_id=$(printf '%s' "$technician" | jq -r '.id')
  expected=$(expected_record "$technician")
  outcome=$(cat "$migration_tmp/outcome.$technician_id")
  fetch_document "$technician_id"

  if test "$firestore_status" = "200" && \
      document_matches "$migration_tmp/firestore-response.json" "$expected"; then
    verified=$((verified + 1))
    if test "$outcome" = "conflicted"; then
      conflicted=$((conflicted - 1))
      skipped=$((skipped + 1))
    elif test "$outcome" = "failed"; then
      failed=$((failed - 1))
      skipped=$((skipped + 1))
    fi
    continue
  fi

  if test "$outcome" = "migrated"; then
    migrated=$((migrated - 1))
    failed=$((failed + 1))
  elif test "$outcome" = "skipped"; then
    skipped=$((skipped - 1))
    failed=$((failed + 1))
  fi
  echo "Verification failed: $technician_id does not match Supabase" >&2
done < <(printf '%s' "$source_rows" | jq -c '.[]')

echo "MIGRATION_RESULT migrated=$migrated skipped=$skipped conflicted=$conflicted failed=$failed verified=$verified source=$source_count"

test "$verified" -eq "$source_count" && \
  test "$conflicted" -eq 0 && \
  test "$failed" -eq 0
