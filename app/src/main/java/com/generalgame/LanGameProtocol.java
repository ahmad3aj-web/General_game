package com.generalgame;

import org.json.JSONException;
import org.json.JSONObject;

/** Message vocabulary and validation helpers for the GENERAL LAN match protocol. */
public final class LanGameProtocol {
    public static final int VERSION = 1;
    public static final String HELLO = "hello";
    public static final String READY = "ready";
    public static final String OPENING_ROLL = "opening_roll";
    public static final String TURN_START = "turn_start";
    public static final String ROLL = "roll";
    public static final String MOVE = "move";
    public static final String TURN_CANCEL = "turn_cancel";
    public static final String STATE = "state";
    public static final String MATCH_END = "match_end";
    public static final String REMATCH = "rematch";
    public static final String ERROR = "error";

    private LanGameProtocol() { }

    public static JSONObject hello(String playerName, int avatar) throws JSONException {
        JSONObject message = base(HELLO);
        message.put("name", safeName(playerName));
        message.put("avatar", Math.max(0, Math.min(17, avatar)));
        return message;
    }

    public static JSONObject roll(int turnId, int value, boolean automatic) throws JSONException {
        if (turnId < 1 || value < 1 || value > 6) throw new JSONException("invalid roll");
        JSONObject message = base(ROLL);
        message.put("turnId", turnId);
        message.put("value", value);
        message.put("automatic", automatic);
        return message;
    }

    public static JSONObject move(int turnId, int lane, int value) throws JSONException {
        if (turnId < 1 || lane < 0 || lane > 2 || value < 1 || value > 6)
            throw new JSONException("invalid move");
        JSONObject message = base(MOVE);
        message.put("turnId", turnId);
        message.put("lane", lane);
        message.put("value", value);
        return message;
    }

    public static JSONObject turnStart(int turnId, int side, long startedAtMillis)
            throws JSONException {
        if (turnId < 1 || (side != 1 && side != 2)) throw new JSONException("invalid turn");
        JSONObject message = base(TURN_START);
        message.put("turnId", turnId);
        message.put("side", side);
        message.put("startedAt", startedAtMillis);
        message.put("timeoutSeconds", 30);
        return message;
    }

    public static boolean isSupported(JSONObject message) {
        return message != null && message.optInt("version", -1) == VERSION
                && message.optString("type", "").length() > 0;
    }

    public static boolean isValidRoll(JSONObject message, int expectedTurn) {
        return isSupported(message) && ROLL.equals(message.optString("type"))
                && message.optInt("turnId", -1) == expectedTurn
                && message.optInt("value", 0) >= 1 && message.optInt("value", 0) <= 6;
    }

    public static boolean isValidMove(JSONObject message, int expectedTurn, int expectedRoll) {
        return isSupported(message) && MOVE.equals(message.optString("type"))
                && message.optInt("turnId", -1) == expectedTurn
                && message.optInt("lane", -1) >= 0 && message.optInt("lane", -1) <= 2
                && message.optInt("value", 0) == expectedRoll;
    }

    private static JSONObject base(String type) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("version", VERSION);
        message.put("type", type);
        return message;
    }

    private static String safeName(String value) {
        if (value == null) return "لاعب";
        String cleaned = value.trim();
        if (cleaned.length() == 0) return "لاعب";
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }
}
