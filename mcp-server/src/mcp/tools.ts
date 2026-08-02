import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { firestore } from "../firebaseAdmin";
import { buildGroupChatDoc, buildPersonaDoc, buildPlaceDoc, buildSpaceDoc } from "../firestoreSchemas";

function json(data: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }] };
}

function error(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

/** Re-derives ownership from Firestore itself -- never trusts a client-supplied owner field, same defensive pattern as assertOwnership() in backend/src/services/spacesService.ts. */
async function loadOwnedSpace(spaceId: string, uid: string) {
  const doc = await firestore.collection("spaces").doc(spaceId).get();
  if (!doc.exists) return { space: null, forbidden: false };
  const data = doc.data() as { ownerUid: string };
  if (data.ownerUid !== uid) return { space: null, forbidden: true };
  return { space: { id: doc.id, ...data }, forbidden: false };
}

const appearanceSchema = z
  .object({
    hairColor: z.string().optional(),
    hairStyle: z.string().optional(),
    eyeColor: z.string().optional(),
    skinTone: z.string().optional(),
    build: z.string().optional(),
    height: z.string().optional(),
    extraFeatures: z.string().optional(),
  })
  .optional();

/**
 * Registers every tool this MCP server exposes, scoped to a single already-authenticated `uid`.
 * A fresh McpServer (and thus a fresh call to this function) is built per HTTP request -- see
 * mcp/server.ts -- so `uid` is safely captured in closure rather than passed as a tool argument
 * a client could spoof.
 */
export function registerTools(server: McpServer, uid: string): void {
  server.registerTool(
    "list_spaces",
    {
      title: "List Spaces",
      description: "List every Space owned by the signed-in user.",
      inputSchema: {},
    },
    async () => {
      const snap = await firestore.collection("spaces").where("ownerUid", "==", uid).get();
      return json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    }
  );

  server.registerTool(
    "get_space",
    {
      title: "Get Space",
      description: "Get a single Space's details by id.",
      inputSchema: { spaceId: z.string() },
    },
    async ({ spaceId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      return json(space);
    }
  );

  server.registerTool(
    "create_space",
    {
      title: "Create Space",
      description:
        "Create a new Space (a story world). Starts paused -- it will not begin the live simulation loop.",
      inputSchema: { name: z.string().min(1), premise: z.string().optional() },
    },
    async ({ name, premise }) => {
      const ref = firestore.collection("spaces").doc();
      const doc = buildSpaceDoc(ref.id, uid, name, premise ?? "");
      await ref.set(doc);
      return json(doc);
    }
  );

  server.registerTool(
    "list_personas",
    {
      title: "List Personas",
      description: "List every Persona in a Space.",
      inputSchema: { spaceId: z.string() },
    },
    async ({ spaceId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const snap = await firestore.collection("spaces").doc(spaceId).collection("personas").get();
      return json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    }
  );

  server.registerTool(
    "get_persona",
    {
      title: "Get Persona",
      description: "Get a single Persona's details.",
      inputSchema: { spaceId: z.string(), personaId: z.string() },
    },
    async ({ spaceId, personaId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const doc = await firestore.collection("spaces").doc(spaceId).collection("personas").doc(personaId).get();
      if (!doc.exists) return error(`Persona ${personaId} not found in space ${spaceId}.`);
      return json({ id: doc.id, ...doc.data() });
    }
  );

  server.registerTool(
    "create_persona",
    {
      title: "Create Persona",
      description: "Create a new Persona inside a Space.",
      inputSchema: {
        spaceId: z.string(),
        name: z.string().min(1),
        dob: z.string().optional(),
        gender: z.string().optional(),
        relationshipToUser: z.string().optional(),
        bio: z.string().optional(),
        background: z.string().optional(),
        appearance: appearanceSchema,
      },
    },
    async ({ spaceId, name, dob, gender, relationshipToUser, bio, background, appearance }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);

      const spaceRef = firestore.collection("spaces").doc(spaceId);
      const personaRef = spaceRef.collection("personas").doc();
      const doc = buildPersonaDoc(personaRef.id, name, {
        dob,
        gender,
        relationshipToUser,
        bio,
        background,
        appearance,
      });

      const batch = firestore.batch();
      batch.set(personaRef, doc);
      batch.update(spaceRef, {
        personaCount: ((space as { personaCount?: number }).personaCount ?? 0) + 1,
        updatedAt: Date.now(),
      });
      await batch.commit();

      return json(doc);
    }
  );

  server.registerTool(
    "list_places",
    {
      title: "List Places",
      description: "List every Place in a Space.",
      inputSchema: { spaceId: z.string() },
    },
    async ({ spaceId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const snap = await firestore.collection("spaces").doc(spaceId).collection("places").get();
      return json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    }
  );

  server.registerTool(
    "get_place",
    {
      title: "Get Place",
      description: "Get a single Place's details.",
      inputSchema: { spaceId: z.string(), placeId: z.string() },
    },
    async ({ spaceId, placeId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const doc = await firestore.collection("spaces").doc(spaceId).collection("places").doc(placeId).get();
      if (!doc.exists) return error(`Place ${placeId} not found in space ${spaceId}.`);
      return json({ id: doc.id, ...doc.data() });
    }
  );

  server.registerTool(
    "create_place",
    {
      title: "Create Place",
      description: "Create a new Place inside a Space.",
      inputSchema: { spaceId: z.string(), name: z.string().min(1), description: z.string().optional() },
    },
    async ({ spaceId, name, description }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const ref = firestore.collection("spaces").doc(spaceId).collection("places").doc();
      const doc = buildPlaceDoc(ref.id, name, description ?? "");
      await ref.set(doc);
      return json(doc);
    }
  );

  server.registerTool(
    "list_group_chats",
    {
      title: "List Group Chats",
      description: "List every group chat in a Space.",
      inputSchema: { spaceId: z.string() },
    },
    async ({ spaceId }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);
      const snap = await firestore.collection("spaces").doc(spaceId).collection("groupChats").get();
      return json(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    }
  );

  server.registerTool(
    "create_group_chat",
    {
      title: "Create Group Chat",
      description: "Create a new group chat containing a set of existing Personas in a Space.",
      inputSchema: { spaceId: z.string(), name: z.string().min(1), personaIds: z.array(z.string()).min(1) },
    },
    async ({ spaceId, name, personaIds }) => {
      const { space, forbidden } = await loadOwnedSpace(spaceId, uid);
      if (forbidden) return error(`Space ${spaceId} is not owned by this user.`);
      if (!space) return error(`Space ${spaceId} not found.`);

      const personasRef = firestore.collection("spaces").doc(spaceId).collection("personas");
      const checks = await Promise.all(personaIds.map((id) => personasRef.doc(id).get()));
      const missing = checks.filter((d) => !d.exists).map((d) => d.id);
      if (missing.length > 0) {
        return error(`These persona ids don't exist in space ${spaceId}: ${missing.join(", ")}`);
      }

      const ref = firestore.collection("spaces").doc(spaceId).collection("groupChats").doc();
      const doc = buildGroupChatDoc(ref.id, name, personaIds);
      await ref.set(doc);
      return json(doc);
    }
  );
}
