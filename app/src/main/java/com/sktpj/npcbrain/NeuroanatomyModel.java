package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NeuroanatomyModel {
    private static final Map<String, List<String>> MODULE_REGIONS = new LinkedHashMap<>();

    static {
        MODULE_REGIONS.put("perception", Arrays.asList(
                "thalamus",
                "primary_sensory_cortices",
                "posterior_association_cortex"));
        MODULE_REGIONS.put("salience", Arrays.asList(
                "anterior_insula",
                "dorsal_anterior_cingulate_cortex",
                "amygdala",
                "thalamus"));
        MODULE_REGIONS.put("episodic_memory", Arrays.asList(
                "hippocampal_formation",
                "entorhinal_cortex",
                "medial_temporal_lobe"));
        MODULE_REGIONS.put("semantic_memory", Arrays.asList(
                "anterior_temporal_lobe",
                "angular_gyrus",
                "distributed_semantic_cortex"));
        MODULE_REGIONS.put("world_model", Arrays.asList(
                "hippocampus",
                "posterior_parietal_cortex",
                "medial_prefrontal_cortex",
                "cerebellum"));
        MODULE_REGIONS.put("executive_control", Arrays.asList(
                "dorsolateral_prefrontal_cortex",
                "frontoparietal_control_network",
                "basal_ganglia"));
        MODULE_REGIONS.put("valuation", Arrays.asList(
                "ventromedial_prefrontal_cortex",
                "orbitofrontal_cortex",
                "ventral_striatum",
                "nucleus_accumbens",
                "amygdala",
                "hypothalamus",
                "insula",
                "ventral_tegmental_area"));
        MODULE_REGIONS.put("error_monitor", Arrays.asList(
                "dorsal_anterior_cingulate_cortex",
                "anterior_insula",
                "lateral_prefrontal_cortex"));
        MODULE_REGIONS.put("action_selection", Arrays.asList(
                "basal_ganglia",
                "dorsal_striatum",
                "subthalamic_nucleus",
                "supplementary_motor_area",
                "premotor_cortex",
                "motor_cortex"));
        MODULE_REGIONS.put("global_workspace", Arrays.asList(
                "frontoparietal_control_network",
                "thalamus",
                "thalamocortical_broadcast",
                "cingulo_opercular_network"));
    }

    private NeuroanatomyModel() {
    }

    static JSONObject fullContext(JSONObject characterState) {
        JSONObject context = new JSONObject();
        JSONObject moduleRegions = new JSONObject();
        JSONObject interoceptiveSignals = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> entry : MODULE_REGIONS.entrySet()) {
                moduleRegions.put(entry.getKey(), toJson(entry.getValue()));
            }
            JSONObject innerLife = characterState == null
                    ? null
                    : characterState.optJSONObject("inner_life");
            if (innerLife != null) {
                copySignal(innerLife, interoceptiveSignals, "energy");
                copySignal(innerLife, interoceptiveSignals, "hunger");
                copySignal(innerLife, interoceptiveSignals, "sleep_pressure");
                copySignal(innerLife, interoceptiveSignals, "reproductive_drive");
                copySignal(innerLife, interoceptiveSignals, "safety_concern");
            }
            context.put("model", "many_to_many_functional_approximation");
            context.put("module_regions", moduleRegions);
            context.put("interoceptive_signals", interoceptiveSignals);
            context.put("policy",
                    "This is a functional many-to-many approximation of major human brain regions and networks, "
                            + "not a one-region/one-function firing-rate simulation. Each existing cognition specialist "
                            + "uses the module_regions entry matching its own module identity; no listed region independently "
                            + "owns a psychological decision. Interoceptive signals are grounded bounded inputs that can bias "
                            + "attention, valuation, planning and action selection. reproductive_drive is only an internal "
                            + "motivation signal and by itself never establishes romantic or sexual action, consent, or a relationship. "
                            + "The existing Global Workspace remains the final integrator of character decisions.");
        } catch (Exception ignored) {
        }
        return context;
    }

    static JSONArray regionsFor(String moduleId) {
        String key = moduleId == null ? "" : moduleId.trim().toLowerCase(Locale.ROOT);
        List<String> regions = MODULE_REGIONS.get(key);
        return regions == null ? new JSONArray() : toJson(regions);
    }

    static int ownerCount() {
        return MODULE_REGIONS.size();
    }

    private static JSONArray toJson(List<String> values) {
        JSONArray result = new JSONArray();
        if (values == null) return result;
        for (String value : values) result.put(value);
        return result;
    }

    private static void copySignal(JSONObject source, JSONObject destination, String key) {
        if (!source.has(key)) return;
        try {
            destination.put(key, clamp01(source.optDouble(key, 0.0)));
        } catch (Exception ignored) {
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
