// EaglesEye dev — Keycloak bootstrap (one-shot container, like traccar-init).
// Idempotent: realm, console client (+tenant_id claim mapper), roles, dev users.
// Tenant provisioning in production automates exactly these calls per new customer.
const BASE = process.env.KC_URL || "http://keycloak:8080";
const ADMIN_USER = process.env.KC_ADMIN_USER;
const ADMIN_PASSWORD = process.env.KC_ADMIN_PASSWORD;
const USER_PASSWORD = process.env.EE_CONSOLE_USER_PASSWORD;
const REALM = "eagleseye";
const ROLES = ["platform-admin", "tenant-admin", "manager", "viewer", "installer"];

if (!ADMIN_USER || !ADMIN_PASSWORD || !USER_PASSWORD) {
  console.error("missing KC_ADMIN_USER / KC_ADMIN_PASSWORD / EE_CONSOLE_USER_PASSWORD");
  process.exit(1);
}

let token;

async function api(path, options = {}, okStatuses = []) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json", ...(options.headers || {}) },
  });
  if (!res.ok && res.status !== 204 && !okStatuses.includes(res.status)) {
    throw new Error(`${options.method || "GET"} ${path} -> ${res.status}: ${await res.text()}`);
  }
  if (okStatuses.includes(res.status)) return null;   // tolerated miss (404/409) is a null, not a body
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

async function login(timeoutMs = 180_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(`${BASE}/realms/master/protocol/openid-connect/token`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "password", client_id: "admin-cli",
          username: ADMIN_USER, password: ADMIN_PASSWORD }),
      });
      if (res.ok) { token = (await res.json()).access_token; return; }
    } catch { /* keycloak still booting */ }
    await new Promise(r => setTimeout(r, 4000));
  }
  throw new Error("Keycloak not reachable/ready in time");
}

async function ensureRealm() {
  const existing = await api(`/admin/realms/${REALM}`, {}, [404]);
  if (!existing) {
    await api("/admin/realms", { method: "POST", body: JSON.stringify({
      realm: REALM, enabled: true, displayName: "EaglesEye",
      registrationAllowed: false, loginTheme: "keycloak",
    })});
    console.log(`realm '${REALM}' created`);
  } else {
    console.log(`realm '${REALM}' exists`);
  }
  // Keycloak 26 blocks unmanaged user attributes by default — allow them so
  // tenant_id sticks (production would declare it in the user profile instead).
  const profile = await api(`/admin/realms/${REALM}/users/profile`);
  profile.unmanagedAttributePolicy = "ENABLED";
  await api(`/admin/realms/${REALM}/users/profile`, { method: "PUT", body: JSON.stringify(profile) });
  console.log("unmanaged user attributes enabled");
}

async function ensureClient() {
  const clients = await api(`/admin/realms/${REALM}/clients?clientId=eagleseye-console`);
  if (clients.length) { console.log("client exists"); return clients[0].id; }
  await api(`/admin/realms/${REALM}/clients`, { method: "POST", body: JSON.stringify({
    clientId: "eagleseye-console",
    protocol: "openid-connect",
    publicClient: true,
    standardFlowEnabled: true,
    directAccessGrantsEnabled: true,     // dev convenience: password-grant testing
    redirectUris: ["http://localhost:8080/*"],
    webOrigins: ["http://localhost:8080"],
    attributes: { "post.logout.redirect.uris": "http://localhost:8080/*" },
  })});
  const created = await api(`/admin/realms/${REALM}/clients?clientId=eagleseye-console`);
  console.log("client created");
  return created[0].id;
}

async function ensureTenantMapper(clientUuid) {
  const mappers = await api(`/admin/realms/${REALM}/clients/${clientUuid}/protocol-mappers/models`);
  if (mappers.some(m => m.name === "tenant-id")) { console.log("tenant mapper exists"); return; }
  await api(`/admin/realms/${REALM}/clients/${clientUuid}/protocol-mappers/models`, {
    method: "POST", body: JSON.stringify({
      name: "tenant-id", protocol: "openid-connect",
      protocolMapper: "oidc-usermodel-attribute-mapper",
      config: {
        "user.attribute": "tenant_id", "claim.name": "tenant_id",
        "jsonType.label": "String",
        "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true",
      },
    })});
  console.log("tenant_id claim mapper created");
}

async function ensureRoles() {
  for (const role of ROLES) {
    await api(`/admin/realms/${REALM}/roles`, { method: "POST", body: JSON.stringify({ name: role }) }, [409]);
  }
  console.log(`roles ensured: ${ROLES.join(", ")}`);
}

async function ensureUser(username, email, tenantId, roleName) {
  const profile = {
    username, email, enabled: true, emailVerified: true,
    firstName: username.charAt(0).toUpperCase() + username.slice(1),
    lastName: "EaglesEye",
    requiredActions: [],                       // fully set up — password grant must work
    attributes: { tenant_id: [tenantId] },
  };
  let users = await api(`/admin/realms/${REALM}/users?username=${username}&exact=true`);
  if (!users.length) {
    await api(`/admin/realms/${REALM}/users`, { method: "POST", body: JSON.stringify({
      ...profile,
      credentials: [{ type: "password", value: USER_PASSWORD, temporary: false }],
    })});
    users = await api(`/admin/realms/${REALM}/users?username=${username}&exact=true`);
    console.log(`user '${username}' created (tenant ${tenantId})`);
  } else {
    await api(`/admin/realms/${REALM}/users/${users[0].id}`,
        { method: "PUT", body: JSON.stringify(profile) });
    console.log(`user '${username}' updated (tenant ${tenantId})`);
  }
  const role = await api(`/admin/realms/${REALM}/roles/${roleName}`);
  await api(`/admin/realms/${REALM}/users/${users[0].id}/role-mappings/realm`,
      { method: "POST", body: JSON.stringify([{ id: role.id, name: role.name }]) });
}

const main = async () => {
  await login();
  console.log("keycloak admin API reachable");
  await ensureRealm();
  const clientUuid = await ensureClient();
  await ensureTenantMapper(clientUuid);
  await ensureRoles();
  await ensureUser("moataz", "moataz@eagleseye.local", "dev-tenant", "tenant-admin");
  await ensureUser("gulf", "gulf@eagleseye.local", "tenant-b", "tenant-admin");
  console.log("keycloak-init done");
};

main().catch(err => { console.error("keycloak-init FAILED:", err.message); process.exit(1); });
