package com.sktpj.npcbrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NpcPeerRoomPolicy {
    private static final String PREFIX = "peer_";

    private NpcPeerRoomPolicy() {}

    static String roomId(String first, String second) {
        String a = normalized(first);
        String b = normalized(second);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) return "";
        if (a.compareTo(b) > 0) {
            String swap = a;
            a = b;
            b = swap;
        }
        return PREFIX + a + "_" + b;
    }

    static boolean isPeerRoom(String roomId) {
        return participants(roomId).size() == 2;
    }

    static List<String> participants(String roomId) {
        String value = roomId == null ? "" : roomId.trim().toLowerCase(java.util.Locale.US);
        if (!value.startsWith(PREFIX)) return Collections.emptyList();
        String remainder = value.substring(PREFIX.length());
        int split = remainder.indexOf('_');
        if (split <= 0 || split >= remainder.length() - 1) return Collections.emptyList();
        String first = normalized(remainder.substring(0, split));
        String second = normalized(remainder.substring(split + 1));
        if (first.isEmpty() || second.isEmpty() || first.equals(second)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        result.add(first);
        result.add(second);
        return result;
    }

    private static String normalized(String value) {
        try {
            return NpcId.of(value).value();
        } catch (Exception ignored) {
            return "";
        }
    }
}
