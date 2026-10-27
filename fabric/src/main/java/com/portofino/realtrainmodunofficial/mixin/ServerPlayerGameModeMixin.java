package com.portofino.realtrainmodunofficial.mixin;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
 @Shadow @Final protected ServerPlayer player;
 @Redirect(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
 private boolean rtmu$removeSignal(net.minecraft.server.level.ServerLevel level,BlockPos pos,boolean moving) {
  var state=level.getBlockState(pos);
  if(state.getBlock() instanceof com.portofino.realtrainmodunofficial.block.InstalledObjectBlock block)
   return block.onDestroyedByPlayer(state,level,pos,player,!player.getAbilities().instabuild,state.getFluidState());
  return level.removeBlock(pos,moving);
 }
}
