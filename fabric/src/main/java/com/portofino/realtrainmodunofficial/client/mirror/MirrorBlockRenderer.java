package com.portofino.realtrainmodunofficial.client.mirror;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import jp.ngt.rtm.block.BlockMirror;
import jp.ngt.rtm.block.tileentity.TileEntityMirror;
public final class MirrorBlockRenderer implements BlockEntityRenderer<TileEntityMirror> {
    public MirrorBlockRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(TileEntityMirror mirror,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var state=mirror.getBlockState();var block=(BlockMirror)state.getBlock();
        if(block.mirrorType==BlockMirror.MirrorType.Mono_Panel)MirrorRenderer.face(mirror.getBlockPos(),state.getValue(BlockMirror.FACING),true,255,pose,buffers);
        else for(Direction face:Direction.values())MirrorRenderer.face(mirror.getBlockPos(),face,false,mirror.getAlpha(),pose,buffers);
    }
}
