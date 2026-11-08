package com.generalgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.LinearGradient;
import android.graphics.Shader;
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
        private final Region[] countryHitRegions=new Region[15];

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
        private int shade(int color,float factor) {
            return Color.rgb(Math.min(255,Math.max(0,(int)(Color.red(color)*factor))),
                Math.min(255,Math.max(0,(int)(Color.green(color)*factor))),
                Math.min(255,Math.max(0,(int)(Color.blue(color)*factor))));
        }
        private void text(Canvas c,String value,float x,float y,float size,int color) {
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(245,190,65));paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(true);c.drawText(value,x,y,paint);paint.setFakeBoldText(false);
        }
        private void panel(Canvas c,float l,float t,float r,float b,int color) {
            RectF rect=new RectF(l,t,r,b);
            paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawRoundRect(rect,h*.018f,h*.018f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,h*.0035f));paint.setColor(Color.rgb(105,27,48));
            c.drawRoundRect(rect,h*.018f,h*.018f,paint);paint.setStyle(Paint.Style.FILL);
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
        private float[][] countrySites(int continentIndex) {
            float[][] sites=new float[15][2];
            if(continentIndex==1 || continentIndex==2) {
                for(int r=0;r<3;r++) for(int col=0;col<5;col++) {
                    int i=r*5+col;
                    float x=.12f+col*.19f;
                    float y=.20f+r*.30f;
                    float jitter=(float)Math.sin((i+2)*2.13)*.025f;
                    sites[i][0]=x+jitter;sites[i][1]=y+(float)Math.cos(i*1.7)*.025f;
                }
            } else {
                for(int r=0;r<5;r++) for(int col=0;col<3;col++) {
                    int i=r*3+col;
                    float spread=(r==0)?.07f:(r==4)?.045f:(r==1||r==3)?.20f:.26f;
                    sites[i][0]=.5f+(col-1)*spread+(float)Math.sin((i+1)*1.9)*.012f;
                    sites[i][1]=.10f+r*.20f+(float)Math.cos(i*1.3)*.018f;
                }
            }
            return sites;
        }
        private Path continentOutline(RectF b) {
            Path path=new Path();
            float[][] pts;
            if(continent==0) pts=new float[][]{{.43f,.02f},{.61f,.08f},{.75f,.22f},{.71f,.39f},{.64f,.56f},{.56f,.77f},{.46f,.98f},{.35f,.82f},{.27f,.64f},{.18f,.43f},{.25f,.20f}};
            else if(continent==1) pts=new float[][]{{.05f,.25f},{.16f,.12f},{.34f,.16f},{.48f,.04f},{.66f,.10f},{.83f,.17f},{.98f,.30f},{.90f,.48f},{.80f,.60f},{.73f,.78f},{.55f,.92f},{.40f,.80f},{.25f,.74f},{.12f,.56f}};
            else if(continent==2) pts=new float[][]{{.20f,.15f},{.35f,.07f},{.48f,.12f},{.60f,.05f},{.78f,.15f},{.90f,.30f},{.82f,.45f},{.93f,.57f},{.77f,.72f},{.62f,.67f},{.53f,.89f},{.39f,.78f},{.25f,.83f},{.14f,.62f},{.08f,.42f}};
            else pts=new float[][]{{.18f,.05f},{.40f,.08f},{.57f,.17f},{.69f,.27f},{.62f,.40f},{.53f,.49f},{.58f,.61f},{.51f,.76f},{.43f,.98f},{.33f,.83f},{.28f,.66f},{.20f,.52f},{.10f,.34f}};
            path.moveTo(b.left+pts[0][0]*b.width(),b.top+pts[0][1]*b.height());
            for(int i=1;i<pts.length;i++)path.lineTo(b.left+pts[i][0]*b.width(),b.top+pts[i][1]*b.height());
            path.close();return path;
        }
        private java.util.ArrayList<float[]> clipPolygon(java.util.ArrayList<float[]> poly,float nx,float ny,float limit) {
            java.util.ArrayList<float[]> out=new java.util.ArrayList<>();
            if(poly.isEmpty())return out;
            for(int i=0;i<poly.size();i++) {
                float[] a=poly.get(i),z=poly.get((i+1)%poly.size());
                float da=nx*a[0]+ny*a[1]-limit,dz=nx*z[0]+ny*z[1]-limit;
                boolean ina=da<=.00001f,inz=dz<=.00001f;
                if(ina)out.add(new float[]{a[0],a[1]});
                if(ina!=inz) {
                    float t=da/(da-dz);
                    out.add(new float[]{a[0]+(z[0]-a[0])*t,a[1]+(z[1]-a[1])*t});
                }
            }
            return out;
        }
        private Path countryPath(RectF b,float[][] sites,int index) {
            java.util.ArrayList<float[]> poly=new java.util.ArrayList<>();
            poly.add(new float[]{0,0});poly.add(new float[]{1,0});poly.add(new float[]{1,1});poly.add(new float[]{0,1});
            float sx=sites[index][0],sy=sites[index][1];
            for(int j=0;j<sites.length&&!poly.isEmpty();j++)if(j!=index) {
                float nx=2*(sites[j][0]-sx),ny=2*(sites[j][1]-sy);
                float limit=sites[j][0]*sites[j][0]+sites[j][1]*sites[j][1]-sx*sx-sy*sy;
                poly=clipPolygon(poly,nx,ny,limit);
            }
            Path path=new Path();
            if(poly.size()>0) {
                path.moveTo(b.left+poly.get(0)[0]*b.width(),b.top+poly.get(0)[1]*b.height());
                for(int i=1;i<poly.size();i++)path.lineTo(b.left+poly.get(i)[0]*b.width(),b.top+poly.get(i)[1]*b.height());
                path.close();
            }
            return path;
        }
        private void drawContinent(Canvas c) {
            text(c,CONTINENTS[continent]+"  •  "+THEMES[continent],w*.5f,h*.145f,h*.040f,Color.WHITE);
            text(c,"أزرق: أنت     أحمر: العدو (جنيرال)     رمادي: محايد",w*.5f,h*.205f,h*.023f,Color.LTGRAY);
            RectF map=new RectF(w*.075f,h*.225f,w*.925f,h*.835f);
            Path outline=continentOutline(map);
            Region clip=new Region((int)map.left,(int)map.top,(int)map.right,(int)map.bottom);
            Region land=new Region();land.setPath(outline,clip);
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb(145,0,0,0));
            c.save();c.translate(h*.012f,h*.018f);c.drawPath(outline,paint);c.restore();
            paint.setShader(new LinearGradient(map.left,map.top,map.right,map.bottom,
                Color.rgb(89,112,91),Color.rgb(31,55,46),Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);c.drawPath(outline,paint);paint.setShader(null);
            c.save();c.clipPath(outline);
            float[][] sites=countrySites(continent);
            for(int i=0;i<15;i++) {
                Path country=countryPath(map,sites,i);
                Region hit=new Region();hit.setPath(country,land);countryHitRegions[i]=hit;
                paint.setShader(new LinearGradient(map.left,map.top,map.right,map.bottom,
                    shade(colorFor(owners[continent][i]),1.22f),shade(colorFor(owners[continent][i]),.72f),Shader.TileMode.CLAMP));
                paint.setStyle(Paint.Style.FILL);c.drawPath(country,paint);paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,h*.003f));
                paint.setColor(Color.rgb(105,27,48));c.drawPath(country,paint);
                paint.setStyle(Paint.Style.FILL);
                float tx=map.left+sites[i][0]*map.width(),ty=map.top+sites[i][1]*map.height();
                paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(h*.020f);paint.setFakeBoldText(true);
                paint.setColor(Color.rgb(245,190,65));
                c.drawText(COUNTRIES[continent][i],tx,ty,paint);
                if(selected==i) {
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.008f);paint.setColor(Color.rgb(255,219,110));
                    c.drawPath(country,paint);paint.setStyle(Paint.Style.FILL);
                }
            }
            // Relief contours and mountain ridges give the map a raised military-terrain appearance.
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.0022f);paint.setColor(Color.argb(75,236,202,126));
            for(int i=0;i<7;i++) {
                Path ridge=new Path();float yy=map.top+map.height()*(.12f+i*.115f);
                ridge.moveTo(map.left+map.width()*.18f,yy);
                ridge.cubicTo(map.left+map.width()*.34f,yy-h*.045f,map.left+map.width()*.56f,yy+h*.035f,map.left+map.width()*.80f,yy-h*.018f);
                c.drawPath(ridge,paint);
            }
            c.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.009f);paint.setColor(Color.rgb(105,27,48));c.drawPath(outline,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.0025f);paint.setColor(Color.rgb(231,190,105));c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);
            if(selected>=0) {
                String owner=owners[continent][selected]==BLUE?"ملك اللاعب":owners[continent][selected]==RED?"ملك جنيرال":"دولة محايدة";
                panel(c,w*.30f,h*.825f,w*.70f,h*.91f,Color.rgb(49,35,32));
                text(c,COUNTRIES[continent][selected]+"  •  "+owner,w*.5f,h*.88f,h*.027f,Color.rgb(245,190,65));
            }
            panel(c,w*.025f,h*.925f,w*.17f,h*.985f,Color.rgb(75,79,74));
            text(c,"القارات",w*.097f,h*.965f,h*.024f,Color.WHITE);
            panel(c,w*.42f,h*.925f,w*.58f,h*.985f,Color.rgb(95,53,43));
            text(c,"عودة",w*.5f,h*.965f,h*.027f,Color.WHITE);
        }
        private void launchCountryBattle(int country,int owner) {
            android.content.Intent battle=new android.content.Intent(CampaignActivity.this,MainActivity.class);
            battle.putExtra("campaign_battle",true);
            battle.putExtra("campaign_continent",continent);
            battle.putExtra("campaign_country",country);
            battle.putExtra("campaign_general",owner==RED);
            battle.putExtra("campaign_opponent_name",COUNTRIES[continent][country]);
            CampaignActivity.this.startActivityForResult(battle,701);
        }
        private boolean adjacent(int a,int b) {
            // حدود الدول مرسومة بخلايا Voronoi؛ نعتمد الجيران الأقرب هندسياً
            // بدل شبكة الصفوف والأعمدة القديمة حتى تتوافق الهجمات مع شكل الخريطة.
            if(a==b)return false;
            float[][] sites=countrySites(continent);
            float target=(float)Math.hypot(sites[a][0]-sites[b][0],sites[a][1]-sites[b][1]);
            int closer=0;
            for(int k=0;k<sites.length;k++)if(k!=a&&k!=b) {
                float da=(float)Math.hypot(sites[a][0]-sites[k][0],sites[a][1]-sites[k][1]);
                float db=(float)Math.hypot(sites[b][0]-sites[k][0],sites[b][1]-sites[k][1]);
                if(da<target*.72f && db<target*.72f)closer++;
            }
            return closer<=1;
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
                for(int i=0;i<15;i++) if(countryHitRegions[i]!=null&&countryHitRegions[i].contains((int)x,(int)y)) {
                    selected=i;
                    if(owners[continent][i]!=BLUE) {
                        final int targetCountry=i;
                        boolean canAttack=false;
                        for(int j=0;j<15;j++)if(owners[continent][j]==BLUE&&adjacent(targetCountry,j))canAttack=true;
                        if(canAttack) new AlertDialog.Builder(CampaignActivity.this)
                            .setTitle("الهجوم على "+COUNTRIES[continent][targetCountry])
                            .setMessage("ستبدأ معركة للسيطرة على هذه الدولة. هل أنت مستعد؟")
                            .setNegativeButton("إلغاء",null)
                            .setPositiveButton("ابدأ الحرب",(d,which)->launchCountryBattle(targetCountry,owners[continent][targetCountry])).show();
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
