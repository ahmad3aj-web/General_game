package com.generalgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.text.InputType;
import java.util.Random;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(new GameView());
    }

    private class GameView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final int[] pos1 = {0,0,0}, pos2 = {0,0,0};
        private final boolean[] dead1 = {false,false,false}, dead2 = {false,false,false};
        private final String[] names = {"الدبابة","الطائرة","المدفع"};
        private int mode = 0; // 0 menu, 1 local two-player, 2 vs General, 3 Wi-Fi
        private boolean setup = false;
        private boolean historyScreen = false;
        private boolean recorded = false;
        private int matchEpoch = 0;
        private int setupSide = 1;
        private int difficulty = 1; // 0 beginner, 1 medium, 2 professional, 3 legendary
        private final String[] playerNames = {"أنت","اللاعب 2"};
        private final int[] avatars = {0,1};
        private final int[] avatarColors = {Color.rgb(35,112,205),Color.rgb(205,69,54),Color.rgb(45,145,105),Color.rgb(142,85,180),Color.rgb(225,145,45),Color.rgb(65,115,155)};
        private int stage = 0; // opening toss: 0 or 1; 2 means game
        private int firstRoll = 0, player = 1, die = 0, winner = 0;
        private int openingRoll1 = 0, openingRoll2 = 0;
        private boolean openingToss = false;
        private boolean choosing = false;
        private String message = "اختر طريقة اللعب";
        private float w, h;
        private long explosionAt = 0;
        private float explosionX = 0, explosionY = 0;
        private final int bg = Color.rgb(190,225,165), dark = Color.rgb(36,67,43);
        private final int blue = Color.rgb(35,112,205), red = Color.rgb(205,69,54);
        private final int gold = Color.rgb(245,190,65);
        private LanMatchSession lanSession;
        private int lanLocalSide=1, lanTurnId=0, lanCountdown=30;
        private boolean lanOpeningStarted=false;
        private boolean lanHostPending=false;
        private String lanJoinAddress=null;

        GameView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }

        @Override protected void onDetachedFromWindow() {
            timerlessCloseLan();
            super.onDetachedFromWindow();
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
            box(c,l,t,r,b,color); txt(c,s,(l+r)/2,(t+b)/2+h*.014f,h*.043f,Color.WHITE,true);
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
                    lanSession.beginTurn(lanTurnId,side);invalidate();
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
                        message="لا يوجد سلاح مناسب! الدور ملغي";choosing=false;die=0;invalidate();
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
                    if(mode==3 && winner==0 && side==player){die=0;choosing=false;message="الدور ملغي";postDelayed(()->afterTurn(),650);invalidate();}
                });}
                @Override public void onDisconnected(String reason){runOnUiThread(()->{
                    timerlessCloseLan();message="انقطع الاتصال: "+reason;
                    new AlertDialog.Builder(MainActivity.this).setTitle("انقطع اتصال Wi-Fi").setMessage(reason)
                        .setPositiveButton("العودة للرئيسية",(d,which)->{mode=0;setup=false;invalidate();}).show();
                });}
                @Override public void onProtocolError(String reason){runOnUiThread(()->{message=reason;invalidate();});}
            });
        }
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
        private void drawAvatar(Canvas c,int index,float cx,float cy,float radius,boolean selected,boolean monochrome) {
            int teamColor=avatarColors[index%avatarColors.length];
            if(monochrome)teamColor=grayColor(teamColor);
            int ink=monochrome?Color.rgb(75,75,75):Color.rgb(43,48,49);
            int skin=monochrome?grayColor(Color.rgb(255,218,178)):Color.rgb(255,218,178);
            int hair=monochrome?Color.rgb(90,90,90):new int[]{Color.rgb(54,39,31),Color.rgb(35,34,34),Color.rgb(105,63,35),Color.rgb(47,43,65),Color.rgb(76,48,35),Color.rgb(35,54,65)}[index%6];
            c.save();
            p.setShader(null);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(55,20,30,20));c.drawOval(new RectF(cx-radius*.72f,cy+radius*.68f,cx+radius*.72f,cy+radius*.91f),p);
            p.setColor(selected?gold:ink);c.drawCircle(cx,cy,radius+(selected?h*.009f:h*.004f),p);
            p.setColor(teamColor);c.drawCircle(cx,cy,radius*.94f,p);
            p.setColor(Color.argb(48,255,255,255));c.drawCircle(cx-radius*.30f,cy-radius*.32f,radius*.52f,p);
            // Shoulders and uniform.
            p.setColor(ink);c.drawOval(new RectF(cx-radius*.61f,cy+radius*.38f,cx+radius*.61f,cy+radius*.99f),p);
            p.setColor(shade(teamColor,.78f));c.drawOval(new RectF(cx-radius*.55f,cy+radius*.43f,cx+radius*.55f,cy+radius*.94f),p);
            p.setColor(shade(teamColor,1.24f));c.drawRoundRect(new RectF(cx-radius*.10f,cy+radius*.47f,cx+radius*.10f,cy+radius*.78f),radius*.06f,radius*.06f,p);
            // Neck, ears and face.
            p.setColor(ink);c.drawRoundRect(new RectF(cx-radius*.19f,cy+radius*.20f,cx+radius*.19f,cy+radius*.58f),radius*.10f,radius*.10f,p);
            p.setColor(skin);c.drawRoundRect(new RectF(cx-radius*.16f,cy+radius*.19f,cx+radius*.16f,cy+radius*.53f),radius*.09f,radius*.09f,p);
            p.setColor(ink);c.drawCircle(cx-radius*.39f,cy-radius*.02f,radius*.13f,p);c.drawCircle(cx+radius*.39f,cy-radius*.02f,radius*.13f,p);
            p.setColor(skin);c.drawCircle(cx-radius*.39f,cy-radius*.02f,radius*.085f,p);c.drawCircle(cx+radius*.39f,cy-radius*.02f,radius*.085f,p);
            p.setColor(ink);c.drawOval(new RectF(cx-radius*.36f,cy-radius*.48f,cx+radius*.36f,cy+radius*.37f),p);
            p.setColor(skin);c.drawOval(new RectF(cx-radius*.33f,cy-radius*.43f,cx+radius*.33f,cy+radius*.35f),p);
            // Hair silhouette and individual locks.
            p.setColor(ink);
            Path hairShape=new Path();hairShape.moveTo(cx-radius*.35f,cy-radius*.12f);
            hairShape.quadTo(cx-radius*.52f,cy-radius*.59f,cx-radius*.12f,cy-radius*.57f);
            hairShape.quadTo(cx+radius*.18f,cy-radius*.68f,cx+radius*.36f,cy-radius*.35f);
            hairShape.lineTo(cx+radius*.31f,cy-radius*.20f);
            hairShape.quadTo(cx,cy-radius*.34f,cx-radius*.35f,cy-radius*.12f);hairShape.close();
            c.drawPath(hairShape,p);
            p.setColor(hair);
            Path hairFill=new Path();hairFill.moveTo(cx-radius*.32f,cy-radius*.15f);
            hairFill.quadTo(cx-radius*.43f,cy-radius*.53f,cx-radius*.10f,cy-radius*.53f);
            hairFill.quadTo(cx+radius*.17f,cy-radius*.62f,cx+radius*.32f,cy-radius*.34f);
            hairFill.lineTo(cx+radius*.28f,cy-radius*.22f);
            hairFill.quadTo(cx,cy-radius*.31f,cx-radius*.32f,cy-radius*.15f);hairFill.close();c.drawPath(hairFill,p);
            if(index%3==1){p.setColor(ink);c.drawRoundRect(new RectF(cx-radius*.34f,cy-radius*.28f,cx+radius*.34f,cy-radius*.12f),radius*.08f,radius*.08f,p);p.setColor(hair);c.drawRoundRect(new RectF(cx-radius*.30f,cy-radius*.27f,cx+radius*.30f,cy-radius*.16f),radius*.06f,radius*.06f,p);}
            // Eyebrows, eyes, nose and friendly expression.
            p.setColor(ink);p.setStrokeWidth(Math.max(2,radius*.045f));p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx-radius*.23f,cy-radius*.12f,cx-radius*.10f,cy-radius*.14f,p);
            c.drawLine(cx+radius*.10f,cy-radius*.14f,cx+radius*.23f,cy-radius*.12f,p);
            p.setColor(Color.WHITE);c.drawCircle(cx-radius*.17f,cy-radius*.04f,radius*.075f,p);c.drawCircle(cx+radius*.17f,cy-radius*.04f,radius*.075f,p);
            p.setColor(ink);c.drawCircle(cx-radius*.16f,cy-radius*.035f,radius*.038f,p);c.drawCircle(cx+radius*.16f,cy-radius*.035f,radius*.038f,p);
            p.setColor(shade(skin,.82f));c.drawOval(new RectF(cx-radius*.035f,cy+radius*.01f,cx+radius*.035f,cy+radius*.16f),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,radius*.035f));p.setColor(ink);
            c.drawArc(new RectF(cx-radius*.15f,cy+radius*.11f,cx+radius*.15f,cy+radius*.29f),15,150,false,p);
            p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
            // Small uniform badge.
            p.setColor(Color.argb(210,245,225,160));c.drawCircle(cx+radius*.34f,cy+radius*.60f,radius*.075f,p);
            c.restore();
        }
        private void drawSetup(Canvas c) {
            txt(c,"إعداد اللاعبين",w*.5f,h*.16f,h*.065f,dark,true);
            txt(c,setupSide==1?"اللاعب الأول — الأزرق":"اللاعب الثاني — الأحمر",w*.5f,h*.25f,h*.043f,setupSide==1?blue:red,true);
            int idx=setupSide-1;
            drawAvatar(c,avatars[idx],w*.5f,h*.40f,h*.075f,true);
            box(c,w*.27f,h*.50f,w*.73f,h*.59f,Color.rgb(248,250,239));
            txt(c,playerNames[idx],w*.5f,h*.56f,h*.043f,dark,true);
            txt(c,"اضغط على الاسم لتعديله",w*.5f,h*.65f,h*.028f,dark,false);
            if(mode==2) {
                String[] levels={"مبتدئ","متوسط","محترف","أسطوري"};
                txt(c,"مستوى جنيرال",w*.5f,h*.675f,h*.031f,dark,true);
                for(int i=0;i<4;i++) {
                    float left=w*(.16f+i*.18f);
                    box(c,left,h*.695f,left+w*.16f,h*.755f,difficulty==i?gold:Color.rgb(230,237,218));
                    txt(c,levels[i],left+w*.08f,h*.733f,h*.025f,dark,difficulty==i);
                }
                txt(c,"اختر الشخصية",w*.5f,h*.785f,h*.03f,dark,true);
                for(int i=0;i<avatarColors.length;i++) drawAvatar(c,i,w*(.20f+i*.12f),h*.85f,h*.034f,avatars[idx]==i);
            } else {
                txt(c,"اختر الشخصية",w*.5f,h*.73f,h*.035f,dark,true);
                for(int i=0;i<avatarColors.length;i++) drawAvatar(c,i,w*(.20f+i*.12f),h*.82f,h*.038f,avatars[idx]==i);
            }
            btn(c,setupSide==1 && mode==1?"التالي":"ابدأ المباراة",w*.36f,h*.90f,w*.64f,h*.985f,setupSide==1?blue:red);
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
                item.put("date",new SimpleDateFormat("EEEE dd/MM/yyyy HH:mm",new Locale("ar","JO")).format(new Date()));
                updated.put(item);
                int start=Math.max(0,old.length()-99);
                for(int i=start;i<old.length();i++)updated.put(old.getJSONObject(i));
                getSharedPreferences("GENERAL_STATS",MODE_PRIVATE).edit().putString("matches",updated.toString()).apply();
            } catch(Exception ignored) { }
        }
        private void drawHistory(Canvas c) {
            txt(c,"سجل مباريات GENERAL",w*.5f,h*.16f,h*.06f,dark,true);
            JSONArray data=loadHistory(); int total=data.length(),wins=0,losses=0;
            for(int i=0;i<total;i++)try {
                JSONObject o=data.getJSONObject(i);
                if("جنيرال".equals(o.optString("opponent"))) {
                    if("فوز".equals(o.optString("result")))wins++;
                    else if("خسارة".equals(o.optString("result")))losses++;
                }
            } catch(Exception ignored) { }
            box(c,w*.10f,h*.23f,w*.90f,h*.36f,Color.rgb(245,249,239));
            txt(c,"إجمالي المباريات: "+total,w*.5f,h*.275f,h*.036f,dark,true);
            txt(c,"فوز ضد جنيرال: "+wins+"     خسارة: "+losses,w*.5f,h*.33f,h*.032f,blue,true);
            txt(c,"السجل حسب الخصم",w*.5f,h*.42f,h*.038f,dark,true);
            java.util.LinkedHashMap<String,int[]> byOpponent=new java.util.LinkedHashMap<>();
            for(int i=total-1;i>=0;i--)try {
                JSONObject o=data.getJSONObject(i);
                if((o.optInt("mode",2)==1 || o.optInt("mode",2)==3)
                        && o.has("player1") && o.has("player2")) {
                    String p1=o.optString("player1"), p2=o.optString("player2");
                    int side=o.optInt("winnerSide",0);
                    int[] first=byOpponent.get(p1+" ضد "+p2);
                    if(first==null){first=new int[2];byOpponent.put(p1+" ضد "+p2,first);}
                    int[] second=byOpponent.get(p2+" ضد "+p1);
                    if(second==null){second=new int[2];byOpponent.put(p2+" ضد "+p1,second);}
                    if(side==1){first[0]++;second[1]++;} else if(side==2){first[1]++;second[0]++;}
                } else {
                    String opponent=o.optString("opponent","غير معروف");
                    int[] counts=byOpponent.get(opponent);
                    if(counts==null){counts=new int[2];byOpponent.put(opponent,counts);}
                    String result=o.optString("result");
                    if("فوز".equals(result))counts[0]++; else if("خسارة".equals(result))counts[1]++;
                }
            } catch(Exception ignored) { }
            int row=0;
            for(java.util.Map.Entry<String,int[]> entry:byOpponent.entrySet()) {
                if(row>=4)break;
                float y=h*(.49f+row*.075f);
                txt(c,entry.getKey(),w*.30f,y,h*.029f,dark,true);
                txt(c,"فوز "+entry.getValue()[0]+"  |  خسارة "+entry.getValue()[1],w*.70f,y,h*.029f,blue,false);
                row++;
            }
            if(total==0)txt(c,"لا توجد مباريات مسجلة حتى الآن",w*.5f,h*.58f,h*.032f,dark,false);
            txt(c,"آخر المباريات",w*.5f,h*.81f,h*.034f,dark,true);
            for(int i=0;i<Math.min(3,total);i++)try {
                JSONObject o=data.getJSONObject(total-1-i);
                txt(c,o.optString("winner")+" فاز على "+o.optString("loser"),w*.5f,h*(.855f+i*.04f),h*.025f,dark,false);
            } catch(Exception ignored) { }
            btn(c,"عودة",w*.40f,h*.94f,w*.60f,h*.995f,dark);
        }

        private void resetMatch() {
            matchEpoch++;
            for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}
            stage=0; firstRoll=0; player=1; die=0; winner=0; choosing=false; recorded=false; explosionAt=0;
            openingRoll1=0; openingRoll2=0; openingToss=false;
            if(mode==3 && lanSession!=null && lanSession.isConnected()) {
                lanOpeningStarted=true;
                openingToss=false;
                message="طلب إعادة المباراة — بانتظار موافقة اللاعب الآخر";
                lanSession.requestRematch();
            } else {
                message="استعدوا — النظام يحدد من يبدأ";
                startOpeningToss();
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
                message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب 1 يبدأ"):(mode==2?"جنيرال يبدأ":"اللاعب 2 يبدأ");
                invalidate();
                if(mode==2 && player==2) postDelayed(new Runnable(){ @Override public void run(){ if(epoch==matchEpoch && mode==2 && winner==0 && player==2){computerTurn();invalidate();} }},700);
            }},2600);
        }
        private void finishCheck() {
            int p1Destroyed=0, p2Destroyed=0;
            for(int i=0;i<3;i++) {
                if(dead1[i]) p1Destroyed++;
                if(dead2[i]) p2Destroyed++;
            }
            // الفوز يتحقق عند تدمير سلاحين من أصل ثلاثة لدى الخصم.
            if(p1Destroyed>=2) winner=2;
            if(p2Destroyed>=2) winner=1;
            if(winner!=0){
                die=0;choosing=false;
                if(mode==2) message=winner==1?"انتهت المباراة — أنت الفائز!":"انتهت المباراة — فاز جنيرال!";
                else message="انتهت المباراة — الفائز: "+(winner==1?playerNames[0]:playerNames[1]);
                saveMatchResult();
            }
        }
        private void afterTurn() {
            if(winner!=0)return;
            die=0; choosing=false;
            if(mode==2) {
                player=1;
                message="دورك — اضغط النرد";
            } else {
                player=player==1?2:1;
                if(mode==3 && lanSession!=null){lanTurnId++;lanSession.beginTurn(lanTurnId,player);}
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
                        message="لا يوجد سلاح مناسب! الدور ملغي";
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
            choosing=false;
            final int start=positions(side)[lane];
            final int target=Math.min(15,start+amount);
            message=(side==1?"الأزرق":"الأحمر")+" يطلق...";
            final Runnable[] step=new Runnable[1];
            step[0]=new Runnable(){ @Override public void run(){
                if(epoch!=matchEpoch || winner!=0)return;
                if(positions(side)[lane]<target){
                    positions(side)[lane]++;
                    invalidate();
                    postDelayed(step[0],115);
                } else {
                    if(target>=15){
                        if(side==1){dead2[lane]=true;spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);}
                        else {dead1[lane]=true;spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[lane]);}
                    }
                    finishCheck();
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
            explosionX=x; explosionY=y; explosionAt=System.currentTimeMillis();
            invalidate();
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
            // Vector artwork: hand-inked outlines, soft highlights and a fixed team flag.
            int ink=Color.rgb(42,48,43), highlight=shade(color,1.28f), shadow=shade(color,.58f);
            float sx=w*.00122f, sy=h*.00110f;
            c.save(); c.translate(cx-w*.061f,cy-h*.047f); c.scale(sx,sy);
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
            // Team pennant, always blue for player one and red for player two.
            p.setColor(ink);p.setStrokeWidth(2.3f);c.drawLine(17,17,17,2,p);
            Path flag=new Path();flag.moveTo(18,3);flag.lineTo(37,7);flag.lineTo(31,16);flag.lineTo(18,13);flag.close();
            p.setColor(color);c.drawPath(flag,p);p.setColor(highlight);p.setStrokeWidth(1.4f);c.drawLine(19,5,31,8,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(ink);
            if(lane==0)c.drawRoundRect(new RectF(7,39,94,68),13,13,p);
            p.setStyle(Paint.Style.FILL);p.setShader(null);c.restore();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight();
            p.setShader(new LinearGradient(0,0,0,h*.9f,Color.rgb(205,233,183),Color.rgb(126,177,105),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setColor(Color.rgb(190,220,166));c.drawCircle(w*.10f,h*.08f,h*.15f,p);
            p.setColor(Color.rgb(151,196,125));c.drawOval(new RectF(-w*.12f,h*.12f,w*.48f,h*.36f),p);
            p.setColor(Color.rgb(137,184,111));c.drawOval(new RectF(w*.52f,h*.10f,w*1.12f,h*.34f),p);
            p.setColor(Color.rgb(105,157,91));c.drawRect(0,h*.91f,w,h,p);
            p.setColor(Color.argb(45,255,255,255));
            for(int i=0;i<18;i++)c.drawCircle((i*73%1000)/1000f*w,h*(.22f+(i*37%620)/1000f),h*.0025f,p);
            txt(c,"GENERAL  •  جينيرال",w*.5f,h*.075f,h*.06f,dark,true);
            if(setup) { drawSetup(c); return; }
            if(mode==0) {
                if(historyScreen){drawHistory(c);return;}
                txt(c,"اختر طريقة اللعب",w*.5f,h*.31f,h*.065f,dark,true);
                btn(c,"لاعبان على نفس الجهاز",w*.12f,h*.37f,w*.48f,h*.53f,blue);
                btn(c,"ضد جنيرال الكمبيوتر",w*.52f,h*.37f,w*.88f,h*.53f,red);
                btn(c,"مواجهة عبر Wi-Fi",w*.30f,h*.56f,w*.70f,h*.68f,Color.rgb(52,125,91));
                btn(c,"سجل المباريات",w*.36f,h*.73f,w*.64f,h*.83f,dark);
                txt(c,"جنيرال يلعب وحده ويتخذ قراراته",w*.5f,h*.90f,h*.028f,dark,false);
                return;
            }
            if(winner!=0) {
                box(c,w*.12f,h*.19f,w*.88f,h*.80f,Color.rgb(245,249,239));
                String winnerName=winner==1?playerNames[0]:(mode==2?"جنيرال":playerNames[1]);
                String loserName=winner==1?(mode==2?"جنيرال":playerNames[1]):playerNames[0];
                int winnerAvatar=winner==1?avatars[0]:(mode==2?5:avatars[1]);
                int loserAvatar=winner==1?(mode==2?5:avatars[1]):avatars[0];
                float portraitY=h*.39f, portraitR=h*.085f;
                drawAvatar(c,winnerAvatar,w*.34f,portraitY,portraitR,true);
                drawAvatar(c,loserAvatar,w*.66f,portraitY,portraitR,false,true);
                txt(c,winnerName,w*.34f,h*.52f,h*.038f,blue,true);
                txt(c,loserName,w*.66f,h*.52f,h*.038f,dark,true);
                txt(c,"الفائز: "+winnerName,w*.5f,h*.60f,h*.05f,dark,true);
                btn(c,"إعادة المحاولة",w*.25f,h*.68f,w*.48f,h*.77f,blue);
                btn(c,"العودة إلى الرئيسية",w*.52f,h*.68f,w*.75f,h*.77f,dark);
                return;
            }
            txt(c,message,w*.5f,h*.145f,h*.036f,dark,true);
            if(mode==3 && (lanSession==null || !lanSession.isConnected())) {
                btn(c,"إلغاء الاتصال والعودة",w*.31f,h*.81f,w*.69f,h*.92f,dark);
                return;
            }
            float trackL=w*.18f, trackR=w*.82f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.49f,h*.69f};
            txt(c,mode==2?"جنيرال":playerNames[1],w*.92f,h*.19f,h*.035f,red,true);
            txt(c,playerNames[0],w*.08f,h*.19f,h*.035f,blue,true);
            drawAvatar(c,avatars[0],w*.08f,h*.105f,h*.035f,false);
            drawAvatar(c,mode==2?5:avatars[1],w*.92f,h*.105f,h*.035f,false);
            if(openingToss){
                float ds=h*.075f;
                box(c,w*.19f-ds/2,h*.145f,w*.19f+ds/2,h*.145f+ds,red);
                txt(c,String.valueOf(openingRoll2),w*.19f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
                box(c,w*.81f-ds/2,h*.145f,w*.81f+ds/2,h*.145f+ds,blue);
                txt(c,String.valueOf(openingRoll1),w*.81f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
            }
            for(int lane=0;lane<3;lane++) {
                float y=rows[lane], gap=h*.038f;
                // ستة صفوف واضحة: صفّان متقابلان لكل نوع سلاح.
                // اللاعب الأزرق يساراً يطلق يميناً، والخصم الأحمر يميناً يطلق يساراً.
                float bulletW=trackW/34f, bulletH=h*.024f;
                float leftStart=trackL+bulletW*.65f, rightStart=trackR-bulletW*.65f;
                for(int i=0;i<15;i++) {
                    float bx=leftStart+i*(trackW*.46f/14f);
                    float rx=rightStart-i*(trackW*.46f/14f);
                    boolean leftDead=dead1[lane], rightDead=dead2[lane];
                    drawBullet(c,bx,y-gap,bulletW,bulletH,leftDead?Color.GRAY:blue,!leftDead && i<pos1[lane],true);
                    drawBullet(c,rx,y+gap,bulletW,bulletH,rightDead?Color.GRAY:red,!rightDead && i<pos2[lane],false);
                }
                drawWeapon(c,lane,w*.075f,y,blue,true);
                drawWeapon(c,lane,w*.925f,y,red,false);
                if(dead1[lane]) txt(c,"×",w*.075f,y-gap,h*.07f,Color.RED,true);
                if(dead2[lane]) txt(c,"×",w*.925f,y+gap,h*.07f,Color.RED,true);
                if(choosing && winner==0) {
                    float ownX=player==1?w*.075f:w*.925f;
                    if(canMove(player,lane)) {
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(gold);
                        c.drawCircle(ownX,y,h*.055f,p); p.setStyle(Paint.Style.FILL);
                    }
                }
            }
            drawExplosion(c);
            float diceSize=h*.14f, diceCx=w*.5f, diceCy=h*.865f;
            if((mode==1 || (mode==3 && player==lanLocalSide && lanSession!=null && lanSession.isConnected())) && !choosing && die==0) {
                btn(c,mode==3?("أطلق ("+lanCountdown+")"):"أطلق",w*.39f,h*.80f,w*.61f,h*.93f,player==1?blue:red);
            } else {
                int diceColor=winner!=0?dark:(player==1?blue:red);
                box(c,diceCx-diceSize/2,diceCy-diceSize/2,diceCx+diceSize/2,diceCy+diceSize/2,diceColor);
                txt(c,die==0?"النرد":String.valueOf(die),diceCx,diceCy+h*.018f,diceSize*.48f,Color.WHITE,true);
            }
            if(choosing) txt(c,"اختر السلاح",w*.5f,h*.965f,h*.026f,dark,true);
            if(winner!=0) {
                box(c,w*.76f,h*.82f,w*.96f,h*.93f,dark);
                txt(c,"إعادة",w*.86f,h*.89f,h*.035f,Color.WHITE,true);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY(),by=h*.795f, buttonBottom=h*.935f;
            float diceSize=h*.14f, diceCx=w*.5f, diceCy=h*.865f;
            boolean diceTapped=Math.abs(x-diceCx)<=diceSize*.65f && Math.abs(y-diceCy)<=diceSize*.65f;
            if(mode==0) {
                if(historyScreen) { if(y>=h*.93f)historyScreen=false; }
                else if(y>=h*.37f&&y<=h*.53f){mode=x<w*.5f?1:2;setup=true;setupSide=1;playerNames[0]="أنت";playerNames[1]="اللاعب 2";avatars[0]=0;avatars[1]=1;}
                else if(y>=h*.56f&&y<=h*.68f)showLanModeDialog();
                else if(y>=h*.73f&&y<=h*.84f)historyScreen=true;
                invalidate();return true;
            }
            if(setup) {
                int idx=setupSide-1;
                if(y>=h*.50f && y<=h*.60f) { showNameDialog(); return true; }
                if(mode==2 && y>=h*.695f && y<=h*.76f) {
                    int level=Math.round((x/w-.16f)/.18f);
                    if(level>=0 && level<4) difficulty=level;
                } else if((mode==2 && y>=h*.80f && y<=h*.90f) || (mode!=2 && y>=h*.76f && y<=h*.88f)) {
                    int choice=Math.round((x/w-.20f)/.12f);
                    if(choice>=0 && choice<avatarColors.length) avatars[idx]=choice;
                } else if(y>=h*.89f && y<=h*.995f) {
                    if(setupSide==1 && mode==1) setupSide=2;
                    else if(mode==3){setup=false;matchEpoch++;for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}stage=0;winner=0;die=0;choosing=false;recorded=false;openingToss=false;lanOpeningStarted=false;if(lanHostPending){lanHostPending=false;startLanHost();}else if(lanJoinAddress!=null){String address=lanJoinAddress;lanJoinAddress=null;startLanJoin(address);} }
                    else { setup=false;resetMatch(); }
                }
                invalidate();return true;
            }
            if(openingToss)return true;
            if(mode==3 && (lanSession==null || !lanSession.isConnected())) {
                if(y>=h*.80f && y<=h*.94f) {
                    timerlessCloseLan();lanHostPending=false;lanJoinAddress=null;
                    mode=0;setup=false;message="اختر طريقة اللعب";
                }
                invalidate();return true;
            }
            if(winner!=0) {
                if(y>=h*.68f&&y<=h*.78f) {
                    if(x>=w*.25f&&x<=w*.48f) resetMatch();
                    else if(x>=w*.52f&&x<=w*.75f) { matchEpoch++; timerlessCloseLan(); lanHostPending=false; lanJoinAddress=null; mode=0; setup=false; stage=0; winner=0; die=0; choosing=false; openingToss=false; explosionAt=0; message="اختر طريقة اللعب"; }
                }
                invalidate();return true;
            }
            boolean launchTapped=(mode==1 || (mode==3 && player==lanLocalSide)) && !choosing && die==0 && y>=h*.80f && y<=h*.94f && x>=w*.39f && x<=w*.61f;
            if(!choosing && !diceTapped && !launchTapped)return true;
            if(stage<2 && diceTapped) {
                // قرعة البداية أصبحت تلقائية وتُعرض بجانب كل لاعب.
                return true;
            } else if(!choosing && (diceTapped || launchTapped)) {
                if(mode==2&&player==2){computerTurn();}
                else if(mode==3){if(lanSession!=null)lanSession.rollNow(false);}
                else {
                    die=random.nextInt(6)+1;choosing=true;
                    if(!anyMove(player)){
                        message="لا يوجد سلاح مناسب! الدور ملغي";
                        choosing=false;die=0;invalidate();
                        final int epoch=matchEpoch;
                        postDelayed(new Runnable(){ @Override public void run(){
                            if(epoch!=matchEpoch || winner!=0)return;
                            if(mode==2){
                                player=2;message="دور جنيرال";invalidate();
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
                float ownX=player==1?w*.075f:w*.925f;
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
                        else animateMove(player,lane,die);
                    }
                }
            }
            invalidate();return true;
        }
    }
}