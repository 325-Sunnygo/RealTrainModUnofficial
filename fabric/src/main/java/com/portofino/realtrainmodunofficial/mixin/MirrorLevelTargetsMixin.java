package com.portofino.realtrainmodunofficial.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.*;

/** Secondary views cannot clear the main view's Fabulous/outline buffers. */
@Mixin(LevelRenderer.class)
public abstract class MirrorLevelTargetsMixin implements com.portofino.realtrainmodunofficial.client.mirror.MirrorLevelTargets {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="renderSky")
    private void rtmu$skyProjection(org.joml.Matrix4f view,org.joml.Matrix4f projection,float partialTick,net.minecraft.client.Camera camera,boolean foggy,Runnable setup,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if(!com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.rendering()){original.call(view,projection,partialTick,camera,foggy,setup);return;}
        var sky=com.portofino.realtrainmodunofficial.client.mirror.MirrorGeometry.skyProjection(projection);
        var previous=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(sky,com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        try{original.call(view,sky,partialTick,camera,foggy,setup);}finally{
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(previous,com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }
    @Shadow private PostChain transparencyChain;
    @Shadow private RenderTarget translucentTarget,itemEntityTarget,particlesTarget,weatherTarget,cloudsTarget,entityTarget;
    @Override public Runnable rtmu$suspendAuxiliaryTargets() {
        var chain=transparencyChain;var translucent=translucentTarget;var items=itemEntityTarget;var particles=particlesTarget;var weather=weatherTarget;var clouds=cloudsTarget;var entities=entityTarget;
        transparencyChain=null;translucentTarget=null;itemEntityTarget=null;particlesTarget=null;weatherTarget=null;cloudsTarget=null;entityTarget=null;
        return ()->{transparencyChain=chain;translucentTarget=translucent;itemEntityTarget=items;particlesTarget=particles;weatherTarget=weather;cloudsTarget=clouds;entityTarget=entities;};
    }
}
