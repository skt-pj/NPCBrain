package com.sktpj.npcbrain;

import org.json.JSONObject;

final class NpcProfileDraft {
    private final String name;
    private final int extraversion;
    private final int neuroticism;
    private final int agreeableness;
    private final int conscientiousness;
    private final int openness;
    private final String speechStyle;
    private final String relationshipToUser;
    private final String age;
    private final String occupation;
    private final String background;
    private final String roleIdentity;
    private final String values;
    private final String goals;
    private final String fears;
    private final String relationships;

    NpcProfileDraft(
            String name,
            int extraversion,
            int neuroticism,
            int agreeableness,
            int conscientiousness,
            int openness,
            String speechStyle,
            String relationshipToUser,
            String age,
            String occupation,
            String background,
            String roleIdentity,
            String values,
            String goals,
            String fears,
            String relationships
    ) {
        this.name = clean(name);
        this.extraversion = clampPercent(extraversion);
        this.neuroticism = clampPercent(neuroticism);
        this.agreeableness = clampPercent(agreeableness);
        this.conscientiousness = clampPercent(conscientiousness);
        this.openness = clampPercent(openness);
        this.speechStyle = clean(speechStyle);
        this.relationshipToUser = clean(relationshipToUser);
        this.age = clean(age);
        this.occupation = clean(occupation);
        this.background = clean(background);
        this.roleIdentity = clean(roleIdentity);
        this.values = clean(values);
        this.goals = clean(goals);
        this.fears = clean(fears);
        this.relationships = clean(relationships);
    }

    String name() { return name; }
    int extraversion() { return extraversion; }
    int neuroticism() { return neuroticism; }
    int agreeableness() { return agreeableness; }
    int conscientiousness() { return conscientiousness; }
    int openness() { return openness; }
    String speechStyle() { return speechStyle; }
    String relationshipToUser() { return relationshipToUser; }
    String age() { return age; }
    String occupation() { return occupation; }
    String background() { return background; }
    String roleIdentity() { return roleIdentity; }
    String values() { return values; }
    String goals() { return goals; }
    String fears() { return fears; }
    String relationships() { return relationships; }

    JSONObject toJson(String npcId) {
        JSONObject root = new JSONObject();
        JSONObject traits = new JSONObject();
        try {
            root.put("character_id", NpcId.of(npcId).value());
            root.put("name", name);
            traits.put("extraversion", extraversion);
            traits.put("neuroticism", neuroticism);
            traits.put("agreeableness", agreeableness);
            traits.put("conscientiousness", conscientiousness);
            traits.put("openness", openness);
            root.put("traits_percent", traits);
            root.put("speech_style", speechStyle);
            root.put("relationship_to_user", relationshipToUser);
            root.put("age", age);
            root.put("occupation", occupation);
            root.put("background", background);
            root.put("role_identity", roleIdentity);
            root.put("values", values);
            root.put("goals", goals);
            root.put("fears", fears);
            root.put("relationships", relationships);
        } catch (Exception ignored) {
        }
        return root;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
