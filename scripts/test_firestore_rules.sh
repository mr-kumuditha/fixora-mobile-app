#!/usr/bin/env bash
set -euo pipefail

AUTH_BASE="http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1"
FIRESTORE_BASE="http://127.0.0.1:8080/v1/projects/demo-fixora/databases/(default)/documents"
FIRESTORE_COMMIT="$FIRESTORE_BASE:commit"
OWNER_HEADER="Authorization: Bearer owner"

passed=0

create_identity() {
  local email="$1"
  local response
  response="$(curl -sS -X POST "$AUTH_BASE/accounts:signUp?key=fake" \
    -H 'Content-Type: application/json' \
    --data "{\"email\":\"$email\",\"password\":\"Secure123!\",\"returnSecureToken\":true}")"
  printf '%s|%s' "$(printf '%s' "$response" | jq -r .localId)" "$(printf '%s' "$response" | jq -r .idToken)"
}

seed_document() {
  local path="$1"
  local body="$2"
  curl -sS -o /dev/null -X PATCH "$FIRESTORE_BASE/$path" \
    -H "$OWNER_HEADER" -H 'Content-Type: application/json' --data "$body"
}

expect_status() {
  local label="$1"
  local expected="$2"
  local token="$3"
  local method="$4"
  local url="$5"
  local body="${6:-}"
  local actual
  if [[ -n "$body" ]]; then
    actual="$(curl -sS -o /dev/null -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data "$body")"
  else
    actual="$(curl -sS -o /dev/null -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token")"
  fi
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $label expected $expected, got $actual" >&2
    exit 1
  fi
  passed=$((passed + 1))
  echo "PASS: $label"
}

admin_identity="$(create_identity admin@fixora.test)"
manager_identity="$(create_identity manager@fixora.test)"
unscoped_identity="$(create_identity unscoped@fixora.test)"
technician_identity="$(create_identity technician@fixora.test)"
peer_identity="$(create_identity peer@fixora.test)"
customer_identity="$(create_identity customer@fixora.test)"

admin_uid="${admin_identity%%|*}"; admin_token="${admin_identity#*|}"
manager_uid="${manager_identity%%|*}"; manager_token="${manager_identity#*|}"
unscoped_uid="${unscoped_identity%%|*}"; unscoped_token="${unscoped_identity#*|}"
technician_uid="${technician_identity%%|*}"; technician_token="${technician_identity#*|}"
peer_uid="${peer_identity%%|*}"; peer_token="${peer_identity#*|}"
customer_uid="${customer_identity%%|*}"; customer_token="${customer_identity#*|}"

seed_document "users/$admin_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$admin_uid\"},\"email\":{\"stringValue\":\"admin@fixora.test\"},\"role\":{\"stringValue\":\"ADMIN\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
seed_document "users/$manager_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$manager_uid\"},\"role\":{\"stringValue\":\"BRANCH_MANAGER\"},\"branchId\":{\"stringValue\":\"colombo\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
seed_document "users/$unscoped_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$unscoped_uid\"},\"role\":{\"stringValue\":\"BRANCH_MANAGER\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
seed_document "users/$technician_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$technician_uid\"},\"role\":{\"stringValue\":\"TECHNICIAN\"},\"branchId\":{\"stringValue\":\"colombo\"},\"technicianId\":{\"stringValue\":\"tech-1\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
seed_document "users/$peer_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$peer_uid\"},\"role\":{\"stringValue\":\"TECHNICIAN\"},\"branchId\":{\"stringValue\":\"colombo\"},\"technicianId\":{\"stringValue\":\"tech-3\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
seed_document "users/$customer_uid" "{\"fields\":{\"uid\":{\"stringValue\":\"$customer_uid\"},\"email\":{\"stringValue\":\"customer@fixora.test\"},\"role\":{\"stringValue\":\"CUSTOMER\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"

technician_fields() {
  local id="$1" branch="$2" available="$3" active="$4" linked_uid="$5" category="$6"
  printf '%s' "{\"fields\":{\"id\":{\"stringValue\":\"$id\"},\"name\":{\"stringValue\":\"$id\"},\"branchId\":{\"stringValue\":\"$branch\"},\"categorySkills\":{\"arrayValue\":{\"values\":[{\"stringValue\":\"$category\"}]}},\"available\":{\"booleanValue\":$available},\"active\":{\"booleanValue\":$active},\"linkedUserId\":{\"stringValue\":\"$linked_uid\"},\"archivedAt\":{\"nullValue\":null},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"},\"updatedAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"
}

seed_document "technicians/tech-1" "$(technician_fields tech-1 colombo true true "$technician_uid" MOBILE)"
seed_document "technicians/tech-2" "$(technician_fields tech-2 galle true true missing-user MOBILE)"
seed_document "technicians/tech-3" "$(technician_fields tech-3 colombo true true "$peer_uid" MOBILE)"
seed_document "technicians/unavailable-tech" "$(technician_fields unavailable-tech colombo false true "$peer_uid" MOBILE)"
seed_document "technicians/wrong-skill-tech" "$(technician_fields wrong-skill-tech colombo true true "$peer_uid" LAPTOP)"
seed_document "technicians/archived-tech" "$(technician_fields archived-tech colombo false false "$peer_uid" MOBILE)"
seed_document "technicians/unlinked-tech" "$(technician_fields unlinked-tech colombo true true missing-user MOBILE)"

repair_fields() {
  local customer="$1" branch="$2" technician="$3" status="$4"
  printf '%s' "{\"fields\":{\"customerId\":{\"stringValue\":\"$customer\"},\"serviceId\":{\"stringValue\":\"service\"},\"deviceDetails\":{\"mapValue\":{\"fields\":{\"category\":{\"stringValue\":\"MOBILE\"},\"brand\":{\"stringValue\":\"Fixora\"},\"model\":{\"stringValue\":\"Test\"}}}},\"issueDescription\":{\"stringValue\":\"Issue\"},\"imageUrls\":{\"arrayValue\":{}},\"branchId\":{\"stringValue\":\"$branch\"},\"technicianId\":{\"stringValue\":\"$technician\"},\"status\":{\"stringValue\":\"$status\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"},\"scheduledAt\":{\"nullValue\":null},\"completedAt\":{\"nullValue\":null}}}"
}

seed_document "repairRequests/colombo-repair" "$(repair_fields "$customer_uid" colombo tech-1 CONFIRMED)"
seed_document "repairRequests/galle-repair" "$(repair_fields other-customer galle tech-2 CONFIRMED)"
seed_document "repairRequests/other-tech-repair" "$(repair_fields other-customer colombo tech-2 CONFIRMED)"
seed_document "payments/payment-1" "{\"fields\":{\"repairRequestId\":{\"stringValue\":\"colombo-repair\"},\"amount\":{\"doubleValue\":4800},\"method\":{\"stringValue\":\"CARD\"},\"status\":{\"stringValue\":\"SUCCESS\"},\"receiptId\":{\"stringValue\":\"FX-TEST\"},\"createdAt\":{\"timestampValue\":\"2026-08-23T00:00:00Z\"}}}"

expect_status "admin reads another user" 200 "$admin_token" GET "$FIRESTORE_BASE/users/$customer_uid"
expect_status "manager cannot read user directory" 403 "$manager_token" GET "$FIRESTORE_BASE/users/$customer_uid"
expect_status "manager reads linked technician in own branch" 200 "$manager_token" GET "$FIRESTORE_BASE/users/$peer_uid"
expect_status "manager reads own branch repair" 200 "$manager_token" GET "$FIRESTORE_BASE/repairRequests/colombo-repair"
expect_status "manager cannot read other branch repair" 403 "$manager_token" GET "$FIRESTORE_BASE/repairRequests/galle-repair"
expect_status "unscoped manager cannot read repairs" 403 "$unscoped_token" GET "$FIRESTORE_BASE/repairRequests/colombo-repair"
expect_status "technician reads assigned repair" 200 "$technician_token" GET "$FIRESTORE_BASE/repairRequests/colombo-repair"
expect_status "technician cannot read peer repair" 403 "$technician_token" GET "$FIRESTORE_BASE/repairRequests/other-tech-repair"
expect_status "customer reads own repair" 200 "$customer_token" GET "$FIRESTORE_BASE/repairRequests/colombo-repair"
expect_status "customer cannot read another repair" 403 "$customer_token" GET "$FIRESTORE_BASE/repairRequests/galle-repair"
expect_status "manager cannot read payments" 403 "$manager_token" GET "$FIRESTORE_BASE/payments/payment-1"
expect_status "admin reads payment" 200 "$admin_token" GET "$FIRESTORE_BASE/payments/payment-1"
expect_status "repair owner reads payment" 200 "$customer_token" GET "$FIRESTORE_BASE/payments/payment-1"

expect_status "manager assigns verified same-branch technician" 200 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"tech-3"}}}'
expect_status "manager cannot assign unavailable technician" 403 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"unavailable-tech"}}}'
expect_status "manager cannot assign wrong-skill technician" 403 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"wrong-skill-tech"}}}'
expect_status "manager cannot assign archived technician" 403 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"archived-tech"}}}'
expect_status "manager cannot assign missing account link" 403 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"unlinked-tech"}}}'
expect_status "manager restores original verified technician" 200 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"tech-1"}}}'

expect_status "technician advances assigned repair" 200 "$technician_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=status" '{"fields":{"status":{"stringValue":"RECEIVED"}}}'
expect_status "technician cannot reassign repair" 403 "$technician_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"tech-2"}}}'
expect_status "technician cannot change own technician link" 403 "$technician_token" PATCH "$FIRESTORE_BASE/users/$technician_uid?updateMask.fieldPaths=technicianId" '{"fields":{"technicianId":{"stringValue":"tech-3"}}}'
expect_status "technician cannot change own branch" 403 "$technician_token" PATCH "$FIRESTORE_BASE/users/$technician_uid?updateMask.fieldPaths=branchId" '{"fields":{"branchId":{"stringValue":"galle"}}}'
expect_status "technician cannot change own role" 403 "$technician_token" PATCH "$FIRESTORE_BASE/users/$technician_uid?updateMask.fieldPaths=role" '{"fields":{"role":{"stringValue":"ADMIN"}}}'
expect_status "admin cannot break linked technician branch" 403 "$admin_token" PATCH "$FIRESTORE_BASE/technicians/tech-1?updateMask.fieldPaths=branchId" '{"fields":{"branchId":{"stringValue":"galle"}}}'

atomic_branch_body() {
  local branch="$1"
  jq -n \
    --arg technician_name "projects/demo-fixora/databases/(default)/documents/technicians/tech-1" \
    --arg user_name "projects/demo-fixora/databases/(default)/documents/users/$technician_uid" \
    --arg branch "$branch" '
      {writes:[
        {update:{name:$technician_name,fields:{branchId:{stringValue:$branch}}},
          updateMask:{fieldPaths:["branchId"]},
          updateTransforms:[{fieldPath:"updatedAt",setToServerValue:"REQUEST_TIME"}]},
        {update:{name:$user_name,fields:{branchId:{stringValue:$branch}}},
          updateMask:{fieldPaths:["branchId"]},
          updateTransforms:[{fieldPath:"updatedAt",setToServerValue:"REQUEST_TIME"}]}
      ]}'
}

expect_status "admin atomically moves linked technician branch" 200 "$admin_token" POST "$FIRESTORE_COMMIT" "$(atomic_branch_body galle)"
expect_status "admin atomically restores linked technician branch" 200 "$admin_token" POST "$FIRESTORE_COMMIT" "$(atomic_branch_body colombo)"
expect_status "admin cannot permanently delete technician" 403 "$admin_token" DELETE "$FIRESTORE_BASE/technicians/tech-3"
expect_status "manager cannot move repair to another branch" 403 "$manager_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=branchId" '{"fields":{"branchId":{"stringValue":"galle"}}}'
expect_status "customer cannot change repair branch" 403 "$customer_token" PATCH "$FIRESTORE_BASE/repairRequests/colombo-repair?updateMask.fieldPaths=branchId" '{"fields":{"branchId":{"stringValue":"galle"}}}'
expect_status "admin cannot change own role" 403 "$admin_token" PATCH "$FIRESTORE_BASE/users/$admin_uid?updateMask.fieldPaths=role" '{"fields":{"role":{"stringValue":"CUSTOMER"}}}'

echo "$passed Firestore emulator security assertions passed."
