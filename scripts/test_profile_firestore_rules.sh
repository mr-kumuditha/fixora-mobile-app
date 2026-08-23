#!/usr/bin/env bash
set -euo pipefail

# Run through Firebase emulators:exec so Auth tokens and Firestore data are
# disposable while requests still exercise the real firestore.rules file.

: "${FIREBASE_AUTH_EMULATOR_HOST:?Run this script through firebase emulators:exec with the auth emulator}"
: "${FIRESTORE_EMULATOR_HOST:?Run this script through firebase emulators:exec with the firestore emulator}"

PROJECT_ID="${GCLOUD_PROJECT:-techfix-mobile-app}"
AUTH_BASE="http://${FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts"
FIRESTORE_BASE="http://${FIRESTORE_EMULATOR_HOST}/v1/projects/${PROJECT_ID}/databases/(default)/documents"
TEST_PASSWORD="FixoraProfileTest123!"
TMP_DIR="$(mktemp -d)"
trap 'rm -r "$TMP_DIR"' EXIT

fail() {
  echo "profile-rules-test: FAILED: $1" >&2
  exit 1
}

sign_up() {
  local email="$1"
  local output="$2"
  local status
  status="$(curl -sS -o "$output" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -X POST \
    -d "$(jq -nc --arg email "$email" --arg password "$TEST_PASSWORD" '{email: $email, password: $password, returnSecureToken: true}')" \
    "${AUTH_BASE}:signUp?key=emulator-key")"
  [[ "$status" == "200" ]] || fail "Auth emulator sign-up returned HTTP ${status}"
}

firestore_request() {
  local method="$1"
  local url="$2"
  local token="$3"
  local payload="$4"
  local output="$5"
  local args=(-sS -o "$output" -w '%{http_code}' -H "Authorization: Bearer ${token}" -X "$method")
  if [[ -n "$payload" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$payload")
  fi
  curl "${args[@]}" "$url"
}

create_profile() {
  local uid="$1"
  local email="$2"
  local token="$3"
  local output="$4"
  local now payload status
  now="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  payload="$(jq -nc --arg uid "$uid" --arg email "$email" --arg now "$now" '{fields: {uid: {stringValue: $uid}, email: {stringValue: $email}, role: {stringValue: "CUSTOMER"}, createdAt: {timestampValue: $now}}}')"
  status="$(firestore_request PATCH "${FIRESTORE_BASE}/users/${uid}" "$token" "$payload" "$output")"
  [[ "$status" == "200" ]] || fail "profile creation returned HTTP ${status}"
}

write_payload() {
  local document_name="$1"
  local fields_json="$2"
  local mask_json="$3"
  jq -nc \
    --arg name "$document_name" \
    --argjson fields "$fields_json" \
    --argjson mask "$mask_json" \
    '{writes: [{update: {name: $name, fields: $fields}, updateMask: {fieldPaths: $mask}, updateTransforms: [{fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME"}]}]}'
}

sign_up "fixora-profile-owner@example.com" "$TMP_DIR/owner-auth.json"
sign_up "fixora-profile-other@example.com" "$TMP_DIR/other-auth.json"
OWNER_TOKEN="$(jq -r '.idToken' "$TMP_DIR/owner-auth.json")"
OWNER_UID="$(jq -r '.localId' "$TMP_DIR/owner-auth.json")"
OTHER_TOKEN="$(jq -r '.idToken' "$TMP_DIR/other-auth.json")"
OTHER_UID="$(jq -r '.localId' "$TMP_DIR/other-auth.json")"

create_profile "$OWNER_UID" "fixora-profile-owner@example.com" "$OWNER_TOKEN" "$TMP_DIR/owner-create.json"
create_profile "$OTHER_UID" "fixora-profile-other@example.com" "$OTHER_TOKEN" "$TMP_DIR/other-create.json"

DOCUMENT_NAME="projects/${PROJECT_ID}/databases/(default)/documents/users/${OWNER_UID}"
COMMIT_URL="${FIRESTORE_BASE}:commit"

safe_payload="$(write_payload "$DOCUMENT_NAME" '{"name":{"stringValue":"Kumuditha Tharinda"},"phone":{"stringValue":"+94 77 123 4567"}}' '["name","phone"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$safe_payload" "$TMP_DIR/safe.json")"
[[ "$status" == "200" ]] || fail "owner profile update returned HTTP ${status}"

photo_payload="$(write_payload "$DOCUMENT_NAME" '{"photoUrl":{"stringValue":"https://example.com/profile.jpg"}}' '["photoUrl"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$photo_payload" "$TMP_DIR/photo.json")"
[[ "$status" == "200" ]] || fail "owner profile-photo update returned HTTP ${status}"

remove_photo_payload="$(write_payload "$DOCUMENT_NAME" '{}' '["photoUrl"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$remove_photo_payload" "$TMP_DIR/remove-photo.json")"
[[ "$status" == "200" ]] || fail "owner profile-photo removal returned HTTP ${status}"

role_payload="$(write_payload "$DOCUMENT_NAME" '{"role":{"stringValue":"ADMIN"}}' '["role"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$role_payload" "$TMP_DIR/role-denied.json")"
[[ "$status" == "403" ]] || fail "role change returned HTTP ${status}, expected 403"

branch_payload="$(write_payload "$DOCUMENT_NAME" '{"branchId":{"stringValue":"colombo"}}' '["branchId"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$branch_payload" "$TMP_DIR/branch-denied.json")"
[[ "$status" == "403" ]] || fail "branch change returned HTTP ${status}, expected 403"

technician_payload="$(write_payload "$DOCUMENT_NAME" '{"technicianId":{"stringValue":"other-technician"}}' '["technicianId"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OWNER_TOKEN" "$technician_payload" "$TMP_DIR/technician-denied.json")"
[[ "$status" == "403" ]] || fail "technician change returned HTTP ${status}, expected 403"

other_payload="$(write_payload "$DOCUMENT_NAME" '{"name":{"stringValue":"Unauthorized"}}' '["name"]')"
status="$(firestore_request POST "$COMMIT_URL" "$OTHER_TOKEN" "$other_payload" "$TMP_DIR/other-denied.json")"
[[ "$status" == "403" ]] || fail "cross-user update returned HTTP ${status}, expected 403"

status="$(firestore_request GET "${FIRESTORE_BASE}/users/${OWNER_UID}" "$OWNER_TOKEN" "" "$TMP_DIR/final.json")"
[[ "$status" == "200" ]] || fail "final profile read returned HTTP ${status}"
[[ "$(jq -r '.fields.role.stringValue' "$TMP_DIR/final.json")" == "CUSTOMER" ]] || fail "role changed unexpectedly"
[[ "$(jq -r '.fields.name.stringValue' "$TMP_DIR/final.json")" == "Kumuditha Tharinda" ]] || fail "name did not persist"
[[ "$(jq -r '.fields.phone.stringValue' "$TMP_DIR/final.json")" == "+94 77 123 4567" ]] || fail "phone did not persist"
[[ "$(jq -r '.fields.photoUrl.stringValue // empty' "$TMP_DIR/final.json")" == "" ]] || fail "photo URL was not removed"

echo "profile-rules-test: passed owner updates, photo set/remove, role/staff-field protection, and cross-user denial"
