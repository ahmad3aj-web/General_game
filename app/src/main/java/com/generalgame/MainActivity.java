package com.generalgame;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
        setContentView(new GameView());
    }

    private class GameView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final int[] pos1 = {0,0,0};
        private final int[] pos2 = {0,0,0};
        private final boolean[] dead1 = {false,false,false};
        private final boolean[] dead2 = {false,false,false};
        private int stage = 0; // 0/1: opening draw, 2: normal turn
        private int firstRoll = 0;
        private int player = 1;
        private int die = 0;
        private boolean choosing = false;
        private String message = "قرعة البداية: اضغط زر النرد";
        private int winner = 0;
        private float w, h;

        private final int bg = Color.rgb(190, 225, 165);
        private final int dark = Color.rgb(36, 67, 43);
        private final int blue = Color.rgb(35, 112, 205);
        private final int red = Color.rgb(205, 69, 54);
        private final int gold = Color.rgb(245, 190, 65);

        GameView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
            p.setColor(color); p.setTextSize(size); p.setFakeBoldText(bold);
            p.setTextAlign(Paint.Align.CENTER); c.drawText(s, x, y, p);
        }
        private void rounded(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setColor(color); c.drawRoundRect(new RectF(l,t,r,b), radius, radius, p);
        }
        private void button(Canvas c, String label, float l, float t, float r, float b, int color) {
            rounded(c,l,t,r,b,18,color);
            text(c,label,(l+r)/2,(t+b)/2+ h*0.012f,h*0.045f,Color.WHITE,true);
        }
        private boolean alive(int side, int lane) {
            return side == 1 ? (!dead1[lane] && !dead2[lane]) : (!dead2[lane] && !dead1[lane]);
        }
        private int[] ownPositions(int side) { return side == 1 ? pos1 : pos2; }
        private int[] enemyPositions(int side) { return side == 1 ? pos2 : pos1; }
        private boolean canMove(int side, int lane) {
            int own = ownPositions(side)[lane];
            return alive(side,lane) && own + die <= 15;
        }
        private boolean anyMove(int side) {
            for (int i=0;i<3;i++) if (canMove(side,i)) return true;
            return false;
        }
        private void nextTurn() {
            player = player == 1 ? 2 : 1;
            die = 0; choosing = false;
            message = "دور اللاعب " + player + " — اضغط النرد";
        }
        private void checkWin() {
            boolean[] enemyDead = player == 1 ? dead2 : dead1;
            if (enemyDead[0] && enemyDead[1] && enemyDead[2]) {
                winner = player;
                message = "فاز اللاعب " + player + "!";
            }
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            w = getWidth(); h = getHeight();
            c.drawColor(bg);
            // gentle sky and field
            p.setColor(Color.rgb(220,240,207)); c.drawCircle(w*0.12f,h*0.08f,h*0.15f,p);
            p.setColor(Color.rgb(165,211,140)); c.drawRect(0,h*0.86f,w,h,p);
            text(c,"GENERAL  •  جينيرال",w*0.5f,h*0.075f,h*0.065f,dark,true);
            text(c,message,w*0.5f,h*0.145f,h*0.038f,dark,true);

            float x0=w*0.235f, cell=w*0.044f, cellW=cell*0.82f;
            float[] ys={h*0.255f,h*0.345f,h*0.435f,h*0.59f,h*0.68f,h*0.77f};
            String[] names={"دبابة","طائرة","مدفع"};
            for(int side=1;side<=2;side++) {
                int[] arr=ownPositions(side);
                int color=side==1?blue:red;
                for(int lane=0;lane<3;lane++) {
                    int row=(side==1?0:3)+lane;
                    float y=ys[row];
                    text(c,(side==1?"1 ":"2 ")+names[lane],w*0.115f,y+h*0.014f,h*0.034f,color,true);
                    boolean disabled=!alive(side,lane);
                    for(int i=0;i<15;i++) {
                        float x=x0+i*cell;
                        boolean filled=side==1 ? i<arr[lane] : i>=15-arr[lane];
                        int fill=disabled?Color.rgb(135,139,132):(filled?color:Color.rgb(241,246,231));
                        p.setColor(fill);
                        c.drawRoundRect(new RectF(x,y-h*0.027f,x+cellW,y+h*0.027f),8,8,p);
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f);
                        p.setColor(disabled?Color.DKGRAY:Color.rgb(112,145,105));
                        c.drawRoundRect(new RectF(x,y-h*0.027f,x+cellW,y+h*0.027f),8,8,p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    if(disabled) text(c,"×",x0+15*cell+w*0.025f,y+h*0.012f,h*0.05f,Color.RED,true);
                }
            }

            // player labels
            rounded(c,w*0.02f,h*0.205f,w*0.19f,h*0.49f,16,Color.argb(45,35,112,205));
            text(c,"اللاعب 1",w*0.105f,h*0.235f,h*0.033f,blue,true);
            rounded(c,w*0.02f,h*0.545f,w*0.19f,h*0.83f,16,Color.argb(45,205,69,54));
            text(c,"اللاعب 2",w*0.105f,h*0.575f,h*0.033f,red,true);

            if(die>0) {
                rounded(c,w*0.83f,h*0.04f,w*0.96f,h*0.16f,18,Color.WHITE);
                text(c,"النرد",w*0.895f,h*0.085f,h*0.027f,dark,true);
                text(c,String.valueOf(die),w*0.895f,h*0.145f,h*0.07f,Color.BLACK,true);
            }

            float by=h*0.88f, bh=h*0.105f;
            if(winner!=0) {
                button(c,"إعادة المباراة",w*0.34f,by,w*0.66f,by+bh,dark);
            } else if(stage<2) {
                button(c,"ارمِ النرد",w*0.39f,by,w*0.61f,by+bh,gold);
            } else if(!choosing) {
                button(c,"ارمِ النرد",w*0.39f,by,w*0.61f,by+bh,player==1?blue:red);
            } else {
                float left=w*0.08f, gap=w*0.018f, bw=w*0.26f;
                for(int i=0;i<3;i++) {
                    int color=canMove(player,i)?(player==1?blue:red):Color.GRAY;
                    button(c,names[i],left+i*(bw+gap),by,left+i*(bw+gap)+bw,by+bh,color);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX(), y=e.getY();
            float by=h*0.88f, bh=h*0.105f;
            if(y<by || y>by+bh) return true;

            if(winner!=0) {
                pos1[0]=pos1[1]=pos1[2]=0; pos2[0]=pos2[1]=pos2[2]=0;
                for(int i=0;i<3;i++){ dead1[i]=false; dead2[i]=false; }
                winner=0; stage=0; firstRoll=0; die=0; choosing=false; player=1;
                message="قرعة البداية: اضغط زر النرد";
                invalidate(); return true;
            }

            if(stage<2) {
                int r=random.nextInt(6)+1;
                if(stage==0) {
                    firstRoll=r; stage=1;
                    message="نتيجة اللاعب 1: "+r+" — اللاعب 2 يرمي الآن";
                } else {
                    if(r>firstRoll) { player=2; stage=2; message="اللاعب 2 يبدأ! اضغط النرد"; }
                    else if(r<firstRoll) { player=1; stage=2; message="اللاعب 1 يبدأ! اضغط النرد"; }
                    else { stage=0; firstRoll=0; message="تعادل في القرعة — أعيدوا الرمي"; }
                }
            } else if(!choosing) {
                die=random.nextInt(6)+1;
                choosing=true;
                if(!anyMove(player)) {
                    message="لا توجد حركة ممكنة — تم إلغاء الدور";
                    nextTurn();
                } else {
                    message="اللاعب "+player+" رمى "+die+" — اختر سلاحاً";
                }
            } else {
                float left=w*0.08f, gap=w*0.018f, bw=w*0.26f;
                int lane=(int)((x-left)/(bw+gap));
                if(lane>=0 && lane<3 && x<=left+lane*(bw+gap)+bw) {
                    if(!canMove(player,lane)) {
                        message="هذه الحركة غير ممكنة — اختر سلاحاً آخر";
                    } else {
                        ownPositions(player)[lane]+=die;
                        if(ownPositions(player)[lane] == 15) {
                            if(player==1) dead2[lane]=true; else dead1[lane]=true;
                        }
                        checkWin();
                        if(winner==0) nextTurn();
                    }
                }
            }
            invalidate();
            return true;
        }
    }
}
