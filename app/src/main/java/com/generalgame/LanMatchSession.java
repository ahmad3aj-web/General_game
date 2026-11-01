package com.generalgame;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Coordinates one peer's actions in a two-device GENERAL match.
 * Each device generates only its own die result. The peer receives that
 * result and renders it; it never generates a result on the other player's behalf.
 */
public final class LanMatchSession implements LanConnection.Listener {
    public interface Listener {
        void onConnected();
        void onPeerInfo(int side, String name, int avatar);
        void onOpeningRolls(int blueValue, int redValue, boolean tied);
        void onOpeningWinner(int startingSide);
        void onTurnStarted(int turnId, int activeSide, boolean localTurn);
        void onCountdown(int secondsRemaining);
        void onRoll(int side, int value, boolean automatic);
        void onMove(int side, int lane, int value);
        void onTurnCancelled(int side);
        void onDisconnected(String reason);
        void onProtocolError(String reason);
    }

    private final LanConnection connection;
    private final LanTurnTimer timer;
    private final Listener listener;
    private final int localSide;
    private volatile boolean connected;
    private int expectedTurn;
    private int activeSide;
    private int rolledValue;
    private boolean rollReceived;
    private boolean moveReceived;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int openingRound;
    private int localOpeningValue;
    private int remoteOpeningValue;

    public LanMatchSession(int localSide, Listener listener) {
        if (localSide != 1 && localSide != 2) throw new IllegalArgumentException("localSide");
        if (listener == null) throw new IllegalArgumentException("listener");
        this.localSide = localSide;
        this.listener = listener;
        this.connection = new LanConnection(this);
        this.timer = new LanTurnTimer(new LanTurnTimer.Listener() {
            @Override public void onTick(int seconds) {
                listener.onCountdown(seconds);
            }
            @Override public void onTimeout() {
                // The player's own phone performs the automatic roll at 30 seconds.
                rollNow(true);
            }
        });
    }

    public int getLocalSide() { return localSide; }
    public void sendHello(String name, int avatar) {
        if (!connected) return;
        try {
            JSONObject message = LanGameProtocol.hello(name, avatar);
            message.put("side", localSide);
            connection.send(message);
        } catch (Exception error) { listener.onProtocolError("تعذر إرسال بيانات اللاعب"); }
    }
    public boolean isConnected() { return connected; }
    public boolean isLocalTurn() { return connected && activeSide == localSide; }

    public void host() throws java.io.IOException { connection.host(); }
    public void join(String address) throws java.io.IOException { connection.join(address); }

    /** Both phones generate their own opening die and exchange the results. */
    public synchronized void startOpeningToss() {
        if (!connected) return;
        openingRound++;
        localOpeningValue = (int) (Math.random() * 6) + 1;
        remoteOpeningValue = 0;
        try {
            JSONObject message = new JSONObject();
            message.put("version", LanGameProtocol.VERSION);
            message.put("type", LanGameProtocol.OPENING_ROLL);
            message.put("round", openingRound);
            message.put("side", localSide);
            message.put("value", localOpeningValue);
            connection.send(message);
        } catch (Exception error) {
            listener.onProtocolError("تعذر إرسال قرعة البداية");
        }
    }

    private synchronized void evaluateOpeningToss(int round) {
        if (round != openingRound || localOpeningValue == 0 || remoteOpeningValue == 0) return;
        final int blue = localSide == 1 ? localOpeningValue : remoteOpeningValue;
        final int red = localSide == 2 ? localOpeningValue : remoteOpeningValue;
        boolean tied = blue == red;
        listener.onOpeningRolls(blue, red, tied);
        if (tied) {
            mainHandler.postDelayed(() -> {
                synchronized (LanMatchSession.this) {
                    if (connected && openingRound == round && localOpeningValue != 0
                            && remoteOpeningValue != 0 && localOpeningValue == remoteOpeningValue) {
                        startOpeningToss();
                    }
                }
            }, 2600L);
        } else {
            listener.onOpeningWinner(blue > red ? 1 : 2);
        }
    }

    /** Called on both devices after the opening toss has selected the first player. */
    public synchronized void beginTurn(int turnId, int side) {
        if (!connected || turnId < 1 || (side != 1 && side != 2)) return;
        timer.cancel();
        expectedTurn = turnId;
        activeSide = side;
        rolledValue = 0;
        rollReceived = false;
        moveReceived = false;
        listener.onTurnStarted(turnId, side, side == localSide);
        if (side == localSide) timer.start(turnId);
    }

    /** Called only by the active player's button, or by its 30-second timeout. */
    public synchronized boolean rollNow(boolean automatic) {
        if (!connected || activeSide != localSide || expectedTurn < 1 || rollReceived
                || rolledValue != 0) return false;
        int value = (int) (Math.random() * 6) + 1;
        try {
            JSONObject message = LanGameProtocol.roll(expectedTurn, value, automatic);
            message.put("side", localSide);
            connection.send(message);
            rolledValue = value;
            rollReceived = true;
            timer.cancel();
            listener.onRoll(localSide, value, automatic);
            return true;
        } catch (Exception error) {
            listener.onProtocolError("تعذر إرسال رمية النرد");
            return false;
        }
    }

    /** Send the selected lane after the local die has been accepted. */
    public synchronized boolean sendMove(int lane) {
        if (!connected || activeSide != localSide || rolledValue < 1
                || moveReceived || lane < 0 || lane > 2) return false;
        try {
            connection.send(LanGameProtocol.move(expectedTurn, lane, rolledValue)
                    .put("side", localSide));
            moveReceived = true;
            return true;
        } catch (Exception error) {
            listener.onProtocolError("تعذر إرسال الحركة");
            return false;
        }
    }

    public synchronized void cancelTurn() {
        if (!connected || activeSide != localSide || moveReceived) return;
        timer.cancel();
        try {
            JSONObject message = new JSONObject();
            message.put("version", LanGameProtocol.VERSION);
            message.put("type", LanGameProtocol.TURN_CANCEL);
            message.put("turnId", expectedTurn);
            message.put("side", localSide);
            message.put("value", rolledValue);
            connection.send(message);
            moveReceived = true;
            listener.onTurnCancelled(localSide);
        } catch (JSONException | java.io.IOException error) {
            listener.onProtocolError("تعذر إلغاء الدور");
        }
    }

    @Override public void onConnected() {
        connected = true;
        listener.onConnected();
    }

    @Override public synchronized void onMessage(JSONObject message) {
        if (!LanGameProtocol.isSupported(message)) {
            listener.onProtocolError("إصدار رسائل غير متوافق");
            return;
        }
        String type = message.optString("type", "");
        int side = message.optInt("side", 0);
        if (LanGameProtocol.HELLO.equals(type)) {
            if (side != (localSide == 1 ? 2 : 1)) {
                listener.onProtocolError("بيانات لاعب غير صالحة"); return;
            }
            listener.onPeerInfo(side, message.optString("name", "لاعب"),
                    Math.max(0, Math.min(5, message.optInt("avatar", 0))));
            return;
        }
        int turn = message.optInt("turnId", -1);
        if (side != (localSide == 1 ? 2 : 1) || turn != expectedTurn) {
            listener.onProtocolError("تم تجاهل رسالة من دور غير متوقع");
            return;
        }
        if (LanGameProtocol.ROLL.equals(type)) {
            if (activeSide != side || rollReceived || rolledValue != 0
                    || !LanGameProtocol.isValidRoll(message, expectedTurn)) {
                listener.onProtocolError("رمية غير صالحة أو مكررة");
                return;
            }
            int value = message.optInt("value");
            rolledValue = value;
            rollReceived = true;
            listener.onRoll(side, value, message.optBoolean("automatic", false));
        } else if (LanGameProtocol.MOVE.equals(type)) {
            if (activeSide != side || !rollReceived || moveReceived
                    || !LanGameProtocol.isValidMove(message, expectedTurn, rolledValue)) {
                listener.onProtocolError("حركة غير صالحة أو مكررة");
                return;
            }
            int lane = message.optInt("lane");
            moveReceived = true;
            listener.onMove(side, lane, rolledValue);
        } else if (LanGameProtocol.TURN_CANCEL.equals(type)) {
            if (activeSide != side || moveReceived
                    || message.optInt("value", 0) != rolledValue) {
                listener.onProtocolError("إلغاء دور غير صالح");
                return;
            }
            timer.cancel();
            listener.onTurnCancelled(side);
        } else {
            listener.onProtocolError("نوع رسالة غير معروف");
        }
    }

    @Override public void onDisconnected(String reason) {
        connected = false;
        timer.cancel();
        listener.onDisconnected(reason);
    }

    public void close() {
        connected = false;
        timer.cancel();
        connection.close();
    }
}
