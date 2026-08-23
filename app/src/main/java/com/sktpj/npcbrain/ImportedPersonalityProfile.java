package com.sktpj.npcbrain;

import org.json.JSONObject;

final class ImportedPersonalityProfile {
    final String name;
    final String speechStyle;
    final int extraversion;
    final int neuroticism;
    final int agreeableness;
    final int conscientiousness;
    final int openness;
    final String relationshipToUser;
    final String age;
    final String background;

    ImportedPersonalityProfile(
            String name,
            String speechStyle,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness
    ) {
        this(name, speechStyle, extraversion, neuroticism, agreeableness,
                conscientiousness, openness,
                CharacterStateStore.DEFAULT_RELATIONSHIP,
                CharacterStateStore.DEFAULT_AGE,
                CharacterStateStore.DEFAULT_BACKGROUND);
    }

    ImportedPersonalityProfile(
            String name,
            String speechStyle,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness,
            String relationshipToUser,
            String age,
            String background
    ) {
        this.name = name;
        this.speechStyle = speechStyle;
        this.extraversion = extraversion;
        this.neuroticism = neuroticism;
        this.agreeableness = agreeableness;
        this.conscientiousness = conscientiousness;
        this.openness = openness;
        this.relationshipToUser = safe(relationshipToUser, CharacterStateStore.DEFAULT_RELATIONSHIP);
        this.age = safe(age, CharacterStateStore.DEFAULT_AGE);
        this.background = safe(background, CharacterStateStore.DEFAULT_BACKGROUND);
    }

    static ImportedPersonalityProfile fromJson(JSONObject root) {
        if (root == null) throw new IllegalArgumentException("Personality JSON is missing");
        Object rawName = root.opt("name");
        Object rawSpeechStyle = root.opt("speech_style");
        if (!(rawName instanceof String) || ((String) rawName).trim().isEmpty()) {
            throw new IllegalArgumentException("Personality JSON name is missing");
        }
        if (!(rawSpeechStyle instanceof String) || ((String) rawSpeechStyle).trim().isEmpty()) {
            throw new IllegalArgumentException("Personality JSON speech_style is missing");
        }
        JSONObject traits = root.optJSONObject("traits");
        if (traits == null) throw new IllegalArgumentException("Personality JSON traits is missing");

        return new ImportedPersonalityProfile(
                ((String) rawName).trim(),
                ((String) rawSpeechStyle).trim(),
                trait(traits, "extraversion"),
                trait(traits, "neuroticism"),
                trait(traits, "agreeableness"),
                trait(traits, "conscientiousness"),
                trait(traits, "openness"),
                string(root, "relationship_to_user", CharacterStateStore.DEFAULT_RELATIONSHIP),
                string(root, "age", CharacterStateStore.DEFAULT_AGE),
                string(root, "background", CharacterStateStore.DEFAULT_BACKGROUND)
        );
    }

    private static int trait(JSONObject traits, String key) {
        Object value = traits.opt(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Personality JSON trait is missing or invalid: " + key);
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new IllegalArgumentException("Personality JSON trait is invalid: " + key);
        }
        return Math.max(0, Math.min(100, (int) Math.round(number)));
    }

    private static String string(JSONObject root, String key, String fallback) {
        Object value = root.opt(key);
        return value instanceof String ? safe((String) value, fallback) : fallback;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
