package com.generalgame;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Local, persistent card inventory and match reservation ledger.
 *
 * This class deliberately has no UI dependencies. MainActivity can integrate it
 * incrementally without changing the existing progress/history stores.
 */
public final class CardInventory {
    public static final int REPAIR = 0;
    public static final int DOUBLE_DICE = 1;
    public static final int CYBER_WARFARE = 2;
    public static final int SHIELD = 3;
    public static final int AIRSTRIKE = 4;
    public static final int SMOKE_SCREEN = 5;
    public static final int MILITARY_SUPPLY = 6;
    public static final int RADAR = 7;
    public static final int MINEFIELD = 8;
    public static final int MINE_DETECTOR = 9;
    public static final int CARD_TYPES = 10;
    public static final int MAX_MATCH_CARDS = 3;

    private static final String PREFS = "GENERAL_CARD_INVENTORY";
    private static final String[] COUNT_KEYS = {
        "count_repair", "count_double_dice", "count_cyber_warfare", "count_shield",
        "count_airstrike", "count_smoke_screen", "count_military_supply",
        "count_radar", "count_minefield", "count_mine_detector"
    };
    private static final String KEY_PENDING = "pending_selection";
    private static final String KEY_ACTIVE = "active_match_cards";
    private static final String KEY_USED = "used_match_cards";
    private static final String KEY_MATCH_ACTIVE = "match_active";
    private static final String KEY_RANK_CLAIMS = "rank_claims";
    private static final String KEY_LAST_LOGIN_DAY = "last_login_day";
    private static final String KEY_LOGIN_STREAK = "login_streak";
    private static final String KEY_WEEKLY_CLAIMED = "weekly_claimed";
    private static final String KEY_TEST_GRANT_V1 = "test_card_bundle_granted_v1";
    private final SharedPreferences prefs;
    private final Random random = new Random();

    public CardInventory(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        grantNewCardTypesToExistingInstall();
        grantTestingBundleOnce();
        recoverInterruptedSetup();
    }

    /** Existing players receive three copies of each newly introduced card once. */
    private synchronized void grantNewCardTypesToExistingInstall() {
        final String key = "new_card_types_bundle_v1";
        if (prefs.getBoolean(key, false)) return;
        if (!prefs.getBoolean(KEY_TEST_GRANT_V1, false)) {
            // Fresh installs already receive the standard starter bundle below.
            prefs.edit().putBoolean(key, true).commit();
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            edit.putInt(COUNT_KEYS[i], getCount(i) + 3);
        }
        edit.putBoolean(key, true);
        edit.commit();
    }

    /**
     * One-time testing bundle for this experimental build: five of each card.
     * The preference guard prevents repeated grants on subsequent app launches.
     */
    private synchronized void grantTestingBundleOnce() {
        if (prefs.getBoolean(KEY_TEST_GRANT_V1, false)) return;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            edit.putInt(COUNT_KEYS[i], Math.max(getCount(i), 5));
        }
        edit.putBoolean(KEY_TEST_GRANT_V1, true);
        edit.commit();
    }

    /** One-time top-up for the first build that includes the mission system. */
    public synchronized void topUpCardsToFiveOnce() {
        final String key = "mission_feature_topup_v1";
        if (prefs.getBoolean(key, false)) return;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            edit.putInt(COUNT_KEYS[i], Math.max(getCount(i), 5));
        }
        edit.putBoolean(key, true);
        edit.commit();
    }

    private void recoverInterruptedSetup() {
        // A selection is only a reservation until a match starts. If the app was
        // closed before starting, simply clear it; counts were never deducted.
        if (!prefs.getBoolean(KEY_MATCH_ACTIVE, false)
                && prefs.getString(KEY_PENDING, "").length() > 0) {
            prefs.edit().remove(KEY_PENDING).commit();
        }
    }

    public synchronized int getCount(int type) {
        checkType(type);
        return Math.max(0, prefs.getInt(COUNT_KEYS[type], 0));
    }

    public synchronized int getTotalCount() {
        int total = 0;
        for (int i = 0; i < CARD_TYPES; i++) total += getCount(i);
        return total;
    }

    public synchronized int[] getCounts() {
        int[] result = new int[CARD_TYPES];
        for (int i = 0; i < CARD_TYPES; i++) result[i] = getCount(i);
        return result;
    }

    /** Idempotent grant: the same reward token can never add cards twice. */
    public synchronized boolean addCardsOnce(String token, int[] amounts) {
        if (token == null || token.length() == 0 || amounts == null || amounts.length != CARD_TYPES) return false;
        String grants = prefs.getString("applied_reward_tokens", "");
        String wrapped = "|" + grants + "|";
        if (wrapped.contains("|" + token + "|")) return false;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            int amount = Math.max(0, amounts[i]);
            if (amount > 0) edit.putInt(COUNT_KEYS[i], getCount(i) + amount);
        }
        edit.putString("applied_reward_tokens", grants + (grants.length() == 0 ? "" : "|") + token);
        return edit.commit();
    }

    /** Adds one card of a uniformly random type and returns its type. */
    public synchronized int addRandomCard() {
        int type = random.nextInt(CARD_TYPES);
        addCards(type, 1);
        return type;
    }

    public synchronized void addCards(int type, int amount) {
        checkType(type);
        if (amount <= 0) return;
        prefs.edit().putInt(COUNT_KEYS[type], getCount(type) + amount).commit();
    }

    /** Adds a bundle, independently drawing each card with equal type odds. */
    public synchronized int[] addRandomCards(int amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        int[] awarded = new int[CARD_TYPES];
        // The first three rewards (and the first four when applicable) are
        // guaranteed to be different card types. Only extra rewards may repeat.
        int[] pool = new int[CARD_TYPES];
        for (int i = 0; i < CARD_TYPES; i++) pool[i] = i;
        for (int i = pool.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = pool[i]; pool[i] = pool[j]; pool[j] = swap;
        }
        int distinct = Math.min(amount, CARD_TYPES);
        for (int i = 0; i < distinct; i++) awarded[pool[i]]++;
        for (int i = distinct; i < amount; i++) awarded[random.nextInt(CARD_TYPES)]++;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            if (awarded[i] > 0) edit.putInt(COUNT_KEYS[i], getCount(i) + awarded[i]);
        }
        edit.commit();
        return awarded;
    }

    /** Weekly attendance reward: exactly one of each type, once per 7-day streak. */
    public synchronized boolean claimWeeklyLoginReward() {
        String today = localDay();
        if (!today.equals(prefs.getString(KEY_LAST_LOGIN_DAY, ""))) return false;
        int streak = prefs.getInt(KEY_LOGIN_STREAK, 0);
        if (streak < 7 || prefs.getBoolean(KEY_WEEKLY_CLAIMED, false)) return false;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) edit.putInt(COUNT_KEYS[i], getCount(i) + 1);
        edit.putBoolean(KEY_WEEKLY_CLAIMED, true);
        return edit.commit();
    }

    /** Call once on app entry. Returns current consecutive local-day streak (0..7). */
    public synchronized int recordDailyLogin() {
        String today = localDay();
        String last = prefs.getString(KEY_LAST_LOGIN_DAY, "");
        if (today.equals(last)) return Math.min(7, prefs.getInt(KEY_LOGIN_STREAK, 0));

        int streak;
        if (isYesterday(last, today)) {
            streak = Math.min(7, prefs.getInt(KEY_LOGIN_STREAK, 0) + 1);
        } else {
            streak = 1;
            // A new streak earns a new weekly attendance reward.
            prefs.edit().putBoolean(KEY_WEEKLY_CLAIMED, false).commit();
        }
        prefs.edit().putString(KEY_LAST_LOGIN_DAY, today).putInt(KEY_LOGIN_STREAK, streak).commit();
        if (streak == 7) claimWeeklyLoginReward();
        return streak;
    }

    public synchronized int getLoginStreak() {
        return Math.min(7, prefs.getInt(KEY_LOGIN_STREAK, 0));
    }

    /** A rank index is zero-based: first promotion is index 0 and awards one card. */
    public synchronized boolean claimRankPromotion(int promotionIndex) {
        if (promotionIndex < 0) return false;
        String claims = prefs.getString(KEY_RANK_CLAIMS, "");
        String token = "," + promotionIndex + ",";
        if (("," + claims + ",").contains(token)) return false;
        int amount = promotionIndex + 1;
        int[] awarded = new int[CARD_TYPES];
        for (int i = 0; i < amount; i++) awarded[random.nextInt(CARD_TYPES)]++;
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            if (awarded[i] > 0) edit.putInt(COUNT_KEYS[i], getCount(i) + awarded[i]);
        }
        edit.putString(KEY_RANK_CLAIMS, claims + (claims.length() == 0 ? "" : ",") + promotionIndex);
        return edit.commit();
    }

    /** Select or deselect one distinct card type before a match. */
    public synchronized boolean toggleSelection(int type) {
        checkType(type);
        if (prefs.getBoolean(KEY_MATCH_ACTIVE, false)) return false;
        boolean[] selected = parseFlags(prefs.getString(KEY_PENDING, ""));
        if (selected[type]) {
            selected[type] = false;
            saveFlags(KEY_PENDING, selected);
            return true;
        }
        int selectedCount = countFlags(selected);
        if (selectedCount >= MAX_MATCH_CARDS || getCount(type) <= 0) return false;
        selected[type] = true;
        saveFlags(KEY_PENDING, selected);
        return true;
    }

    public synchronized boolean isSelected(int type) {
        checkType(type);
        return parseFlags(prefs.getString(KEY_PENDING, ""))[type];
    }

    public synchronized void clearSelection() {
        if (!prefs.getBoolean(KEY_MATCH_ACTIVE, false)) {
            prefs.edit().remove(KEY_PENDING).commit();
        }
    }

    /**
     * Called when the player opens the pre-match setup screen. Any match ledger
     * still active at this point belongs to an interrupted previous session:
     * settle it normally (used cards stay consumed; unused cards are returned),
     * then allow a fresh selection.
     */
    public synchronized void prepareNewMatchSetup() {
        if (prefs.getBoolean(KEY_MATCH_ACTIVE, false)) {
            finishMatchNormally();
        }
        clearSelection();
    }

    public synchronized int getSelectedCount() {
        return countFlags(parseFlags(prefs.getString(KEY_PENDING, "")));
    }

    /** Permanently reserves selected copies at the exact moment the match begins. */
    public synchronized boolean beginMatch() {
        if (prefs.getBoolean(KEY_MATCH_ACTIVE, false)) return false;
        boolean[] selected = parseFlags(prefs.getString(KEY_PENDING, ""));
        int count = countFlags(selected);
        if (count > MAX_MATCH_CARDS) return false;
        for (int i = 0; i < CARD_TYPES; i++) {
            if (selected[i] && getCount(i) <= 0) return false;
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            if (selected[i]) edit.putInt(COUNT_KEYS[i], getCount(i) - 1);
        }
        edit.putString(KEY_ACTIVE, flagsToString(selected));
        edit.putString(KEY_USED, "");
        edit.putString(KEY_PENDING, "");
        edit.putBoolean(KEY_MATCH_ACTIVE, true);
        return edit.commit();
    }

    /** Marks a carried card as consumed; can only consume a card in this match. */
    public synchronized boolean markUsed(int type) {
        checkType(type);
        if (!prefs.getBoolean(KEY_MATCH_ACTIVE, false)) return false;
        boolean[] active = parseFlags(prefs.getString(KEY_ACTIVE, ""));
        boolean[] used = parseFlags(prefs.getString(KEY_USED, ""));
        if (!active[type] || used[type]) return false;
        used[type] = true;
        return saveFlags(KEY_USED, used);
    }

    /** Normal completion consumes used cards and returns unused cards to inventory. */
    public synchronized boolean finishMatchNormally() {
        if (!prefs.getBoolean(KEY_MATCH_ACTIVE, false)) return false;
        boolean[] active = parseFlags(prefs.getString(KEY_ACTIVE, ""));
        boolean[] used = parseFlags(prefs.getString(KEY_USED, ""));
        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < CARD_TYPES; i++) {
            if (active[i] && !used[i]) edit.putInt(COUNT_KEYS[i], getCount(i) + 1);
        }
        clearMatch(edit);
        return edit.commit();
    }

    /** Surrender consumes every card taken into the match, including unused cards. */
    public synchronized boolean abandonMatch() {
        if (!prefs.getBoolean(KEY_MATCH_ACTIVE, false)) return false;
        SharedPreferences.Editor edit = prefs.edit();
        clearMatch(edit);
        return edit.commit();
    }

    public synchronized boolean isMatchActive() {
        return prefs.getBoolean(KEY_MATCH_ACTIVE, false);
    }

    public synchronized boolean[] getActiveCards() {
        return parseFlags(prefs.getString(KEY_ACTIVE, ""));
    }

    public synchronized boolean[] getUsedCards() {
        return parseFlags(prefs.getString(KEY_USED, ""));
    }

    private void clearMatch(SharedPreferences.Editor edit) {
        edit.remove(KEY_ACTIVE).remove(KEY_USED).remove(KEY_PENDING).putBoolean(KEY_MATCH_ACTIVE, false);
    }

    private boolean saveFlags(String key, boolean[] flags) {
        return prefs.edit().putString(key, flagsToString(flags)).commit();
    }

    private static String flagsToString(boolean[] flags) {
        StringBuilder b = new StringBuilder(CARD_TYPES);
        for (boolean flag : flags) b.append(flag ? '1' : '0');
        return b.toString();
    }

    private static boolean[] parseFlags(String value) {
        boolean[] flags = new boolean[CARD_TYPES];
        if (value == null) return flags;
        for (int i = 0; i < CARD_TYPES && i < value.length(); i++) flags[i] = value.charAt(i) == '1';
        return flags;
    }

    private static int countFlags(boolean[] flags) {
        int count = 0;
        for (boolean flag : flags) if (flag) count++;
        return count;
    }

    private static String localDay() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static boolean isYesterday(String previous, String today) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date a = f.parse(previous), b = f.parse(today);
            return a != null && b != null && b.getTime() - a.getTime() == 24L * 60L * 60L * 1000L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void checkType(int type) {
        if (type < 0 || type >= CARD_TYPES) throw new IllegalArgumentException("Unknown card type");
    }
}
