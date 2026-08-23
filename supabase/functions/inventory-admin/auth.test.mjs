import assert from "node:assert/strict";
import test from "node:test";
import { verifyFirebaseAdmin } from "./auth.mjs";

function tokenFor(uid) {
  const payload = Buffer.from(JSON.stringify({ sub: uid })).toString("base64url");
  return `header.${payload}.signature`;
}

function requestFor(token) {
  return new Request("https://example.test", {
    headers: token ? { authorization: `Bearer ${token}` } : {},
  });
}

function firestoreResponse(role, email = "admin@example.com", ok = true) {
  return {
    ok,
    json: async () => ({
      fields: {
        role: { stringValue: role },
        email: { stringValue: email },
      },
    }),
  };
}

test("missing bearer token is rejected before Firestore", async () => {
  let called = false;
  const result = await verifyFirebaseAdmin(requestFor(null), {
    fetchImpl: async () => { called = true; return firestoreResponse("ADMIN"); },
  });
  assert.equal(result, null);
  assert.equal(called, false);
});

for (const role of ["BRANCH_MANAGER", "TECHNICIAN", "CUSTOMER"]) {
  test(`${role} cannot enter the Admin mutation boundary`, async () => {
    const result = await verifyFirebaseAdmin(requestFor(tokenFor("staff-uid")), {
      fetchImpl: async () => firestoreResponse(role),
    });
    assert.equal(result, null);
  });
}

test("a token refused by Firestore is rejected", async () => {
  const result = await verifyFirebaseAdmin(requestFor(tokenFor("forged-uid")), {
    fetchImpl: async () => firestoreResponse("ADMIN", "admin@example.com", false),
  });
  assert.equal(result, null);
});

test("ADMIN is accepted only after the authorized Firestore self-read", async () => {
  const token = tokenFor("admin-uid");
  let requestedUrl = "";
  let authorization = "";
  const result = await verifyFirebaseAdmin(requestFor(token), {
    projectId: "techfix-mobile-app",
    fetchImpl: async (url, init) => {
      requestedUrl = String(url);
      authorization = init.headers.authorization;
      return firestoreResponse("ADMIN", "admin@example.com");
    },
  });
  assert.deepEqual(result, { uid: "admin-uid", email: "admin@example.com" });
  assert.match(requestedUrl, /projects\/techfix-mobile-app\/.*\/users\/admin-uid$/);
  assert.equal(authorization, `Bearer ${token}`);
});

