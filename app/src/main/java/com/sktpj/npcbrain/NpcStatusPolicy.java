package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class NpcStatusPolicy {
    static final String REPLY_THINKING = "思考中";
    static final String REPLY_SENT = "返信あり";
    static final String REPLY_SILENT = "返信なし";
    static final String REPLY_SPONTANEOUS = "自発判断";
    static final String REPLY_NONE = "履歴なし";

    private NpcStatusPolicy() {
    }

    static List<String> selectorNpcIds(List<String> ids) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (String raw : ids) {
                try {
                    unique.add(NpcId.of(raw).value());
                } catch (Exception ignored) {
                }
            }
        }
        return new ArrayList<>(unique);
    }

    static String replyState(String npcId, String latestSenderId, boolean live) {
        if (live) return REPLY_THINKING;
        String id;
        try {
            id = NpcId.of(npcId).value();
        } catch (Exception ignored) {
            return REPLY_NONE;
        }
        String sender = latestSenderId == null ? "" : latestSenderId.trim();
        if (id.equals(sender)) return REPLY_SENT;
        if (("decision_" + id).equals(sender)) return REPLY_SILENT;
        if (("runtime_decision_" + id).equals(sender)) return REPLY_SPONTANEOUS;
        return REPLY_NONE;
    }
}
