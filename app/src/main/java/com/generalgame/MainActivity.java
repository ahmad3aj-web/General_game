package com.generalgame;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

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
        private int mode = 0; // 0 menu, 1 local two-player, 2 vs General
        private int stage = 0; // opening toss: 0 or 1; 2 means game
        private int firstRoll = 0, player = 1, die = 0, winner = 0;
        private boolean choosing = false;
        private String message = "اختر طريقة اللعب";
        private float w, h;
        private final int bg = Color.rgb(190,225,165), dark = Color.rgb(36,67,43);
        private final int blue = Color.rgb(35,112,205), red = Color.rgb(205,69,54);
        private final int gold = Color.rgb(245,190,65);

        GameView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
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
        private void resetMatch() {
            for(int i=0;i<3;i++){pos1[i]=pos2[i]=0;dead1[i]=dead2[i]=false;}
            stage=0; firstRoll=0; player=1; die=0; winner=0; choosing=false;
            message= mode==2 ? "أنت ضد جنيرال — اضغط لبدء القرعة" : "قرعة البداية: اللاعب 1 يرمي";
        }
        private void finishCheck() {
            boolean p1=dead1[0]&&dead1[1]&&dead1[2];
            boolean p2=dead2[0]&&dead2[1]&&dead2[2];
            if(p1) winner=2;
            if(p2) winner=1;
            if(winner!=0){die=0;choosing=false;message=winner==1?"انتهت المباراة — أنت الفائز!":"انتهت المباراة — فاز جنيرال!";}
        }
        private void afterTurn() {
            if(winner!=0)return;
            die=0; choosing=false;
            if(mode==2) {
                player=1;
                message="دورك — اضغط النرد";
            } else {
                player=player==1?2:1;
                message="دور اللاعب "+player+" — اضغط النرد";
            }
        }
        private void computerTurn() {
            if(mode!=2 || winner!=0 || player!=2)return;
            die=random.nextInt(6)+1;
            int pick=-1;
            // يختار أقرب سلاح متاح للهدف، وإذا لم يناسبه الرقم يختار سلاحاً آخر.
            for(int i=0;i<3;i++) if(canMove(2,i) && (pick<0 || pos2[i]>pos2[pick])) pick=i;
            if(pick>=0) {
                pos2[pick]+=die;
                if(pos2[pick]==15) dead1[pick]=true;
                message="جنيرال رمى "+die+" وحرّك "+names[pick];
                finishCheck();
            } else {
                message="جنيرال لا يملك حركة ممكنة — انتهى دوره";
            }
            if(winner==0){player=1;die=0;choosing=false;message="دورك — اضغط النرد";}
        }
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL);
            if(lane==0) { // tank
                c.drawRoundRect(new RectF(cx-w*.026f,cy-h*.012f,cx+w*.026f,cy+h*.012f),8,8,p);
                c.drawCircle(cx-w*.016f,cy+h*.012f,h*.009f,p);
                c.drawCircle(cx+w*.016f,cy+h*.012f,h*.009f,p);
                c.drawRoundRect(new RectF(cx-w*.012f,cy-h*.026f,cx+w*.012f,cy-h*.006f),6,6,p);
                c.drawRect(cx,cy-h*.02f,cx+w*.03f,cy-h*.014f,p);
            } else if(lane==1) { // plane
                Path a=new Path(); a.moveTo(cx,cy-h*.03f); a.lineTo(cx+w*.012f,cy+h*.004f);
                a.lineTo(cx+w*.04f,cy+h*.02f); a.lineTo(cx+w*.04f,cy+h*.028f);
                a.lineTo(cx+w*.008f,cy+h*.018f); a.lineTo(cx,cy+h*.03f);
                a.lineTo(cx-w*.008f,cy+h*.018f); a.lineTo(cx-w*.04f,cy+h*.028f);
                a.lineTo(cx-w*.04f,cy+h*.02f); a.lineTo(cx-w*.012f,cy+h*.004f); a.close(); c.drawPath(a,p);
            } else { // cannon
                c.drawRoundRect(new RectF(cx-w*.023f,cy-h*.012f,cx+w*.023f,cy+h*.015f),6,6,p);
                c.drawRect(cx-w*.004f,cy-h*.033f,cx+w*.004f,cy-h*.004f,p);
                c.drawCircle(cx-w*.015f,cy+h*.015f,h*.008f,p);
                c.drawCircle(cx+w*.015f,cy+h*.015f,h*.008f,p);
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight(); c.drawColor(bg);
            p.setColor(Color.rgb(220,240,207)); c.drawCircle(w*.1f,h*.08f,h*.15f,p);
            p.setColor(Color.rgb(165,211,140)); c.drawRect(0,h*.91f,w,h,p);
            txt(c,"GENERAL  •  جينيرال",w*.5f,h*.075f,h*.06f,dark,true);
            if(mode==0) {
                txt(c,"اختر طريقة اللعب",w*.5f,h*.31f,h*.065f,dark,true);
                btn(c,"لاعبان على نفس الجهاز",w*.12f,h*.42f,w*.48f,h*.62f,blue);
                btn(c,"ضد جنيرال الكمبيوتر",w*.52f,h*.42f,w*.88f,h*.62f,red);
                txt(c,"جنيرال يلعب وحده ويتخذ قراراته",w*.5f,h*.73f,h*.035f,dark,false);
                return;
            }
            txt(c,message,w*.5f,h*.145f,h*.036f,dark,true);
            float trackL=w*.16f, trackR=w*.84f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.43f,h*.57f,h*.70f,h*.81f,h*.92f};
            for(int side=1;side<=2;side++) for(int lane=0;lane<3;lane++) {
                boolean right=side==1;
                float y=rows[(right?3:0)+lane];
                int color=right?blue:red;
                float iconX=right?w*.93f:w*.07f;
                drawWeapon(c,lane,iconX,y,color);
                txt(c,names[lane],iconX,y+h*.045f,h*.025f,color,true);
                boolean dead=destroyed(side)[lane];
                for(int i=0;i<15;i++) {
                    float x=right ? trackR-(i+1)*(trackW/15f) : trackL+i*(trackW/15f);
                    boolean filled=i<positions(side)[lane];
                    int fill=dead?Color.rgb(130,130,125):(filled?color:Color.rgb(242,247,235));
                    p.setColor(fill); c.drawRoundRect(new RectF(x,y-h*.018f,x+trackW/15f-3,y+h*.018f),6,6,p);
                    if(dead) { txt(c,"×",iconX,y+h*.012f,h*.065f,Color.RED,true); }
                }
            }
            txt(c,mode==2?"جنيرال":"اللاعب 2",w*.08f,h*.20f,h*.035f,red,true);
            txt(c,mode==2?"أنت":"اللاعب 1",w*.92f,h*.62f,h*.035f,blue,true);
            box(c,w*.46f,h*.045f,w*.54f,h*.13f,Color.WHITE);
            txt(c,die==0?"النرد":String.valueOf(die),w*.5f,h*.115f,h*.06f,dark,true);
            float by=h*.935f,bh=h*.055f;
            if(winner!=0) btn(c,"إعادة المباراة",w*.35f,by-h*.055f,w*.65f,by, dark);
            else if(stage<2 || !choosing) btn(c,"ارمِ النرد",w*.39f,by-h*.055f,w*.61f,by,player==1?blue:red);
            else {
                for(int i=0;i<3;i++) btn(c,names[i],w*(.08f+i*.28f),by-h*.055f,w*(.32f+i*.28f),by,canMove(player,i)?(player==1?blue:red):Color.GRAY);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY(),by=h*.88f;
            if(mode==0) {
                if(y>=h*.42f&&y<=h*.62f){mode=x<w*.5f?1:2;resetMatch();if(mode==2)message="أنت ضد جنيرال — اضغط النرد لقرعة البداية";}
                invalidate();return true;
            }
            if(winner!=0) {
                if(y>=by-h*.07f&&y<=by+h*.02f){resetMatch();}
                invalidate();return true;
            }
            if(y<by-h*.07f||y>by+h*.02f)return true;
            if(stage<2) {
                int r=random.nextInt(6)+1;
                if(mode==2) {
                    int ai=random.nextInt(6)+1;
                    player=r>=ai?1:2; stage=2;
                    message="قرعتك "+r+" — قرعة جنيرال "+ai+" — "+(player==1?"أنت تبدأ":"جنيرال يبدأ");
                    if(player==2)computerTurn();
                } else if(stage==0) {
                    firstRoll=r;stage=1;message="نتيجة اللاعب 1: "+r+" — اللاعب 2 يرمي";
                } else {
                    if(r>firstRoll){player=1;stage=2;message="اللاعب 1 يبدأ";}
                    else if(r<firstRoll){player=2;stage=2;message="اللاعب 2 يبدأ";}
                    else {stage=0;firstRoll=0;message="تعادل — أعيدوا القرعة";}
                }
            } else if(!choosing) {
                if(mode==2&&player==2){computerTurn();}
                else {
                    die=random.nextInt(6)+1;choosing=true;
                    if(!anyMove(player)){message="لا توجد حركة ممكنة — انتهى الدور";afterTurn();}
                    else message=(mode==2?"أنت":"اللاعب "+player)+" رمى "+die+" — اختر سلاحاً";
                }
            } else {
                float left=w*.08f,gap=w*.02f,bw=w*.26f;
                int lane=(int)((x-left)/(bw+gap));
                if(lane>=0&&lane<3&&x<=left+lane*(bw+gap)+bw) {
                    if(!canMove(player,lane))message="هذه الحركة غير ممكنة — اختر سلاحاً آخر";
                    else {
                        positions(player)[lane]+=die;
                        if(positions(player)[lane]==15) {
                            if(player==1)dead2[lane]=true;else dead1[lane]=true;
                        }
                        finishCheck();
                        if(winner==0) {
                            if(mode==2){player=2;choosing=false;die=0;computerTurn();}
                            else afterTurn();
                        }
                    }
                }
            }
            invalidate();return true;
        }
    }
}