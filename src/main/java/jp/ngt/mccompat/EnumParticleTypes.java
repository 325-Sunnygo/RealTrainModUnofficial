package jp.ngt.mccompat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Map;

/**
 * 1.7.10 {@code net.minecraft.util.EnumParticleTypes} の互換。
 *
 * <p>SL パック (RTM Taiwan SL Pack 等) の描画スクリプトは蒸気/煙の演出で
 * {@code entity.field_70170_p.func_175688_a(EnumParticleTypes.EXPLOSION_NORMAL, ...)} を呼ぶ。
 * 各定数に 1.21 のバニラ {@link ParticleOptions} を持たせ、{@link WorldCompat#func_175688_a}
 * がそのまま {@code level.addParticle} へ渡せるようにする。
 *
 * <p>1.7.10 の粒子名 ({@code "explode"} など、文字列で渡す旧経路
 * {@code func_72869_a}/{@code renderer.spawnParticle}) との対応も {@link #byLegacyName} で持つ。
 */
public enum EnumParticleTypes {
    EXPLOSION_NORMAL(ParticleTypes.POOF, "explode"),
    EXPLOSION_LARGE(ParticleTypes.EXPLOSION, "largeexplode"),
    EXPLOSION_HUGE(ParticleTypes.EXPLOSION_EMITTER, "hugeexplosion"),
    SMOKE_NORMAL(ParticleTypes.SMOKE, "smoke"),
    SMOKE_LARGE(ParticleTypes.LARGE_SMOKE, "largesmoke"),
    CLOUD(ParticleTypes.CLOUD, "cloud"),
    FLAME(ParticleTypes.FLAME, "flame"),
    LAVA(ParticleTypes.LAVA, "lava"),
    CRIT(ParticleTypes.CRIT, "crit"),
    SPLASH(ParticleTypes.SPLASH, "splash"),
    WATER_BUBBLE(ParticleTypes.BUBBLE, "bubble"),
    WATER_DROP(ParticleTypes.RAIN, "droplet"),
    DRIP_WATER(ParticleTypes.DRIPPING_WATER, "dripWater"),
    SUSPENDED(ParticleTypes.UNDERWATER, "suspended"),
    SNOWBALL(ParticleTypes.ITEM_SNOWBALL, "snowballpoof"),
    HEART(ParticleTypes.HEART, "heart");

    public final ParticleOptions particle;
    public final String legacyName;

    EnumParticleTypes(ParticleOptions particle, String legacyName) {
        this.particle = particle;
        this.legacyName = legacyName;
    }

    private static final Map<String, EnumParticleTypes> BY_LEGACY;

    static {
        java.util.HashMap<String, EnumParticleTypes> m = new java.util.HashMap<>();
        for (EnumParticleTypes t : values()) {
            m.put(t.legacyName.toLowerCase(java.util.Locale.ROOT), t);
        }
        BY_LEGACY = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * 1.7.10 の粒子名 ({@code "explode"} 等) から {@link ParticleOptions} を引く。
     * 未知の名前は煙 (SMOKE) にフォールバック (エラーで描画を止めないため)。
     */
    public static ParticleOptions particleByLegacyName(String name) {
        if (name != null) {
            EnumParticleTypes t = BY_LEGACY.get(name.toLowerCase(java.util.Locale.ROOT));
            if (t != null) {
                return t.particle;
            }
        }
        return ParticleTypes.SMOKE;
    }
}
