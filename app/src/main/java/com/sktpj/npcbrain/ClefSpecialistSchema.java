package com.sktpj.npcbrain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed, situation-independent reaction schema for each specialist brain. */
final class ClefSpecialistSchema {
    static final class Field {
        final String id;
        final String instruction;
        final String[] optionIds;
        final String[] optionDescriptions;

        Field(
                String id,
                String instruction,
                String[] optionIds,
                String[] optionDescriptions
        ) {
            this.id = id;
            this.instruction = instruction;
            this.optionIds = Arrays.copyOf(optionIds, optionIds.length);
            this.optionDescriptions = Arrays.copyOf(
                    optionDescriptions, optionDescriptions.length);
        }
    }

    private static final String[] LEVEL_IDS =
            {"none", "low", "medium", "high", "extreme"};
    private static final String[] LEVEL_DESC =
            {"No reaction.", "Weak reaction.", "Moderate reaction.", "Strong reaction.", "Very strong reaction."};

    private static final String[] FIVE_IDS =
            {"very_low", "low", "medium", "high", "very_high"};
    private static final String[] FIVE_DESC =
            {"Very low.", "Low.", "Moderate.", "High.", "Very high."};

    private static final String[] VALENCE_IDS =
            {"very_negative", "negative", "neutral", "positive", "very_positive"};
    private static final String[] VALENCE_DESC =
            {"Strongly negative.", "Negative.", "Neutral.", "Positive.", "Strongly positive."};

    private static final String[] STABILITY_IDS =
            {"very_unstable", "unstable", "neutral", "stable", "very_stable"};
    private static final String[] STABILITY_DESC =
            {"Very unstable.", "Unstable.", "Neither stable nor unstable.", "Stable.", "Very stable."};

    private static final String[] CONSISTENCY_IDS =
            {"strong_conflict", "conflict", "neutral", "consistent", "strong_consistency"};
    private static final String[] CONSISTENCY_DESC =
            {"Strong conflict with known meaning.", "Some conflict.", "No clear relation.", "Consistent.", "Strongly consistent."};

    private static final String[] APPROACH_IDS =
            {"strong_avoid", "avoid", "neutral", "approach", "strong_approach"};
    private static final String[] APPROACH_DESC =
            {"Strong avoidance tendency.", "Avoidance tendency.", "Neither approach nor avoidance.", "Approach tendency.", "Strong approach tendency."};

    private static final String[] IMMEDIACY_IDS =
            {"defer", "low", "medium", "high", "immediate"};
    private static final String[] IMMEDIACY_DESC =
            {"Defer action.", "Low immediacy.", "Moderate immediacy.", "High immediacy.", "Act immediately."};

    static List<Field> forModule(String moduleId) {
        if ("perception".equals(moduleId)) {
            return Arrays.asList(
                    five("clarity", "How clear is the current perceived situation to this brain?"),
                    five("ambiguity", "How ambiguous is the current perceived situation?"),
                    level("change_signal", "How strongly does the current input signal a meaningful change?"));
        }
        if ("salience".equals(moduleId)) {
            return Arrays.asList(
                    level("attention_pull", "How strongly does the current state pull attention?"),
                    level("urgency", "How urgent does the current state feel?"),
                    level("persistence", "How strongly should attention remain engaged?"));
        }
        if ("episodic_memory".equals(moduleId)) {
            return Arrays.asList(
                    level("memory_activation", "How strongly are relevant past episodes activated?"),
                    five("familiarity", "How familiar does the current state feel from experience?"),
                    valence("memory_valence", "What affective tone is activated by episodic memory?"));
        }
        if ("semantic_memory".equals(moduleId)) {
            return Arrays.asList(
                    level("knowledge_activation", "How strongly is stored knowledge activated?"),
                    five("knownness", "How strongly does the current state feel known or understood?"),
                    field("consistency", "How consistent is the current state with stored knowledge?",
                            CONSISTENCY_IDS, CONSISTENCY_DESC));
        }
        if ("world_model".equals(moduleId)) {
            return Arrays.asList(
                    five("predictability", "How predictable does the current situation feel?"),
                    field("stability", "How stable does the current situation feel?",
                            STABILITY_IDS, STABILITY_DESC),
                    level("change_pressure", "How strongly does the current state imply that change is developing?"));
        }
        if ("executive_control".equals(moduleId)) {
            return Arrays.asList(
                    five("maintain_set", "How strongly should the current mental or behavioral set be maintained?"),
                    five("switch_pressure", "How strongly is there pressure to switch the current mental or behavioral set?"),
                    five("inhibition", "How strongly should immediate response be inhibited?"));
        }
        if ("valuation".equals(moduleId)) {
            return Arrays.asList(
                    valence("valence", "What is the overall positive or negative value reaction?"),
                    five("attraction", "How attractive or desirable does the current state feel?"),
                    five("danger", "How dangerous or threatening does the current state feel?"));
        }
        if ("error_monitor".equals(moduleId)) {
            return Arrays.asList(
                    level("conflict", "How strongly does the current state trigger internal conflict detection?"),
                    level("uncertainty", "How strongly does the current state trigger uncertainty detection?"),
                    level("error_signal", "How strongly does the current state trigger an error or mismatch signal?"));
        }
        if ("action_selection".equals(moduleId)) {
            return Arrays.asList(
                    level("action_drive", "How strongly is there a drive to act rather than remain inactive?"),
                    field("approach_tendency", "What is the approach-versus-avoidance tendency?",
                            APPROACH_IDS, APPROACH_DESC),
                    field("immediacy", "How immediate is the tendency to act?",
                            IMMEDIACY_IDS, IMMEDIACY_DESC));
        }
        return Collections.emptyList();
    }

    private static Field level(String id, String instruction) {
        return field(id, instruction, LEVEL_IDS, LEVEL_DESC);
    }

    private static Field five(String id, String instruction) {
        return field(id, instruction, FIVE_IDS, FIVE_DESC);
    }

    private static Field valence(String id, String instruction) {
        return field(id, instruction, VALENCE_IDS, VALENCE_DESC);
    }

    private static Field field(
            String id,
            String instruction,
            String[] optionIds,
            String[] optionDescriptions
    ) {
        return new Field(id, instruction, optionIds, optionDescriptions);
    }

    private ClefSpecialistSchema() {
    }
}
