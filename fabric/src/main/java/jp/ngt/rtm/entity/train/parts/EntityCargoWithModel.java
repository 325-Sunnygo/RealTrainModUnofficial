package jp.ngt.rtm.entity.train.parts;

import com.portofino.realtrainmodunofficial.cargo.CargoDefinition;
import com.portofino.realtrainmodunofficial.cargo.CargoRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * モデルを選べる貨物の基底。本家 {@code EntityCargoWithModel} の移植。
 *
 * <p>本家は {@code ResourceState} を持つが、RTMU はモデルを<b>id 文字列</b>で持つ
 * (車両/設置物と同じやり方)。スニーク右クリックでモデル選択画面が開く。
 */
public abstract class EntityCargoWithModel extends EntityCargo {
    private static final EntityDataAccessor<String> MODEL_ID =
        SynchedEntityData.defineId(EntityCargoWithModel.class, EntityDataSerializers.STRING);

    public EntityCargoWithModel(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL_ID, "");
    }

    public String getModelId() {
        return this.entityData.get(MODEL_ID);
    }

    public void setModelId(String id) {
        this.entityData.set(MODEL_ID, id == null ? "" : id);
    }

    /** 選ばれているモデル。未設定なら先頭 (本家も既定を返す)。 */
    public CargoDefinition getDefinition() {
        CargoDefinition def = CargoRegistry.getById(this.getModelId());
        return def != null ? def : CargoRegistry.getDefault(this.getKind());
    }

    protected abstract CargoDefinition.Kind getKind();

    @Override
    protected void readCargoFromNBT(CompoundTag nbt) {
        this.setModelId(nbt.getString("ModelId"));
    }

    @Override
    protected void writeCargoToNBT(CompoundTag nbt) {
        nbt.putString("ModelId", this.getModelId());
    }

    /** 本家: スニーク右クリックでモデル選択画面。 */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (this.level().isClientSide()) {
                com.portofino.realtrainmodunofficial.ClientHooks.openCargoModelScreen(this.getId());
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return InteractionResult.PASS;
    }
}
