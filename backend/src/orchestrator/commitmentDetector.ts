import { chatComplete } from "../llm/llmClient";

export interface CommitmentResult {
  commits: boolean;
  targetName: string; // a specific other named person, blank if none
  topic: string;
  /** A milestone-worthy event that just happened (first date, first kiss/intimacy, a big
   *  reveal, a breakup, etc.) -- distinct from "commits", which is about a FUTURE action. This
   *  is about something significant that ALREADY occurred and should be remembered long-term. */
  isMilestone: boolean;
  milestoneNote: string;
}

const NO_COMMITMENT: CommitmentResult = {
  commits: false,
  targetName: "",
  topic: "",
  isMilestone: false,
  milestoneNote: "",
};

/**
 * After a persona replies (direct chat, group chat, or an NPC<->NPC tick exchange), runs one
 * combined classification covering two different things (kept in one call to avoid tripling LLM
 * spend per message on top of place detection):
 *
 * 1. Commitments -- does this reply commit the persona to a FUTURE action? Either reaching out to
 *    a specific named person ("I'll check with Sanya"), or a bare commitment with no named target
 *    ("I'll get back to you on this"). This is what makes claims become reality: the orchestrator
 *    tick later either simulates the named handoff, or has the SAME persona follow up once the
 *    tick loop gets to it -- without this, a persona could promise something and the story would
 *    just never follow through, which is the single biggest thing breaking immersion.
 *
 * 2. Milestones -- did something significant just HAPPEN (not a future promise) that the persona
 *    should remember long-term regardless of the app's fixed size recent-activity window -- a
 *    first date, a first kiss/intimacy, a breakup, a major reveal? Written to coreMemories.
 */
export async function detectCommitment(
  replyText: string,
  llmConfig: { baseUrl: string; model: string }
): Promise<CommitmentResult> {
  const systemPrompt =
    "You analyze one message from a roleplay story for two separate things. Respond with ONLY compact JSON: " +
    '{"commits": true|false, "targetName": string, "topic": string, "isMilestone": true|false, "milestoneNote": string}. ' +
    "COMMITS: set true if the speaker just committed, on their own initiative, to DOING something in the future -- " +
    "telling/informing/contacting/checking with someone, following up, getting back to someone, completing a task, etc. " +
    "If they named a SPECIFIC other person (not the one they're replying to) who this involves, put that person's name " +
    "exactly as mentioned in targetName (a role/title like \"the CTO\" counts as naming them); otherwise leave targetName blank. " +
    "topic is a short (<15 word) description of what they committed to. " +
    "ISMILESTONE: set true if something SIGNIFICANT just happened in this message (already occurred, not a future promise) " +
    "that this character would remember for a long time -- e.g. a first date, a first kiss or intimate moment, a breakup, " +
    "a major emotional reveal, a big relationship shift. milestoneNote is a short first-person memory note as this " +
    "character would remember it (e.g. \"Went on our first date together\"). " +
    "If neither applies, respond {\"commits\": false, \"targetName\": \"\", \"topic\": \"\", \"isMilestone\": false, \"milestoneNote\": \"\"}.";

  try {
    const raw = await chatComplete(
      [
        { role: "system", content: systemPrompt },
        { role: "user", content: replyText },
      ],
      { baseUrl: llmConfig.baseUrl, model: llmConfig.model, temperature: 0 }
    );

    const jsonMatch = raw.match(/\{[\s\S]*\}/);
    const parsed = JSON.parse(jsonMatch ? jsonMatch[0] : raw);
    return {
      commits: Boolean(parsed.commits),
      targetName: parsed.commits && typeof parsed.targetName === "string" ? parsed.targetName.trim() : "",
      topic: parsed.commits && typeof parsed.topic === "string" ? parsed.topic.trim() : "",
      isMilestone: Boolean(parsed.isMilestone),
      milestoneNote:
        parsed.isMilestone && typeof parsed.milestoneNote === "string" ? parsed.milestoneNote.trim() : "",
    };
  } catch {
    // Best-effort: a failed/unparseable classification should never break the response the
    // user (or another persona) is actually waiting on.
    return NO_COMMITMENT;
  }
}
