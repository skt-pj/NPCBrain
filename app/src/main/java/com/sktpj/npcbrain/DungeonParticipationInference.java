package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class DungeonParticipationInference {
    private static final String[] DUNGEON_TERMS = {
            "ダンジョン", "地下迷宮", "迷宮", "冒険", "最上階", "階段", "モンスター", "魔物",
            "戦闘", "戦う", "dungeon", "adventure", "monster", "fight"
    };
    private static final String[] REFUSAL_TERMS = {
            "行きたくない", "行かない", "行けない", "無理", "嫌だ", "いやだ", "断る",
            "やめたい", "帰りたい", "死にたくない", "危険すぎ", "怖すぎ", "行く気はない",
            "won't go", "do not want to go", "don't want to go", "too dangerous"
    };
    private static final String[] WITHDRAW_TERMS = {
            "もう無理", "これ以上は無理", "撤退", "帰りたい", "やめる", "続けられない",
            "turn back", "retreat", "can't continue", "cannot continue"
    };
    private static final String[] ACCEPT_TERMS = {
            "行くことにする", "一緒に行く", "行こう", "参加する", "手伝う", "付き合う",
            "ついていく", "覚悟を決め", "やってみる", "挑戦する", "行くよ", "行く。",
            "i'll go", "i will go", "let's go", "i'll help", "i will help"
    };
    private static final String[] HESITATION_TERMS = {
            "怖い", "不安", "迷う", "迷って", "考えさせ", "気が進ま", "心配", "条件なら",
            "scared", "afraid", "unsure", "not sure", "worried"
    };
    private static final String[] REASON_CUES = {
            "から", "だから", "ため", "守り", "守る", "助け", "心配", "放って", "約束",
            "自分で", "確かめ", "取り戻", "一人で行かせ", "一緒なら", "大切", "救い",
            "because", "to help", "to protect", "i care", "promise"
    };

    private DungeonParticipationInference() {
    }

    static DungeonParticipationPolicy.Candidate infer(
            DungeonParticipationState previous,
            String userText,
            String npcText,
            JSONArray brainTrace
    ) {
        DungeonParticipationState before = previous == null
                ? DungeonParticipationState.initial() : previous;
        String user = normalize(userText);
        String npc = normalize(npcText);
        String summary = normalize(globalWorkspaceSummary(brainTrace));
        String evidence = (npc + " " + summary).trim();
        if (!isRelevant(user, evidence)) return DungeonParticipationPolicy.Candidate.none();
        if (evidence.isEmpty()) {
            return new DungeonParticipationPolicy.Candidate(
                    true,
                    DungeonParticipationState.HESITATE,
                    Math.max(before.willingness, 0.16),
                    Math.max(before.fear, 0.80),
                    Math.max(before.resolve, 0.10),
                    "");
        }

        boolean withdraw = before.isAccepted() && containsAny(evidence, WITHDRAW_TERMS);
        boolean refuse = containsAny(evidence, REFUSAL_TERMS);
        boolean accept = containsAny(evidence, ACCEPT_TERMS) || simplePositiveGo(evidence);
        boolean hesitate = containsAny(evidence, HESITATION_TERMS);
        String personalReason = meaningfulPersonalReason(npc, summary);

        if (withdraw) {
            return new DungeonParticipationPolicy.Candidate(
                    true,
                    DungeonParticipationState.WITHDRAW,
                    0.18,
                    0.96,
                    0.12,
                    npc.isEmpty() ? summary : npc);
        }
        if (refuse && !explicitFearButAccept(evidence)) {
            return new DungeonParticipationPolicy.Candidate(
                    true,
                    DungeonParticipationState.REFUSE,
                    0.08,
                    0.94,
                    0.10,
                    npc.isEmpty() ? summary : npc);
        }
        if (accept) {
            return new DungeonParticipationPolicy.Candidate(
                    true,
                    DungeonParticipationState.ACCEPT,
                    0.96,
                    hesitate ? 0.62 : 0.48,
                    0.94,
                    personalReason);
        }
        return new DungeonParticipationPolicy.Candidate(
                true,
                DungeonParticipationState.HESITATE,
                0.48,
                hesitate ? 0.86 : 0.76,
                0.42,
                personalReason);
    }

    private static boolean isRelevant(String user, String evidence) {
        return containsAny(user, DUNGEON_TERMS) || containsAny(evidence, DUNGEON_TERMS)
                || (containsAny(user, new String[]{"行こう", "行かない", "一緒に行", "戦って"})
                && containsAny(evidence, new String[]{"行く", "行か", "怖", "危険", "戦"}));
    }

    private static boolean simplePositiveGo(String evidence) {
        if (!evidence.contains("行く")) return false;
        if (evidence.contains("行かない") || evidence.contains("行きたくない")
                || evidence.contains("行く気はない") || evidence.contains("行くのは嫌")) {
            return false;
        }
        return evidence.contains("行くよ") || evidence.contains("行くこと")
                || evidence.contains("行く。") || evidence.contains("なら行く")
                || evidence.contains("けど行く") || evidence.contains("でも行く");
    }

    private static boolean explicitFearButAccept(String evidence) {
        boolean positive = containsAny(evidence, ACCEPT_TERMS) || simplePositiveGo(evidence);
        return positive && containsAny(evidence, new String[]{"けど", "でも", "それでも", "though", "but"});
    }

    private static String meaningfulPersonalReason(String npc, String summary) {
        String source = npc;
        if (source.length() < 8 || !containsAny(source, REASON_CUES)) source = summary;
        if (source.length() < 8 || !containsAny(source, REASON_CUES)) return "";
        return source.length() <= 180 ? source : source.substring(0, 180);
    }

    private static String globalWorkspaceSummary(JSONArray trace) {
        if (trace == null) return "";
        for (int i = trace.length() - 1; i >= 0; i--) {
            JSONObject stage = trace.optJSONObject(i);
            if (stage == null) continue;
            if ("global_workspace".equals(stage.optString("stage_id", ""))) {
                return stage.optString("summary", "");
            }
        }
        return "";
    }

    private static boolean containsAny(String source, String[] terms) {
        if (source == null || source.isEmpty()) return false;
        for (String term : terms) {
            if (term != null && !term.isEmpty() && source.contains(term)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim().toLowerCase(Locale.ROOT);
    }
}
