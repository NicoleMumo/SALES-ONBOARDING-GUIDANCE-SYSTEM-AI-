const fs = require("node:fs");
const { after, before, beforeEach, describe, it } = require("mocha");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { ref, get, set } = require("firebase/database");

describe("Realtime Database access rules", function () {
  let testEnv;

  before(async function () {
    testEnv = await initializeTestEnvironment({
      projectId: "demo-salesmesh-rules",
      database: {
        host: "127.0.0.1",
        port: 9000,
        rules: fs.readFileSync("database.rules.json", "utf8"),
      },
    });
  });

  beforeEach(async function () {
    await testEnv.clearDatabase();
  });

  after(async function () {
    await testEnv?.cleanup();
  });

  it("denies unauthenticated session and configuration access", async function () {
    const db = testEnv.unauthenticatedContext().database();
    await assertFails(get(ref(db, "onboardingSessions/session-1")));
    await assertFails(get(ref(db, "fieldRuleOverrides")));
  });

  it("allows an agent to create and read only their own session", async function () {
    const ownerDb = testEnv.authenticatedContext("agent-1").database();
    const otherDb = testEnv.authenticatedContext("agent-2").database();
    const session = {
      sessionId: "session-1",
      agentUid: "agent-1",
      status: "in_progress",
    };

    await assertSucceeds(set(ref(ownerDb, "onboardingSessions/session-1"), session));
    await assertSucceeds(get(ref(ownerDb, "onboardingSessions/session-1")));
    await assertFails(get(ref(otherDb, "onboardingSessions/session-1")));
    await assertFails(
      set(ref(ownerDb, "onboardingSessions/session-1/agentUid"), "agent-2"),
    );
  });

  it("restricts management data to administrators", async function () {
    const agentDb = testEnv.authenticatedContext("agent-1").database();
    const adminDb = testEnv.authenticatedContext("admin-1", { admin: true }).database();

    await assertFails(set(ref(agentDb, "salesAgents/agent-2"), { email: "agent@example.com" }));
    await assertFails(set(ref(agentDb, "fieldRuleOverrides/email_address/required"), true));
    await assertSucceeds(set(ref(adminDb, "salesAgents/agent-2"), { email: "agent@example.com" }));
    await assertSucceeds(set(ref(adminDb, "fieldRuleOverrides/email_address/required"), true));
    await assertSucceeds(get(ref(adminDb, "onboardingSessions")));
  });
});
