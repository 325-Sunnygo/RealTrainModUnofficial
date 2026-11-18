package jp.ngt.ngtlib.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** 本家 net.minecraft.client.audio.MovingSound (+ Movi… */
public abstract class MovingSoundBase extends AbstractSoundInstance implements TickableSoundInstance {

    /** 本家 donePlaying。 */
    public boolean isStopped;

    protected MovingSoundBase(ResourceLocation location, SoundSource source) {
        super(location, source, RandomSource.create());
        this.looping = true;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    /** 本家 MovingSoundCustom.update / MovingSoundTileEnti… */
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

    /** 本家 MovingSoundCustom.stop(): donePlaying = true。 */
    public void stop() {
        this.isStopped = true;
    }
}
