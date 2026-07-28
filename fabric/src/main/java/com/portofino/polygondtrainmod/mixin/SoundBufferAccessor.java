package com.portofino.polygondtrainmod.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** SoundBuffer の private フィールドへアクセスする (ステレオ→モノラル変換用)。 */
@Mixin(SoundBuffer.class)
public interface SoundBufferAccessor {

    @Accessor("data")
    ByteBuffer rtmu$data();

    @Accessor("format")
    AudioFormat rtmu$format();
}
