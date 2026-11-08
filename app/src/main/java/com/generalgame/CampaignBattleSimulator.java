package com.generalgame;

import java.util.Random;

/**
 * Headless campaign battle resolver. Uses the same 3-lane, 15-hit,
 * two-of-three weapon win condition and the campaign General card set.
 * It never touches the player's persistent card inventory.
 */
final class CampaignBattleSimulator {
    static final class Result {
        final boolean generalWon;
        final int turns;
        final int generalCardsUsed;
        final int defenderCardsUsed;
        Result(boolean won,int turns,int generalUsed,int defenderUsed) {
            this.generalWon=won;this.turns=turns;
            this.generalCardsUsed=generalUsed;this.defenderCardsUsed=defenderUsed;
        }
    }

    private static final int REPAIR=0, DOUBLE_DICE=1, CYBER=2, SHIELD=3,
            AIRSTRIKE=4, SMOKE=5, SUPPLY=6, RADAR=7, MINEFIELD=8, DETECTOR=9, CARD_COUNT=10;
    private final Random random;
    private final int difficulty;
    private final int[][] pos=new int[2][3];
    private final boolean[][] dead=new boolean[2][3];
    private final boolean[][] cyber=new boolean[2][3];
    private final int[][] cyberTurns=new int[2][3];
    private final boolean[][] shield=new boolean[2][3];
    private final int[][] shieldTurns=new int[2][3];
    private final boolean[][] smoke=new boolean[2][3];
    private final int[][] smokeTurns=new int[2][3];
    private final boolean[][] cards=new boolean[2][CARD_COUNT];
    private final boolean[][] used=new boolean[2][CARD_COUNT];
    private int mineSide=-1,mineLane=-1,mineTurns=0;
    private int radarOwner=-1;
    private int turns=0;

    private CampaignBattleSimulator(Random r,int level) {
        random=r;difficulty=Math.max(0,Math.min(3,level));
        dealCards(1,difficulty);
        // The defending force receives an equal-size virtual hand. It is not
        // deducted from the player's saved inventory.
        dealCards(0,difficulty);
    }

    static Result play(int difficulty,Random random) {
        CampaignBattleSimulator game=new CampaignBattleSimulator(random,difficulty);
        return game.run();
    }

    private void dealCards(int side,int count) {
        int[] pool=new int[CARD_COUNT];
        for(int i=0;i<CARD_COUNT;i++)pool[i]=i;
        for(int i=CARD_COUNT-1;i>0;i--) {
            int j=random.nextInt(i+1),t=pool[i];pool[i]=pool[j];pool[j]=t;
        }
        for(int i=0;i<count;i++)cards[side][pool[i]]=true;
    }

    private Result run() {
        int tossA,tossB;
        do { tossA=random.nextInt(6)+1;tossB=random.nextInt(6)+1; } while(tossA==tossB);
        int side=tossA>tossB?1:0;
        while(turns<700) {
            turns++;
            useBeforeRoll(side);
            if(winnerResolved())break;
            int die=random.nextInt(6)+1;
            int lane=chooseDoubleDiceLane(side,die);
            if(lane>=0 && take(side,DOUBLE_DICE))die*=2;
            die=useSupplyAfterRoll(side,die);
            int target=chooseLane(side,die);
            if(target>=0)attack(side,target,die);
            finishTurn(side);
            if(winnerResolved())break;
            side=1-side;
        }
        int winsGeneral=0,winsDefender=0,resolved=0;
        for(int i=0;i<3;i++) {
            if(dead[0][i]&&!dead[1][i]){winsGeneral++;resolved++;}
            else if(dead[1][i]&&!dead[0][i]){winsDefender++;resolved++;}
        }
        boolean generalWon=winsGeneral>=2 || (winsGeneral>winsDefender);
        return new Result(generalWon,turns,countUsed(1),countUsed(0));
    }

    private int countUsed(int side){int n=0;for(boolean b:used[side])if(b)n++;return n;}
    private boolean winnerResolved() {
        int a=0,b=0,r=0;
        for(int i=0;i<3;i++) {
            if(dead[0][i]&&!dead[1][i]){a++;r++;}
            else if(dead[1][i]&&!dead[0][i]){b++;r++;}
        }
        return r==3&&(a>=2||b>=2);
    }
    private boolean has(int side,int card){return cards[side][card]&&!used[side][card];}
    private boolean take(int side,int card){if(!has(side,card))return false;used[side][card]=true;return true;}
    private boolean canMove(int side,int lane,int die) {
        int other=1-side;
        return !dead[side][lane]&&!dead[other][lane]&&!cyber[side][lane]
                &&!smoke[other][lane]&&pos[side][lane]+die<=15;
    }
    private int chooseLane(int side,int die) {
        if(difficulty==0) {
            int count=0;for(int i=0;i<3;i++)if(canMove(side,i,die))count++;
            if(count==0)return -1;
            int pick=random.nextInt(count);
            for(int i=0;i<3;i++)if(canMove(side,i,die)&&pick--==0)return i;
            return -1;
        }
        int pick=-1,best=Integer.MIN_VALUE;
        for(int i=0;i<3;i++)if(canMove(side,i,die)) {
            int score=pos[side][i]*10;
            if(pos[side][i]+die>=15)score+=(difficulty==1?15:(difficulty==2?80:120));
            if(difficulty==3)score+=Math.min(14,die*2);
            if(score>best){best=score;pick=i;}
        }
        return pick;
    }
    private int chooseDoubleDiceLane(int side,int die) {
        if(!has(side,DOUBLE_DICE))return -1;
        int best=-1,progress=-1;
        for(int i=0;i<3;i++)if(canMove(side,i,die)&&pos[side][i]+die*2<=15&&pos[side][i]>progress) {
            best=i;progress=pos[side][i];
        }
        return best;
    }

    private void useBeforeRoll(int side) {
        int other=1-side;
        if(has(side,REPAIR))for(int i=0;i<3;i++)if(dead[side][i]||cyber[side][i]) {
            if(take(side,REPAIR)) {
                boolean destroyed=dead[side][i];dead[side][i]=false;
                if(destroyed){pos[side][i]=0;pos[other][i]=0;}
                cyber[side][i]=false;cyberTurns[side][i]=0;break;
            }
        }
        if(has(side,CYBER)) {
            int target=mostAdvanced(other,false);
            if(target>=0&&pos[other][target]>=5&&!dead[other][target]&&!smoke[other][target]
                    &&take(side,CYBER)){cyber[other][target]=true;cyberTurns[other][target]=2;}
        }
        if(has(side,SHIELD)) {
            int target=mostAdvanced(side,false);
            if(target>=0&&!dead[side][target]&&!shield[side][target]
                    &&(pos[side][target]>=6||pos[other][target]>=8)&&take(side,SHIELD)) {
                shield[side][target]=true;shieldTurns[side][target]=2;
            }
        }
        if(has(side,AIRSTRIKE)) {
            boolean useful=false;for(int i=0;i<3;i++)if(!dead[side][i]&&!dead[other][i]&&pos[side][i]>=7)useful=true;
            if(useful&&take(side,AIRSTRIKE))for(int i=0;i<3;i++)if(!dead[side][i]&&!dead[other][i]) {
                pos[side][i]=Math.min(15,pos[side][i]+3);
                if(pos[side][i]>=15)dead[other][i]=true;
            }
        }
        if(has(side,SMOKE)) {
            int target=mostAdvanced(side,false);
            if(target>=0&&!dead[side][target]&&!smoke[side][target]
                    &&(pos[side][target]>=4||cyber[side][target])&&take(side,SMOKE)) {
                smoke[side][target]=true;smokeTurns[side][target]=3;
            }
        }
        if(has(side,MINEFIELD)&&mineSide<0) {
            int target=mostAdvanced(other,true);
            if(target>=0&&take(side,MINEFIELD)){mineSide=other;mineLane=target;mineTurns=4;}
        }
        if(has(side,DETECTOR)&&mineSide==other&&take(side,DETECTOR)) {
            mineSide=-1;mineLane=-1;mineTurns=0;
        }
        if(has(side,RADAR)&&radarOwner<0&&take(side,RADAR))radarOwner=side;
    }

    private int useSupplyAfterRoll(int side,int die) {
        if(!has(side,SUPPLY))return die;
        for(int bonus=3;bonus>=1;bonus--)for(int i=0;i<3;i++) {
            if(!dead[side][i]&&!dead[1-side][i]&&!cyber[side][i]
                    &&!shield[1-side][i]&&!smoke[1-side][i]
                    &&pos[side][i]+die+bonus<=15&&take(side,SUPPLY))return die+bonus;
        }
        return die;
    }

    private int mostAdvanced(int side,boolean includeDisabled) {
        int target=-1,best=-1;
        for(int i=0;i<3;i++)if(!dead[side][i]&&!smoke[side][i]
                &&(includeDisabled||!cyber[side][i])&&pos[side][i]>best) {
            best=pos[side][i];target=i;
        }
        return target;
    }

    private void attack(int side,int lane,int amount) {
        int other=1-side;
        if(mineSide==side&&mineLane==lane) {
            pos[side][lane]=Math.max(0,pos[side][lane]-3);
            mineSide=-1;mineLane=-1;mineTurns=0;return;
        }
        if(radarOwner==other) {radarOwner=-1;return;}
        if(shield[other][lane])return;
        pos[side][lane]=Math.min(15,pos[side][lane]+amount);
        if(pos[side][lane]>=15)dead[other][lane]=true;
    }

    private void finishTurn(int side) {
        if(mineSide==side&&mineTurns>0&&--mineTurns==0){mineSide=-1;mineLane=-1;}
        int other=1-side;
        for(int lane=0;lane<3;lane++) {
            if(cyber[side][lane]&&--cyberTurns[side][lane]<=0)cyber[side][lane]=false;
            if(shield[other][lane]&&--shieldTurns[other][lane]<=0)shield[other][lane]=false;
            if(smoke[other][lane]&&--smokeTurns[other][lane]<=0)smoke[other][lane]=false;
        }
        if(radarOwner==other)radarOwner=-1;
    }
}
