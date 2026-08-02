package com.portofino.realtrainmodunofficial.item;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.Formation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 編成バール。
 *
 * <p>殴った車両が連結している編成を<b>まるごと</b>消す。1 両ずつ壊す
 * {@link CrowbarItem} とは別物。長い編成を片付けるとき用。
 */
public class FormationCrowbarItem extends Item {

    public FormationCrowbarItem() {
        super(new Properties().stacksTo(1));
    }

    /**
     * 殴ったとき。
     *
     * <p>台車を殴っても車体を殴っても効くようにする (見た目で当たるのはほぼ台車)。
     *
     * @return true = ここで処理した (通常のダメージ処理へ流さない)
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        return handleAttack(player, target);
    }

    /**
     * 殴打の本体。ローダーごとに受け口が違うのでここだけ共通にしてある
     * (NeoForge = {@code Item#onLeftClickEntity} / Fabric = {@code AttackEntityCallback})。
     *
     * @return true = ここで処理した
     */
    public static boolean handleAttack(Player player, Entity target) {
        EntityTrainBase train = resolveTrain(target);
        if (train == null) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;   //サーバー側で消す。クライアントは殴打を消費するだけ
        }
        List<EntityTrainBase> trains = collectFormation(train);
        for (EntityTrainBase car : trains) {
            if (!car.isRemoved()) {
                car.kill();   //台車・座席は remove 側で片付く
            }
        }
        player.displayClientMessage(Component.translatable(
            "message.realtrainmodunofficial.formation_crowbar.removed", trains.size()), true);
        return true;
    }

    /** 殴った相手から車体を引く。台車なら親の車体。 */
    private static EntityTrainBase resolveTrain(Entity target) {
        if (target instanceof EntityTrainBase train) {
            return train;
        }
        if (target instanceof EntityBogie bogie) {
            return bogie.getTrain();
        }
        return null;
    }

    /**
     * その車両が属する編成の全車両。
     * 編成が無ければその 1 両だけ (連結していない単行)。
     */
    private static List<EntityTrainBase> collectFormation(EntityTrainBase train) {
        Set<EntityTrainBase> out = new LinkedHashSet<>();
        try {
            Formation formation = train.getFormation();
            if (formation != null) {
                // 先に集めてから消す。消しながら回すと編成の中身がずれる
                formation.getTrainStream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(out::add);
            }
        } catch (Throwable ignored) {
            //編成の整合が崩れていても、殴った 1 両は必ず消せるようにする
        }
        out.add(train);
        return new ArrayList<>(out);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.realtrainmodunofficial.formation_crowbar")
            .withStyle(ChatFormatting.GRAY));
    }
}
