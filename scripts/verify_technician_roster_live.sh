#!/usr/bin/env bash
set -euo pipefail

# Live, reversible verification for the approved technician migration.
# Requires FIXORA_TECHNICIAN_PASSWORD. Passwords and tokens never leave memory.

project_id="techfix-mobile-app"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
firebase_config="/Users/kumudithatharinda/.config/configstore/firebase-tools.json"
firestore_base="https://firestore.googleapis.com/v1/projects/$project_id/databases/(default)/documents"
run_query_url="https://firestore.googleapis.com/v1/projects/$project_id/databases/(default)/documents:runQuery"
identity_base="https://identitytoolkit.googleapis.com/v1"
api_key="$(jq -r '.client[] | select(.client_info.android_client_info.package_name == "com.techfix.app") | .api_key[0].current_key' "$root_dir/app/google-services.json")"
technician_password="${FIXORA_TECHNICIAN_PASSWORD:-}"

[[ -n "$technician_password" ]] || { echo "FIXORA_TECHNICIAN_PASSWORD is required" >&2; exit 1; }
firebase projects:list --json >/dev/null
oauth_token="$(jq -r '.tokens.access_token' "$firebase_config")"

emails=(
  "kasuntech@fixora.com" "kavishkaka@fixora.com" "ravidutech@fixora.com"
  "tharushatech@fixora.com" "rivinitech@fixora.com" "nethmitech@fixora.com"
)
branches=("colombo" "colombo" "colombo" "galle" "galle" "galle")

sign_in() {
  local email="$1" password="$2"
  local payload
  payload="$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password,returnSecureToken:true}')"
  curl -fsS -X POST "$identity_base/accounts:signInWithPassword?key=$api_key" \
    -H 'Content-Type: application/json' --data "$payload"
}

query_repairs() {
  local token="$1" technician_id="$2"
  local payload
  payload="$(jq -n --arg id "$technician_id" '{structuredQuery:{from:[{collectionId:"repairRequests"}],where:{fieldFilter:{field:{fieldPath:"technicianId"},op:"EQUAL",value:{stringValue:$id}}},orderBy:[{field:{fieldPath:"createdAt"},direction:"DESCENDING"}]}}')"
  curl -fsS -X POST "$run_query_url" -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' --data "$payload"
}

request_status() {
  local method="$1" token="$2" url="$3" body="${4:-}"
  local args=(--silent --show-error --output /dev/null --write-out '%{http_code}' -X "$method" -H "Authorization: Bearer $token")
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  curl "${args[@]}" "$url"
}

expect_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $label expected_http=$expected actual_http=$actual" >&2
    exit 1
  fi
  echo "PASS $label"
}

auth_json="$(curl -fsS "$identity_base/projects/$project_id/accounts:batchGet?maxResults=1000" -H "Authorization: Bearer $oauth_token")"

technician_ids=()
technician_uids=()
baseline_counts=()
technician_tokens=()

for index in "${!emails[@]}"; do
  email="${emails[$index]}"
  expected_branch="${branches[$index]}"
  first_login="$(sign_in "$email" "$technician_password")"
  uid="$(jq -r '.localId' <<<"$first_login")"
  token="$(jq -r '.idToken' <<<"$first_login")"
  user_doc="$(curl -fsS "$firestore_base/users/$uid" -H "Authorization: Bearer $token")"
  technician_id="$(jq -r '.fields.technicianId.stringValue' <<<"$user_doc")"
  technician_doc="$(curl -fsS "$firestore_base/technicians/$technician_id" -H "Authorization: Bearer $token")"

  jq -e --arg uid "$uid" --arg id "$technician_id" --arg email "$email" --arg branch "$expected_branch" '
    .fields.uid.stringValue == $uid and (.fields.email.stringValue | ascii_downcase) == $email and
    .fields.role.stringValue == "TECHNICIAN" and .fields.technicianId.stringValue == $id and
    .fields.branchId.stringValue == $branch
  ' <<<"$user_doc" >/dev/null
  jq -e --arg uid "$uid" --arg id "$technician_id" --arg branch "$expected_branch" '
    .fields.id.stringValue == $id and .fields.linkedUserId.stringValue == $uid and
    .fields.branchId.stringValue == $branch and .fields.active.booleanValue == true and
    .fields.available.booleanValue == true
  ' <<<"$technician_doc" >/dev/null

  repairs="$(query_repairs "$token" "$technician_id")"
  jq -e --arg id "$technician_id" '[.[] | select(.document != null) | select(.document.fields.technicianId.stringValue != $id)] | length == 0' <<<"$repairs" >/dev/null
  count="$(jq '[.[] | select(.document != null)] | length' <<<"$repairs")"

  # A second sign-in and server query covers logout/login refresh semantics.
  second_login="$(sign_in "$email" "$technician_password")"
  second_token="$(jq -r '.idToken' <<<"$second_login")"
  second_repairs="$(query_repairs "$second_token" "$technician_id")"
  second_count="$(jq '[.[] | select(.document != null)] | length' <<<"$second_repairs")"
  [[ "$second_count" == "$count" ]] || { echo "FAIL repeat login repair count changed for $email" >&2; exit 1; }

  technician_ids[$index]="$technician_id"
  technician_uids[$index]="$uid"
  technician_tokens[$index]="$second_token"
  baseline_counts[$index]="$count"
  echo "PASS login-link-query email=$email branch=$expected_branch assignedRepairs=$count"
done

test_suffix="$(uuidgen | tr '[:upper:]' '[:lower:]')"
temp_password="Temp-${test_suffix}"
admin_email="roster-admin-$test_suffix@example.com"
manager_email="roster-manager-$test_suffix@example.com"
admin_auth="$(curl -fsS -X POST "$identity_base/accounts:signUp?key=$api_key" -H 'Content-Type: application/json' --data "$(jq -n --arg email "$admin_email" --arg password "$temp_password" '{email:$email,password:$password,returnSecureToken:true}')")"
manager_auth="$(curl -fsS -X POST "$identity_base/accounts:signUp?key=$api_key" -H 'Content-Type: application/json' --data "$(jq -n --arg email "$manager_email" --arg password "$temp_password" '{email:$email,password:$password,returnSecureToken:true}')")"
admin_uid="$(jq -r '.localId' <<<"$admin_auth")"; admin_token="$(jq -r '.idToken' <<<"$admin_auth")"
manager_uid="$(jq -r '.localId' <<<"$manager_auth")"; manager_token="$(jq -r '.idToken' <<<"$manager_auth")"
colombo_repair="roster-colombo-$test_suffix"
galle_repair="roster-galle-$test_suffix"
unlinked_technician="roster-unlinked-$test_suffix"
rivini_id="${technician_ids[4]}"
rivini_restored=false

cleanup() {
  # Always restore the one temporary availability mutation first.
  restore_timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
  restore_body="$(jq -n --arg timestamp "$restore_timestamp" '{fields:{available:{booleanValue:true},updatedAt:{timestampValue:$timestamp}}}')"
  curl -sS -o /dev/null -X PATCH "$firestore_base/technicians/$rivini_id?updateMask.fieldPaths=available&updateMask.fieldPaths=updatedAt" \
    -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$restore_body" || true
  for path in "repairRequests/$colombo_repair" "repairRequests/$galle_repair" "technicians/$unlinked_technician" "users/$admin_uid" "users/$manager_uid"; do
    curl -sS -o /dev/null -X DELETE "$firestore_base/$path" -H "Authorization: Bearer $oauth_token" || true
  done
  for auth_record in "$admin_auth" "$manager_auth"; do
    id_token="$(jq -r '.idToken' <<<"$auth_record")"
    curl -sS -o /dev/null -X POST "$identity_base/accounts:delete?key=$api_key" \
      -H 'Content-Type: application/json' --data "$(jq -n --arg token "$id_token" '{idToken:$token}')" || true
  done
}
trap cleanup EXIT

timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
admin_user_body="$(jq -n --arg uid "$admin_uid" --arg email "$admin_email" --arg timestamp "$timestamp" '{fields:{uid:{stringValue:$uid},email:{stringValue:$email},role:{stringValue:"ADMIN"},createdAt:{timestampValue:$timestamp}}}')"
manager_user_body="$(jq -n --arg uid "$manager_uid" --arg email "$manager_email" --arg timestamp "$timestamp" '{fields:{uid:{stringValue:$uid},email:{stringValue:$email},role:{stringValue:"BRANCH_MANAGER"},branchId:{stringValue:"galle"},createdAt:{timestampValue:$timestamp}}}')"
curl -fsS -X PATCH "$firestore_base/users/$admin_uid" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$admin_user_body" >/dev/null
curl -fsS -X PATCH "$firestore_base/users/$manager_uid" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$manager_user_body" >/dev/null

repair_body() {
  local branch="$1"
  jq -n --arg branch "$branch" --arg timestamp "$timestamp" '{fields:{
    customerId:{stringValue:"roster-verification-customer"},serviceId:{stringValue:"mobile-charging-port-repair"},
    deviceDetails:{mapValue:{fields:{category:{stringValue:"MOBILE"},brand:{stringValue:"Fixora"},model:{stringValue:"Verification"}}}},
    issueDescription:{stringValue:"Disposable technician assignment verification"},imageUrls:{arrayValue:{}},
    branchId:{stringValue:$branch},technicianId:{nullValue:null},status:{stringValue:"SUBMITTED"},
    createdAt:{timestampValue:$timestamp},scheduledAt:{nullValue:null},completedAt:{nullValue:null}
  }}'
}
curl -fsS -X PATCH "$firestore_base/repairRequests/$colombo_repair" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$(repair_body colombo)" >/dev/null
curl -fsS -X PATCH "$firestore_base/repairRequests/$galle_repair" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$(repair_body galle)" >/dev/null

assignment_body() { jq -n --arg id "$1" '{fields:{technicianId:{stringValue:$id},status:{stringValue:"CONFIRMED"}}}'; }

status="$(request_status PATCH "$admin_token" "$firestore_base/repairRequests/$colombo_repair?updateMask.fieldPaths=technicianId&updateMask.fieldPaths=status" "$(assignment_body "${technician_ids[0]}")")"
expect_status "Admin assigns verified Colombo technician" 200 "$status"
status="$(request_status PATCH "$manager_token" "$firestore_base/repairRequests/$galle_repair?updateMask.fieldPaths=technicianId&updateMask.fieldPaths=status" "$(assignment_body "${technician_ids[3]}")")"
expect_status "Branch Manager assigns verified own-branch technician" 200 "$status"

status="$(request_status PATCH "$manager_token" "$firestore_base/repairRequests/$colombo_repair?updateMask.fieldPaths=technicianId" "$(jq -n --arg id "${technician_ids[1]}" '{fields:{technicianId:{stringValue:$id}}}')")"
expect_status "Branch Manager cannot alter another branch assignment" 403 "$status"
status="$(request_status PATCH "$manager_token" "$firestore_base/repairRequests/$galle_repair?updateMask.fieldPaths=technicianId" "$(jq -n --arg id "${technician_ids[5]}" '{fields:{technicianId:{stringValue:$id}}}')")"
expect_status "Wrong-skill technician is excluded" 403 "$status"

busy_timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
busy_body="$(jq -n --arg timestamp "$busy_timestamp" '{fields:{available:{booleanValue:false},updatedAt:{timestampValue:$timestamp}}}')"
status="$(request_status PATCH "$admin_token" "$firestore_base/technicians/$rivini_id?updateMask.fieldPaths=available&updateMask.fieldPaths=updatedAt" "$busy_body")"
expect_status "Admin marks technician unavailable" 200 "$status"
status="$(request_status PATCH "$manager_token" "$firestore_base/repairRequests/$galle_repair?updateMask.fieldPaths=technicianId" "$(jq -n --arg id "$rivini_id" '{fields:{technicianId:{stringValue:$id}}}')")"
expect_status "Unavailable technician is excluded" 403 "$status"

unlinked_body="$(jq -n --arg id "$unlinked_technician" --arg timestamp "$timestamp" '{fields:{id:{stringValue:$id},name:{stringValue:"Unlinked verification"},branchId:{stringValue:"galle"},categorySkills:{arrayValue:{values:[{stringValue:"MOBILE"}]}},available:{booleanValue:true},active:{booleanValue:true},linkedUserId:{nullValue:null},archivedAt:{nullValue:null},createdAt:{timestampValue:$timestamp},updatedAt:{timestampValue:$timestamp}}}')"
curl -fsS -X PATCH "$firestore_base/technicians/$unlinked_technician" -H "Authorization: Bearer $oauth_token" -H 'Content-Type: application/json' --data "$unlinked_body" >/dev/null
status="$(request_status PATCH "$manager_token" "$firestore_base/repairRequests/$galle_repair?updateMask.fieldPaths=technicianId" "$(jq -n --arg id "$unlinked_technician" '{fields:{technicianId:{stringValue:$id}}}')")"
expect_status "Missing account link is excluded" 403 "$status"

status="$(request_status PATCH "${technician_tokens[3]}" "$firestore_base/repairRequests/$galle_repair?updateMask.fieldPaths=technicianId" "$(jq -n --arg id "${technician_ids[3]}" '{fields:{technicianId:{stringValue:$id}}}')")"
expect_status "Technician cannot self-assign" 403 "$status"

kasun_repairs="$(query_repairs "${technician_tokens[0]}" "${technician_ids[0]}")"
tharusha_repairs="$(query_repairs "${technician_tokens[3]}" "${technician_ids[3]}")"
kasun_count="$(jq '[.[] | select(.document != null)] | length' <<<"$kasun_repairs")"
tharusha_count="$(jq '[.[] | select(.document != null)] | length' <<<"$tharusha_repairs")"
[[ "$kasun_count" == "$((baseline_counts[0] + 1))" ]] || { echo "FAIL Kasun did not receive Colombo test assignment" >&2; exit 1; }
[[ "$tharusha_count" == "$((baseline_counts[3] + 1))" ]] || { echo "FAIL Tharusha did not receive Galle test assignment" >&2; exit 1; }
echo "PASS assigned technicians receive exact-id repair queries"

other_query_payload="$(jq -n --arg id "${technician_ids[4]}" '{structuredQuery:{from:[{collectionId:"repairRequests"}],where:{fieldFilter:{field:{fieldPath:"technicianId"},op:"EQUAL",value:{stringValue:$id}}}}}')"
status="$(request_status POST "${technician_tokens[3]}" "$run_query_url" "$other_query_payload")"
expect_status "Technician cannot query another technician repairs" 403 "$status"

# New login after assignments proves server persistence across session recreation.
kasun_restart="$(sign_in "${emails[0]}" "$technician_password")"
kasun_restart_repairs="$(query_repairs "$(jq -r '.idToken' <<<"$kasun_restart")" "${technician_ids[0]}")"
jq -e --arg repair "$colombo_repair" '[.[] | select(.document != null) | (.document.name|split("/")|last)] | index($repair) != null' <<<"$kasun_restart_repairs" >/dev/null
echo "PASS logout-login and session recreation preserve assignments"

echo "LIVE_VERIFICATION_RESULT technicianLogins=6 reciprocalLinks=6 exactIdQueries=6 adminAssignment=pass managerAssignment=pass managerBranchRestriction=pass technicianSelfAssignDenied=pass crossTechnicianReadDenied=pass unavailableExcluded=pass wrongSkillExcluded=pass missingLinkExcluded=pass restartPersistence=pass"

