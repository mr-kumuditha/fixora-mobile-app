function decodeFirebaseUid(token) {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const claims = JSON.parse(atob(padded));
    const uid = claims.user_id ?? claims.sub;
    return typeof uid === "string" && uid.length > 0 ? uid : null;
  } catch {
    return null;
  }
}

function firestoreString(document, field) {
  const value = document?.fields?.[field]?.stringValue;
  return typeof value === "string" ? value : "";
}

/**
 * Firestore is the token verifier here, not the unverified JWT payload. The
 * decoded UID only chooses the document path. A valid Firebase token and the
 * deployed self-read rule are both required before a role can be returned.
 */
export async function verifyFirebaseAdmin(
  request,
  {
    projectId = "techfix-mobile-app",
    fetchImpl = fetch,
  } = {},
) {
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return null;
  const token = authorization.slice("Bearer ".length).trim();
  const uid = decodeFirebaseUid(token);
  if (!uid) return null;

  const documentUrl = `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/users/${encodeURIComponent(uid)}`;
  const response = await fetchImpl(documentUrl, {
    headers: { authorization: `Bearer ${token}` },
  });
  if (!response.ok) return null;

  const document = await response.json();
  if (firestoreString(document, "role") !== "ADMIN") return null;
  return { uid, email: firestoreString(document, "email") };
}

