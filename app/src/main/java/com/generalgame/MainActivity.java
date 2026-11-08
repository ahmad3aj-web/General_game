package com.generalgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Movie;
import android.view.MotionEvent;
import android.view.View;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.media.MediaPlayer;
import android.widget.EditText;
import android.text.InputType;
import java.util.Random;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private GameView gameView;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        gameView = new GameView();
        setContentView(gameView);
        gameView.handleInviteIntent(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (gameView != null) gameView.handleInviteIntent(intent);
    }

    @Override protected void onPause() { if(gameView!=null)gameView.pauseMusic(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(gameView!=null)gameView.resumeMusic(); }

    private class GameView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final CardInventory cardInventory;
        private final MissionManager missionManager;
        private boolean missionsScreen=false;
        private boolean warMenuScreen=false;
        private boolean campaignBattle=false;
        private boolean campaignGeneralOpponent=false;
        private String campaignOpponentName="جنيرال";
        private boolean weeklyMissionTab=false;
        private final HashMap<String,Bitmap> art = new HashMap<>();
        private Movie fireMovie;
        private Movie winMovie;
        private Movie shieldCardMovie, cyberCardMovie, repairCardMovie;
        private Bitmap shieldCardImage, cyberCardImage, repairCardImage;
        private Bitmap airstrikeCardImage, smokeCardImage, supplyCardImage, doubleDiceCardImage;
        private Bitmap radarCardImage, minefieldCardImage, mineDetectorCardImage;
        private Bitmap airstrikePlaneImage, landMineImage, smokeEffectImage;
        private long airstrikeStartedAt=0;
        private int airstrikeTargetSide=2;
        private final long[][] repairEffectUntil = new long[3][3];
        private final long[][] repairedStarBlinkUntil = new long[3][3];
        private final boolean[] blueWinEffect = {false,false,false};
        private final boolean[] redWinEffect = {false,false,false};
        private long fireStartedAt = System.currentTimeMillis();
        private boolean titleScreen = true;
        private SoundPool gameSoundPool;
        private MediaPlayer backgroundMusic;
        private final int[] backgroundMusicTracks = {R.raw.battle_music_1,R.raw.battle_music_2,R.raw.battle_music_3,R.raw.battle_music_4};
        private int selectedBackgroundMusicIndex = -1;
        private float musicVolume = 0.5f;
        private String battleBackgroundName="1793716501127.jpg";
        private boolean soundEnabled, musicEnabled, vibrationEnabled, screenShakeEnabled;
        private long screenShakeStartedAt=0;
        private int helpScrollY=0;
        private int helpContentHeight=0;
        private final int[] gameSoundIds = new int[4];
        private final boolean[] gameSoundLoaded = new boolean[4];
        private final Random random = new Random();
        private final int[] pos1 = {0,0,0}, pos2 = {0,0,0};
        private final boolean[] dead1 = {false,false,false}, dead2 = {false,false,false};
        private final String[] names = {"الدبابة","الطائرة","المدفع"};
        private int mode = 0; // 0 menu, 1 local two-player, 2 vs General, 3 Wi-Fi, 4 online
        private boolean setup = false;
        private boolean historyScreen = false;
        private boolean progressScreen = false;
        private boolean helpScreen = false;
        private int helpSection=-1;
        private boolean inventoryScreen = false;
        private boolean cardMatchSettled = false;
        private int selectedBattleCard = -1;
        private final boolean[][] cyberDisabled = new boolean[3][3];
        private final int[][] cyberTurns = new int[3][3];
        private final boolean[][] shieldActive = new boolean[3][3];
        private final int[][] shieldTurns = new int[3][3];
        private final boolean[][] smokeActive = new boolean[3][3];
        private final int[][] smokeTurns = new int[3][3];
        private int mineOwnerSide=0, mineTargetSide=0, mineLane=-1, mineTurns=0;
        private boolean radarPending=false;
        private int radarOwnerSide=0;
        private boolean radarPauseActive=false;
        private boolean cardTargeting = false;
        private boolean doubleDicePending = false;
        private int doubleDiceLane = -1;
        private int doubleDiceDisplayValue = 0;
        private long radarBlockStartedAt=0;
        private int radarBlockLane=-1,radarBlockAttackerSide=0,radarBlockAmount=0;
        private final boolean[] airstrikeShakeTriggered={false,false,false};
        private String temporaryCardMessage="";
        private long temporaryCardMessageUntil=0;
        private long cardNoticeUntil = 0;
        private int cardNoticeType = -1;
        private boolean cardNoticeFromGeneral = false;
        private final boolean[] generalCards = new boolean[CardInventory.CARD_TYPES];
        private final boolean[] generalCardsUsed = new boolean[CardInventory.CARD_TYPES];
        private boolean recorded = false;
        private boolean bonusAttempt = false, perfectWin = false, pointsAwarded = false;
        private int matchPoints = 0;
        private final int[] matchRewardCounts = new int[CardInventory.CARD_TYPES];
        private boolean battleIntro = false;
        private int introStep = 0;
        private final String[] rankNames = {"جندي","عريف","رقيب","ملازم","نقيب","رائد","عقيد","لواء"};
        private final int[] rankThresholds = {0,130,320,650,1100,1700,2500,3600};
        private int matchEpoch = 0;
        private final ArrayList<Projectile> activeProjectiles = new ArrayList<>();
        private final ArrayList<ImpactEffect> impactEffects = new ArrayList<>();
        private class Projectile {
            final float startX,startY,endX,endY,controlY;
            final long startedAt,duration;
            final int color;
            Projectile(float sx,float sy,float ex,float ey,float cy,long duration) {
                this(sx,sy,ex,ey,cy,duration,Color.rgb(238,166,58));
            }
            Projectile(float sx,float sy,float ex,float ey,float cy,long duration,int color) {
                startX=sx;startY=sy;endX=ex;endY=ey;controlY=cy;
                this.color=color;startedAt=System.currentTimeMillis();this.duration=duration;
            }
        }
        private class ImpactEffect {
            final float x,y;
            final long startedAt;
            ImpactEffect(float x,float y){this.x=x;this.y=y;startedAt=System.currentTimeMillis();}
        }
        private int setupSide = 1;
        private int difficulty = 1; // 0 beginner, 1 medium, 2 professional, 3 legendary
        private final String[] playerNames = {"أنت","اللاعب 2"};
        private String savedPlayerName(int index) {
            String fallback=index==0?"أنت":"اللاعب 2";
            return MainActivity.this.getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE)
                .getString("player_name_"+(index+1),fallback);
        }
        private void savePlayerName(int index,String name) {
            MainActivity.this.getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).edit()
                .putString("player_name_"+(index+1),name).apply();
        }
        private int savedPlayerAvatar() { return MainActivity.this.getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).getInt("player_avatar",0); }
        private void savePlayerAvatar(int avatar) { MainActivity.this.getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).edit().putInt("player_avatar",avatar).apply(); }
        private boolean isGeneralOpponent() { return mode==2 && (!campaignBattle || campaignGeneralOpponent); }
        private String computerOpponentName() { return campaignBattle?(campaignGeneralOpponent?"جنيرال":campaignOpponentName):"جنيرال"; }
        private final int[] avatars = {0,1};
        private final int[] avatarColors = {Color.rgb(35,112,205),Color.rgb(205,69,54),Color.rgb(45,145,105),Color.rgb(142,85,180),Color.rgb(225,145,45),Color.rgb(65,115,155),Color.rgb(176,91,119),Color.rgb(105,137,91),Color.rgb(48,96,133),Color.rgb(166,91,61),Color.rgb(67,119,83),Color.rgb(116,82,145),Color.rgb(185,126,54),Color.rgb(54,124,132),Color.rgb(151,79,104),Color.rgb(112,125,73)};
        private int stage = 0; // opening toss: 0 or 1; 2 means game
        private int firstRoll = 0, player = 1, die = 0, winner = 0;
        private int openingRoll1 = 0, openingRoll2 = 0;
        private boolean openingToss = false;
        private boolean choosing = false;
        private String message = "اختر طريقة اللعب";
        private String battleDialogueText = "";
        private String battleDialogueReplyText = "";
        private int battleDialogueSpeaker = 1;
        private int battleDialogueReplySpeaker = 2;
        private long battleDialogueStartedAt = 0;
        private final int[] dialogueSequence = {0,0,0};
        private final String[][] dialogueOpening = {
            {"استعد، لن أترك لك الميدان!","سنرى من يقود هذه المعركة.","بدأت المواجهة، فكن على قدر التحدي.","خطتي جاهزة ولن أتراجع.","اليوم سيُحسم الميدان للأقوى.","لا مجال للأخطاء من اللحظة الأولى.","أعددت لك مفاجآت في هذه الحرب.","دع الأفعال تتحدث بدل التهديدات.","لن أتنازل عن النصر بسهولة.","الميدان مفتوح، فلنبدأ."},
            {"لنبدأ المواجهة، أنا جاهز!","خطتي جاهزة، هل أنت مستعد؟","سأثبت أنني القائد هنا.","راقب تحركاتي جيداً.","لن تكون معركة سهلة عليك.","أنا هنا من أجل الفوز.","استعد لأقوى جولة.","لن أسمح لك بالسيطرة.","حان وقت اختبار الخطط.","فلنترك الحكم لنتيجة المعركة."}
        };
        private final String[][] dialogueTurn = {
            {"دورك الآن، ورّني مهارتك!","لا تتسرع، فكر قبل أن تطلق.","أحسنت، لكن لا تفرح مبكراً.","كل حركة تصنع فرقاً.","اختر سلاحك بعناية.","الميدان لا يرحم المتردد.","هل هذه أفضل خططك؟","ما زالت أمامي فرص كثيرة.","لن أضيّع هذه الجولة.","راقب ما سأفعله الآن."},
            {"جاء دوري، استعد للمواجهة!","لن أضيّع هذه الفرصة.","حان وقت الرد.","خطوتي القادمة ستكون حاسمة.","لن أترك لك زمام المبادرة.","استعد، أنا قادم.","سأستغل هذه الفرصة جيداً.","كل جولة تقرّبني من النصر.","لا تظن أن الأمر انتهى.","دعنا نرى قوة دفاعك."}
        };
        private final String[][] dialogueHit = {
            {"أصبت سلاحاً! وما زال لدي المزيد.","هذه ضربة قوية، لكنني مستمر!","سلاحك خرج من المعركة!","ضربة في مكانها تماماً.","بدأت كفة المعركة تميل.","هذا تقدم مهم لفريقي.","لن أسمح لك باستعادة المبادرة.","هدف جديد تحقق.","ما زلت أملك القوة للتقدم.","هذه ليست آخر ضرباتي."},
            {"خسرت سلاحاً، لكن المعركة لم تنتهِ!","سأردّ عليك في الجولة القادمة.","ضربتك أصابت، لكنني صامد.","لن تكسر عزيمتي بخسارة واحدة.","ما زال لدي سلاحان.","سأغيّر خطتي وأعود أقوى.","هذه الجولة لك، وليس الحرب.","لن أستسلم مهما حدث.","سأقاتل حتى آخر سلاح.","انتظر ردي في الميدان."}
        };
        private float w, h;
        private float touchDownX, touchDownY;
        private boolean touchDown = false;
        private long explosionAt = 0;
        private float explosionX = 0, explosionY = 0;
        private long muzzleFlashAt = 0;
        private float muzzleFlashX = 0, muzzleFlashY = 0;
        private final int bg = Color.rgb(190,225,165), dark = Color.rgb(36,67,43);
        private final int blue = Color.rgb(35,112,205), red = Color.rgb(205,69,54);
        private final int gold = Color.rgb(245,190,65);
        private LanMatchSession lanSession;
        private int lanLocalSide=1, lanTurnId=0, lanCountdown=30;
        private boolean lanOpeningStarted=false;
        private boolean lanHostPending=false;
        private String lanJoinAddress=null;
        private OnlineMatchSession onlineSession;
        private int onlineLocalSide=1, onlineTurnId=0;
        private boolean onlineOpeningStarted=false;
        private String onlineJoinCode=null;
        private boolean onlineQuickMatch=false;
        private boolean onlineInvitePending=false;
        private boolean onlineWaitingForPeer=false;
        private boolean resultRevealReady=false;
        private final boolean[] onlineCards = new boolean[CardInventory.CARD_TYPES];
        private final boolean[][] onlineCardsUsedBySide = new boolean[3][CardInventory.CARD_TYPES];
        private boolean onlineLoadoutReady=false, onlinePeerLoadoutReady=false, onlineLoadoutDialogShown=false;
        private boolean onlineDoubleDicePending=false;
        private int onlineDoubleDiceLane=-1;
        private int onlineRadarBlockedLane=-1, onlineRadarBlockedAttackerSide=0;
        private int onlineRadarPendingLane=-1, onlineRadarPendingAmount=0, onlineRadarPendingAttackerSide=0;

        private void loadGameArt() {
            String[] names={"character_01.png","character_02.png","character_03.png","character_04.png","character_05.png","character_06.png","character_07.png","character_08.png","character_09.png","character_10.png","character_11.png","character_12.png","character_13.png","character_14.png","character_15.png","character_16.png","character_17.png","character_18.png","tank_01.png","cannon_01.png","fighter_01.png","fighter_02.png","fighter_03.png","ammo_round_left_gold.png","ammo_round_right_gold.png","ammo_round_left_white.png","ammo_round_right_white.png"};
            for(String name:names) {
                String folder=name.startsWith("character_")?"characters":(name.startsWith("tank_")||name.startsWith("cannon_")||name.startsWith("fighter_"))?"vehicles":"ammo";
                try { art.put(name,BitmapFactory.decodeStream(getAssets().open(folder+"/"+name))); } catch(Exception ignored) {}
            }
            String[] generals={"1_Khabeer_Nashi.png","2_Qaid_Zou_Khabra.png","3_General_Mukhadram.png","4_Qaid_Faiq_Alkhabra.png"};
            for(String name:generals)try{art.put(name,BitmapFactory.decodeStream(getAssets().open("characters/"+name)));}catch(Exception ignored){}
            String[] uiButtons={"blue_button_01.png","blue_button_02.png","red_button_01.png","red_button_02.png","vs_emblem.png"};
            for(String name:uiButtons)try{art.put(name,BitmapFactory.decodeStream(getAssets().open("ui/"+name)));}catch(Exception ignored){}
            String[] scenes={"2112.w023.n001.1491B.p1.1491.jpg","2207_w023_n003_2757b_p1_2757.jpg","png.png","war-zone-landscape-with-apocalyptic-destruction.jpg","1793716501127.jpg","1793905666116.jpg","Intro.jpg"};
            for(String name:scenes)try{art.put(name,BitmapFactory.decodeStream(getAssets().open("backgrounds/"+name)));}catch(Exception ignored){}
            try { fireMovie=Movie.decodeStream(getAssets().open("vehicles/Flame_GIF_2.gif")); } catch(Exception ignored) {}
            try { winMovie=Movie.decodeStream(getAssets().open("vehicles/Win.gif")); } catch(Exception ignored) {}
            repairCardImage=loadCardImage("ui/card_repair.png");
            cyberCardImage=loadCardImage("ui/card_cyber.png");
            shieldCardImage=loadCardImage("ui/card_shield_transparent.png");
            airstrikeCardImage=loadCardImage("ui/card_airstrike.png");
            smokeCardImage=loadCardImage("ui/card_smoke.png");
            smokeEffectImage=loadCardImage("ui/smoke_effect.png");
            doubleDiceCardImage=loadCardImage("ui/card_double_dice.png");
            supplyCardImage=loadCardImage("ui/card_supply.png");
            radarCardImage=loadCardImage("ui/card_radar.png");
            minefieldCardImage=loadCardImage("ui/card_minefield.png");
            mineDetectorCardImage=loadCardImage("ui/card_mine_detector.png");
            airstrikePlaneImage=loadCardImage("ui/airstrike_plane.png");
            landMineImage=loadCardImage("ui/land_mine.png");
        }
        // Card images are preprocessed into real RGBA PNG files by the build workflow.
        private Bitmap loadCardImage(String assetPath) {
            try { return BitmapFactory.decodeStream(getAssets().open(assetPath)); }
            catch(Exception ignored) { return null; }
        }
        private Bitmap loadTransparentEncodedArtwork(String assetPath) {
            try(java.io.InputStream input=getAssets().open(assetPath);
                    java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                byte[] bytes=android.util.Base64.decode(output.toByteArray(),android.util.Base64.DEFAULT);
                Bitmap source=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(source==null)return null;
                Bitmap transparent=removeCheckerBackground(source);
                source.recycle();
                return transparent;
            } catch(Exception ignored) { return null; }
        }
        private Bitmap cardImageForType(int type) {
            switch(type) {
                case CardInventory.REPAIR:return repairCardImage;
                case CardInventory.DOUBLE_DICE:return doubleDiceCardImage;
                case CardInventory.CYBER_WARFARE:return cyberCardImage;
                case CardInventory.SHIELD:return shieldCardImage;
                case CardInventory.AIRSTRIKE:return airstrikeCardImage;
                case CardInventory.SMOKE_SCREEN:return smokeCardImage;
                case CardInventory.MILITARY_SUPPLY:return supplyCardImage;
                case CardInventory.RADAR:return radarCardImage;
                case CardInventory.MINEFIELD:return minefieldCardImage;
                case CardInventory.MINE_DETECTOR:return mineDetectorCardImage;
                default:return null;
            }
        }
        private boolean isCheckerBackground(int color) {
            int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
            int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
            // The supplied artwork contains both white and light-grey checkerboard
            // squares. Treat neutral edge-connected shades as background, not only
            // near-white pixels, so the flood fill can cross the whole pattern.
            return Color.alpha(color)==0 || (min>=135 && max-min<=48);
        }
        private void enqueueBackground(int index,int[] pixels,boolean[] queued,int[] queue,int[] tail) {
            if(index<0 || index>=pixels.length || queued[index] || !isCheckerBackground(pixels[index]))return;
            queued[index]=true;
            queue[tail[0]++]=index;
        }
        private Bitmap removeCheckerBackground(Bitmap source) {
            int width=source.getWidth(),height=source.getHeight();
            Bitmap result=source.copy(Bitmap.Config.ARGB_8888,true);
            int[] pixels=new int[width*height];
            result.getPixels(pixels,0,width,0,0,width,height);
            boolean[] queued=new boolean[pixels.length];
            int[] queue=new int[pixels.length];
            int[] tail={0};
            for(int x=0;x<width;x++) {
                enqueueBackground(x,pixels,queued,queue,tail);
                enqueueBackground((height-1)*width+x,pixels,queued,queue,tail);
            }
            for(int y=1;y<height-1;y++) {
                enqueueBackground(y*width,pixels,queued,queue,tail);
                enqueueBackground(y*width+width-1,pixels,queued,queue,tail);
            }
            int head=0;
            while(head<tail[0]) {
                int index=queue[head++];
                pixels[index]=Color.TRANSPARENT;
                int x=index%width,y=index/width;
                if(x>0)enqueueBackground(index-1,pixels,queued,queue,tail);
                if(x<width-1)enqueueBackground(index+1,pixels,queued,queue,tail);
                if(y>0)enqueueBackground(index-width,pixels,queued,queue,tail);
                if(y<height-1)enqueueBackground(index+width,pixels,queued,queue,tail);
            }
            result.setPixels(pixels,0,width,0,0,width,height);
            return result;
        }
        private void drawArt(Canvas c,String name,float l,float t,float r,float b,boolean flip) {
            Bitmap bitmap=art.get(name);if(bitmap==null)return;RectF dst=new RectF(l,t,r,b);
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(255);p.setFilterBitmap(true);
            if(flip){c.save();c.scale(-1f,1f,(l+r)*.5f,(t+b)*.5f);c.drawBitmap(bitmap,null,dst,p);c.restore();}else c.drawBitmap(bitmap,null,dst,p);
        }

        GameView() {
            super(MainActivity.this);
            cardInventory = new CardInventory(MainActivity.this);
            cardInventory.topUpCardsToFiveOnce();
            missionManager = new MissionManager(MainActivity.this,cardInventory);
            missionManager.recordDailyLogin();
            loadGameArt();
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE);
            soundEnabled=prefs.getBoolean("sound",true);
            musicEnabled=prefs.getBoolean("music",true);
            musicVolume=prefs.getFloat("music_volume",0.5f);
            musicVolume=Math.max(0f,Math.min(1f,musicVolume));
            vibrationEnabled=prefs.getBoolean("vibration",true);
            screenShakeEnabled=prefs.getBoolean("screen_shake",true);
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();            gameSoundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attributes)
                .build();
            gameSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                    if (status == 0) {
                        for (int i = 0; i < gameSoundIds.length; i++) {
                            if (gameSoundIds[i] == sampleId) gameSoundLoaded[i] = true;
                        }
                    }
                }
            });
            gameSoundIds[0] = gameSoundPool.load(MainActivity.this, R.raw.tank_fire, 1);
            gameSoundIds[1] = gameSoundPool.load(MainActivity.this, R.raw.aircraft_fire, 1);
            gameSoundIds[2] = gameSoundPool.load(MainActivity.this, R.raw.cannon_fire, 1);
            gameSoundIds[3] = gameSoundPool.load(MainActivity.this, R.raw.explosion, 1);
            selectedBackgroundMusicIndex=random.nextInt(backgroundMusicTracks.length);
            updateBackgroundMusic();
        }
        private void playGameSound(int index) {
            if (soundEnabled && gameSoundPool != null && index >= 0 && index < gameSoundIds.length
                    && gameSoundLoaded[index]) {
                gameSoundPool.play(gameSoundIds[index], 0.95f, 0.95f, 1, 0, 1.0f);
            }
        }

        @Override protected void onDetachedFromWindow() {
            timerlessCloseLan();
            timerlessCloseOnline();
            if (gameSoundPool != null) {
                gameSoundPool.release();
                gameSoundPool = null;
            }
            if(backgroundMusic!=null){backgroundMusic.release();backgroundMusic=null;}
            super.onDetachedFromWindow();
        }
        private void playRandomBattleMusic() {
            if(backgroundMusicTracks.length>1) {
                int next= random.nextInt(backgroundMusicTracks.length-1);
                if(next>=selectedBackgroundMusicIndex)next++;
                selectedBackgroundMusicIndex=next;
            }
            if(backgroundMusic!=null) {
                try { backgroundMusic.stop(); } catch(Exception ignored) {}
                try { backgroundMusic.release(); } catch(Exception ignored) {}
                backgroundMusic=null;
            }
            updateBackgroundMusic();
        }
        private void updateBackgroundMusic() {
            if(!musicEnabled) {
                if(backgroundMusic!=null) {
                    try { backgroundMusic.stop(); } catch(Exception ignored) {}
                    backgroundMusic.release();
                    backgroundMusic=null;
                }
                return;
            }
            try {
                if(backgroundMusic==null) {
                    AudioAttributes musicAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                    if(selectedBackgroundMusicIndex<0 || selectedBackgroundMusicIndex>=backgroundMusicTracks.length) selectedBackgroundMusicIndex=random.nextInt(backgroundMusicTracks.length);
                    backgroundMusic=MediaPlayer.create(MainActivity.this,backgroundMusicTracks[selectedBackgroundMusicIndex]);
                    if(backgroundMusic!=null) {
                        backgroundMusic.setAudioAttributes(musicAttributes);
                        backgroundMusic.setLooping(true);
                        backgroundMusic.setVolume(musicVolume,musicVolume);
                    }
                }
                if(backgroundMusic!=null && !backgroundMusic.isPlaying()) {
                    backgroundMusic.start();
                }
            } catch(Exception error) {
                if(backgroundMusic!=null) {
                    try { backgroundMusic.release(); } catch(Exception ignored) {}
                    backgroundMusic=null;
                }
            }
        }
        void pauseMusic(){
            if(backgroundMusic!=null) {
                try { if(backgroundMusic.isPlaying())backgroundMusic.pause(); }
                catch(Exception ignored) {}
            }
        }
        void resumeMusic(){if(musicEnabled)updateBackgroundMusic();}
        private void showSettingsDialog() {
            final android.widget.LinearLayout panel=new android.widget.LinearLayout(MainActivity.this);
            panel.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad=(int)(20*getResources().getDisplayMetrics().density);
            panel.setPadding(pad,pad/2,pad,pad/2);
            final android.widget.CheckBox soundBox=new android.widget.CheckBox(MainActivity.this);
            soundBox.setText("المؤثرات الصوتية");soundBox.setChecked(soundEnabled);
            final android.widget.CheckBox musicBox=new android.widget.CheckBox(MainActivity.this);
            musicBox.setText("تشغيل الموسيقى");musicBox.setChecked(musicEnabled);
            final android.widget.TextView volumeLabel=new android.widget.TextView(MainActivity.this);
            volumeLabel.setText("مستوى الموسيقى: "+Math.round(musicVolume*100)+"%");
            volumeLabel.setPadding(0,pad/2,0,0);
            final android.widget.SeekBar volumeBar=new android.widget.SeekBar(MainActivity.this);
            volumeBar.setMax(100);volumeBar.setProgress(Math.round(musicVolume*100));
            final android.widget.CheckBox vibrationBox=new android.widget.CheckBox(MainActivity.this);
            vibrationBox.setText("اهتزاز الهاتف عند تدمير السلاح");vibrationBox.setChecked(vibrationEnabled);
            final android.widget.CheckBox shakeBox=new android.widget.CheckBox(MainActivity.this);
            shakeBox.setText("اهتزاز الشاشة عند تدمير السلاح");shakeBox.setChecked(screenShakeEnabled);
            int settingsGold=Color.rgb(150,105,0);
            android.graphics.Typeface settingsBold=android.graphics.Typeface.DEFAULT_BOLD;
            soundBox.setTextColor(settingsGold);soundBox.setTypeface(settingsBold);
            musicBox.setTextColor(settingsGold);musicBox.setTypeface(settingsBold);
            volumeLabel.setTextColor(settingsGold);volumeLabel.setTypeface(settingsBold);
            vibrationBox.setTextColor(settingsGold);vibrationBox.setTypeface(settingsBold);
            shakeBox.setTextColor(settingsGold);shakeBox.setTypeface(settingsBold);
            final android.widget.Button profileButton=new android.widget.Button(MainActivity.this);
            profileButton.setText("تحديد المستخدم والهوية الثابتة");profileButton.setTextColor(settingsGold);
            panel.addView(profileButton);
            panel.addView(soundBox);panel.addView(musicBox);panel.addView(volumeLabel);panel.addView(volumeBar);
            panel.addView(vibrationBox);panel.addView(shakeBox);
            volumeBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){
                    musicVolume=progress/100f;
                    volumeLabel.setText("مستوى الموسيقى: "+progress+"%");
                    if(backgroundMusic!=null)backgroundMusic.setVolume(musicVolume,musicVolume);
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar bar){}
                @Override public void onStopTrackingTouch(android.widget.SeekBar bar){}
            });
            android.widget.TextView settingsTitle=new android.widget.TextView(MainActivity.this);
            settingsTitle.setText("إعدادات اللعبة");
            settingsTitle.setTextColor(Color.rgb(212,160,23));
            settingsTitle.setTextSize(22);
            settingsTitle.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
            settingsTitle.setGravity(android.view.Gravity.CENTER);
            settingsTitle.setPadding(pad,pad,pad,pad);
            AlertDialog dialog=new AlertDialog.Builder(MainActivity.this).setCustomTitle(settingsTitle)
                .setView(panel).setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).create();
            profileButton.setOnClickListener(v->{dialog.dismiss();showProfileDialog();});
            dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                soundEnabled=soundBox.isChecked();musicEnabled=musicBox.isChecked();
                vibrationEnabled=vibrationBox.isChecked();screenShakeEnabled=shakeBox.isChecked();
                getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).edit()
                    .putBoolean("sound",soundEnabled).putBoolean("music",musicEnabled)
                    .putBoolean("vibration",vibrationEnabled).putBoolean("screen_shake",screenShakeEnabled)
                    .putFloat("music_volume",musicVolume).apply();
                updateBackgroundMusic();dialog.dismiss();
            }));
            dialog.setOnCancelListener(d->{
                musicVolume=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).getFloat("music_volume",0.5f);
                if(backgroundMusic!=null)backgroundMusic.setVolume(musicVolume,musicVolume);
            });
            dialog.show();
        }
        private void showProfileDialog() {
            final int[] draftAvatar={savedPlayerAvatar()};
            final EditText nameInput=new EditText(MainActivity.this);
            nameInput.setSingleLine(true);nameInput.setHint("اسم المستخدم");nameInput.setText(savedPlayerName(0));
            nameInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            final android.widget.TextView avatarLabel=new android.widget.TextView(MainActivity.this);
            avatarLabel.setGravity(android.view.Gravity.CENTER);avatarLabel.setTextColor(Color.rgb(150,105,0));
            avatarLabel.setText("الشخصية "+(draftAvatar[0]+1)+" من 17");avatarLabel.setTextSize(18);
            final View preview=new View(MainActivity.this) {
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);float radius=Math.min(getWidth(),getHeight())*.43f;
                    drawAvatar(canvas,draftAvatar[0],getWidth()*.5f,getHeight()*.5f,radius,true);
                }
            };
            preview.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1,(int)(150*getResources().getDisplayMetrics().density)));
            android.widget.LinearLayout chooser=new android.widget.LinearLayout(MainActivity.this);chooser.setGravity(android.view.Gravity.CENTER);
            android.widget.Button previous=new android.widget.Button(MainActivity.this);previous.setText("◀");
            android.widget.Button next=new android.widget.Button(MainActivity.this);next.setText("▶");
            chooser.addView(previous);chooser.addView(avatarLabel,new android.widget.LinearLayout.LayoutParams(0,-2,1));chooser.addView(next);
            previous.setOnClickListener(v->{draftAvatar[0]=nextAvatar(draftAvatar[0],-1);avatarLabel.setText("الشخصية "+(draftAvatar[0]+1)+" من 17");preview.invalidate();});
            next.setOnClickListener(v->{draftAvatar[0]=nextAvatar(draftAvatar[0],1);avatarLabel.setText("الشخصية "+(draftAvatar[0]+1)+" من 17");preview.invalidate();});
            android.widget.LinearLayout panel=new android.widget.LinearLayout(MainActivity.this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad=(int)(16*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad,pad,pad);
            android.widget.TextView nameLabel=new android.widget.TextView(MainActivity.this);
            nameLabel.setText("اسم المستخدم الثابت");nameLabel.setTextColor(Color.rgb(150,105,0));nameLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            android.widget.TextView avatarTitle=new android.widget.TextView(MainActivity.this);
            avatarTitle.setText("الشخصية الثابتة");avatarTitle.setTextColor(Color.rgb(150,105,0));avatarTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);avatarTitle.setGravity(android.view.Gravity.CENTER);
            panel.addView(nameLabel);panel.addView(nameInput);panel.addView(avatarTitle);panel.addView(preview);panel.addView(chooser);
            new AlertDialog.Builder(MainActivity.this).setTitle("هوية المستخدم").setView(panel)
                .setPositiveButton("حفظ الهوية",(d,which)->{
                    String value=nameInput.getText().toString().trim();
                    if(!value.isEmpty()){savePlayerName(0,value);playerNames[0]=value;}
                    savePlayerAvatar(draftAvatar[0]);avatars[0]=draftAvatar[0];invalidate();
                }).setNegativeButton("إلغاء",null).show();
        }
        private void showExitConfirmation() {
            boolean surrender=mode==2 && winner==0 && cardInventory.isMatchActive();
            new AlertDialog.Builder(MainActivity.this)
                .setTitle(surrender?"الاستسلام":"تأكيد الخروج")
                .setMessage(surrender?"هل تريد الاستسلام وإنهاء المعركة؟ ستخسر جميع البطاقات التي دخلت بها.":"هل أنت متأكد أنك تريد الخروج من لعبة GENERAL؟")
                .setPositiveButton(surrender?"استسلام":"خروج",(dialog,which)->{
                    if(surrender) {
                        winner=2;cardInventory.abandonMatch();cardMatchSettled=true;
                        clearBattleCardEffects();bonusAttempt=false;finishMatch();
                    } else {timerlessCloseLan();timerlessCloseOnline();finish();}
                })
                .setNegativeButton("إلغاء",null).show();
        }
        private void vibrateForExplosion() {
            if(!vibrationEnabled)return;
            try {
                Vibrator vibrator = null;
                if(Build.VERSION.SDK_INT>=31) {
                    android.os.VibratorManager manager=getSystemService(android.os.VibratorManager.class);
                    if(manager!=null) vibrator=manager.getDefaultVibrator();
                }
                if(vibrator==null) {
                    vibrator=(Vibrator)getSystemService(android.content.Context.VIBRATOR_SERVICE);
                }
                if(vibrator!=null && vibrator.hasVibrator()) {
                    vibrator.cancel();
                    if(Build.VERSION.SDK_INT>=26) {
                        // Two distinct pulses make the explosion perceptible without being excessive.
                        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,110,65,190},-1));
                    } else {
                        vibrator.vibrate(new long[]{0,110,65,190},-1);
                    }
                } else {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                }
            } catch(Exception error) {
                try { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); }
                catch(Exception ignored) {}
            }
        }


        private void drawDiceFace(Canvas c,float cx,float cy,float size,int value) {
            // رسم جميع أوجه النرد برمجياً لضمان تطابق الأرقام وعدم اختلاف ألوان الصور.
            value=Math.max(1,Math.min(6,value));
            RectF face=new RectF(cx-size*.5f,cy-size*.5f,cx+size*.5f,cy+size*.5f);
            p.setShader(new LinearGradient(face.left,face.top,face.right,face.bottom,
                    Color.rgb(255,255,247),Color.rgb(218,225,214),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(face,size*.17f,size*.17f,p);
            p.setShader(null);
            p.setColor(Color.rgb(48,65,53));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2,size*.035f));
            c.drawRoundRect(new RectF(face.left+size*.012f,face.top+size*.012f,
                    face.right-size*.012f,face.bottom-size*.012f),size*.15f,size*.15f,p);
            p.setStyle(Paint.Style.FILL);
            float d=size*.115f, a=size*.245f;
            float[][] dots={
                {0,0},
                {-a,-a,a,a},
                {-a,-a,0,0,a,a},
                {-a,-a,a,-a,-a,a,a,a},
                {-a,-a,a,-a,0,0,-a,a,a,a},
                {-a,-a,a,-a,-a,0,a,0,-a,a,a,a}
            };
            float[] points=dots[value-1];
            for(int i=0;i<points.length;i+=2) {
                float dx=points[i],dy=points[i+1];
                p.setColor(Color.rgb(35,48,39));
                c.drawCircle(cx+dx,cy+dy,d,p);
                p.setColor(Color.argb(65,255,255,255));
                c.drawCircle(cx+dx-d*.22f,cy+dy-d*.22f,d*.22f,p);
            }
        }

        private void drawFire(Canvas c,float cx,float cy) {
            if(fireMovie==null)return;
            int duration=Math.max(1,fireMovie.duration());
            fireMovie.setTime((int)((System.currentTimeMillis()-fireStartedAt)%duration));
            float fw=w*.105f,fh=h*.115f;
            c.save();c.translate(cx-fw*.5f,cy-fh*.5f);c.scale(fw/fireMovie.width(),fh/fireMovie.height());
            fireMovie.draw(c,0,0);c.restore();
            postInvalidateDelayed(45);
        }

        // Keep the victory animation behind the winning weapon until the match is reset.
        private void drawWinEffect(Canvas c,float cx,float cy) {
            if(winMovie==null)return;
            int duration=Math.max(1,winMovie.duration());
            winMovie.setTime((int)((System.currentTimeMillis()-fireStartedAt)%duration));
            float fw=w*.105f,fh=h*.125f;
            c.save();
            c.translate(cx-fw*.5f,cy-fh*.5f);
            c.scale(fw/Math.max(1,winMovie.width()),fh/Math.max(1,winMovie.height()));
            winMovie.draw(c,0,0);
            c.restore();
            postInvalidateDelayed(45);
        }
        private void drawWinnerGif(Canvas c,float cx,float cy) {
            if(winMovie==null)return;
            int duration=Math.max(1,winMovie.duration());
            winMovie.setTime((int)((System.currentTimeMillis()-fireStartedAt)%duration));
            float fw=w*.19f,fh=h*.23f;
            c.save();c.translate(cx-fw*.5f,cy-fh*.5f);
            c.scale(fw/Math.max(1,winMovie.width()),fh/Math.max(1,winMovie.height()));
            winMovie.draw(c,0,0);c.restore();
            postInvalidateDelayed(45);
        }
        private void clearWinEffects() {
            for(int i=0;i<3;i++){blueWinEffect[i]=false;redWinEffect[i]=false;}
        }

        private void drawMenuBackdrop(Canvas c) {
            Bitmap image=art.get("1793716501127.jpg");
            if(image!=null) {
                float scale=Math.max(w/image.getWidth(),h/image.getHeight());
                float dw=image.getWidth()*scale,dh=image.getHeight()*scale;
                c.drawBitmap(image,null,new RectF((w-dw)*.5f,(h-dh)*.5f,(w+dw)*.5f,(h+dh)*.5f),p);
            } else c.drawColor(Color.rgb(38,48,39));
            p.setShader(new LinearGradient(0,0,0,h,Color.argb(155,5,13,14),Color.argb(185,5,12,10),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
        }

        private void drawIntroScreen(Canvas c) {
            Bitmap image=art.get("Intro.jpg");
            if(image!=null) {
                float scale=Math.max(w/image.getWidth(),h/image.getHeight());
                float dw=image.getWidth()*scale,dh=image.getHeight()*scale;
                RectF dst=new RectF((w-dw)*.5f,(h-dh)*.5f,(w+dw)*.5f,(h+dh)*.5f);
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);c.drawBitmap(image,null,dst,p);
            } else c.drawColor(Color.rgb(18,24,25));
            p.setShader(new LinearGradient(0,h*.68f,0,h,Color.TRANSPARENT,Color.argb(205,5,10,10),Shader.TileMode.CLAMP));
            c.drawRect(0,h*.68f,w,h,p);p.setShader(null);
            txt(c,"انقر للاستمرار",w*.5f,h*.88f,h*.038f,Color.WHITE,true);
            txt(c,"الإصدار "+BuildConfig.VERSION_NAME,w*.5f,h*.96f,h*.032f,Color.rgb(255,205,75),true);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.003f));p.setColor(Color.argb(220,245,190,65));
            c.drawRoundRect(new RectF(w*.34f,h*.82f,w*.66f,h*.92f),h*.025f,h*.025f,p);p.setStyle(Paint.Style.FILL);
        }

        private void setBattleDialogue(int speaker, int situation) {
            int type=Math.max(0,Math.min(2,situation));
            String[][] lines=type==0?dialogueOpening:type==1?dialogueTurn:dialogueHit;
            int index=dialogueSequence[type]++ % lines[0].length;
            int other=speaker==1?2:1;
            battleDialogueSpeaker=speaker;
            battleDialogueReplySpeaker=other;
            battleDialogueText=lines[speaker==2?1:0][index];
            // الحوار ليس تبادلاً إجبارياً: الرد يظهر أحياناً فقط، بحسب الموقف.
            int replyChance=type==0?45:(type==1?22:38);
            boolean shouldReply=random.nextInt(100)<replyChance;
            battleDialogueReplyText=shouldReply?lines[other==2?1:0][index]:"";
            battleDialogueStartedAt=System.currentTimeMillis();
            invalidate();
        }

        private long dialogueFirstDuration() {
            return Math.max(1900L, battleDialogueText.codePointCount(0,battleDialogueText.length())*72L);
        }

        private void setCardBattleDialogue(int type,int speaker,boolean success) {
            if(type<0 || type>=CardInventory.CARD_TYPES || stage<2 || winner!=0 || bonusAttempt)return;
            String[][] lines=success?cardDialogueSuccess:cardDialogueFailure;
            int other=speaker==1?2:1;
            battleDialogueSpeaker=speaker;
            battleDialogueReplySpeaker=other;
            battleDialogueText=lines[speaker==1?0:1][type];
            battleDialogueReplyText=lines[speaker==1?2:3][type];
            battleDialogueStartedAt=System.currentTimeMillis();
            invalidate();
            postInvalidateDelayed(70);
        }

        // Four rows: player announces, General announces, General replies, player replies.
        private final String[][] cardDialogueSuccess = {
            {"رجّعت سلاحي للخدمة، المعركة لسه إلي!","ضاعفت النرد، جهّز حالك للاندفاع!","عطّلت سلاحك بالحرب السيبرانية!","درعي شغّال، جرّب تخترق دفاعي!","ضربتي الجوية رفعت جاهزية أسلحتي!","اختفيت خلف الدخان، دورك تخمّن!","وصلني الإمداد، والنتيجة صارت أقوى!","الرادار كشف خطوتك القادمة!","زرعت لك حقل ألغام، انتبه وين بتتحرك!","كشفت الألغام وأمّنت المعسكر!"},
            {"جنيرال أصلح سلاحه، لكنه لن ينجو من المواجهة!","جنيرال ضاعف النرد، استعد لهجوم أقوى!","جنيرال عطّل سلاحك سيبرانياً!","جنيرال حصّن سلاحه، ابحث عن ثغرة!","جنيرال نفّذ ضربة جوية ورفع جاهزية أسلحته!","جنيرال أخفى سلاحه بستار الدخان!","جنيرال حصل على إمداد إضافي!","جنيرال فعّل الرادار لمراقبة هجومك!","جنيرال زرع ألغاماً في معسكرك!","جنيرال كشف الألغام وأبطل مفعولها!"},
            {"الإصلاح يؤجل خسارتك، ولن يغيّر نتيجة الحرب!","ضاعف كما تشاء، سأوقف تقدمك!","تعطيلك لن يمنعني من إيجاد طريق للنصر!","درعك لن يحميك طوال المعركة!","ضربتك الجوية لن تحسم المواجهة وحدها!","الدخان يخفي السلاح، لكنه لا يخفي ضعفك!","الإمداد لن ينقذك من خطتي!","سأغيّر مساري حتى لا يفيدك الرادار!","سأتعامل مع الألغام بحذر!","كشفت الألغام، لكن المعركة مستمرة!"},
            {"إصلاحك لن يوقف تقدمي!","ضاعف النرد، وأنا جاهز للدفاع!","سأعيد تشغيل سلاحي وأرد عليك!","درعك لن يدوم، سأنتظر فرصتي!","سأصمد أمام ضربتك الجوية!","لن تعرف أين أتمركز بسهولة!","حتى مع الإمداد، النصر مش مضمون!","سأغيّر خطتي وأفاجئك!","سأنتبه لكل خطوة في المعسكر!","لن تنجح الألغام في إيقافي!"}
        };
        private final String[][] cardDialogueFailure = {
            {"البطاقة ما اشتغلت، لازم أراجع هدفي!","ما قدرت أضاعف النرد على هذا السلاح!","الهجوم السيبراني ما وصل لهدفه!","ما قدرت أفعّل الدرع على هذا السلاح!","الضربة الجوية ما انطلقت كما خططت!","ما قدرت أخفي السلاح بستار الدخان!","الإمداد ما أعطاني النتيجة المطلوبة!","الرادار ما قدر يحدد الهجوم!","زرع الألغام ما تم بنجاح!","ما لقيت ألغاماً لأفككها!"},
            {"حتى بطاقة جنيرال لم تحقق هدفها!","تعذّر على جنيرال مضاعفة النرد!","فشلت محاولة جنيرال السيبرانية!","لم يتمكن جنيرال من تفعيل الدرع!","تعذّر تنفيذ الضربة الجوية لجنيرال!","لم ينجح جنيرال في إخفاء السلاح!","لم يحصل جنيرال على الإمداد المطلوب!","الرادار لم يكشف الهجوم كما أراد جنيرال!","فشلت محاولة جنيرال لزرع الألغام!","لم يعثر جنيرال على ألغام لإبطالها!"},
            {"فرصتك محدودة، ولن أضيّعها!","لن تنجح كل محاولاتك في الميدان!","دفاعي أقوى من خطتك السيبرانية!","سلاحي سيبقى مكشوفاً، وسأستغل ذلك!","سأستفيد من فشل ضربتك!","سأراك بوضوح الآن!","خطتك تحتاج إلى إمداد أفضل!","سأهاجم قبل أن تعيد المحاولة!","سأواصل التقدم دون خوف!","إذن لا ألغام تعيق طريقي!"},
            {"مش كل بطاقة بتنجح، ولسه عندي خطة!","فشل محاولتك أعطاني فرصة!","حاول مرة ثانية إذا بتقدر!","ما زال سلاحي جاهزاً للمواجهة!","سأستغل فشل الضربة الجوية!","لن يختبئ سلاحك عني طويلاً!","سأكمل المعركة بما لدي!","لن يفيدك الرادار في كل مرة!","سأنتبه لمكان الألغام!","معسكري صار آمناً!"}
        };

        private void setNoMoveDialogue(int speaker) {
            int other=speaker==1?2:1;
            battleDialogueSpeaker=speaker;
            battleDialogueReplySpeaker=other;
            battleDialogueText=speaker==1 ? "النرد ما أعطاني حركة ممكنة، انتهى دوري!" : "ما في حركة متاحة إلي، دورك الآن!";
            battleDialogueReplyText=random.nextInt(100)<45
                    ? (other==1 ? "وصلتني الفرصة، رح أستغلها!" : "فرصتك انتهت، الآن دوري!") : "";
            battleDialogueStartedAt=System.currentTimeMillis();
            invalidate();
        }

        private void drawDialogueBubble(Canvas c,String text,int speaker,float top,long elapsed) {
            if(text==null || text.isEmpty())return;
            boolean isBlue=speaker==1;
            // Compact centered bubble: half the previous width, positioned at the top.
            float left=w*.3225f, right=w*.6775f;
            float bottom=top+h*.078f;
            float radius=h*.020f;
            int color=isBlue?blue:red;
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(248,8,17,20));
            c.drawRoundRect(new RectF(left,top,right,bottom),radius,radius,p);
            Path tail=new Path();
            float mid=top+h*.039f;
            if(isBlue){tail.moveTo(right-h*.052f,top+h*.010f);tail.lineTo(right+h*.030f,mid);tail.lineTo(right-h*.052f,bottom-h*.010f);}
            else {tail.moveTo(left+h*.052f,top+h*.010f);tail.lineTo(left-h*.030f,mid);tail.lineTo(left+h*.052f,bottom-h*.010f);}
            tail.close();
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(248,8,17,20));c.drawPath(tail,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(4,h*.0055f));p.setColor(color);
            c.drawPath(tail,p);
            c.drawRoundRect(new RectF(left,top,right,bottom),radius,radius,p);
            p.setStyle(Paint.Style.FILL);
            int total=text.codePointCount(0,text.length());
            int count=(int)Math.min(total,Math.max(0,elapsed/68L));
            int finish=text.offsetByCodePoints(0,count);
            String visible=text.substring(0,finish);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(h*.027f);p.setFakeBoldText(true);
            while(p.measureText(visible)>right-left-h*.045f && p.getTextSize()>h*.018f)p.setTextSize(p.getTextSize()*.96f);
            p.setColor(isBlue?Color.rgb(160,207,255):Color.rgb(255,177,162));
            c.drawText(visible,(left+right)*.5f,top+h*.050f,p);
            p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(false);p.setStyle(Paint.Style.FILL);
        }

        private void drawBattleDialogue(Canvas c) {
            if(stage<2 || winner!=0 || bonusAttempt || battleDialogueText.isEmpty())return;
            long elapsed=System.currentTimeMillis()-battleDialogueStartedAt;
            long firstDuration=dialogueFirstDuration();
            long replyStart=firstDuration+500L;
            long totalDuration=replyStart+Math.max(1900L,battleDialogueReplyText.codePointCount(0,battleDialogueReplyText.length())*72L)+1100L;
            if(elapsed>totalDuration)return;
            drawDialogueBubble(c,battleDialogueText,battleDialogueSpeaker,h*.018f,elapsed);
            if(elapsed>=replyStart)drawDialogueBubble(c,battleDialogueReplyText,battleDialogueReplySpeaker,h*.108f,elapsed-replyStart);
            postInvalidateDelayed(70);
        }

        private void drawBattlefield(Canvas c) {
            Bitmap image=art.get(battleBackgroundName);
            if(image==null)image=art.get("1793716501127.jpg");
            if(image!=null){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);c.drawBitmap(image,null,new RectF(0,0,w,h),p);}else c.drawColor(Color.rgb(115,145,105));
            p.setColor(Color.argb(42,18,25,24));p.setStyle(Paint.Style.FILL);c.drawRect(0,0,w,h,p);
        }

        private void txt(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setColor(color); p.setTextSize(size); p.setFakeBoldText(bold);
            p.setTextAlign(Paint.Align.CENTER); p.setStyle(Paint.Style.FILL); c.drawText(s,x,y,p);
        }
        private void box(Canvas c,float l,float t,float r,float b,int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(l,t,r,b),16,16,p);
        }
        private void namePlate(Canvas c,String name,float cx,float cy,int color) {
            float width=Math.min(w*.16f,Math.max(w*.12f,p.measureText(name)*1.12f));
            float top=cy-h*.030f,bottom=cy+h*.012f;
            box(c,cx-width*.5f,top,cx+width*.5f,bottom,Color.argb(235,15,23,21));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.0028f));p.setColor(color);
            c.drawRoundRect(new RectF(cx-width*.5f,top,cx+width*.5f,bottom),h*.014f,h*.014f,p);p.setStyle(Paint.Style.FILL);
            float size=h*.034f;p.setTextSize(size);p.setFakeBoldText(true);
            while(p.measureText(name)>width*.88f && size>h*.018f){size*=.94f;p.setTextSize(size);}
            txt(c,name,cx,cy+h*.003f,size,Color.WHITE,true);
        }
        private void drawSevenPointStar(Canvas c,float cx,float cy,float radius,int alpha) {
            Path star=new Path();
            for(int i=0;i<14;i++) {
                double angle=-Math.PI/2.0+i*Math.PI/7.0;
                float r=radius*(i%2==0?1f:.46f);
                float x=cx+(float)Math.cos(angle)*r;
                float y=cy+(float)Math.sin(angle)*r;
                if(i==0)star.moveTo(x,y);else star.lineTo(x,y);
            }
            star.close();
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha,247,190,54));c.drawPath(star,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.2f,radius*.12f));
            p.setColor(Color.argb(alpha,105,65,20));c.drawPath(star,p);
            p.setStyle(Paint.Style.FILL);
        }
        private void drawDestroyedStars(Canvas c,int attackingSide,String displayName,float cx) {
            long now=System.currentTimeMillis();
            boolean[] targetDestroyed=destroyed(attackingSide==1?2:1);
            int count=0;
            for(int lane=0;lane<3;lane++)
                if(targetDestroyed[lane] || now<repairedStarBlinkUntil[attackingSide][lane])count++;
            if(count==0)return;
            p.setTextSize(h*.034f);p.setFakeBoldText(true);
            float nameWidth=Math.min(w*.16f,Math.max(w*.12f,p.measureText(displayName)*1.12f));
            float radius=Math.min(h*.0125f,nameWidth/(count*2.7f));
            float spacing=radius*2.35f;
            float total=(count-1)*spacing;
            int slot=0;
            for(int lane=0;lane<3;lane++) {
                boolean destroyedNow=targetDestroyed[lane];
                long blinkUntil=repairedStarBlinkUntil[attackingSide][lane];
                if(!destroyedNow && now>=blinkUntil)continue;
                int alpha=255;
                if(!destroyedNow) {
                    float phase=(1100f-(blinkUntil-now))/150f;
                    alpha=(int)(70+185*(.5f+.5f*(float)Math.sin(phase*Math.PI*2)));
                    postInvalidateDelayed(45);
                }
                drawSevenPointStar(c,cx-total*.5f+slot*spacing,h*.023f,radius,alpha);
                slot++;
            }
        }
        private void iconButton(Canvas c,float l,float t,float r,float b,int color,String glyph) {
            float radius=Math.min(b-t,r-l)*.22f;
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(90,0,0,0));
            c.drawRoundRect(new RectF(l,t+h*.004f,r,b+h*.004f),radius,radius,p);
            p.setColor(color);c.drawRoundRect(new RectF(l,t,r,b),radius,radius,p);
            txt(c,glyph,(l+r)*.5f,(t+b)*.5f+h*.018f,(b-t)*.52f,Color.WHITE,true);
        }
        private void btn(Canvas c,String s,float l,float t,float r,float b,int color) {
            float radius=Math.min(h*.025f,(b-t)*.24f);
            boolean pressed=touchDown && touchDownX>=l && touchDownX<=r && touchDownY>=t && touchDownY<=b;
            float lift=pressed?h*.002f:h*.004f;
            RectF outer=new RectF(l,t+lift,r,b);
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(75,20,28,24));
            c.drawRoundRect(new RectF(l,t+lift+h*.004f,r,b+h*.004f),radius,radius,p);
            p.setColor(shade(color,pressed?.72f:1.16f));
            c.drawRoundRect(outer,radius,radius,p);
            RectF face=new RectF(l+h*.003f,t+lift+h*.003f,r-h*.003f,b-h*.003f);
            p.setShader(new LinearGradient(0,face.top,0,face.bottom,
                    pressed?shade(color,.88f):shade(color,1.12f),
                    pressed?shade(color,.72f):shade(color,.88f),Shader.TileMode.CLAMP));
            c.drawRoundRect(face,radius*.82f,radius*.82f,p);p.setShader(null);
            // Avoid overlaying unrelated image fragments on button faces.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.5f,h*.0022f));
            p.setColor(Color.argb(165,255,255,255));
            c.drawRoundRect(face,radius*.82f,radius*.82f,p);p.setStyle(Paint.Style.FILL);
            float textSize=Math.min(h*.043f,(r-l)*.105f);
            p.setTextSize(textSize);p.setFakeBoldText(true);
            while(p.measureText(s)>(r-l)*.88f && textSize>h*.020f){textSize*=.94f;p.setTextSize(textSize);}
            txt(c,s,(l+r)/2,(t+b)/2+textSize*.34f+(pressed?h*.002f:0),textSize,Color.WHITE,true);
        }
        private int nextAvatar(int current,int direction) {
            int candidate=current;
            for(int i=0;i<18;i++){candidate=Math.floorMod(candidate+direction,18);if(candidate!=5)return candidate;}
            return 0;
        }
        private int[] positions(int side) { return side==1 ? pos1 : pos2; }
        private boolean[] destroyed(int side) { return side==1 ? dead1 : dead2; }
        private boolean canMove(int side,int lane) {
            int opposingSide=side==1?2:1;
            // Disabled weapons cannot fire; a shield blocks ordinary attacks on its weapon.
            return !destroyed(side)[lane] && !destroyed(opposingSide)[lane]
                    && !cyberDisabled[side][lane]
                    && !smokeActive[opposingSide][lane]
                    && positions(side)[lane]+die<=15;
        }
        private void clearBattleCardEffects() {
            for(int side=1;side<=2;side++) for(int lane=0;lane<3;lane++) {
                cyberDisabled[side][lane]=false;cyberTurns[side][lane]=0;
                shieldActive[side][lane]=false;shieldTurns[side][lane]=0;
                smokeActive[side][lane]=false;smokeTurns[side][lane]=0;
            }
            mineOwnerSide=0;mineTargetSide=0;mineLane=-1;mineTurns=0;
            radarPending=false;radarOwnerSide=0;radarPauseActive=false;
            selectedBattleCard=-1;cardTargeting=false;
            doubleDicePending=false;doubleDiceLane=-1;doubleDiceDisplayValue=0;
            radarBlockStartedAt=0;radarBlockLane=-1;radarBlockAttackerSide=0;radarBlockAmount=0;
            temporaryCardMessage="";temporaryCardMessageUntil=0;
        }
        private boolean cardAvailable(int type) {
            return mode==2 && cardInventory.isMatchActive()
                    && cardInventory.getActiveCards()[type] && !cardInventory.getUsedCards()[type];
        }
        private void showCardUseNotice(int type,boolean fromGeneral) {
            cardNoticeType=type;
            cardNoticeFromGeneral=fromGeneral;
            cardNoticeUntil=System.currentTimeMillis()+2000L;
            setCardBattleDialogue(type,fromGeneral?2:1,true);
            invalidate();
            postInvalidateDelayed(35);
        }
        private boolean useCard(int type) {
            if(!cardAvailable(type) || !cardInventory.markUsed(type))return false;
            selectedBattleCard=-1;cardTargeting=false;
            showCardUseNotice(type,false);
            return true;
        }
        private void tickMineExpiry(int completedSide) {
            if(mineTargetSide!=completedSide || mineTurns<=0)return;
            mineTurns--;
            if(mineTurns==0) {
                mineOwnerSide=0;mineTargetSide=0;mineLane=-1;
                message="انتهت صلاحية حقل الألغام بعد 4 جولات";
            }
        }
        private void finishCardTurnEffects(int side) {
            tickMineExpiry(side);
            for(int lane=0;lane<3;lane++) {
                if(cyberDisabled[side][lane] && --cyberTurns[side][lane]<=0) cyberDisabled[side][lane]=false;
                int opponent=side==1?2:1;
                if(shieldActive[opponent][lane] && --shieldTurns[opponent][lane]<=0) shieldActive[opponent][lane]=false;
                if(smokeActive[opponent][lane] && --smokeTurns[opponent][lane]<=0) smokeActive[opponent][lane]=false;
            }
            if(radarPending && side!=radarOwnerSide) { radarPending=false;radarOwnerSide=0; }
        }
        private void confirmImmediateCard(final int type) {
            String title=type==CardInventory.AIRSTRIKE?"تأكيد الضربة الجوية":
                    type==CardInventory.RADAR?"تفعيل الرادار":"تأكيد كشف الألغام";
            String body=type==CardInventory.AIRSTRIKE?"ستزيد عدّادات أسلحتك الثلاثة ثلاث طلقات لكل سلاح. هل تريد المتابعة؟":
                    type==CardInventory.RADAR?"سيُراقب الرادار هجوم الخصم القادم ويمنحك 10 ثوانٍ لاستخدام الدرع. هل تريد التفعيل؟":
                    "سيتم فحص معسكرات الخصم الثلاثة وإبطال أي لغم يُكتشف دون إحداث ضرر. هل تريد المتابعة؟";
            new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(body)
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("تأكيد",(d,wid)->{
                    if(!useCard(type))return;
                    if(type==CardInventory.AIRSTRIKE) {
                        startAirstrikeAnimation(2);
                        message="الطائرة بدأت الهجوم — ستُطلق ذخائرها على الأسلحة الثلاثة تباعاً";
                    } else if(type==CardInventory.RADAR) {
                        radarPending=true;radarOwnerSide=1;
                        message="الرادار جاهز — سيتم كشف السلاح المستهدف بعد رمية الخصم";
                    } else {
                        if(mineTargetSide==2 && mineLane>=0) {
                            mineTargetSide=0;mineOwnerSide=0;mineLane=-1;
                            message="";showTemporaryCardMessage("تمت إزالة اللغم");
                        } else {message="";showTemporaryCardMessage("لم يتم العثور على لغم");}
                    }
                    invalidate();
                }).show();
        }
        private void showSupplyChoice() {
            final String[] choices={"0 — إلغاء البطاقة","1 — إضافة طلقة","2 — إضافة طلقتين","3 — إضافة 3 طلقات"};
            new AlertDialog.Builder(MainActivity.this).setTitle("الإمداد العسكري")
                .setItems(choices,(dialog,which)->{
                    if(which==0){selectedBattleCard=-1;cardTargeting=false;message="تم إلغاء الإمداد دون استهلاك البطاقة";invalidate();return;}
                    if(!useCard(CardInventory.MILITARY_SUPPLY))return;
                    die+=which;message="الإمداد العسكري: أُضيفت "+which+" إلى نتيجة النرد — المجموع "+die;
                    choosing=true;selectedBattleCard=-1;cardTargeting=false;invalidate();
                }).setOnCancelListener(d->{selectedBattleCard=-1;cardTargeting=false;invalidate();}).show();
        }
        private boolean triggerMineByCardAttempt(int side,int lane) {
            if(mineTargetSide!=side || mineLane!=lane)return false;
            positions(side)[lane]=Math.max(0,positions(side)[lane]-3);
            mineTargetSide=0;mineOwnerSide=0;mineLane=-1;
            message="انفجر اللغم في معسكر "+names[lane]+" وخسر السلاح حتى 3 طلقات";
            invalidate();return true;
        }
        private void finishSelectedBattleCard(int card,int lane) {
            selectedBattleCard=-1;cardTargeting=false;
            if(card==CardInventory.DOUBLE_DICE && die>0) {
                boolean laneReady=!destroyed(1)[lane] && !destroyed(2)[lane]
                        && !cyberDisabled[1][lane] && !shieldActive[2][lane]
                        && positions(1)[lane]<15;
                if(laneReady) animateMove(player,lane,die);
                else message="لا يمكن تنفيذ الحركة المضاعفة بهذا السلاح";
            }
            invalidate();
        }
        private void applySelectedBattleCardToLane(final int card,final int lane) {
            boolean revealsSmoke=smokeActive[1][lane]
                    && (card==CardInventory.REPAIR || card==CardInventory.SHIELD || card==CardInventory.DOUBLE_DICE);
            if(revealsSmoke) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("تنبيه: كشف السلاح")
                    .setMessage("هذا السلاح مختبئ بستار دخان. استخدام البطاقة عليه سيزيل الدخان ويكشف موقعه للخصم. هل تريد المتابعة؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("متابعة",(dialog,which)->{
                        if(executeBattleCard(card,lane)) {
                            smokeActive[1][lane]=false;smokeTurns[1][lane]=0;
                            message="تم استخدام البطاقة وأُزيل ستار الدخان — أصبح السلاح مكشوفاً";
                            finishSelectedBattleCard(card,lane);
                        } else {
                            message="لا يمكن استخدام البطاقة على هذا السلاح الآن";
                            setCardBattleDialogue(card,1,false);
                            invalidate();
                        }
                    }).show();
                return;
            }
            if(executeBattleCard(card,lane))finishSelectedBattleCard(card,lane);
            else {
                message="لا يمكن استخدام البطاقة على هذا السلاح الآن";
                setCardBattleDialogue(card,1,false);
            }
        }
        private boolean executeBattleCard(int type,int lane) {
            if(type==CardInventory.DOUBLE_DICE) {
                if(lane>=0 && lane<3 && triggerMineByCardAttempt(1,lane))return false;
                if(lane<0 || lane>2 || destroyed(1)[lane] || destroyed(2)[lane]
                        || cyberDisabled[1][lane] || shieldActive[2][lane] || positions(1)[lane]>=15
                        || doubleDicePending)return false;
                if(!useCard(type))return false;
                if(die>0) {
                    doubleDiceDisplayValue=die;
                    die*=2;
                    message="تم تفعيل مضاعفة النرد على "+names[lane];
                    choosing=true;
                } else {
                    doubleDicePending=true;
                    doubleDiceLane=lane;
                    message="تم تفعيل مضاعفة النرد على "+names[lane]+" — اضغط النرد";
                }
                return true;
            }
            if(lane<0 || lane>2)return false;
            if(type==CardInventory.REPAIR) {
                if(mineTargetSide==1 && mineLane==lane)return false;
                if(!destroyed(1)[lane] && !cyberDisabled[1][lane])return false;
                if(!useCard(type))return false;
                // Cyber-disabled weapons keep their exact progress; only a destroyed
                // weapon is rebuilt from the beginning with its original ammunition.
                boolean wasDestroyed=destroyed(1)[lane];
                destroyed(1)[lane]=false;
                if(wasDestroyed) {
                    repairedStarBlinkUntil[2][lane]=System.currentTimeMillis()+1100L;
                    positions(1)[lane]=0;
                    positions(2)[lane]=0;
                    blueWinEffect[lane]=false;redWinEffect[lane]=false;
                }
                cyberDisabled[1][lane]=false;cyberTurns[1][lane]=0;
                repairEffectUntil[1][lane]=System.currentTimeMillis()+1000L;
                message="تم إصلاح "+names[lane]+" وإعادته إلى العمل";playGameSound(3);return true;
            }
            if(type==CardInventory.CYBER_WARFARE) {
                if(destroyed(2)[lane] || smokeActive[2][lane])return false;
                if(triggerMineByCardAttempt(2,lane))return false;
                if(!useCard(type))return false;
                cyberDisabled[2][lane]=true;cyberTurns[2][lane]=2;
                message="تم تعطيل "+names[lane]+" لدى جنيرال لمدة دورين";return true;
            }
            if(type==CardInventory.SHIELD) {
                if(destroyed(1)[lane] || shieldActive[1][lane])return false;
                if(triggerMineByCardAttempt(1,lane))return false;
                boolean allProtected=true;for(int i=0;i<3;i++)if(!shieldActive[1][i]&&!destroyed(1)[i])allProtected=false;
                if(allProtected)return false;
                if(!useCard(type))return false;
                shieldActive[1][lane]=true;shieldTurns[1][lane]=2;
                message="تم تفعيل درع الحماية على "+names[lane];return true;
            }
            if(type==CardInventory.SMOKE_SCREEN) {
                if(destroyed(1)[lane] || smokeActive[1][lane])return false;
                if(triggerMineByCardAttempt(1,lane))return false;
                if(!useCard(type))return false;
                smokeActive[1][lane]=true;smokeTurns[1][lane]=3;
                message="تم تفعيل ستار الدخان على "+names[lane]+" لمدة 3 أدوار للخصم";return true;
            }
            if(type==CardInventory.MINEFIELD) {
                if(triggerMineByCardAttempt(2,lane))return false;
                if(mineTargetSide!=0 || destroyed(2)[lane] || smokeActive[2][lane])return false;
                if(!useCard(type))return false;
                mineOwnerSide=1;mineTargetSide=2;mineLane=lane;mineTurns=4;
                message="تم زرع لغم أرضي في أحد معسكرات الخصم";return true;
            }
            return false;
        }
        private void showOnlineSupplyChoice() {
            final String[] choices={"إلغاء البطاقة","إضافة طلقة","إضافة طلقتين","إضافة 3 طلقات"};
            new AlertDialog.Builder(MainActivity.this).setTitle("الإمداد العسكري")
                .setItems(choices,(dialog,which)->{if(which>0)applyOnlineCardEffect(onlineLocalSide,CardInventory.MILITARY_SUPPLY,-1,which,true);}).show();
        }
        private boolean applyOnlineCardEffect(int side,int type,int lane,int value,boolean send) {
            if(mode!=4 || side<1 || side>2 || type<0 || type>=CardInventory.CARD_TYPES
                    || type==CardInventory.AIRSTRIKE || onlineCardsUsedBySide[side][type])return false;
            boolean local=side==onlineLocalSide;int opponent=side==1?2:1;
            if(send && (!local || !onlineCards[type] || onlineSession==null || !onlineSession.isConnected()))return false;
            if(type==CardInventory.RADAR) { if(radarPending)return false; }
            else if(type==CardInventory.MINE_DETECTOR) { /* A detector may also report an empty scan. */ }
            else if(type==CardInventory.MILITARY_SUPPLY) { if(value<1 || value>3 || !choosing || die<=0)return false; }
            else {
                if(lane<0 || lane>2)return false;
                if(type==CardInventory.REPAIR) {
                    if(mineTargetSide==side && mineLane==lane)return false;
                    if(!destroyed(side)[lane] && !cyberDisabled[side][lane])return false;
                } else if(type==CardInventory.DOUBLE_DICE) {
                    if(die!=0 || choosing || destroyed(side)[lane] || destroyed(opponent)[lane]
                            || cyberDisabled[side][lane] || shieldActive[opponent][lane]
                            || positions(side)[lane]>=15 || onlineDoubleDicePending)return false;
                } else if(type==CardInventory.CYBER_WARFARE) {
                    if(destroyed(opponent)[lane] || smokeActive[opponent][lane]
                            || (mineTargetSide==opponent && mineLane==lane))return false;
                } else if(type==CardInventory.SHIELD) {
                    if(destroyed(side)[lane] || shieldActive[side][lane])return false;
                } else if(type==CardInventory.SMOKE_SCREEN) {
                    if(destroyed(side)[lane] || smokeActive[side][lane])return false;
                } else if(type==CardInventory.MINEFIELD) {
                    if(mineTargetSide!=0 || destroyed(opponent)[lane] || smokeActive[opponent][lane])return false;
                }
            }
            if(send && !onlineSession.sendCardUse(type,lane,value))return false;
            onlineCardsUsedBySide[side][type]=true;
            if(type==CardInventory.RADAR) {
                radarPending=true;radarOwnerSide=side;message=(local?"فعّلت":"فعّل الخصم")+" الرادار لمراقبة الهجوم القادم";
            } else if(type==CardInventory.MINE_DETECTOR) {
                if(mineTargetSide==side) {mineTargetSide=0;mineOwnerSide=0;mineLane=-1;message="";showTemporaryCardMessage("تمت إزالة اللغم");}
                else {message="";showTemporaryCardMessage("لم يتم العثور على لغم");}
            } else if(type==CardInventory.MILITARY_SUPPLY) {
                die+=value;if(onlineSession!=null)onlineSession.adjustCurrentRoll(side,value);
                message=(local?"الإمداد العسكري":"إمداد الخصم العسكري")+" — أضيفت "+value+" إلى النرد";
            } else if(type==CardInventory.REPAIR) {
                boolean wasDestroyed=destroyed(side)[lane];destroyed(side)[lane]=false;
                if(wasDestroyed){repairedStarBlinkUntil[opponent][lane]=System.currentTimeMillis()+1100L;positions(side)[lane]=0;positions(opponent)[lane]=0;blueWinEffect[lane]=false;redWinEffect[lane]=false;}
                cyberDisabled[side][lane]=false;cyberTurns[side][lane]=0;
                repairEffectUntil[side][lane]=System.currentTimeMillis()+1000L;
                message=(local?"أصلحت":"أصلح الخصم")+" "+names[lane]+" وأعاده إلى العمل";
            } else if(type==CardInventory.DOUBLE_DICE) {
                onlineDoubleDicePending=true;onlineDoubleDiceLane=lane;
                message=(local?"فعّلت":"فعّل الخصم")+" مضاعفة النرد على "+names[lane];
            } else if(type==CardInventory.CYBER_WARFARE) {
                cyberDisabled[opponent][lane]=true;cyberTurns[opponent][lane]=2;
                message=(local?"عطّلت":"عطّل الخصم")+" "+names[lane]+" سيبرانياً لدورين";
            } else if(type==CardInventory.SHIELD) {
                shieldActive[side][lane]=true;shieldTurns[side][lane]=2;
                message=(local?"فعّلت":"فعّل الخصم")+" الدرع على "+names[lane];
                if(value==-1 && radarPauseActive){onlineRadarBlockedLane=lane;onlineRadarBlockedAttackerSide=opponent;
                    radarPauseActive=false;finishRadarResponseTurn(opponent);}
            } else if(type==CardInventory.SMOKE_SCREEN) {
                smokeActive[side][lane]=true;smokeTurns[side][lane]=3;
                message=(local?"فعّلت":"فعّل الخصم")+" ستار الدخان على "+names[lane];
            } else if(type==CardInventory.MINEFIELD) {
                mineOwnerSide=side;mineTargetSide=opponent;mineLane=lane;mineTurns=4;
                message=(local?"زرعت":"زرع الخصم")+" لغماً أرضياً في أحد المعسكرات";
            }
            cardNoticeType=type;cardNoticeFromGeneral=!local;cardNoticeUntil=System.currentTimeMillis()+2000L;
            setCardBattleDialogue(type,side,true);invalidate();postInvalidateDelayed(35);return true;
        }
        private void drawIntroCardRow(Canvas c,boolean general,float centerX,float top) {
            boolean[] cards=general?generalCards:cardInventory.getActiveCards();
            boolean[] used=general?generalCardsUsed:cardInventory.getUsedCards();
            float tileW=w*.052f,tileH=h*.075f,gap=w*.008f;
            int count=0;for(int i=0;i<CardInventory.CARD_TYPES;i++)if(cards[i]&&!used[i])count++;
            if(count==0)return;
            float total=count*tileW+(count-1)*gap,left=centerX-total*.5f;
            int slot=0;
            for(int type=0;type<CardInventory.CARD_TYPES;type++)if(cards[type]&&!used[type]) {
                float l=left+slot*(tileW+gap);
                drawCardTile(c,type,l,top,l+tileW,top+tileH,false,true,1);
                slot++;
            }
        }
        private void drawBattleCards(Canvas c) {
            if(mode==4){drawOnlineBattleCards(c);return;}
            if(mode!=2 || !cardInventory.isMatchActive())return;
            boolean[] active=cardInventory.getActiveCards(),used=cardInventory.getUsedCards();
            float width=w*.075f,gap=w*.012f,top=h*.125f,tileHeight=h*.085f;
            float left=w*.70f;
            int slot=0;
            for(int type=0;type<CardInventory.CARD_TYPES;type++) if(active[type]&&!used[type]) {
                float x=left+slot*(width+gap);
                drawCardTile(c,type,x,top,x+width,top+tileHeight,selectedBattleCard==type,true,1);
                slot++;
            }
        }
        private void drawOnlineBattleCards(Canvas c) {
            float width=w*.075f,gap=w*.012f,top=h*.125f,tileHeight=h*.085f;
            float left=onlineLocalSide==1?w*.70f:w*.02f;int slot=0;
            for(int type=0;type<CardInventory.CARD_TYPES;type++)if(onlineCards[type]&&!onlineCardsUsedBySide[onlineLocalSide][type]){
                float x=onlineLocalSide==1?left+slot*(width+gap):left+slot*(width+gap);
                drawCardTile(c,type,x,top,x+width,top+tileHeight,selectedBattleCard==type,true,1);slot++;
            }
        }
        private void drawTemporaryCardMessage(Canvas c) {
            if(temporaryCardMessageUntil<=System.currentTimeMillis()||temporaryCardMessage.length()==0)return;
            float alpha=Math.min(1f,(temporaryCardMessageUntil-System.currentTimeMillis())/180f);
            float top=h*.205f;
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb((int)(220*alpha),10,20,16));
            c.drawRoundRect(new RectF(w*.24f,top,w*.76f,top+h*.075f),h*.018f,h*.018f,p);
            txt(c,temporaryCardMessage,w*.5f,top+h*.050f,h*.032f,Color.argb((int)(255*alpha),255,220,125),true);
            postInvalidateDelayed(35);
        }
        private void showTemporaryCardMessage(String value) {
            temporaryCardMessage=value;temporaryCardMessageUntil=System.currentTimeMillis()+1000L;
            invalidate();postInvalidateDelayed(35);
        }
        private void drawCardUseNotice(Canvas c) {
            long now=System.currentTimeMillis();
            if(cardNoticeType<0 || now>=cardNoticeUntil)return;
            float progress=1f-(cardNoticeUntil-now)/2000f;
            float pulse=(float)(1.0+0.025*Math.sin(progress*Math.PI*10));
            float cx=w*.5f,cy=h*.49f;
            int alpha=(int)(245*Math.min(1f,Math.min(progress*9f,(1f-progress)*9f+0.25f)));
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(Math.max(0,alpha),5,12,10));c.drawRect(0,0,w,h,p);
            float cardW=w*.40f*pulse,cardH=h*.58f*pulse;
            RectF frame=new RectF(cx-cardW*.5f,cy-cardH*.5f,cx+cardW*.5f,cy+cardH*.5f);
            p.setColor(Color.argb(Math.max(0,alpha),28,45,36));
            c.drawRoundRect(frame,h*.025f,h*.025f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(h*.008f);
            p.setColor(Color.argb(Math.max(0,alpha),245,190,65));
            c.drawRoundRect(frame,h*.025f,h*.025f,p);p.setStyle(Paint.Style.FILL);
            Bitmap image=cardImageForType(cardNoticeType);
            float imageMaxW=cardW*.78f,imageMaxH=cardH*.70f;
            if(image!=null) {
                float scale=Math.min(imageMaxW/Math.max(1,image.getWidth()),imageMaxH/Math.max(1,image.getHeight()));
                float iw=image.getWidth()*scale,ih=image.getHeight()*scale;
                p.setAlpha(Math.max(0,alpha));p.setFilterBitmap(true);
                c.drawBitmap(image,null,new RectF(cx-iw*.5f,cy-ih*.40f,cx+iw*.5f,cy+ih*.60f),p);
                p.setAlpha(255);
            } else {
                txt(c,cardIcons[Math.max(0,Math.min(CardInventory.CARD_TYPES-1,cardNoticeType))],cx,cy+h*.10f,h*.30f,Color.WHITE,true);
            }
            String[] labels={"إصلاح السلاح","مضاعفة النرد","الحرب السيبرانية","درع الحماية","الضربة الجوية","ستار الدخان","الإمداد العسكري","الرادار","حقل الألغام","كشف الألغام"};
            String who=cardNoticeFromGeneral?"جنيرال":"أنت";
            txt(c,who+" استخدم بطاقة",cx,frame.top+h*.055f,h*.032f,Color.rgb(255,220,125),true);
            txt(c,labels[Math.max(0,Math.min(CardInventory.CARD_TYPES-1,cardNoticeType))],cx,frame.bottom-h*.045f,h*.040f,Color.WHITE,true);
            postInvalidateDelayed(35);
        }
        private void drawGeneralBattleCards(Canvas c) {
            if(mode!=2 || winner!=0 || battleIntro)return;
            float width=w*.043f,gap=w*.006f,left=w*.035f,top=h*.145f,rowGap=h*.008f;
            txt(c,"بطاقات جنيرال",left+w*.11f,top-h*.012f,h*.018f,Color.rgb(235,194,105),true);
            int slot=0;
            for(int type=0;type<CardInventory.CARD_TYPES;type++) if(generalCards[type]) {
                int row=slot/5,col=slot%5;
                float x=left+col*(width+gap),y=top+row*(h*.055f+rowGap);
                boolean available=!generalCardsUsed[type];
                drawCardTile(c,type,x,y,x+width,y+h*.055f,false,available,available?1:0);
                slot++;
            }
        }
        private boolean anyMove(int side) {
            for(int i=0;i<3;i++) if(canMove(side,i)) return true;
            return false;
        }

        private void showLanModeDialog() {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("اللعب عبر Wi-Fi")
                .setMessage("يجب أن يكون الجهازان على شبكة Wi-Fi نفسها. اختر إنشاء مباراة أو الانضمام.")
                .setPositiveButton("إنشاء مباراة", (d,which)->{
                    mode=3; lanLocalSide=1; setup=true; setupSide=1;
                    playerNames[0]=savedPlayerName(0); playerNames[1]=savedPlayerName(1);avatars[0]=savedPlayerAvatar();
                    showLanHostAddress();
                })
                .setNegativeButton("انضمام لمباراة", (d,which)->{
                    mode=3; lanLocalSide=2; setup=true; setupSide=2;
                    playerNames[0]=savedPlayerName(0); playerNames[1]=savedPlayerName(0);avatars[1]=savedPlayerAvatar();
                    showLanJoinDialog();
                })
                .setNeutralButton("إلغاء",null).show();
        }
        private void showOnlineModeDialog() {
            final String[] choices={"⚔  لعب سريع — ابحث عن لاعب","🏠  إنشاء غرفة جديدة","✉  دعوة صديق عبر واتساب","⌨  الانضمام برمز غرفة"};
            AlertDialog dialog=new AlertDialog.Builder(MainActivity.this)
                .setTitle("اللعب أون لاين")
                .setItems(choices,(d,which)->{
                    mode=4; setup=true; setupSide=1; onlineJoinCode=null;
                    playerNames[0]=savedPlayerName(0); avatars[0]=savedPlayerAvatar();
                    onlineLocalSide=1; onlineQuickMatch=false; onlineInvitePending=false;
                    if(which==0) {
                        onlineQuickMatch=true;
                    } else if(which==1) {
                        message="إنشاء غرفة جديدة — سيظهر رمز الغرفة بعد الاتصال";
                    } else if(which==2) {
                        onlineInvitePending=true;
                    } else {
                        setup=false;
                        showOnlineSetupNotice(false);
                        return;
                    }
                    invalidate();
                })
                .setNegativeButton("إلغاء",null).create();
            dialog.setOnShowListener(d->styleOnlineDialog(dialog));
            dialog.show();
        }

        private void handleInviteIntent(android.content.Intent intent) {
            if(intent==null)return;
            if(intent.getBooleanExtra("campaign_battle",false)) {
                campaignBattle=true;
                campaignGeneralOpponent=intent.getBooleanExtra("campaign_general",false);
                campaignOpponentName=intent.getStringExtra("campaign_opponent_name");
                if(campaignOpponentName==null||campaignOpponentName.trim().isEmpty())campaignOpponentName="خصم الحملة";
                int campaignContinent=intent.getIntExtra("campaign_continent",0);
                difficulty=campaignGeneralOpponent?Math.max(0,Math.min(3,campaignContinent)):0;
                titleScreen=false;
                mode=2;setup=true;setupSide=1;
                cardInventory.prepareNewMatchSetup();
                playerNames[0]=savedPlayerName(0);playerNames[1]=computerOpponentName();
                avatars[0]=savedPlayerAvatar();avatars[1]=1;
                message="حرب الحملة — استعد للمعركة";
                invalidate();
                return;
            }
            if(intent.getData()==null)return;
            android.net.Uri uri=intent.getData();
            if(!"generalgame".equals(uri.getScheme()) || !"join".equals(uri.getHost()))return;
            String code=uri.getQueryParameter("room");
            if(code==null || !code.matches("[A-Za-z0-9]{8}"))return;
            onlineJoinCode=code.toUpperCase(Locale.ROOT);
            onlineLocalSide=2; onlineQuickMatch=false; onlineInvitePending=false;
            mode=4; setup=true; setupSide=2;            playerNames[1]=savedPlayerName(0); avatars[1]=savedPlayerAvatar();
            message="وصلتك دعوة للعب عبر الإنترنت";
            invalidate();
        }

        private void shareInviteLink(String code) {
            String link="generalgame://join?room="+code;
            android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(android.content.Intent.EXTRA_TEXT,
                    "تعال نلعب GENERAL مع بعض! اضغط الرابط لفتح اللعبة والانضمام للمباراة:\n"+link);
            startActivity(android.content.Intent.createChooser(send,"إرسال دعوة GENERAL"));
        }
        private void showOnlineSetupNotice(boolean createRoom) {
            if(createRoom) {
                mode=4; setup=true; setupSide=1; playerNames[0]=savedPlayerName(0); avatars[0]=0;
                onlineJoinCode=null; invalidate();
                return;
            }
            final EditText input=new EditText(MainActivity.this);
            input.setSingleLine(true);
            input.setHint("مثال: A7K9M2QX");
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps(),
                    new android.text.InputFilter.LengthFilter(8)});
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(MainActivity.this).setTitle("رمز غرفة الإنترنت")
                .setMessage("أدخل الرمز المكوّن من 8 أحرف الذي أرسله لك اللاعب الآخر.")
                .setView(input).setPositiveButton("متابعة",(d,which)->{
                    String code=input.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if(code.length()!=8) {
                        new AlertDialog.Builder(MainActivity.this).setMessage("الرمز يجب أن يتكون من 8 أحرف.").setPositiveButton("حسناً",null).show();
                        return;
                    }
                    onlineJoinCode=code; onlineLocalSide=2; mode=4; setup=true; setupSide=2;
                    onlineQuickMatch=false; onlineInvitePending=false;
                    playerNames[1]=savedPlayerName(0); avatars[1]=savedPlayerAvatar(); invalidate();
                }).setNegativeButton("إلغاء",null).show();
        }
        private void showLanHostAddress() {
            String ip=LanConnection.getLocalIpv4();
            new AlertDialog.Builder(MainActivity.this).setTitle("عنوان الجهاز المضيف")
                .setMessage((ip.isEmpty()?"تعذر تحديد عنوان IP تلقائياً":("أرسل هذا العنوان للاعب الآخر:\n"+ip))+"\n\nاضغط انتظار الاتصال بعد أن يصبح الجهازان على الشبكة نفسها.")
                .setPositiveButton("انتظار الاتصال",(d,which)->{lanHostPending=true;invalidate();})
                .setNegativeButton("إلغاء",(d,which)->{mode=0;setup=false;invalidate();}).show();
        }
        private void showLanJoinDialog() {
            final EditText input=new EditText(MainActivity.this);
            input.setSingleLine(true); input.setHint("مثال: 192.168.1.10");
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            new AlertDialog.Builder(MainActivity.this).setTitle("عنوان الجهاز المضيف")
                .setMessage("اكتب عنوان IP الذي يظهر على جهاز اللاعب الأول.")
                .setView(input).setPositiveButton("اتصال",(d,which)->{
                    String address=input.getText().toString().trim();
                    if(address.isEmpty()){showLanJoinDialog();return;}
                    lanJoinAddress=address;invalidate();
                }).setNegativeButton("إلغاء",(d,which)->{mode=0;setup=false;invalidate();}).show();
        }
        private void createLanSession() {
            if(lanSession!=null)lanSession.close();
            lanSession=new LanMatchSession(lanLocalSide,new LanMatchSession.Listener(){
                @Override public void onConnected(){runOnUiThread(()->{
                    message="تم الاتصال — جارٍ تحديد من يبدأ";
                    if(!lanOpeningStarted){lanOpeningStarted=true;lanSession.sendHello(playerNames[lanLocalSide-1],avatars[lanLocalSide-1]);if(lanLocalSide==1)lanSession.startOpeningToss();else message="تم الاتصال — بانتظار قرعة البداية";}
                    invalidate();
                });}
                @Override public void onPeerInfo(int side,String name,int avatar){runOnUiThread(()->{playerNames[side-1]=name;avatars[side-1]=avatar;invalidate();});}
                @Override public void onOpeningRolls(int b,int r,boolean tied){runOnUiThread(()->{
                    openingRoll1=b;openingRoll2=r;openingToss=true;
                    message=tied?"تعادل — إعادة القرعة تلقائياً":"قرعة البداية";
                    invalidate();
                });}
                @Override public void onRematchReady(){runOnUiThread(()->{
                    message="اللاعبان جاهزان — قرعة البداية";
                    invalidate();
                });}
                @Override public void onOpeningWinner(int side){runOnUiThread(()->{
                    if(!lanOpeningStarted || mode!=3)return;
                    openingToss=false;stage=2;player=side;die=0;choosing=false;lanTurnId=1;
                    message=(side==lanLocalSide?"أنت تبدأ":"الخصم يبدأ");
                    lanSession.beginTurn(lanTurnId,side);startBattleIntro();invalidate();
                });}
                @Override public void onTurnStarted(int id,int side,boolean local){runOnUiThread(()->{
                    lanTurnId=id;player=side;die=0;choosing=false;lanCountdown=30;
                    message=local?"دورك — اضغط أطلق خلال 30 ثانية":"دور الخصم — بانتظار رمي النرد";
                    invalidate();
                });}
                @Override public void onCountdown(int seconds){runOnUiThread(()->{lanCountdown=seconds;invalidate();});}
                @Override public void onRoll(int side,int value,boolean automatic){runOnUiThread(()->{
                    if(mode!=3 || winner!=0 || side!=player)return;
                    die=value;choosing=true;
                    message=(side==lanLocalSide?"أنت":"الخصم")+" رمى "+value+(automatic?" تلقائياً":"")+" — اختر السلاح";
                    if(!anyMove(side)){
                        message="لا توجد حركة متاحة — نتيجة النرد: "+die+" — تم تجاوز الدور";choosing=false;invalidate();
                        if(side==lanLocalSide)lanSession.cancelTurn();
                    }
                    invalidate();
                });}
                @Override public void onMove(int side,int lane,int value){runOnUiThread(()->{
                    if(mode!=3 || winner!=0 || side!=player || value!=die)return;
                    if(lane<0 || lane>2 || !canMove(side,lane)) {
                        message="وصلت حركة غير ممكنة — تمت مزامنة إيقافها";
                        invalidate();
                        return;
                    }
                    animateMove(side,lane,value);invalidate();
                });}
                @Override public void onTurnCancelled(int side){runOnUiThread(()->{
                    if(mode==3 && side==player){
                        final int epoch=matchEpoch;
                        message="الدور ملغي — النرد: "+die;
                        postDelayed(()->{if(epoch==matchEpoch && mode==3 && side==player){die=0;choosing=false;afterTurn();invalidate();}},1100);
                        invalidate();
                    }
                });}
                @Override public void onDisconnected(String reason){runOnUiThread(()->{
                    timerlessCloseLan();message="انقطع الاتصال: "+reason;
                    new AlertDialog.Builder(MainActivity.this).setTitle("انقطع اتصال Wi-Fi").setMessage(reason)
                        .setPositiveButton("العودة للرئيسية",(d,which)->{mode=0;setup=false;invalidate();}).show();
                });}
                @Override public void onProtocolError(String reason){runOnUiThread(()->{message=reason;invalidate();});}
            });
        }
        private void styleOnlineDialog(AlertDialog dialog) {
            int burgundy=Color.rgb(105,27,48), gold=Color.rgb(242,196,94);
            android.graphics.drawable.GradientDrawable buttonBg=new android.graphics.drawable.GradientDrawable();
            buttonBg.setColor(Color.rgb(38,43,35));buttonBg.setCornerRadius(18f);
            buttonBg.setStroke(2,burgundy);
            for(int which:new int[]{-1,-2,-3}) {
                android.widget.Button button=dialog.getButton(which);
                if(button!=null){button.setTextColor(gold);button.setBackground(buttonBg.getConstantState().newDrawable().mutate());}
            }
            int titleId=getResources().getIdentifier("alertTitle","id","android");
            android.widget.TextView platformTitle=dialog.findViewById(titleId);
            if(platformTitle!=null)platformTitle.setTextColor(gold);
            android.view.Window window=dialog.getWindow();
            if(window!=null) {
                android.graphics.drawable.GradientDrawable panel=new android.graphics.drawable.GradientDrawable();
                panel.setColor(Color.rgb(24,35,29));panel.setCornerRadius(22f);panel.setStroke(3,burgundy);
                window.setBackgroundDrawable(panel);
            }
            android.widget.ListView list=dialog.getListView();
            if(list!=null) {
                list.setBackgroundColor(Color.rgb(24,35,29));
                list.setDivider(new android.graphics.drawable.ColorDrawable(burgundy));list.setDividerHeight(2);
                list.post(()->{
                    for(int i=0;i<list.getChildCount();i++) {
                        android.view.View child=list.getChildAt(i);
                        if(child instanceof android.widget.TextView)((android.widget.TextView)child).setTextColor(gold);
                    }
                });
            }
        }
        private void showOnlineCardSelectionDialog() {
            if(onlineLoadoutDialogShown || onlineLoadoutReady || mode!=4)return;
            onlineLoadoutDialogShown=true;
            final int[] types={CardInventory.REPAIR,CardInventory.DOUBLE_DICE,CardInventory.CYBER_WARFARE,
                    CardInventory.SHIELD,CardInventory.SMOKE_SCREEN,CardInventory.MILITARY_SUPPLY,
                    CardInventory.RADAR,CardInventory.MINEFIELD,CardInventory.MINE_DETECTOR};
            final String[] labels={"🔧  الإصلاح","🎲  مضاعفة النرد","💻  الحرب السيبرانية","🛡  الدرع","🌫  ستار الدخان",
                    "📦  الإمداد العسكري","📡  الرادار","💣  الألغام الأرضية","🔎  كاشف الألغام"};
            final boolean[] checked=new boolean[types.length];
            AlertDialog loadoutDialog=new AlertDialog.Builder(MainActivity.this).setTitle("اختر 3 بطاقات سرية للحرب")
                .setMessage("اختر ثلاث بطاقات مختلفة من البطاقات الظاهرة أدناه. لن يراها الخصم إلا عند استخدام كل بطاقة. الضربة الجوية مستثناة.")
                .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->{
                    if(isChecked){int count=0;for(boolean value:checked)if(value)count++;
                        if(count>3){checked[which]=false;((AlertDialog)dialog).getListView().setItemChecked(which,false);
                            android.widget.Toast.makeText(MainActivity.this,"مسموح بثلاث بطاقات فقط",android.widget.Toast.LENGTH_SHORT).show();}}
                    invalidate();
                })
                .setPositiveButton("تأكيد الاختيار",(dialog,which)->{
                    int count=0;for(boolean value:checked)if(value)count++;
                    if(count!=3){onlineLoadoutDialogShown=false;
                        new AlertDialog.Builder(MainActivity.this).setMessage("يجب اختيار 3 بطاقات بالضبط.")
                            .setPositiveButton("متابعة",(d,wid)->showOnlineCardSelectionDialog()).show();return;}
                    for(int i=0;i<types.length;i++)if(checked[i])onlineCards[types[i]]=true;
                    onlineLoadoutReady=true;message="تم حفظ بطاقاتك بسرية — بانتظار اختيار الخصم";
                    if(onlineSession!=null)onlineSession.markLoadoutReady();
                    startOnlineBattleIfReady();invalidate();
                })
                .setNegativeButton("إلغاء",(dialog,which)->{
                    onlineLoadoutDialogShown=false;timerlessCloseOnline();mode=0;setup=false;
                    message="تم إلغاء المباراة";invalidate();
                }).setCancelable(false).create();
            loadoutDialog.setOnShowListener(dialog->{
                styleOnlineDialog(loadoutDialog);
                android.widget.ListView list=loadoutDialog.getListView();
                if(list!=null) {
                    list.setDivider(new android.graphics.drawable.ColorDrawable(Color.rgb(205,190,150)));
                    list.setDividerHeight(1);
                    list.setBackgroundColor(Color.rgb(24,35,29));
                    list.post(()->{
                        for(int i=0;i<list.getChildCount();i++) {
                            android.view.View child=list.getChildAt(i);
                            if(child instanceof android.widget.TextView) {
                                android.widget.TextView item=(android.widget.TextView)child;
                                item.setTextColor(Color.rgb(242,196,94));
                                item.setTextSize(17f);
                            }
                        }
                    });
                }
            });
            loadoutDialog.show();
        }
        private void startOnlineBattleIfReady() {
            if(mode!=4 || !onlineLoadoutReady || !onlinePeerLoadoutReady || onlineOpeningStarted
                    || onlineSession==null || !onlineSession.isConnected())return;
            onlineWaitingForPeer=false;
            message="اكتمل اختيار البطاقات — جارٍ إجراء قرعة البداية";
            onlineOpeningStarted=true;
            if(onlineLocalSide==1)onlineSession.startOpeningToss();
            invalidate();
        }
        private void createOnlineSession() {
            if(onlineSession!=null)onlineSession.close();
            onlineOpeningStarted=false; onlineTurnId=0;
            onlineLoadoutReady=false;onlinePeerLoadoutReady=false;onlineLoadoutDialogShown=false;
            java.util.Arrays.fill(onlineCards,false);
            for(int side=1;side<=2;side++)java.util.Arrays.fill(onlineCardsUsedBySide[side],false);
            selectedBattleCard=-1;onlineDoubleDicePending=false;onlineDoubleDiceLane=-1;
            onlineSession=new OnlineMatchSession(new OnlineMatchSession.Listener(){
                @Override public void onConnected(String code){runOnUiThread(()->{
                    if(onlineInvitePending && onlineLocalSide==1) {
                        message="تم تجهيز الدعوة — أرسلها لصديقك";
                        onlineInvitePending=false;
                        shareInviteLink(code);
                    } else if(onlineQuickMatch) {
                        message="جارٍ البحث عن لاعب متاح...";
                    } else {
                        message=onlineLocalSide==1?"الغرفة جاهزة — أرسل الرمز للاعب الثاني":"تم الانضمام — بانتظار بدء المباراة";
                        if(onlineLocalSide==1) new AlertDialog.Builder(MainActivity.this).setTitle("رمز غرفة GENERAL")
                            .setMessage("رمز غرفتك هو:\n\n"+code+"\n\nأرسل الرمز للاعب الثاني، وانتظر انضمامه.")
                            .setPositiveButton("حسناً",null).show();
                    }
                    invalidate();
                });}
                @Override public void onPeerInfo(int side,String name,int avatar){runOnUiThread(()->{
                    playerNames[side-1]=name; avatars[side-1]=avatar; invalidate();
                });}
                @Override public void onPeerJoined(){runOnUiThread(()->{
                    if(mode!=4)return;
                    boolean quickMatchFound=onlineQuickMatch;
                    message=quickMatchFound?"تم العثور على لاعب — جارٍ إجراء قرعة البداية":
                            (onlineLocalSide==1?"تم انضمام اللاعب — جارٍ إجراء قرعة البداية":"تم الاتصال بالمضيف — بانتظار القرعة");
                    onlineWaitingForPeer=false;
                    onlineQuickMatch=false;
                    showOnlineCardSelectionDialog();
                    invalidate();
                });}
                @Override public void onOpeningRolls(int b,int r,boolean tied){runOnUiThread(()->{
                    openingRoll1=b;openingRoll2=r;openingToss=true;
                    message=tied?"تعادل — إعادة القرعة تلقائياً":"قرعة البداية"; invalidate();
                });}
                @Override public void onOpeningWinner(int side){runOnUiThread(()->{
                    if(!onlineOpeningStarted || mode!=4)return;
                    openingToss=false;stage=2;player=side;die=0;choosing=false;onlineTurnId=1;
                    message=(side==onlineLocalSide?"أنت تبدأ":"الخصم يبدأ");
                    onlineSession.beginTurn(onlineTurnId,side);startBattleIntro();invalidate();
                });}
                @Override public void onRematchReady(){runOnUiThread(()->{
                    onlineOpeningStarted=false;onlineLoadoutReady=false;onlinePeerLoadoutReady=false;
                    onlineLoadoutDialogShown=false;resultRevealReady=false;java.util.Arrays.fill(onlineCards,false);
                    for(int side=1;side<=2;side++)java.util.Arrays.fill(onlineCardsUsedBySide[side],false);
                    message="اختَر بطاقاتك الثلاث للمباراة الجديدة";
                    showOnlineCardSelectionDialog();invalidate();
                });}
                @Override public void onPeerLoadoutReady(){runOnUiThread(()->{
                    onlinePeerLoadoutReady=true;
                    if(onlineLoadoutReady)startOnlineBattleIfReady();
                    else message="الخصم أنهى اختيار بطاقاته — اختر بطاقاتك الثلاث";
                    invalidate();
                });}
                @Override public void onCardUsed(int side,int cardType,int lane,int value){runOnUiThread(()->{
                    if(mode!=4 || winner!=0 || onlineCardsUsedBySide[side][cardType])return;
                    if(!applyOnlineCardEffect(side,cardType,lane,value,false))return;
                    cardNoticeType=cardType;cardNoticeFromGeneral=(side!=onlineLocalSide);
                    cardNoticeUntil=System.currentTimeMillis()+2000L;
                    setCardBattleDialogue(cardType,side,true);
                    invalidate();postInvalidateDelayed(35);
                });}
                @Override public void onTurnStarted(int id,int side,boolean local){runOnUiThread(()->{
                    onlineTurnId=id;player=side;die=0;choosing=false;lanCountdown=30;
                    message=local?"دورك — اضغط أطلق خلال 30 ثانية":"دور الخصم — بانتظار رمي النرد"; invalidate();
                });}
                @Override public void onCountdown(int seconds){runOnUiThread(()->{lanCountdown=seconds;invalidate();});}
                @Override public void onRoll(int side,int value,boolean automatic){runOnUiThread(()->{
                    if(mode!=4 || winner!=0 || side!=player)return;
                    die=value;choosing=true;
                    if(onlineDoubleDicePending){die=value*2;int target=onlineDoubleDiceLane;
                        if(onlineSession!=null)onlineSession.adjustCurrentRoll(side,value);
                        onlineDoubleDicePending=false;onlineDoubleDiceLane=-1;
                        if(side==onlineLocalSide && target>=0 && target<3 && onlineSession.sendMove(target)){
                            choosing=false;animateMove(side,target,die);return;
                        }
                    }
                    message=(side==onlineLocalSide?"أنت":"الخصم")+" رمى "+die+(automatic?" تلقائياً":"")+" — اختر السلاح";
                    if(!anyMove(side)){                        message="لا توجد حركة متاحة — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        choosing=false;invalidate();
                        if(side==onlineLocalSide)onlineSession.cancelTurn();
                    }
                    invalidate();
                });}
                @Override public void onMove(int side,int lane,int value){runOnUiThread(()->{
                    if(mode!=4 || winner!=0 || side!=player || value!=die)return;
                    if(lane<0 || lane>2 || !canMove(side,lane)){message="وصلت حركة غير ممكنة — تم تجاهلها";invalidate();return;}
                    animateMove(side,lane,value);invalidate();
                });}
                @Override public void onTurnCancelled(int side){runOnUiThread(()->{
                    if(mode==4 && side==player){
                        final int epoch=matchEpoch;
                        message="الدور ملغي — النرد: "+die;
                        postDelayed(()->{if(epoch==matchEpoch && mode==4 && side==player){
                            die=0;choosing=false;afterTurn();invalidate();
                        }},1100);
                        invalidate();
                    }
                });}
                @Override public void onRadarDecision(int side,int lane,boolean continueAttack){runOnUiThread(()->{
                    if(mode!=4 || !continueAttack || onlineRadarPendingLane!=lane || onlineRadarPendingAttackerSide==0)return;
                    int attackSide=onlineRadarPendingAttackerSide,amount=onlineRadarPendingAmount;
                    onlineRadarPendingLane=-1;onlineRadarPendingAttackerSide=0;radarPauseActive=false;
                    message="تم تجاوز إنذار الرادار — استكمل الهجوم";animateMove(attackSide,lane,amount);invalidate();
                });}
                @Override public void onDisconnected(String reason){runOnUiThread(()->{
                    onlineWaitingForPeer=false;
                    timerlessCloseOnline();message="انقطع اتصال الإنترنت: "+reason;
                    new AlertDialog.Builder(MainActivity.this).setTitle("انقطع اتصال الإنترنت")
                        .setMessage(reason).setPositiveButton("العودة للرئيسية",(d,which)->{
                            mode=0;setup=false;message="اختر طريقة اللعب";invalidate();
                        }).show();
                });}
                @Override public void onError(String reason){runOnUiThread(()->{
                    message=reason;
                    onlineWaitingForPeer=false;
                    if(onlineQuickMatch) {
                        timerlessCloseOnline();
                        onlineQuickMatch=false;
                        mode=0; setup=false;
                    }
                    new AlertDialog.Builder(MainActivity.this).setTitle("اللعب عبر الإنترنت")
                        .setMessage(reason).setPositiveButton("حسناً",null).show(); invalidate();
                });}
            });
        }
        private void startOnlineHost(String name,int avatar) {
            onlineLocalSide=1; onlineWaitingForPeer=onlineInvitePending;
            createOnlineSession(); message="جارٍ إنشاء غرفة الإنترنت...";
            onlineSession.createRoom(name,avatar); invalidate();
        }
        private void startOnlineQuickMatch(String name,int avatar) {
            onlineLocalSide=1; onlineWaitingForPeer=true;
            createOnlineSession(); message="جارٍ البحث عن لاعب متاح...";
            onlineSession.quickMatch(name,avatar); invalidate();
        }
        private void startOnlineJoin(String code,String name,int avatar) {
            onlineLocalSide=2; createOnlineSession(); message="جارٍ الانضمام إلى غرفة الإنترنت...";
            onlineSession.joinRoom(code,name,avatar); invalidate();
        }
        private void timerlessCloseOnline(){if(onlineSession!=null){onlineSession.close();onlineSession=null;}}
        private void timerlessCloseLan(){if(lanSession!=null){lanSession.close();lanSession=null;}}
        private void startLanHost(){
            createLanSession();message="بانتظار اتصال اللاعب الثاني...";
            new Thread(()->{try{lanSession.host();}catch(Exception ex){runOnUiThread(()->{
                if(mode!=3 || lanSession==null)return;
                message="تعذر إنشاء الاتصال";new AlertDialog.Builder(MainActivity.this).setMessage("تعذر إنشاء المباراة: "+ex.getMessage()).setPositiveButton("حسناً",null).show();invalidate();
            });}},"GENERAL-LAN-host").start();invalidate();
        }
        private void startLanJoin(String address){
            createLanSession();message="جارٍ الاتصال بالجهاز المضيف...";
            new Thread(()->{try{lanSession.join(address);}catch(Exception ex){runOnUiThread(()->{
                if(mode!=3 || lanSession==null)return;
                message="تعذر الاتصال";new AlertDialog.Builder(MainActivity.this).setMessage("تعذر الاتصال: "+ex.getMessage()).setPositiveButton("حسناً",null).show();invalidate();
            });}},"GENERAL-LAN-join").start();invalidate();
        }

        private void showNameDialog() {
            final EditText input = new EditText(MainActivity.this);
            input.setSingleLine(true);
            input.setText(playerNames[setupSide-1]);
            input.setSelection(input.getText().length());
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            new AlertDialog.Builder(MainActivity.this)
                .setTitle(setupSide==1?"اسم اللاعب الأول":"اسم اللاعب الثاني")
                .setMessage("اكتب الاسم الذي سيظهر أثناء المباراة")
                .setView(input)
                .setPositiveButton("حفظ", (dialog,which)->{
                    String value=input.getText().toString().trim();
                    if(!value.isEmpty()) { playerNames[setupSide-1]=value; savePlayerName(setupSide-1,value); }
                    invalidate();
                })
                .setNegativeButton("إلغاء",null)
                .show();
        }
        private int grayColor(int color) {
            int gray=(int)(Color.red(color)*.299f+Color.green(color)*.587f+Color.blue(color)*.114f);
            return Color.rgb(gray,gray,gray);
        }
        private void drawAvatar(Canvas c,int index,float cx,float cy,float radius,boolean selected) { drawAvatar(c,index,cx,cy,radius,selected,false); }
        private void drawAvatar(Canvas c,int index,float cx,float cy,float radius,boolean selected,boolean monochrome) {
            if(index==5)index=6; // Character 06 is reserved exclusively for General.
            float rr=radius*1.18f;drawArt(c,"character_"+String.format(Locale.US,"%02d",Math.floorMod(index,18)+1)+".png",cx-rr,cy-rr*1.03f,cx+rr,cy+rr*1.03f,false);
            if(selected){p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.005f));p.setColor(gold);c.drawRoundRect(new RectF(cx-rr,cy-rr*1.03f,cx+rr,cy+rr*1.03f),radius*.22f,radius*.22f,p);p.setStyle(Paint.Style.FILL);}
        }

        private String generalImageName() {
            String[] levels={"1_Khabeer_Nashi.png","2_Qaid_Zou_Khabra.png","3_General_Mukhadram.png","4_Qaid_Faiq_Alkhabra.png"};
            return levels[Math.max(0,Math.min(3,difficulty))];
        }
        private void drawGeneralAvatar(Canvas c,float cx,float cy,float radius,boolean selected,boolean monochrome) {
            Bitmap bitmap=art.get(generalImageName());
            if(bitmap==null){drawArt(c,"character_06.png",cx-radius,cy-radius,cx+radius,cy+radius,false);return;}
            // احتواء صورة الجنيرال داخل مساحة ثابتة مع الحفاظ على أبعادها كاملة.
            float maxW=Math.min(w*(radius>=h*.10f?.19f:.16f),radius*(radius>=h*.10f?1.95f:2.25f));
            float maxH=radius*(radius>=h*.10f?2.35f:2.55f);
            float aspect=(float)bitmap.getWidth()/Math.max(1,bitmap.getHeight());
            float imageW=maxW,imageH=imageW/aspect;
            if(imageH>maxH){imageH=maxH;imageW=imageH*aspect;}
            RectF dst=new RectF(cx-imageW*.5f,cy-imageH*.5f,cx+imageW*.5f,cy+imageH*.5f);
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(255);p.setFilterBitmap(true);
            c.drawBitmap(bitmap,null,dst,p);
            if(selected){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.004f));p.setColor(gold);c.drawRoundRect(dst,radius*.12f,radius*.12f,p);p.setStyle(Paint.Style.FILL);}
        }
        private void arrowButton(Canvas c,float cx,float cy,boolean left) {
            float r=h*.045f;p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(225,25,48,38));c.drawCircle(cx,cy,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.005f));p.setColor(gold);c.drawCircle(cx,cy,r,p);
            Path a=new Path();float d=r*.34f;
            if(left){a.moveTo(cx+d*.55f,cy-d);a.lineTo(cx-d*.55f,cy);a.lineTo(cx+d*.55f,cy+d);}else{a.moveTo(cx-d*.55f,cy-d);a.lineTo(cx+d*.55f,cy);a.lineTo(cx-d*.55f,cy+d);}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,h*.007f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.WHITE);c.drawPath(a,p);p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeJoin(Paint.Join.MITER);p.setStyle(Paint.Style.FILL);
        }
        private final String[] cardNames = {"إصلاح السلاح","مضاعفة النرد","الحرب السيبرانية","درع الحماية","الضربة الجوية","ستار الدخان","الإمداد العسكري","الرادار","حقل الألغام","كشف الألغام"};
        private final String[] cardIcons = {"🔧","🎲","💻","🛡","✈","🌫","📦","📡","💣","🔎"};
        private final String[] cardDescriptions = {
            "يعيد السلاح المدمّر مع طلقاته كاملة، أو يزيل التعطيل السيبراني دون تغيير الطلقات.",
            "تختار السلاح أولاً، ثم تضاعف رمية النرد التالية وتنفذ الحركة عليه.",
            "يعطّل سلاحاً واحداً لدى الخصم لدورين كاملين، ويخترق الدرع دون إلغائه.",
            "يحمي سلاحاً واحداً من الطلقات العادية لمدة دورين كاملين للخصم.",
            "تزيد عداد الطلقات على أسلحة الخصم الثلاثة 3 طلقات لكل سلاح.",
            "يخفي سلاحاً واحداً ويحميه 3 أدوار للخصم، باستثناء الضربة الجوية.",
            "بعد النرد، اختر إضافة 0 أو 1 أو 2 أو 3 إلى النتيجة.",
            "يكشف السلاح المستهدف بعد رمية الخصم ويمنحك 10 ثوانٍ لاستخدام الدرع.",
            "يزرع لغماً مخفياً في سلاح واحد؛ أول محاولة استخدام تفجّره وتخصم حتى 3 طلقات.",
            "يكشف اللغم في معسكر الخصم ويبطل مفعوله دون ضرر."
        };
        private void drawCardTile(Canvas c,int type,float l,float t,float r,float b,boolean selected,boolean enabled,int count) {
            int edge=selected?Color.rgb(245,190,65):Color.rgb(125,145,130);
            int fill=selected?Color.rgb(62,91,67):(enabled?Color.rgb(30,48,40):Color.rgb(75,78,72));
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);
            c.drawRoundRect(new RectF(l,t,r,b),h*.012f,h*.012f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.003f));p.setColor(edge);
            c.drawRoundRect(new RectF(l,t,r,b),h*.012f,h*.012f,p);p.setStyle(Paint.Style.FILL);
            Bitmap image=cardImageForType(type);
            if(image!=null) {
                RectF dst=new RectF(l+(r-l)*.10f,t+(b-t)*.07f,r-(r-l)*.10f,b-(b-t)*.18f);
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);
                c.drawBitmap(image,null,dst,p);
            } else txt(c,cardIcons[type],(l+r)*.5f,t+(b-t)*.48f,(b-t)*.47f,Color.WHITE,true);
            txt(c,String.valueOf(count),(l+r)*.5f,b-h*.008f,h*.018f,Color.WHITE,true);
        }
        private void drawInventory(Canvas c) {
            txt(c,"مخزن البطاقات",w*.5f,h*.115f,h*.052f,Color.rgb(245,190,65),true);
            txt(c,"إجمالي البطاقات: "+cardInventory.getTotalCount(),w*.5f,h*.165f,h*.029f,Color.WHITE,true);
            int[] counts=cardInventory.getCounts();
            float left=w*.055f,gap=w*.018f,cardW=(w*.872f-gap)/2f,top=h*.205f,cardH=h*.128f,rowGap=h*.012f;
            for(int i=0;i<CardInventory.CARD_TYPES;i++) {
                int col=i%2,row=i/2;float l=left+col*(cardW+gap),t=top+row*(cardH+rowGap);
                box(c,l,t,l+cardW,t+cardH,Color.rgb(27,43,36));
                drawCardTile(c,i,l+w*.012f,t+h*.012f,l+w*.075f,t+h*.078f,false,counts[i]>0,counts[i]);
                float titleSize=h*.030f;p.setTextSize(titleSize);p.setFakeBoldText(true);
                while(p.measureText(cardNames[i])>cardW*.76f && titleSize>h*.021f){titleSize*=.94f;p.setTextSize(titleSize);}
                txt(c,cardNames[i],l+cardW*.60f,t+h*.038f,titleSize,Color.WHITE,true);
                txt(c,"العدد: "+counts[i],l+cardW*.60f,t+h*.078f,h*.024f,Color.rgb(245,190,65),true);
                float descSize=h*.018f;p.setTextSize(descSize);p.setFakeBoldText(false);
                while(p.measureText(cardDescriptions[i])>cardW*.78f && descSize>h*.013f){descSize*=.94f;p.setTextSize(descSize);}
                txt(c,cardDescriptions[i],l+cardW*.60f,t+h*.108f,descSize,Color.rgb(220,229,216),false);
            }
            btn(c,"عودة",w*.41f,h*.91f,w*.59f,h*.985f,dark);
        }

        private String missionDifficultyName(int difficulty) {
            String[] labels={"سهلة","متوسطة","صعبة","صعبة جداً"};
            return labels[Math.max(0,Math.min(3,difficulty))];
        }
        private void drawMissionColumn(Canvas c,boolean weekly,float left,float right,int accent) {
            List<MissionManager.MissionTask> tasks=missionManager.getTasks(weekly);
            float headerTop=h*.185f,headerBottom=h*.245f;
            box(c,left,headerTop,right,headerBottom,Color.rgb(25,43,35));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.003f));p.setColor(accent);
            c.drawRoundRect(new RectF(left,headerTop,right,headerBottom),h*.014f,h*.014f,p);p.setStyle(Paint.Style.FILL);
            txt(c,weekly?"المهمات الأسبوعية":"المهمات اليومية",(left+right)*.5f,h*.224f,h*.032f,Color.WHITE,true);
            txt(c,"التجديد خلال: "+missionManager.getCountdown(weekly),(left+right)*.5f,h*.264f,h*.020f,Color.rgb(240,211,145),true);
            float top=h*.285f,rowH=h*.122f;
            for(int i=0;i<tasks.size();i++) {
                MissionManager.MissionTask task=tasks.get(i);
                float y=top+i*rowH;
                box(c,left,y,right,y+rowH-h*.008f,Color.rgb(238,244,232));
                float titleSize=h*.024f;
                p.setTextSize(titleSize);p.setFakeBoldText(true);
                float maxTextWidth=(right-left)*.88f;
                while(p.measureText(task.title)>maxTextWidth && titleSize>h*.017f){titleSize*=.94f;p.setTextSize(titleSize);}
                txt(c,task.title,(left+right)*.5f,y+h*.027f,titleSize,Color.rgb(35,55,42),true);
                String meta=missionDifficultyName(task.difficulty)+"  •  "+task.reward+" "+(task.reward==1?"بطاقة":"بطاقات");
                txt(c,meta,(left+right)*.5f,y+h*.052f,h*.018f,Color.rgb(130,91,29),false);
                float barWidth=(right-left)*.52f,l=(left+right-barWidth)*.5f,r=l+barWidth;
                float barY=y+h*.071f,barH=h*.012f;
                box(c,l,barY,r,barY+barH,Color.rgb(205,215,202));
                float ratio=Math.min(1f,(float)task.progress/Math.max(1,task.target));
                if(ratio>0)box(c,l,barY,l+(r-l)*ratio,barY+barH,task.completed?Color.rgb(35,155,83):Color.rgb(65,130,180));
                txt(c,task.progress+"/"+task.target,(left+right)*.5f,y+h*.108f,h*.018f,Color.rgb(45,65,48),true);
                if(task.completed)txt(c,"✓",right-h*.025f,y+h*.030f,h*.030f,Color.rgb(25,155,75),true);
            }
        }
        private void drawMissions(Canvas c) {
            txt(c,"المهمات العسكرية",w*.5f,h*.115f,h*.058f,Color.rgb(245,190,65),true);
            // عمودان متجاوران: اليومية على اليمين والأسبوعية على اليسار.
            drawMissionColumn(c,true,w*.035f,w*.485f,Color.rgb(155,105,28));
            drawMissionColumn(c,false,w*.515f,w*.965f,Color.rgb(55,125,175));
            btn(c,"عودة",w*.41f,h*.925f,w*.59f,h*.985f,dark);
            postInvalidateDelayed(1000);
        }
        private boolean hasMissionRewards() {
            int[] values=missionManager.getPendingMissionRewards();
            for(int value:values)if(value>0)return true;
            return false;
        }
        private boolean isRewardPopupVisible() {
            return mode==0 && !missionsScreen && !historyScreen && !progressScreen && !helpScreen && !inventoryScreen
                && (missionManager.getPendingLoginCard()>=0 || hasMissionRewards());
        }
        private void drawRewardCard(Canvas c,int type,float l,float t,float r,float b,int count) {
            box(c,l,t,r,b,Color.rgb(28,47,38));
            Bitmap image=cardImageForType(type);
            if(image!=null) {
                RectF dst=new RectF(l+(r-l)*.12f,t+(b-t)*.08f,r-(r-l)*.12f,b-(b-t)*.22f);
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);
                c.drawBitmap(image,null,dst,p);
            } else {
                txt(c,cardIcons[type],(l+r)*.5f,t+(b-t)*.56f,(b-t)*.48f,Color.WHITE,true);
            }
            txt(c,String.valueOf(count),(l+r)*.5f,b-(b-t)*.055f,h*.023f,Color.rgb(255,220,130),true);
        }
        private void drawRewardPopup(Canvas c) {
            int login=missionManager.getPendingLoginCard();
            int[] rewards=missionManager.getPendingMissionRewards();
            if(login<0 && !hasMissionRewards())return;
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(205,0,0,0));c.drawRect(0,0,w,h,p);
            box(c,w*.20f,h*.16f,w*.80f,h*.86f,Color.rgb(239,245,232));
            txt(c,login>=0?"مكافأة الدخول اليومي":"مكافآت المهمات",w*.5f,h*.265f,h*.052f,Color.rgb(120,83,25),true);
            if(login>=0) {
                txt(c,"حصلت على بطاقة جديدة",w*.5f,h*.37f,h*.030f,dark,true);
                float tw=w*.13f,th=h*.25f;
                drawRewardCard(c,login,w*.5f-tw*.5f,h*.43f,w*.5f+tw*.5f,h*.43f+th,1);
            } else {
                txt(c,"البطاقات التي أضيفت إلى مخزنك",w*.5f,h*.36f,h*.027f,dark,true);
                int count=0;for(int n:rewards)if(n>0)count++;
                float tw=Math.min(w*.12f,w*.62f/Math.max(1,count)),th=h*.23f,gap=w*.018f;
                float total=count*tw+Math.max(0,count-1)*gap,left=(w-total)*.5f;int slot=0;
                for(int type=0;type<CardInventory.CARD_TYPES;type++)if(rewards[type]>0) {
                    float x=left+slot*(tw+gap);
                    drawRewardCard(c,type,x,h*.43f,x+tw,h*.43f+th,rewards[type]);slot++;
                }
            }
            btn(c,"موافق",w*.39f,h*.755f,w*.61f,h*.835f,dark);
        }
        private void acknowledgeRewardPopup() {
            if(missionManager.getPendingLoginCard()>=0)missionManager.acknowledgeLoginCard();
            else missionManager.acknowledgeMissionRewards();
            invalidate();
        }

        private void drawSetup(Canvas c) {
            txt(c,mode==4?"إعداد اللعب أون لاين":(campaignBattle?"تجهيز بطاقات الحملة":"إعداد اللاعبين"),w*.5f,h*.105f,h*.060f,mode==4||campaignBattle?Color.rgb(242,196,94):dark,true);
            iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");
            int idx=setupSide-1;
            if(mode==2) {
                box(c,w*.055f,h*.18f,w*.47f,h*.90f,Color.argb(225,238,243,230));box(c,w*.53f,h*.18f,w*.945f,h*.90f,Color.argb(225,248,250,239));
                if(campaignBattle) {
                    if(campaignGeneralOpponent)drawGeneralAvatar(c,w*.265f,h*.36f,h*.115f,true,false);
                    else drawAvatar(c,1,w*.265f,h*.36f,h*.115f,true,true);
                    txt(c,computerOpponentName(),w*.265f,h*.555f,h*.032f,dark,true);
                    if(campaignGeneralOpponent) {
                        String[] levels={"أساسي","أصعب","محترف","أسطوري"};
                        txt(c,"مستوى جنيرال: "+levels[Math.max(0,Math.min(3,difficulty))],w*.265f,h*.605f,h*.027f,dark,true);
                        txt(c,"الدولة: "+campaignOpponentName,w*.265f,h*.655f,h*.023f,dark,false);
                    } else txt(c,"دولة محايدة",w*.265f,h*.615f,h*.027f,dark,true);
                } else {
                    drawGeneralAvatar(c,w*.265f,h*.36f,h*.115f,true,false);
                    String[] generalLevels={"خبير ناشئ","قائد ذو خبرة","جنرال مخضرم","قائد فائق الخبرة"};
                    txt(c,"المستوى الحالي: "+generalLevels[Math.max(0,Math.min(3,difficulty))],w*.265f,h*.555f,h*.023f,dark,true);
                    txt(c,"اختر مستوى الصعوبة",w*.265f,h*.592f,h*.027f,dark,true);
                    String[] levels={"مبتدئ","متوسط","محترف","أسطوري"};String[] symbols={"Ⅰ","Ⅱ","Ⅲ","★"};
                    for(int i=0;i<4;i++){
                        float top=h*(.615f+i*.069f),bottom=top+h*.064f;
                        int fill=difficulty==i?Color.rgb(180,132,35):Color.rgb(218,228,211);
                        box(c,w*.085f,top,w*.445f,bottom,fill);
                        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.0025f));p.setColor(difficulty==i?gold:Color.rgb(150,165,145));
                        c.drawRoundRect(new RectF(w*.085f,top,w*.445f,bottom),h*.012f,h*.012f,p);p.setStyle(Paint.Style.FILL);
                        txt(c,symbols[i],w*.145f,top+h*.043f,h*.034f,difficulty==i?Color.WHITE:dark,true);
                        txt(c,levels[i],w*.285f,top+h*.043f,h*.030f,difficulty==i?Color.WHITE:dark,difficulty==i);
                    }
                }
                drawAvatar(c,avatars[0],w*.765f,h*.405f,h*.135f,true);
                txt(c,playerNames[0],w*.765f,h*.585f,h*.034f,dark,true);
                txt(c,"هويتك الثابتة",w*.765f,h*.625f,h*.022f,dark,false);
                txt(c,"بطاقاتك للمباراة — اختر حتى 3",w*.765f,h*.715f,h*.020f,dark,true);
                int[] counts=cardInventory.getCounts();
                for(int i=0;i<CardInventory.CARD_TYPES;i++){
                    int row=i/5,col=i%5;float l=w*(.585f+col*.071f),t=h*(.735f+row*.067f);
                    drawCardTile(c,i,l,t,l+w*.064f,t+h*.058f,cardInventory.isSelected(i),counts[i]>0,counts[i]);
                }
            } else {
                if(mode==4) {
                    txt(c,onlineLocalSide==1?"إعداد الاتصال — اللاعب المضيف":"إعداد الاتصال — اللاعب المنضم",w*.5f,h*.20f,h*.036f,Color.rgb(242,196,94),true);
                    float portraitY=h*.43f;
                    drawAvatar(c,avatars[idx],w*.5f,portraitY,h*.125f,true);
                    txt(c,"الشخصية "+(avatars[idx]+1)+" من 17",w*.5f,portraitY+h*.16f,h*.027f,Color.rgb(242,196,94),true);
                    box(c,w*.27f,h*.62f,w*.73f,h*.71f,Color.rgb(32,39,32));
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,h*.004f));p.setColor(Color.rgb(105,27,48));
                    c.drawRoundRect(new RectF(w*.27f,h*.62f,w*.73f,h*.71f),h*.018f,h*.018f,p);p.setStyle(Paint.Style.FILL);
                    txt(c,playerNames[idx],w*.5f,h*.675f,h*.043f,Color.rgb(242,196,94),true);
                    txt(c,"هويتك الثابتة",w*.5f,h*.745f,h*.024f,Color.rgb(242,196,94),false);
                } else {
                    txt(c,setupSide==1?"المستخدم — الأزرق":"اللاعب الثاني — الأحمر",w*.5f,h*.20f,h*.040f,setupSide==1?blue:red,true);
                    float portraitY=h*.43f;drawAvatar(c,avatars[idx],w*.5f,portraitY,h*.125f,true);
                    if(mode==1 && setupSide==2){arrowButton(c,w*.39f,portraitY,true);arrowButton(c,w*.61f,portraitY,false);}
                    txt(c,"الشخصية "+(avatars[idx]+1)+" من 17",w*.5f,portraitY+h*.16f,h*.027f,dark,true);
                    box(c,w*.27f,h*.62f,w*.73f,h*.71f,Color.rgb(248,250,239));txt(c,playerNames[idx],w*.5f,h*.675f,h*.043f,dark,true);
                    txt(c,mode==1 && setupSide==2?"اضغط على الاسم لتعديله":"هويتك الثابتة",w*.5f,h*.745f,h*.024f,dark,false);
                }
            }
            String startLabel=setupSide==1 && mode==1?"التالي":(mode==4 && onlineQuickMatch?"ابحث عن لاعب":(mode==4 && onlineInvitePending?"إنشاء الدعوة":(mode==4 && onlineLocalSide==1?"إنشاء الغرفة":"ابدأ المباراة")));
            btn(c,startLabel,w*.37f,h*.91f,w*.63f,h*.985f,mode==4?Color.rgb(105,27,48):(setupSide==1?blue:red));
            if(mode==4) {
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.003f));p.setColor(Color.rgb(242,196,94));
                c.drawRoundRect(new RectF(w*.37f,h*.91f,w*.63f,h*.985f),h*.018f,h*.018f,p);p.setStyle(Paint.Style.FILL);
            }
        }


        private void drawMenuNavigation(Canvas c) {
            iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");
            iconButton(c,w*.145f,h*.065f,w*.245f,h*.155f,dark,"⚙");
            iconButton(c,w*.875f,h*.065f,w*.965f,h*.155f,Color.rgb(91,24,43),"⏻");
        }
        private void wideMenuButton(Canvas c,String glyph,String label,float l,float t,float r,float b) {
            RectF rect=new RectF(l,t,r,b);
            p.setShader(new LinearGradient(l,t,r,b,Color.rgb(55,67,55),Color.rgb(17,28,25),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(rect,h*.035f,h*.035f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(5,h*.014f));p.setColor(Color.rgb(105,27,48));
            c.drawRoundRect(rect,h*.035f,h*.035f,p);
            p.setStrokeWidth(Math.max(2,h*.004f));p.setColor(Color.rgb(255,211,112));
            c.drawRoundRect(new RectF(l+h*.008f,t+h*.008f,r-h*.008f,b-h*.008f),h*.030f,h*.030f,p);
            p.setStyle(Paint.Style.FILL);
            txt(c,glyph,(l+r)*.5f,(t+b)*.49f,h*.105f,Color.rgb(242,196,94),true);
            txt(c,label,(l+r)*.5f,b-h*.045f,h*.052f,Color.rgb(242,196,94),true);
        }
        private void menuButton(Canvas c,String glyph,String label,float l,float t,float r,float b,int color) {
            // زر أيقونة مربع مستقل، والعنوان تحته مباشرة.
            float cx=(l+r)*.5f;
            float side=Math.min(w*.205f,h*.145f);
            float top=t+(b-t)*.08f;
            RectF tile=new RectF(cx-side*.5f,top,cx+side*.5f,top+side);
            p.setShader(new LinearGradient(tile.left,tile.top,tile.right,tile.bottom,
                    shade(color,1.22f),shade(color,.58f),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(tile,side*.13f,side*.13f,p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,side*.035f));
            p.setColor(Color.rgb(105,27,48));
            c.drawRoundRect(tile,side*.13f,side*.13f,p);
            p.setStrokeWidth(Math.max(1.5f,side*.012f));p.setColor(Color.rgb(242,196,94));
            c.drawRoundRect(new RectF(tile.left+side*.025f,tile.top+side*.025f,tile.right-side*.025f,tile.bottom-side*.025f),side*.11f,side*.11f,p);
            p.setStyle(Paint.Style.FILL);
            txt(c,glyph,cx,top+side*.68f,side*.56f,Color.rgb(242,196,94),true);
            txt(c,label,cx,top+side+h*.032f,h*.024f,Color.rgb(242,196,94),true);
        }
        private void miniMenuButton(Canvas c,String glyph,String label,float cx,float top,int color) {
            float side=h*.083f;
            RectF tile=new RectF(cx-side*.5f,top,cx+side*.5f,top+side);
            p.setShader(new LinearGradient(tile.left,tile.top,tile.right,tile.bottom,
                    shade(color,1.22f),shade(color,.58f),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(tile,side*.15f,side*.15f,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,side*.025f));
            p.setColor(Color.rgb(105,27,48));c.drawRoundRect(tile,side*.15f,side*.15f,p);
            p.setStyle(Paint.Style.FILL);
            txt(c,glyph,cx,top+side*.69f,side*.54f,Color.rgb(242,196,94),true);
            txt(c,label,cx,top+side+h*.025f,h*.021f,Color.rgb(242,196,94),true);
        }
        private int rankIndex(int points) {
            int rank=0;
            for(int i=0;i<rankThresholds.length;i++)if(points>=rankThresholds[i])rank=i;
            return rank;
        }
        private String playerRankPrefix() {
            int points=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE).getInt("points",0);
            return "["+rankNames[rankIndex(points)]+"] ";
        }        private String rankedPlayerName(String name) { return playerRankPrefix()+name; }
        private void awardProgressPoints() {
            if(pointsAwarded || !isGeneralOpponent() || winner!=1)return;
            int[] base={10,20,35,50}, perfectBonus={5,10,20,30};
            int level=Math.max(0,Math.min(3,difficulty));
            int earned=base[level]+(perfectWin?perfectBonus[level]:0);
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE);
            int oldPoints=prefs.getInt("points",0);
            int newPoints=oldPoints+earned;
            int oldRank=rankIndex(oldPoints),newRank=rankIndex(newPoints);
            prefs.edit().putInt("points",newPoints)
                .putInt("wins",prefs.getInt("wins",0)+1)
                .putInt("perfectWins",prefs.getInt("perfectWins",0)+(perfectWin?1:0))
                .putInt("lastEarned",earned).putInt("lastDifficulty",difficulty).apply();
            for(int rank=oldRank+1;rank<=newRank;rank++) cardInventory.claimRankPromotion(rank-1);
            matchPoints=earned;pointsAwarded=true;
        }
        private void finishMatch() {
            if(winner==0)return;
            awardProgressPoints();
            if(isGeneralOpponent() && missionManager.isTrackedMatchActive()) {
                int lostWeapons=missionManager.getPlayerLostWeaponCount();
                missionManager.finishTrackedMatch(winner==1,difficulty,lostWeapons,winner==1 && perfectWin);
            }
            if(mode==2 && !cardMatchSettled && cardInventory.isMatchActive()) {
                cardInventory.finishMatchNormally();
                int[] rewards={1,2,3,4};
                for(int i=0;i<CardInventory.CARD_TYPES;i++)matchRewardCounts[i]=0;
                if(winner==1) {
                    int[] awarded=cardInventory.addRandomCards(rewards[Math.max(0,Math.min(3,difficulty))]+(perfectWin?1:0));
                    System.arraycopy(awarded,0,matchRewardCounts,0,CardInventory.CARD_TYPES);
                }
                cardMatchSettled=true;
                clearBattleCardEffects();
            }
            bonusAttempt=false;battleIntro=false;die=0;choosing=false;resultRevealReady=false;
            if(mode==2)message=winner==1?"انتهت المباراة — أنت الفائز!":"انتهت المباراة — فاز "+computerOpponentName()+"!";
            else message="انتهت المباراة — الفائز: "+(winner==1?playerNames[0]:playerNames[1]);
            saveMatchResult();
            final int finishedEpoch=matchEpoch;
            postDelayed(()->{
                if(finishedEpoch==matchEpoch && winner!=0 && !bonusAttempt) {
                    resultRevealReady=true;invalidate();
                }
            },2000L);
            invalidate();
        }
        private void beginBonusAttempt() {
            bonusAttempt=true;player=1;die=0;choosing=false;
            message="فزت! لديك رمية إضافية لتدمير السلاح الثالث والحصول على نقاط أكثر";
            invalidate();
        }
        private void startBattleIntro() {
            playRandomBattleMusic();
            battleIntro=true;introStep=3;
            final int epoch=matchEpoch;invalidate();
            postDelayed(new Runnable(){@Override public void run(){
                if(epoch!=matchEpoch || !battleIntro)return;
                if(introStep>1){introStep--;invalidate();postDelayed(this,700);}
                else if(introStep==1){
                    introStep=0;invalidate();
                    postDelayed(()->{
                        if(epoch!=matchEpoch || !battleIntro)return;
                        battleIntro=false;
                        if(stage<2) {
                            message="استعدوا — النظام يحدد من يبدأ";
                            invalidate();
                            startOpeningToss();
                            return;
                        }
                        message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب الأول يبدأ"):(mode==2?computerOpponentName()+" يبدأ":"اللاعب الثاني يبدأ");
                        invalidate();
                        if(mode==2 && player==2)computerTurn();
                    },800);
                }
            }},700);
        }
        private void drawBattleIntro(Canvas c) {
            p.setColor(Color.argb(224,12,20,18));c.drawRect(0,0,w,h,p);
            box(c,w*.12f,h*.16f,w*.88f,h*.86f,Color.rgb(29,39,35));
            iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");
            txt(c,"استعدوا للحرب",w*.5f,h*.25f,h*.055f,Color.rgb(245,190,65),true);
            if(mode==2) {
                if(isGeneralOpponent())drawGeneralAvatar(c,w*.30f,h*.45f,h*.120f,false,false);
                else drawAvatar(c,1,w*.30f,h*.45f,h*.115f,false,true);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,computerOpponentName(),w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,rankedPlayerName(playerNames[0]),w*.70f,h*.61f,h*.036f,Color.WHITE,true);
                if(isGeneralOpponent())drawIntroCardRow(c,true,w*.30f,h*.665f);
                drawIntroCardRow(c,false,w*.70f,h*.665f);
            } else {
                drawAvatar(c,avatars[1],w*.30f,h*.45f,h*.115f,false);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,rankedPlayerName(playerNames[1]),w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,rankedPlayerName(playerNames[0]),w*.70f,h*.61f,h*.036f,Color.WHITE,true);
            }
            drawArt(c,"vs_emblem.png",w*.425f,h*.385f,w*.575f,h*.555f,false);
            txt(c,String.valueOf(Math.max(1,introStep)),w*.5f,h*.79f,h*.17f,Color.WHITE,true);
        }
        private float drawHelpLine(Canvas c,String text,float y,int color,float size,boolean bold) {
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Math.max(size,h*.024f));p.setFakeBoldText(bold);
            while(p.measureText(text)>w*.82f && p.getTextSize()>h*.017f)p.setTextSize(p.getTextSize()*.94f);
            p.setColor(color);c.drawText(text,w*.5f,y,p);
            p.setFakeBoldText(false);
            return y+h*.043f;
        }
        private float drawHelpHeading(Canvas c,String text,float y) {
            y+=h*.012f;
            txt(c,text,w*.5f,y,h*.034f,Color.rgb(155,105,28),true);
            return y+h*.052f;
        }
        private void drawHelpButton(Canvas c,String title,String subtitle,int icon,float left,float top,float right,float bottom) {
            RectF r=new RectF(left,top,right,bottom);
            p.setShader(new LinearGradient(left,top,right,bottom,Color.rgb(43,61,48),Color.rgb(17,29,25),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,h*.018f,h*.018f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.0025f));p.setColor(Color.rgb(126,37,55));
            c.drawRoundRect(r,h*.018f,h*.018f,p);p.setStrokeWidth(Math.max(1,h*.0012f));p.setColor(gold);
            c.drawRoundRect(new RectF(left+h*.006f,top+h*.006f,right-h*.006f,bottom-h*.006f),h*.014f,h*.014f,p);
            p.setStyle(Paint.Style.FILL);
            float cx=(left+right)*.5f,cy=(top+bottom)*.5f;
            txt(c,title,cx,cy-h*.006f,h*.027f,Color.rgb(245,204,112),true);
            txt(c,subtitle,cx,cy+h*.034f,h*.016f,Color.rgb(225,231,216),false);
        }
        private void drawHelpBitmap(Canvas c,Bitmap bitmap,float cx,float cy,float width,float height) {
            if(bitmap==null)return;p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);
            c.drawBitmap(bitmap,null,new RectF(cx-width*.5f,cy-height*.5f,cx+width*.5f,cy+height*.5f),p);
        }
        private void drawHelpInfoCard(Canvas c,String title,String detail,Bitmap image,float y,float height) {
            float left=w*.075f,right=w*.925f,top=y,bottom=y+height;
            p.setShader(new LinearGradient(left,top,right,bottom,Color.rgb(38,54,44),Color.rgb(18,29,25),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(left,top,right,bottom),h*.012f,h*.012f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.5f,h*.0018f));p.setColor(Color.rgb(123,39,55));
            c.drawRoundRect(new RectF(left,top,right,bottom),h*.012f,h*.012f,p);p.setStyle(Paint.Style.FILL);
            float imageCx=right-w*.075f,imageCy=(top+bottom)*.5f;
            if(image!=null) {
                drawHelpBitmap(c,image,imageCx,imageCy,w*.075f,height*.66f);
            } else {
                // لا تترك أي بطاقة بلا رمز مرئي إذا تعذر تحميل ملف صورتها.
                for(int i=0;i<CardInventory.CARD_TYPES;i++) {
                    if(cardNames[i].equals(title)) {
                        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(31,48,39));
                        c.drawCircle(imageCx,imageCy,w*.043f,p);
                        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.0025f));p.setColor(gold);
                        c.drawCircle(imageCx,imageCy,w*.043f,p);p.setStyle(Paint.Style.FILL);
                        txt(c,cardIcons[i],imageCx,imageCy+h*.018f,h*.060f,Color.WHITE,true);
                        break;
                    }
                }
            }
            float textX=w*.51f;
            txt(c,title,textX,top+height*.31f,h*.014f,Color.rgb(245,204,112),true);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(h*.042f);p.setFakeBoldText(false);p.setColor(Color.rgb(225,231,216));
            String[] words=detail.split(" ");String line="";float yy=top+height*.61f;
            for(String word:words){String next=line.isEmpty()?word:line+" "+word;if(p.measureText(next)>w*.66f&&!line.isEmpty()){c.drawText(line,textX,yy,p);yy+=h*.058f;line=word;}else line=next;}
            if(!line.isEmpty())c.drawText(line,textX,yy,p);p.setTextAlign(Paint.Align.CENTER);
        }
        private void drawHelp(Canvas c) {
            // لوحة شرح عسكرية متناسقة مع خلفيات وأزرار اللعبة.
            p.setShader(new LinearGradient(0,h*.10f,0,h*.90f,Color.rgb(35,52,42),Color.rgb(13,23,21),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(w*.035f,h*.085f,w*.965f,h*.99f),h*.025f,h*.025f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(4,h*.006f));p.setColor(Color.rgb(116,34,51));
            c.drawRoundRect(new RectF(w*.035f,h*.085f,w*.965f,h*.99f),h*.025f,h*.025f,p);p.setStrokeWidth(Math.max(2,h*.002f));p.setColor(gold);
            c.drawRoundRect(new RectF(w*.045f,h*.095f,w*.955f,h*.98f),h*.020f,h*.020f,p);p.setStyle(Paint.Style.FILL);
            String[] titles={"طريقة اللعب","الأسلحة","البطاقات","المهمات","النقاط والرتب","اللعب الجماعي"};
            txt(c,helpSection<0?"دليل جنيرال":titles[Math.min(5,helpSection)],w*.5f,h*.15f,h*.025f,Color.rgb(245,200,105),true);
            c.save();c.clipRect(w*.065f,h*.19f,w*.935f,h*.875f);
            if(helpSection<0) {
                String[] subs={"الحرب والأدوار والفوز","الدبابة والطائرة والمدفع","البطاقات العشر واستخدامها","اليومية والأسبوعية","الترقيات والمكافآت","محلي وشبكة وإنترنت"};
                float top=h*.225f,cardH=h*.19f;
                for(int i=0;i<6;i++){int row=i/2,col=i%2;float left=col==0?w*.075f:w*.515f,right=col==0?w*.485f:w*.925f;
                    drawHelpButton(c,titles[i],subs[i],i,left,top+row*h*.205f,right,top+row*h*.205f+cardH);}
            } else {
                c.translate(0,-helpScrollY);float y=h*.205f,cardH=h*.12f,gap=h*.012f;
                if(helpSection==0) {
                    drawHelpInfoCard(c,"هدف المعركة","دمّر سلاحين من أسلحة الخصم الثلاثة لتحسم الفوز، ويمكنك مواصلة القتال على السلاح الثالث للمكافأة.",art.get("tank_01.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"رمي النرد","اضغط زر إطلاق النرد، ثم اختر السلاح الذي تريد تحريكه بعد ظهور النتيجة.",art.get("cannon_01.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"خطوات الهجوم","يتحرك السلاح بعدد خطوات النرد، ويحتاج المسار إلى 15 خطوة لإصابة السلاح المقابل.",art.get("fighter_01.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"الأدوار","تتناوب أنت وجنيرال على رمي النرد واختيار السلاح وتنفيذ الحركة.",art.get("fighter_02.png"),y,cardH);
                } else if(helpSection==1) {
                    for(int i=0;i<3;i++){drawHelpInfoCard(c,names[i],"هذا السلاح يواجه السلاح المقابل في مساره. اختره بعد رمية النرد لتحريك الهجوم.",art.get(i==0?"tank_01.png":i==1?"fighter_01.png":"cannon_01.png"),y,cardH);y+=cardH+gap;}
                    drawHelpInfoCard(c,"تدمير السلاح","عند اكتمال 15 خطوة يُدمّر السلاح المقابل، ويتوقف القتال في ذلك المسار.",art.get("tank_01.png"),y,cardH);
                } else if(helpSection==2) {
                    for(int i=0;i<CardInventory.CARD_TYPES;i++){drawHelpInfoCard(c,cardNames[i],cardDescriptions[i],cardImageForType(i),y,cardH);y+=cardH+gap;}
                    drawHelpInfoCard(c,"اختيار البطاقات","اختر حتى 3 أنواع مختلفة قبل مباراة جنيرال. البطاقة المستخدمة تُستهلك ولا تستخدم أكثر من بطاقة في الدور.",null,y,cardH);
                } else if(helpSection==3) {
                    drawHelpInfoCard(c,"المهمات اليومية","3 مهمات: سهلة ومتوسطة وصعبة. تتجدد يومياً منتصف الليل بتوقيت الأردن.",cardImageForType(CardInventory.RADAR),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"المهمات الأسبوعية","5 مهمات: مهمتان متوسطتان، مهمتان صعبتان، ومهمة صعبة جداً. تتجدد الجمعة.",cardImageForType(CardInventory.DOUBLE_DICE),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"المكافآت","اليومية: 1 و2 و3 بطاقات حسب الصعوبة. الأسبوعية: 3 و5 و7 بطاقات.",cardImageForType(CardInventory.MILITARY_SUPPLY),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"شروط الاحتساب","تُحتسب المهمات عند اللعب ضد جنيرال فقط، ولا تُحتسب في اللعب الجماعي.",art.get("vs_emblem.png"),y,cardH);
                } else if(helpSection==4) {
                    drawHelpInfoCard(c,"النقاط العسكرية","تُكتسب نقاط التقدم من الفوز على جنيرال، وتزداد مع ارتفاع مستوى الصعوبة.",art.get("character_01.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"الرتب","تتدرج من جندي إلى لواء، والترقية تمنح بطاقات إضافية.",art.get("character_02.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"مكافأة الفوز الكامل","تدمير الأسلحة الثلاثة يمنح مكافأة إضافية.",art.get("tank_01.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"الدخول اليومي","تحصل على بطاقة عشوائية واحدة عند الدخول مرة كل يوم.",cardImageForType(CardInventory.MILITARY_SUPPLY),y,cardH);
                } else {
                    drawHelpInfoCard(c,"اللعب المحلي","لاعبان على الهاتف نفسه، ويتناوبان على رمي النرد واختيار الأسلحة.",art.get("vs_emblem.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"اللعب عبر Wi-Fi","يتصل جهازان بالشبكة المحلية نفسها؛ أحدهما ينشئ المباراة والآخر ينضم.",art.get("vs_emblem.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"اللعب عبر الإنترنت","أنشئ مباراة أو انضم إلى مباراة لاعب آخر عند توفر الاتصال.",art.get("vs_emblem.png"),y,cardH);y+=cardH+gap;
                    drawHelpInfoCard(c,"الإعدادات","يمكنك التحكم بالموسيقى والمؤثرات الصوتية والاهتزاز من شاشة الإعدادات.",cardImageForType(CardInventory.SHIELD),y,cardH);
                }
                helpContentHeight=(int)(y+cardH+h*.04f);
            }
            c.restore();
            if(helpSection>=0)txt(c,"اسحب للأعلى والأسفل لعرض بقية الشرح",w*.5f,h*.887f,h*.018f,Color.rgb(225,205,155),false);
            btn(c,helpSection<0?"العودة إلى الرئيسية":"العودة إلى قائمة الشروحات",w*.32f,h*.91f,w*.68f,h*.985f,dark);
        }

        private void drawProgress(Canvas c) {
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE);
            int points=prefs.getInt("points",0),wins=prefs.getInt("wins",0);
            int perfect=prefs.getInt("perfectWins",0),last=prefs.getInt("lastEarned",0);
            int rank=rankIndex(points),previous=rank-1,next=rank<rankNames.length-1?rank+1:rank;
            txt(c,"لوحة التقدم العسكري",w*.5f,h*.16f,h*.055f,dark,true);
            float gap=w*.018f,cardW=(w*.76f-gap*2)/3f,left=w*.12f,top=h*.235f,cardH=h*.245f;
            float[] centers={left+cardW*.5f,left+cardW+gap+cardW*.5f,left+2*(cardW+gap)+cardW*.5f};
            int[] indices={previous,rank,next};
            String[] labels={"الرتبة السابقة","رتبتك الحالية","الرتبة التالية"};
            for(int i=0;i<3;i++) {
                boolean current=i==1;
                float l=left+i*(cardW+gap),r=l+cardW;
                box(c,l,top,r,top+cardH,current?Color.rgb(225,236,215):Color.rgb(244,248,235));
                txt(c,labels[i],centers[i],top+h*.055f,h*.022f,current?Color.rgb(155,105,28):dark,true);
                if(indices[i]>=0 && indices[i]<rankNames.length) {
                    txt(c,rankNames[indices[i]],centers[i],top+h*.135f,current?h*.050f:h*.038f,current?Color.rgb(155,105,28):dark,true);
                    txt(c,rankThresholds[indices[i]]+" نقطة",centers[i],top+h*.195f,h*.021f,dark,false);
                } else {
                    txt(c,current?"—":"لم تبدأ بعد",centers[i],top+h*.135f,h*.029f,dark,true);
                    txt(c,"",centers[i],top+h*.195f,h*.021f,dark,false);
                }
            }
            if(next>rank) {
                int rankStart=rankThresholds[rank],rankGoal=rankThresholds[next];
                int remaining=Math.max(0,rankGoal-points);
                float ratio=(float)(points-rankStart)/(rankGoal-rankStart);
                ratio=Math.max(0f,Math.min(1f,ratio));
                txt(c,"تقدمك نحو رتبة "+rankNames[next],w*.5f,h*.535f,h*.029f,dark,true);
                txt(c,points+" من "+rankGoal+" نقطة",w*.5f,h*.575f,h*.027f,Color.rgb(35,91,58),true);
                float l=w*.16f,r=w*.84f,y=h*.605f,barH=h*.038f;
                box(c,l,y,r,y+barH,Color.rgb(215,224,210));
                if(ratio>0f)box(c,l,y,l+(r-l)*ratio,y+barH,Color.rgb(45,145,82));
                txt(c,"باقي "+remaining+" نقطة للترقية",w*.5f,h*.685f,h*.027f,Color.rgb(155,105,28),true);
            } else {
                txt(c,"مجموع نقاطك: "+points+" نقطة",w*.5f,h*.565f,h*.030f,dark,true);
                txt(c,"وصلت إلى أعلى رتبة عسكرية!",w*.5f,h*.645f,h*.028f,Color.rgb(155,105,28),true);
            }
            box(c,w*.12f,h*.70f,w*.88f,h*.87f,Color.rgb(244,248,235));
            txt(c,"الانتصارات: "+wins+"     انتصارات كاملة: "+perfect,w*.5f,h*.765f,h*.026f,dark,true);
            txt(c,"نقاط آخر مباراة: "+last,w*.5f,h*.825f,h*.025f,dark,false);
            btn(c,"عودة",w*.41f,h*.93f,w*.59f,h*.99f,dark);
        }

        private JSONArray loadHistory() {
            try { return new JSONArray(getSharedPreferences("GENERAL_STATS",MODE_PRIVATE).getString("matches","[]")); }
            catch(Exception ex) { return new JSONArray(); }
        }
        private void saveMatchResult() {
            if(recorded || winner==0)return;
            recorded=true;
            try {
                JSONArray old=loadHistory(), updated=new JSONArray();
                JSONObject item=new JSONObject();
                String opponent=mode==2?computerOpponentName():(winner==1?playerNames[1]:playerNames[0]);
                String winnerName=winner==1?playerNames[0]:(mode==2?computerOpponentName():playerNames[1]);
                String loserName=winner==1?(mode==2?computerOpponentName():playerNames[1]):playerNames[0];
                item.put("opponent",opponent); item.put("winner",winnerName); item.put("loser",loserName);
                if(mode==2) item.put("result",winner==1?"فوز":"خسارة");
                else {
                    item.put("player1",playerNames[0]);
                    item.put("player2",playerNames[1]);
                    item.put("winnerSide",winner);
                }
                item.put("mode",mode);
                item.put("points",isGeneralOpponent() && winner==1?matchPoints:0);
                item.put("perfectWin",isGeneralOpponent() && winner==1 && perfectWin);
                item.put("difficulty",difficulty);
                item.put("date",new SimpleDateFormat("EEEE dd/MM/yyyy HH:mm",new Locale("ar","JO")).format(new Date()));
                updated.put(item);
                int start=Math.max(0,old.length()-99);
                for(int i=start;i<old.length();i++)updated.put(old.getJSONObject(i));
                getSharedPreferences("GENERAL_STATS",MODE_PRIVATE).edit().putString("matches",updated.toString()).apply();
            } catch(Exception ignored) { }
        }
        private void drawHistory(Canvas c) {
            JSONArray data=loadHistory();
            int total=data.length(),wins=0,losses=0;
            java.util.LinkedHashMap<String,int[]> byOpponent=new java.util.LinkedHashMap<>();
            for(int i=total-1;i>=0;i--)try {
                JSONObject o=data.getJSONObject(i);
                if("جنيرال".equals(o.optString("opponent"))) {
                    if("فوز".equals(o.optString("result")))wins++;
                    else if("خسارة".equals(o.optString("result")))losses++;
                }
                if((o.optInt("mode",2)==1 || o.optInt("mode",2)==3)
                        && o.has("player1") && o.has("player2")) {
                    String p1=o.optString("player1"), p2=o.optString("player2");
                    int side=o.optInt("winnerSide",0);
                    int[] first=byOpponent.get(p1+" ضد "+p2);
                    if(first==null){first=new int[2];byOpponent.put(p1+" ضد "+p2,first);}
                    int[] second=byOpponent.get(p2+" ضد "+p1);
                    if(second==null){second=new int[2];byOpponent.put(p2+" ضد "+p1,second);}
                    if(side==1){first[0]++;second[1]++;}                    else if(side==2){first[1]++;second[0]++;}
                } else {
                    String opponent=o.optString("opponent","غير معروف");
                    int[] counts=byOpponent.get(opponent);
                    if(counts==null){counts=new int[2];byOpponent.put(opponent,counts);}
                    String result=o.optString("result");
                    if("فوز".equals(result))counts[0]++;
                    else if("خسارة".equals(result))counts[1]++;
                }
            } catch(Exception ignored) { }

            txt(c,"إحصائيات GENERAL",w*.5f,h*.16f,h*.052f,dark,true);

            // Three large, clearly labelled summary cards.
            float cardTop=h*.205f, cardBottom=h*.365f, cardGap=w*.018f;
            float cardW=w*.285f, cardStart=w*.055f;
            int[] cardColors={Color.rgb(244,247,235),Color.rgb(225,242,226),Color.rgb(250,231,225)};
            String[] labels={"إجمالي المباريات","الانتصارات","الخسائر"};
            int[] values={total,wins,losses};
            int[] valueColors={dark,Color.rgb(35,125,72),Color.rgb(180,65,53)};
            for(int i=0;i<3;i++) {
                float left=cardStart+i*(cardW+cardGap);
                box(c,left,cardTop,left+cardW,cardBottom,cardColors[i]);
                txt(c,labels[i],left+cardW*.5f,cardTop+h*.047f,h*.026f,dark,true);
                txt(c,String.valueOf(values[i]),left+cardW*.5f,cardTop+h*.118f,h*.060f,valueColors[i],true);
            }

            // General match record with an easy-to-read win-rate bar.
            float generalTop=h*.395f, generalBottom=h*.515f;
            box(c,w*.055f,generalTop,w*.945f,generalBottom,Color.rgb(245,249,239));
            txt(c,"نتائجك ضد جنيرال",w*.5f,generalTop+h*.038f,h*.027f,dark,true);
            txt(c,"فوز  "+wins,w*.34f,generalTop+h*.083f,h*.027f,Color.rgb(35,125,72),true);
            txt(c,"خسارة  "+losses,w*.66f,generalTop+h*.083f,h*.027f,Color.rgb(180,65,53),true);
            float rateW=w*.76f, rateL=w*.12f, rateY=generalBottom-h*.018f;
            box(c,rateL,rateY,rateL+rateW,rateY+h*.009f,Color.rgb(220,225,214));
            float winRate=(wins+losses)==0?0f:(float)wins/(wins+losses);
            if(winRate>0) box(c,rateL,rateY,rateL+rateW*winRate,rateY+h*.009f,Color.rgb(54,145,86));

            txt(c,"السجل حسب الخصم",w*.5f,h*.565f,h*.031f,dark,true);
            int row=0;
            for(java.util.Map.Entry<String,int[]> entry:byOpponent.entrySet()) {
                if(row>=3)break;
                float top=h*(.59f+row*.075f);
                box(c,w*.075f,top,w*.925f,top+h*.061f,Color.argb(225,248,250,241));
                txt(c,entry.getKey(),w*.29f,top+h*.039f,h*.024f,dark,true);
                txt(c,"فوز "+entry.getValue()[0],w*.63f,top+h*.039f,h*.023f,Color.rgb(35,125,72),true);
                txt(c,"خسارة "+entry.getValue()[1],w*.82f,top+h*.039f,h*.023f,Color.rgb(180,65,53),true);
                row++;
            }
            if(byOpponent.isEmpty()) {
                txt(c,"ستظهر نتائج خصومك هنا بعد أول مباراة",w*.5f,h*.635f,h*.025f,dark,false);
            }

            txt(c,"آخر المباريات",w*.5f,h*.835f,h*.028f,dark,true);
            if(total==0) {
                txt(c,"لا توجد مباريات مسجلة حتى الآن",w*.5f,h*.885f,h*.025f,dark,false);
            } else {
                for(int i=0;i<Math.min(2,total);i++)try {
                    JSONObject o=data.getJSONObject(i);
                    String resultText=o.optString("winner")+" فاز على "+o.optString("loser");
                    txt(c,resultText,w*.5f,h*(.872f+i*.038f),h*.023f,dark,false);
                } catch(Exception ignored) { }
            }
            btn(c,"عودة",w*.41f,h*.945f,w*.59f,h*.995f,dark);
        }

        private void prepareGeneralCards() {
            for(int i=0;i<CardInventory.CARD_TYPES;i++){generalCards[i]=false;generalCardsUsed[i]=false;}
            int count=new int[]{0,1,2,3}[Math.max(0,Math.min(3,difficulty))];
            int[] pool=new int[CardInventory.CARD_TYPES];
            for(int i=0;i<CardInventory.CARD_TYPES;i++)pool[i]=i;
            for(int i=pool.length-1;i>0;i--){int j=random.nextInt(i+1);int t=pool[i];pool[i]=pool[j];pool[j]=t;}
            for(int i=0;i<count;i++)generalCards[pool[i]]=true;
        }
        private boolean generalHasCard(int type) {
            return type>=0 && type<CardInventory.CARD_TYPES && generalCards[type] && !generalCardsUsed[type];
        }
        private boolean generalUseCard(int type) {
            if(!generalHasCard(type))return false;
            generalCardsUsed[type]=true;
            showCardUseNotice(type,true);
            return true;
        }
        private boolean generalUseBeforeRoll() {
            // الإصلاح أولوية قصوى إذا فقد جنيرال سلاحاً أو عُطّل سيبرانياً.
            if(generalHasCard(CardInventory.REPAIR)) {
                for(int lane=0;lane<3;lane++) if(dead2[lane] || cyberDisabled[2][lane]) {
                    if(generalUseCard(CardInventory.REPAIR)) {
                        boolean wasDestroyed=dead2[lane];
                        dead2[lane]=false;
                        if(wasDestroyed) {
                            pos2[lane]=0;pos1[lane]=0;
                            blueWinEffect[lane]=false;redWinEffect[lane]=false;
                        }
                        cyberDisabled[2][lane]=false;cyberTurns[2][lane]=0;
                        repairEffectUntil[2][lane]=System.currentTimeMillis()+1000L;
                        message="جنيرال فعّل بطاقة إصلاح السلاح — "+names[lane];
                        playGameSound(3);invalidate();return true;
                    }
                }
            }
            // الحرب السيبرانية تستهدف سلاح اللاعب المتقدم أو الأخطر.
            if(generalHasCard(CardInventory.CYBER_WARFARE)) {
                int target=-1,best=-1;
                for(int lane=0;lane<3;lane++) if(!dead1[lane] && !cyberDisabled[1][lane] && !smokeActive[1][lane]) {
                    int score=pos1[lane]+(shieldActive[1][lane]?18:0);
                    if(score>best){best=score;target=lane;}
                }
                if(target>=0 && best>=5 && generalUseCard(CardInventory.CYBER_WARFARE)) {
                    cyberDisabled[1][target]=true;cyberTurns[1][target]=2;
                    message="جنيرال فعّل الحرب السيبرانية ضد "+names[target];
                    invalidate();return true;
                }
            }
            // الدرع يحمي سلاح جنيرال الأكثر تقدماً، إن لم يكن محمياً.
            if(generalHasCard(CardInventory.SHIELD)) {
                int target=-1,best=-1;
                for(int lane=0;lane<3;lane++) if(!dead2[lane] && !shieldActive[2][lane]) {
                    int score=pos2[lane]+(pos2[lane]>=8?12:0);
                    if(score>best){best=score;target=lane;}
                }
                if(target>=0 && (best>=6 || pos1[target]>=8) && generalUseCard(CardInventory.SHIELD)) {
                    shieldActive[2][target]=true;shieldTurns[2][target]=2;
                    message="جنيرال فعّل درع الحماية على "+names[target];
                    invalidate();return true;
                }
            }
            // الضربة الجوية: يستخدمها جنيرال عندما يقترب سلاح اللاعب من التدمير.
            if(generalHasCard(CardInventory.AIRSTRIKE)) {
                boolean useful=false;
                for(int lane=0;lane<3;lane++)if(!dead2[lane] && !dead1[lane] && pos2[lane]>=7)useful=true;
                if(useful && generalUseCard(CardInventory.AIRSTRIKE)) {
                    startAirstrikeAnimation(1);
                    for(int lane=0;lane<3;lane++)if(!dead2[lane] && !dead1[lane]) {
                        pos2[lane]=Math.min(15,pos2[lane]+3);
                        if(pos2[lane]>=15) {
                            dead1[lane]=true;redWinEffect[lane]=true;
                            if(isGeneralOpponent())missionManager.recordPlayerWeaponDestroyed(lane);
                            smokeActive[1][lane]=false;smokeTurns[1][lane]=0;
                            if(mineTargetSide==1&&mineLane==lane){mineTargetSide=0;mineOwnerSide=0;mineLane=-1;}
                            spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);playGameSound(3);
                        }
                    }
                    message="جنيرال نفّذ ضربة جوية على أسلحتك الثلاثة";finishCheck();invalidate();return true;
                }
            }
            // ستار الدخان يحمي أفضل سلاح لدى جنيرال من الاستهداف والبطاقات.
            if(generalHasCard(CardInventory.SMOKE_SCREEN)) {
                int target=-1,best=-1;
                for(int lane=0;lane<3;lane++)if(!dead2[lane]&&!smokeActive[2][lane]) {
                    int score=pos2[lane]+(cyberDisabled[2][lane]?12:0);
                    if(score>best){best=score;target=lane;}
                }
                if(target>=0 && (best>=4 || cyberDisabled[2][target]) && generalUseCard(CardInventory.SMOKE_SCREEN)) {
                    smokeActive[2][target]=true;smokeTurns[2][target]=3;
                    message="جنيرال فعّل ستار الدخان على "+names[target];invalidate();return true;
                }
            }
            // حقل الألغام: يزرع جنيرال لغماً واحداً في أكثر أسلحتك تقدماً.
            if(generalHasCard(CardInventory.MINEFIELD) && mineTargetSide==0) {
                int target=-1,best=-1;
                for(int lane=0;lane<3;lane++)if(!dead1[lane]&&!smokeActive[1][lane]) {
                    if(pos1[lane]>best){best=pos1[lane];target=lane;}
                }
                if(target>=0 && generalUseCard(CardInventory.MINEFIELD)) {
                    mineOwnerSide=2;mineTargetSide=1;mineLane=target;mineTurns=4;
                    message="تم زرع ألغام أرضية في أحد معسكراتك";invalidate();return true;
                }
            }
            // كاشف الألغام لدى جنيرال يبطل اللغم الذي زرعه اللاعب في معسكراته.
            if(generalHasCard(CardInventory.MINE_DETECTOR) && mineTargetSide==1) {
                if(generalUseCard(CardInventory.MINE_DETECTOR)) {
                    mineTargetSide=0;mineOwnerSide=0;mineLane=-1;
                    message="";showTemporaryCardMessage("تمت إزالة اللغم");invalidate();return true;
                }
            }
            // الرادار يراقب هجوم اللاعب القادم بعد انتهاء دور جنيرال.
            if(generalHasCard(CardInventory.RADAR) && !radarPending) {
                if(generalUseCard(CardInventory.RADAR)) {
                    radarPending=true;radarOwnerSide=2;
                    message="جنيرال فعّل الرادار لمراقبة هجومك القادم";invalidate();return true;
                }
            }
            return false;
        }
        private void generalUseAfterRoll() {
            if(!generalHasCard(CardInventory.MILITARY_SUPPLY))return;
            int bonus=0;
            for(int add=3;add>=1;add--) {
                for(int lane=0;lane<3;lane++) {
                    if(!dead2[lane]&&!dead1[lane]&&!cyberDisabled[2][lane]
                            &&!shieldActive[1][lane]&&!smokeActive[1][lane]
                            &&pos2[lane]+die+add<=15) {bonus=add;break;}
                }
                if(bonus>0)break;
            }
            if(bonus>0 && generalUseCard(CardInventory.MILITARY_SUPPLY)) {
                die+=bonus;message="جنيرال استخدم الإمداد العسكري وأضاف "+bonus+" إلى نتيجة النرد";
            }
        }
        private boolean generalDoubleDiceAvailable(int roll) {
            if(!generalHasCard(CardInventory.DOUBLE_DICE))return false;
            for(int lane=0;lane<3;lane++) if(canMove(2,lane) && pos2[lane]+roll*2<=15)return true;
            return false;
        }
        private void resetMatch() {
            battleBackgroundName=random.nextBoolean()?"1793716501127.jpg":"1793905666116.jpg";
            matchEpoch++;
            for(int i=0;i<CardInventory.CARD_TYPES;i++)matchRewardCounts[i]=0;
            for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();
            activeProjectiles.clear();impactEffects.clear();muzzleFlashAt=0;
            stage=0; firstRoll=0; player=1; die=0; winner=0; resultRevealReady=false; choosing=false; recorded=false; explosionAt=0;airstrikeStartedAt=0;
            bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
            clearBattleCardEffects();
            if(isGeneralOpponent())prepareGeneralCards();
            openingRoll1=0; openingRoll2=0; openingToss=false;
            battleDialogueText="";battleDialogueReplyText="";dialogueSequence[0]=dialogueSequence[1]=dialogueSequence[2]=0;
            if(mode==3 && lanSession!=null && lanSession.isConnected()) {
                lanOpeningStarted=true;
                openingToss=false;
                message="طلب إعادة المباراة — بانتظار موافقة اللاعب الآخر";
                lanSession.requestRematch();
            } else if(mode==4 && onlineSession!=null && onlineSession.isConnected()) {
                onlineOpeningStarted=true; openingToss=false;
                message="طلب إعادة المباراة — بانتظار موافقة اللاعب الآخر";
                onlineSession.requestRematch();
            } else {
                message="استعدوا للحرب";
                startBattleIntro();
            }
        }
        private void startOpeningToss() {
            if(mode==0 || winner!=0)return;
            final int epoch=matchEpoch;
            openingToss=true; stage=0; choosing=false; die=0;
            openingRoll1=random.nextInt(6)+1;
            openingRoll2=random.nextInt(6)+1;
            message=openingRoll1==openingRoll2 ? "تعادل — ستُعاد القرعة تلقائياً" : "قرعة البداية";
            invalidate();
            postDelayed(new Runnable(){ @Override public void run(){
                if(epoch!=matchEpoch || !openingToss || mode==0 || winner!=0)return;
                if(openingRoll1==openingRoll2){ startOpeningToss(); return; }
                player=openingRoll1>openingRoll2?1:2;
                openingToss=false; stage=2;
                setBattleDialogue(player,0);
                message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب الأول يبدأ"):(mode==2?computerOpponentName()+" يبدأ":"اللاعب الثاني يبدأ");
                invalidate();
                if(mode==2 && player==2)computerTurn();
            }},2600);
        }
        private void finishCheck() {
            int blueLaneWins=0,redLaneWins=0,resolvedLanes=0;
            for(int i=0;i<3;i++) {
                if(dead2[i] && !dead1[i]) { blueLaneWins++; resolvedLanes++; }
                else if(dead1[i] && !dead2[i]) { redLaneWins++; resolvedLanes++; }
            }
            // لا تُحسم المباراة بعد مواجهتين؛ يجب أن تُحسم المواجهة الثالثة أيضاً.
            if(resolvedLanes<3) {
                winner=0;
                perfectWin=false;
                bonusAttempt=false;
                return;
            }
            if(blueLaneWins>=2) {
                winner=1;
                perfectWin=blueLaneWins==3;
                finishMatch();
                return;
            }
            if(redLaneWins>=2) {
                winner=2;
                perfectWin=false;
                finishMatch();
            }
        }
        private void afterTurn() {
            if(winner!=0 && !bonusAttempt)return;
            die=0; choosing=false;doubleDiceDisplayValue=0;
            if(mode==4 || mode==1 || mode==3)finishCardTurnEffects(player);
            if(mode==2) {
                player=1;
                setBattleDialogue(1,1);
                message="دورك — اضغط النرد";
            } else {
                player=player==1?2:1;
                setBattleDialogue(player,1);
                if(mode==3 && lanSession!=null){lanTurnId++;lanSession.beginTurn(lanTurnId,player);}
                else if(mode==4 && onlineSession!=null){onlineTurnId++;onlineSession.beginTurn(onlineTurnId,player);}
                else message="دور اللاعب "+player+" — اضغط النرد";
            }
            invalidate();
        }
        private void computerTurn() {
            if(mode!=2 || winner!=0 || player!=2)return;
            final int epoch=matchEpoch;
            choosing=false;
            if(isGeneralOpponent())generalUseBeforeRoll();
            if(winner!=0)return;
            die=random.nextInt(6)+1;
            if(isGeneralOpponent() && generalDoubleDiceAvailable(die)) {
                int lane=-1,best=-1;
                for(int i=0;i<3;i++)if(canMove(2,i)&&pos2[i]+die*2<=15&&pos2[i]>best){best=pos2[i];lane=i;}
                if(lane>=0 && generalUseCard(CardInventory.DOUBLE_DICE)) {
                    doubleDiceDisplayValue=die;
                    die*=2;message=computerOpponentName()+" فعّل بطاقة مضاعفة النرد!";
                } else message=computerOpponentName()+" يرمي النرد...";
            } else message=computerOpponentName()+" يرمي النرد...";
            if(isGeneralOpponent())generalUseAfterRoll();
            invalidate();
            postDelayed(new Runnable(){ @Override public void run(){
                if(epoch!=matchEpoch || mode!=2 || winner!=0 || player!=2)return;
                message=computerOpponentName()+" حصل على "+die;
                invalidate();
                postDelayed(new Runnable(){ @Override public void run(){
                    if(epoch!=matchEpoch || mode!=2 || winner!=0 || player!=2)return;
                    int pick=-1;
                    // النرد عشوائي دائماً، والمستوى يغير استراتيجية اختيار السلاح فقط.
                    if(difficulty==0) {
                        int count=0;
                        for(int i=0;i<3;i++) if(canMove(2,i)) count++;
                        if(count>0) {
                            int target=random.nextInt(count);
                            for(int i=0;i<3;i++) if(canMove(2,i) && target--==0) { pick=i; break; }
                        }
                    } else {
                        int bestScore=Integer.MIN_VALUE;
                        for(int i=0;i<3;i++) if(canMove(2,i)) {
                            int score=pos2[i]*10;
                            if(pos2[i]+die>=15) score+=(difficulty==1?15:(difficulty==2?80:120));
                            if(difficulty==3) score+=Math.min(14,die*2);
                            if(score>bestScore){bestScore=score;pick=i;}
                        }
                    }
                    if(pick>=0) {
                        message=computerOpponentName()+" اختار "+names[pick];
                        invalidate();
                        final int selected=pick;
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch==matchEpoch && mode==2 && winner==0 && player==2) animateMove(2,selected,die);
                        }},650);
                    } else {
                        setNoMoveDialogue(2);
                        message="لا توجد حركة متاحة لـ"+computerOpponentName()+" — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        invalidate();
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch==matchEpoch && mode==2 && winner==0 && player==2) {
                                finishCardTurnEffects(2);
                                player=1;die=0;choosing=false;
                                message="دورك — اضغط النرد";
                                invalidate();
                            }
                        }},1200);
                    }
                }},850);
            }},850);
        }
        private void startRadarBlockedAttack(final int attackingSide,final int lane,final int amount) {
            // الرادار لا يغيّر شكل الطلقات: نستخدم نفس مقذوفات المعركة المعتادة،
            // ويطلق السلاح المستهدف رشقة مضادة من فوهته بالاتجاه المعاكس.
            final int epoch=matchEpoch;
            final int shots=Math.max(1,Math.min(12,amount));
            final float laneY=new float[]{h*.29f,h*.49f,h*.69f}[lane];
            final float attackerMuzzle=attackingSide==1?w*.875f:w*.125f;
            final float defenderMuzzle=attackingSide==1?w*.125f:w*.875f;
            final float meetingX=w*.5f;
            final long flight=560L,gap=105L;
            final ArrayList<Projectile> volley=new ArrayList<>();
            choosing=false;playGameSound(lane);
            message="الرادار رصد الهجوم — إطلاق طلقات مضادة!";
            for(int i=0;i<shots;i++) {
                final int shot=i;
                postDelayed(()->{
                    if(epoch!=matchEpoch)return;
                    muzzleFlashAt=System.currentTimeMillis();
                    muzzleFlashX=attackerMuzzle;muzzleFlashY=laneY;
                    float arc=((shot%3)-1)*h*.009f;
                    Projectile incoming=new Projectile(attackerMuzzle,laneY,meetingX,laneY,
                            laneY-h*.115f+arc,flight);
                    Projectile counter=new Projectile(defenderMuzzle,laneY,meetingX,laneY,
                            laneY-h*.115f-arc,flight,Color.rgb(65,220,105));
                    volley.add(incoming);volley.add(counter);
                    activeProjectiles.add(incoming);activeProjectiles.add(counter);
                    invalidate();
                    postDelayed(()->{
                        if(epoch!=matchEpoch)return;
                        activeProjectiles.remove(incoming);activeProjectiles.remove(counter);
                        impactEffects.add(new ImpactEffect(meetingX,laneY));
                        invalidate();
                    },flight);
                },shot*gap);
            }
            long total=(shots-1)*gap+flight+80L;
            postDelayed(()->{
                if(epoch!=matchEpoch)return;
                activeProjectiles.removeAll(volley);
                radarBlockStartedAt=0;radarBlockLane=-1;radarBlockAttackerSide=0;radarBlockAmount=0;
                finishRadarResponseTurn(attackingSide);invalidate();
            },total);
        }
        private void finishRadarResponseTurn(int attackingSide) {
            if(mode==4){die=0;choosing=false;afterTurn();return;}
            finishCardTurnEffects(attackingSide);
            die=0;choosing=false;doubleDiceDisplayValue=0;
            if(mode==2 && attackingSide==2) {
                player=1;message="تم صد الهجوم — دورك الآن";setBattleDialogue(1,1);
            } else if(mode==2) {
                player=2;message="انتهى دور الهجوم";postDelayed(()->{if(mode==2&&winner==0&&player==2)computerTurn();},500);
            }
            invalidate();
        }
        private boolean showRadarInterception(final int side,final int lane,final int amount) {
            if(!radarPending || side==radarOwnerSide)return false;
            final int epoch=matchEpoch;
            if(mode==4){
                final int radarOwner=radarOwnerSide;radarPending=false;radarOwnerSide=0;radarPauseActive=true;
                onlineRadarBlockedLane=-1;onlineRadarBlockedAttackerSide=side;
                if(side==onlineLocalSide){
                    onlineRadarPendingLane=lane;onlineRadarPendingAmount=amount;onlineRadarPendingAttackerSide=side;
                    message="رادار الخصم كشف هجومك — بانتظار قرار الدفاع خلال 10 ثوانٍ";invalidate();
                    return true;
                }
                boolean shieldReady=onlineCards[CardInventory.SHIELD]
                        && !onlineCardsUsedBySide[onlineLocalSide][CardInventory.SHIELD]
                        && !destroyed(onlineLocalSide)[lane] && !shieldActive[onlineLocalSide][lane];
                final boolean[] resolved={false};
                final AlertDialog dialog=new AlertDialog.Builder(MainActivity.this)
                    .setTitle("الرادار كشف الهجوم")
                    .setMessage("الخصم يستهدف "+names[lane]+". لديك 10 ثوانٍ لاستخدام بطاقة الدرع.")
                    .setPositiveButton("استخدام الدرع",(d,which)->{
                        if(resolved[0])return;resolved[0]=true;
                        if(shieldReady && applyOnlineCardEffect(onlineLocalSide,CardInventory.SHIELD,lane,-1,true)){
                            message="استخدمت الدرع بعد كشف الرادار وتم صد الهجوم";invalidate();
                        } else {message="لا تتوفر بطاقة درع — استمر الهجوم";invalidate();animateMove(side,lane,amount);}
                    })
                    .setNegativeButton("متابعة الهجوم",(d,which)->{
                        if(resolved[0])return;resolved[0]=true;radarPauseActive=false;
                        if(onlineSession!=null)onlineSession.sendRadarDecision(lane,true);
                        message="تم تجاوز إنذار الرادار";animateMove(side,lane,amount);
                    }).setCancelable(false).create();
                dialog.show();
                if(!shieldReady)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                postDelayed(()->{if(!resolved[0]&&epoch==matchEpoch&&radarPauseActive){
                    resolved[0]=true;radarPauseActive=false;if(dialog.isShowing())dialog.dismiss();
                    if(onlineSession!=null)onlineSession.sendRadarDecision(lane,true);
                    message="انتهت مهلة الرادار — استكمل الخصم الهجوم";animateMove(side,lane,amount);
                }},10000L);
                invalidate();return true;
            }
            final int radarOwner=radarOwnerSide;
            radarPending=false;radarOwnerSide=0;radarPauseActive=true;
            if(radarOwner==2 && side==1) {
                radarPauseActive=false;
                message="جنيرال كشف هجومك بالرادار وصدّه تلقائياً";
                startRadarBlockedAttack(side,lane,amount);return true;
            }
            // حماية الرادار المجانية تصد هجمة واحدة ولا تستهلك بطاقة الدرع.
            final boolean[] resolved={false};
            final int[] secondsLeft={10};
            int radarPad=(int)(18*getResources().getDisplayMetrics().density);
            android.widget.LinearLayout radarPanel=new android.widget.LinearLayout(MainActivity.this);
            radarPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
            radarPanel.setPadding(radarPad,radarPad/2,radarPad,radarPad/2);
            android.widget.TextView radarText=new android.widget.TextView(MainActivity.this);
            radarText.setText("جنيرال يستهدف "+names[lane]+" — اضغط لصد الهجمة مجاناً قبل انتهاء العدّ التنازلي.");
            radarText.setTextColor(Color.rgb(150,105,0));
            radarText.setTextSize(19);
            radarText.setGravity(android.view.Gravity.CENTER);
            radarText.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
            radarPanel.addView(radarText);
            android.widget.ImageView weaponImage=new android.widget.ImageView(MainActivity.this);
            String weaponArtName=new String[]{"tank_01.png","fighter_01.png","cannon_01.png"}[lane];
            Bitmap weaponBitmap=art.get(weaponArtName);
            if(weaponBitmap!=null) {
                weaponImage.setImageBitmap(weaponBitmap);
                weaponImage.setAdjustViewBounds(true);
                weaponImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                android.widget.LinearLayout.LayoutParams imageParams=new android.widget.LinearLayout.LayoutParams(
                    (int)(150*getResources().getDisplayMetrics().density),
                    (int)(105*getResources().getDisplayMetrics().density));
                imageParams.gravity=android.view.Gravity.CENTER;
                imageParams.topMargin=radarPad/2;
                radarPanel.addView(weaponImage,imageParams);
            }
            android.widget.TextView weaponName=new android.widget.TextView(MainActivity.this);
            weaponName.setText(names[lane]);
            weaponName.setTextColor(Color.rgb(212,160,23));
            weaponName.setTextSize(19);
            weaponName.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
            weaponName.setGravity(android.view.Gravity.CENTER);
            radarPanel.addView(weaponName);
            android.widget.TextView radarTitle=new android.widget.TextView(MainActivity.this);
            radarTitle.setText("الرادار كشف الهجوم");
            radarTitle.setTextColor(Color.rgb(212,160,23));
            radarTitle.setTextSize(22);
            radarTitle.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
            radarTitle.setGravity(android.view.Gravity.CENTER);
            radarTitle.setPadding(radarPad,radarPad,radarPad,radarPad/2);
            final AlertDialog dialog=new AlertDialog.Builder(MainActivity.this)
                .setCustomTitle(radarTitle).setView(radarPanel)
                .setPositiveButton("صد الهجوم 10",(d,which)->{
                    if(resolved[0])return;
                    resolved[0]=true;radarPauseActive=false;
                    message="صدَدت هجمة جنيرال بحماية مجانية من الرادار — لم تُستهلك بطاقة الدرع";
                    startRadarBlockedAttack(side,lane,amount);
                })
                .setNegativeButton("متابعة الهجوم",(d,which)->{
                    if(resolved[0])return;
                    resolved[0]=true;radarPauseActive=false;message="تم تجاوز إنذار الرادار";
                    animateMove(side,lane,amount);
                }).setCancelable(false).create();
            dialog.show();
            final android.widget.Button blockButton=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final Runnable countdown=new Runnable(){@Override public void run(){
                if(resolved[0] || epoch!=matchEpoch || !radarPauseActive)return;
                secondsLeft[0]--;
                if(secondsLeft[0]<=0) {
                    resolved[0]=true;radarPauseActive=false;
                    if(dialog.isShowing())dialog.dismiss();
                    message="انتهت مهلة الرادار — استكمل جنيرال هجومه";
                    animateMove(side,lane,amount);
                    return;
                }
                blockButton.setText("صد الهجوم "+secondsLeft[0]);
                postDelayed(this,1000L);
            }};
            postDelayed(countdown,1000L);
            invalidate();return true;
        }
        private void animateMove(final int side, final int lane, final int amount) {
            if(mineTargetSide==side && mineLane==lane) {
                positions(side)[lane]=Math.max(0,positions(side)[lane]-3);
                mineTargetSide=0;mineOwnerSide=0;mineLane=-1;
                message="انفجر اللغم في معسكر "+names[lane]+" وخسر السلاح حتى 3 طلقات";
                choosing=false;die=0;
                if(mode==2) {
                    finishCardTurnEffects(side);
                    if(side==1){player=2;postDelayed(()->{if(mode==2&&winner==0&&player==2)computerTurn();},500);}
                    else {player=1;message+=" — دورك الآن";}
                } else afterTurn();
                invalidate();return;
            }
            if(showRadarInterception(side,lane,amount))return;
            final int epoch=matchEpoch;
            final int defendingSide=side==1?2:1;
            if(shieldActive[defendingSide][lane]) {
                choosing=false;
                playGameSound(lane);
                final float laneY=new float[]{h*.29f,h*.49f,h*.69f}[lane];
                final float meetingX=w*.5f;
                final long interceptFlight=430L;
                Projectile incoming=new Projectile(side==1?w*.875f:w*.125f,laneY,meetingX,laneY-h*.012f,laneY-h*.045f,interceptFlight);
                Projectile counter=new Projectile(side==1?w*.125f:w*.875f,laneY,meetingX,laneY+h*.012f,laneY+h*.045f,interceptFlight);
                activeProjectiles.add(incoming);activeProjectiles.add(counter);
                message="إطلاق متبادل! الخصم يصدّ الطلقات قبل وصولها إلى السلاح";
                invalidate();
                postDelayed(()->{
                    if(epoch!=matchEpoch)return;
                    activeProjectiles.remove(incoming);activeProjectiles.remove(counter);
                    impactEffects.add(new ImpactEffect(meetingX,laneY));
                    if(mode==2) {
                        finishCardTurnEffects(side);
                        die=0;choosing=false;
                        if(side==1) {
                            player=2;message="جنيرال يرد بعد صدّ الهجوم";
                            postDelayed(()->{if(epoch==matchEpoch&&mode==2&&winner==0&&player==2)computerTurn();},450L);
                        } else {player=1;message="دورك — اضغط النرد";}
                    } else afterTurn();
                    invalidate();
                },interceptFlight);
                return;
            }
            choosing=false;
            playGameSound(lane);
            final int start=positions(side)[lane];
            final int shots=Math.max(1,Math.min(12,amount));
            final int target=Math.min(15,start+shots);
            final float laneY=new float[]{h*.29f,h*.49f,h*.69f}[lane];
            final float startX=side==1?w*.875f:w*.125f;
            final float endX=side==1?w*.125f:w*.875f;
            final long launchGap=105L;
            final long flightTime=560L;
            message=(side==1?"الأزرق":"الأحمر")+" يطلق رشقة...";
            for(int shot=0;shot<shots;shot++) {
                final int shotIndex=shot;
                postDelayed(()->{
                    if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt))return;
                    muzzleFlashAt=System.currentTimeMillis();
                    muzzleFlashX=side==1?w*.874f:w*.126f;
                    muzzleFlashY=laneY;
                    float arcOffset=((shotIndex%3)-1)*h*.009f;
                    Projectile projectile=new Projectile(startX,laneY,endX,laneY,
                            laneY-h*.115f+arcOffset,flightTime);
                    activeProjectiles.add(projectile);
                    invalidate();
                    postDelayed(()->{
                        if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt)) {
                            activeProjectiles.remove(projectile);
                            return;
                        }
                        activeProjectiles.remove(projectile);
                        if(positions(side)[lane]<target)positions(side)[lane]++;
                        impactEffects.add(new ImpactEffect(endX,laneY));
                        invalidate();
                    },flightTime);
                },shotIndex*launchGap);
            }
            long totalFlight=(shots-1)*launchGap+flightTime+90L;
            postDelayed(()->{
                if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt))return;
                if(positions(side)[lane]<target)positions(side)[lane]=target;
                if(target>=15){
                    if(side==1){
                        dead2[lane]=true;smokeActive[2][lane]=false;smokeTurns[2][lane]=0;
                        if(mineTargetSide==2&&mineLane==lane){mineTargetSide=0;mineOwnerSide=0;mineLane=-1;}
                        if(isGeneralOpponent())missionManager.recordGeneralWeaponDestroyed(lane);
                        blueWinEffect[lane]=true;setBattleDialogue(1,2);
                        spawnExplosion(w*.075f,laneY);playGameSound(3);
                    } else {
                        dead1[lane]=true;smokeActive[1][lane]=false;smokeTurns[1][lane]=0;
                        if(mineTargetSide==1&&mineLane==lane){mineTargetSide=0;mineOwnerSide=0;mineLane=-1;}
                        if(isGeneralOpponent())missionManager.recordPlayerWeaponDestroyed(lane);
                        redWinEffect[lane]=true;setBattleDialogue(2,2);
                        spawnExplosion(w*.925f,laneY);playGameSound(3);
                    }
                }
                finishCheck();
                if(winner==0){
                    if(mode==2 && side==1){
                        finishCardTurnEffects(1);
                        player=2;setBattleDialogue(2,1);die=0;message="دور "+computerOpponentName();
                        postDelayed(()->{if(epoch==matchEpoch && mode==2 && winner==0 && player==2){computerTurn();invalidate();}},650);
                    } else if(mode==2 && side==2){
                        finishCardTurnEffects(2);
                        player=1;die=0;choosing=false;message="دورك — اضغط النرد";
                    } else afterTurn();
                }
                invalidate();
            },totalFlight);
        }
        private void spawnExplosion(float x,float y) {
            vibrateForExplosion();
            if(screenShakeEnabled) {
                screenShakeStartedAt=System.currentTimeMillis();
                postInvalidateDelayed(16);
            }
            explosionX=x; explosionY=y; explosionAt=System.currentTimeMillis();
            invalidate();
        }
        private void applyScreenShake(Canvas c) {
            if(!screenShakeEnabled || screenShakeStartedAt<=0)return;
            long elapsed=System.currentTimeMillis()-screenShakeStartedAt;
            final long duration=430L;
            if(elapsed>=duration) { screenShakeStartedAt=0; return; }
            float fade=1f-(float)elapsed/duration;
            float strength=h*.016f*fade;
            float phase=elapsed/24f;
            float dx=(float)Math.sin(phase*2.7f)*strength;
            float dy=(float)Math.cos(phase*3.2f)*strength*.62f;
            c.translate(dx,dy);
            postInvalidateDelayed(16);
        }
        private void drawMuzzleFlash(Canvas c) {
            if(muzzleFlashAt==0)return;
            float age=(System.currentTimeMillis()-muzzleFlashAt)/260f;
            if(age>=1f){muzzleFlashAt=0;return;}
            float r=h*(.025f+.012f*(1f-age));
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb((int)(190*(1f-age)),255,120,15));c.drawCircle(muzzleFlashX,muzzleFlashY,r*1.5f,p);
            Path flame=new Path();boolean right=muzzleFlashX<w*.5f;
            flame.moveTo(muzzleFlashX-r*.45f,muzzleFlashY-r*.55f);
            flame.lineTo(muzzleFlashX+(right?r*2.2f:-r*2.2f),muzzleFlashY);
            flame.lineTo(muzzleFlashX-r*.45f,muzzleFlashY+r*.55f);flame.close();
            p.setColor(Color.argb((int)(255*(1f-age)),255,205,48));c.drawPath(flame,p);
            p.setColor(Color.WHITE);c.drawCircle(muzzleFlashX,muzzleFlashY,r*.32f,p);
            postInvalidateDelayed(30);
        }
        private void startAirstrikeAnimation(final int targetSide) {
            for(int i=0;i<3;i++)airstrikeShakeTriggered[i]=false;
            airstrikeTargetSide=targetSide;
            final int attackerSide=targetSide==2?1:2;
            final int epoch=matchEpoch;
            // The aircraft crosses the battlefield after the card notice fades.
            airstrikeStartedAt=System.currentTimeMillis()+2000L;
            final float[] rows={h*.29f,h*.49f,h*.69f};
            for(int lane=0;lane<3;lane++) {
                final int targetLane=lane;
                final long launchDelay=3750L+lane*650L;
                postDelayed(()->{
                    if(epoch!=matchEpoch || winner!=0 || destroyed(targetSide)[targetLane])return;
                    float progress=Math.max(0f,Math.min(1f,(1750f+targetLane*650f)/5000f));
                    float startX=targetSide==2?w*.94f:w*.06f;
                    float endX=targetSide==2?w*.06f:w*.94f;
                    float planeX=startX+(endX-startX)*progress;
                    float planeY=h*.225f-(float)Math.sin(progress*Math.PI)*h*.012f;
                    float targetX=targetSide==2?w*.075f:w*.925f;
                    float targetY=rows[targetLane];
                    muzzleFlashAt=System.currentTimeMillis();muzzleFlashX=planeX;muzzleFlashY=planeY;
                    playGameSound(1);
                    Projectile bomb=new Projectile(planeX,planeY,targetX,targetY,
                            (planeY+targetY)*.5f-h*.055f,850L);
                    activeProjectiles.add(bomb);invalidate();
                    postDelayed(()->{
                        if(epoch!=matchEpoch)return;
                        activeProjectiles.remove(bomb);
                        if(winner!=0 || destroyed(targetSide)[targetLane]){invalidate();return;}
                        positions(attackerSide)[targetLane]=Math.min(15,positions(attackerSide)[targetLane]+3);
                        impactEffects.add(new ImpactEffect(targetX,targetY));
                        spawnExplosion(targetX,targetY);playGameSound(3);
                        if(positions(attackerSide)[targetLane]>=15) {
                            destroyed(targetSide)[targetLane]=true;
                            if(attackerSide==1)blueWinEffect[targetLane]=true;else redWinEffect[targetLane]=true;
                            if(isGeneralOpponent() && targetSide==2)missionManager.recordGeneralWeaponDestroyed(targetLane);
                            smokeActive[targetSide][targetLane]=false;smokeTurns[targetSide][targetLane]=0;
                            if(mineTargetSide==targetSide&&mineLane==targetLane){mineTargetSide=0;mineOwnerSide=0;mineLane=-1;mineTurns=0;}
                        }
                        message="وصلت ذخائر الضربة الجوية إلى "+names[targetLane];
                        finishCheck();invalidate();
                    },850L);
                },launchDelay);
            }
            invalidate();postInvalidateDelayed(16);
        }
        private void drawAirstrikeBlast(Canvas c,float cx,float cy,float progress) {
            float radius=h*(.012f+.035f*progress);
            int alpha=(int)(235*(1f-progress));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha,255,105,18));c.drawCircle(cx,cy,radius,p);
            p.setColor(Color.argb(alpha,255,205,55));c.drawCircle(cx,cy,radius*.58f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,h*.0025f));
            p.setColor(Color.argb(alpha,255,235,150));c.drawCircle(cx,cy,radius*1.18f,p);
            p.setStyle(Paint.Style.FILL);
            for(int i=0;i<9;i++) {
                double angle=Math.PI*2*i/9.0;
                float distance=radius*(1.15f+(i%3)*.18f);
                float sx=cx+(float)Math.cos(angle)*distance,sy=cy+(float)Math.sin(angle)*distance;
                p.setColor(Color.argb(alpha,255,i%2==0?95:180,25));
                c.drawCircle(sx,sy,Math.max(1,h*.0035f*(1f-progress)),p);
            }
        }
        private void drawAirstrikeEffect(Canvas c) {
            if(airstrikeStartedAt<=0)return;
            long elapsed=System.currentTimeMillis()-airstrikeStartedAt;
            if(elapsed>=5000L) { airstrikeStartedAt=0;return; }
            float progress=Math.max(0f,Math.min(1f,elapsed/5000f));
            float startX=airstrikeTargetSide==2?w*.94f:w*.06f;
            float endX=airstrikeTargetSide==2?w*.06f:w*.94f;
            float planeX=startX+(endX-startX)*progress;
            float planeY=h*.225f-(float)Math.sin(progress*Math.PI)*h*.012f;
            float planeW=w*.145f,planeH=h*.112f;
            RectF planeRect=new RectF(planeX-planeW*.5f,planeY-planeH*.5f,planeX+planeW*.5f,planeY+planeH*.5f);
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);
            if(airstrikePlaneImage!=null) {
                if(airstrikeTargetSide==1) {
                    c.save();c.scale(-1f,1f,planeX,planeY);c.drawBitmap(airstrikePlaneImage,null,planeRect,p);c.restore();
                } else c.drawBitmap(airstrikePlaneImage,null,planeRect,p);
            } else txt(c,"✈",planeX,planeY+h*.025f,h*.085f,Color.WHITE,true);
            // Actual bomb projectiles and their impacts are drawn by the shared projectile system.
            postInvalidateDelayed(30);
        }
        private void drawExplosion(Canvas c) {
            if(explosionAt==0)return;
            float age=(System.currentTimeMillis()-explosionAt)/700f;
            if(age>=1f){explosionAt=0;return;}
            float radius=h*(.025f+age*.105f);
            int alpha=(int)(255*(1f-age));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(h*(.008f*(1f-age)+.001f));
            p.setColor(Color.argb(alpha,255,190,45));
            c.drawCircle(explosionX,explosionY,radius,p);
            p.setStrokeWidth(h*.003f); p.setColor(Color.argb(alpha,255,245,190));
            c.drawCircle(explosionX,explosionY,radius*.58f,p);
            p.setStyle(Paint.Style.FILL);
            for(int i=0;i<18;i++) {
                double angle=(Math.PI*2*i/18.0)+(i%3)*.09;
                float distance=radius*(.75f+(i%4)*.13f);
                float sx=explosionX+(float)Math.cos(angle)*distance;
                float sy=explosionY+(float)Math.sin(angle)*distance;
                float size=h*(.004f+(i%3)*.0015f)*(1f-age);
                p.setColor(Color.argb(alpha,255,i%3==0?90:190,35));
                c.drawCircle(sx,sy,Math.max(1,size),p);
                p.setStrokeWidth(Math.max(1,h*.0025f));
                c.drawLine(explosionX,explosionY,sx,sy,p);
            }
            p.setStyle(Paint.Style.FILL);
            postInvalidateDelayed(35);
        }
        private int shade(int color,float factor) {
            return Color.rgb(Math.min(255,(int)(Color.red(color)*factor)),Math.min(255,(int)(Color.green(color)*factor)),Math.min(255,(int)(Color.blue(color)*factor)));
        }
        private void drawBullet(Canvas c,float cx,float cy,float bw,float bh,int color,boolean filled,boolean pointsRight) {
            String file=filled?(pointsRight?"ammo_round_right_gold.png":"ammo_round_left_gold.png"):(pointsRight?"ammo_round_right_white.png":"ammo_round_left_white.png");
            drawArt(c,file,cx-bw*.52f,cy-bh*.55f,cx+bw*.52f,cy+bh*.55f,false);
        }
        private void drawAmmoCounter(Canvas c,float cx,float cy,int remaining) {
            remaining=Math.max(0,Math.min(15,remaining));
            float boxW=w*.028f,boxH=h*.128f;
            RectF back=new RectF(cx-boxW*.5f,cy-boxH*.5f,cx+boxW*.5f,cy+boxH*.5f);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(205,18,23,27));
            c.drawRoundRect(back,h*.009f,h*.009f,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,h*.0018f));
            p.setColor(Color.argb(190,151,112,54));c.drawRoundRect(back,h*.009f,h*.009f,p);
            p.setStyle(Paint.Style.FILL);
            float segmentW=boxW*.56f,segmentH=h*.0052f,gap=h*.0022f;
            float total=15*segmentH+14*gap;
            for(int i=0;i<15;i++) {
                float top=cy+total*.5f-(i+1)*segmentH-i*gap;
                RectF segment=new RectF(cx-segmentW*.5f,top,cx+segmentW*.5f,top+segmentH);
                p.setColor(i<remaining?Color.rgb(226,174,67):Color.rgb(75,79,82));
                c.drawRoundRect(segment,segmentH*.35f,segmentH*.35f,p);
            }
            txt(c,String.valueOf(remaining),cx,cy+boxH*.5f+h*.020f,h*.018f,
                    remaining<=3?Color.rgb(245,112,85):Color.rgb(238,194,103),true);
        }
        private void drawProjectiles(Canvas c) {
            long now=System.currentTimeMillis();
            for(Projectile projectile:activeProjectiles) {
                float t=Math.max(0f,Math.min(1f,(now-projectile.startedAt)/(float)projectile.duration));
                float one=1f-t;
                float controlX=(projectile.startX+projectile.endX)*.5f;
                float x=one*one*projectile.startX+2f*one*t*controlX+t*t*projectile.endX;
                float y=one*one*projectile.startY+2f*one*t*projectile.controlY+t*t*projectile.endY;
                float dx=2f*one*(controlX-projectile.startX)+2f*t*(projectile.endX-controlX);
                float dy=2f*one*(projectile.controlY-projectile.startY)+2f*t*(projectile.endY-projectile.controlY);
                float angle=(float)Math.toDegrees(Math.atan2(dy,dx));
                float radius=h*.009f;
                c.save();c.rotate(angle,x,y);
                int base=projectile.color;
                p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(65,Color.red(base),Color.green(base),Color.blue(base)));
                c.drawCircle(x,y,radius*2.1f,p);
                p.setColor(base);
                c.drawRoundRect(new RectF(x-radius*1.65f,y-radius*.48f,x+radius*1.65f,y+radius*.48f),
                        radius*.42f,radius*.42f,p);
                p.setColor(Color.rgb(255,238,185));
                c.drawRoundRect(new RectF(x-radius*.65f,y-radius*.27f,x+radius*.85f,y+radius*.27f),
                        radius*.24f,radius*.24f,p);
                p.setColor(Color.argb(190,Color.red(base),Color.green(base),Color.blue(base)));
                c.drawCircle(x-radius*1.45f,y,radius*.34f,p);
                c.restore();
            }
            if(!activeProjectiles.isEmpty())postInvalidateDelayed(16);
        }
        private void drawImpactEffects(Canvas c) {
            long now=System.currentTimeMillis();
            for(int index=impactEffects.size()-1;index>=0;index--) {
                ImpactEffect effect=impactEffects.get(index);
                float age=(now-effect.startedAt)/330f;
                if(age>=1f){impactEffects.remove(index);continue;}
                float radius=h*(.006f+age*.018f);
                int alpha=(int)(210*(1f-age));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,h*.002f));
                p.setColor(Color.argb(alpha,255,190,85));
                c.drawCircle(effect.x,effect.y,radius,p);
                p.setStyle(Paint.Style.FILL);
                for(int spark=0;spark<6;spark++) {
                    double angle=(Math.PI*2*spark/6.0)+.18;
                    float distance=radius*(.7f+(spark%2)*.35f);
                    float sx=effect.x+(float)Math.cos(angle)*distance;
                    float sy=effect.y+(float)Math.sin(angle)*distance;
                    p.setColor(Color.argb(alpha,255,spark%2==0?225:155,92));
                    c.drawCircle(sx,sy,Math.max(1,h*.0024f*(1f-age)),p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            if(!impactEffects.isEmpty())postInvalidateDelayed(24);
        }
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color,boolean faceRight) {
            String file=lane==0?"tank_01.png":lane==1?"fighter_01.png":"cannon_01.png";float ww=w*.105f,hh=h*.105f;
            drawArt(c,file,cx-ww*.5f,cy-hh*.5f,cx+ww*.5f,cy+hh*.5f,!faceRight);
        }
        private void drawCardImage(Canvas c,Bitmap bitmap,float cx,float cy) {
            if(bitmap==null)return;
            float fw=w*.105f,fh=h*.105f;
            RectF dst=new RectF(cx-fw*.5f,cy-fh*.5f,cx+fw*.5f,cy+fh*.5f);
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(255);p.setFilterBitmap(true);
            c.drawBitmap(bitmap,null,dst,p);
        }
        private void drawScaledWeaponEffect(Canvas c,Bitmap bitmap,float cx,float cy,float scale,int alpha) {
            if(bitmap==null)return;
            float fw=w*.105f*scale,fh=h*.105f*scale;
            int oldAlpha=p.getAlpha();
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(alpha);p.setFilterBitmap(true);
            c.drawBitmap(bitmap,null,new RectF(cx-fw*.5f,cy-fh*.5f,cx+fw*.5f,cy+fh*.5f),p);
            p.setAlpha(oldAlpha);
        }
        // Effects are drawn behind the vehicle so the weapon stays fully visible.
        private void drawWeaponCardEffect(Canvas c,int side,int lane,float cx,float cy) {
            long now=System.currentTimeMillis();
            boolean repair=repairEffectUntil[side][lane]>now;
            boolean active=smokeActive[side][lane]||cyberDisabled[side][lane]||shieldActive[side][lane];
            if(!repair&&!active)return;
            float pulse=.5f+.5f*(float)Math.sin(now/115.0);
            int alpha=(int)(55+200*pulse);
            if(smokeActive[side][lane]) {
                if(smokeEffectImage!=null)drawScaledWeaponEffect(c,smokeEffectImage,cx,cy,1.58f,alpha);
                else txt(c,"🌫",cx,cy-h*.065f,h*.050f,Color.argb(alpha,255,255,255),true);
            } else if(repair)drawScaledWeaponEffect(c,repairCardImage,cx,cy,1.32f,alpha);
            else if(cyberDisabled[side][lane])drawScaledWeaponEffect(c,cyberCardImage,cx,cy,1.32f,alpha);
            else if(shieldActive[side][lane])drawScaledWeaponEffect(c,shieldCardImage,cx,cy,1.32f,alpha);
            postInvalidateDelayed(45);
        }
        // Mines remain in front of the weapon, separate from the background effects.
        private void drawWeaponMineEffect(Canvas c,int side,int lane,float cx,float cy) {
            if(mineTargetSide!=side || mineLane!=lane)return;
            float mineX=cx+(side==1?-1f:1f)*w*.034f;
            float mineY=cy+h*.022f;
            float mineW=w*.038f,mineH=h*.050f;
            int pulseAlpha=(int)(65+190*(.5+.5*Math.sin(System.currentTimeMillis()/115.0)));
            if(landMineImage!=null) {
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setAlpha(pulseAlpha);
                c.drawBitmap(landMineImage,null,new RectF(mineX-mineW*.5f,mineY-mineH*.5f,mineX+mineW*.5f,mineY+mineH*.5f),p);
            } else txt(c,"💣",mineX,mineY+h*.012f,h*.045f,Color.rgb(255,215,80),true);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight();
            c.drawColor(Color.rgb(23,34,29));
            if(titleScreen) { drawIntroScreen(c); return; }
            txt(c,"GENERAL  •  جنيرال",w*.5f,h*.075f,h*.06f,mode==0?Color.rgb(242,196,94):Color.rgb(238,224,190),true);
            if(battleIntro){drawBattleIntro(c);return;}
            if(setup) { drawSetup(c); return; }
            if(mode==0) {
                drawMenuBackdrop(c);
                if(missionsScreen){drawMissions(c);drawMenuNavigation(c);return;}
                if(inventoryScreen){drawInventory(c);drawMenuNavigation(c);return;}
                if(historyScreen){drawHistory(c);drawMenuNavigation(c);return;}
                if(progressScreen){drawProgress(c);drawMenuNavigation(c);return;}
                if(helpScreen){drawHelp(c);drawMenuNavigation(c);return;}
                if(warMenuScreen) {
                    txt(c,"اختر نمط الحرب",w*.5f,h*.205f,h*.060f,Color.rgb(242,196,94),true);
                    iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");
                    iconButton(c,w*.145f,h*.065f,w*.245f,h*.155f,dark,"⚙");
                    iconButton(c,w*.875f,h*.065f,w*.965f,h*.155f,Color.rgb(91,24,43),"⏻");
                    // ثلاثة خيارات جانبية، ومواجهة جنيرال هي الخيار الأبرز على اليمين.
                    wideMenuButton(c,"⚔","لاعبان على نفس الجهاز",w*.065f,h*.295f,w*.535f,h*.455f);
                    wideMenuButton(c,"◎","أون لاين",w*.065f,h*.505f,w*.535f,h*.665f);
                    wideMenuButton(c,"🌍","الحملة",w*.065f,h*.715f,w*.535f,h*.875f);
                    wideMenuButton(c,"★","حرب ضد جنيرال",w*.605f,h*.295f,w*.945f,h*.875f);
                } else {
                    txt(c,"المقر الرئيسي",w*.5f,h*.205f,h*.060f,Color.rgb(242,196,94),true);
                    iconButton(c,w*.035f,h*.065f,w*.125f,h*.155f,dark,"⚙");
                    iconButton(c,w*.875f,h*.065f,w*.965f,h*.155f,Color.rgb(91,24,43),"⏻");
                    // الخدمات والملفات يساراً، وزر الحرب الكبير يميناً.
                    p.setShader(null);p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(205,12,22,20));
                    c.drawRoundRect(new RectF(w*.035f,h*.285f,w*.585f,h*.865f),h*.035f,h*.035f,p);
                    c.drawRoundRect(new RectF(w*.615f,h*.285f,w*.965f,h*.865f),h*.035f,h*.035f,p);
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3,h*.006f));p.setColor(Color.rgb(105,27,48));
                    c.drawRoundRect(new RectF(w*.035f,h*.285f,w*.585f,h*.865f),h*.035f,h*.035f,p);
                    c.drawRoundRect(new RectF(w*.615f,h*.285f,w*.965f,h*.865f),h*.035f,h*.035f,p);
                    p.setStyle(Paint.Style.FILL);
                    txt(c,"الخدمات والملفات",w*.31f,h*.355f,h*.030f,Color.rgb(242,196,94),true);
                    miniMenuButton(c,"⚑","المهمات",w*.155f,h*.425f,Color.rgb(55,48,37));
                    miniMenuButton(c,"▣","البطاقات",w*.335f,h*.425f,Color.rgb(55,48,37));
                    miniMenuButton(c,"▤","السجل",w*.515f,h*.425f,Color.rgb(55,48,37));
                    miniMenuButton(c,"♜","التقدم",w*.245f,h*.665f,Color.rgb(55,48,37));
                    miniMenuButton(c,"?","الشرح",w*.425f,h*.665f,Color.rgb(55,48,37));
                    
                    txt(c,"ساحة المعركة",w*.79f,h*.355f,h*.030f,Color.rgb(242,196,94),true);
                    wideMenuButton(c,"⚔","الحرب",w*.655f,h*.405f,w*.925f,h*.785f);
                    p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);
                    p.setTextSize(h*.020f);p.setColor(Color.WHITE);
                    c.drawText("© 2026 أحمد جرادات",w*.5f,h*.945f,p);
                    p.setTextSize(h*.018f);
                    c.drawText("ahmad3aj@gmail.com",w*.5f,h*.975f,p);
                    p.setFakeBoldText(false);
                }
                drawRewardPopup(c);
                return;
            }
            if(winner!=0 && !bonusAttempt && resultRevealReady) {
                box(c,w*.12f,h*.19f,w*.88f,h*.88f,Color.rgb(245,249,239));
                String winnerName=winner==1?playerNames[0]:(mode==2?computerOpponentName():playerNames[1]);
                int blueWins=0,redWins=0;
                for(int i=0;i<3;i++) {
                    if(dead2[i] && !dead1[i])blueWins++;
                    else if(dead1[i] && !dead2[i])redWins++;
                }
                float portraitY=h*.35f, portraitR=h*.100f;
                if(mode==2) {
                    boolean generalWon=winner==2;
                    float generalX=w*.34f,playerX=w*.66f;
                    drawWinnerGif(c,generalWon?generalX:playerX,portraitY-h*.045f);
                    if(isGeneralOpponent())drawGeneralAvatar(c,generalX,portraitY,portraitR,generalWon,!generalWon);
                    else drawAvatar(c,1,generalX,portraitY,portraitR,generalWon,!generalWon);
                    drawAvatar(c,avatars[0],playerX,portraitY,portraitR,!generalWon,generalWon);
                    txt(c,computerOpponentName(),generalX,h*.505f,h*.034f,dark,true);
                    txt(c,playerNames[0],playerX,h*.505f,h*.034f,blue,true);
                } else {
                    String loserName=winner==1?playerNames[1]:playerNames[0];
                    int winnerAvatar=winner==1?avatars[0]:avatars[1];
                    int loserAvatar=winner==1?avatars[1]:avatars[0];
                    drawWinnerGif(c,w*.34f,portraitY-h*.045f);
                    drawAvatar(c,winnerAvatar,w*.34f,portraitY,portraitR,true);
                    drawAvatar(c,loserAvatar,w*.66f,portraitY,portraitR,false,true);
                    txt(c,winnerName,w*.34f,h*.505f,h*.034f,blue,true);
                    txt(c,loserName,w*.66f,h*.505f,h*.034f,dark,true);
                }
                txt(c,"الأسلحة التي دمّرها كل لاعب",w*.5f,h*.555f,h*.027f,dark,true);
                if(mode==2) {
                    txt(c,computerOpponentName()+": "+redWins+" أسلحة",w*.34f,h*.615f,h*.032f,red,true);
                    txt(c,playerNames[0]+": "+blueWins+" أسلحة",w*.66f,h*.615f,h*.032f,blue,true);
                } else {
                    txt(c,playerNames[1]+": "+redWins+" أسلحة",w*.34f,h*.615f,h*.032f,red,true);
                    txt(c,playerNames[0]+": "+blueWins+" أسلحة",w*.66f,h*.615f,h*.032f,blue,true);
                }
                txt(c,"الفائز: "+winnerName,w*.5f,h*.685f,h*.040f,winner==1?blue:red,true);
                if(isGeneralOpponent() && winner==1) {
                    txt(c,"النقاط المكتسبة: "+matchPoints,w*.5f,h*.715f,h*.022f,Color.rgb(35,125,72),true);
                    int earnedTypes=0;for(int n:matchRewardCounts)if(n>0)earnedTypes++;
                    if(earnedTypes>0) {
                        txt(c,"البطاقات التي ربحتها",w*.5f,h*.755f,h*.022f,dark,true);
                        float tileW=w*.070f,tileH=h*.085f,gap=w*.012f;
                        float total=earnedTypes*tileW+(earnedTypes-1)*gap,left=(w-total)*.5f;
                        int slot=0;
                        for(int type=0;type<CardInventory.CARD_TYPES;type++)if(matchRewardCounts[type]>0) {
                            float l=left+slot*(tileW+gap);
                            drawCardTile(c,type,l,h*.775f,l+tileW,h*.775f+tileH,false,true,matchRewardCounts[type]);
                            slot++;
                        }
                    }
                }
                btn(c,"العودة إلى الرئيسية",w*.36f,h*.895f,w*.64f,h*.975f,dark);
                return;
            }
            txt(c,message,w*.5f,h*.145f,h*.036f,dark,true);
            if(mode==4 && onlineWaitingForPeer) {
                btn(c,onlineQuickMatch?"إلغاء البحث والعودة":"إلغاء الدعوة والعودة",
                        w*.31f,h*.81f,w*.69f,h*.92f,dark);
                return;
            }
            if((mode==3 && (lanSession==null || !lanSession.isConnected()))
                    || (mode==4 && (onlineSession==null || !onlineSession.isConnected()))) {
                btn(c,"إلغاء الاتصال والعودة",w*.31f,h*.81f,w*.69f,h*.92f,dark);
                return;
            }
            c.save();
            applyScreenShake(c);
            drawBattlefield(c);
            // Draw utility controls AFTER the battlefield so the background cannot cover them.
            iconButton(c,w*.025f,h*.765f,w*.095f,h*.835f,dark,"⚙");
            iconButton(c,w*.025f,h*.855f,w*.095f,h*.925f,Color.rgb(135,48,43),"↪");            float trackL=w*.18f, trackR=w*.82f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.49f,h*.69f};
            // Move portraits to the extreme upper corners and keep names immediately inward.
            drawAvatar(c,avatars[0],w*.965f,h*.075f,h*.060f,false);
            if(mode==2 && isGeneralOpponent())drawGeneralAvatar(c,w*.035f,h*.075f,h*.060f,false,false); else drawAvatar(c,mode==2?1:avatars[1],w*.035f,h*.075f,h*.060f,false,mode==2&&!campaignGeneralOpponent);
            namePlate(c,rankedPlayerName(playerNames[0]),w*.835f,h*.075f,blue);
            namePlate(c,mode==2?computerOpponentName():rankedPlayerName(playerNames[1]),w*.165f,h*.075f,red);
            drawDestroyedStars(c,1,rankedPlayerName(playerNames[0]),w*.835f);
            drawDestroyedStars(c,2,mode==2?computerOpponentName():rankedPlayerName(playerNames[1]),w*.165f);
            if(openingToss){
                float ds=h*.075f;
                // اللاعب الأول (الأزرق) على اليمين، والثاني (الأحمر) على اليسار.
                box(c,w*.75f-ds/2,h*.145f,w*.75f+ds/2,h*.145f+ds,blue);
                txt(c,String.valueOf(openingRoll1),w*.75f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
                box(c,w*.25f-ds/2,h*.145f,w*.25f+ds/2,h*.145f+ds,red);
                txt(c,String.valueOf(openingRoll2),w*.25f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
            }
            for(int lane=0;lane<3;lane++) {
                float y=rows[lane], gap=h*.038f;
                // تم إخفاء صفوف الذخيرة الستة (60 طلقة) نهائياً من واجهة المعركة.
                drawAmmoCounter(c,w*.84f,y,15-pos2[lane]);
                drawAmmoCounter(c,w*.16f,y,15-pos1[lane]);
                // Draw the GIF behind the weapon so the weapon remains unobstructed.
                if(blueWinEffect[lane]) drawWinEffect(c,w*.925f,y-h*.025f);
                if(redWinEffect[lane]) drawWinEffect(c,w*.075f,y-h*.025f);
                drawWeapon(c,lane,w*.925f,y,blue,false);
                drawWeapon(c,lane,w*.075f,y,red,true);
                drawWeaponCardEffect(c,1,lane,w*.925f,y);
                drawWeaponCardEffect(c,2,lane,w*.075f,y);
                drawWeaponMineEffect(c,1,lane,w*.925f,y);
                drawWeaponMineEffect(c,2,lane,w*.075f,y);
                if(dead1[lane]) drawFire(c,w*.925f,y);
                if(dead2[lane]) drawFire(c,w*.075f,y);
                if(choosing && winner==0) {
                    float ownX=player==1?w*.925f:w*.075f;
                    if(canMove(player,lane)) {
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(gold);
                        c.drawCircle(ownX,y,h*.055f,p); p.setStyle(Paint.Style.FILL);
                    }
                }
            }
            drawAirstrikeEffect(c);
            drawBattleCards(c);
            drawGeneralBattleCards(c);
            drawProjectiles(c);
            drawImpactEffects(c);
            drawMuzzleFlash(c);
            drawExplosion(c);
            drawBattleDialogue(c);
            float diceSize=h*.14f, diceCx=player==1?w*.80f:w*.20f, diceCy=h*.865f;
            boolean localRollTurn=(mode==1 || (mode==2 && player==1)
                    || (mode==3 && player==lanLocalSide && lanSession!=null && lanSession.isConnected())
                    || (mode==4 && player==onlineLocalSide && onlineSession!=null && onlineSession.isConnected()));
            if(localRollTurn && winner==0 && !choosing && die==0) {
                float buttonHalf=w*.115f;
                btn(c,(mode==3 || mode==4)?("أطلق ("+lanCountdown+")"):"أطلق",
                        diceCx-buttonHalf,h*.80f,diceCx+buttonHalf,h*.93f,player==1?blue:red);
            } else {
                int diceColor=winner!=0?dark:(player==1?blue:red);
                if((mode==3 || mode==4) && winner==0 && !choosing && die==0) {
                    txt(c,"بانتظار دورك",diceCx,diceCy+h*.012f,h*.027f,Color.WHITE,true);
                } else if(die>0) {
                    if(doubleDiceDisplayValue>0) {
                        float faceSize=diceSize*.68f,gap=diceSize*.08f;
                        drawDiceFace(c,diceCx-faceSize*.58f-gap*.5f,diceCy,faceSize,doubleDiceDisplayValue);
                        drawDiceFace(c,diceCx+faceSize*.58f+gap*.5f,diceCy,faceSize,doubleDiceDisplayValue);
                    } else drawDiceFace(c,diceCx,diceCy,diceSize*.88f,die);
                }
            }
            if(choosing) { txt(c,"اختر السلاح",w*.5f,h*.965f,h*.026f,dark,true);txt(c,"عدد الطلقات المطلوبة: "+die,w*.5f,h*.925f,h*.022f,Color.WHITE,true); }
            drawCardUseNotice(c);
            drawTemporaryCardMessage(c);
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                touchDown=true;touchDownX=e.getX();touchDownY=e.getY();invalidate();return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){touchDown=false;invalidate();return true;}
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            if(!touchDown)return true;
            float x=e.getX(),y=e.getY();
            if(mode==0 && helpScreen && touchDownY>h*.18f && touchDownY<h*.88f
                    && Math.abs(y-touchDownY)>h*.025f) {
                int maxScroll=Math.max(0,helpContentHeight-(int)(h*.875f));
                helpScrollY=Math.max(0,Math.min(maxScroll,helpScrollY+(int)(touchDownY-y)));
                touchDown=false;invalidate();return true;
            }
            if(Math.hypot(x-touchDownX,y-touchDownY)>h*.10f){touchDown=false;return true;}
            touchDown=false;invalidate();
            if(radarPauseActive)return true;
            if(titleScreen){titleScreen=false;invalidate();return true;}
            if(battleIntro){if(x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f){battleIntro=false;setup=true;setupSide=(mode==1?2:1);}invalidate();return true;}
            float diceSize=h*.14f, diceCx=player==1?w*.80f:w*.20f, diceCy=h*.865f;
            boolean diceTapped=Math.abs(x-diceCx)<=diceSize*.65f && Math.abs(y-diceCy)<=diceSize*.65f;
            if(y>=h*.765f && y<=h*.925f && x>=w*.025f && x<=w*.095f) {
                if(y<=h*.835f)showSettingsDialog(); else showExitConfirmation();
                invalidate();return true;
            }
            if(mode==0) {
                if(isRewardPopupVisible()) {
                    if(x>=w*.35f&&x<=w*.65f&&y>=h*.74f&&y<=h*.85f)acknowledgeRewardPopup();
                    invalidate();return true;
                }
                if(historyScreen || progressScreen || helpScreen || inventoryScreen || missionsScreen) {
                    if(helpScreen && helpSection>=0 && x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f) {
                        helpSection=-1;helpScrollY=0;
                    } else if(helpScreen && helpSection<0 && y>=h*.23f&&y<=h*.78f) {
                        int row=(int)((y-h*.23f)/(h*.20f));int col=x<w*.5f?0:1;
                        if(row>=0&&row<3){helpSection=row*2+col;helpScrollY=0;}
                    } else if((x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f)||(x>=w*.32f&&x<=w*.68f&&y>=h*.91f&&y<=h*.995f)) {
                        if(helpScreen && helpSection>=0){helpSection=-1;helpScrollY=0;}
                        else {historyScreen=false;progressScreen=false;helpScreen=false;inventoryScreen=false;missionsScreen=false;helpSection=-1;}
                    } else if(y>=h*.065f&&y<=h*.155f&&x>=w*.145f&&x<=w*.245f)showSettingsDialog();
                    else if(y>=h*.065f&&y<=h*.155f&&x>=w*.875f&&x<=w*.965f)showExitConfirmation();
                }
                else if(warMenuScreen) {
                    if(y>=h*.065f&&y<=h*.155f&&x>=w*.025f&&x<=w*.125f)warMenuScreen=false;
                    else if(y>=h*.065f&&y<=h*.155f&&x>=w*.145f&&x<=w*.245f)showSettingsDialog();
                    else if(y>=h*.065f&&y<=h*.155f&&x>=w*.875f&&x<=w*.965f)showExitConfirmation();
                    else {
                        boolean sameDevice=x>=w*.065f&&x<=w*.535f&&y>=h*.295f&&y<=h*.455f
                                &&touchDownX>=w*.065f&&touchDownX<=w*.535f&&touchDownY>=h*.295f&&touchDownY<=h*.455f;
                        boolean online=x>=w*.065f&&x<=w*.535f&&y>=h*.505f&&y<=h*.665f
                                &&touchDownX>=w*.065f&&touchDownX<=w*.535f&&touchDownY>=h*.505f&&touchDownY<=h*.665f;
                        boolean campaign=x>=w*.065f&&x<=w*.535f&&y>=h*.715f&&y<=h*.875f
                                &&touchDownX>=w*.065f&&touchDownX<=w*.535f&&touchDownY>=h*.715f&&touchDownY<=h*.875f;
                        boolean vsGeneral=x>=w*.605f&&x<=w*.945f&&y>=h*.295f&&y<=h*.875f
                                &&touchDownX>=w*.605f&&touchDownX<=w*.945f&&touchDownY>=h*.295f&&touchDownY<=h*.875f;
                        if(sameDevice){mode=1;setup=true;setupSide=1;playerNames[0]=savedPlayerName(0);playerNames[1]=savedPlayerName(1);avatars[0]=savedPlayerAvatar();avatars[1]=1;}
                        else if(online)showOnlineModeDialog();
                        else if(campaign)startActivity(new android.content.Intent(MainActivity.this,CampaignActivity.class));
                        else if(vsGeneral){cardInventory.prepareNewMatchSetup();mode=2;setup=true;setupSide=1;playerNames[0]=savedPlayerName(0);playerNames[1]=savedPlayerName(1);avatars[0]=savedPlayerAvatar();avatars[1]=1;}
                    }
                }
                else if(y>=h*.065f&&y<=h*.165f&&x>=w*.035f&&x<=w*.125f)showSettingsDialog();
                else if(y>=h*.065f&&y<=h*.165f&&x>=w*.875f&&x<=w*.965f)showExitConfirmation();
                else if(y>=h*.38f&&y<=h*.82f&&x>=w*.635f&&x<=w*.945f)warMenuScreen=true;
                else if(y>=h*.405f&&y<=h*.59f&&x>=w*.08f&&x<=w*.23f)missionsScreen=true;
                else if(y>=h*.405f&&y<=h*.59f&&x>=w*.26f&&x<=w*.41f)inventoryScreen=true;
                else if(y>=h*.405f&&y<=h*.59f&&x>=w*.44f&&x<=w*.58f)historyScreen=true;
                else if(y>=h*.65f&&y<=h*.83f&&x>=w*.17f&&x<=w*.32f)progressScreen=true;
                else if(y>=h*.65f&&y<=h*.83f&&x>=w*.35f&&x<=w*.50f){helpScreen=true;helpScrollY=0;}
                
                invalidate();return true;
            }
            if(setup) {
                if(x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f){
                    if(campaignBattle){setResult(RESULT_CANCELED);finish();return true;}
                    else if(setupSide==2 && mode==1)setupSide=1;
                    else {if(mode==2)cardInventory.clearSelection();setup=false;mode=0;message="اختر طريقة اللعب";}
                    invalidate();return true;
                }
                int idx=setupSide-1;
                if(mode==2 && !campaignBattle && x>=w*.075f && x<=w*.455f && y>=h*.615f && y<=h*.895f) {
                    for(int level=0;level<4;level++) {
                        float top=h*(.615f+level*.069f),bottom=top+h*.064f;
                        if(y>=top-h*.006f && y<=bottom+h*.006f) {
                            difficulty=level;
                            message="تم اختيار مستوى "+new String[]{"مبتدئ","متوسط","محترف","أسطوري"}[level];
                            invalidate();return true;
                        }
                    }
                }
                if(mode==2 && y>=h*.735f && y<=h*.865f && x>=w*.585f && x<=w*.933f) {
                    for(int type=0;type<CardInventory.CARD_TYPES;type++) {
                        int row=type/5,col=type%5;float left=w*(.585f+col*.071f),top=h*(.735f+row*.067f);
                        if(x>=left && x<=left+w*.064f && y>=top && y<=top+h*.058f) {
                            boolean changed=cardInventory.toggleSelection(type);
                            if(!changed) message=cardInventory.getCount(type)<=0?"لا توجد نسخ من هذه البطاقة في المخزن":"تعذر اختيار البطاقة — أعد فتح إعداد المباراة";
                            else message=cardInventory.isSelected(type)?"تم اختيار البطاقة":"تم إلغاء اختيار البطاقة";
                            invalidate();return true;
                        }
                    }
                }
                else if(mode==1 && setupSide==2 && x>=w*.27f && x<=w*.73f && y>=h*.62f && y<=h*.75f) { showNameDialog(); return true; }
                else if(mode==1 && setupSide==2 && y>=h*.30f && y<=h*.62f && x>=w*.36f && x<=w*.64f) { if(x<=w*.43f)avatars[idx]=nextAvatar(avatars[idx],-1);else if(x>=w*.57f)avatars[idx]=nextAvatar(avatars[idx],1); }
                else if(x>=w*.37f && x<=w*.63f && y>=h*.91f && y<=h*.985f) {
                    if(setupSide==1 && mode==1) setupSide=2;
                    else if(mode==3){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;lanOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;if(lanHostPending){lanHostPending=false;startLanHost();}else if(lanJoinAddress!=null){String address=lanJoinAddress;lanJoinAddress=null;startLanJoin(address);} }
                    else if(mode==4){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;onlineOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
                        if(onlineQuickMatch)startOnlineQuickMatch(playerNames[0],avatars[0]);
                        else if(onlineInvitePending)startOnlineHost(playerNames[0],avatars[0]);
                        else if(onlineLocalSide==1)startOnlineHost(playerNames[0],avatars[0]);
                        else startOnlineJoin(onlineJoinCode,playerNames[1],avatars[1]);}
                    else { if(mode==2 && !cardInventory.beginMatch()){message="تعذر تجهيز البطاقات — تحقق من المخزن";invalidate();return true;}if(isGeneralOpponent())missionManager.beginTrackedMatch();cardMatchSettled=false;clearBattleCardEffects();setup=false;resetMatch(); }
                }
                invalidate();return true;
            }
            if(mode==4 && onlineWaitingForPeer) {
                if(x>=w*.31f && x<=w*.69f && y>=h*.81f && y<=h*.92f) {
                    timerlessCloseOnline();
                    onlineWaitingForPeer=false; onlineQuickMatch=false; onlineInvitePending=false;
                    mode=0; setup=false; message="اختر طريقة اللعب";
                }
                invalidate();return true;
            }
            if(openingToss)return true;
            if(mode==4 && winner==0 && player==onlineLocalSide && onlineLoadoutReady
                    && y>=h*.125f && y<=h*.210f) {
                float left=onlineLocalSide==1?w*.70f:w*.02f,width=w*.075f,gap=w*.012f;int slot=0;
                for(int type=0;type<CardInventory.CARD_TYPES;type++)if(onlineCards[type]&&!onlineCardsUsedBySide[onlineLocalSide][type]){
                    float l=left+slot*(width+gap);slot++;
                    if(x>=l&&x<=l+width){
                        if(type==CardInventory.MILITARY_SUPPLY){if(die>0&&choosing)showOnlineSupplyChoice();else message="استخدم الإمداد بعد رمي النرد";}
                        else if(type==CardInventory.RADAR||type==CardInventory.MINE_DETECTOR){
                            if(applyOnlineCardEffect(onlineLocalSide,type,-1,0,true))selectedBattleCard=-1;
                        } else {selectedBattleCard=selectedBattleCard==type?-1:type;
                            message=selectedBattleCard<0?"تم إلغاء البطاقة":"اختر السلاح المستهدف";}
                        invalidate();return true;
                    }
                }
            }
            if(mode==2 && player==1 && winner==0 && !bonusAttempt
                    && y>=h*.125f && y<=h*.210f && x>=w*.70f && x<=w*.99f) {
                boolean[] active=cardInventory.getActiveCards(),used=cardInventory.getUsedCards();
                float width=w*.075f,gap=w*.012f,top=h*.125f,tileHeight=h*.085f,left=w*.70f;
                int slot=0;
                for(int type=0;type<CardInventory.CARD_TYPES;type++) {
                    if(!active[type]||used[type])continue;
                    float l=left+slot*(width+gap),t=top;
                    slot++;
                    if(x>=l&&x<=l+width&&y>=t&&y<=t+tileHeight) {
                        if(type==CardInventory.DOUBLE_DICE && doubleDicePending)message="تم تفعيل مضاعفة النرد بالفعل";
                        else if(type==CardInventory.AIRSTRIKE || type==CardInventory.RADAR || type==CardInventory.MINE_DETECTOR) {
                            selectedBattleCard=-1;cardTargeting=false;confirmImmediateCard(type);
                        }
                        else if(type==CardInventory.MILITARY_SUPPLY) {
                            if(die>0 && choosing)showSupplyChoice();
                            else message="يمكن استخدام الإمداد العسكري بعد رمي النرد";
                        }
                        else {selectedBattleCard=selectedBattleCard==type?-1:type;cardTargeting=selectedBattleCard>=0;message=selectedBattleCard<0?"تم إلغاء اختيار البطاقة":"اختر السلاح المستهدف";}
                        invalidate();return true;
                    }
                }
            }
            if((mode==3 && (lanSession==null || !lanSession.isConnected()))
                    || (mode==4 && (onlineSession==null || !onlineSession.isConnected()))) {
                if(x>=w*.31f && x<=w*.69f && y>=h*.81f && y<=h*.92f) {
                    if(mode==3){timerlessCloseLan();lanHostPending=false;lanJoinAddress=null;}
                    else timerlessCloseOnline();
                    mode=0;setup=false;message="اختر طريقة اللعب";
                }
                invalidate();return true;
            }
            if(winner!=0 && !bonusAttempt && !resultRevealReady) return true;
            if(winner!=0 && !bonusAttempt && resultRevealReady) {
                if(y>=h*.885f&&y<=h*.985f) {
                    if(x>=w*.36f&&x<=w*.64f) {
                        if(campaignBattle) {
                            android.content.Intent result=new android.content.Intent();
                            result.putExtra("campaign_won",winner==1);
                            result.putExtra("campaign_continent",MainActivity.this.getIntent().getIntExtra("campaign_continent",-1));
                            result.putExtra("campaign_country",MainActivity.this.getIntent().getIntExtra("campaign_country",-1));
                            setResult(RESULT_OK,result);finish();return true;
                        }
                        matchEpoch++; timerlessCloseLan(); timerlessCloseOnline(); lanHostPending=false; lanJoinAddress=null; mode=0; setup=false; stage=0; winner=0; die=0; choosing=false; openingToss=false; battleIntro=false; progressScreen=false; explosionAt=0; message="اختر طريقة اللعب";
                    }
                }
                invalidate();return true;
            }
            boolean localRollTurn=(mode==1 || (mode==2 && player==1)
                    || (mode==3 && player==lanLocalSide && lanSession!=null && lanSession.isConnected())
                    || (mode==4 && player==onlineLocalSide && onlineSession!=null && onlineSession.isConnected()));
            boolean launchTapped=localRollTurn && !choosing && die==0
                    && y>=h*.80f && y<=h*.94f && Math.abs(x-diceCx)<=w*.115f;
            // Allow the selected battle card to receive a weapon tap even before rolling; repair must work on a destroyed weapon with die==0.
            boolean cardTargetTap=(mode==2 && player==1 || mode==4 && player==onlineLocalSide)
                    && winner==0 && selectedBattleCard>=0;
            if(!choosing && !diceTapped && !launchTapped && !cardTargetTap)return true;
            if(stage<2 && diceTapped) {
                // قرعة البداية أصبحت تلقائية وتُعرض بجانب كل لاعب.
                return true;
            } else if(!choosing && (diceTapped || launchTapped)) {
                if(mode==2&&player==2){computerTurn();}
                else if(mode==3){if(lanSession!=null)lanSession.rollNow(false);}
                else if(mode==4){if(onlineSession!=null)onlineSession.rollNow(false);}
                else {
                    die=random.nextInt(6)+1;doubleDiceDisplayValue=0;choosing=true;
                    if(mode==2 && player==1 && doubleDicePending) {
                        int targetLane=doubleDiceLane;
                        doubleDicePending=false;doubleDiceLane=-1;
                        doubleDiceDisplayValue=die;
                        die*=2;
                        boolean laneReady=targetLane>=0 && targetLane<3
                                && !destroyed(1)[targetLane] && !destroyed(2)[targetLane]
                                && !cyberDisabled[1][targetLane] && !shieldActive[2][targetLane]
                                && positions(1)[targetLane]<15;
                        if(laneReady) {
                            message="مضاعفة النرد — النتيجة: "+die;
                            animateMove(1,targetLane,die);
                        } else {
                            message="تعذر تنفيذ مضاعفة النرد على السلاح المحدد";
                            die=0;choosing=false;invalidate();
                        }
                    } else if(!anyMove(player)){
                        setNoMoveDialogue(player);
                        message="لا توجد حركة متاحة — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        choosing=false;invalidate();
                        final int epoch=matchEpoch;
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt))return;
                            if(mode==2){
                                finishCardTurnEffects(1);
                                die=0;player=2;message="دور "+computerOpponentName();invalidate();
                                postDelayed(new Runnable(){@Override public void run(){
                                    if(epoch==matchEpoch && mode==2 && winner==0 && player==2)computerTurn();
                                }},500);
                            } else afterTurn();
                            invalidate();
                        }},1200);
                    }
                    else message=(mode==2?"أنت":"اللاعب "+player)+" رمى "+die+" — اختر سلاحاً";
                }
            } else {
                float ownX=player==1?w*.925f:w*.075f;                int lane=-1;
                boolean targetsOpponent=selectedBattleCard==CardInventory.CYBER_WARFARE || selectedBattleCard==CardInventory.MINEFIELD;
                int cardTargetSide=targetsOpponent?(player==1?2:1):player;
                float targetX=cardTargetSide==1?w*.925f:w*.075f;
                float[] weaponRows={h*.29f,h*.49f,h*.69f};
                // الحرب السيبرانية وحقل الألغام يستهدفان سلاح الخصم يسار الشاشة؛ بقية البطاقات تستهدف سلاح اللاعب يميناً.
                if(Math.abs(x-targetX)<w*.070f) {
                    for(int i=0;i<3;i++) if(Math.abs(y-weaponRows[i])<h*.045f) lane=i;
                }
                if(lane>=0 && mode==2 && player==1 && selectedBattleCard>=0) {
                    applySelectedBattleCardToLane(selectedBattleCard,lane);
                } else if(lane>=0 && mode==4 && player==onlineLocalSide && selectedBattleCard>=0){
                    int card=selectedBattleCard;applyOnlineCardEffect(onlineLocalSide,card,lane,0,true);selectedBattleCard=-1;
                } else if(lane>=0) {
                    if(mineTargetSide==player && mineLane==lane)animateMove(player,lane,die);
                    else if(!canMove(player,lane))message="هذه الحركة غير ممكنة — اختر سلاحاً آخر";
                    else {
                        if(mode==3 && lanSession!=null){if(lanSession.sendMove(lane))animateMove(player,lane,die);}
                        else if(mode==4 && onlineSession!=null){if(onlineSession.sendMove(lane))animateMove(player,lane,die);}
                        else animateMove(player,lane,die);
                    }
                }
            }
            invalidate();return true;
        }
    }
}