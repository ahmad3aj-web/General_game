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
        private Bitmap spriteSheet;
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
        private boolean recorded = false;
        private boolean bonusAttempt = false, perfectWin = false, pointsAwarded = false;
        private int matchPoints = 0;
        private boolean battleIntro = false;
        private int introStep = 0;
        private final String[] rankNames = {"جندي","عريف","رقيب","ملازم","نقيب","رائد","عقيد","لواء"};
        private final int[] rankThresholds = {0,100,250,500,850,1300,1900,2700};
        private int matchEpoch = 0;
        private int setupSide = 1;
        private int difficulty = 1; // 0 beginner, 1 medium, 2 professional, 3 legendary
        private final String[] playerNames = {"أنت","اللاعب 2"};
        private final int[] avatars = {0,1};
        private final int[] avatarColors = {Color.rgb(35,112,205),Color.rgb(205,69,54),Color.rgb(45,145,105),Color.rgb(142,85,180),Color.rgb(225,145,45),Color.rgb(65,115,155),Color.rgb(176,91,119),Color.rgb(105,137,91),Color.rgb(48,96,133),Color.rgb(166,91,61),Color.rgb(67,119,83),Color.rgb(116,82,145),Color.rgb(185,126,54),Color.rgb(54,124,132),Color.rgb(151,79,104),Color.rgb(112,125,73)};
        private int stage = 0; // opening toss: 0 or 1; 2 means game
        private int firstRoll = 0, player = 1, die = 0, winner = 0;
        private int openingRoll1 = 0, openingRoll2 = 0;
        private boolean openingToss = false;
        private boolean choosing = false;
        private String message = "اختر طريقة اللعب";
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

        GameView() {
            super(MainActivity.this);
            try { spriteSheet=BitmapFactory.decodeStream(getAssets().open("1793691666432.jpg")); } catch(Exception ignored) { spriteSheet=null; }
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE);
            soundEnabled=prefs.getBoolean("sound",true);
            musicEnabled=prefs.getBoolean("music",true);
            vibrationEnabled=prefs.getBoolean("vibration",true);
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            gameSoundPool = new SoundPool.Builder()
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
            float half=size*.50f;
            RectF face=new RectF(cx-half,cy-half,cx+half,cy+half);
            p.setShader(new LinearGradient(face.left,face.top,face.right,face.bottom,Color.rgb(255,255,250),Color.rgb(205,215,222),Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawRoundRect(face,size*.13f,size*.13f,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,h*.003f));p.setColor(Color.rgb(70,83,94));c.drawRoundRect(face,size*.13f,size*.13f,p);p.setStyle(Paint.Style.FILL);
            float d=size*.105f,a=size*.25f;
            float[][] dots={{},{ {0,0} },{ {-a,-a},{a,a} },{ {-a,-a},{0,0},{a,a} },{ {-a,-a},{a,-a},{-a,a},{a,a} },{ {-a,-a},{a,-a},{0,0},{-a,a},{a,a} },{ {-a,-a},{a,-a},{-a,0},{a,0},{-a,a},{a,a} }};
            for(float[] dot:dots[value>=1&&value<=6?value:1]){p.setColor(Color.rgb(30,42,53));c.drawCircle(cx+dot[0],cy+dot[1],d,p);}
        }

        private void drawBattlefield(Canvas c) {
            int scene=Math.floorMod(matchEpoch,4);
            int[][] crops={{16,354,365,531},{375,354,724,531},{733,354,1082,531},{15,545,366,698}};
            int[] q=crops[scene];
            drawSprite(c,q[0],q[1],q[2],q[3],0,0,w,h,false);
            p.setColor(Color.argb(28,18,25,24));p.setStyle(Paint.Style.FILL);c.drawRect(0,0,w,h,p);
        }

        private void txt(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setColor(color); p.setTextSize(size); p.setFakeBoldText(bold);
            p.setTextAlign(Paint.Align.CENTER); p.setStyle(Paint.Style.FILL); c.drawText(s,x,y,p);
        }
        private void box(Canvas c,float l,float t,float r,float b,int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(l,t,r,b),16,16,p);
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
        private int[] positions(int side) { return side==1 ? pos1 : pos2; }
        private boolean[] destroyed(int side) { return side==1 ? dead1 : dead2; }
        private boolean canMove(int side,int lane) {
            return !destroyed(side)[lane] && positions(side)[lane]+die<=15;
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
                    playerNames[0]="اللاعب الأزرق"; playerNames[1]="اللاعب الأحمر";
                    showLanHostAddress();
                })
                .setNegativeButton("انضمام لمباراة", (d,which)->{
                    mode=3; lanLocalSide=2; setup=true; setupSide=2;
                    playerNames[0]="اللاعب الأزرق"; playerNames[1]="أنت";
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
                    playerNames[0]="أنت"; avatars[0]=0;
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
            mode=4; setup=true; setupSide=2;
            playerNames[1]="أنت"; avatars[1]=1;
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
                mode=4; setup=true; setupSide=1; playerNames[0]="أنت"; avatars[0]=0;
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
                    playerNames[1]="أنت"; avatars[1]=1; invalidate();
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
                    if(!anyMove(side)){
                        message="لا توجد حركة متاحة — نتيجة النرد: "+die+" — تم تجاوز الدور";
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
                    if(!value.isEmpty()) playerNames[setupSide-1]=value;
                    invalidate();
                })
                .setNegativeButton("إلغاء",null)
                .show();
        }
        private int grayColor(int color) {
            int gray=(int)(Color.red(color)*.299f+Color.green(color)*.587f+Color.blue(color)*.114f);
            return Color.rgb(gray,gray,gray);
        }
        private void drawAvatar(Canvas c,int index,float cx,float cy,float radius,boolean selected) {
            drawAvatar(c,index,cx,cy,radius,selected,false);
        }

        private void drawGeneralAvatar(Canvas c,float cx,float cy,float radius,boolean selected,boolean monochrome) {
            // General has a unique veteran commander portrait; rank details evolve with difficulty.
            int level=Math.max(0,Math.min(3,difficulty));
            int ink=monochrome?Color.DKGRAY:Color.rgb(39,43,39);
            int goldTone=monochrome?Color.GRAY:Color.rgb(224,180,72);
            int uniform=monochrome?Color.GRAY:new int[]{Color.rgb(91,119,91),Color.rgb(73,105,79),Color.rgb(58,83,68),Color.rgb(48,65,57)}[level];
            drawAvatar(c,5,cx,cy,radius,selected,monochrome);
            c.save();
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            // Distinct commander beard and strong moustache.
            p.setColor(ink);
            if(level>=1) {
                Path beard=new Path();beard.moveTo(cx-radius*.24f,cy+radius*.19f);
                beard.quadTo(cx,cy+radius*.43f,cx+radius*.24f,cy+radius*.19f);
                beard.quadTo(cx+radius*.17f,cy+radius*.48f,cx,cy+radius*.48f);
                beard.quadTo(cx-radius*.17f,cy+radius*.48f,cx-radius*.24f,cy+radius*.19f);
                c.drawPath(beard,p);
                c.drawOval(new RectF(cx-radius*.23f,cy+radius*.12f,cx+radius*.23f,cy+radius*.27f),p);
            }
            // Commander's peaked cap, with progressively richer insignia.
            p.setColor(ink);c.drawRoundRect(new RectF(cx-radius*.43f,cy-radius*.57f,cx+radius*.43f,cy-radius*.31f),radius*.10f,radius*.10f,p);
            p.setColor(uniform);c.drawRoundRect(new RectF(cx-radius*.39f,cy-radius*.54f,cx+radius*.39f,cy-radius*.34f),radius*.08f,radius*.08f,p);
            p.setColor(ink);c.drawOval(new RectF(cx-radius*.48f,cy-radius*.37f,cx+radius*.48f,cy-radius*.27f),p);
            p.setColor(goldTone);c.drawCircle(cx,cy-radius*.43f,radius*(level==3?.105f:.075f),p);
            p.setColor(Color.rgb(255,235,170));c.drawCircle(cx,cy-radius*.43f,radius*.035f,p);
            // Rank bars and decorations.
            p.setColor(goldTone);
            for(int i=0;i<=level;i++) c.drawRoundRect(new RectF(cx-radius*.32f+i*radius*.13f,cy+radius*.56f,cx-radius*.25f+i*radius*.13f,cy+radius*.66f),radius*.02f,radius*.02f,p);
            if(level>=2) {
                p.setColor(goldTone);
                Path medal=new Path();medal.moveTo(cx+radius*.34f,cy+radius*.48f);medal.lineTo(cx+radius*.43f,cy+radius*.61f);medal.lineTo(cx+radius*.34f,cy+radius*.73f);medal.lineTo(cx+radius*.25f,cy+radius*.61f);medal.close();c.drawPath(medal,p);
                p.setColor(Color.rgb(255,235,170));c.drawCircle(cx+radius*.34f,cy+radius*.61f,radius*.045f,p);
            }
            if(level==3) {
                p.setColor(Color.argb(monochrome?80:180,235,205,120));
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.5f,radius*.045f));
                c.drawArc(new RectF(cx-radius*.52f,cy-radius*.68f,cx+radius*.52f,cy+radius*.02f),205,130,false,p);
                p.setStyle(Paint.Style.FILL);
            }
            c.restore();
        }
        private void drawSetup(Canvas c) {
            txt(c,"إعداد اللاعبين",w*.5f,h*.16f,h*.065f,dark,true);
            txt(c,setupSide==1?"اللاعب الأول — الأزرق":"اللاعب الثاني — الأحمر",w*.5f,h*.25f,h*.043f,setupSide==1?blue:red,true);
            int idx=setupSide-1;
            float portraitY=mode==2?h*.43f:h*.46f;
            drawAvatar(c,avatars[idx],w*.5f,portraitY,h*.135f,true);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(34,65,48));
            Path leftArrow=new Path();leftArrow.moveTo(w*.17f,portraitY);leftArrow.lineTo(w*.235f,portraitY-h*.045f);leftArrow.lineTo(w*.235f,portraitY+h*.045f);leftArrow.close();c.drawPath(leftArrow,p);
            Path rightArrow=new Path();rightArrow.moveTo(w*.83f,portraitY);rightArrow.lineTo(w*.765f,portraitY-h*.045f);rightArrow.lineTo(w*.765f,portraitY+h*.045f);rightArrow.close();c.drawPath(rightArrow,p);
            txt(c,"الشخصية "+(avatars[idx]+1)+" من 18",w*.5f,portraitY+h*.17f,h*.027f,dark,true);
            box(c,w*.27f,h*.62f,w*.73f,h*.70f,Color.rgb(248,250,239));
            txt(c,playerNames[idx],w*.5f,h*.675f,h*.043f,dark,true);
            txt(c,"اضغط على الاسم لتعديله",w*.5f,h*.72f,h*.024f,dark,false);
            if(mode==2) {
                String[] levels={"مبتدئ","متوسط","محترف","أسطوري"};
                txt(c,"مستوى جنيرال",w*.5f,h*.755f,h*.026f,dark,true);
                for(int i=0;i<4;i++) {
                    float left=w*(.16f+i*.18f);
                    box(c,left,h*.775f,left+w*.16f,h*.825f,difficulty==i?gold:Color.rgb(230,237,218));
                    txt(c,levels[i],left+w*.08f,h*.807f,h*.022f,dark,difficulty==i);
                }
            }
            String startLabel=setupSide==1 && mode==1?"التالي":
                    (mode==4 && onlineQuickMatch?"ابحث عن لاعب":
                    (mode==4 && onlineInvitePending?"إنشاء الدعوة":"ابدأ المباراة"));
            btn(c,startLabel,w*.36f,h*.92f,w*.64f,h*.985f,setupSide==1?blue:red);
        }


        private int rankIndex(int points) {
            int rank=0;
            for(int i=0;i<rankThresholds.length;i++)if(points>=rankThresholds[i])rank=i;
            return rank;
        }
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
            txt(c,"استعدوا للحرب",w*.5f,h*.25f,h*.055f,Color.rgb(245,190,65),true);
            if(mode==2) {
                drawGeneralAvatar(c,w*.30f,h*.45f,h*.115f,false,false);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,"جنيرال",w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,playerNames[0],w*.70f,h*.61f,h*.036f,Color.WHITE,true);
            } else {
                drawAvatar(c,avatars[1],w*.30f,h*.45f,h*.115f,false);
                drawAvatar(c,avatars[0],w*.70f,h*.45f,h*.115f,true);
                txt(c,playerNames[1],w*.30f,h*.61f,h*.036f,Color.WHITE,true);
                txt(c,playerNames[0],w*.70f,h*.61f,h*.036f,Color.WHITE,true);
            }
            txt(c,"VS",w*.5f,h*.47f,h*.052f,Color.rgb(245,190,65),true);
            txt(c,introStep>0?String.valueOf(introStep):"FIGHT!",w*.5f,h*.77f,
                introStep>0?h*.17f:h*.105f,introStep>0?Color.WHITE:Color.rgb(245,190,65),true);
        }
        private void drawProgress(Canvas c) {
            android.content.SharedPreferences prefs=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE);
            int points=prefs.getInt("points",0),wins=prefs.getInt("wins",0);
            int perfect=prefs.getInt("perfectWins",0),last=prefs.getInt("lastEarned",0);
            int rank=rankIndex(points),next=rank<rankNames.length-1?rank+1:rank;
            txt(c,"لوحة التقدم",w*.5f,h*.16f,h*.055f,dark,true);
            box(c,w*.12f,h*.22f,w*.88f,h*.48f,Color.rgb(244,248,235));
            txt(c,"رتبتك العسكرية",w*.5f,h*.285f,h*.03f,dark,true);
            txt(c,rankNames[rank],w*.5f,h*.385f,h*.075f,Color.rgb(155,105,28),true);
            txt(c,"مجموع النقاط: "+points,w*.5f,h*.445f,h*.031f,dark,true);
            if(next>rank) {
                float left=w*.20f,right=w*.80f,top=h*.545f;
                box(c,left,top,right,top+h*.025f,Color.rgb(220,226,211));
                float ratio=(float)(points-rankThresholds[rank])/(rankThresholds[next]-rankThresholds[rank]);
                box(c,left,top,left+(right-left)*Math.max(0f,Math.min(1f,ratio)),top+h*.025f,Color.rgb(52,135,83));
                txt(c,"باقي "+Math.max(0,rankThresholds[next]-points)+" نقطة للترقية إلى "+rankNames[next],w*.5f,h*.615f,h*.026f,dark,false);
            } else txt(c,"وصلت إلى أعلى رتبة عسكرية!",w*.5f,h*.615f,h*.029f,Color.rgb(155,105,28),true);
            box(c,w*.12f,h*.68f,w*.88f,h*.85f,Color.rgb(244,248,235));
            txt(c,"الانتصارات ضد جنيرال: "+wins,w*.5f,h*.735f,h*.031f,dark,true);
            txt(c,"انتصارات بتدمير الأسلحة الثلاثة: "+perfect,w*.5f,h*.785f,h*.027f,dark,true);
            txt(c,"نقاط آخر مباراة: "+last,w*.5f,h*.83f,h*.027f,dark,false);
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
                    if(side==1){first[0]++;second[1]++;}
                    else if(side==2){first[1]++;second[0]++;}
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
            for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}
            stage=0; firstRoll=0; player=1; die=0; winner=0; choosing=false; recorded=false; explosionAt=0;
            bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
            openingRoll1=0; openingRoll2=0; openingToss=false;
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
                message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب الأول يبدأ"):(mode==2?"جنيرال يبدأ":"اللاعب الثاني يبدأ");
                invalidate();
                if(mode==2 && player==2)computerTurn();
            }},2600);
        }
        private void finishCheck() {
            int p1Destroyed=0,p2Destroyed=0;
            for(int i=0;i<3;i++){if(dead1[i])p1Destroyed++;if(dead2[i])p2Destroyed++;}
            if(bonusAttempt) {
                if(p2Destroyed>=3){perfectWin=true;finishMatch();}
                return;
            }
            if(p1Destroyed>=2){winner=2;finishMatch();return;}
            if(p2Destroyed>=2) {
                winner=1;
                if(mode==2){beginBonusAttempt();return;}
                finishMatch();
            }
        }
        private void afterTurn() {
            if(winner!=0 && !bonusAttempt)return;
            die=0; choosing=false;
            if(mode==2) {
                player=1;
                message="دورك — اضغط النرد";
            } else {
                player=player==1?2:1;
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
                        message="لا توجد حركة متاحة لجنيرال — نتيجة النرد: "+die+" — تم تجاوز الدور";
                        invalidate();
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch==matchEpoch && mode==2 && winner==0 && player==2) {
                                player=1;die=0;choosing=false;
                                message="دورك — اضغط النرد";
                                invalidate();
                            }
                        }},1200);
                    }
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
            final int target=Math.min(15,start+amount);
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
                        if(side==1){dead2[lane]=true;spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);playGameSound(3);}
                        else {dead1[lane]=true;spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);playGameSound(3);}
                    }
                    finishCheck();
                    if(bonusShot && bonusAttempt && winner==1)finishMatch();
                    if(winner==0){
                        if(mode==2 && side==1){
                            player=2; die=0; message="دور جنيرال";
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
            if(spriteSheet!=null) {
                int left=pointsRight?1112:1268, right=pointsRight?1264:1425;
                int top=filled?397:454, bottom=filled?449:505;
                drawSprite(c,left,top,right,bottom,cx-bw*.52f,cy-bh*.55f,cx+bw*.52f,cy+bh*.55f,false);
                return;
            }
            int body=filled?color:Color.rgb(247,248,239);
            int outline=Color.rgb(48,57,46);
            p.setStyle(Paint.Style.FILL);
            p.setColor(body);
            RectF shell=new RectF(cx-bw*.42f,cy-bh*.34f,cx+bw*.25f,cy+bh*.34f);
            c.drawRoundRect(shell,bh*.12f,bh*.12f,p);
            Path tip=new Path();
            if(pointsRight){tip.moveTo(cx+bw*.25f,cy-bh*.34f);tip.quadTo(cx+bw*.66f,cy,cx+bw*.25f,cy+bh*.34f);}
            else{tip.moveTo(cx-bw*.42f,cy-bh*.34f);tip.quadTo(cx-bw*.83f,cy,cx-bw*.42f,cy+bh*.34f);}
            tip.close();c.drawPath(tip,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1f,bh*.075f));p.setColor(outline);
            c.drawRoundRect(shell,bh*.12f,bh*.12f,p);c.drawPath(tip,p);
            p.setStyle(Paint.Style.FILL);p.setColor(filled?shade(color,.62f):Color.rgb(135,143,127));
            float baseX=pointsRight?cx-bw*.39f:cx+bw*.22f;
            c.drawRect(baseX-bw*.035f,cy-bh*.39f,baseX+bw*.035f,cy+bh*.39f,p);
        }
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color,boolean faceRight) {
            if(spriteSheet!=null) {
                int[][] weapons={{8,258,196,349},{376,254,568,347},{219,258,365,347}};
                int[] q=weapons[lane];float ww=w*.105f,hh=h*.105f;
                drawSprite(c,q[0],q[1],q[2],q[3],cx-ww*.5f,cy-hh*.5f,cx+ww*.5f,cy+hh*.5f,!faceRight);
                return;
            }
            // Vector artwork: hand-inked outlines, soft highlights and a fixed team flag.
            int ink=Color.rgb(42,48,43), highlight=shade(color,1.28f), shadow=shade(color,.58f);
            float sx=w*.00098f, sy=h*.00088f;
            c.save(); c.translate(cx-w*.049f,cy-h*.0335f); c.scale(sx,sy);
            if(!faceRight) { c.translate(100,0); c.scale(-1,1); }
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(65,25,35,20));c.drawOval(new RectF(7,66,96,76),p);
            if(lane==0) {
                // Tank tracks and individual road wheels.
                p.setColor(ink);c.drawRoundRect(new RectF(7,39,94,68),13,13,p);
                p.setColor(Color.rgb(116,126,105));c.drawRoundRect(new RectF(11,43,90,64),10,10,p);
                for(int i=0;i<5;i++){float wx=20+i*15;p.setColor(ink);c.drawCircle(wx,54,8,p);p.setColor(Color.rgb(205,207,183));c.drawCircle(wx,54,5.3f,p);p.setColor(Color.rgb(116,126,105));c.drawCircle(wx,54,2,p);}
                // Lower hull.
                p.setColor(ink);c.drawRoundRect(new RectF(9,31,92,55),8,8,p);
                p.setShader(new LinearGradient(0,31,0,55,highlight,color,Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(12,33,89,52),7,7,p);p.setShader(null);
                p.setColor(Color.rgb(225,220,190));c.drawRoundRect(new RectF(19,36,34,47),3,3,p);
                p.setColor(shadow);c.drawRoundRect(new RectF(58,36,76,47),3,3,p);
                // Turret and barrel.
                p.setColor(ink);c.drawRoundRect(new RectF(34,19,73,43),12,12,p);
                p.setShader(new LinearGradient(0,19,0,43,highlight,color,Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(36,20,71,40),10,10,p);p.setShader(null);
                p.setColor(ink);c.drawRoundRect(new RectF(58,25,99,32),3,3,p);
                p.setColor(Color.rgb(174,181,157));c.drawRoundRect(new RectF(62,26,96,28),2,2,p);
                p.setColor(Color.rgb(223,225,204));c.drawRoundRect(new RectF(36,15,55,21),6,6,p);
                p.setColor(ink);c.drawCircle(46,17,5,p);p.setColor(Color.rgb(218,220,199));c.drawCircle(46,16,3,p);
                // Small rivets.
                p.setColor(Color.rgb(236,232,207));for(int i=0;i<4;i++)c.drawCircle(18+i*8,34,1.1f,p);
            } else if(lane==1) {
                // Cartoon aircraft: fuselage, wings, tail and cockpit.
                Path wing=new Path();wing.moveTo(42,35);wing.lineTo(5,55);wing.lineTo(31,52);wing.lineTo(45,45);wing.lineTo(60,53);wing.lineTo(96,57);wing.lineTo(62,35);wing.close();
                p.setColor(ink);c.drawPath(wing,p);p.setColor(color);c.drawPath(wing,p);
                p.setColor(highlight);Path wingLight=new Path();wingLight.moveTo(43,37);wingLight.lineTo(14,51);wingLight.lineTo(39,47);wingLight.close();c.drawPath(wingLight,p);
                Path body=new Path();body.moveTo(5,37);body.quadTo(35,28,72,31);body.quadTo(91,34,99,39);body.quadTo(75,45,35,44);body.lineTo(5,40);body.close();
                p.setColor(ink);c.drawPath(body,p);p.setColor(color);Path inner=new Path();inner.moveTo(8,37);inner.quadTo(40,30,73,33);inner.quadTo(88,36,94,39);inner.quadTo(62,42,35,42);inner.lineTo(8,39);inner.close();c.drawPath(inner,p);
                p.setColor(highlight);c.drawLine(19,36,73,34,p);
                p.setColor(ink);c.drawOval(new RectF(42,31,57,40),p);p.setColor(Color.rgb(157,211,224));c.drawOval(new RectF(44,32,55,38),p);
                p.setColor(Color.rgb(238,235,205));c.drawCircle(14,38,1.4f,p);c.drawCircle(83,38,1.4f,p);
            } else {
                // Field cannon on two wheels with a shield and long barrel.
                p.setColor(ink);c.drawCircle(25,58,13,p);c.drawCircle(76,58,13,p);
                p.setColor(Color.rgb(164,171,145));c.drawCircle(25,58,8,p);c.drawCircle(76,58,8,p);
                p.setColor(ink);c.drawCircle(25,58,3,p);c.drawCircle(76,58,3,p);
                p.setColor(ink);c.drawRoundRect(new RectF(16,38,86,52),5,5,p);
                p.setShader(new LinearGradient(0,38,0,52,highlight,color,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(18,39,84,49),4,4,p);p.setShader(null);
                Path shield=new Path();shield.moveTo(33,30);shield.quadTo(50,23,69,30);shield.lineTo(66,49);shield.quadTo(50,56,36,48);shield.close();
                p.setColor(ink);c.drawPath(shield,p);p.setColor(color);c.drawPath(shield,p);
                p.setColor(ink);c.drawRoundRect(new RectF(48,18,99,25),3,3,p);p.setColor(Color.rgb(187,192,168));c.drawRoundRect(new RectF(52,19,96,21),2,2,p);
                p.setColor(highlight);c.drawLine(38,33,62,30,p);
            }
            // Fine mechanical details that enrich the existing silhouettes without changing their colors.
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
            if(lane==0) {
                p.setColor(Color.argb(175,235,232,205));p.setStrokeWidth(1.25f);
                c.drawLine(15,47,86,47,p);c.drawLine(15,61,86,61,p);
                p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(224,218,184));
                for(int i=0;i<5;i++)c.drawCircle(20+i*15,54,1.35f,p);
                p.setColor(shade(color,.72f));c.drawRoundRect(new RectF(39,22,67,25),2,2,p);
                p.setColor(Color.rgb(226,220,190));c.drawCircle(84,35,1.5f,p);
            } else if(lane==1) {
                p.setStrokeWidth(1.7f);p.setColor(ink);
                c.drawLine(28,45,15,60,p);c.drawLine(15,60,34,56,p);
                c.drawLine(70,43,84,55,p);c.drawLine(84,55,69,51,p);
                p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(238,235,205));
                c.drawCircle(24,38,1.8f,p);c.drawCircle(31,36,1.8f,p);c.drawCircle(38,35,1.8f,p);
                p.setColor(Color.argb(170,255,255,255));c.drawOval(new RectF(47,33,53,35),p);
            } else {
                p.setStrokeWidth(1.5f);p.setColor(ink);
                c.drawLine(21,58,25,58,p);c.drawLine(76,58,80,58,p);
                c.drawLine(25,71,76,71,p);c.drawLine(50,50,50,68,p);
                p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(224,218,184));
                c.drawCircle(25,58,2.1f,p);c.drawCircle(76,58,2.1f,p);
                p.setColor(Color.argb(150,255,255,255));c.drawRoundRect(new RectF(57,19,89,20.5f),1,1,p);
            }
            p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
            // Clean comic ink outline; no national flags or symbols.
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);p.setColor(ink);
            if(lane==0)c.drawRoundRect(new RectF(7,39,94,68),13,13,p);
            p.setStyle(Paint.Style.FILL);p.setShader(null);c.restore();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight();
            drawBattlefield(c);
            txt(c,"GENERAL  •  جنيرال",w*.5f,h*.075f,h*.06f,dark,true);
            btn(c,"الإعدادات",w*.015f,h*.025f,w*.17f,h*.105f,dark);
            btn(c,"خروج",w*.83f,h*.025f,w*.985f,h*.105f,Color.rgb(135,48,43));
            if(battleIntro){drawBattleIntro(c);return;}
            if(setup) { drawSetup(c); return; }
            if(mode==0) {
                if(historyScreen){drawHistory(c);return;}
                if(progressScreen){drawProgress(c);return;}
                txt(c,"اختر طريقة اللعب",w*.5f,h*.285f,h*.065f,dark,true);
                btn(c,"لاعبان على نفس الجهاز",w*.07f,h*.35f,w*.47f,h*.49f,blue);
                btn(c,"ضد جنيرال الكمبيوتر",w*.53f,h*.35f,w*.93f,h*.49f,red);
                btn(c,"اللعب عبر الإنترنت",w*.07f,h*.535f,w*.47f,h*.675f,Color.rgb(37,105,160));
                btn(c,"مواجهة عبر Wi-Fi",w*.53f,h*.535f,w*.93f,h*.675f,Color.rgb(52,125,91));
                btn(c,"سجل المباريات",w*.16f,h*.735f,w*.48f,h*.835f,dark);
                btn(c,"لوحة التقدم",w*.52f,h*.735f,w*.84f,h*.835f,Color.rgb(155,105,28));
                txt(c,"الإنترنت: لعب سريع أو دعوة صديق أو رمز  •  Wi-Fi: الشبكة نفسها",w*.5f,h*.91f,h*.024f,dark,false);
                return;
            }
            if(winner!=0 && !bonusAttempt) {
                box(c,w*.12f,h*.19f,w*.88f,h*.80f,Color.rgb(245,249,239));
                String winnerName=winner==1?playerNames[0]:(mode==2?"جنيرال":playerNames[1]);
                float portraitY=h*.39f, portraitR=h*.085f;
                if(mode==2) {
                    boolean generalWon=winner==2;
                    drawGeneralAvatar(c,w*.34f,portraitY,portraitR,generalWon,!generalWon);
                    drawAvatar(c,avatars[0],w*.66f,portraitY,portraitR,!generalWon,generalWon);
                    txt(c,"جنيرال",w*.34f,h*.52f,h*.038f,dark,true);
                    txt(c,playerNames[0],w*.66f,h*.52f,h*.038f,blue,true);
                } else {
                    String loserName=winner==1?playerNames[1]:playerNames[0];
                    int winnerAvatar=winner==1?avatars[0]:avatars[1];
                    int loserAvatar=winner==1?avatars[1]:avatars[0];
                    drawAvatar(c,winnerAvatar,w*.34f,portraitY,portraitR,true);
                    drawAvatar(c,loserAvatar,w*.66f,portraitY,portraitR,false,true);
                    txt(c,winnerName,w*.34f,h*.52f,h*.038f,blue,true);
                    txt(c,loserName,w*.66f,h*.52f,h*.038f,dark,true);
                }
                txt(c,"الفائز: "+winnerName,w*.5f,h*.60f,h*.05f,dark,true);
                if(mode==2 && winner==1)txt(c,"النقاط المكتسبة: "+matchPoints,w*.5f,h*.65f,h*.029f,Color.rgb(35,125,72),true);
                btn(c,"إعادة المحاولة",w*.25f,h*.70f,w*.48f,h*.79f,blue);
                btn(c,"العودة إلى الرئيسية",w*.52f,h*.70f,w*.75f,h*.79f,dark);
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
            float trackL=w*.18f, trackR=w*.82f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.49f,h*.69f};
            txt(c,mode==2?"جنيرال":playerNames[1],w*.08f,h*.19f,h*.035f,red,true);
            txt(c,playerNames[0],w*.92f,h*.19f,h*.035f,blue,true);
            drawAvatar(c,avatars[0],w*.92f,h*.105f,h*.035f,false);
            if(mode==2) drawGeneralAvatar(c,w*.08f,h*.105f,h*.035f,false,false); else drawAvatar(c,avatars[1],w*.08f,h*.105f,h*.035f,false);
            if(openingToss){
                float ds=h*.075f;
                box(c,w*.81f-ds/2,h*.145f,w*.81f+ds/2,h*.145f+ds,red);
                txt(c,String.valueOf(openingRoll2),w*.81f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
                box(c,w*.19f-ds/2,h*.145f,w*.19f+ds/2,h*.145f+ds,blue);
                txt(c,String.valueOf(openingRoll1),w*.19f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
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
                drawWeapon(c,lane,w*.925f,y,blue,false);
                drawWeapon(c,lane,w*.075f,y,red,true);
                if(dead1[lane]) txt(c,"×",w*.925f,y-gap,h*.07f,Color.RED,true);
                if(dead2[lane]) txt(c,"×",w*.075f,y+gap,h*.07f,Color.RED,true);
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
                drawDiceFace(c,diceCx,diceCy,diceSize*.88f,die>0?die:1);
            }
            if(choosing) txt(c,"اختر السلاح",w*.5f,h*.965f,h*.026f,dark,true);
            if(winner!=0) {
                box(c,w*.76f,h*.82f,w*.96f,h*.93f,dark);
                txt(c,"إعادة",w*.86f,h*.89f,h*.035f,Color.WHITE,true);
            }
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
            if(battleIntro)return true;
            float diceSize=h*.14f, diceCx=player==1?w*.80f:w*.20f, diceCy=h*.865f;
            boolean diceTapped=Math.abs(x-diceCx)<=diceSize*.65f && Math.abs(y-diceCy)<=diceSize*.65f;
            if(y>=h*.025f && y<=h*.11f) {
                if(x>=w*.015f && x<=w*.19f)showSettingsDialog();
                else if(x>=w*.81f && x<=w*.985f)showExitConfirmation();
                invalidate();return true;
            }
            if(mode==0) {
                if(historyScreen || progressScreen) { if(x>=w*.40f&&x<=w*.60f&&y>=h*.93f&&y<=h*.995f){historyScreen=false;progressScreen=false;} }
                else if(y>=h*.35f&&y<=h*.49f&&x>=w*.07f&&x<=w*.47f){mode=1;setup=true;setupSide=1;playerNames[0]="أنت";playerNames[1]="اللاعب 2";avatars[0]=0;avatars[1]=1;}
                else if(y>=h*.35f&&y<=h*.49f&&x>=w*.53f&&x<=w*.93f){mode=2;setup=true;setupSide=1;playerNames[0]="أنت";playerNames[1]="اللاعب 2";avatars[0]=0;avatars[1]=1;}
                else if(y>=h*.535f&&y<=h*.675f&&x>=w*.07f&&x<=w*.47f)showOnlineModeDialog();
                else if(y>=h*.535f&&y<=h*.675f&&x>=w*.53f&&x<=w*.93f)showLanModeDialog();
                else if(y>=h*.735f&&y<=h*.835f&&x>=w*.16f&&x<=w*.48f)historyScreen=true;
                else if(y>=h*.735f&&y<=h*.835f&&x>=w*.52f&&x<=w*.84f)progressScreen=true;
                invalidate();return true;
            }
            if(setup) {
                int idx=setupSide-1;
                if(x>=w*.27f && x<=w*.73f && y>=h*.62f && y<=h*.70f) { showNameDialog(); return true; }
                if(mode==2 && x>=w*.16f && x<=w*.86f && y>=h*.775f && y<=h*.835f) {
                    int level=Math.round((x/w-.24f)/.18f);
                    if(level>=0 && level<4) difficulty=level;
                } else if(x>=w*.12f && x<=w*.88f && y>=h*.32f && y<=h*.62f) {
                    if(x>=w*.12f && x<=w*.30f) avatars[idx]=Math.floorMod(avatars[idx]-1,18);
                    else if(x>=w*.70f && x<=w*.88f) avatars[idx]=Math.floorMod(avatars[idx]+1,18);
                } else if(x>=w*.36f && x<=w*.64f && y>=h*.92f && y<=h*.985f) {
                    if(setupSide==1 && mode==1) setupSide=2;
                    else if(mode==3){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;lanOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;if(lanHostPending){lanHostPending=false;startLanHost();}else if(lanJoinAddress!=null){String address=lanJoinAddress;lanJoinAddress=null;startLanJoin(address);} }
                    else if(mode==4){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;onlineOpeningStarted=false;bonusAttempt=false;perfectWin=false;pointsAwarded=false;matchPoints=0;battleIntro=false;introStep=0;
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
                    if(x>=w*.25f&&x<=w*.48f) resetMatch();
                    else if(x>=w*.52f&&x<=w*.75f) { matchEpoch++; timerlessCloseLan(); timerlessCloseOnline(); lanHostPending=false; lanJoinAddress=null; mode=0; setup=false; stage=0; winner=0; die=0; choosing=false; openingToss=false; battleIntro=false; progressScreen=false; explosionAt=0; message="اختر طريقة اللعب"; }
                }
                invalidate();return true;
            }
            boolean localRollTurn=(mode==1 || (mode==2 && player==1)
                    || (mode==3 && player==lanLocalSide) || (mode==4 && player==onlineLocalSide));
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
                float ownX=player==1?w*.925f:w*.075f;
                int lane=-1;
                float[] weaponRows={h*.30f,h*.49f,h*.68f};
                // اختيار السلاح بالضغط على رسمه نفسه، وليس على زر يحمل اسمه.
                if(Math.abs(x-ownX)<w*.075f) {
                    for(int i=0;i<3;i++) if(Math.abs(y-weaponRows[i])<h*.085f) lane=i;
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