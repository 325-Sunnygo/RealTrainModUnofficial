package jp.ngt.rtm.rail.util;


import jp.ngt.ngtlib.math.NGTMath;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * レールの曲線を構成する点
 * (本家 jp.ngt.rtm.rail.util.RailPosition の忠実移植)
 */
public final class RailPosition {
    /**
     * 補正値、デフォルト長=これ*マーカー間距離最小値
     */
    protected static final float Anchor_Correction_Value = 0.55228475F;//(√(2)-1)*4/3
    /**
     * 向きごとのベジェ曲線開始位置への補正値
     */
    public static final float[][] REVISION = new float[][]{
        {0.0F, -0.5F}, {-0.5F, -0.5F}, {-0.5F, 0.0F}, {-0.5F, 0.499999F},
        {0.0F, 0.499999F}, {0.499999F, 0.499999F}, {0.499999F, 0.0F}, {0.499999F, -0.5F}
    };
    public int blockX;
    public int blockY;
    public int blockZ;
    public final byte switchType;
    public byte direction;
    public byte height;
    public float anchorYaw;
    public float anchorPitch;
    public float anchorLengthHorizontal;
    public float anchorLengthVertical;
    public float cantCenter;
    public float cantEdge;
    public float cantRandom;
    /**
     * Remaster 拡張: アンカーを手動編集したか。
     * 未編集はアンカー線を 5 ブロック固定表示、編集済みは実制御長で表示するための区別。
     */
    public boolean anchorManual;
    public float constLimitHP;
    public float constLimitHN;
    public float constLimitWP;
    public float constLimitWN;
    public double posX;
    public double posY;
    public double posZ;

    /**
     * 自由配置 (ペンマーカー) の点か。
     * <p>
     * 通常のマーカーは「ブロック + 8方向」で置くので、{@link #init()} が
     * {@code blockX/Z + REVISION[direction]} から posX/posZ を導出できる。ペンマーカーは
     * ブロック内の任意の位置に点を打つため、posX/posY/posZ が<b>一次情報</b>になる。
     * このフラグが立っている間は init() が posX/posY/posZ を上書きしない。
     * <p>
     * 曲線 ({@link RailMapBasic}) は元々 posX/posY/posZ だけを見ているので、
     * ここさえ守れば下流はそのまま動く。blockX/Y/Z は floor(pos) を入れておき、
     * コアブロックの設置位置や始終点の隣接判定に使う。
     */
    public boolean freePos;

    public RailPosition(int x, int y, int z, int dir, int type) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        this.direction = (byte) dir;
        this.switchType = (byte) type;
        this.height = 0;
        this.anchorYaw = NGTMath.wrapAngle((float) dir * 45.0F);
        this.anchorLengthHorizontal = -1.0F;
        this.constLimitHP = 3.99F;
        this.constLimitHN = 0.0F;
        this.constLimitWP = 1.49F;
        this.constLimitWN = -1.49F;
        this.init();
    }

    public void init() {
        if (this.freePos) {
            //ペンマーカーの点は posX/posY/posZ が一次情報。ここで潰さない。
            return;
        }
        this.posX = (double) this.blockX + 0.5D + (double) REVISION[this.direction & 7][0];
        this.posY = (double) this.blockY + (double) (this.height + 1) * 0.0625D;
        this.posZ = (double) this.blockZ + 0.5D + (double) REVISION[this.direction & 7][1];
    }

    /**
     * ペンマーカー用: ブロック内の任意の位置に点を打つ。
     *
     * @param x     レール面のワールド座標
     * @param yaw   曲線の接線 (度)。プレイヤーの視線、または吸着したレールから引き継ぐ
     * @param pitch 接線の勾配 (度)
     */
    public static RailPosition free(double x, double y, double z, float yaw, float pitch, int switchType) {
        int bx = Mth.floor(x);
        int by = Mth.floor(y);
        int bz = Mth.floor(z);
        //direction は 8 方向しか無いので、互換のため最も近い方向を入れておく
        //(曲線そのものは anchorYaw を使うので、丸めの誤差は形に出ない)
        int dir = Math.floorMod(Math.round(NGTMath.wrapAngle(yaw) / 45.0F), 8);
        RailPosition rp = new RailPosition(bx, by, bz, dir, switchType);
        rp.freePos = true;
        rp.posX = x;
        rp.posY = y;
        rp.posZ = z;
        rp.anchorYaw = NGTMath.wrapAngle(yaw);
        rp.anchorPitch = pitch;
        return rp;
    }

    public void addHeight(double par1) {
        int h2 = (int) (par1 / 0.0625D);
        this.height = (byte) (this.height + h2);
        this.init();
    }

    public static RailPosition readFromNBT(CompoundTag nbt) {
        int[] pos = nbt.getIntArray("BlockPos");
        if (pos.length < 3) return null;
        byte b0 = nbt.getByte("Direction");
        byte b2 = nbt.getByte("SwitchType");
        RailPosition rp = new RailPosition(pos[0], pos[1], pos[2], b0, b2);
        rp.setHeight(nbt.getByte("Height"));
        rp.anchorYaw = nbt.getFloat("A_Direction");
        rp.anchorPitch = nbt.getFloat("A_Pitch");
        rp.anchorLengthHorizontal = nbt.getFloat("A_Length");
        rp.anchorLengthVertical = nbt.getFloat("A_LenV");
        rp.cantCenter = nbt.getFloat("C_Center");
        rp.cantEdge = nbt.getFloat("C_Edge");
        rp.cantRandom = nbt.getFloat("C_Random");
        rp.constLimitHP = nbt.getFloat("Const_Limit_HP");
        rp.constLimitHN = nbt.getFloat("Const_Limit_HN");
        rp.constLimitWP = nbt.getFloat("Const_Limit_WP");
        rp.constLimitWN = nbt.getFloat("Const_Limit_WN");
        rp.anchorManual = nbt.getBoolean("A_Manual");
        //ペンマーカーの点は posX/posY/posZ が一次情報なので、init() より先に復元して
        //freePos を立てる (立っていれば init() は上書きしない)。
        if (nbt.getBoolean("FreePos")) {
            rp.freePos = true;
            rp.posX = nbt.getDouble("PosX");
            rp.posY = nbt.getDouble("PosY");
            rp.posZ = nbt.getDouble("PosZ");
        }
        rp.init();
        return rp;
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putIntArray("BlockPos", new int[]{this.blockX, this.blockY, this.blockZ});
        nbt.putByte("SwitchType", this.switchType);
        nbt.putByte("Direction", this.direction);
        nbt.putByte("Height", this.height);
        nbt.putFloat("A_Direction", this.anchorYaw);
        nbt.putFloat("A_Pitch", this.anchorPitch);
        nbt.putFloat("A_Length", this.anchorLengthHorizontal);
        nbt.putFloat("A_LenV", this.anchorLengthVertical);
        nbt.putFloat("C_Center", this.cantCenter);
        nbt.putFloat("C_Edge", this.cantEdge);
        nbt.putFloat("C_Random", this.cantRandom);
        nbt.putFloat("Const_Limit_HP", this.constLimitHP);
        nbt.putFloat("Const_Limit_HN", this.constLimitHN);
        nbt.putFloat("Const_Limit_WP", this.constLimitWP);
        nbt.putFloat("Const_Limit_WN", this.constLimitWN);
        nbt.putBoolean("A_Manual", this.anchorManual);
        if (this.freePos) {
            nbt.putBoolean("FreePos", true);
            nbt.putDouble("PosX", this.posX);
            nbt.putDouble("PosY", this.posY);
            nbt.putDouble("PosZ", this.posZ);
        }
        return nbt;
    }

    public void setHeight(byte par1) {
        this.height = par1;
        this.posY = (double) this.blockY + (double) (par1 + 1) * 0.0625D;
    }

    /**
     * 与えられた距離だけ平行移動
     */
    public void movePos(int x, int y, int z) {
        this.blockX += x;
        this.blockY += y;
        this.blockZ += z;
        this.posX += x;
        this.posY += y;
        this.posZ += z;
    }

    public int[] getNeighborPos() {
        int x2 = NGTMath.floor(this.posX + (double) REVISION[this.direction & 7][0]);
        int y2 = this.blockY;
        int z2 = NGTMath.floor(this.posZ + (double) REVISION[this.direction & 7][1]);
        return new int[]{x2, y2, z2};
    }

    public BlockPos getNeighborBlockPos() {
        int[] pos = this.getNeighborPos();
        return new BlockPos(pos[0], pos[1], pos[2]);
    }

    public RailDir getDir(RailPosition p1, RailPosition p2) {
        double dif1x = p1.posX - this.posX;
        double dif1z = p1.posZ - this.posZ;
        double dif2x = p2.posX - this.posX;
        double dif2z = p2.posZ - this.posZ;
        double cross = dif1z * dif2x - dif1x * dif2z;
        return cross > 0.0D ? RailDir.LEFT : cross < 0.0D ? RailDir.RIGHT : RailDir.NONE;
    }

    public boolean checkRSInput(Level level) {
        return level != null && level.getBestNeighborSignal(new BlockPos(this.blockX, this.blockY, this.blockZ)) > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RailPosition other)) {
            return false;
        }
        // 本家同様、座標のみで比較 (switchType は含めない)
        return this.blockX == other.blockX && this.blockY == other.blockY && this.blockZ == other.blockZ;
    }

    @Override
    public int hashCode() {
        return this.blockX ^ this.blockZ << 8 ^ this.blockY << 16;
    }
}
