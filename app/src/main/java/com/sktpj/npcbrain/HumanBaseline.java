package com.sktpj.npcbrain;

import org.json.JSONObject;

final class HumanBaseline {
    private HumanBaseline() {
    }

    static JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("ordinary_human", true);
            object.put("decision_owner", "global_workspace");
            object.put("risk_rule",
                    "Injury, death, uncertainty and violence are meaningful concerns to evaluate together with this character's personality, current state, memory, relationships and grounded situation. They are not fixed numeric action gates.");
            object.put("autonomy_rule",
                    "The NPC is a person, not a commanded game piece. Requests may be accepted, refused, reconsidered or withdrawn by the character's own integrated decision.");
            object.put("authority_rule",
                    "Only hard world constraints may block execution after the Brain chooses. Android policy must not replace a psychological choice with fixed thresholds or trait formulas.");
        } catch (Exception ignored) {
        }
        return object;
    }
}
