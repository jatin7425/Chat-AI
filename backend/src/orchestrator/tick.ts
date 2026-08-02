import { firestore } from "../firebaseAdmin";

/**
 * The core orchestrator tick -- for each running Space, advances its Story Feed tasks
 * (simulates NPC<->NPC exchanges, handles missing-persona blocking, reports back to the user,
 * sends notifications). Full logic lands in Phase 4; this is a structural stub so the
 * scheduler has something real to call.
 */
export async function tick(): Promise<void> {
  const runningSpaces = await firestore.collection("spaces").where("simStatus", "==", "running").get();
  for (const doc of runningSpaces.docs) {
    // Phase 4: pull due storyFeed tasks for doc.id and advance them.
    console.log(`[tick] space ${doc.id} is running (task advancement not yet implemented)`);
  }
}
