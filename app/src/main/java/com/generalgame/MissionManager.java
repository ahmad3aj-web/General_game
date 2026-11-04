package com.generalgame;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** Offline daily/weekly mission engine. All state is committed synchronously. */
public final class MissionManager {
    private static final String PREFS="GENERAL_MISSIONS_V1";
    private static final TimeZone JORDAN=TimeZone.getTimeZone("Asia/Amman");
    private final SharedPreferences prefs;
    private final CardInventory inventory;
    private final Random random=new Random();

    public static final class MissionTask {
        public final String id,title,type;
        public final int target,reward,difficulty,progress;
        public final boolean completed;
        MissionTask(String id,String title,String type,int target,int reward,int difficulty,int progress,boolean completed) {
            this.id=id;this.title=title;this.type=type;this.target=target;this.reward=reward;
            this.difficulty=difficulty;this.progress=progress;this.completed=completed;
        }
    }

    private static final class Template {
        final String id,title,type; final int target,difficulty;
        Template(String id,String title,String type,int target,int difficulty) {
            this.id=id;this.title=title;this.type=type;this.target=target;this.difficulty=difficulty;
        }
    }

    private static final Template[] BANK={
        new Template("d_win1","اربح مباراة واحدة ضد جنيرال","wins",1,0),
        new Template("d_win2","اربح مباراتين ضد جنيرال","wins",2,0),
        new Template("d_destroy1","دمّر سلاحاً واحداً لجنيرال","destroys",1,0),
        new Template("d_destroy2","دمّر سلاحين لجنيرال","destroys",2,0),
        new Template("d_noloss","اربح دون خسارة أي سلاح","no_loss",1,0),
        new Template("d_beginner","اربح على المبتدئ أو أعلى","difficulty",1,0),
        new Template("d_win3","اربح 3 مباريات ضد جنيرال","wins",3,1),
        new Template("d_destroy5","دمّر 5 أسلحة لجنيرال","destroys",5,1),
        new Template("d_streak2","حقق انتصارين متتاليين","streak",2,1),
        new Template("d_medium2","اربح على المتوسط أو أعلى مرتين","medium_wins",2,1),
        new Template("d_perfect1","حقق انتصاراً مثالياً واحداً","perfect",1,1),
        new Template("d_pro","اربح على المحترف أو أعلى","pro_wins",1,2),
        new Template("d_legend","اربح على الأسطوري","legend_wins",1,2),
        new Template("d_streak3","حقق 3 انتصارات متتالية","streak",3,2),
        new Template("d_destroy9","دمّر 9 أسلحة لجنيرال","destroys",9,2),
        new Template("d_perfect2","حقق انتصارين مثاليين","perfect",2,2),
        new Template("w_wins5","اربح 5 مباريات ضد جنيرال","wins",5,1),
        new Template("w_destroy12","دمّر 12 سلاحاً لجنيرال","destroys",12,1),
        new Template("w_medium5","اربح على المتوسط أو أعلى 5 مرات","medium_wins",5,1),
        new Template("w_perfect3","حقق 3 انتصارات مثالية","perfect",3,1),
        new Template("w_wins10","اربح 10 مباريات ضد جنيرال","wins",10,2),
        new Template("w_destroy20","دمّر 20 سلاحاً لجنيرال","destroys",20,2),
        new Template("w_pro5","اربح على المحترف أو أعلى 5 مرات","pro_wins",5,2),
        new Template("w_streak5","حقق 5 انتصارات متتالية","streak",5,2),
        new Template("w_perfect5","حقق 5 انتصارات مثالية","perfect",5,2),
        new Template("w_legend10","اربح على الأسطوري 10 مرات","legend_wins",10,3),
        new Template("w_streak10","حقق 10 انتصارات متتالية","streak",10,3),
        new Template("w_perfect10","حقق 10 انتصارات مثالية","perfect",10,3),
        new Template("w_wins20","اربح 20 مباراة ضد جنيرال","wins",20,3)
    };

    public MissionManager(Context context,CardInventory inventory) {
        this.prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        this.inventory=inventory;
        recoverInterruptedMatch();
        refreshPeriods();
        applyPendingGrants();
        applyPendingLoginGrant();
    }

    private synchronized long trustedNow() {
        long now=System.currentTimeMillis();
        long last=prefs.getLong("last_trusted_time",0L);
        long accepted=Math.max(now,last);
        if(accepted>last)prefs.edit().putLong("last_trusted_time",accepted).commit();
        return accepted;
    }

    private String dayKey(long time) {
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);
        f.setTimeZone(JORDAN);return f.format(new java.util.Date(time));
    }
    private String weekKey(long time) {
        Calendar c=Calendar.getInstance(JORDAN,Locale.US);c.setTimeInMillis(time);
        int days=(c.get(Calendar.DAY_OF_WEEK)-Calendar.FRIDAY+7)%7;
        c.add(Calendar.DAY_OF_YEAR,-days);
        return dayKey(c.getTimeInMillis());
    }

    private synchronized void refreshPeriods() {
        long now=trustedNow();
        String day=dayKey(now),week=weekKey(now);
        if(!day.equals(prefs.getString("daily_period",""))) {
            prefs.edit().putString("daily_period",day).putString("daily_tasks",makeTasks(false).toString()).commit();
        }
        if(!week.equals(prefs.getString("weekly_period",""))) {
            prefs.edit().putString("weekly_period",week).putString("weekly_tasks",makeTasks(true).toString()).commit();
        }
    }

    private JSONArray makeTasks(boolean weekly) {
        List<Template> pool=new ArrayList<>();
        for(Template t:BANK) if(weekly ? t.id.startsWith("w_") : t.id.startsWith("d_")) pool.add(t);
        List<Template> chosen=new ArrayList<>();
        int[] distribution=weekly?new int[]{0,2,2,1}:new int[]{1,1,1};
        for(int difficulty=0;difficulty<distribution.length;difficulty++) {
            List<Template> eligible=new ArrayList<>();
            for(Template t:pool)if(t.difficulty==difficulty)eligible.add(t);
            Collections.shuffle(eligible,random);
            for(int i=0;i<distribution[difficulty] && !eligible.isEmpty();i++)chosen.add(eligible.remove(0));
        }
        Collections.shuffle(chosen,random);
        JSONArray result=new JSONArray();
        for(Template t:chosen)try {
            JSONObject o=new JSONObject();o.put("id",t.id);o.put("title",t.title);o.put("type",t.type);
            o.put("target",t.target);o.put("difficulty",t.difficulty);
            o.put("reward",weekly?(t.difficulty==1?3:t.difficulty==2?5:7):(t.difficulty==0?1:t.difficulty==1?2:3));
            o.put("progress",0);o.put("completed",false);result.put(o);
        } catch(Exception ignored) {}
        return result;
    }

    public synchronized int recordDailyLogin() {
        refreshPeriods();
        String today=dayKey(trustedNow());
        if(today.equals(prefs.getString("login_reward_day","")))return -1;
        int type=random.nextInt(CardInventory.CARD_TYPES);
        String token="daily-login-"+today;
        prefs.edit().putString("login_reward_day",today).putInt("login_reward_type",type).putBoolean("login_reward_pending",true).commit();
        applyPendingLoginGrant();
        return type;
    }

    private void applyPendingLoginGrant() {
        if(!prefs.getBoolean("login_reward_pending",false))return;
        int type=prefs.getInt("login_reward_type",-1);
        if(type>=0 && type<CardInventory.CARD_TYPES) {
            int[] grant=new int[4];grant[type]=1;
            inventory.addCardsOnce("daily-login-"+prefs.getString("login_reward_day",""),grant);
        }
    }

    public synchronized int getPendingLoginCard() {
        return prefs.getBoolean("login_reward_pending",false)?prefs.getInt("login_reward_type",-1):-1;
    }
    public synchronized void acknowledgeLoginCard() {
        prefs.edit().putBoolean("login_reward_pending",false).commit();
    }

    public synchronized void beginTrackedMatch() {
        prefs.edit().putBoolean("match_active",true).putInt("match_destroyed",0).commit();
    }

    /** Called immediately when a General weapon is first destroyed. */
    public synchronized void recordGeneralWeaponDestroyed() {
        if(!prefs.getBoolean("match_active",false))return;
        prefs.edit().putInt("match_destroyed",prefs.getInt("match_destroyed",0)+1).commit();
        addProgress("destroys",1);
    }

    public synchronized void finishTrackedMatch(boolean won,int difficulty,int lostPlayerWeapons,boolean perfect) {
        if(!prefs.getBoolean("match_active",false))return;
        int streak=prefs.getInt("win_streak",0);
        if(won) {
            streak++;
            addProgress("wins",1);
            addProgress("difficulty",1);
            setStreakProgress(streak);
            if(difficulty>=1)addProgress("medium_wins",1);
            if(difficulty>=2)addProgress("pro_wins",1);
            if(difficulty>=3)addProgress("legend_wins",1);
            if(lostPlayerWeapons==0)addProgress("no_loss",1);
            if(perfect)addProgress("perfect",1);
        } else { streak=0; setStreakProgress(0); }
        prefs.edit().putInt("win_streak",streak).putBoolean("match_active",false).putInt("match_destroyed",0).commit();
    }

    private void recoverInterruptedMatch() {
        if(prefs.getBoolean("match_active",false)) {
            // Per-weapon destruction progress was already committed as it happened.
            prefs.edit().putInt("win_streak",0).putBoolean("match_active",false).putInt("match_destroyed",0).commit();
        }
    }

    public synchronized boolean isTrackedMatchActive() { return prefs.getBoolean("match_active",false); }

    private void setStreakProgress(int streak) {
        setTaskProgress("daily_tasks","streak",streak);
        setTaskProgress("weekly_tasks","streak",streak);
    }

    private void setTaskProgress(String key,String type,int value) {
        JSONArray tasks;try{tasks=new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return;}
        for(int i=0;i<tasks.length();i++)try {
            JSONObject o=tasks.getJSONObject(i);if(o.optBoolean("completed") || !type.equals(o.optString("type")))continue;
            o.put("progress",Math.min(o.optInt("target",1),Math.max(0,value)));
            if(value>=o.optInt("target",1)){
                o.put("completed",true);
                prefs.edit().putString(key,tasks.toString()).commit();
                String token=key+"-"+prefs.getString(key.equals("daily_tasks")?"daily_period":"weekly_period","")+"-"+o.optString("id");
                int[] grant=new int[4];for(int n=0;n<o.optInt("reward",1);n++)grant[random.nextInt(4)]++;
                addPendingGrant(token,grant);
            } else prefs.edit().putString(key,tasks.toString()).commit();
        }catch(Exception ignored){}
    }

    private void addProgress(String type,int amount) {
        if(amount<=0)return;
        updateTasks("daily_tasks",type,amount);
        updateTasks("weekly_tasks",type,amount);
    }

    private void updateTasks(String key,String type,int amount) {
        JSONArray tasks;
        try{tasks=new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return;}
        for(int i=0;i<tasks.length();i++)try {
            JSONObject o=tasks.getJSONObject(i);
            if(o.optBoolean("completed") || !type.equals(o.optString("type")))continue;
            int progress=o.optInt("progress",0)+amount,target=o.optInt("target",1);
            o.put("progress",Math.min(target,progress));
            boolean completed=progress>=target;
            if(completed)o.put("completed",true);
            // Persist progress/completion before attempting the inventory grant.
            prefs.edit().putString(key,tasks.toString()).commit();
            if(completed) {
                String token=key+"-"+prefs.getString(key.equals("daily_tasks")?"daily_period":"weekly_period","")+"-"+o.optString("id");
                int[] grant=new int[CardInventory.CARD_TYPES];
                for(int n=0;n<o.optInt("reward",1);n++)grant[random.nextInt(CardInventory.CARD_TYPES)]++;
                addPendingGrant(token,grant);
            }
        }catch(Exception ignored){}
    }

    private void addPendingGrant(String token,int[] grant) {
        String pending=prefs.getString("pending_grants","");
        if(pending.contains(token+"|"))return;
        String encoded=token+"|"+grant[0]+","+grant[1]+","+grant[2]+","+grant[3];
        prefs.edit().putString("pending_grants",pending+(pending.isEmpty()?"":";")+encoded).commit();
        int[] total=getPendingMissionRewards();
        for(int i=0;i<4;i++)total[i]+=grant[i];
        prefs.edit().putString("pending_mission_popup",join(total)).commit();
        applyPendingGrants();
    }

    private void applyPendingGrants() {
        String pending=prefs.getString("pending_grants","");
        if(pending.isEmpty())return;
        for(String entry:pending.split(";"))try {
            String[] parts=entry.split("\\|",2);if(parts.length!=2)continue;
            String[] nums=parts[1].split(",");
            int[] counts=new int[4];for(int i=0;i<4;i++)counts[i]=Integer.parseInt(nums[i]);
            inventory.addCardsOnce(parts[0],counts);
        }catch(Exception ignored){}
    }

    public synchronized int[] getPendingMissionRewards() {
        String[] p=prefs.getString("pending_mission_popup","0,0,0,0").split(",");
        int[] result=new int[4];for(int i=0;i<4;i++)try{result[i]=Integer.parseInt(p[i]);}catch(Exception ignored){}
        return result;
    }
    public synchronized void acknowledgeMissionRewards() {
        prefs.edit().putString("pending_mission_popup","0,0,0,0").commit();
    }

    private String join(int[] values){return values[0]+","+values[1]+","+values[2]+","+values[3];}

    public synchronized List<MissionTask> getTasks(boolean weekly) {
        refreshPeriods();
        JSONArray a;
        try{a=new JSONArray(prefs.getString(weekly?"weekly_tasks":"daily_tasks","[]"));}catch(Exception e){a=new JSONArray();}
        List<MissionTask> result=new ArrayList<>();
        for(int i=0;i<a.length();i++)try {
            JSONObject o=a.getJSONObject(i);
            result.add(new MissionTask(o.optString("id"),o.optString("title"),o.optString("type"),
                o.optInt("target",1),o.optInt("reward",1),o.optInt("difficulty",0),
                o.optInt("progress",0),o.optBoolean("completed",false)));
        }catch(Exception ignored){}
        return result;
    }

    public synchronized String getCountdown(boolean weekly) {
        long now=trustedNow();
        Calendar next=Calendar.getInstance(JORDAN,Locale.US);next.setTimeInMillis(now);
        next.set(Calendar.HOUR_OF_DAY,0);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);
        if(weekly) {
            int days=(Calendar.FRIDAY-next.get(Calendar.DAY_OF_WEEK)+7)%7;
            if(days==0 && now>=next.getTimeInMillis())days=7;
            next.add(Calendar.DAY_OF_YEAR,days);
        } else next.add(Calendar.DAY_OF_YEAR,1);
        long left=Math.max(0,next.getTimeInMillis()-now);
        long hours=left/3600000L,minutes=(left/60000L)%60L,seconds=(left/1000L)%60L;
        return String.format(Locale.US,"%02d:%02d:%02d",hours,minutes,seconds);
    }
}
