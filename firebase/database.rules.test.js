// Tests for firebase/database.rules.json, run against the Firebase emulator:
//   cd firebase && npm install && npm run test:database-rules
//
// Two companies ("acme" and "beta") are set up, so each test can check both what a
// person may do in their own company and that they are walled out of the other. One sales
// agent ("agent-both") works for both companies.
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
  const member = (email, key, isActive = true) => ({
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
    // Before agents could work for several companies: kept only so the app can move them.
    companyMemberships: {
      "agent-1": { companyId: ACME, ...member("one@acme.com", "one_acme_com") },
    },
    agentCompanies: {
      "agent-1": { [ACME]: true },
      "agent-2": { [ACME]: true },
      "agent-off": { [ACME]: true },
      "agent-beta": { [BETA]: true },
      "agent-both": { [ACME]: true, [BETA]: true },
    },
    companies: {
      [ACME]: {
        settings: {
          industry: "banking",
          shareForTraining: true,
          displayName: "Acme Finance",
          agentBrief: "Open accounts for walk-in clients.",
        },
        agentInvitations: {
          one_acme_com: { email: "one@acme.com", displayName: "one", isActive: true },
          new_acme_com: { email: "new@acme.com", displayName: "new", isActive: true },
          paused_acme_com: { email: "paused@acme.com", displayName: "paused", isActive: false },
          both_mail_com: { email: "both@mail.com", displayName: "both", isActive: true },
        },
        members: {
          "agent-1": member("one@acme.com", "one_acme_com"),
          "agent-2": member("two@acme.com", "two_acme_com"),
          "agent-off": member("off@acme.com", "off_acme_com", false),
          "agent-both": member("both@mail.com", "both_mail_com"),
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
        agentInvitations: {
          both_mail_com: { email: "both@mail.com", displayName: "both", isActive: true },
          new_acme_com: { email: "new@acme.com", displayName: "new", isActive: true },
        },
        members: {
          "agent-beta": member("bee@beta.com", "bee_beta_com"),
          "agent-both": member("both@mail.com", "both_mail_com"),
        },
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
  const agent1 = () => as("agent-1", { email: "one@acme.com", email_verified: true });
  const agent2 = () => as("agent-2", { email: "two@acme.com", email_verified: true });
  const agentOff = () => as("agent-off", { email: "off@acme.com", email_verified: true });
  const agentBeta = () => as("agent-beta", { email: "bee@beta.com", email_verified: true });
  const agentBoth = () => as("agent-both", { email: "both@mail.com", email_verified: true });
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
      email: "new@acme.com",
      displayName: "New agent",
      isActive: true,
      invitationKey: "new_acme_com",
      ...extra,
    });
    const newAgent = () => as("agent-new", { email: "new@acme.com", email_verified: true });
    // Joining writes the membership and the agent's own list of companies together.
    const join = (db, company, uid, membership) =>
      update(ref(db), {
        [`companies/${company}/members/${uid}`]: membership,
        [`agentCompanies/${uid}/${company}`]: true,
      });

    it("lets an invited agent join with the invited email", async function () {
      await assertSucceeds(join(newAgent(), ACME, "agent-new", joining()));
    });

    it("lets one agent join a second company that also invited them", async function () {
      await assertSucceeds(join(newAgent(), ACME, "agent-new", joining()));
      await assertSucceeds(join(newAgent(), BETA, "agent-new", joining()));
      await assertSucceeds(get(ref(newAgent(), "agentCompanies/agent-new")));
    });

    it("rejects a join until the agent has verified their email", async function () {
      const unverified = as("agent-new", { email: "new@acme.com", email_verified: false });
      await assertFails(join(unverified, ACME, "agent-new", joining()));
    });

    it("rejects a join with no matching invitation", async function () {
      await assertFails(join(newAgent(), ACME, "agent-new", joining({ invitationKey: "nope" })));
    });

    it("rejects a join using someone else's invitation", async function () {
      const thief = as("agent-thief", { email: "thief@acme.com", email_verified: true });
      await assertFails(join(thief, ACME, "agent-thief", joining({ email: "thief@acme.com" })));
      await assertFails(join(thief, ACME, "agent-thief", joining()));
    });

    it("rejects a join to a company that did not invite the agent", async function () {
      const one = as("agent-x", { email: "one@acme.com", email_verified: true });
      await assertFails(join(one, BETA, "agent-x", joining({ email: "one@acme.com", invitationKey: "one_acme_com" })));
    });

    it("rejects a join with a switched-off invitation", async function () {
      const paused = as("agent-paused", { email: "paused@acme.com", email_verified: true });
      await assertFails(
        join(paused, ACME, "agent-paused", joining({ email: "paused@acme.com", invitationKey: "paused_acme_com" })),
      );
    });

    it("rejects joining under someone else's user id", async function () {
      await assertFails(join(newAgent(), ACME, "agent-1", joining()));
    });

    it("rejects a membership that starts switched off, or is incomplete", async function () {
      await assertFails(join(newAgent(), ACME, "agent-new", joining({ isActive: false })));
      await assertFails(join(newAgent(), ACME, "agent-new", { email: "new@acme.com" }));
    });

    it("rejects adding a company to the agent's list without joining it", async function () {
      await assertFails(set(ref(newAgent(), `agentCompanies/agent-new/${ACME}`), true));
      await assertFails(set(ref(agent1(), `agentCompanies/agent-1/${BETA}`), true));
    });

    it("lets an agent read only their own memberships and list", async function () {
      await assertSucceeds(get(ref(agent1(), path(ACME, "members/agent-1"))));
      await assertSucceeds(get(ref(agent1(), "agentCompanies/agent-1")));
      await assertSucceeds(get(ref(agent1(), "companyMemberships/agent-1")));
      await assertFails(get(ref(agent1(), path(ACME, "members/agent-2"))));
      await assertFails(get(ref(agent1(), path(ACME, "members"))));
      await assertFails(get(ref(agent1(), "agentCompanies/agent-2")));
    });

    it("never lets an agent edit their own membership", async function () {
      await assertFails(set(ref(agentOff(), path(ACME, "members/agent-off/isActive")), true));
      await assertFails(set(ref(agent1(), "companyMemberships/agent-1/isActive"), false));
    });

    it("lets an admin list and switch off agents in their own company only", async function () {
      await assertSucceeds(get(ref(adminAcme(), path(ACME, "members"))));
      await assertSucceeds(set(ref(adminAcme(), path(ACME, "members/agent-1/isActive")), false));
      await assertFails(set(ref(adminAcme(), path(ACME, "members/agent-1/email")), "other@acme.com"));
      await assertFails(set(ref(adminAcme(), path(ACME, "members/made-up")), joining()));
      await assertFails(get(ref(adminAcme(), path(BETA, "members"))));
      await assertFails(set(ref(adminAcme(), path(BETA, "members/agent-both/isActive")), false));
    });

    it("keeps an agent of two companies working in both, and switched off in one only", async function () {
      await assertSucceeds(set(ref(agentBoth(), path(ACME, "onboardingSessions/a")), newSession(ACME, "agent-both")));
      await assertSucceeds(set(ref(agentBoth(), path(BETA, "onboardingSessions/b")), newSession(BETA, "agent-both")));
      await assertSucceeds(set(ref(adminBeta(), path(BETA, "members/agent-both/isActive")), false));
      await assertFails(set(ref(agentBoth(), path(BETA, "onboardingSessions/c")), newSession(BETA, "agent-both")));
      await assertSucceeds(set(ref(agentBoth(), path(ACME, "onboardingSessions/d")), newSession(ACME, "agent-both")));
    });
  });

  describe("the company's message to its agents", function () {
    it("lets the company's admin set the name and message, within a length limit", async function () {
      await assertSucceeds(set(ref(adminAcme(), path(ACME, "settings/displayName")), "Acme Finance Ltd"));
      await assertSucceeds(set(ref(adminAcme(), path(ACME, "settings/agentBrief")), "Check the ID first."));
      await assertFails(set(ref(adminAcme(), path(ACME, "settings/agentBrief")), "x".repeat(1501)));
      await assertFails(set(ref(adminAcme(), path(ACME, "settings/displayName")), 42));
      await assertFails(set(ref(adminBeta(), path(ACME, "settings/agentBrief")), "Hijacked"));
    });

    it("lets the company's active agents read it, and nobody else", async function () {
      await assertSucceeds(get(ref(agent1(), path(ACME, "settings/agentBrief"))));
      await assertSucceeds(get(ref(agentBoth(), path(BETA, "settings"))));
      await assertFails(get(ref(agentBeta(), path(ACME, "settings/agentBrief"))));
      await assertFails(get(ref(agentOff(), path(ACME, "settings/agentBrief"))));
      await assertFails(set(ref(agent1(), path(ACME, "settings/agentBrief")), "Changed"));
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
          newSession(ACME, "agent-1", { consentAt: 1999 }),
        ),
      );
    });

    it("blocks a member whose email is not verified from saving sessions", async function () {
      const unverified = as("agent-1", { email: "one@acme.com" });
      await assertFails(set(ref(unverified, path(ACME, "onboardingSessions/new")), newSession(ACME, "agent-1")));
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

    it("lets an agent cancel their own unfinished session, which then cannot change", async function () {
      const db = agent1();
      await assertFails(update(ref(agent2(), path(ACME, "onboardingSessions/s1")), { status: "abandoned" }));
      await assertSucceeds(update(ref(db, path(ACME, "onboardingSessions/s1")), { status: "abandoned", abandonedAt: 3000 }));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/status")), "in_progress"));
      await assertFails(set(ref(db, path(ACME, "onboardingSessions/s1/entries/full_name")), { value: "Late" }));
      await assertFails(update(ref(db, path(ACME, "onboardingSessions/done")), { status: "abandoned" }));
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

  describe("anonymised steps for improving the system", function () {
    const RECORD = "T" + "0123456789ABCDEF".repeat(2);
    const record = (extra = {}) => ({
      companyId: ACME,
      industry: "banking",
      context: "individual",
      agentCode: "AGENT-0123456789AB",
      fieldCount: 20,
      startedAt: 1000,
      status: "in_progress",
      updatedAt: 1000,
      ...extra,
    });
    const step = { fieldId: "full_name", passed: true, t: 2000 };
    const training = (rest = "") => `trainingData/${RECORD}${rest}`;

    it("lets only the company's admin turn sharing on or off", async function () {
      await assertSucceeds(set(ref(adminAcme(), path(ACME, "settings/shareForTraining")), false));
      await assertFails(set(ref(adminAcme(), path(ACME, "settings/shareForTraining")), "yes"));
      await assertFails(set(ref(adminBeta(), path(ACME, "settings/shareForTraining")), true));
      await assertFails(set(ref(agent1(), path(ACME, "settings/shareForTraining")), true));
    });

    it("lets an active agent of a sharing company add a record and its steps", async function () {
      const db = agent1();
      await assertSucceeds(set(ref(db, training()), record()));
      await assertSucceeds(update(ref(db, training()), { "steps/s1": step, updatedAt: 2000 }));
      await assertSucceeds(update(ref(db, training()), { status: "completed", updatedAt: 3000 }));
    });

    it("refuses records from companies that do not share, switched-off agents and admins", async function () {
      await assertFails(set(ref(agentBeta(), training()), record()));
      await assertFails(set(ref(agentBeta(), training()), record({ companyId: BETA })));
      await assertFails(set(ref(agentBoth(), training()), record({ companyId: BETA })));
      await assertFails(set(ref(agentOff(), training()), record()));
      await assertFails(set(ref(adminAcme(), training()), record()));
      await assertFails(set(ref(signedOut(), training()), record()));
    });

    it("refuses anything that could identify a person, and badly formed records", async function () {
      const db = agent1();
      await assertFails(set(ref(db, training()), record({ fullName: "Jane Mika" })));
      await assertFails(set(ref(db, training()), record({ agentCode: "one@acme.com" })));
      await assertFails(set(ref(db, training()), { ...record(), steps: { s1: { ...step, value: "0700000000" } } }));
      await assertFails(set(ref(db, "trainingData/session-1"), record()));
      await assertFails(set(ref(db, training()), { industry: "banking" }));
      await assertFails(set(ref(db, training()), (({ companyId, ...withoutCompany }) => withoutCompany)(record())));
    });

    it("lets nobody read the records or delete them through the app", async function () {
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await set(ref(context.database(), training()), record());
      });
      await assertFails(get(ref(agent1(), training())));
      await assertFails(get(ref(adminAcme(), training())));
      await assertFails(get(ref(adminAcme(), "trainingData")));
      await assertFails(set(ref(agent1(), training()), null));
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
