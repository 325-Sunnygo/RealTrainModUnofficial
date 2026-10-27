package jp.ngt.rtm.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** A real tickable sound with the original public stop/volume/pitch API. */
public class MovingSoundEntity extends MovingSoundCustom<Entity> {
    public MovingSoundEntity(Entity entity, Object sound, boolean repeat) { this(entity,sound,repeat,16.0F); }
    public MovingSoundEntity(Entity entity, Object sound, boolean repeat, float range) {
        super(entity, resource(sound), repeat, range);
        updatePosition();
    }
    private static ResourceLocation resource(Object value) {
        if(value instanceof ResourceLocation location) return location;
        if(value instanceof jp.ngt.mccompat.ResourceLocation location) return location.toReal();
        return ResourceLocation.parse(String.valueOf(value));
    }
    @Override public void func_73660_a() {
        if(this.entity==null || !this.entity.isAlive()) { stop(); return; }
        super.func_73660_a();
    }
    @Override protected void updatePosition() {
        if(this.entity==null) return;
        this.x=this.entity.getX(); this.y=this.entity.getY(); this.z=this.entity.getZ();
    }
}
