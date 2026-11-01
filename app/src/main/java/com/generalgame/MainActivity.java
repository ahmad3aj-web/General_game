package com.generalgame;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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
            openingRoll1=0; openingRoll2=0; openingToss=false;
            message="استعدوا — النظام يحدد من يبدأ";
            startOpeningToss();
        }
        private void startOpeningToss() {
            if(mode==0 || winner!=0)return;
            openingToss=true; stage=0; choosing=false; die=0;
            openingRoll1=random.nextInt(6)+1;
            openingRoll2=random.nextInt(6)+1;
            message=openingRoll1==openingRoll2 ? "تعادل — ستُعاد القرعة تلقائياً" : "قرعة البداية";
            invalidate();
            postDelayed(new Runnable(){ @Override public void run(){
                if(!openingToss || mode==0 || winner!=0)return;
                if(openingRoll1==openingRoll2){ startOpeningToss(); return; }
                player=openingRoll1>openingRoll2?1:2;
                openingToss=false; stage=2;
                message=player==1?(mode==2?"أنت تبدأ — دورك":"اللاعب 1 يبدأ"):(mode==2?"جنيرال يبدأ":"اللاعب 2 يبدأ");
                invalidate();
                if(mode==2 && player==2) postDelayed(new Runnable(){ @Override public void run(){ computerTurn(); invalidate(); }},700);
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
                if(pos2[pick]==15) { dead1[pick]=true; spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[pick]); }
                message="جنيرال رمى "+die+" وحرّك "+names[pick];
                finishCheck();
            } else {
                message="جنيرال لا يملك حركة ممكنة — انتهى دوره";
            }
            if(winner==0){player=1;die=0;choosing=false;message="دورك — اضغط النرد";}
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
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color) {
            int darkColor=shade(color,.48f), lightColor=shade(color,1.35f);
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            p.setColor(Color.argb(65,20,35,20));
            c.drawOval(new RectF(cx-w*.047f,cy+h*.026f,cx+w*.047f,cy+h*.043f),p);
            if(lane==0) {
                p.setColor(Color.rgb(42,52,43));
                c.drawRoundRect(new RectF(cx-w*.043f,cy-h*.004f,cx+w*.043f,cy+h*.027f),h*.014f,h*.014f,p);
                for(int i=0;i<5;i++){p.setColor(i%2==0?Color.rgb(135,145,112):Color.rgb(75,88,69));c.drawCircle(cx-w*.031f+i*w*.0155f,cy+h*.012f,h*.009f,p);}
                p.setShader(new LinearGradient(cx,cy-h*.02f,cx,cy+h*.018f,lightColor,color,Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(cx-w*.037f,cy-h*.018f,cx+w*.037f,cy+h*.014f),h*.009f,h*.009f,p);p.setShader(null);
                p.setColor(darkColor);c.drawRoundRect(new RectF(cx-w*.016f,cy-h*.034f,cx+w*.017f,cy-h*.002f),h*.009f,h*.009f,p);
                p.setColor(color);c.drawRoundRect(new RectF(cx-w*.012f,cy-h*.031f,cx+w*.012f,cy-h*.006f),h*.008f,h*.008f,p);
                p.setColor(darkColor);c.drawRoundRect(new RectF(cx+w*.006f,cy-h*.025f,cx+w*.047f,cy-h*.016f),h*.004f,h*.004f,p);
                p.setColor(lightColor);c.drawRect(cx+w*.018f,cy-h*.024f,cx+w*.043f,cy-h*.021f,p);
            } else if(lane==1) {
                p.setColor(darkColor);Path wing=new Path();
                wing.moveTo(cx,cy-h*.009f);wing.lineTo(cx+w*.045f,cy+h*.022f);wing.lineTo(cx+w*.012f,cy+h*.015f);
                wing.lineTo(cx,cy+h*.028f);wing.lineTo(cx-w*.012f,cy+h*.015f);wing.lineTo(cx-w*.045f,cy+h*.022f);wing.close();c.drawPath(wing,p);
                p.setShader(new LinearGradient(cx-w*.04f,cy,cx+w*.04f,cy,lightColor,color,Shader.TileMode.CLAMP));c.drawPath(wing,p);p.setShader(null);
                p.setColor(darkColor);Path body=new Path();body.moveTo(cx,cy-h*.038f);body.quadTo(cx+w*.009f,cy-h*.006f,cx+w*.006f,cy+h*.025f);
                body.lineTo(cx,cy+h*.033f);body.lineTo(cx-w*.006f,cy+h*.025f);body.quadTo(cx-w*.009f,cy-h*.006f,cx,cy-h*.038f);body.close();c.drawPath(body,p);
                p.setColor(lightColor);c.drawLine(cx-w*.002f,cy-h*.027f,cx-w*.001f,cy+h*.019f,p);
                p.setColor(Color.rgb(135,205,220));c.drawOval(new RectF(cx-w*.005f,cy-h*.018f,cx+w*.005f,cy-h*.002f),p);
            } else {
                p.setColor(Color.rgb(48,56,43));c.drawCircle(cx-w*.022f,cy+h*.014f,h*.016f,p);c.drawCircle(cx+w*.022f,cy+h*.014f,h*.016f,p);
                p.setColor(Color.rgb(180,185,155));c.drawCircle(cx-w*.022f,cy+h*.014f,h*.007f,p);c.drawCircle(cx+w*.022f,cy+h*.014f,h*.007f,p);
                p.setShader(new LinearGradient(cx,cy-h*.01f,cx,cy+h*.02f,lightColor,color,Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(cx-w*.027f,cy-h*.009f,cx+w*.027f,cy+h*.016f),h*.006f,h*.006f,p);p.setShader(null);
                p.setColor(darkColor);Path shield=new Path();shield.moveTo(cx-w*.018f,cy-h*.009f);shield.lineTo(cx+w*.018f,cy-h*.009f);
                shield.lineTo(cx+w*.014f,cy+h*.012f);shield.lineTo(cx-w*.014f,cy+h*.012f);shield.close();c.drawPath(shield,p);
                p.setColor(color);c.drawRoundRect(new RectF(cx-w*.007f,cy-h*.031f,cx+w*.007f,cy-h*.003f),h*.004f,h*.004f,p);
                p.setColor(darkColor);c.drawRoundRect(new RectF(cx-w*.004f,cy-h*.041f,cx+w*.004f,cy-h*.025f),h*.003f,h*.003f,p);
            }
            p.setShader(null);p.setStyle(Paint.Style.FILL);
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
            if(mode==0) {
                txt(c,"اختر طريقة اللعب",w*.5f,h*.31f,h*.065f,dark,true);
                btn(c,"لاعبان على نفس الجهاز",w*.12f,h*.42f,w*.48f,h*.62f,blue);
                btn(c,"ضد جنيرال الكمبيوتر",w*.52f,h*.42f,w*.88f,h*.62f,red);
                txt(c,"جنيرال يلعب وحده ويتخذ قراراته",w*.5f,h*.73f,h*.035f,dark,false);
                return;
            }
            if(winner!=0) {
                box(c,w*.20f,h*.25f,w*.80f,h*.78f,Color.rgb(245,249,239));
                txt(c,winner==1?"مبروك! أنت الفائز":"انتهت المباراة — جنيرال فاز",w*.5f,h*.39f,h*.065f,winner==1?blue:red,true);
                txt(c,"تم تدمير سلاحين من أصل ثلاثة",w*.5f,h*.49f,h*.038f,dark,true);
                txt(c,"انتهت المعركة بنجاح",w*.5f,h*.56f,h*.032f,dark,false);
                btn(c,"إعادة المباراة",w*.27f,h*.63f,w*.49f,h*.73f,blue);
                btn(c,"القائمة الرئيسية",w*.52f,h*.63f,w*.74f,h*.73f,dark);
                return;
            }
            txt(c,message,w*.5f,h*.145f,h*.036f,dark,true);
            float trackL=w*.18f, trackR=w*.82f, trackW=trackR-trackL;
            float[] rows={h*.29f,h*.49f,h*.69f};
            txt(c,mode==2?"جنيرال":"اللاعب 2",w*.92f,h*.19f,h*.035f,red,true);
            txt(c,mode==2?"أنت":"اللاعب 1",w*.08f,h*.19f,h*.035f,blue,true);
            if(openingToss){
                float ds=h*.075f;
                box(c,w*.19f-ds/2,h*.145f,w*.19f+ds/2,h*.145f+ds,red);
                txt(c,String.valueOf(openingRoll2),w*.19f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
                box(c,w*.81f-ds/2,h*.145f,w*.81f+ds/2,h*.145f+ds,blue);
                txt(c,String.valueOf(openingRoll1),w*.81f,h*.145f+ds*.72f,ds*.58f,Color.WHITE,true);
            }
            for(int lane=0;lane<3;lane++) {
                float y=rows[lane], gap=h*.012f, laneH=h*.012f;
                // 15 طلقة منفصلة لكل لاعب في كل سلاح، باتجاه الخصم.
                float bulletW=trackW/34f, bulletH=h*.022f;
                float leftStart=trackL+bulletW*.65f, rightStart=trackR-bulletW*.65f;
                for(int i=0;i<15;i++) {
                    float bx=leftStart+i*(trackW*.46f/14f);
                    float rx=rightStart-i*(trackW*.46f/14f);
                    boolean leftDead=dead1[lane], rightDead=dead2[lane];
                    drawBullet(c,bx,y-gap,bulletW,bulletH,leftDead?Color.GRAY:blue,!leftDead && i<pos1[lane],true);
                    drawBullet(c,rx,y+gap,bulletW,bulletH,rightDead?Color.GRAY:red,!rightDead && i<pos2[lane],false);
                }
                drawWeapon(c,lane,w*.075f,y,blue);
                drawWeapon(c,lane,w*.925f,y,red);
                if(dead1[lane]) txt(c,"×",w*.075f,y+h*.012f,h*.07f,Color.RED,true);
                if(dead2[lane]) txt(c,"×",w*.925f,y+h*.012f,h*.07f,Color.RED,true);
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
            int diceColor=winner!=0?dark:(player==1?blue:red);
            box(c,diceCx-diceSize/2,diceCy-diceSize/2,diceCx+diceSize/2,diceCy+diceSize/2,diceColor);
            txt(c,die==0?(stage<2?"ارمِ":"ارمِ"):String.valueOf(die),diceCx,diceCy+h*.018f,diceSize*.48f,Color.WHITE,true);
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
                if(y>=h*.42f&&y<=h*.62f){mode=x<w*.5f?1:2;resetMatch();}
                invalidate();return true;
            }
            if(openingToss)return true;
            if(winner!=0) {
                if(y>=h*.63f&&y<=h*.73f) {
                    if(x>=w*.27f&&x<=w*.49f) resetMatch();
                    else if(x>=w*.52f&&x<=w*.74f) { mode=0; stage=0; winner=0; die=0; choosing=false; message="اختر طريقة اللعب"; }
                }
                invalidate();return true;
            }
            if(!choosing && !diceTapped)return true;
            if(stage<2 && diceTapped) {
                // قرعة البداية أصبحت تلقائية وتُعرض بجانب كل لاعب.
                return true;
            } else if(!choosing && diceTapped) {
                if(mode==2&&player==2){computerTurn();}
                else {
                    die=random.nextInt(6)+1;choosing=true;
                    if(!anyMove(player)){message="لا توجد حركة ممكنة — انتهى الدور";if(mode==2){player=2;computerTurn();}else afterTurn();}
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
                        positions(player)[lane]+=die;
                        if(positions(player)[lane]==15) {
                            if(player==1) { dead2[lane]=true; spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]); } else { dead1[lane]=true; spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[lane]); }
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