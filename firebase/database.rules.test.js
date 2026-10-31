// Tests for firebase/database.rules.json, run against the Firebase emulator:
//   cd firebase && npm install && npm run test:database-rules
//
// Two companies ("acme" and "beta") are set up, so each test can check both what a
// person may do in their own company and that they are walled out of the other.
const fs = require("node:fs");
const { after, before, beforeEach, describe, it } = require("mocha");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { equalTo, get, orderByChild, query, ref, set, update } = require("firebase/database");

const ACME = "acme";
const BETA = "beta";

/** The data every test starts from. Written with the rules switched off. */
function seedData() {
  const member = (companyId, email, key, isActive = true) => ({
    companyId,
    email,
    displayName: email.split("@")[0],
    isActive,
    invitationKey: key,
  });
  const session = (companyId, agentId, status = "in_progress") => ({
    companyId,
    agentId,
    status,
    startedAt: 1000,
  });
  return {
    companyMemberships: {
      "agent-1": member(ACME, "one@acme.com", "one_acme_com"),
      "agent-2": member(ACME, "two@acme.com", "two_acme_com"),
      "agent-off": member(ACME, "off@acme.com", "off_acme_com", false),
      "agent-beta": member(BETA, "bee@beta.com", "bee_beta_com"),
    },
    companies: {
      [ACME]: {
        settings: { industry: "banking" },
        agentInvitations: {
          one_acme_com: { email: "one@acme.com", displayName: "one", isActive: true },
          new_acme_com: { email: "new@acme.com", displayName: "new", isActive: true },
          paused_acme_com: { email: "paused@acme.com", displayName: "paused", isActive: false },
        },
        fieldRuleOverrides: { email_address: { required: true } },
        fieldDefinitions: { loyalty_number: { fieldId: "loyalty_number", fieldName: "Loyalty number" } },
        onboardingSessions: {
          s1: session(ACME, "agent-1"),
          s2: session(ACME, "agent-2"),
          done: session(ACME, "agent-1", "completed"),
          off: session(ACME, "agent-off"),
        },
      },
      [BETA]: {
        settings: { industry: "telecom" },
        fieldRuleOverrides: { email_address: { required: false } },
        onboardingSessions: { b1: session(BETA, "agent-beta") },
      },
    },
  };
}

describe("Realtime Database access rules", function () {
  let testEnv;

  // People, as the database sees them (their sign-in token).
  const as = (uid, claims = {}) => testEnv.authenticatedContext(uid, claims).database();
  const agent1 = () => as("agent-1", { email: "one@acme.com" });
  const agent2 = () => as("agent-2", { email: "two@acme.com" });
  const agentOff = () => as("agent-off", { email: "off@acme.com" });
  const agentBeta = () => as("agent-beta", { email: "bee@beta.com" });
  const adminAcme = () => as("admin-acme", { admin: true, companyId: ACME });
  const adminBeta = () => as("admin-beta", { admin: true, companyId: BETA });
  const signedOut = () => testEnv.unauthenticatedContext().database();

  const path = (company, rest) => `companies/${company}/${rest}`;
  const newSession = (companyId, agentId, extra = {}) => ({
    companyId,
    agentId,
    status: "in_progress",
    startedAt: 2000,
    ...extra,
  });

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
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await set(ref(context.database(), "/"), seedData());
    });
  });

  after(async function () {
    await testEnv?.cleanup();
  });

  describe("signed-out visitors", function () {
    it("can read and write nothing", async function () {
      const db = signedOut();
      await assertFails(get(ref(db, path(ACME, "settings"))));
      await assertFails(get(ref(db, path(ACME, "onboardingSessions/s1"))));
      await assertFails(get(ref(db, "companyMemberships/agent-1")));
      await assertFails(set(ref(db, path(ACME, "settings/industry")), "banking"));
      await assertFails(set(ref(db, "anything/else"), true));
    });
  });

  describe("the wall between companies", function () {
    it("lets an admin read their own company's settings", async function () {
      await assertSucceeds(get(ref(adminAcme(), path(ACME, "settings"))));
      await assertSucceeds(get(ref(adminAcme(), path(ACME, "fieldRuleOverrides"))));
      await assertSucceeds(get(ref(adminAcme(), path(ACME, "fieldDefinitions"))));
    });

    it("blocks an admin from another company's data", async function () {
      const db = adminBeta();
      await assertFails(get(ref(db, path(ACME, "settings"))));
      await assertFails(get(ref(db, path(ACME, "fieldRuleOverrides"))));
      await assertFails(get(ref(db, path(ACME, "agentInvitations"))));
      await assertFails(get(ref(db, path(ACME, "onboardingSessions/s1"))));
      await assertFails(set(ref(db, path(ACME, "settings/industry")), "telecom"));
      await assertFails(set(ref(db, path(ACME, "fieldRuleOverrides/email_address/required")), false));
    });

    it("blocks an agent from another company's settings and sessions", async function () {
      const db = agentBeta();
      await assertFails(get(ref(db, path(ACME, "settings"))));
      await assertFails(get(ref(db, path(ACME, "fieldRuleOverrides"))));
      await assertFails(get(ref(db, path(ACME, "onboardingSessions/s1"))));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/x")), newSession(ACME, "agent-beta")));
    });

    it("blocks an admin whose token has no company", async function () {
      const db = as("admin-x", { admin: true });
      await assertFails(get(ref(db, path(ACME, "settings"))));
    });
  });

  describe("company settings and questions", function () {
    it("lets an agent read settings and question rules, but not change them", async function () {
      const db = agent1();
      await assertSucceeds(get(ref(db, path(ACME, "settings"))));
      await assertSucceeds(get(ref(db, path(ACME, "fieldRuleOverrides"))));
      await assertSucceeds(get(ref(db, path(ACME, "fieldDefinitions"))));
      await assertFails(set(ref(db, path(ACME, "settings/industry")), "telecom"));
      await assertFails(set(ref(db, path(ACME, "fieldRuleOverrides/email_address/required")), false));
      await assertFails(set(ref(db, path(ACME, "fieldDefinitions/extra")), { fieldId: "extra" }));
    });

    it("blocks a deactivated agent from reading settings", async function () {
      await assertFails(get(ref(agentOff(), path(ACME, "settings"))));
    });

    it("lets an admin set the industry to banking or telecom only", async function () {
      const db = adminAcme();
      await assertSucceeds(set(ref(db, path(ACME, "settings/industry")), "telecom"));
      await assertSucceeds(set(ref(db, path(ACME, "settings/industry")), "banking"));
      await assertFails(set(ref(db, path(ACME, "settings/industry")), "retail"));
    });

    it("lets an admin change required rules and add, edit and delete their own questions", async function () {
      const db = adminAcme();
      await assertSucceeds(set(ref(db, path(ACME, "fieldRuleOverrides/email_address/required")), false));
      await assertSucceeds(set(ref(db, path(ACME, "fieldDefinitions/extra")), { fieldId: "extra", fieldName: "Extra" }));
      await assertSucceeds(set(ref(db, path(ACME, "fieldDefinitions/extra")), { fieldId: "extra", fieldName: "Extra v2" }));
      await assertSucceeds(set(ref(db, path(ACME, "fieldDefinitions/extra")), null));
    });
  });

  describe("agent invitations", function () {
    it("lets an admin invite, edit and switch off an agent", async function () {
      const db = adminAcme();
      await assertSucceeds(get(ref(db, path(ACME, "agentInvitations"))));
      await assertSucceeds(
        set(ref(db, path(ACME, "agentInvitations/fresh_acme_com")), {
          email: "fresh@acme.com",
          displayName: "Fresh",
          isActive: true,
        }),
      );
      await assertSucceeds(set(ref(db, path(ACME, "agentInvitations/one_acme_com/isActive")), false));
    });

    it("rejects an invitation missing its email, name or active flag", async function () {
      await assertFails(set(ref(adminAcme(), path(ACME, "agentInvitations/bad")), { email: "bad@acme.com" }));
    });

    it("hides the invitation list from agents, but lets an agent read their own invitation", async function () {
      const db = agent1();
      await assertFails(get(ref(db, path(ACME, "agentInvitations"))));
      await assertSucceeds(get(ref(db, path(ACME, "agentInvitations/one_acme_com"))));
      await assertFails(get(ref(db, path(ACME, "agentInvitations/new_acme_com"))));
    });

    it("never lets an agent write an invitation", async function () {
      await assertFails(set(ref(agent1(), path(ACME, "agentInvitations/one_acme_com/isActive")), true));
      await assertFails(
        set(ref(agent1(), path(ACME, "agentInvitations/mine")), {
          email: "one@acme.com",
          displayName: "me",
          isActive: true,
        }),
      );
    });
  });

  describe("joining a company (membership)", function () {
    const joining = (extra = {}) => ({
      companyId: ACME,
      email: "new@acme.com",
      displayName: "New agent",
      isActive: true,
      invitationKey: "new_acme_com",
      ...extra,
    });
    const newAgent = () => as("agent-new", { email: "new@acme.com" });

    it("lets an invited agent join with the invited email", async function () {
      await assertSucceeds(set(ref(newAgent(), "companyMemberships/agent-new"), joining()));
    });

    it("rejects a join with no matching invitation", async function () {
      await assertFails(set(ref(newAgent(), "companyMemberships/agent-new"), joining({ invitationKey: "nope" })));
    });

    it("rejects a join using someone else's invitation", async function () {
      const thief = as("agent-thief", { email: "thief@acme.com" });
      await assertFails(
        set(ref(thief, "companyMemberships/agent-thief"), joining({ email: "thief@acme.com" })),
      );
      await assertFails(set(ref(thief, "companyMemberships/agent-thief"), joining()));
    });

    it("rejects a join to a company the invitation does not belong to", async function () {
      await assertFails(set(ref(newAgent(), "companyMemberships/agent-new"), joining({ companyId: BETA })));
    });

    it("rejects a join with a switched-off invitation", async function () {
      const paused = as("agent-paused", { email: "paused@acme.com" });
      await assertFails(
        set(
          ref(paused, "companyMemberships/agent-paused"),
          joining({ email: "paused@acme.com", invitationKey: "paused_acme_com" }),
        ),
      );
    });

    it("rejects joining under someone else's user id", async function () {
      await assertFails(set(ref(newAgent(), "companyMemberships/agent-1"), joining()));
    });

    it("rejects a membership that starts switched off", async function () {
      await assertFails(set(ref(newAgent(), "companyMemberships/agent-new"), joining({ isActive: false })));
    });

    it("rejects an incomplete membership", async function () {
      await assertFails(set(ref(newAgent(), "companyMemberships/agent-new"), { companyId: ACME }));
    });

    it("lets an agent read only their own membership", async function () {
      await assertSucceeds(get(ref(agent1(), "companyMemberships/agent-1")));
      await assertFails(get(ref(agent1(), "companyMemberships/agent-2")));
    });

    it("never lets an agent edit their own membership", async function () {
      await assertFails(set(ref(agentOff(), "companyMemberships/agent-off/isActive"), true));
      await assertFails(set(ref(agent1(), "companyMemberships/agent-1/companyId"), BETA));
    });

    it("lets an admin read and switch off agents in their own company only", async function () {
      await assertSucceeds(get(ref(adminAcme(), "companyMemberships/agent-1")));
      await assertSucceeds(set(ref(adminAcme(), "companyMemberships/agent-1/isActive"), false));
      await assertFails(get(ref(adminAcme(), "companyMemberships/agent-beta")));
      await assertFails(set(ref(adminAcme(), "companyMemberships/agent-beta/isActive"), false));
    });

    it("lets an admin list their own company's agents, and no other company's", async function () {
      const list = (db, company) =>
        get(query(ref(db, "companyMemberships"), orderByChild("companyId"), equalTo(company)));
      await assertSucceeds(list(adminAcme(), ACME));
      await assertFails(list(adminAcme(), BETA));
      await assertFails(get(ref(adminAcme(), "companyMemberships")));
      await assertFails(list(agent1(), ACME));
    });
  });

  describe("onboarding sessions", function () {
    it("lets an agent start a session in their own company as themselves", async function () {
      await assertSucceeds(set(ref(agent1(), path(ACME, "onboardingSessions/new")), newSession(ACME, "agent-1")));
    });

    it("lets an agent save consent time and agent with the session", async function () {
      await assertSucceeds(
        set(
          ref(agent1(), path(ACME, "onboardingSessions/new")),
          newSession(ACME, "agent-1", { consentAt: 1999, consentBy: "agent-1" }),
        ),
      );
    });

    it("rejects a session started as another agent", async function () {
      await assertFails(set(ref(agent1(), path(ACME, "onboardingSessions/new")), newSession(ACME, "agent-2")));
    });

    it("rejects a session that starts already completed", async function () {
      await assertFails(
        set(ref(agent1(), path(ACME, "onboardingSessions/new")), newSession(ACME, "agent-1", { status: "completed" })),
      );
    });

    it("rejects a session missing its company, agent or status", async function () {
      await assertFails(set(ref(agent1(), path(ACME, "onboardingSessions/new")), { agentId: "agent-1" }));
    });

    it("lets an agent save answers and finish their own session", async function () {
      const db = agent1();
      await assertSucceeds(set(ref(db, path(ACME, "onboardingSessions/s1/entries/full_name")), { value: "Mura Mi" }));
      await assertSucceeds(update(ref(db, path(ACME, "onboardingSessions/s1")), { status: "completed", completedAt: 3000 }));
    });

    it("freezes a completed session", async function () {
      const db = agent1();
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/done/entries/full_name")), { value: "Changed" }));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/done/status")), "in_progress"));
    });

    it("never lets a session change owner or company", async function () {
      const db = agent1();
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/agentId")), "agent-2"));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/companyId")), BETA));
    });

    it("keeps one agent out of another agent's session", async function () {
      const db = agent2();
      await assertFails(get(ref(db, path(ACME, "onboardingSessions/s1"))));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/entries/full_name")), { value: "x" }));
    });

    it("lets an agent list only their own sessions", async function () {
      const db = agent1();
      const mine = query(ref(db, path(ACME, "onboardingSessions")), orderByChild("agentId"), equalTo("agent-1"));
      const theirs = query(ref(db, path(ACME, "onboardingSessions")), orderByChild("agentId"), equalTo("agent-2"));
      await assertSucceeds(get(mine));
      await assertFails(get(theirs));
      await assertFails(get(ref(db, path(ACME, "onboardingSessions"))));
    });

    it("blocks a deactivated agent from their own sessions", async function () {
      const db = agentOff();
      await assertFails(get(ref(db, path(ACME, "onboardingSessions/off"))));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/off/entries/full_name")), { value: "x" }));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/again")), newSession(ACME, "agent-off")));
    });

    it("lets an admin read every session in their company, and never write one", async function () {
      const db = adminAcme();
      await assertSucceeds(get(ref(db, path(ACME, "onboardingSessions/s1"))));
      await assertSucceeds(get(query(ref(db, path(ACME, "onboardingSessions")), orderByChild("startedAt"))));
      await assertFails(get(ref(db, path(ACME, "onboardingSessions"))));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/entries/full_name")), { value: "x" }));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/made-by-admin")), newSession(ACME, "agent-1")));
    });
  });

  describe("everything else", function () {
    it("is closed by default", async function () {
      await assertFails(get(ref(adminAcme(), "somewhere/else")));
      await assertFails(set(ref(adminAcme(), "somewhere/else"), true));
      await assertFails(get(ref(adminAcme(), "companies")));
    });
  });
});
