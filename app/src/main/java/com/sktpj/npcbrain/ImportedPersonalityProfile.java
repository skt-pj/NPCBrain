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

    ImportedPersonalityProfile(
            String name,
            String speechStyle,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness
    ) {
        this.name = name;
        this.speechStyle = speechStyle;
        this.extraversion = extraversion;
        this.neuroticism = neuroticism;
        this.agreeableness = agreeableness;
        this.conscientiousness = conscientiousness;
        this.openness = openness;
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
                trait(traits, "openness")
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
}
