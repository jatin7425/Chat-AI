import { firestore } from "../firebaseAdmin";

const SPACE_ID = "6AstmNpAAJutOJ9yCzNF"; // "Test Space", created earlier by seedTestData.ts

async function main() {
  const spaceRef = firestore.collection("spaces").doc(SPACE_ID);
  const spaceSnap = await spaceRef.get();
  if (!spaceSnap.exists) throw new Error(`Space ${SPACE_ID} not found -- run seedTestData.ts first`);

  const now = Date.now();
  const personasRef = spaceRef.collection("personas");
  const placesRef = spaceRef.collection("places");

  // Places first, so persona docs can reference real placeIds.
  const places = [
    { name: "Open Office Floor", description: "Rows of desks, standing meetings, way too much cold brew." },
    { name: "Conference Room", description: "Glass-walled room where the hard conversations happen." },
    { name: "Cafe Down the Street", description: "Where half the real decisions actually get made." },
  ].map((p) => ({ id: placesRef.doc().id, ...p }));

  const placeBatch = firestore.batch();
  places.forEach((p) => {
    placeBatch.set(placesRef.doc(p.id), {
      id: p.id,
      name: p.name,
      description: p.description,
      createdAt: now,
      updatedAt: now,
    });
  });
  await placeBatch.commit();

  const [office, conferenceRoom, cafe] = places;

  // Pre-generate IDs so cross-persona relationship maps can reference each other.
  const ids = {
    arjun: personasRef.doc().id,
    sanya: personasRef.doc().id,
    karan: personasRef.doc().id,
    priya: personasRef.doc().id,
    devansh: personasRef.doc().id,
    neha: personasRef.doc().id,
  };

  const emptyEmotions = { affection: 0, love: 0, lust: 0, trust: 0 };
  const baseAppearance = { hairColor: "", hairStyle: "", eyeColor: "", skinTone: "", build: "", height: "", extraFeatures: "" };

  const personas = [
    {
      id: ids.arjun,
      name: "Arjun Mehta",
      dob: "1990-02-18",
      gender: "male",
      relationshipToUser: "Co-founder & CEO",
      bio: "Relentlessly optimistic, terrible at delegating.",
      background: "Started the company with you in a shared apartment five years ago.",
      mood: 15,
      aggressiveness: 40,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.sanya]: "Trusts her judgment completely",
        [ids.karan]: "Occasionally clashes over pricing",
        [ids.priya]: "Relies on her for hard calls",
        [ids.devansh]: "Admires his taste, ignores his timelines",
        [ids.neha]: "Barely knows her yet",
      },
      emotionsTowardUser: { affection: 60, love: 0, lust: 0, trust: 70 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Arjun Mehta",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: conferenceRoom.id,
      currentPlaceName: conferenceRoom.name,
    },
    {
      id: ids.sanya,
      name: "Sanya Verma",
      dob: "1992-07-09",
      gender: "female",
      relationshipToUser: "Co-founder & CTO",
      bio: "Blunt, brilliant, hates status meetings.",
      background: "Joined as the first engineering hire, now runs the whole stack.",
      mood: -10,
      aggressiveness: 55,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.arjun]: "Respects him but pushes back often",
        [ids.karan]: "Thinks sales overpromises constantly",
        [ids.priya]: "Friendly, mostly avoids HR stuff",
        [ids.devansh]: "Good working relationship",
        [ids.neha]: "Mentoring her",
      },
      emotionsTowardUser: { affection: 45, love: 0, lust: 0, trust: 65 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Sanya Verma",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: office.id,
      currentPlaceName: office.name,
    },
    {
      id: ids.karan,
      name: "Karan Malhotra",
      dob: "1988-11-30",
      gender: "male",
      relationshipToUser: "Head of Sales",
      bio: "Charming, competitive, lives in his inbox.",
      background: "Hired to build the sales team from scratch two years ago.",
      mood: 25,
      aggressiveness: 50,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.arjun]: "Wants more autonomy on deals",
        [ids.sanya]: "Frustrated by engineering timelines",
        [ids.priya]: "Easygoing",
        [ids.devansh]: "Wants flashier sales decks",
        [ids.neha]: "Hasn't met her",
      },
      emotionsTowardUser: { affection: 30, love: 0, lust: 0, trust: 40 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Karan Malhotra",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: cafe.id,
      currentPlaceName: cafe.name,
    },
    {
      id: ids.priya,
      name: "Priya Nair",
      dob: "1994-03-22",
      gender: "female",
      relationshipToUser: "HR Manager",
      bio: "Warm, organized, the person everyone actually trusts.",
      background: "Joined a year ago after the team grew past 'we all know everything about each other'.",
      mood: 35,
      aggressiveness: 15,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.arjun]: "Direct reporting line, good rapport",
        [ids.sanya]: "Respects her boundaries",
        [ids.karan]: "Keeps an eye on his team's burnout",
        [ids.devansh]: "Friendly",
        [ids.neha]: "Her main point of contact",
      },
      emotionsTowardUser: { affection: 50, love: 0, lust: 0, trust: 75 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Priya Nair",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: office.id,
      currentPlaceName: office.name,
    },
    {
      id: ids.devansh,
      name: "Devansh Rao",
      dob: "1996-09-14",
      gender: "male",
      relationshipToUser: "Lead Designer",
      bio: "Quiet, perfectionist, headphones always on.",
      background: "Freelanced for the company for a year before joining full-time.",
      mood: 5,
      aggressiveness: 20,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.arjun]: "Wishes he had more design input upstream",
        [ids.sanya]: "Solid collaborator",
        [ids.karan]: "Tired of last-minute requests",
        [ids.priya]: "Comfortable with her",
        [ids.neha]: "Showing her the ropes",
      },
      emotionsTowardUser: { affection: 35, love: 0, lust: 0, trust: 55 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Devansh Rao",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: office.id,
      currentPlaceName: office.name,
    },
    {
      id: ids.neha,
      name: "Neha Kulkarni",
      dob: "2002-05-05",
      gender: "female",
      relationshipToUser: "Intern",
      bio: "Eager, nervous, asks great questions.",
      background: "Three weeks into her first internship, still learning where everything is.",
      mood: 20,
      aggressiveness: 10,
      appearance: baseAppearance,
      relationshipsToOtherPersonas: {
        [ids.arjun]: "In awe of him",
        [ids.sanya]: "Grateful for the mentorship",
        [ids.karan]: "Hasn't interacted much",
        [ids.priya]: "Goes to her with questions",
        [ids.devansh]: "Learning design basics from him",
      },
      emotionsTowardUser: { affection: 20, love: 0, lust: 0, trust: 30 },
      emotionsTowardPersonas: {},
      avatarStyle: "Avataaars (Modern)",
      avatarSeed: "Neha Kulkarni",
      avatarImageUrl: "",
      chatBackgroundImageUrl: "",
      chatBackgroundOpacity: 1,
      portfolioImageUrls: [],
      currentPlaceId: office.id,
      currentPlaceName: office.name,
    },
  ];

  const personaBatch = firestore.batch();
  personas.forEach((p) => {
    personaBatch.set(personasRef.doc(p.id), { ...p, createdAt: now, updatedAt: now });
  });
  await personaBatch.commit();

  await spaceRef.update({
    personaCount: (spaceSnap.data()?.personaCount ?? 0) + personas.length,
    updatedAt: now,
  });

  await spaceRef.collection("userCharacter").doc("profile").set({
    name: "Jatin Vishwakarma",
    dob: "2007-05-15", // 19 as of the space's simDate (2026-08-02)
    background: "19-year-old co-owner of the company alongside Arjun and Sanya -- youngest person in every board meeting.",
    appearance: baseAppearance,
    currentPlaceId: conferenceRoom.id,
    currentPlaceName: conferenceRoom.name,
    updatedAt: now,
  });

  console.log(`Added ${personas.length} personas and ${places.length} places to Test Space (${SPACE_ID}).`);
  console.log("Personas:", personas.map((p) => `${p.name} (${p.id})`).join(", "));
  console.log("Places:", places.map((p) => `${p.name} (${p.id})`).join(", "));
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
