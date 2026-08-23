#!/usr/bin/env bash
set -euo pipefail

# Safe, one-time Fixora technician replacement.
# Default mode is read-only. Pass --apply and provide FIXORA_TECHNICIAN_PASSWORD
# in the environment to perform the approved migration. The password and all
# OAuth/ID tokens stay in memory and are never printed.

mode="${1:---dry-run}"
if [[ "$mode" != "--dry-run" && "$mode" != "--apply" ]]; then
  echo "Usage: $0 [--dry-run|--apply]" >&2
  exit 2
fi

project_id="techfix-mobile-app"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
firebase_config="/Users/kumudithatharinda/.config/configstore/firebase-tools.json"
firestore_base="https://firestore.googleapis.com/v1/projects/$project_id/databases/(default)/documents"
identity_base="https://identitytoolkit.googleapis.com/v1"
api_key="$(jq -r '.client[] | select(.client_info.android_client_info.package_name == "com.techfix.app") | .api_key[0].current_key' "$root_dir/app/google-services.json")"

command -v firebase >/dev/null
command -v curl >/dev/null
command -v jq >/dev/null
[[ "$api_key" != "null" && -n "$api_key" ]] || { echo "Firebase API key is unavailable" >&2; exit 1; }
[[ -f "$firebase_config" ]] || { echo "Firebase CLI authentication is unavailable" >&2; exit 1; }

# Refresh the existing Firebase CLI OAuth session without displaying it.
firebase projects:list --json >/dev/null
oauth_token="$(jq -r '.tokens.access_token' "$firebase_config")"
[[ -n "$oauth_token" && "$oauth_token" != "null" ]] || { echo "Firebase CLI OAuth token is unavailable" >&2; exit 1; }

auth_json="$(curl -fsS "$identity_base/projects/$project_id/accounts:batchGet?maxResults=1000" -H "Authorization: Bearer $oauth_token")"
technicians_json="$(curl -fsS "$firestore_base/technicians?pageSize=1000" -H "Authorization: Bearer $oauth_token")"
users_json="$(curl -fsS "$firestore_base/users?pageSize=1000" -H "Authorization: Bearer $oauth_token")"
repairs_json="$(curl -fsS "$firestore_base/repairRequests?pageSize=1000" -H "Authorization: Bearer $oauth_token")"

old_ids=(
  "44b87a2d-417f-4947-a8c9-ceeacafeaca1"
  "a369866b-1bec-4ce2-b6a0-ed0e4b74dcd8"
  "f3181e79-2aa4-40f2-be4e-7fe0159bd8e9"
  "ebacb5ef-2c26-4cd8-9a5d-5891d5bef933"
  "f492bb78-edbb-43ac-9ba3-1f4138b04bb5"
  "shTWtO9M7GeArfiWmTVq"
)

old_auth_uids=(
  "odheuUxWzyNm5j7of6um2BxmpaF2"
  "oqW8d6XUu6XKfkHZJqUnwhVHuXO2"
  "rEBFXakQzaY0hoORrr4L1cSjoR73"
  "tKgzgMkhwaPZfulB6HonGuCElVp2"
)

old_auth_emails=(
  "nuwan-tech@fixora.com"
  "kasun-tech@fixora.com"
  "tharinda-tech@fixora.com"
  "ishara-tech@fixora.com"
)

# name|email|branch|comma-separated skills
new_roster=(
  "Kasun|kasuntech@fixora.com|colombo|MOBILE,TABLET,LAPTOP"
  "Kavishka|kavishkaka@fixora.com|colombo|MOBILE,LAPTOP,DESKTOP"
  "Ravidu|ravidutech@fixora.com|colombo|TABLET,DESKTOP"
  "Tharusha|tharushatech@fixora.com|galle|MOBILE,TABLET,LAPTOP"
  "Rivini|rivinitech@fixora.com|galle|MOBILE,LAPTOP,DESKTOP"
  "Nethmi|nethmitech@fixora.com|galle|TABLET,DESKTOP"
)

document_exists() {
  local collection_json="$1" document_id="$2"
  jq -e --arg id "$document_id" '.documents[]? | select((.name | split("/") | last) == $id)' <<<"$collection_json" >/dev/null
}

for old_id in "${old_ids[@]}"; do
  document_exists "$technicians_json" "$old_id" || {
    echo "Preflight failed: expected old technician document $old_id is missing" >&2
    exit 1
  }
done

new_email_conflicts=0
for roster_row in "${new_roster[@]}"; do
  IFS='|' read -r name email branch skills <<<"$roster_row"
  auth_count="$(jq --arg email "$email" '[.users[]? | select((.email // "" | ascii_downcase) == $email)] | length' <<<"$auth_json")"
  user_count="$(jq --arg email "$email" '[.documents[]? | select((.fields.email.stringValue // "" | ascii_downcase) == $email)] | length' <<<"$users_json")"
  if [[ "$auth_count" != "0" || "$user_count" != "0" ]]; then
    echo "Conflict: $email already has Auth=$auth_count usersDocs=$user_count" >&2
    new_email_conflicts=$((new_email_conflicts + 1))
  fi
done
[[ "$new_email_conflicts" == "0" ]] || { echo "No writes performed because new-account conflicts exist" >&2; exit 1; }

old_nuwan_id="44b87a2d-417f-4947-a8c9-ceeacafeaca1"
active_repair_id="yfTT5EbN2J1Y3MgdXjBp"
historical_repair_id="QXBi8TVAgNHuxYMvz3R0"

jq -e --arg id "$active_repair_id" --arg old "$old_nuwan_id" '
  .documents[] | select((.name | split("/") | last) == $id) |
  .fields.technicianId.stringValue == $old and
  .fields.branchId.stringValue == "colombo" and
  .fields.deviceDetails.mapValue.fields.category.stringValue == "MOBILE" and
  .fields.status.stringValue == "APPROVED"
' <<<"$repairs_json" >/dev/null || { echo "Active repair preflight does not match the approved migration" >&2; exit 1; }

jq -e --arg id "$historical_repair_id" --arg old "$old_nuwan_id" '
  .documents[] | select((.name | split("/") | last) == $id) |
  .fields.technicianId.stringValue == $old and .fields.status.stringValue == "COMPLETED"
' <<<"$repairs_json" >/dev/null || { echo "Historical repair preflight does not match the preserved reference" >&2; exit 1; }

for index in "${!old_auth_uids[@]}"; do
  uid="${old_auth_uids[$index]}"
  expected_email="${old_auth_emails[$index]}"
  jq -e --arg uid "$uid" --arg email "$expected_email" '
    .users[] | select(.localId == $uid) | (.email | ascii_downcase) == $email and (.disabled // false) == false
  ' <<<"$auth_json" >/dev/null || {
    echo "Old Auth account preflight mismatch for $expected_email; no writes performed" >&2
    exit 1
  }
done

echo "PREFLIGHT oldTechnicians=6 oldRepairReferences=2 activeReassignments=1 historicalPreserved=1 newEmailConflicts=0"
for roster_row in "${new_roster[@]}"; do
  IFS='|' read -r name email branch skills <<<"$roster_row"
  echo "PLAN name=$name email=$email branch=$branch skills=$skills available=true"
done

if [[ "$mode" == "--dry-run" ]]; then
  echo "DRY_RUN_OK no external writes performed"
  exit 0
fi

technician_password="${FIXORA_TECHNICIAN_PASSWORD:-}"
[[ -n "$technician_password" ]] || { echo "FIXORA_TECHNICIAN_PASSWORD is required for --apply" >&2; exit 1; }

new_uids=()
new_technician_ids=()

firestore_technician_body() {
  local document_id="$1" name="$2" branch="$3" skills_csv="$4" linked_uid="$5" active="$6" timestamp="$7"
  jq -n \
    --arg id "$document_id" --arg name "$name" --arg branch "$branch" \
    --arg linkedUid "$linked_uid" --arg skills "$skills_csv" \
    --argjson active "$active" --arg timestamp "$timestamp" '
      {fields: {
        id: {stringValue: $id},
        name: {stringValue: $name},
        branchId: {stringValue: $branch},
        categorySkills: {arrayValue: {values: ($skills | split(",") | map({stringValue: .}))}},
        available: {booleanValue: true},
        active: {booleanValue: $active},
        linkedUserId: {stringValue: $linkedUid},
        archivedAt: {nullValue: null},
        createdAt: {timestampValue: $timestamp},
        updatedAt: {timestampValue: $timestamp}
      }}'
}

for roster_index in "${!new_roster[@]}"; do
  roster_row="${new_roster[$roster_index]}"
  IFS='|' read -r name email branch skills <<<"$roster_row"
  auth_payload="$(jq -n --arg email "$email" --arg password "$technician_password" '{email:$email,password:$password,returnSecureToken:true}')"
  auth_response="$(curl -fsS -X POST "$identity_base/accounts:signUp?key=$api_key" -H 'Content-Type: application/json' --data "$auth_payload")"
  uid="$(jq -r '.localId' <<<"$auth_response")"
  [[ -n "$uid" && "$uid" != "null" ]] || { echo "Auth creation did not return a UID for $email" >&2; exit 1; }

  timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
  placeholder_body="$(firestore_technician_body pending "$name" "$branch" "$skills" "$uid" false "$timestamp")"
  created_document="$(curl -fsS -X POST "$firestore_base/technicians" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$placeholder_body")"
  technician_id="$(jq -r '.name | split("/") | last' <<<"$created_document")"
  [[ -n "$technician_id" && "$technician_id" != "null" ]] || { echo "Firestore did not generate a technician ID for $email" >&2; exit 1; }

  technician_body="$(firestore_technician_body "$technician_id" "$name" "$branch" "$skills" "$uid" true "$timestamp")"
  curl -fsS -X PATCH "$firestore_base/technicians/$technician_id" \
    -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' \
    --data "$technician_body" >/dev/null

  user_body="$(jq -n --arg uid "$uid" --arg email "$email" --arg branch "$branch" --arg technicianId "$technician_id" --arg timestamp "$timestamp" '
    {fields:{
      uid:{stringValue:$uid}, email:{stringValue:$email}, role:{stringValue:"TECHNICIAN"},
      technicianId:{stringValue:$technicianId}, branchId:{stringValue:$branch},
      createdAt:{timestampValue:$timestamp}, updatedAt:{timestampValue:$timestamp}
    }}')"
  curl -fsS -X PATCH "$firestore_base/users/$uid" \
    -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' \
    --data "$user_body" >/dev/null

  new_uids[$roster_index]="$uid"
  new_technician_ids[$roster_index]="$technician_id"
done

# Verify all six complete links before touching an old technician, repair, or account.
for roster_index in "${!new_roster[@]}"; do
  roster_row="${new_roster[$roster_index]}"
  IFS='|' read -r name email branch skills <<<"$roster_row"
  uid="${new_uids[$roster_index]}"
  technician_id="${new_technician_ids[$roster_index]}"
  technician_doc="$(curl -fsS "$firestore_base/technicians/$technician_id" -H "Authorization: Bearer $oauth_token")"
  user_doc="$(curl -fsS "$firestore_base/users/$uid" -H "Authorization: Bearer $oauth_token")"
  jq -e --arg id "$technician_id" --arg uid "$uid" --arg branch "$branch" '
    .fields.id.stringValue == $id and .fields.linkedUserId.stringValue == $uid and
    .fields.branchId.stringValue == $branch and .fields.active.booleanValue == true and
    .fields.available.booleanValue == true
  ' <<<"$technician_doc" >/dev/null
  jq -e --arg id "$technician_id" --arg uid "$uid" --arg email "$email" --arg branch "$branch" '
    .fields.uid.stringValue == $uid and (.fields.email.stringValue | ascii_downcase) == $email and
    .fields.role.stringValue == "TECHNICIAN" and .fields.technicianId.stringValue == $id and
    .fields.branchId.stringValue == $branch
  ' <<<"$user_doc" >/dev/null
done

archive_timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
archive_body="$(jq -n --arg timestamp "$archive_timestamp" '{fields:{active:{booleanValue:false},available:{booleanValue:false},archivedAt:{timestampValue:$timestamp},updatedAt:{timestampValue:$timestamp}}}')"
for old_id in "${old_ids[@]}"; do
  curl -fsS -X PATCH "$firestore_base/technicians/$old_id?updateMask.fieldPaths=active&updateMask.fieldPaths=available&updateMask.fieldPaths=archivedAt&updateMask.fieldPaths=updatedAt" \
    -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$archive_body" >/dev/null
done

new_kasun_id="${new_technician_ids[0]}"
assignment_body="$(jq -n --arg id "$new_kasun_id" '{fields:{technicianId:{stringValue:$id}}}')"
curl -fsS -X PATCH "$firestore_base/repairRequests/$active_repair_id?updateMask.fieldPaths=technicianId" \
  -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$assignment_body" >/dev/null

for old_uid in "${old_auth_uids[@]}"; do
  disable_payload="$(jq -n --arg uid "$old_uid" --arg project "$project_id" '{localId:$uid,targetProjectId:$project,disableUser:true}')"
  curl -fsS -X POST "$identity_base/accounts:update" \
    -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$disable_payload" >/dev/null
done

# Final persistence verification.
final_repair="$(curl -fsS "$firestore_base/repairRequests/$active_repair_id" -H "Authorization: Bearer $oauth_token")"
jq -e --arg id "$new_kasun_id" '
  .fields.technicianId.stringValue == $id and .fields.status.stringValue == "APPROVED" and
  .fields.branchId.stringValue == "colombo"
' <<<"$final_repair" >/dev/null

historical_repair="$(curl -fsS "$firestore_base/repairRequests/$historical_repair_id" -H "Authorization: Bearer $oauth_token")"
jq -e --arg id "$old_nuwan_id" '.fields.technicianId.stringValue == $id and .fields.status.stringValue == "COMPLETED"' <<<"$historical_repair" >/dev/null

for old_id in "${old_ids[@]}"; do
  archived_doc="$(curl -fsS "$firestore_base/technicians/$old_id" -H "Authorization: Bearer $oauth_token")"
  jq -e '.fields.active.booleanValue == false and .fields.available.booleanValue == false and .fields.archivedAt.timestampValue != null' <<<"$archived_doc" >/dev/null
done

final_auth="$(curl -fsS "$identity_base/projects/$project_id/accounts:batchGet?maxResults=1000" -H "Authorization: Bearer $oauth_token")"
for old_uid in "${old_auth_uids[@]}"; do
  jq -e --arg uid "$old_uid" '.users[] | select(.localId == $uid) | .disabled == true' <<<"$final_auth" >/dev/null
done

echo "MIGRATION_RESULT createdTechnicians=6 createdAuthAccounts=6 linkedUsers=6 archivedTechnicians=6 reassignedActiveRepairs=1 preservedHistoricalRepairs=1 disabledOldAccounts=4 deletedRecords=0"
for roster_index in "${!new_roster[@]}"; do
  roster_row="${new_roster[$roster_index]}"
  IFS='|' read -r name email branch skills <<<"$roster_row"
  echo "NEW_TECHNICIAN name=$name email=$email uid=${new_uids[$roster_index]} technicianId=${new_technician_ids[$roster_index]} branch=$branch skills=$skills accountLinked=true"
done
