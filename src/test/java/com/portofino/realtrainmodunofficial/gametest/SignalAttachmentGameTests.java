package com.portofino.realtrainmodunofficial.gametest;

import com.mojang.authlib.GameProfile;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("realtrainmodunofficial")
@PrefixGameTestTemplate(false)
public final class SignalAttachmentGameTests {
    @GameTest(template="signal_attachment_empty")
    public static void creativeSignalLeavesPoleAndWire(GameTestHelper helper) { restore(helper,true,true); }
    @GameTest(template="signal_attachment_empty")
    public static void survivalSignalLeavesPoleAndDropsOnce(GameTestHelper helper) { restore(helper,false,true); }
    @GameTest(template="signal_attachment_empty")
    public static void signalRestoresDifferentBlockType(GameTestHelper helper) { restore(helper,false,false); }

    private static void restore(GameTestHelper helper,boolean creative,boolean pole) {
        var level=helper.getLevel();
        BlockPos relative=new BlockPos(2,2,2), pos=helper.absolutePos(relative);
        var installed=RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState();
        var support=pole ? installed : Blocks.STONE.defaultBlockState();
        helper.setBlock(relative,support);
        net.minecraft.nbt.CompoundTag saved=null;
        if(pole) {
            var base=(InstalledObjectBlockEntity)level.getBlockEntity(pos);
            base.setDefinition("regression_pole",InstalledObjectCategory.OVERHEAD_LINE_POLE,90);
            base.setWireColor(0x123456); base.setRenderOffset(0.125,0.25,0.375);
            saved=base.saveWithFullMetadata(level.registryAccess());
        }
        helper.setBlock(relative,installed);
        var signal=(InstalledObjectBlockEntity)level.getBlockEntity(pos);
        signal.setDefinition("regression_signal",InstalledObjectCategory.SIGNAL,0);
        signal.setSignalOrigBlock(support); signal.setSignalOrigTileNbt(saved);
        BlockPos wirePos=pos.offset(1,0,0);
        if(pole) {
            level.setBlock(wirePos,installed,3);
            var wire=(InstalledObjectBlockEntity)level.getBlockEntity(wirePos);
            wire.setDefinition("regression_wire",InstalledObjectCategory.WIRE,0);
            wire.setWireEndpoints(pos,pos.above());
        }
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"signal_test"));
        player.gameMode.changeGameModeForPlayer(creative?GameType.CREATIVE:GameType.SURVIVAL);
        helper.assertTrue(player.gameMode.destroyBlock(pos),"first signal removal was rejected");
        helper.assertTrue(level.getBlockState(pos).equals(support),"support block disappeared");
        if(pole) {
            helper.assertTrue(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity,"pole tile was not recreated");
            var restored=(InstalledObjectBlockEntity)level.getBlockEntity(pos);
            helper.assertTrue(restored!=signal && restored.getCategory()==InstalledObjectCategory.OVERHEAD_LINE_POLE,"signal tile survived on the pole");
            helper.assertTrue(restored.getDefinitionId().equals("regression_pole") && restored.getYaw()==90,"pole model/rotation changed");
            helper.assertTrue(restored.getWireColor()==0x123456 && restored.getRenderOffset().x==0.125,"pole custom data changed");
            helper.assertTrue(restored.getSignalOrigBlock()==null && restored.getSignalOrigTileNbt()==null,"signal removal state leaked into the pole");
            helper.assertTrue(level.getBlockEntity(wirePos) instanceof InstalledObjectBlockEntity wire && pos.equals(wire.getWireStart()),"support wire was removed with the signal");
        } else helper.assertTrue(level.getBlockEntity(pos)==null,"signal tile survived on a plain block");
        int drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.5),item->item.getItem().is(RealTrainModUnofficialItems.SIGNAL_ITEM.get()))
                .stream().mapToInt(item->item.getItem().getCount()).sum();
        helper.assertTrue(drops==(creative?0:1),"signal drop count is "+drops);
        helper.succeed();
    }
}
