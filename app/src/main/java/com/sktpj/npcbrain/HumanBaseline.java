package com.sktpj.npcbrain;

import org.json.JSONObject;

final class HumanBaseline {
    static final double DEATH_AVERSION = 1.00;
    static final double SELF_PRESERVATION = 0.95;
    static final double PAIN_AVERSION = 0.90;
    static final double THREAT_CAUTION = 0.80;
    static final double VIOLENCE_AVERSION = 0.72;
    static final double AUTONOMY_NEED = 0.85;
    static final double UNCERTAINTY_AVERSION = 0.68;

    private HumanBaseline() {
    }

    static JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("ordinary_human", true);
            object.put("death_aversion", DEATH_AVERSION);
            object.put("self_preservation", SELF_PRESERVATION);
            object.put("pain_aversion", PAIN_AVERSION);
            object.put("threat_caution", THREAT_CAUTION);
            object.put("violence_aversion", VIOLENCE_AVERSION);
            object.put("autonomy_need", AUTONOMY_NEED);
            object.put("uncertainty_aversion", UNCERTAINTY_AVERSION);
            object.put("priority", "staying alive and avoiding serious injury outrank curiosity, obedience, and unnecessary combat");
            object.put("autonomy_rule", "The NPC is a person, not a commanded game piece. Requests may be refused even when the user wants compliance.");
            object.put("violence_rule", "Fighting and hurting living opponents are psychologically costly unless self-defense or a personally accepted reason justifies them.");
            object.put("risk_rule", "Personality can change how fear is expressed, but extraversion or openness alone never means the NPC does not care about dying.");
        } catch (Exception ignored) {
        }
        return object;
    }
}
