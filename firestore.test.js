const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "businesskingscopia@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Users: unauthenticated cannot read/write; owner can create/update own profile; cannot self-escalate role", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  const now = pastDate(3000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "user",
      status: "active",
      notificationsEnabled: true,
      createdAt: now,
      lastLoginAt: now,
      updatedAt: now,
    })
  );

  // Ghost field & role escalation blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read blocked
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Admin security: email spoofing (email_verified=false) is rejected; verified bootstrap admin succeeds", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const now = pastDate(2000);
  await assertFails(
    spoofDb.collection("categories").doc("cat_tools").set({
      name: "Tools",
      slug: "tools",
      iconName: "build",
      gameCategory: false,
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("categories").doc("cat_tools").set({
      name: "Tools",
      slug: "tools",
      iconName: "build",
      gameCategory: false,
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Public unauthenticated read of categories succeeds
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("categories").get());
});

test("Developer & Application lifecycle: registration, approval, APK submission, self-publish denial, admin publish, public browse", async () => {
  const now = pastDate(5000);
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@dev.io",
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  // Seed category
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_prod").set({
      name: "Productivity",
      slug: "productivity",
      iconName: "work",
      gameCategory: false,
      sortOrder: 1,
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice registers as developer (pending)
  await assertSucceeds(
    aliceDb.collection("developers").doc(ALICE_UID).set({
      developerId: ALICE_UID,
      developerName: "Alice Studio",
      bio: "Indie Android tools",
      contactEmail: "alice@dev.io",
      status: "pending",
      totalDownloads: 0,
      publishedAppsCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unapproved developer cannot submit application
  const baseApp = {
    developerId: ALICE_UID,
    developerName: "Alice Studio",
    appName: "FocusNote",
    packageName: "io.alice.focusnote",
    categoryId: "cat_prod",
    categoryName: "Productivity",
    gameApp: false,
    shortDescription: "Minimal offline markdown notes",
    fullDescription: "A fast, clean markdown note-taking tool built for Android.",
    latestVersionName: "1.0.0",
    latestVersionCode: 1,
    apkUrl: "https://example.com/focusnote-1.0.0.apk",
    apkSizeBytes: 2450000,
    apkSha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    signingCertSha256: "0336a266abed9ffa7344724066822308f767b891",
    privacyPolicyUrl: "https://alice.dev.io/privacy",
    contactEmail: "alice@dev.io",
    status: "pending_review",
    featured: false,
    editorsPick: false,
    recommended: false,
    averageRating: 0.0,
    totalRatings: 0,
    downloadCount: 0,
    createdAt: now,
    updatedAt: now,
  };

  await assertFails(aliceDb.collection("applications").doc("app_focus").set(baseApp));

  // Admin approves Alice as developer
  await assertSucceeds(
    adminDb.collection("developers").doc(ALICE_UID).update({
      status: "approved",
      updatedAt: pastDate(2000),
    })
  );

  // Approved developer cannot self-publish directly
  await assertFails(
    aliceDb.collection("applications").doc("app_focus").set({
      ...baseApp,
      status: "published",
    })
  );

  // Approved developer submits pending_review app
  await assertSucceeds(aliceDb.collection("applications").doc("app_focus").set(baseApp));

  // Create app_version in pending_review
  await assertSucceeds(
    aliceDb.collection("app_versions").doc("ver_1").set({
      appId: "app_focus",
      developerId: ALICE_UID,
      packageName: "io.alice.focusnote",
      versionName: "1.0.0",
      versionCode: 1,
      apkUrl: "https://example.com/focusnote-1.0.0.apk",
      apkSizeBytes: 2450000,
      apkSha256: baseApp.apkSha256,
      signingCertSha256: baseApp.signingCertSha256,
      whatsNew: "Initial release",
      minSdkVersion: 24,
      targetSdkVersion: 36,
      securityScanStatus: "pending",
      status: "pending_review",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unauthenticated user cannot see pending_review app
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("applications").doc("app_focus").get());
  await assertFails(unauthDb.collection("applications").get());

  // Admin publishes app & version
  await assertSucceeds(
    adminDb.collection("applications").doc("app_focus").update({
      status: "published",
      publishedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    adminDb.collection("app_versions").doc("ver_1").update({
      status: "published",
      securityScanStatus: "verified",
      updatedAt: pastDate(1000),
    })
  );

  // Public filtered query for published apps succeeds; unfiltered fails
  await assertSucceeds(
    unauthDb.collection("applications").where("status", "==", "published").get()
  );
  await assertFails(unauthDb.collection("applications").get());
});

test("User downloads, favorites, and single-review-per-app enforcement", async () => {
  const now = pastDate(5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("applications").doc("app_1").set({
      developerId: ALICE_UID,
      developerName: "Alice Studio",
      appName: "FocusNote",
      packageName: "io.alice.focusnote",
      categoryId: "cat_prod",
      categoryName: "Productivity",
      gameApp: false,
      shortDescription: "Minimal notes",
      fullDescription: "Full markdown notes application.",
      latestVersionName: "1.0.0",
      latestVersionCode: 1,
      apkUrl: "https://example.com/app.apk",
      apkSizeBytes: 1000,
      apkSha256: "abc",
      signingCertSha256: "def",
      privacyPolicyUrl: "https://example.com/privacy",
      contactEmail: "alice@dev.io",
      status: "published",
      featured: false,
      editorsPick: false,
      recommended: false,
      averageRating: 0.0,
      totalRatings: 0,
      downloadCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Bob creates a download record under /users/bob/downloads
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("downloads").doc("dl_1").set({
      userId: BOB_UID,
      appId: "app_1",
      versionId: "ver_1",
      appName: "FocusNote",
      packageName: "io.alice.focusnote",
      versionName: "1.0.0",
      versionCode: 1,
      status: "downloading",
      bytesDownloaded: 250,
      totalBytes: 1000,
      apkUrl: "https://example.com/app.apk",
      sha256: "abc",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot read Bob's downloads
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("downloads").get()
  );

  // Bob submits review using deterministic ID app_1_bob_456
  await assertSucceeds(
    bobDb.collection("reviews").doc(`app_1_${BOB_UID}`).set({
      appId: "app_1",
      userId: BOB_UID,
      userName: "Bob",
      rating: 5,
      comment: "Clean and fast!",
      helpfulCount: 0,
      status: "approved",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Duplicate review with arbitrary ID rejected
  await assertFails(
    bobDb.collection("reviews").doc("arbitrary_id").set({
      appId: "app_1",
      userId: BOB_UID,
      userName: "Bob",
      rating: 5,
      comment: "Spam duplicate!",
      helpfulCount: 0,
      status: "approved",
      createdAt: now,
      updatedAt: now,
    })
  );
});
