package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class RenderHandEvent extends Event implements ICancellableEvent {
    private final InteractionHand hand;
    private final PoseStack poseStack;
    private final float partialTick;

    public RenderHandEvent(InteractionHand hand, PoseStack poseStack, float partialTick) {
        this.hand = hand;
        this.poseStack = poseStack;
        this.partialTick = partialTick;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getPartialTick() {
        return partialTick;
    }
}
