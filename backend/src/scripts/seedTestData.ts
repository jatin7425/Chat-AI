import { auth, firestore } from "../firebaseAdmin";

const OWNER_EMAIL = process.env.SEED_OWNER_EMAIL;

async function main() {
  if (!OWNER_EMAIL) {
    throw new Error("Set SEED_OWNER_EMAIL to the Firebase Auth email to seed test data for.");
  }
  const user = await auth.getUserByEmail(OWNER_EMAIL);
  const uid = user.uid;
  const now = Date.now();

  const spaceRef = firestore.collection("spaces").doc();
  await spaceRef.set({
    id: spaceRef.id,
    ownerUid: uid,
    name: "Test Space",
    premise: "A quiet startup office where everyone is a little too online.",
    simDate: "2026-08-02",
    simStatus: "paused",
    personaCount: 1,
    createdAt: now,
    updatedAt: now,
    lastTickAt: 0,
  });

  const personaRef = spaceRef.collection("personas").doc();
  await personaRef.set({
    id: personaRef.id,
    spaceId: spaceRef.id,
    name: "Riya Kapoor",
    dob: "1998-04-12",
    gender: "female",
    relationshipToUser: "Co-founder",
    bio: "Sharp, sarcastic, drinks too much coffee.",
    background: "Met the user in college; joined the startup as the second hire.",
    mood: 20,
    aggressiveness: 30,
    appearance: {
      hairColor: "Black",
      hairStyle: "Shoulder length",
      eyeColor: "Brown",
      skinTone: "Olive",
      build: "Slim",
      height: "5'5\"",
      extraFeatures: "Wears round glasses",
    },
    relationshipsToOtherPersonas: {},
    emotionsTowardUser: { affection: 40, love: 0, lust: 0, trust: 50 },
    emotionsTowardPersonas: {},
    avatarStyle: "Avataaars (Modern)",
    avatarSeed: "Riya Kapoor",
    avatarImageUrl: "",
    chatBackgroundImageUrl: "",
    chatBackgroundOpacity: 1,
    portfolioImageUrls: [],
    createdAt: now,
    updatedAt: now,
  });

  console.log(`Created space ${spaceRef.id} ("Test Space") with persona ${personaRef.id} ("Riya Kapoor") for uid ${uid}`);
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
