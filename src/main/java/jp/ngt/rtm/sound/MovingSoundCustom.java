package jp.ngt.rtm.sound;

import jp.ngt.ngtlib.sound.MovingSoundBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * 本家 {@code jp.ngt.rtm.sound.MovingSoundCustom<T>} の移植。
 * 音量/ピッチは次 tick に反映される (本家 prevVolume / prevPitch と同じ挙動)。
 */
public abstract class MovingSoundCustom<T> extends MovingSoundBase {

    protected final T entity;
    private float prevVolume = -1.0F;
    private float prevPitch = -1.0F;

    protected MovingSoundCustom(T entity, ResourceLocation sound, boolean repeat, float range) {
        super(sound, SoundSource.BLOCKS);
        this.entity = entity;
        this.looping = repeat;
        // 本家: volume = range / 16
        this.setVolume(range / 16.0F);
    }

    @Override
    public void func_73660_a() {
        if (this.prevVolume >= 0.0F) {
            this.volume = this.prevVolume;
            this.prevVolume = -1.0F;
        }
        if (this.prevPitch >= 0.0F) {
            this.pitch = this.prevPitch;
            this.prevPitch = -1.0F;
        }
        this.updatePosition();
    }

    /** 位置更新 (TileEntity/Entity 版が上書き)。 */
    protected void updatePosition() {
    }

    public void setVolume(float par1) {
        this.prevVolume = Math.max(par1, 0.0F);
    }

    public void setPitch(float par1) {
        this.prevPitch = Math.max(par1, 0.0F);
    }
}
