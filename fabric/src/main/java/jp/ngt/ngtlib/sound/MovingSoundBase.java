package jp.ngt.ngtlib.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 本家 {@code net.minecraft.client.audio.MovingSound} (+ {@code MovingSoundCustom} の基底部分)
 * の 1.21 移植。
 *
 * <p>パックのスクリプトは {@code Java.extend(MovingSoundTileEntity, {func_73660_a: ...})} で
 * {@code func_73660_a} (= 1.7.10 の {@code update()}) を上書きしてループ音を実装する。
 * そのため上書き点として {@link #func_73660_a()} を公開し、{@link #tick()} から呼ぶ。
 *
 * <p>{@code stop()} は 1.7.10 では public だったが 1.21 の
 * {@code AbstractTickableSoundInstance#stop()} は {@code protected final} のため使えない。
 * ここでは {@code AbstractSoundInstance} を継承して {@link TickableSoundInstance} を実装し、
 * 本家同様の <b>public stop()</b> を提供する。
 */
public abstract class MovingSoundBase extends AbstractSoundInstance implements TickableSoundInstance {

    /** 本家 donePlaying。 */
    public boolean isStopped;

    protected MovingSoundBase(ResourceLocation location, SoundSource source) {
        super(location, source, RandomSource.create());
        this.looping = true;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    /** 本家 MovingSoundCustom.update / MovingSoundTileEntity.update の上書き点 (SRG 名)。 */
    public void func_73660_a() {
    }

    /** 本家 MovingSound.update()。 */
    public void update() {
        this.func_73660_a();
    }

    @Override
    public void tick() {
        this.func_73660_a();
    }

    @Override
    public boolean isStopped() {
        return this.isStopped;
    }

    /** 本家 MovingSoundCustom.stop(): donePlaying = true。スクリプトが {@code sound.stop()} で呼ぶ。 */
    public void stop() {
        this.isStopped = true;
    }
}
