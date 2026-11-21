package net.neoforged.neoforge.client.event;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.Event;

/**
 * シム: 登録をバニラ/Fabric の登録ヘルパへ直接委譲する
 * (EntityRendererRegistry / BlockEntityRendererRegistry 相当)。
 */
public abstract class EntityRenderersEvent extends Event {

    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(
                EntityType<? extends T> type, EntityRendererProvider<T> provider) {
            net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
        }

        /**
         * NeoForge と同じく親クラス用のレンダラーを子の BlockEntityType へ登録できる形にする。
         * (BlockEntityType<? extends T>)。
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends BlockEntity> void registerBlockEntityRenderer(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry
                .register((BlockEntityType) type, (BlockEntityRendererProvider) provider);
        }
    }
}
