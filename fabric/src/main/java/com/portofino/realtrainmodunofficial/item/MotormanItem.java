package com.portofino.realtrainmodunofficial.item;

import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.item.ItemNPC (運転士アイテム、damage=0) の移植。
 * <ul>
 *   <li>地面に使用 → 運転士が立って出現 (向きはプレイヤーの逆、15°刻み — 本家同様)</li>
 *   <li><b>列車に使用 → 運転士が運転台に乗車</b> (EntityTrainBase.interactTrain 側で処理)</li>
 * </ul>
 * 乗車した運転士は信号現示に従って自動運転する。素手右クリックでマクロ選択、
 * 「本と羽根ペン」でダイヤ運転。殴ると回収できる。
 */
public class MotormanItem extends Item {

    public MotormanItem() {
        super(new Properties().stacksTo(16)); //本家 setMaxStackSize(16)
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        //本家: プレイヤーの向きの逆を 15° 刻みにスナップ
        float rotationInterval = 15.0F;
        int yaw = Mth.floor(Mth.wrapDegrees(-player.getYRot() + 180.0F + (rotationInterval / 2.0F)) / rotationInterval);
        float yawF = yaw * rotationInterval;

        EntityMotorman motorman = RTMEntities.MOTORMAN.get().create(level);
        if (motorman == null) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        motorman.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, yawF, 0.0F);
        motorman.yHeadRot = yawF;
        motorman.yBodyRot = yawF;
        if (level.addFreshEntity(motorman)) {
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }
}
