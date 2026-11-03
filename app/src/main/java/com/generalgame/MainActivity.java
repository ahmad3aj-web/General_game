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
        private final HashMap<String,Bitmap> art = new HashMap<>();
        private Movie fireMovie;
        private Movie winMovie;
        private final boolean[] blueWinEffect = {false,false,false};
        private final boolean[] redWinEffect = {false,false,false};
        private long fireStartedAt = System.currentTimeMillis();
        private boolean titleScreen = true;
        private SoundPool gameSoundPool;
        private MediaPlayer backgroundMusic;
        private boolean soundEnabled, musicEnabled, vibrationEnabled;
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
        private boolean recorded = false;
        private boolean bonusAttempt = false, perfectWin = false, pointsAwarded = false;
        private int matchPoints = 0;
        private boolean battleIntro = false;
        private int introStep = 0;
        private final String[] rankNames = {"جندي","عريف","رقيب","ملازم","نقيب","رائد","عقيد","لواء"};
        private final int[] rankThresholds = {0,130,320,650,1100,1700,2500,3600};
        private int matchEpoch = 0;
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
            String[] scenes={"2112.w023.n001.1491B.p1.1491.jpg","2207_w023_n003_2757b_p1_2757.jpg","png.png","war-zone-landscape-with-apocalyptic-destruction.jpg","1793716501127.jpg","Intro.jpg"};
            for(String name:scenes)try{art.put(name,BitmapFactory.decodeStream(getAssets().open("backgrounds/"+name)));}catch(Exception ignored){}
            try { fireMovie=Movie.decodeStream(getAssets().open("vehicles/Flame_GIF_2.gif")); } catch(Exception ignored) {}
            try { winMovie=Movie.decodeStream(getAssets().open("vehicles/Win.gif")); } catch(Exception ignored) {}
        }
        private void drawArt(Canvas c,String name,float l,float t,float r,float b,boolean flip) {
            Bitmap bitmap=art.get(name);if(bitmap==null)return;RectF dst=new RectF(l,t,r,b);
            p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(255);p.setFilterBitmap(true);
            if(flip){c.save();c.scale(-1f,1f,(l+r)*.5f,(t+b)*.5f);c.drawBitmap(bitmap,null,dst,p);c.restore();}else c.drawBitmap(bitmap,null,dst,p);
        }

        GameView() {
            super(MainActivity.this);
            loadGameArt();
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE);
            soundEnabled=prefs.getBoolean("sound",true);
            musicEnabled=prefs.getBoolean("music",true);
            vibrationEnabled=prefs.getBoolean("vibration",true);
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
                    backgroundMusic=MediaPlayer.create(MainActivity.this,R.raw.battle_music);
                    if(backgroundMusic!=null) {
                        backgroundMusic.setAudioAttributes(musicAttributes);
                        backgroundMusic.setLooping(true);
                        backgroundMusic.setVolume(0.40f,0.40f);
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
            final String[] labels={"المؤثرات الصوتية","الموسيقى","الاهتزاز عند الانفجار"};
            final boolean[] values={soundEnabled,musicEnabled,vibrationEnabled};
            new AlertDialog.Builder(MainActivity.this).setTitle("إعدادات اللعبة")
                .setMultiChoiceItems(labels,values,(dialog,which,checked)->{
                    if(which==0)soundEnabled=checked;
                    else if(which==1){musicEnabled=checked;updateBackgroundMusic();}
                    else vibrationEnabled=checked;
                    getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE).edit()
                        .putBoolean("sound",soundEnabled).putBoolean("music",musicEnabled)
                        .putBoolean("vibration",vibrationEnabled).apply();
                }).setPositiveButton("تم",null).show();
        }
        private void showExitConfirmation() {
            new AlertDialog.Builder(MainActivity.this).setTitle("تأكيد الخروج")
                .setMessage("هل أنت متأكد أنك تريد الخروج من لعبة GENERAL؟")
                .setPositiveButton("خروج",(dialog,which)->{timerlessCloseLan();timerlessCloseOnline();finish();})
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
            Bitmap image=art.get("1793716501127.jpg");
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
            // Each lane is a one-on-one duel: once either weapon is destroyed, that lane is closed.
            return !destroyed(side)[lane] && !destroyed(opposingSide)[lane] && positions(side)[lane]+die<=15;
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
                    playerNames[0]=savedPlayerName(0); playerNames[1]=savedPlayerName(1);
                    showLanHostAddress();
                })
                .setNegativeButton("انضمام لمباراة", (d,which)->{
                    mode=3; lanLocalSide=2; setup=true; setupSide=2;
                    playerNames[0]=savedPlayerName(0); playerNames[1]=savedPlayerName(1);
                    showLanJoinDialog();
                })
                .setNeutralButton("إلغاء",null).show();
        }
        private void showOnlineModeDialog() {
            final String[] choices={"لعب سريع — ابحث عن لاعب","دعوة صديق عبر واتساب","الانضمام برمز غرفة"};
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("اللعب عبر الإنترنت")
                .setItems(choices,(dialog,which)->{
                    mode=4; setup=true; setupSide=1; onlineJoinCode=null;
                    playerNames[0]=savedPlayerName(0); avatars[0]=0;
                    if(which==0) {
                        onlineLocalSide=1; onlineQuickMatch=true; onlineInvitePending=false;
                    } else if(which==1) {
                        onlineLocalSide=1; onlineQuickMatch=false; onlineInvitePending=true;
                    } else {
                        setup=false; onlineQuickMatch=false; onlineInvitePending=false;
                        showOnlineSetupNotice(false);
                        return;
                    }
                    invalidate();
                })
                .setNegativeButton("إلغاء",null).show();
        }

        private void handleInviteIntent(android.content.Intent intent) {
            if(intent==null || intent.getData()==null)return;
            android.net.Uri uri=intent.getData();
            if(!"generalgame".equals(uri.getScheme()) || !"join".equals(uri.getHost()))return;
            String code=uri.getQueryParameter("room");
            if(code==null || !code.matches("[A-Za-z0-9]{8}"))return;
            onlineJoinCode=code.toUpperCase(Locale.ROOT);
            onlineLocalSide=2; onlineQuickMatch=false; onlineInvitePending=false;
            mode=4; setup=true; setupSide=2;            playerNames[1]=savedPlayerName(1); avatars[1]=1;
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
                    playerNames[1]=savedPlayerName(1); avatars[1]=1; invalidate();
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
                        postDelayed(()->{if(epoch==matchEpoch && mode==3 && side==player){die=0;choosing=false;if(bonusAttempt)finishMatch();else afterTurn();invalidate();}},1100);
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
        private void createOnlineSession() {
            if(onlineSession!=null)onlineSession.close();
            onlineOpeningStarted=false; onlineTurnId=0;
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
                    if(!onlineOpeningStarted) {
                        onlineOpeningStarted=true;
                        if(onlineLocalSide==1)onlineSession.startOpeningToss();
                    }
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
                    message="اللاعبان جاهزان — قرعة البداية"; invalidate();
                });}
                @Override public void onTurnStarted(int id,int side,boolean local){runOnUiThread(()->{
                    onlineTurnId=id;player=side;die=0;choosing=false;lanCountdown=30;
                    message=local?"دورك — اضغط أطلق خلال 30 ثانية":"دور الخصم — بانتظار رمي النرد"; invalidate();
                });}
                @Override public void onCountdown(int seconds){runOnUiThread(()->{lanCountdown=seconds;invalidate();});}
                @Override public void onRoll(int side,int value,boolean automatic){runOnUiThread(()->{
                    if(mode!=4 || winner!=0 || side!=player)return;
                    die=value;choosing=true;
                    message=(side==onlineLocalSide?"أنت":"الخصم")+" رمى "+value+(automatic?" تلقائياً":"")+" — اختر السلاح";
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
                            die=0;choosing=false;if(bonusAttempt)finishMatch();else afterTurn();invalidate();
                        }},1100);
                        invalidate();
                    }
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
        private void drawSetup(Canvas c) {
            txt(c,"إعداد اللاعبين",w*.5f,h*.105f,h*.060f,dark,true);
            iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");
            int idx=setupSide-1;
            if(mode==2) {
                box(c,w*.055f,h*.19f,w*.47f,h*.86f,Color.argb(225,238,243,230));box(c,w*.53f,h*.19f,w*.945f,h*.86f,Color.argb(225,248,250,239));
                drawGeneralAvatar(c,w*.265f,h*.36f,h*.115f,true,false);
                String[] generalLevels={"خبير ناشئ","قائد ذو خبرة","جنرال مخضرم","قائد فائق الخبرة"};
                txt(c,"المستوى الحالي: "+generalLevels[Math.max(0,Math.min(3,difficulty))],w*.265f,h*.565f,h*.024f,dark,true);
                txt(c,"مستوى الصعوبة",w*.265f,h*.595f,h*.030f,dark,true);
                String[] levels={"مبتدئ","متوسط","محترف","أسطوري"};String[] symbols={"Ⅰ","Ⅱ","Ⅲ","★"};
                for(int i=0;i<4;i++){float top=h*(.615f+i*.058f),bottom=top+h*.050f;box(c,w*.105f,top,w*.425f,bottom,difficulty==i?Color.rgb(180,132,35):Color.rgb(218,228,211));txt(c,symbols[i],w*.155f,top+h*.035f,h*.032f,difficulty==i?Color.WHITE:dark,true);txt(c,levels[i],w*.29f,top+h*.034f,h*.025f,difficulty==i?Color.WHITE:dark,difficulty==i);}
                drawAvatar(c,avatars[0],w*.765f,h*.405f,h*.135f,true);arrowButton(c,w*.64f,h*.405f,true);arrowButton(c,w*.89f,h*.405f,false);
                txt(c,"الشخصية "+(avatars[0]+1)+" من 17",w*.765f,h*.565f,h*.027f,dark,true);
                box(c,w*.585f,h*.64f,w*.945f,h*.755f,Color.rgb(27,48,39));txt(c,playerNames[0],w*.765f,h*.695f,h*.043f,Color.WHITE,true);txt(c,"اضغط لتعديل الاسم",w*.765f,h*.735f,h*.022f,Color.rgb(221,229,215),false);
            } else {
                txt(c,setupSide==1?"اللاعب الأول — الأزرق":"اللاعب الثاني — الأحمر",w*.5f,h*.20f,h*.040f,setupSide==1?blue:red,true);
                float portraitY=h*.43f;drawAvatar(c,avatars[idx],w*.5f,portraitY,h*.125f,true);arrowButton(c,w*.39f,portraitY,true);arrowButton(c,w*.61f,portraitY,false);
                txt(c,"الشخصية "+(avatars[idx]+1)+" من 17",w*.5f,portraitY+h*.16f,h*.027f,dark,true);
                box(c,w*.27f,h*.62f,w*.73f,h*.71f,Color.rgb(248,250,239));txt(c,playerNames[idx],w*.5f,h*.675f,h*.043f,dark,true);txt(c,"اضغط على الاسم لتعديله",w*.5f,h*.745f,h*.024f,dark,false);
            }
            String startLabel=setupSide==1 && mode==1?"التالي":(mode==4 && onlineQuickMatch?"ابحث عن لاعب":(mode==4 && onlineInvitePending?"إنشاء الدعوة":"ابدأ المباراة"));
            btn(c,startLabel,w*.37f,h*.91f,w*.63f,h*.985f,setupSide==1?blue:red);
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
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,side*.025f));
            p.setColor(Color.argb(225,238,224,190));
            c.drawRoundRect(tile,side*.13f,side*.13f,p);
            p.setStyle(Paint.Style.FILL);
            txt(c,glyph,cx,top+side*.68f,side*.56f,Color.WHITE,true);
            txt(c,label,cx,top+side+h*.032f,h*.024f,Color.WHITE,true);
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
            if(pointsAwarded || mode!=2 || winner!=1)return;
            int[] base={10,20,35,50}, perfectBonus={5,10,20,30};
            int level=Math.max(0,Math.min(3,difficulty));
            int earned=base[level]+(perfectWin?perfectBonus[level]:0);
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE);
            prefs.edit().putInt("points",prefs.getInt("points",0)+earned)
                .putInt("wins",prefs.getInt("wins",0)+1)
                .putInt("perfectWins",prefs.getInt("perfectWins",0)+(perfectWin?1:0))
                .putInt("lastEarned",earned).putInt("lastDifficulty",difficulty).apply();
            matchPoints=earned;pointsAwarded=true;
        }
        private void finishMatch() {
            if(winner==0)return;
            awardProgressPoints();
            bonusAttempt=false;battleIntro=false;die=0;choosing=false;
            if(mode==2)message=winner==1?"انتهت المباراة — أنت الفائز!":"انتهت المباراة — فاز جنيرال!";
            else message="انتهت المباراة — الفائز: "+(winner==1?playerNames[0]:playerNames[1]);
            saveMatchResult();invalidate();
        }
        private void beginBonusAttempt() {
            bonusAttempt=true;player=1;die=0;choosing=false;
            message="فزت! لديك رمية إضافية لتدمير السلاح الثالث والحصول على نقاط أكثر";
            invalidate();
        }
        private void startBattleIntro() {
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
                        message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب الأول يبدأ"):(mode==2?"جنيرال يبدأ":"اللاعب الثاني يبدأ");
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
                drawGeneralAvatar(c,w*.30f,h*.45f,h*.120f,false,false);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,"جنيرال",w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,rankedPlayerName(playerNames[0]),w*.70f,h*.61f,h*.036f,Color.WHITE,true);
            } else {
                drawAvatar(c,avatars[1],w*.30f,h*.45f,h*.115f,false);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,rankedPlayerName(playerNames[1]),w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,rankedPlayerName(playerNames[0]),w*.70f,h*.61f,h*.036f,Color.WHITE,true);
            }
            drawArt(c,"vs_emblem.png",w*.425f,h*.385f,w*.575f,h*.555f,false);
            txt(c,String.valueOf(Math.max(1,introStep)),w*.5f,h*.79f,h*.17f,Color.WHITE,true);
        }
        private void drawHelp(Canvas c) {
            box(c,w*.055f,h*.105f,w*.945f,h*.895f,Color.rgb(244,248,235));
            txt(c,"شرح لعبة GENERAL",w*.5f,h*.19f,h*.052f,dark,true);
            txt(c,"هدف اللعبة",w*.5f,h*.275f,h*.031f,Color.rgb(155,105,28),true);
            txt(c,"دمّر أسلحة الخصم الثلاثة: الدبابة والطائرة والمدفع.",w*.5f,h*.325f,h*.026f,dark,true);
            txt(c,"طريقة اللعب",w*.5f,h*.405f,h*.031f,Color.rgb(155,105,28),true);
            txt(c,"1) تبدأ المباراة بقرعة النرد لتحديد اللاعب الأول.",w*.5f,h*.455f,h*.024f,dark,false);
            txt(c,"2) في دورك اضغط «أطلق» لتحصل على رقم من 1 إلى 6.",w*.5f,h*.505f,h*.024f,dark,false);
            txt(c,"3) اختر دبابة أو طائرة أو مدفعاً متاحاً لتحريكه.",w*.5f,h*.555f,h*.024f,dark,false);
            txt(c,"4) لكل سلاح مسار مستقل من 15 خطوة باتجاه سلاح الخصم المقابل.",w*.5f,h*.605f,h*.024f,dark,false);
            txt(c,"5) عندما يصل السلاح إلى نهاية المسار يدمّر السلاح المقابل.",w*.5f,h*.655f,h*.024f,dark,false);
            txt(c,"6) بعد تدمير أحد سلاحي المسار لا يمكن مواصلة اللعب فيه.",w*.5f,h*.705f,h*.024f,dark,false);
            txt(c,"الفوز",w*.5f,h*.775f,h*.031f,Color.rgb(155,105,28),true);
            txt(c,"تُحسم المواجهات الثلاث؛ والفائز من يحسم مسارين على الأقل.",w*.5f,h*.825f,h*.023f,dark,true);
            btn(c,"العودة إلى الرئيسية",w*.38f,h*.91f,w*.62f,h*.985f,dark);
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
                String opponent=mode==2?"جنيرال":(winner==1?playerNames[1]:playerNames[0]);
                String winnerName=winner==1?playerNames[0]:(mode==2?"جنيرال":playerNames[1]);
                String loserName=winner==1?(mode==2?"جنيرال":playerNames[1]):playerNames[0];
                item.put("opponent",opponent); item.put("winner",winnerName); item.put("loser",loserName);
                if(mode==2) item.put("result",winner==1?"فوز":"خسارة");
                else {
                    item.put("player1",playerNames[0]);
                    item.put("player2",playerNames[1]);
                    item.put("winnerSide",winner);
                }
                item.put("mode",mode);
                item.put("points",mode==2 && winner==1?matchPoints:0);
                item.put("perfectWin",mode==2 && winner==1 && perfectWin);
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

        private void resetMatch() {
            matchEpoch++;
            for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();
            stage=0; firstRoll=0; player=1; die=0; winner=0; choosing=false; recorded=false; explosionAt=0;
            bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
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
                message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب الأول يبدأ"):(mode==2?"جنيرال يبدأ":"اللاعب الثاني يبدأ");
                invalidate();
                if(mode==2 && player==2)computerTurn();
            }},2600);
        }
        private void finishCheck() {
            int blueLaneWins=0,redLaneWins=0,resolvedDuels=0;
            for(int i=0;i<3;i++) {
                if(dead2[i] && !dead1[i]){blueLaneWins++;resolvedDuels++;}
                else if(dead1[i] && !dead2[i]){redLaneWins++;resolvedDuels++;}
            }
            // Do not end after only two lanes: the third opposing pair must also be decided.
            if(resolvedDuels<3)return;
            if(blueLaneWins>=2){winner=1;perfectWin=blueLaneWins==3;finishMatch();return;}
            if(redLaneWins>=2){winner=2;finishMatch();}
        }
        private void afterTurn() {
            if(winner!=0 && !bonusAttempt)return;
            die=0; choosing=false;
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
            die=random.nextInt(6)+1;
            message="جنيرال يرمي النرد...";
            invalidate();
            postDelayed(new Runnable(){ @Override public void run(){
                if(epoch!=matchEpoch || mode!=2 || winner!=0 || player!=2)return;
                message="جنيرال حصل على "+die;
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
                        message="جنيرال اختار "+names[pick];
                        invalidate();
                        final int selected=pick;
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch==matchEpoch && mode==2 && winner==0 && player==2) animateMove(2,selected,die);
                        }},650);
                    } else {
                        setNoMoveDialogue(2);
                        message="لا توجد حركة متاحة لجنيرال — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        invalidate();
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch==matchEpoch && mode==2 && winner==0 && player==2) {
                                player=1;die=0;choosing=false;
                                message="دورك — اضغط النرد";
                                invalidate();
                            }
                        }},1200);                    }
                }},850);
            }},850);
        }
        private void animateMove(final int side, final int lane, final int amount) {
            final int epoch=matchEpoch;
            final boolean bonusShot=bonusAttempt;
            choosing=false;
            playGameSound(lane);
            muzzleFlashAt=System.currentTimeMillis();
            muzzleFlashX=side==1?w*.874f:w*.126f;
            muzzleFlashY=new float[]{h*.29f,h*.49f,h*.69f}[lane];
            final int start=positions(side)[lane];
            final int target=Math.min(15,start+Math.max(1,Math.min(6,amount)));
            message=(side==1?"الأزرق":"الأحمر")+" يطلق...";
            final Runnable[] step=new Runnable[1];
            step[0]=new Runnable(){ @Override public void run(){
                if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt))return;
                if(positions(side)[lane]<target){
                    positions(side)[lane]++;
                    invalidate();
                    postDelayed(step[0],115);
                } else {
                    if(target>=15){
                        // The blue weapon is on the right and the red weapon is on the left.
                        // Explosions must appear on the opposing weapon that was destroyed.
                        if(side==1){dead2[lane]=true;blueWinEffect[lane]=true;setBattleDialogue(1,2);spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);playGameSound(3);}
                        else {dead1[lane]=true;redWinEffect[lane]=true;setBattleDialogue(2,2);spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);playGameSound(3);}
                    }
                    finishCheck();
                    if(bonusShot && bonusAttempt && winner==1)finishMatch();
                    if(winner==0){
                        if(mode==2 && side==1){
                            player=2; setBattleDialogue(2,1); die=0; message="دور جنيرال";
                            postDelayed(new Runnable(){@Override public void run(){if(epoch==matchEpoch && mode==2 && winner==0 && player==2){computerTurn();invalidate();}}},650);
                        } else if(mode==2 && side==2){
                            player=1;die=0;choosing=false;message="دورك — اضغط النرد";
                        } else afterTurn();
                    }
                    invalidate();
                }
            }};
            postDelayed(step[0],180);
        }
        private void spawnExplosion(float x,float y) {
            vibrateForExplosion();
            explosionX=x; explosionY=y; explosionAt=System.currentTimeMillis();
            invalidate();
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
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color,boolean faceRight) {
            String file=lane==0?"tank_01.png":lane==1?"fighter_01.png":"cannon_01.png";float ww=w*.105f,hh=h*.105f;
            drawArt(c,file,cx-ww*.5f,cy-hh*.5f,cx+ww*.5f,cy+hh*.5f,!faceRight);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight();
            c.drawColor(Color.rgb(23,34,29));
            if(titleScreen) { drawIntroScreen(c); return; }
            txt(c,"GENERAL  •  جنيرال",w*.5f,h*.075f,h*.06f,Color.rgb(238,224,190),true);
            if(battleIntro){drawBattleIntro(c);return;}
            if(setup) { drawSetup(c); return; }
            if(mode==0) {
                drawMenuBackdrop(c);
                if(historyScreen){drawHistory(c);iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");return;}if(progressScreen){drawProgress(c);iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");return;}if(helpScreen){drawHelp(c);iconButton(c,w*.025f,h*.065f,w*.125f,h*.155f,dark,"↩");return;}
                txt(c,"اختر ساحة المعركة",w*.5f,h*.205f,h*.060f,Color.rgb(248,232,190),true);
                iconButton(c,w*.035f,h*.065f,w*.125f,h*.165f,dark,"⚙");iconButton(c,w*.875f,h*.065f,w*.965f,h*.165f,Color.rgb(135,48,43),"⏻");
                float c1=w*.18f,c2=w*.50f,c3=w*.82f;
                menuButton(c,"⚔","لاعبان على نفس الجهاز",c1-w*.15f,h*.275f,c1+w*.15f,h*.55f,blue);
                menuButton(c,"★","ضد جنيرال الكمبيوتر",c2-w*.15f,h*.275f,c2+w*.15f,h*.55f,red);
                menuButton(c,"◎","اللعب عبر الإنترنت",c3-w*.15f,h*.275f,c3+w*.15f,h*.55f,Color.rgb(37,105,160));
                menuButton(c,"▤","سجل المباريات",c1-w*.15f,h*.59f,c1+w*.15f,h*.88f,dark);
                menuButton(c,"♜","لوحة التقدم",c2-w*.15f,h*.59f,c2+w*.15f,h*.88f,Color.rgb(155,105,28));
                menuButton(c,"?","شرح اللعبة",c3-w*.15f,h*.59f,c3+w*.15f,h*.88f,Color.rgb(55,91,72));
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setFakeBoldText(true);
                p.setTextSize(h*.020f);p.setColor(Color.WHITE);
                c.drawText("© 2026 أحمد جرادات",w*.5f,h*.945f,p);
                p.setTextSize(h*.018f);
                c.drawText("ahmad3aj@gmail.com",w*.5f,h*.975f,p);
                p.setFakeBoldText(false);
                return;
            }
            if(winner!=0 && !bonusAttempt) {
                box(c,w*.12f,h*.19f,w*.88f,h*.80f,Color.rgb(245,249,239));
                String winnerName=winner==1?playerNames[0]:(mode==2?"جنيرال":playerNames[1]);
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
                    drawGeneralAvatar(c,generalX,portraitY,portraitR,generalWon,!generalWon);
                    drawAvatar(c,avatars[0],playerX,portraitY,portraitR,!generalWon,generalWon);
                    txt(c,"جنيرال",generalX,h*.505f,h*.034f,dark,true);
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
                    txt(c,"جنيرال: "+redWins+" أسلحة",w*.34f,h*.615f,h*.032f,red,true);
                    txt(c,playerNames[0]+": "+blueWins+" أسلحة",w*.66f,h*.615f,h*.032f,blue,true);
                } else {
                    txt(c,playerNames[1]+": "+redWins+" أسلحة",w*.34f,h*.615f,h*.032f,red,true);
                    txt(c,playerNames[0]+": "+blueWins+" أسلحة",w*.66f,h*.615f,h*.032f,blue,true);
                }
                txt(c,"الفائز: "+winnerName,w*.5f,h*.685f,h*.040f,winner==1?blue:red,true);
                if(mode==2 && winner==1)txt(c,"النقاط المكتسبة: "+matchPoints,w*.5f,h*.715f,h*.025f,Color.rgb(35,125,72),true);
                btn(c,"العودة إلى الرئيسية",w*.36f,h*.73f,w*.64f,h*.80f,dark);
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
            drawBattlefield(c);
            // Draw utility controls AFTER the battlefield so the background cannot cover them.
            iconButton(c,w*.025f,h*.765f,w*.095f,h*.835f,dark,"⚙");
            iconButton(c,w*.025f,h*.855f,w*.095f,h*.925f,Color.rgb(135,48,43),"↪");            float trackL=w*.18f, trackR=w*.82f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.49f,h*.69f};
            // Move portraits to the extreme upper corners and keep names immediately inward.
            drawAvatar(c,avatars[0],w*.965f,h*.075f,h*.060f,false);
            if(mode==2) drawGeneralAvatar(c,w*.035f,h*.075f,h*.060f,false,false); else drawAvatar(c,avatars[1],w*.035f,h*.075f,h*.060f,false);
            namePlate(c,rankedPlayerName(playerNames[0]),w*.835f,h*.075f,blue);
            namePlate(c,mode==2?"جنيرال":rankedPlayerName(playerNames[1]),w*.165f,h*.075f,red);
            int blueDestroyed=0,redDestroyed=0;
            for(int i=0;i<3;i++){if(dead2[i])blueDestroyed++;if(dead1[i])redDestroyed++;}
            txt(c,"أسلحة مدمرة: "+blueDestroyed,w*.835f,h*.125f,h*.019f,Color.WHITE,true);
            txt(c,"أسلحة مدمرة: "+redDestroyed,w*.165f,h*.125f,h*.019f,Color.WHITE,true);
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
                // ستة صفوف واضحة: صفّان متقابلان لكل نوع سلاح.
                // اللاعب الأزرق يميناً يطلق يساراً، والخصم الأحمر يساراً يطلق يميناً.
                float bulletW=trackW/17f, bulletH=h*.048f;
                float leftStart=trackL+bulletW*.65f, rightStart=trackR-bulletW*.65f;
                for(int i=0;i<15;i++) {
                    float bx=rightStart-i*(trackW*.92f/14f);
                    float rx=leftStart+i*(trackW*.92f/14f);
                    boolean leftDead=dead1[lane], rightDead=dead2[lane];
                    drawBullet(c,bx,y-gap,bulletW,bulletH,leftDead?Color.GRAY:blue,!leftDead && i<pos1[lane],false);
                    drawBullet(c,rx,y+gap,bulletW,bulletH,rightDead?Color.GRAY:red,!rightDead && i<pos2[lane],true);
                }
                // Draw the GIF behind the weapon so the weapon remains unobstructed.
                if(blueWinEffect[lane]) drawWinEffect(c,w*.925f,y-h*.025f);
                if(redWinEffect[lane]) drawWinEffect(c,w*.075f,y-h*.025f);
                drawWeapon(c,lane,w*.925f,y,blue,false);
                drawWeapon(c,lane,w*.075f,y,red,true);
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
            drawMuzzleFlash(c);
            drawExplosion(c);
            drawBattleDialogue(c);
            float diceSize=h*.14f, diceCx=player==1?w*.80f:w*.20f, diceCy=h*.865f;
            boolean localRollTurn=(mode==1 || (mode==2 && player==1)
                    || (mode==3 && player==lanLocalSide && lanSession!=null && lanSession.isConnected())
                    || (mode==4 && player==onlineLocalSide && onlineSession!=null && onlineSession.isConnected()));
            if(localRollTurn && !choosing && die==0) {
                float buttonHalf=w*.115f;
                btn(c,(mode==3 || mode==4)?("أطلق ("+lanCountdown+")"):"أطلق",
                        diceCx-buttonHalf,h*.80f,diceCx+buttonHalf,h*.93f,player==1?blue:red);
            } else {
                int diceColor=winner!=0?dark:(player==1?blue:red);
                if((mode==3 || mode==4) && winner==0 && !choosing && die==0) {
                    txt(c,"بانتظار دورك",diceCx,diceCy+h*.012f,h*.027f,Color.WHITE,true);
                } else if(die>0) drawDiceFace(c,diceCx,diceCy,diceSize*.88f,die);
            }
            if(choosing) { txt(c,"اختر السلاح",w*.5f,h*.965f,h*.026f,dark,true);txt(c,"عدد الطلقات المطلوبة: "+die,w*.5f,h*.925f,h*.022f,Color.WHITE,true); }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                touchDown=true;touchDownX=e.getX();touchDownY=e.getY();invalidate();return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){touchDown=false;invalidate();return true;}
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            if(!touchDown)return true;
            float x=e.getX(),y=e.getY();
            if(Math.hypot(x-touchDownX,y-touchDownY)>h*.10f){touchDown=false;return true;}
            touchDown=false;invalidate();
            if(titleScreen){titleScreen=false;invalidate();return true;}
            if(battleIntro){if(x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f){battleIntro=false;setup=true;setupSide=(mode==1?2:1);}invalidate();return true;}
            float diceSize=h*.14f, diceCx=player==1?w*.80f:w*.20f, diceCy=h*.865f;
            boolean diceTapped=Math.abs(x-diceCx)<=diceSize*.65f && Math.abs(y-diceCy)<=diceSize*.65f;
            if(y>=h*.765f && y<=h*.925f && x>=w*.025f && x<=w*.095f) {
                if(y<=h*.835f)showSettingsDialog(); else showExitConfirmation();
                invalidate();return true;
            }
            if(mode==0) {
                if(historyScreen || progressScreen || helpScreen) { if((x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f)||(x>=w*.38f&&x<=w*.62f&&y>=h*.91f&&y<=h*.995f)){historyScreen=false;progressScreen=false;helpScreen=false;} }
                else if(y>=h*.065f&&y<=h*.165f&&x>=w*.035f&&x<=w*.125f)showSettingsDialog();
                else if(y>=h*.065f&&y<=h*.165f&&x>=w*.875f&&x<=w*.965f)showExitConfirmation();
                else if(y>=h*.275f&&y<=h*.55f&&x>=w*.075f&&x<=w*.285f){mode=1;setup=true;setupSide=1;playerNames[0]=savedPlayerName(0);playerNames[1]=savedPlayerName(1);avatars[0]=0;avatars[1]=1;}
                else if(y>=h*.275f&&y<=h*.55f&&x>=w*.3975f&&x<=w*.6025f){mode=2;setup=true;setupSide=1;playerNames[0]=savedPlayerName(0);playerNames[1]=savedPlayerName(1);avatars[0]=0;avatars[1]=1;}
                else if(y>=h*.275f&&y<=h*.55f&&x>=w*.7175f&&x<=w*.9225f)showOnlineModeDialog();
                else if(y>=h*.59f&&y<=h*.89f&&x>=w*.075f&&x<=w*.285f)historyScreen=true;
                else if(y>=h*.59f&&y<=h*.89f&&x>=w*.3975f&&x<=w*.6025f)progressScreen=true;
                else if(y>=h*.59f&&y<=h*.89f&&x>=w*.7175f&&x<=w*.9225f)helpScreen=true;
                invalidate();return true;
            }
            if(setup) {
                if(x>=w*.025f&&x<=w*.125f&&y>=h*.065f&&y<=h*.155f){
                    if(setupSide==2 && mode==1)setupSide=1;
                    else {setup=false;mode=0;message="اختر طريقة اللعب";}
                    invalidate();return true;
                }
                int idx=setupSide-1;
                if(mode==2 && x>=w*.585f && x<=w*.945f && y>=h*.64f && y<=h*.755f) { showNameDialog(); return true; }
                if(mode==2 && x>=w*.105f && x<=w*.425f && y>=h*.615f && y<=h*.90f) { difficulty=Math.max(0,Math.min(3,(int)((y/h-.615f)/.058f))); }
                else if(mode==2 && y>=h*.28f && y<=h*.59f && x>=w*.60f && x<=w*.93f) { if(x<=w*.68f)avatars[0]=nextAvatar(avatars[0],-1);else if(x>=w*.85f)avatars[0]=nextAvatar(avatars[0],1); }
                else if(mode!=2 && x>=w*.27f && x<=w*.73f && y>=h*.62f && y<=h*.75f) { showNameDialog(); return true; }
                else if(mode!=2 && y>=h*.30f && y<=h*.62f && x>=w*.36f && x<=w*.64f) { if(x<=w*.43f)avatars[idx]=nextAvatar(avatars[idx],-1);else if(x>=w*.57f)avatars[idx]=nextAvatar(avatars[idx],1); }
                else if(x>=w*.37f && x<=w*.63f && y>=h*.91f && y<=h*.985f) {
                    if(setupSide==1 && mode==1) setupSide=2;
                    else if(mode==3){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;lanOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;if(lanHostPending){lanHostPending=false;startLanHost();}else if(lanJoinAddress!=null){String address=lanJoinAddress;lanJoinAddress=null;startLanJoin(address);} }
                    else if(mode==4){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}clearWinEffects();stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;onlineOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
                        if(onlineQuickMatch)startOnlineQuickMatch(playerNames[0],avatars[0]);
                        else if(onlineInvitePending)startOnlineHost(playerNames[0],avatars[0]);
                        else if(onlineLocalSide==1)startOnlineHost(playerNames[0],avatars[0]);
                        else startOnlineJoin(onlineJoinCode,playerNames[1],avatars[1]);}
                    else { setup=false;resetMatch(); }
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
            if((mode==3 && (lanSession==null || !lanSession.isConnected()))
                    || (mode==4 && (onlineSession==null || !onlineSession.isConnected()))) {
                if(x>=w*.31f && x<=w*.69f && y>=h*.81f && y<=h*.92f) {
                    if(mode==3){timerlessCloseLan();lanHostPending=false;lanJoinAddress=null;}
                    else timerlessCloseOnline();
                    mode=0;setup=false;message="اختر طريقة اللعب";
                }
                invalidate();return true;
            }
            if(winner!=0 && !bonusAttempt) {
                if(y>=h*.70f&&y<=h*.80f) {
                    if(x>=w*.36f&&x<=w*.64f) { matchEpoch++; timerlessCloseLan(); timerlessCloseOnline(); lanHostPending=false; lanJoinAddress=null; mode=0; setup=false; stage=0; winner=0; die=0; choosing=false; openingToss=false; battleIntro=false; progressScreen=false; explosionAt=0; message="اختر طريقة اللعب"; }
                }
                invalidate();return true;
            }
            boolean localRollTurn=(mode==1 || (mode==2 && player==1)
                    || (mode==3 && player==lanLocalSide && lanSession!=null && lanSession.isConnected())
                    || (mode==4 && player==onlineLocalSide && onlineSession!=null && onlineSession.isConnected()));
            boolean launchTapped=localRollTurn && !choosing && die==0
                    && y>=h*.80f && y<=h*.94f && Math.abs(x-diceCx)<=w*.115f;
            if(!choosing && !diceTapped && !launchTapped)return true;
            if(stage<2 && diceTapped) {
                // قرعة البداية أصبحت تلقائية وتُعرض بجانب كل لاعب.
                return true;
            } else if(!choosing && (diceTapped || launchTapped)) {
                if(mode==2&&player==2){computerTurn();}
                else if(mode==3){if(lanSession!=null)lanSession.rollNow(false);}
                else if(mode==4){if(onlineSession!=null)onlineSession.rollNow(false);}
                else {
                    die=random.nextInt(6)+1;choosing=true;
                    if(!anyMove(player)){
                        setNoMoveDialogue(player);
                        message="لا توجد حركة متاحة — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        choosing=false;invalidate();
                        final int epoch=matchEpoch;
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch!=matchEpoch || (winner!=0 && !bonusAttempt))return;
                            if(bonusAttempt){finishMatch();return;}
                            if(mode==2){
                                die=0;player=2;message="دور جنيرال";invalidate();
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
                float[] weaponRows={h*.30f,h*.49f,h*.68f};
                // اختيار السلاح بالضغط على رسمه نفسه، وليس على زر يحمل اسمه.
                if(Math.abs(x-ownX)<w*.052f) {
                    for(int i=0;i<3;i++) if(Math.abs(y-weaponRows[i])<h*.052f) lane=i;
                }
                if(lane>=0) {
                    if(!canMove(player,lane))message="هذه الحركة غير ممكنة — اختر سلاحاً آخر";
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