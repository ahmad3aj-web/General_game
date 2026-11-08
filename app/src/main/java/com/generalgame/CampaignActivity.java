package com.generalgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
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
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.TextView;
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
        if(requestCode==701 && resultCode==RESULT_OK && data!=null) {
            int c=data.getIntExtra("campaign_continent",-1);
            int country=data.getIntExtra("campaign_country",-1);
            if(c>=0&&c<4&&country>=0&&country<15) {
                CampaignView view=(CampaignView)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
                boolean defense=data.getBooleanExtra("campaign_defense",false);
                boolean playerWon=data.getBooleanExtra("campaign_won",false);
                if(defense) {
                    if(playerWon) {
                        view.selected=country;
                        view.campaignReport="نجحت في صد هجوم جنيرال على "+COUNTRIES[c][country];
                    } else if(view.isPlayerGate(c,country)) {
                        view.collapseContinent(c);
                    } else {
                        view.captureCountry(c,country,RED,"خسرت معركة الدفاع وانتقلت "+COUNTRIES[c][country]+" إلى جنيرال");
                    }
                } else {
                    if(playerWon) {
                        view.captureCountry(c,country,BLUE,"انتصرت واحتللت "+COUNTRIES[c][country]);
                        if(view.isPlayerGate(c,country)) view.prefs.edit().putBoolean("continent_closed_"+c,false).apply();
                    } else {view.selected=country;view.campaignReport="لم تنجح في احتلال "+COUNTRIES[c][country];}
                    if(!view.isContinentClosed(c)) view.runGeneralCampaignAttacks(c);
                }
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
    static {
        java.util.HashSet<String> uniqueNames=new java.util.HashSet<>();
        for(String[] continentNames:COUNTRIES) for(String countryName:continentNames) {
            if(!uniqueNames.add(countryName)) throw new IllegalStateException("اسم دولة مكرر: "+countryName);
        }
    }
    private final class CampaignView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        private int screen=0, continent=0, selected=-1;
        private int unlocked=prefs.getInt("unlocked",-1);
        private final boolean previewAllContinents=false;
        private int[][] owners=new int[4][15];
        private final long[][] flashUntil=new long[4][15];
        private final int[][] flashFrom=new int[4][15];
        private final int[][] flashTo=new int[4][15];
        private String campaignReport="تمت إعادة ضبط القارات — اختر دولة مجاورة للبدء";
        private int pendingCelebrationContinent=-1;
        private float w,h;
        private final Region[] countryHitRegions=new Region[15];

        CampaignView() {
            super(CampaignActivity.this);
            if(!prefs.getBoolean("campaign_new_start_reset_v3",false)) {
                // Fresh campaign: first continent available, remaining continents locked.
                SharedPreferences.Editor reset=prefs.edit().putInt("unlocked",0);
                for(int c=0;c<4;c++) for(int i=0;i<15;i++)
                    reset.putInt("owner_"+c+"_"+i,initialOwner(i));
                reset.putBoolean("campaign_new_start_reset_v3",true).commit();
                unlocked=0;

                // Reset the player's card stock to exactly two of every type.
                SharedPreferences cards=getSharedPreferences("GENERAL_CARD_INVENTORY",MODE_PRIVATE);
                String[] cardKeys={"count_repair","count_double_dice","count_cyber_warfare","count_shield",
                    "count_airstrike","count_smoke_screen","count_military_supply","count_radar","count_minefield","count_mine_detector"};
                SharedPreferences.Editor cardReset=cards.edit();
                for(String key:cardKeys) cardReset.putInt(key,2);
                cardReset.putString("pending_selection","").putString("active_match_cards","")
                    .putString("used_match_cards","").putBoolean("match_active",false)
                    .putString("rank_claims","").commit();

                // Zero progress points so the player starts at the lowest rank.
                SharedPreferences progress=getSharedPreferences("GENERAL_PROGRESS",MODE_PRIVATE);
                progress.edit().putInt("points",0).putInt("wins",0).putInt("perfectWins",0)
                    .putInt("lastEarned",0).putInt("lastDifficulty",0).commit();
            }
            for(int c=0;c<4;c++) for(int i=0;i<15;i++)
                owners[c][i]=prefs.getInt("owner_"+c+"_"+i, initialOwner(i));
            // Reconcile saved ownership on every campaign entry. This also repairs
            // older saves where all 15 countries were captured but the unlock check
            // was not triggered at the moment of the final battle.
            reconcileUnlockedContinents();
        }
        private void reconcileUnlockedContinents() {
            int completedInOrder=0;
            for(int c=0;c<4;c++) {
                boolean complete=true;
                for(int i=0;i<15;i++) if(owners[c][i]!=BLUE) { complete=false; break; }
                if(!complete) break;
                completedInOrder++;
            }
            if(completedInOrder>unlocked) {
                unlocked=completedInOrder;
                save();
                campaignReport=completedInOrder<4
                    ? "تم التحقق من السيطرة وفتح قارة "+CONTINENTS[completedInOrder]
                    : "مبروك! أكملت حملة السيطرة على العالم";
            }
            // Show the celebration even if an earlier build already repaired the unlock value.
            int completed=completedInOrder-1;
            if(completed>=0 && !prefs.getBoolean("celebration_seen_"+completed,false)) {
                pendingCelebrationContinent=completed;
                post(() -> {
                    if(pendingCelebrationContinent>=0) {
                        int celebrate=pendingCelebrationContinent;
                        pendingCelebrationContinent=-1;
                        showContinentCelebration(celebrate);
                    }
                });
            }
        }
        private static final int BLUE_GATE=0, RED_GATE=14;
        private boolean isPlayerGate(int c,int country) { return c>0 && country==BLUE_GATE; }
        private boolean isGate(int c,int country) { return c==0?country==RED_GATE:(country==BLUE_GATE||country==RED_GATE); }
        private boolean isContinentClosed(int c) { return prefs.getBoolean("continent_closed_"+c,false); }
        private boolean previousContinentComplete(int c) {
            if(c<=0)return true;
            for(int i=0;i<15;i++) if(owners[c-1][i]!=BLUE)return false;
            return true;
        }
        private int initialOwner(int i) { return i==0?BLUE:(i==14?RED:NEUTRAL); }
        private void collapseContinent(int c) {
            if(c<=0||c>=4)return;
            for(int i=0;i<15;i++) if(owners[c][i]==BLUE) {
                flashFrom[c][i]=BLUE;flashTo[c][i]=NEUTRAL;
                flashUntil[c][i]=System.currentTimeMillis()+1500L;
                owners[c][i]=NEUTRAL;
            }
            prefs.edit().putBoolean("continent_closed_"+c,true).apply();
            unlocked=Math.min(unlocked,c-1);
            save();
            continent=c-1;screen=1;selected=-1;
            campaignReport="سقطت البوابة! تمردت أراضي "+CONTINENTS[c]+" وعادت إلى الحياد. احتفظ جنيرال بدوله.";
            invalidate();
        }
        private void showGateWarning(final int c,final int country,final boolean playerAttacking) {
            String body="تتعرض البوابة الحدودية لهجوم، وهي شريان الاتصال بين القارات. احمِ هذه البوابة بكل قوتك! سقوطها لن يعني خسارة موقع عسكري فحسب، بل سيشعل تمردًا واسعًا في البلاد، وستخرج أراضي القارة عن سيطرتك، وتعود الدول التي كسبتها فيها إلى حالة الحياد، بينما يحتفظ جنيرال بالدول التي استولى عليها. خسارة البوابة قد تعني ضياع قارة كاملة!";
            SpannableString styled=new SpannableString(body);
            int[] palette={Color.rgb(39,112,205),Color.rgb(202,62,55),Color.rgb(245,190,65),Color.WHITE};
            for(int i=0;i<styled.length();i++) if(!Character.isWhitespace(styled.charAt(i)))
                styled.setSpan(new ForegroundColorSpan(palette[i%palette.length]),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView message=new TextView(CampaignActivity.this);
            message.setText(styled);message.setTextSize(17);message.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            message.setGravity(Gravity.CENTER);message.setPadding(28,24,28,24);
            new AlertDialog.Builder(CampaignActivity.this).setTitle("تحذير عسكري – بوابة استراتيجية").setView(message)
                .setNegativeButton("إلغاء",null)
                .setPositiveButton(playerAttacking?"متابعة الهجوم":"الاستعداد للدفاع",
                    (d,which)->launchCountryBattle(country,owners[c][country],!playerAttacking)).show();
        }
        private void save() {
            SharedPreferences.Editor e=prefs.edit().putInt("unlocked",unlocked);
            for(int c=0;c<4;c++) for(int i=0;i<15;i++) e.putInt("owner_"+c+"_"+i,owners[c][i]);
            e.apply();
        }
        private int colorFor(int owner) { return owner==BLUE?Color.rgb(39,112,205):owner==RED?Color.rgb(202,62,55):Color.rgb(135,139,133); }
        private int mixColor(int a,int b,float t) {
            t=Math.max(0f,Math.min(1f,t));
            return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),
                (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),
                (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
        }
        private int displayedCountryColor(int c,int country,long now) {
            if(now>=flashUntil[c][country])return colorFor(owners[c][country]);
            long elapsed=1500L-(flashUntil[c][country]-now);
            float phase=(elapsed%300L)/300f;
            float blend=phase<.5f?phase*2f:(1f-phase)*2f;
            return mixColor(colorFor(flashFrom[c][country]),colorFor(flashTo[c][country]),blend);
        }
        private void changeOwnerWithFlash(int c,int country,int newOwner) {
            int oldOwner=owners[c][country];
            if(oldOwner==newOwner)return;
            flashFrom[c][country]=oldOwner;flashTo[c][country]=newOwner;
            flashUntil[c][country]=System.currentTimeMillis()+1500L;
            owners[c][country]=newOwner;save();postInvalidateOnAnimation();
        }
        private void captureCountry(int c,int country,int owner,String report) {
            if(c<0||c>=4||country<0||country>=15)return;
            changeOwnerWithFlash(c,country,owner);
            selected=country;
            campaignReport=report;
            checkContinentCompletion(c);
            invalidate();
        }
        private void checkContinentCompletion(int c) {
            if(c<0||c>=4)return;
            for(int i=0;i<15;i++) if(owners[c][i]!=BLUE)return;
            if(unlocked<c+1 && c<3) {
                unlocked=c+1;
                save();
                campaignReport="مبروك! سيطرت على "+CONTINENTS[c]+" وفتحت قارة "+CONTINENTS[c+1];
                showContinentCelebration(c);
            } else if(c==3) {
                save();
                campaignReport="مبروك! أكملت حملة السيطرة على العالم";
                showContinentCelebration(c);
            }
        }

        private void showContinentCelebration(final int completedContinent) {
            if(completedContinent<0 || completedContinent>=4)return;
            String seenKey="celebration_seen_"+completedContinent;
            if(prefs.getBoolean(seenKey,false))return;
            prefs.edit().putBoolean(seenKey,true).apply();
            final boolean worldComplete=completedContinent==3;
            final String playerName=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE)
                .getString("player_name_1","القائد");
            final int avatarIndex=getSharedPreferences("GENERAL_SETTINGS",MODE_PRIVATE)
                .getInt("player_avatar",0);
            final CelebrationView celebration=new CelebrationView(
                worldComplete?"بطل العالم":"فاتح القارات",
                worldComplete?"أكملت السيطرة على العالم!":"أحسنت! سيطرت على "+CONTINENTS[completedContinent],
                worldComplete?"لا توجد قارات متبقية":"تم فتح "+CONTINENTS[completedContinent+1],
                playerName,avatarIndex,worldComplete);
            new AlertDialog.Builder(CampaignActivity.this)
                .setView(celebration)
                .setPositiveButton(worldComplete?"إنجاز عظيم!":"إلى القارة التالية",null)
                .setCancelable(false)
                .show();
        }

        private final class CelebrationView extends View {
            private final Paint fx=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final String title,subtitle,unlockLine,playerName;
            private final boolean worldComplete;
            private final Bitmap avatar;
            private final Movie victoryMovie;
            private final long started=System.currentTimeMillis();
            private final int[] colors={Color.rgb(255,205,72),Color.rgb(255,92,72),
                Color.rgb(72,190,255),Color.rgb(160,105,255),Color.rgb(95,225,155),Color.WHITE};
            CelebrationView(String title,String subtitle,String unlockLine,String playerName,int avatarIndex,boolean worldComplete) {
                super(CampaignActivity.this);
                this.title=title;this.subtitle=subtitle;this.unlockLine=unlockLine;
                this.playerName=(playerName==null||playerName.trim().isEmpty())?"القائد":playerName.trim();
                this.worldComplete=worldComplete;
                Bitmap loaded=null;
                try {
                    String file=String.format(java.util.Locale.US,"character_%02d.png",Math.floorMod(avatarIndex,18)+1);
                    loaded=BitmapFactory.decodeStream(getAssets().open(file));
                } catch(Exception ignored) {}
                avatar=loaded;
                Movie victory=null;
                try { victory=Movie.decodeStream(getAssets().open("vehicles/Win.gif")); } catch(Exception ignored) {}
                victoryMovie=victory;
                setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            }
            @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec) {
                float density=getResources().getDisplayMetrics().density;
                int desiredWidth=(int)(760*density),desiredHeight=(int)(420*density);
                setMeasuredDimension(resolveSize(desiredWidth,widthMeasureSpec),resolveSize(desiredHeight,heightMeasureSpec));
            }
            private void centered(Canvas c,String value,float x,float y,float size,int color) {
                fx.setShader(null);fx.setStyle(Paint.Style.FILL);fx.setColor(color);
                fx.setTextAlign(Paint.Align.CENTER);fx.setTextSize(size);fx.setFakeBoldText(true);
                c.drawText(value,x,y,fx);fx.setFakeBoldText(false);
            }
            private void burst(Canvas c,float cx,float cy,float radius,int color,float phase) {
                fx.setColor(color);fx.setStrokeWidth(Math.max(2,radius*.018f));
                for(int i=0;i<28;i++) {
                    double a=(Math.PI*2*i)/28.0+phase*.12;
                    float inner=radius*(.16f+.10f*(float)Math.sin(i*2.1+phase));
                    float outer=radius*(.62f+.32f*(float)Math.abs(Math.sin(phase*.8+i)));
                    float x1=cx+(float)Math.cos(a)*inner;
                    float y1=cy+(float)Math.sin(a)*inner;
                    float x2=cx+(float)Math.cos(a)*outer;
                    float y2=cy+(float)Math.sin(a)*outer;
                    fx.setAlpha(225);c.drawLine(x1,y1,x2,y2,fx);
                    fx.setStyle(Paint.Style.FILL);c.drawCircle(x2,y2,Math.max(2,radius*.018f),fx);
                }
                fx.setAlpha(255);
            }
            @Override protected void onDraw(Canvas c) {
                super.onDraw(c);
                float ww=getWidth(),hh=getHeight();
                long elapsed=System.currentTimeMillis()-started;
                c.drawColor(Color.rgb(10,22,24));
                fx.setShader(new LinearGradient(0,0,ww,hh,Color.rgb(20,48,49),Color.rgb(38,25,43),Shader.TileMode.CLAMP));
                c.drawRect(0,0,ww,hh,fx);fx.setShader(null);
                for(int b=0;b<4;b++) {
                    float age=((elapsed+b*720)%2900)/2900f;
                    float radius=Math.min(ww,hh)*(.035f+age*.19f);
                    float x=ww*(.16f+(b%2)*.68f);
                    float y=hh*(.17f+(b/2)*.20f);
                    burst(c,x,y,radius,colors[(b+(int)(elapsed/700))%colors.length],age);
                }
                if(victoryMovie!=null) {
                    int duration=Math.max(1,victoryMovie.duration());
                    victoryMovie.setTime((int)(elapsed%duration));
                    float fw=ww*.43f,fh=hh*.43f;
                    c.save();c.translate((ww-fw)*.5f,hh*.17f);
                    c.scale(fw/Math.max(1,victoryMovie.width()),fh/Math.max(1,victoryMovie.height()));
                    victoryMovie.draw(c,0,0);c.restore();
                }
                fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(Math.max(2,hh*.006f));
                fx.setColor(Color.rgb(226,178,66));
                c.drawRoundRect(new RectF(ww*.035f,hh*.035f,ww*.965f,hh*.965f),hh*.035f,hh*.035f,fx);
                fx.setStyle(Paint.Style.FILL);
                centered(c,worldComplete?"🏆 انتصار أسطوري":"🏅 إنجاز جديد",ww*.5f,hh*.16f,hh*.075f,Color.rgb(255,213,105));
                float cx=ww*.5f,cy=hh*.39f,rad=Math.min(ww,hh)*.125f;
                fx.setColor(Color.rgb(190,137,38));c.drawCircle(cx,cy,rad*1.18f,fx);
                fx.setStyle(Paint.Style.STROKE);fx.setStrokeWidth(Math.max(3,hh*.009f));
                fx.setColor(Color.rgb(255,226,132));c.drawCircle(cx,cy,rad*1.18f,fx);fx.setStyle(Paint.Style.FILL);
                if(avatar!=null) {
                    RectF dst=new RectF(cx-rad*.88f,cy-rad*.98f,cx+rad*.88f,cy+rad*.98f);
                    fx.setFilterBitmap(true);c.drawBitmap(avatar,null,dst,fx);
                } else {
                    fx.setColor(Color.rgb(45,103,139));c.drawCircle(cx,cy,rad*.88f,fx);
                    fx.setColor(Color.rgb(225,190,145));c.drawCircle(cx,cy-rad*.12f,rad*.42f,fx);
                    fx.setColor(Color.rgb(45,55,50));c.drawArc(new RectF(cx-rad*.55f,cy-rad*.70f,cx+rad*.55f,cy+rad*.10f),180,180,true,fx);
                }
                centered(c,playerName,ww*.5f,hh*.62f,hh*.060f,Color.WHITE);
                centered(c,title,ww*.5f,hh*.72f,hh*.060f,Color.rgb(255,210,95));
                centered(c,subtitle,ww*.5f,hh*.81f,hh*.040f,Color.WHITE);
                centered(c,unlockLine,ww*.5f,hh*.89f,hh*.034f,Color.rgb(135,232,170));
                centered(c,"✦  ✦  ✦",ww*.5f,hh*.96f,hh*.040f,Color.rgb(255,211,95));
                postInvalidateDelayed(40);
            }
        }

        private void runGeneralCampaignAttacks(int c) {
            // Every battle consumes only the attacker's turn: General gets one attack,
            // then the turn passes to blue. A blue-owned target requires a real defense.
            ArrayList<Integer> targets=new ArrayList<>();
            for(int target=0;target<15;target++)
                if(owners[c][target]!=RED&&hasAdjacentOwnedCountry(target,RED))
                    targets.add(target);
            if(targets.isEmpty()) {
                campaignReport="جنيرال: لا توجد دولة مجاورة متاحة للهجوم";
                invalidate();
                return;
            }

            java.util.Random random=new java.util.Random();
            int target;
            if(c==1||c==2) {
                int gateTarget=-1;
                for(int candidate:targets) if(candidate==BLUE_GATE){gateTarget=candidate;break;}
                if(gateTarget>=0 && random.nextInt(100)<62) target=gateTarget;
                else {
                    float[][] sites=countrySites(c);
                    float best=Float.MAX_VALUE;int chosen=targets.get(0);
                    for(int candidate:targets) {
                        float dx=sites[candidate][0]-sites[BLUE_GATE][0];
                        float dy=sites[candidate][1]-sites[BLUE_GATE][1];
                        float score=(float)Math.hypot(dx,dy)+(random.nextFloat()*.16f);
                        if(score<best){best=score;chosen=candidate;}
                    }
                    target=chosen;
                }
            } else target=targets.get(random.nextInt(targets.size()));
            if(owners[c][target]==BLUE) {
                campaignReport="استعد للدفاع! هناك هجوم على "+COUNTRIES[c][target];
                selected=target;
                invalidate();
                if(isPlayerGate(c,target)) showGateWarning(c,target,false);
                else launchCountryBattle(target,RED,true);
                return;
            }

            // Neutral countries keep the existing automatic, unseen campaign battle.
            CampaignBattleSimulator.Result result=CampaignBattleSimulator.play(c,random);
            if(result.generalWon) {
                changeOwnerWithFlash(c,target,RED);
                campaignReport="جنيرال: "+COUNTRIES[c][target]+" احتلها";
            } else {
                campaignReport="جنيرال: "+COUNTRIES[c][target]+" صمدت أمام الهجوم";
            }
            invalidate();
        }
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
                RectF tile=new RectF(left,top,right,bottom);
                panel(c,left,top,right,bottom,Color.rgb(43,49,48));
                drawContinentButtonShape(c,i,tile);
                text(c,CONTINENTS[i],(left+right)/2,top+h*.155f,h*.035f,Color.WHITE);
                boolean continentUnlocked=(i<=unlocked)||(i==unlocked+1&&previousContinentComplete(i));
                text(c,continentUnlocked?(i==0?"متاحة — ابدأ حملتك":(isContinentClosed(i)?"مغلقة حتى استعادة البوابة":"متاحة — بوابة الدخول")):"مغلقة حالياً",(left+right)/2,top+h*.202f,h*.021f,
                    continentUnlocked?Color.rgb(125,220,145):Color.LTGRAY);
            }
            text(c,"القارات تُفتح بالتتابع بعد إكمال القارة السابقة",w*.5f,h*.90f,h*.025f,Color.LTGRAY);
            panel(c,w*.42f,h*.925f,w*.58f,h*.985f,Color.rgb(95,53,43));
            text(c,"عودة",w*.5f,h*.965f,h*.027f,Color.WHITE);
        }
        private void drawContinentButtonShape(Canvas c,int index,RectF tile) {
            float[][] points=outlinePoints(index);
            float minX=1,maxX=0,minY=1,maxY=0;
            for(float[] p:points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
            float scale=Math.min(tile.width()*.72f/Math.max(.01f,maxX-minX),tile.height()*.70f/Math.max(.01f,maxY-minY));
            float cx=(tile.left+tile.right)*.5f,cy=tile.top+tile.height()*.43f;
            float ox=cx-(maxX-minX)*scale*.5f,oy=cy-(maxY-minY)*scale*.5f;
            Path shape=new Path();
            for(int p=0;p<points.length;p++){
                float x=ox+(points[p][0]-minX)*scale,y=oy+(points[p][1]-minY)*scale;
                if(p==0)shape.moveTo(x,y);else shape.lineTo(x,y);
            }
            shape.close();
            paint.setShader(null);paint.setStyle(Paint.Style.FILL);
            paint.setColor(shade(CONTINENT_COLORS[index],.58f));c.drawPath(shape,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,h*.004f));
            paint.setColor(shade(CONTINENT_COLORS[index],1.25f));c.drawPath(shape,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(145,12,17,17));
            c.drawCircle(cx,cy,Math.min(tile.width(),tile.height())*.075f,paint);
            if(index!=0) text(c,"🔒",cx,cy+h*.012f,h*.030f,Color.WHITE);
            else text(c,"▶",cx,cy+h*.012f,h*.030f,Color.WHITE);
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
            float[][] edge=outlinePoints();
            float centerX=0,centerY=0;
            for(float[] p:edge){centerX+=p[0];centerY+=p[1];}
            centerX/=edge.length;centerY/=edge.length;
            for(int i=0;i<sites.length;i++) {
                for(int attempt=0;attempt<40&&!insideOutline(sites[i][0],sites[i][1],edge);attempt++) {
                    sites[i][0]=sites[i][0]*.88f+centerX*.12f;
                    sites[i][1]=sites[i][1]*.88f+centerY*.12f;
                }
            }
            return sites;
        }
        private boolean insideOutline(float x,float y,float[][] poly) {
            boolean inside=false;
            for(int i=0,j=poly.length-1;i<poly.length;j=i++) {
                float xi=poly[i][0],yi=poly[i][1],xj=poly[j][0],yj=poly[j][1];
                if(((yi>y)!=(yj>y))&&(x<(xj-xi)*(y-yi)/(yj-yi)+xi))inside=!inside;
            }
            return inside;
        }
        private Path continentOutline(RectF b) {
            float[][] pts=outlinePoints();Path path=new Path();
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
        private float[][] outlinePoints() { return outlinePoints(continent); }
        private float[][] outlinePoints(int continentIndex) {
            if(continentIndex==0)return new float[][]{{.43f,.02f},{.61f,.08f},{.75f,.22f},{.71f,.39f},{.64f,.56f},{.56f,.77f},{.46f,.98f},{.35f,.82f},{.27f,.64f},{.18f,.43f},{.25f,.20f}};
            if(continentIndex==1)return new float[][]{{.05f,.25f},{.16f,.12f},{.34f,.16f},{.48f,.04f},{.66f,.10f},{.83f,.17f},{.98f,.30f},{.90f,.48f},{.80f,.60f},{.73f,.78f},{.55f,.92f},{.40f,.80f},{.25f,.74f},{.12f,.56f}};
            if(continentIndex==2)return new float[][]{{.20f,.15f},{.35f,.07f},{.48f,.12f},{.60f,.05f},{.78f,.15f},{.90f,.30f},{.82f,.45f},{.93f,.57f},{.77f,.72f},{.62f,.67f},{.53f,.89f},{.39f,.78f},{.25f,.83f},{.14f,.62f},{.08f,.42f}};
            return new float[][]{{.18f,.05f},{.40f,.08f},{.57f,.17f},{.69f,.27f},{.62f,.40f},{.53f,.49f},{.58f,.61f},{.51f,.76f},{.43f,.98f},{.33f,.83f},{.28f,.66f},{.20f,.52f},{.10f,.34f}};
        }
        private Path countryPath(RectF b,float[][] sites,int index) {
            float[][] edge=outlinePoints();
            java.util.ArrayList<float[]> poly=new java.util.ArrayList<>();
            for(float[] p:edge)poly.add(new float[]{p[0],p[1]});
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
        private float[] countryLabelPoint(RectF b,float[][] sites,int index) {
            float[][] edge=outlinePoints();
            java.util.ArrayList<float[]> poly=new java.util.ArrayList<>();
            for(float[] p:edge)poly.add(new float[]{p[0],p[1]});
            float sx=sites[index][0],sy=sites[index][1];
            for(int j=0;j<sites.length&&!poly.isEmpty();j++)if(j!=index) {
                float nx=2*(sites[j][0]-sx),ny=2*(sites[j][1]-sy);
                float limit=sites[j][0]*sites[j][0]+sites[j][1]*sites[j][1]-sx*sx-sy*sy;
                poly=clipPolygon(poly,nx,ny,limit);
            }
            if(poly.size()<3)return new float[]{b.left+sx*b.width(),b.top+sy*b.height(),b.width()*.08f};
            double area=0,cx=0,cy=0;float minX=1,maxX=0,minY=1,maxY=0;
            for(int i=0;i<poly.size();i++) {
                float[] p=poly.get(i),q=poly.get((i+1)%poly.size());
                double cross=p[0]*q[1]-q[0]*p[1];area+=cross;
                cx+=(p[0]+q[0])*cross;cy+=(p[1]+q[1])*cross;
                minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);
            }
            if(Math.abs(area)<1e-8)return new float[]{b.left+sx*b.width(),b.top+sy*b.height(),b.width()*.06f};
            float x=(float)(cx/(3*area)),y=(float)(cy/(3*area));
            return new float[]{b.left+x*b.width(),b.top+y*b.height(),(maxX-minX)*b.width()*.76f};
        }
        private void drawContinent(Canvas c) {
            text(c,CONTINENTS[continent]+"  •  "+THEMES[continent],w*.5f,h*.145f,h*.040f,Color.WHITE);
            text(c,"أزرق: أنت     أحمر: العدو (جنيرال)     رمادي: محايد",w*.5f,h*.205f,h*.023f,Color.LTGRAY);
            RectF map=new RectF(w*.035f,h*.225f,w*.965f,h*.835f);
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
            boolean flashing=false;
            for(int i=0;i<15;i++) {
                Path country=countryPath(map,sites,i);
                Region hit=new Region();hit.setPath(country,land);countryHitRegions[i]=hit;
                long now=System.currentTimeMillis();
                if(now<flashUntil[continent][i])flashing=true;
                int countryColor=displayedCountryColor(continent,i,now);
                paint.setShader(new LinearGradient(map.left,map.top,map.right,map.bottom,
                    shade(countryColor,1.22f),shade(countryColor,.72f),Shader.TileMode.CLAMP));
                paint.setStyle(Paint.Style.FILL);c.drawPath(country,paint);paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,h*.003f));
                paint.setColor(Color.rgb(105,27,48));c.drawPath(country,paint);
                paint.setStyle(Paint.Style.FILL);
                float[] label=countryLabelPoint(map,sites,i);
                float tx=label[0],ty=label[1];
                paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(true);
                float labelSize=h*.023f;
                paint.setTextSize(labelSize);
                float measured=paint.measureText(COUNTRIES[continent][i]);
                if(measured>label[2] && measured>0)labelSize*=Math.max(.68f,label[2]/measured);
                paint.setTextSize(labelSize);
                paint.setColor(Color.rgb(255,226,153));
                c.drawText(COUNTRIES[continent][i],tx,ty+labelSize*.34f,paint);
                paint.setFakeBoldText(false);
                if(isGate(continent,i)) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(i==BLUE_GATE&&continent>0?Color.rgb(39,112,205):Color.rgb(202,62,55));
                    c.drawCircle(map.left+sites[i][0]*map.width(),map.top+sites[i][1]*map.height(),h*.012f,paint);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,h*.0025f));
                    paint.setColor(Color.WHITE);
                    c.drawCircle(map.left+sites[i][0]*map.width(),map.top+sites[i][1]*map.height(),h*.016f,paint);
                    paint.setStyle(Paint.Style.FILL);
                }
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
            if(flashing)postInvalidateDelayed(45L);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.009f);paint.setColor(Color.rgb(105,27,48));c.drawPath(outline,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(h*.0025f);paint.setColor(Color.rgb(231,190,105));c.drawPath(outline,paint);paint.setStyle(Paint.Style.FILL);
            if(selected>=0) {
                String owner=owners[continent][selected]==BLUE?"ملك اللاعب":owners[continent][selected]==RED?"ملك جنيرال":"دولة محايدة";
                panel(c,w*.035f,h*.84f,w*.965f,h*.915f,Color.rgb(49,35,32));
                text(c,COUNTRIES[continent][selected]+"  •  "+owner,w*.5f,h*.87f,h*.025f,Color.rgb(245,190,65));
                text(c,campaignReport,w*.5f,h*.902f,h*.019f,Color.WHITE);
            } else text(c,campaignReport,w*.5f,h*.895f,h*.019f,Color.WHITE);
            panel(c,w*.025f,h*.925f,w*.17f,h*.985f,Color.rgb(75,79,74));
            text(c,"القارات",w*.097f,h*.965f,h*.024f,Color.WHITE);
            panel(c,w*.42f,h*.925f,w*.58f,h*.985f,Color.rgb(95,53,43));
            text(c,"عودة",w*.5f,h*.965f,h*.027f,Color.WHITE);
        }
        private void launchCountryBattle(int country,int owner,boolean defense) {
            android.content.Intent battle=new android.content.Intent(CampaignActivity.this,MainActivity.class);
            battle.putExtra("campaign_battle",true);
            battle.putExtra("campaign_continent",continent);
            battle.putExtra("campaign_country",country);
            battle.putExtra("campaign_general",owner==RED);
            battle.putExtra("campaign_defense",defense);
            battle.putExtra("campaign_opponent_name",COUNTRIES[continent][country]);
            CampaignActivity.this.startActivityForResult(battle,701);
        }
        private boolean adjacent(int a,int b) {
            // افحص خط التماس الفعلي بين خليتي الدولتين على الخريطة.
            if(a==b||countryHitRegions[a]==null||countryHitRegions[b]==null)return false;
            float[][] sites=countrySites(continent);
            float dx=sites[b][0]-sites[a][0],dy=sites[b][1]-sites[a][1];
            float len=(float)Math.hypot(dx,dy);
            if(len<.0001f)return false;
            float mx=(sites[a][0]+sites[b][0])*.5f,my=(sites[a][1]+sites[b][1])*.5f;
            float px=-dy/len,py=dx/len;
            float left=w*.035f,top=h*.225f,mapW=w*.93f,mapH=h*.61f;
            for(int n=-100;n<=100;n++) {
                float t=n*.012f;
                float nx=mx+px*t,ny=my+py*t;
                if(nx<0||nx>1||ny<0||ny>1)continue;
                float sx=left+nx*mapW,sy=top+ny*mapH;
                float da=0,db=0;
                for(int k=0;k<sites.length;k++) {
                    float dd=(nx-sites[k][0])*(nx-sites[k][0])+(ny-sites[k][1])*(ny-sites[k][1]);
                    if(k==a)da=dd;if(k==b)db=dd;
                }
                if(Math.abs(da-db)>.0005f)continue;
                boolean nearest=true;
                for(int k=0;k<sites.length;k++)if(k!=a&&k!=b) {
                    float dd=(nx-sites[k][0])*(nx-sites[k][0])+(ny-sites[k][1])*(ny-sites[k][1]);
                    if(dd<Math.min(da,db)-.0005f){nearest=false;break;}
                }
                if(nearest&&(countryHitRegions[a].contains((int)sx,(int)sy)||countryHitRegions[b].contains((int)sx,(int)sy)))return true;
            }
            return false;
        }
        // Identical adjacency rule for the player (BLUE) and جنيرال (RED).
        private boolean hasAdjacentOwnedCountry(int target,int owner) {
            for(int j=0;j<15;j++)
                if(owners[continent][j]==owner&&adjacent(target,j))return true;
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();
            if(screen==0) {
                if(y>=h*.925f){finish();return true;}
                for(int i=0;i<4;i++) {
                    float l=w*(.08f+(i%2)*.44f),t=h*(.25f+(i/2)*.30f);
                    if(x>=l&&x<=l+w*.40f&&y>=t&&y<=t+h*.23f) {
                        boolean canEnter=(i<=unlocked)||(i==unlocked+1&&previousContinentComplete(i));
                        if(canEnter) {
                            continent=i;screen=1;selected=-1;
                            campaignReport=isContinentClosed(i)
                                ?"استعد بوابة الدخول أولاً لفتح القارة من جديد"
                                :(i>0&&owners[i][BLUE_GATE]!=BLUE?"هاجم البوابة الزرقاء لبدء التوسع في "+CONTINENTS[i]:"تابع حملتك في "+CONTINENTS[i]);
                            invalidate();
                        } else {
                            new AlertDialog.Builder(CampaignActivity.this)
                                .setMessage("هذه القارة مغلقة حالياً، أكمل القارة الأولى لفتحها.")
                                .setPositiveButton("حسناً",null).show();
                        }
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
                        boolean gateEntry=isPlayerGate(continent,targetCountry)&&previousContinentComplete(continent);
                        boolean canAttack=gateEntry||hasAdjacentOwnedCountry(targetCountry,BLUE);
                        if(canAttack) {
                            if(isGate(continent,targetCountry)) showGateWarning(continent,targetCountry,true);
                            else new AlertDialog.Builder(CampaignActivity.this)
                                .setTitle("الهجوم على "+COUNTRIES[continent][targetCountry])
                                .setMessage("ستبدأ معركة للسيطرة على هذه الدولة. هل أنت مستعد؟")
                                .setNegativeButton("إلغاء",null)
                                .setPositiveButton("ابدأ الحرب",(d,which)->launchCountryBattle(targetCountry,owners[continent][targetCountry],false)).show();
                        } else new AlertDialog.Builder(CampaignActivity.this)
                            .setTitle(COUNTRIES[continent][i])
                            .setMessage("لا يمكنك مهاجمة هذه الدولة حالياً. يجب أن تكون مجاورة لدولة تسيطر عليها، أو أن تكون بوابة الدخول بعد السيطرة على القارة السابقة.")
                            .setPositiveButton("حسناً",null).show();
                    }
                    invalidate();return true;
                }
            }
            return true;
        }
    }
}