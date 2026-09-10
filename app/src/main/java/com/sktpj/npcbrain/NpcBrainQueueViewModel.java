package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure grouping/label logic for the Debug queue: exactly one visible card per NPC brain. */
final class NpcBrainQueueViewModel {
    static final int MAX_RECENT_DETAILS_PER_NPC = 20;

    static final class BrainGroup {
        final String npcId;
        final List<ProcessingQueueRegistry.Entry> active;
        final List<ProcessingQueueRegistry.Entry> recent;

        BrainGroup(
                String npcId,
                List<ProcessingQueueRegistry.Entry> active,
                List<ProcessingQueueRegistry.Entry> recent
        ) {
            this.npcId = safe(npcId);
            this.active = Collections.unmodifiableList(new ArrayList<>(active));
            this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
        }

        boolean hasActive() {
            return !active.isEmpty();
        }

        int runningCount() {
            int count = 0;
            for (ProcessingQueueRegistry.Entry entry : active) {
                if (entry.status == ProcessingQueueRegistry.Status.RUNNING) count++;
            }
            return count;
        }

        int queuedCount() {
            int count = 0;
            for (ProcessingQueueRegistry.Entry entry : active) {
                if (entry.status == ProcessingQueueRegistry.Status.QUEUED) count++;
            }
            return count;
        }

        int activeLlmCount() {
            int count = 0;
            for (ProcessingQueueRegistry.Entry entry : active) {
                if (isLlm(entry)) count++;
            }
            return count;
        }

        ProcessingQueueRegistry.Status headlineStatus() {
            if (hasActive()) {
                for (ProcessingQueueRegistry.Entry entry : active) {
                    if (entry.status == ProcessingQueueRegistry.Status.RUNNING) {
                        return ProcessingQueueRegistry.Status.RUNNING;
                    }
                }
                return ProcessingQueueRegistry.Status.QUEUED;
            }
            for (ProcessingQueueRegistry.Entry entry : recent) {
                if (entry.status == ProcessingQueueRegistry.Status.FAILED) {
                    return ProcessingQueueRegistry.Status.FAILED;
                }
                if (entry.status == ProcessingQueueRegistry.Status.COMPLETED) {
                    return ProcessingQueueRegistry.Status.COMPLETED;
                }
            }
            return ProcessingQueueRegistry.Status.COMPLETED;
        }

        long sortTimestamp() {
            if (hasActive()) {
                long earliest = Long.MAX_VALUE;
                for (ProcessingQueueRegistry.Entry entry : active) {
                    earliest = Math.min(earliest, entry.queuedAtMs);
                }
                return earliest == Long.MAX_VALUE ? 0L : earliest;
            }
            long latest = 0L;
            for (ProcessingQueueRegistry.Entry entry : recent) {
                latest = Math.max(latest, Math.max(entry.finishedAtMs, entry.queuedAtMs));
            }
            return latest;
        }
    }

    private NpcBrainQueueViewModel() {}

    static List<BrainGroup> group(ProcessingQueueRegistry.Snapshot snapshot) {
        if (snapshot == null) return Collections.emptyList();
        Map<String, MutableGroup> groups = new LinkedHashMap<>();

        for (ProcessingQueueRegistry.Entry entry : snapshot.active) {
            if (!belongsToNpcBrain(entry)) continue;
            mutable(groups, entry.npcId).active.add(entry);
        }
        for (ProcessingQueueRegistry.Entry entry : snapshot.recent) {
            if (!belongsToNpcBrain(entry)) continue;
            MutableGroup group = mutable(groups, entry.npcId);
            if (group.recent.size() < MAX_RECENT_DETAILS_PER_NPC) {
                group.recent.add(entry);
            }
        }

        List<BrainGroup> result = new ArrayList<>();
        for (MutableGroup value : groups.values()) {
            value.active.sort(Comparator
                    .comparingLong((ProcessingQueueRegistry.Entry e) -> e.queuedAtMs)
                    .thenComparing(e -> e.id));
            result.add(new BrainGroup(value.npcId, value.active, value.recent));
        }
        result.sort((left, right) -> {
            if (left.hasActive() != right.hasActive()) return left.hasActive() ? -1 : 1;
            if (left.hasActive()) {
                int byTime = Long.compare(left.sortTimestamp(), right.sortTimestamp());
                if (byTime != 0) return byTime;
            } else {
                int byTime = Long.compare(right.sortTimestamp(), left.sortTimestamp());
                if (byTime != 0) return byTime;
            }
            return left.npcId.compareTo(right.npcId);
        });
        return result;
    }

    static boolean belongsToNpcBrain(ProcessingQueueRegistry.Entry entry) {
        return entry != null && !safe(entry.npcId).isEmpty();
    }

    static boolean isLlm(ProcessingQueueRegistry.Entry entry) {
        return entry != null && "llm_request".equals(entry.type);
    }

    static String internalLabel(ProcessingQueueRegistry.Entry entry) {
        if (entry == null) return "処理";
        if (isLlm(entry)) {
            String stageId = stageId(entry.detail);
            if ("global_workspace".equals(stageId)) return "Global Workspace";
            if (!stageId.isEmpty() && !"specialist".equals(stageId)) {
                return "専門Brain · " + BrainEngine.stageLabel(stageId);
            }
            if (safe(entry.detail).contains("Global Workspace")) return "Global Workspace";
            return "専門Brain";
        }
        if ("spontaneous_cognition".equals(entry.type)) return "自分から話すか検討";
        return ProcessingQueueRegistry.displayType(entry.type);
    }

    static String modelLabel(ProcessingQueueRegistry.Entry entry) {
        if (!isLlm(entry)) return "";
        String detail = safe(entry.detail);
        int delimiter = detail.indexOf(" · ");
        String model = delimiter >= 0 ? detail.substring(0, delimiter) : detail;
        if (NpcInferenceModel.LOCAL_LIGHT.equals(model)) return "ローカル・軽い";
        if (NpcInferenceModel.LOCAL_MEDIUM.equals(model)) return "ローカル・中";
        if (NpcInferenceModel.LOCAL_HEAVY.equals(model)) return "ローカル・重い";
        if (NpcInferenceModel.OPENAI_LUNA.equals(model)) return "OpenAI Luna";
        return model;
    }

    static String stageId(String detail) {
        String value = safe(detail);
        String marker = "brain_stage=";
        int start = value.indexOf(marker);
        if (start >= 0) {
            int valueStart = start + marker.length();
            int end = value.indexOf(" · ", valueStart);
            String stage = (end >= 0 ? value.substring(valueStart, end) : value.substring(valueStart)).trim();
            if (!stage.isEmpty()) return stage;
        }
        if (value.contains("Global Workspace")) return "global_workspace";
        return value.contains("specialist / task") ? "specialist" : "";
    }

    private static MutableGroup mutable(Map<String, MutableGroup> groups, String npcId) {
        String key = safe(npcId);
        MutableGroup current = groups.get(key);
        if (current == null) {
            current = new MutableGroup(key);
            groups.put(key, current);
        }
        return current;
    }

    private static final class MutableGroup {
        final String npcId;
        final List<ProcessingQueueRegistry.Entry> active = new ArrayList<>();
        final List<ProcessingQueueRegistry.Entry> recent = new ArrayList<>();

        MutableGroup(String npcId) {
            this.npcId = npcId;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
