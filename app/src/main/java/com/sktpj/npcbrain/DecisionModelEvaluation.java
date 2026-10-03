package com.sktpj.npcbrain;

final class DecisionModelEvaluation {
    final String choiceId;
    final double choiceProbability;
    final double evidenceProbability;
    final String modelId;
    final String executionLocation;

    DecisionModelEvaluation(
            String choiceId,
            double choiceProbability,
            double evidenceProbability,
            String modelId,
            String executionLocation
    ) {
        this.choiceId = choiceId == null ? "" : choiceId.trim();
        this.choiceProbability = clamp01(choiceProbability);
        this.evidenceProbability = clamp01(evidenceProbability);
        this.modelId = modelId == null ? "" : modelId.trim();
        this.executionLocation = executionLocation == null ? "" : executionLocation.trim();
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
