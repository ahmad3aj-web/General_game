package com.generalgame;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Firebase-backed two-player match transport. Each player writes only their own
 * actions; the other client validates and applies them in turn order.
 */
public final class OnlineMatchSession {
    public interface Listener {
        void onConnected(String roomCode);
        void onPeerInfo(int side, String name, int avatar);
        void onPeerJoined();
        void onOpeningRolls(int blueValue, int redValue, boolean tied);
        void onOpeningWinner(int startingSide);
        void onRematchReady();
        void onTurnStarted(int turnId, int activeSide, boolean localTurn);
        void onCountdown(int secondsRemaining);
        void onRoll(int side, int value, boolean automatic);
        void onMove(int side, int lane, int value);
        void onTurnCancelled(int side);
        void onDisconnected(String reason);
        void onError(String reason);
    }

    private static final String DATABASE_URL =
            "https://general-online-2026-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseDatabase database = FirebaseDatabase.getInstance(DATABASE_URL);
    private final DatabaseReference rooms = database.getReference("rooms");
    private final DatabaseReference quickMatches = database.getReference("quickMatch");
    private Boolean databaseConnected = null;
    private volatile String httpsProbeResult = "لم يكتمل فحص HTTPS بعد";
    private volatile String controlProbeResult = "لم يكتمل فحص الاتصال العام بعد";
    private volatile String connectionError = "";
    private final ValueEventListener connectionListener = new ValueEventListener() {
        @Override public void onDataChange(DataSnapshot snapshot) {
            Boolean value = snapshot.getValue(Boolean.class);
            databaseConnected = value;
        }
        @Override public void onCancelled(DatabaseError error) {
            databaseConnected = false;
            connectionError = error == null ? "خطأ غير معروف" : error.getMessage();
        }
    };
    private boolean quickMatchMode;
    private long quickMatchCreatedAt;
    private long quickMatchStartedAt;
    private String quickMatchName = "لاعب";
    private int quickMatchAvatar;
    private Runnable quickMatchPoll;
    private DatabaseReference room;
    private ChildEventListener actionListener;
    private ValueEventListener playersListener;
    private String uid;
    private String roomCode;
    private int localSide;
    private boolean connected;
    private int openingRound;
    private int localOpeningValue;
    private int remoteOpeningValue;
    private int expectedTurn;
    private int activeSide;
    private int rolledValue;
    private boolean rollReceived;
    private boolean moveReceived;
    private boolean localRematchRequested;
    private boolean remoteRematchRequested;
    private boolean rematchStarted;
    private final LanTurnTimer timer;

    public OnlineMatchSession(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("listener");
        this.listener = listener;
        database.getReference(".info/connected").addValueEventListener(connectionListener);
        timer = new LanTurnTimer(new LanTurnTimer.Listener() {
            @Override public void onTick(int seconds) { listener.onCountdown(seconds); }
            @Override public void onTimeout() { rollNow(true); }
        });
    }

    public String getRoomCode() { return roomCode; }
    public int getLocalSide() { return localSide; }
    public boolean isConnected() { return connected; }
    public boolean isLocalTurn() { return connected && activeSide == localSide; }

    public void createRoom(String name, int avatar) {
        quickMatchMode = false;
        authenticate(() -> createRoomAfterAuth(name, avatar));
    }

    public void quickMatch(String name, int avatar) {
        quickMatchMode = true;
        quickMatchName = name == null || name.trim().isEmpty() ? "لاعب" : name.trim();
        quickMatchAvatar = Math.max(0, Math.min(7, avatar));
        quickMatchStartedAt = System.currentTimeMillis();
        authenticate(() -> createRoomAfterAuth(quickMatchName, quickMatchAvatar));
    }

    public void joinRoom(String code, String name, int avatar) {
        final String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.length() != 8) {
            listener.onError("رمز الغرفة يجب أن يتكون من 8 أحرف");
            return;
        }
        authenticate(() -> joinRoomAfterAuth(normalized, name, avatar));
    }

    private void authenticate(Runnable next) {
        if (auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
            next.run();
            return;
        }
        auth.signInAnonymously().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                String detail = task.getException() == null ? "سبب غير معروف" : task.getException().getMessage();
                listener.onError("فشل تسجيل الدخول إلى Firebase: " + detail);
                return;
            }
            uid = auth.getCurrentUser().getUid();
            next.run();
        });
    }

    private void createRoomAfterAuth(String name, int avatar) {
        startHttpsProbe();
        String code = makeCode();
        DatabaseReference candidate = rooms.child(code);
        Map<String, Object> profile = profile(1, name, avatar);
        Map<String, Object> data = new HashMap<>();
        data.put("hostUid", uid);
        data.put("guestUid", "");
        data.put("status", "waiting");
        data.put("createdAt", System.currentTimeMillis());
        Map<String, Object> players = new HashMap<>();
        players.put(uid, profile);
        data.put("players", players);
        final boolean[] finished = {false};
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            String connection = databaseConnected == null ? "لم تصل حالة الاتصال بعد"
                    : (databaseConnected ? "متصل بخادم Realtime Database" : "غير متصل بخادم Realtime Database");
            String authState = auth.getCurrentUser() == null ? "غير مسجل الدخول"
                    : (auth.getCurrentUser().isAnonymous() ? "دخول مجهول ناجح" : "مستخدم مسجل");
            listener.onError("انتهت مهلة إنشاء الغرفة بعد 15 ثانية.\n"
                    + "تشخيص الاتصال: " + connection + ".\n"
                    + "حالة المصادقة: " + authState + ".\n"
                    + "قاعدة البيانات: europe-west1.\n"
                    + "فحص Firebase HTTPS من داخل التطبيق: " + httpsProbeResult + ".\n"
                    + "فحص HTTPS عام (Google): " + controlProbeResult + ".\n"
                    + (connectionError.isEmpty() ? "" : "تفصيل خطأ الاتصال: " + connectionError + ".\n")
                    + "لم يصل تأكيد الكتابة من الخادم؛ لا تغيّر قواعد Firebase الآن.");
        };
        main.postDelayed(timeout, 15000L);
        candidate.setValue(data).addOnCompleteListener(task -> {
            if (finished[0]) return;
            finished[0] = true;
            main.removeCallbacks(timeout);
            if (!task.isSuccessful()) {
                String detail = task.getException() == null
                        ? "سبب غير معروف" : task.getException().toString();
                String connection = databaseConnected == null ? "غير معروف"
                        : (databaseConnected ? "متصل" : "غير متصل");
                listener.onError("فشل إنشاء غرفة الإنترنت في Realtime Database.\n"
                        + "التفاصيل: " + detail + "\n"
                        + "حالة الاتصال بالخادم: " + connection);
                return;
            }
            localSide = 1;
            roomCode = code;
            room = candidate;
            connected = true;
            attachListeners();
            listener.onConnected(code);
            if (quickMatchMode) publishQuickMatch(name, avatar);
        });
    }

    /**
     * Checks ordinary HTTPS reachability from inside the app while the RTDB SDK
     * attempts its persistent connection. HTTP 401/403 is expected because
     * unauthenticated root reads are intentionally denied by database rules.
     */
    private void startHttpsProbe() {
        httpsProbeResult = "جارٍ الفحص";
        controlProbeResult = "جارٍ الفحص";
        runHttpsProbe(DATABASE_URL + "/.json", true);
        runHttpsProbe("https://www.google.com/generate_204", false);
    }

    private void runHttpsProbe(String address, boolean firebase) {
        Thread probe = new Thread(() -> {
            HttpsURLConnection connection = null;
            long started = System.currentTimeMillis();
            try {
                URL url = new URL(address);
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                int status = connection.getResponseCode();
                long elapsed = System.currentTimeMillis() - started;
                String result;
                if (firebase && (status == 401 || status == 403)) {
                    result = "وصل بنجاح (HTTP " + status
                            + "؛ رفض القراءة متوقع بسبب قواعد الحماية، " + elapsed + "ms)";
                } else {
                    result = "وصل بنجاح (HTTP " + status + "، " + elapsed + "ms)";
                }
                if (firebase) httpsProbeResult = result;
                else controlProbeResult = result;
            } catch (Exception error) {
                String result = "فشل: " + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : " - " + error.getMessage());
                if (firebase) httpsProbeResult = result;
                else controlProbeResult = result;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, firebase ? "GENERAL-firebase-https-probe" : "GENERAL-control-https-probe");
        probe.setDaemon(true);
        probe.start();
    }

    private void joinRoomAfterAuth(String code, String name, int avatar) {
        DatabaseReference candidate = rooms.child(code);
        candidate.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !task.getResult().exists()) {
                listener.onError("رمز الغرفة غير صحيح أو أن الغرفة غير موجودة.");
                return;
            }
            DataSnapshot snapshot = task.getResult();
            if (!"waiting".equals(snapshot.child("status").getValue(String.class))
                    || snapshot.child("guestUid").getValue(String.class) == null
                    || !snapshot.child("guestUid").getValue(String.class).isEmpty()) {
                listener.onError("الغرفة ممتلئة أو أن المباراة بدأت بالفعل.");
                return;
            }
            candidate.child("guestUid").runTransaction(new com.google.firebase.database.Transaction.Handler() {
                @Override public com.google.firebase.database.Transaction.Result doTransaction(
                        com.google.firebase.database.MutableData current) {
                    String value = current.getValue(String.class);
                    if (value == null || value.isEmpty()) current.setValue(uid);
                    return com.google.firebase.database.Transaction.success(current);
                }
                @Override public void onComplete(DatabaseError error, boolean committed,
                                                 DataSnapshot currentData) {
                    if (error != null || !committed || !uid.equals(currentData.getValue(String.class))) {
                        listener.onError("سبقك لاعب آخر إلى هذه الغرفة. اطلب رمزاً جديداً.");
                        return;
                    }
                    detachRoomListeners();
                    connected = false;
                    localSide = 2;
                    roomCode = code;
                    room = candidate;
                    Map<String, Object> player = profile(2, name, avatar);
                    room.child("players").child(uid).setValue(player).addOnCompleteListener(write -> {
                        if (!write.isSuccessful()) {
                            listener.onError("تعذر الانضمام إلى الغرفة.");
                            return;
                        }
                        connected = true;
                        attachListeners();
                        removeQuickMatchEntry();
                        quickMatchMode = false;
                        listener.onConnected(code);
                        listener.onPeerJoined();
                    });
                }
            });
        });
    }

    private void publishQuickMatch(String name, int avatar) {
        quickMatchCreatedAt = System.currentTimeMillis();
        Map<String, Object> entry = new HashMap<>();
        entry.put("hostUid", uid);
        entry.put("roomCode", roomCode);
        entry.put("createdAt", quickMatchCreatedAt);
        entry.put("status", "waiting");
        entry.put("name", name == null || name.trim().isEmpty() ? "لاعب" : name.trim());
        entry.put("avatar", Math.max(0, Math.min(7, avatar)));
        quickMatches.child(uid).onDisconnect().removeValue();
        quickMatches.child(uid).setValue(entry).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                String detail = task.getException() == null ? "سبب غير معروف" : task.getException().getMessage();
                listener.onError("تعذر دخول قائمة البحث السريع: " + detail);
                return;
            }
            scheduleQuickMatchPoll();
        });
    }

    private void scheduleQuickMatchPoll() {
        if (!quickMatchMode || !connected || localSide != 1) return;
        if (quickMatchPoll == null) {
            quickMatchPoll = new Runnable() {
                @Override public void run() { pollQuickMatch(); }
            };
        }
        main.removeCallbacks(quickMatchPoll);
        main.postDelayed(quickMatchPoll, 1800L);
    }

    private void pollQuickMatch() {
        if (!quickMatchMode || !connected || localSide != 1) return;
        if (System.currentTimeMillis() - quickMatchStartedAt > 90000L) {
            removeQuickMatchEntry();
            listener.onError("ما لقينا لاعب خلال 90 ثانية. جرّب مرة ثانية أو ادعُ صديقاً.");
            return;
        }
        quickMatches.get().addOnCompleteListener(task -> {
            if (!quickMatchMode || !connected || localSide != 1) return;
            if (!task.isSuccessful()) {
                String detail = task.getException() == null ? "سبب غير معروف" : task.getException().getMessage();
                listener.onError("تعذر البحث عن لاعب: " + detail);
                return;
            }
            DataSnapshot best = null;
            long bestTime = Long.MAX_VALUE;
            for (DataSnapshot item : task.getResult().getChildren()) {
                String host = item.child("hostUid").getValue(String.class);
                String code = item.child("roomCode").getValue(String.class);
                String status = item.child("status").getValue(String.class);
                Long created = item.child("createdAt").getValue(Long.class);
                if (host == null || uid.equals(host) || code == null || code.length() != 8
                        || !"waiting".equals(status) || created == null
                        || created < System.currentTimeMillis() - 120000L) continue;
                boolean older = created < quickMatchCreatedAt
                        || (created == quickMatchCreatedAt && host.compareTo(uid) < 0);
                if (older && created < bestTime) {
                    best = item;
                    bestTime = created;
                }
            }
            if (best != null) {
                String code = best.child("roomCode").getValue(String.class);
                main.removeCallbacks(quickMatchPoll);
                joinRoomAfterAuth(code, quickMatchName, quickMatchAvatar);
            } else {
                scheduleQuickMatchPoll();
            }
        });
    }

    private void removeQuickMatchEntry() {
        if (uid != null) quickMatches.child(uid).removeValue();
        if (quickMatchPoll != null) main.removeCallbacks(quickMatchPoll);
    }

    private void detachRoomListeners() {
        if (room != null) {
            if (actionListener != null) room.child("actions").removeEventListener(actionListener);
            if (playersListener != null) room.child("players").removeEventListener(playersListener);
            if (uid != null) room.child("players").child(uid).child("connected").setValue(false);
        }
        actionListener = null;
        playersListener = null;
    }

    private Map<String, Object> profile(int side, String name, int avatar) {
        Map<String, Object> value = new HashMap<>();
        value.put("side", side);
        value.put("name", name == null || name.trim().isEmpty() ? "لاعب" : name.trim());
        value.put("avatar", Math.max(0, Math.min(7, avatar)));
        value.put("connected", true);
        return value;
    }

    private String makeCode() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 8; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return value.toString();
    }

    private void attachListeners() {
        actionListener = new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String from = snapshot.child("fromUid").getValue(String.class);
                if (uid.equals(from)) return;
                handleAction(snapshot);
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) { }
            @Override public void onChildRemoved(DataSnapshot snapshot) { }
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) { }
            @Override public void onCancelled(DatabaseError error) {
                listener.onError("تعذر استقبال تحديثات المباراة: " + error.getMessage());
            }
        };
        room.child("actions").addChildEventListener(actionListener);
        playersListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!connected) return;
                boolean peerFound = false;
                for (DataSnapshot player : snapshot.getChildren()) {
                    String playerUid = player.getKey();
                    if (uid.equals(playerUid)) continue;
                    Integer side = player.child("side").getValue(Integer.class);
                    if (side == null) continue;
                    peerFound = true;
                    String name = player.child("name").getValue(String.class);
                    Integer avatar = player.child("avatar").getValue(Integer.class);
                    listener.onPeerInfo(side, name == null ? "لاعب" : name,
                            avatar == null ? 0 : Math.max(0, Math.min(5, avatar)));
                }
                if (peerFound) {
                    if (quickMatchMode && localSide == 1) {
                        removeQuickMatchEntry();
                        quickMatchMode = false;
                    }
                    listener.onPeerJoined();
                }
            }
            @Override public void onCancelled(DatabaseError error) {
                listener.onError("تعذر قراءة بيانات اللاعبين.");
            }
        };
        room.child("players").addValueEventListener(playersListener);
        room.child("players").child(uid).child("connected").onDisconnect().setValue(false);
    }

    private void send(String type, int turn, int value, int lane, int round, boolean automatic) {
        if (!connected || room == null) return;
        Map<String, Object> action = new HashMap<>();
        action.put("fromUid", uid);
        action.put("side", localSide);
        action.put("type", type);
        action.put("turnId", turn);
        action.put("value", value);
        action.put("lane", lane);
        action.put("round", round);
        action.put("automatic", automatic);
        action.put("createdAt", System.currentTimeMillis());
        room.child("actions").push().setValue(action).addOnFailureListener(error ->
                listener.onError("تعذر إرسال حركة المباراة. تحقق من الاتصال."));
    }

    public synchronized void startOpeningToss() {
        if (!connected) return;
        openingRound++;
        localOpeningValue = random.nextInt(6) + 1;
        remoteOpeningValue = 0;
        send("opening_roll", 0, localOpeningValue, -1, openingRound, false);
    }

    private synchronized void evaluateOpening() {
        if (localOpeningValue == 0 || remoteOpeningValue == 0) return;
        int blue = localSide == 1 ? localOpeningValue : remoteOpeningValue;
        int red = localSide == 2 ? localOpeningValue : remoteOpeningValue;
        boolean tied = blue == red;
        listener.onOpeningRolls(blue, red, tied);
        int round = openingRound;
        if (tied && localSide == 1) {
            main.postDelayed(() -> {
                synchronized (OnlineMatchSession.this) {
                    if (connected && openingRound == round && localOpeningValue == remoteOpeningValue) {
                        startOpeningToss();
                    }
                }
            }, 2200L);
        } else if (!tied) {
            int starting = blue > red ? 1 : 2;
            main.postDelayed(() -> {
                synchronized (OnlineMatchSession.this) {
                    if (connected && openingRound == round && localOpeningValue != 0
                            && remoteOpeningValue != 0) {
                        listener.onOpeningWinner(starting);
                        localRematchRequested = false;
                        remoteRematchRequested = false;
                        rematchStarted = false;
                    }
                }
            }, 2200L);
        }
    }

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

    public synchronized boolean rollNow(boolean automatic) {
        if (!connected || activeSide != localSide || expectedTurn < 1 || rollReceived || rolledValue != 0)
            return false;
        int value = random.nextInt(6) + 1;
        rolledValue = value;
        rollReceived = true;
        timer.cancel();
        send("roll", expectedTurn, value, -1, 0, automatic);
        listener.onRoll(localSide, value, automatic);
        return true;
    }

    public synchronized boolean sendMove(int lane) {
        if (!connected || activeSide != localSide || rolledValue < 1 || moveReceived || lane < 0 || lane > 2)
            return false;
        moveReceived = true;
        send("move", expectedTurn, rolledValue, lane, 0, false);
        return true;
    }

    public synchronized void cancelTurn() {
        if (!connected || activeSide != localSide || moveReceived) return;
        timer.cancel();
        moveReceived = true;
        send("turn_cancel", expectedTurn, rolledValue, -1, 0, false);
        listener.onTurnCancelled(localSide);
    }

    public synchronized void requestRematch() {
        if (!connected || localRematchRequested) return;
        localRematchRequested = true;
        send("rematch", 0, 0, -1, 0, false);
        maybeStartRematch();
    }

    private synchronized void maybeStartRematch() {
        if (!connected || !localRematchRequested || !remoteRematchRequested || rematchStarted) return;
        rematchStarted = true;
        listener.onRematchReady();
        if (localSide == 1) startOpeningToss();
    }

    private synchronized void handleAction(DataSnapshot snapshot) {
        String type = snapshot.child("type").getValue(String.class);
        Integer sideValue = snapshot.child("side").getValue(Integer.class);
        if (type == null || sideValue == null || sideValue != (localSide == 1 ? 2 : 1)) return;
        int side = sideValue;
        int turn = intValue(snapshot.child("turnId"), -1);
        int value = intValue(snapshot.child("value"), 0);
        if ("opening_roll".equals(type)) {
            int round = intValue(snapshot.child("round"), -1);
            if (round < 1 || value < 1 || value > 6 || round < openingRound) return;
            if (round > openingRound) {
                openingRound = round;
                localOpeningValue = random.nextInt(6) + 1;
                remoteOpeningValue = 0;
                send("opening_roll", 0, localOpeningValue, -1, openingRound, false);
            }
            remoteOpeningValue = value;
            evaluateOpening();
        } else if ("rematch".equals(type)) {
            remoteRematchRequested = true;
            maybeStartRematch();
        } else if ("roll".equals(type)) {
            if (turn != expectedTurn || activeSide != side || rollReceived || rolledValue != 0
                    || value < 1 || value > 6) return;
            rolledValue = value;
            rollReceived = true;
            listener.onRoll(side, value, snapshot.child("automatic").getValue(Boolean.class) != null
                    && Boolean.TRUE.equals(snapshot.child("automatic").getValue(Boolean.class)));
        } else if ("move".equals(type)) {
            int lane = intValue(snapshot.child("lane"), -1);
            if (turn != expectedTurn || activeSide != side || !rollReceived || moveReceived
                    || value != rolledValue || lane < 0 || lane > 2) return;
            moveReceived = true;
            listener.onMove(side, lane, rolledValue);
        } else if ("turn_cancel".equals(type)) {
            if (turn != expectedTurn || activeSide != side || moveReceived || value != rolledValue) return;
            timer.cancel();
            listener.onTurnCancelled(side);
        }
    }

    private int intValue(DataSnapshot snapshot, int fallback) {
        Integer value = snapshot.getValue(Integer.class);
        return value == null ? fallback : value;
    }

    public void close() {
        connected = false;
        timer.cancel();
        removeQuickMatchEntry();
        detachRoomListeners();
        database.getReference(".info/connected").removeEventListener(connectionListener);
    }
}
