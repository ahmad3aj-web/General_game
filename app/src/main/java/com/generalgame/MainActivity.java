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
            message= mode==2 ? "أنت ضد جنيرال — اضغط لبدء القرعة" : "قرعة البداية: اللاعب 1 يرمي";
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
                if(pos2[pick]==15) { dead1[pick]=true; spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[pick]); }
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
        // Flat, top-down 2D artwork. Gameplay coordinates and rules are unchanged.
        private void drawWeapon(Canvas c,int lane,float cx,float cy,int color,boolean facesRight) {
            final float sx=w*.050f, sy=h*.050f;
            final int outline=Color.rgb(35,45,38);
            final int light=shade(color,1.22f), mid=color, darkColor=shade(color,.62f);
            c.save();
            c.translate(cx,cy);
            c.scale(facesRight?1f:-1f,1f);
            p.setShader(null);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);

            if(lane==0) {
                // Tank: two continuous tracks, six road wheels, hull, turret and cannon.
                p.setStyle(Paint.Style.FILL); p.setColor(outline);
                c.drawRoundRect(new RectF(-sx*.98f,-sy*.57f,sx*.98f,-sy*.29f),sy*.12f,sy*.12f,p);
                c.drawRoundRect(new RectF(-sx*.98f,sy*.29f,sx*.98f,sy*.57f),sy*.12f,sy*.12f,p);
                p.setColor(Color.rgb(116,126,103));
                for(int i=0;i<6;i++) {
                    float wx=-sx*.76f+i*sx*.30f;
                    c.drawCircle(wx,-sy*.43f,sy*.075f,p);
                    c.drawCircle(wx, sy*.43f,sy*.075f,p);
                }
                p.setColor(darkColor);
                c.drawRoundRect(new RectF(-sx*.77f,-sy*.34f,sx*.70f,sy*.34f),sy*.13f,sy*.13f,p);
                p.setColor(mid);
                c.drawRoundRect(new RectF(-sx*.72f,-sy*.30f,sx*.64f,sy*.30f),sy*.11f,sy*.11f,p);
                p.setColor(light);
                c.drawRoundRect(new RectF(-sx*.55f,-sy*.25f,sx*.48f,-sy*.16f),sy*.035f,sy*.035f,p);
                c.drawRoundRect(new RectF(-sx*.55f,sy*.16f,sx*.48f,sy*.25f),sy*.035f,sy*.035f,p);
                p.setColor(outline);
                c.drawRoundRect(new RectF(-sx*.10f,-sy*.11f,sx*.95f,sy*.11f),sy*.07f,sy*.07f,p);
                p.setColor(Color.rgb(65,73,61));
                c.drawCircle(-sx*.12f,0,sy*.29f,p);
                p.setColor(light);
                c.drawCircle(-sx*.12f,0,sy*.235f,p);
                p.setColor(mid);
                c.drawCircle(-sx*.12f,0,sy*.18f,p);
                p.setColor(outline);
                c.drawCircle(-sx*.12f,0,sy*.055f,p);
                p.setColor(Color.rgb(232,213,137));
                c.drawCircle(-sx*.12f,0,sy*.025f,p);
            } else if(lane==1) {
                // Aircraft: clean top-view silhouette with swept wings and tail.
                Path wing=new Path();
                wing.moveTo(sx*1.02f,0); wing.lineTo(sx*.28f,-sy*.20f);
                wing.lineTo(-sx*.28f,-sy*.70f); wing.lineTo(-sx*.47f,-sy*.69f);
                wing.lineTo(-sx*.30f,-sy*.12f); wing.lineTo(-sx*.72f,-sy*.27f);
                wing.lineTo(-sx*.84f,-sy*.18f); wing.lineTo(-sx*.48f,0);
                wing.lineTo(-sx*.84f,sy*.18f); wing.lineTo(-sx*.72f,sy*.27f);
                wing.lineTo(-sx*.30f,sy*.12f); wing.lineTo(-sx*.47f,sy*.69f);
                wing.lineTo(-sx*.28f,sy*.70f); wing.lineTo(sx*.28f,sy*.20f);
                wing.close();
                p.setStyle(Paint.Style.FILL); p.setColor(outline); c.drawPath(wing,p);
                c.save(); c.scale(.94f,.91f); p.setColor(mid); c.drawPath(wing,p); c.restore();
                p.setColor(light);
                c.drawLine(-sx*.28f,-sy*.16f,sx*.42f,-sy*.06f,p);
                c.drawLine(-sx*.28f, sy*.16f,sx*.42f, sy*.06f,p);
                Path fuselage=new Path();
                fuselage.moveTo(sx*1.08f,0); fuselage.quadTo(sx*.58f,-sy*.12f,sx*.10f,-sy*.14f);
                fuselage.lineTo(-sx*.62f,-sy*.11f); fuselage.lineTo(-sx*.80f,0);
                fuselage.lineTo(-sx*.62f,sy*.11f); fuselage.lineTo(sx*.10f,sy*.14f);
                fuselage.quadTo(sx*.58f,sy*.12f,sx*1.08f,0); fuselage.close();
                p.setColor(outline); c.drawPath(fuselage,p);
                c.save(); c.scale(.94f,.82f); p.setColor(light); c.drawPath(fuselage,p); c.restore();
                p.setColor(Color.rgb(190,225,235));
                c.drawOval(new RectF(sx*.10f,-sy*.075f,sx*.40f,sy*.075f),p);
                p.setColor(outline); p.setStrokeWidth(Math.max(1f,sy*.025f));
                c.drawLine(-sx*.53f,0,-sx*.23f,0,p);
            } else {
                // Mobile field gun: carriage, paired wheels, shield and long barrel.
                p.setStyle(Paint.Style.FILL); p.setColor(outline);
                c.drawCircle(-sx*.43f,-sy*.34f,sy*.22f,p);
                c.drawCircle(-sx*.43f, sy*.34f,sy*.22f,p);
                p.setColor(Color.rgb(157,165,145));
                c.drawCircle(-sx*.43f,-sy*.34f,sy*.105f,p);
                c.drawCircle(-sx*.43f, sy*.34f,sy*.105f,p);
                p.setColor(darkColor);
                c.drawRoundRect(new RectF(-sx*.68f,-sy*.22f,sx*.38f,sy*.22f),sy*.10f,sy*.10f,p);
                p.setColor(mid);
                c.drawRoundRect(new RectF(-sx*.62f,-sy*.17f,sx*.30f,sy*.17f),sy*.08f,sy*.08f,p);
                p.setColor(outline);
                Path shield=new Path();
                shield.moveTo(-sx*.05f,-sy*.32f); shield.lineTo(sx*.42f,-sy*.25f);
                shield.lineTo(sx*.52f,0); shield.lineTo(sx*.42f,sy*.25f);
                shield.lineTo(-sx*.05f,sy*.32f); shield.close(); c.drawPath(shield,p);
                p.setColor(light); c.drawPath(shield,p);
                p.setColor(outline);
                c.drawRoundRect(new RectF(sx*.02f,-sy*.075f,sx*1.02f,sy*.075f),sy*.05f,sy*.05f,p);
                p.setColor(Color.rgb(66,72,59));
                c.drawRoundRect(new RectF(sx*.72f,-sy*.045f,sx*1.02f,sy*.045f),sy*.03f,sy*.03f,p);
                p.setColor(Color.rgb(221,207,151));
                c.drawCircle(-sx*.23f,0,sy*.055f,p);
            }
            // Fine dark contour gives the flat artwork a crisp illustrated finish.
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f,sy*.025f));
            p.setColor(Color.argb(190,outline==Color.TRANSPARENT?0:35,45,38));
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            c.restore();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); w=getWidth(); h=getHeight();
            // Flat illustrated battlefield: no perspective, gradients or 3D shading.
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(157,194,126)); c.drawRect(0,0,w,h,p);
            p.setColor(Color.rgb(148,186,116));
            for(int i=0;i<7;i++) {
                float x=(i*173%1000)/1000f*w, y=(i*97%850)/1000f*h;
                c.drawOval(new RectF(x-w*.10f,y-h*.035f,x+w*.10f,y+h*.035f),p);
            }
            p.setColor(Color.argb(28,255,255,255));
            for(int i=0;i<11;i++) c.drawLine(0,h*(.22f+i*.065f),w,h*(.22f+i*.065f),p);
            for(int i=0;i<9;i++) c.drawLine(w*(.08f+i*.105f),h*.20f,w*(.08f+i*.105f),h*.88f,p);
            p.setColor(Color.rgb(112,157,88)); c.drawRect(0,h*.91f,w,h,p);
            p.setColor(Color.argb(45,255,255,255));
            for(int i=0;i<24;i++) c.drawCircle((i*73%1000)/1000f*w,h*(.20f+(i*37%650)/1000f),h*.0022f,p);
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
            txt(c,mode==2?"جنيرال":"اللاعب 2",w*.08f,h*.19f,h*.035f,red,true);
            txt(c,mode==2?"أنت":"اللاعب 1",w*.92f,h*.19f,h*.035f,blue,true);
            for(int lane=0;lane<3;lane++) {
                float y=rows[lane], gap=h*.012f, laneH=h*.012f;
                // مساران مستقلان لكل سلاح: 6 خطوط إجمالاً، واحد لكل لاعب.
                for(int side=1;side<=2;side++) {
                    float lineY=y+(side==1?gap:-gap);
                    int color=side==1?blue:red;
                    boolean dead=destroyed(side)[lane];
                    int progress=positions(side)[lane];
                    p.setColor(Color.rgb(115,145,105));
                    c.drawRoundRect(new RectF(trackL,lineY-laneH,trackR,lineY+laneH),5,5,p);
                    for(int i=0;i<15;i++) {
                        float cell=trackW/15f;
                        float x=side==1?trackR-(i+1)*cell:trackL+i*cell;
                        int fill=dead?Color.rgb(125,125,120):(i<progress?color:Color.rgb(242,247,235));
                        p.setColor(fill);
                        c.drawRoundRect(new RectF(x+1,lineY-laneH+1,x+cell-2,lineY+laneH-1),4,4,p);
                    }
                }
                drawWeapon(c,lane,w*.075f,y,red,true);
                drawWeapon(c,lane,w*.925f,y,blue,false);
                if(dead2[lane]) txt(c,"×",w*.075f,y+h*.012f,h*.07f,Color.RED,true);
                if(dead1[lane]) txt(c,"×",w*.925f,y+h*.012f,h*.07f,Color.RED,true);
                if(choosing && winner==0) {
                    float ownX=player==1?w*.925f:w*.075f;
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
                if(y>=h*.42f&&y<=h*.62f){mode=x<w*.5f?1:2;resetMatch();if(mode==2)message="أنت ضد جنيرال — اضغط النرد لقرعة البداية";}
                invalidate();return true;
            }
            if(winner!=0) {
                if(y>=h*.63f&&y<=h*.73f) {
                    if(x>=w*.27f&&x<=w*.49f) resetMatch();
                    else if(x>=w*.52f&&x<=w*.74f) { mode=0; stage=0; winner=0; die=0; choosing=false; message="اختر طريقة اللعب"; }
                }
                invalidate();return true;
            }
            if(!choosing && !diceTapped)return true;
            if(stage<2 && diceTapped) {
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
            } else if(!choosing && diceTapped) {
                if(mode==2&&player==2){computerTurn();}
                else {
                    die=random.nextInt(6)+1;choosing=true;
                    if(!anyMove(player)){message="لا توجد حركة ممكنة — انتهى الدور";if(mode==2){player=2;computerTurn();}else afterTurn();}
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
                        positions(player)[lane]+=die;
                        if(positions(player)[lane]==15) {
                            if(player==1) { dead2[lane]=true; spawnExplosion(w*.075f,new float[]{h*.29f,h*.49f,h*.69f}[lane]); } else { dead1[lane]=true; spawnExplosion(w*.925f,new float[]{h*.29f,h*.49f,h*.69f}[lane]); }
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