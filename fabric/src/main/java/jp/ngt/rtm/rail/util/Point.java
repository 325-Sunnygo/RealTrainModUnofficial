package jp.ngt.rtm.rail.util;

import net.minecraft.world.level.Level;

public final class Point {
    private static final int MAX_COUNT = 80;

    public final RailPosition rpRoot;
    public final RailMapSwitch rmMain;
    public final RailMapSwitch rmBranch;
    public final RailDir branchDir;
    public final boolean mainDirIsPositive;
    public final boolean branchDirIsPositive;
    private int moveCount;
    /** 直前 tick の moveCount。描画で partialTick 補間してトング移動を滑らかにするため保持。 */
    private int prevMoveCount;

    public Point(RailPosition railPos, RailMapSwitch rms1, RailMapSwitch rms2) {
        this.rpRoot = railPos;
        boolean mainFirst = rms1.getLength() <= rms2.getLength();
        this.rmMain = mainFirst ? rms1 : rms2;
        this.rmBranch = mainFirst ? rms2 : rms1;
        this.branchDir = getDir(this.rpRoot, this.rmMain, this.rmBranch);
        this.mainDirIsPositive = this.rmMain.getStartRP() == this.rpRoot;
        this.branchDirIsPositive = this.rmBranch.getStartRP() == this.rpRoot;
    }

    public Point(RailPosition railPos, RailMapSwitch rms1) {
        this.rpRoot = railPos;
        this.rmMain = rms1;
        this.rmBranch = null;
        this.branchDir = RailDir.NONE;
        this.mainDirIsPositive = rms1.getStartRP() == railPos;
        this.branchDirIsPositive = false;
    }

    private static RailDir getDir(RailPosition root, RailMapSwitch rms1, RailMapSwitch rms2) {
        RailPosition rp1 = rms1.getStartRP() == root ? rms1.getEndRP() : rms1.getStartRP();
        RailPosition rp2 = rms2.getStartRP() == root ? rms2.getEndRP() : rms2.getStartRP();
        return root.getDir(rp1, rp2);
    }

    public void onUpdate(Level level) {
        if (level == null) {
            return;
        }
        // 描画の partialTick 補間用に、この tick の変化前の値を控えておく。
        this.prevMoveCount = this.moveCount;
        boolean powered = this.rpRoot.checkRSInput(level);
        if (powered) {
            if (this.moveCount < MAX_COUNT) {
                ++this.moveCount;
            }
        } else if (this.moveCount > 0) {
            --this.moveCount;
        }
    }

    public float getMovement() {
        return (float) this.moveCount / (float) MAX_COUNT;
    }

    /**
     * partialTick 補間したトング移動量 (0.0〜1.0)。
     * 変わらないため、素の #getMovement を描画に使うと 20Hz でカクつく。
     */
    public float getMovement(float partialTick) {
        float t = partialTick < 0.0F ? 0.0F : (partialTick > 1.0F ? 1.0F : partialTick);
        float interp = this.prevMoveCount + (this.moveCount - this.prevMoveCount) * t;
        return interp / (float) MAX_COUNT;
    }

    public RailMap getActiveRailMap(Level level) {
        if (this.branchDir == RailDir.NONE || this.rmBranch == null) {
            return this.rmMain;
        }
        return this.rpRoot.checkRSInput(level) ? this.rmBranch : this.rmMain;
    }

    /**
     * スクリプト互換オーバーロード。
     * SRB3 の描画スクリプトは
     * nearestPoint.getActiveRailMap(world)  // render_SuperRailBuilder3.js:2210
     * と呼ぶが、この world は entity.field_70170_p = jp.ngt.mccompat.WorldCompat
     * であり実 Level ではない。
     */
    public RailMap getActiveRailMap(Object levelLike) {
        return getActiveRailMap(jp.ngt.ngtlib.block.BlockUtil.toLevel(levelLike));
    }
}
