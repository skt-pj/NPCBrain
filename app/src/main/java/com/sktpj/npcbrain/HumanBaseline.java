package com.sktpj.npcbrain;

import org.json.JSONObject;

final class HumanBaseline {
    private HumanBaseline() {
    }

    /**
     * Kept only for source compatibility with older tests/callers. Shared psychological
     * priors are intentionally not injected; character decisions belong to the Brain.
     */
    static JSONObject toJson() {
        return new JSONObject();
    }
}
