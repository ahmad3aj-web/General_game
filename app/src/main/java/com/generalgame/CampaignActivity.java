package com.generalgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated campaign shell. Campaign progress is stored separately from all
 * existing match settings, history, ranks, cards and online sessions.
 */
public class CampaignActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(new CampaignView());
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==701 && resultCode==RESULT_OK && data!=null && data.getBooleanExtra("campaign_won",false)) {
            int c=data.getIntExtra("campaign_continent",-1);
            int country=data.getIntExtra("campaign_country",-1);
            if(c>=0&&c<4&&country>=0&&country<15) {
                CampaignView view=(CampaignView)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
                view.owners[c][country]=BLUE;
                view.save();
                view.selected=country;
                view.invalidate();
            }
        }
    }

    private static final String PREFS = "GENERAL_CAMPAIGN_V1";
    private static final String[] CONTINENTS = {"أزمارا","ثلجور","فيردان","كاردوم"};
    private static final String[] THEMES = {"الصحراء","الجليد","الغابات","الجبال"};
    private static final int[] CONTINENT_COLORS = {
        Color.rgb(190,126,54), Color.rgb(91,153,190),
        Color.rgb(64,139,91), Color.rgb(128,112,104)
    };
    private static final String[][] COUNTRIES = {
        {"أوران","زاهير","ميراث","كالدار","سيراف","ناروك","أديرا","فالكون","رُهام","تيراز","مازار","أورفين","داريك","سولان","أركان"},
        {"فروستار","نوفيرا","إيسكار","ثلجار","ميرفِن","أورليك","كريونا","فالديس","نورفين","إسكارا","جليدون","فيرسك","ألبورا","كالدين","أزفور"},
        {"فيرال","إيلورا","غريندار","سيلفا","أورميا","تالون","ميريدا","فردان","لوريس","أماريث","زيلفا","رينورا","إيفارا","كاليرا","ثورين"},
        {"كاردون","دراكون","أورغاس","فالدار","ميركون","تيرغال","أركوس","دولمار","غارون","إلدراك","كارثا","رومار","زيركون","أوردام","ثاروس"}
    };
    private static final int BLUE=1, RED=2, NEUTRAL=0;
    private final class CampaignView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        private int screen=0, continent=0, selected=-1;
        private int unlocked=prefs.getInt("unlocked",0);
        private int[][] owners=new int[4][15];
        private float w,h;
        private final RectF[] countryRects=new RectF[15];

        CampaignView() {
            super(CampaignActivity.this);
            for(int c=0;c<4;c++) for(int i=0;i<15;i++)
                owners[c][i]=prefs.getInt("owner_"+c+"_"+i, initialOwner(i));
        }
        private int initialOwner(int i) { return i==0?BLUE:(i==14?RED:NEUTRAL); }
        private void save() {
            SharedPreferences.Editor e=prefs.edit().putInt("unlocked",unlocked);
            for(int c=0;c<4;c++) for(int i=0;i<15;i++) e.putInt("owner_"+c+"_"+i,owners[c][i]);
            e.apply();
        }
        private int colorFor(int owner) { return owner==BLUE?Color.rgb(39,112,205):owner==RED?Color.rgb(202,62,55):Color.rgb(135,139,133); }
        private void text(Canvas c,String value,float x,float y,float size,int color) {
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(true);c.drawText(value,x,y,paint);paint.setFakeBoldText(false);
        }
        private void panel(Canvas c,float l,float t,float r,float b,int color) {
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRoundRect(new RectF(l,t,r,b),h*.018f,h*.018f,paint);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);w=getWidth();h=getHeight();
            c.drawColor(Color.rgb(18,29,29));
            paint.setShader(null);
            text(c,"جنيرال  •  حملة السيطرة على العالم",w*.5f,h*.075f,h*.052f,Color.rgb(244,220,165));
            if(screen==0) drawWorld(c); else drawContinent(c);
        }
        private void drawWorld(Canvas c) {
            text(c,"اختر القارة",w*.5f,h*.17f,h*.038f,Color.WHITE);
            for(int i=0;i<4;i++) {
                float left=w*(.08f+(i%2)*.44f), top=h*(.25f+(i/2)*.30f);
                float right=left+w*.40f,bottom=top+h*.23f;
                panel(c,left,top,right,bottom,i<=unlocked?CONTINENT_COLORS[i]:Color.rgb(65,69,67));
                text(c,CONTINENTS[i],(left+right)/2,top+h*.085f,h*.042f,Color.WHITE);
                text(c,THEMES[i],(left+right)/2,top+h*.145f,h*.026f,Color.WHITE);
                text(c,i<=unlocked?"متاحة":"🔒 مغلقة",(left+right)/2,top+h*.195f,h*.024f,Color.WHITE);
            }
            text(c,"القارات تُفتح بالتتابع بعد إكمال القارة السابقة",w*.5f,h*.90f,h*.025f,Color.LTGRAY);
            panel(c,w*.42f,h*.925f,w*.58f,h*.985f,Color.rgb(95,53,43));
            text(c,"عودة",w*.5f,h*.965f,h*.027f,Color.WHITE);
        }
        private void drawContinent(Canvas c) {
            text(c,CONTINENTS[continent]+"  •  "+THEMES[continent],w*.5f,h*.145f,h*.040f,Color.WHITE);
            text(c,"أزرق: اللاعب     أحمر: جنيرال     رمادي: محايد",w*.5f,h*.205f,h*.023f,Color.LTGRAY);
            float left=w*.08f, top=h*.27f, gapX=w*.012f, gapY=h*.025f;
            float cw=w*.158f,ch=h*.155f;
            for(int i=0;i<15;i++) {
                int row=i/5,col=i%5;
                float x=left+col*(cw+gapX),y=top+row*(ch+gapY);
                countryRects[i]=new RectF(x,y,x+cw,y+ch);
                panel(c,x,y,x+cw,y+ch,colorFor(owners[continent][i]));
                text(c,COUNTRIES[continent][i],x+cw/2,y+ch*.48f,h*.026f,Color.WHITE);
                if(selected==i) {
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.006f);paint.setColor(Color.YELLOW);
                    c.drawRoundRect(countryRects[i],h*.018f,h*.018f,paint);paint.setStyle(Paint.Style.FILL);
                }
            }
            if(selected>=0) {
                String owner=owners[continent][selected]==BLUE?"ملك اللاعب":owners[continent][selected]==RED?"ملك جنيرال":"دولة محايدة";
                panel(c,w*.30f,h*.835f,w*.70f,h*.91f,Color.rgb(232,235,222));
                text(c,COUNTRIES[continent][selected]+"  •  "+owner,w*.5f,h*.88f,h*.027f,Color.rgb(31,52,43));
            }
            panel(c,w*.025f,h*.925f,w*.17f,h*.985f,Color.rgb(75,79,74));
            text(c,"القارات",w*.097f,h*.965f,h*.024f,Color.WHITE);
            panel(c,w*.42f,h*.925f,w*.58f,h*.985f,Color.rgb(95,53,43));
            text(c,"عودة",w*.5f,h*.965f,h*.027f,Color.WHITE);
        }
        private void launchCountryBattle(int country) {
            android.content.Intent battle=new android.content.Intent(CampaignActivity.this,MainActivity.class);
            battle.putExtra("campaign_battle",true);
            battle.putExtra("campaign_continent",continent);
            battle.putExtra("campaign_country",country);
            CampaignActivity.this.startActivityForResult(battle,701);
        }
        private boolean adjacent(int a,int b) {
            int ar=a/5,ac=a%5,br=b/5,bc=b%5;
            return Math.abs(ar-br)+Math.abs(ac-bc)==1;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();
            if(screen==0) {
                if(y>=h*.925f){finish();return true;}
                for(int i=0;i<4;i++) {
                    float l=w*(.08f+(i%2)*.44f),t=h*(.25f+(i/2)*.30f);
                    if(x>=l&&x<=l+w*.40f&&y>=t&&y<=t+h*.23f) {
                        if(i<=unlocked){continent=i;screen=1;selected=-1;invalidate();}
                        else new AlertDialog.Builder(CampaignActivity.this).setMessage("أكمل القارة السابقة لفتح هذه القارة.").setPositiveButton("حسناً",null).show();
                        return true;
                    }
                }
            } else {
                if(y>=h*.925f&&x>=w*.40f&&x<=w*.60f){screen=0;selected=-1;invalidate();return true;}
                if(y>=h*.925f&&x<=w*.18f){screen=0;selected=-1;invalidate();return true;}
                for(int i=0;i<15;i++) if(countryRects[i]!=null&&countryRects[i].contains(x,y)) {
                    selected=i;
                    if(owners[continent][i]!=BLUE) {
                        final int targetCountry=i;
                        boolean canAttack=false;
                        for(int j=0;j<15;j++)if(owners[continent][j]==BLUE&&adjacent(targetCountry,j))canAttack=true;
                        if(canAttack) new AlertDialog.Builder(CampaignActivity.this)
                            .setTitle("الهجوم على "+COUNTRIES[continent][targetCountry])
                            .setMessage("ستبدأ معركة ضد جنيرال للسيطرة على هذه الدولة. هل أنت مستعد؟")
                            .setNegativeButton("إلغاء",null)
                            .setPositiveButton("ابدأ الحرب",(d,which)->launchCountryBattle(targetCountry)).show();
                        else new AlertDialog.Builder(CampaignActivity.this)
                            .setTitle(COUNTRIES[continent][i])
                            .setMessage("لا يمكنك مهاجمة هذه الدولة حالياً. يجب أن تكون مجاورة لدولة تسيطر عليها.")
                            .setPositiveButton("حسناً",null).show();
                    }
                    invalidate();return true;
                }
            }
            return true;
        }
    }
}
