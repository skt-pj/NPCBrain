package com.sktpj.npcbrain;

/** Canonical private room id for autonomous NPC-to-NPC conversation. */
final class NpcPeerRoomPolicy {
    private static final String PREFIX = "peer_";

    private NpcPeerRoomPolicy() {}

    static String roomId(String firstNpcId, String secondNpcId) {
        String first = NpcId.of(firstNpcId).value();
        String second = NpcId.of(secondNpcId).value();
        if (first.equals(second)) throw new IllegalArgumentException("peer room requires two NPCs");
        String left = first.compareTo(second) <= 0 ? first : second;
        String right = first.compareTo(second) <= 0 ? second : first;
        return PREFIX + left + "__" + right;
    }

    static boolean isPeerRoom(String roomId) {
        return roomId != null && roomId.startsWith(PREFIX) && roomId.contains("__");
    }
}
