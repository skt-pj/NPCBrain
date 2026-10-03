package com.sktpj.npcbrain;

import java.util.LinkedHashMap;
import java.util.Map;

final class SpecialistDecisionSchema {
    static final class Spec {
        final String questionId;
        final String instruction;
        final String evidenceInstruction;
        final LinkedHashMap<String, String> criteria;

        Spec(
                String questionId,
                String instruction,
                String evidenceInstruction,
                LinkedHashMap<String, String> criteria
        ) {
            this.questionId = questionId;
            this.instruction = instruction;
            this.evidenceInstruction = evidenceInstruction;
            this.criteria = criteria;
        }

        String[] optionIds() {
            return criteria.keySet().toArray(new String[0]);
        }

        String[] optionDescriptions() {
            return criteria.values().toArray(new String[0]);
        }

        String descriptionFor(String id) {
            String description = criteria.get(id);
            return description == null ? id : description;
        }
    }

    private SpecialistDecisionSchema() {
    }

    static Spec forModule(String moduleId, String state) {
        switch (moduleId == null ? "" : moduleId) {
            case "perception":
                return spec(
                        "perception_focus",
                        "Which category best describes the most important directly observable grounded cue right now? Do not infer hidden facts.",
                        "Is there enough grounded observable evidence to commit to this perception category?",
                        "social_cue", "A directly observable social or relationship cue is most important.",
                        "threat_cue", "A directly observable danger or threat cue is most important.",
                        "reward_cue", "A directly observable resource, reward, or opportunity cue is most important.",
                        "novelty_cue", "A directly observable novel or anomalous cue is most important.",
                        "rule_or_constraint", "A directly observable rule, boundary, or hard constraint is most important.",
                        "goal_relevant", "A directly observable cue relevant to the active goal is most important.",
                        "neutral_observation", "No stronger category dominates; the main evidence is neutral observation.");
            case "salience":
                return spec(
                        "salience_focus",
                        "What should receive the highest attention priority now, given the grounded state and this character?",
                        "Is the evidence strong enough to prioritize this category over the alternatives?",
                        "urgent_threat", "An urgent threat or safety issue should dominate attention.",
                        "goal_blocker", "A blocker or dependency affecting an active goal should dominate attention.",
                        "social_priority", "A socially or relationally important cue should dominate attention.",
                        "opportunity", "A useful opportunity or reward should dominate attention.",
                        "novel_anomaly", "A novel, surprising, or anomalous cue should dominate attention.",
                        "missing_information", "An important unknown or missing fact should dominate attention.",
                        "routine_low_priority", "Nothing requires elevated priority; the situation is routine.");
            case "episodic_memory":
                return spec(
                        "episodic_relation",
                        "How does the most relevant retrieved episode relate to the current grounded situation?",
                        "Is there a retrieved episode relevant enough to use in this cognition cycle?",
                        "supportive_analogy", "A past episode provides a useful analogous pattern that supports the current interpretation.",
                        "warning_analogy", "A past episode is a cautionary analogue suggesting risk or restraint.",
                        "contradictory_memory", "A retrieved episode conflicts with or challenges the current interpretation.",
                        "relationship_memory", "A relationship-specific past episode is most relevant.",
                        "goal_memory", "A past episode tied to the active goal or unfinished task is most relevant.",
                        "no_relevant_episode", "No retrieved episode is relevant enough to materially influence this cycle.");
            case "semantic_memory":
                return spec(
                        "semantic_constraint",
                        "Which type of durable knowledge most materially constrains or informs this situation?",
                        "Is there durable semantic knowledge strong enough to influence the current cycle?",
                        "world_fact", "A durable factual belief about the world is most relevant.",
                        "self_belief", "A durable self-belief or learned self-model is most relevant.",
                        "goal_or_value", "A durable goal or value is most relevant.",
                        "relationship_knowledge", "Durable relationship knowledge is most relevant.",
                        "role_or_rule", "A durable role identity, rule, or obligation is most relevant.",
                        "knowledge_conflict", "Durable knowledge conflicts with current evidence and should be flagged.",
                        "no_semantic_constraint", "No durable semantic item materially constrains the current situation.");
            case "world_model":
                return spec(
                        "predicted_direction",
                        "Which causal forecast best describes the likely near-term direction if the current course continues?",
                        "Is there enough grounded causal evidence to commit to this forecast category?",
                        "likely_improves", "The current course is likely to improve progress, safety, or goal satisfaction.",
                        "likely_worsens", "The current course is likely to worsen risk, conflict, or goal progress.",
                        "stable", "The current course is likely to leave the situation materially stable.",
                        "branching_uncertain", "Several plausible futures remain; the outcome is materially uncertain.",
                        "constraint_blocked", "A hard physical, social, or rule constraint blocks the intended outcome.",
                        "information_needed", "A meaningful forecast cannot be made without additional grounded information.");
            case "executive_control":
                return spec(
                        "control_policy",
                        "Which executive-control policy should govern the next cognition step?",
                        "Is there enough evidence to commit to this executive-control policy now?",
                        "continue_plan", "Continue the current plan without switching.",
                        "switch_plan", "Switch away from the current plan because another course is better grounded.",
                        "decompose_goal", "Break the active goal into smaller subgoals before acting.",
                        "gather_information", "Gather missing information before committing further.",
                        "delay_commitment", "Delay commitment because uncertainty or timing makes immediate action premature.",
                        "execute_now", "Commit to a feasible next action now.");
            case "valuation":
                return spec(
                        "dominant_value",
                        "Which value dimension should dominate this character's preference in the current grounded situation?",
                        "Is this value preference strong enough to materially influence the current decision?",
                        "approach_reward", "Approach a meaningful reward or positive outcome.",
                        "avoid_harm", "Reduce threat, harm, loss, or exposure.",
                        "cooperate", "Favor cooperation, fairness, or another person's needs.",
                        "protect_relationship", "Protect or strengthen an important relationship.",
                        "explore_information", "Favor curiosity, learning, or information gain.",
                        "fulfill_duty", "Favor obligation, standards, or goal completion.",
                        "conserve_effort", "Favor lower effort, reversibility, or conserving resources.");
            case "error_monitor":
                return spec(
                        "error_class",
                        "Which error or failure category most needs monitoring in the current grounded state?",
                        "Is there enough evidence that a meaningful error/failure concern is present?",
                        "no_major_error", "No major contradiction or failure mode is currently evident.",
                        "contradiction", "Two grounded claims or constraints materially contradict each other.",
                        "unsupported_assumption", "A current interpretation depends on an unsupported assumption.",
                        "missing_information", "Important information is missing for a safe or correct commitment.",
                        "rule_violation", "A proposed interpretation or action risks violating a hard rule or constraint.",
                        "goal_conflict", "The current course conflicts with an active goal or explicit objective.",
                        "high_failure_risk", "The current course has a high grounded risk of failure.");
            case "action_selection":
                return actionSelection(state);
            default:
                return spec(
                        "specialist_classification",
                        "Choose the single classification that best fits this specialist's role and the grounded state.",
                        "Is there enough grounded evidence to commit to this classification?",
                        "relevant", "The specialist has a material grounded signal for this cognition cycle.",
                        "uncertain", "The specialist signal is materially uncertain.",
                        "not_relevant", "The specialist has no material signal for this cognition cycle.");
        }
    }

    private static Spec actionSelection(String state) {
        if (hasMode(state, "dungeon_turn")) {
            return spec(
                    "action_class",
                    "Choose the single feasible action class this NPC should recommend now from grounded dungeon state.",
                    "Is there enough evidence to commit to this action class now?",
                    "attack", "Attack a currently legal visible target when combat is preferred.",
                    "advance", "Move toward the current grounded objective using legal movement.",
                    "explore", "Gather new grounded dungeon information through legal exploration.",
                    "retreat", "Move away from danger or leave combat when withdrawal is preferred.",
                    "wait", "Take no committed movement or attack now.");
        }
        if (hasMode(state, "conversational_message")
                || hasMode(state, "spontaneous_life_event")
                || hasMode(state, "reply_timer")) {
            return spec(
                    "action_class",
                    "Choose the single communication/action class this NPC should recommend now.",
                    "Is there enough evidence to commit to this action class now?",
                    "speak_now", "Communicate now with grounded content relevant to the current interaction.",
                    "remain_silent", "Do not communicate now.",
                    "defer", "Do not communicate now, but leave room to respond later.",
                    "act_without_speaking", "Take an in-world action without communicating.");
        }
        return spec(
                "action_class",
                "Choose the single feasible action class this NPC should recommend now.",
                "Is there enough evidence to commit to this action class now?",
                "continue_current_activity", "Continue the current grounded activity.",
                "pursue_goal", "Take a legal action that advances the active goal.",
                "communicate", "Communicate with a relevant person when socially or practically warranted.",
                "gather_information", "Seek grounded information before committing further.",
                "withdraw_or_avoid", "Reduce exposure to a relevant threat or unwanted situation.",
                "wait", "Take no new committed action now.");
    }

    private static Spec spec(
            String questionId,
            String instruction,
            String evidenceInstruction,
            String... idDescriptionPairs
    ) {
        LinkedHashMap<String, String> criteria = new LinkedHashMap<>();
        for (int i = 0; i + 1 < idDescriptionPairs.length; i += 2) {
            criteria.put(idDescriptionPairs[i], idDescriptionPairs[i + 1]);
        }
        return new Spec(questionId, instruction, evidenceInstruction, criteria);
    }

    private static boolean hasMode(String source, String mode) {
        if (source == null) return false;
        String plain = "\"mode\":\"" + mode + "\"";
        String escaped = "\\\"mode\\\":\\\"" + mode + "\\\"";
        return source.contains(plain) || source.contains(escaped);
    }
}
