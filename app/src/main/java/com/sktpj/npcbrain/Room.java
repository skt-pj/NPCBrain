package com.sktpj.npcbrain;

import org.json.JSONArray;
import org.json.JSONObject;

final class Room {
    private final String roomId;
    private final String roomType;
    private final String[] participantIds;

    Room(String roomId, String roomType, String... participantIds) {
        this.roomId = roomId == null ? "" : roomId.trim();
        this.roomType = roomType == null ? "" : roomType.trim();
        this.participantIds = participantIds == null ? new String[0] : participantIds.clone();
    }

    String roomId() {
        return roomId;
    }

    String roomType() {
        return roomType;
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        JSONArray participants = new JSONArray();
        for (String participantId : participantIds) participants.put(participantId);
        try {
            json.put("room_id", roomId);
            json.put("room_type", roomType);
            json.put("participant_ids", participants);
        } catch (Exception ignored) {
        }
        return json;
    }
}
