package jp.kaiz.atsassistmod.ifttt;

import jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity;
import jp.kaiz.atsassistmod.network.IFTTTPlaySoundPayload;
import jp.kaiz.atsassistmod.utils.CardinalDirection;
import jp.kaiz.atsassistmod.utils.ComparisonManager;
import jp.kaiz.atsassistmod.utils.KaizUtils;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.parts.EntityVehiclePart;
import jp.ngt.rtm.modelpack.state.ResourceState;
import jp.ngt.rtm.rail.BlockLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 本家 jp.kaiz.atsassistmod.ifttt.IFTTTContainer (1118行) の移植。
 * 条件 (This) とアクション (That) の実装本体。
 * 本家の Java シリアライズ + GZIP の代わりに CompoundTag で保存/通信する。
 * SetBlock は 1.21 に数値ブロック id がないため、ブロック名 (例 minecraft:stone) 指定に変更。
 */
public abstract class IFTTTContainer {

    protected boolean once;

    public IFTTTContainer() {
    }

    public abstract IFTTTType.IFTTTEnumBase getType();

    public String getTitle() {
        return this.getType().getName();
    }

    public abstract String[] getExplanation();

    public abstract void setFromGui(IFTTTGuiFields gui);

    public void setOnce(boolean once) {
        this.once = once;
    }

    public boolean isOnce() {
        return once;
    }

    //------------------------------------------------------------ NBT 保存/復元

    protected abstract void writeExtra(CompoundTag tag);

    protected abstract void readExtra(CompoundTag tag);

    public final CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TypeId", this.getType().getId());
        tag.putBoolean("Once", this.once);
        this.writeExtra(tag);
        return tag;
    }

    public static IFTTTContainer fromNbt(CompoundTag tag) {
        IFTTTContainer container = newByTypeId(tag.getInt("TypeId"));
        if (container != null) {
            container.once = tag.getBoolean("Once");
            container.readExtra(tag);
        }
        return container;
    }

    /** 種別 id → 新規インスタンス (GUI の新規作成でも使う)。 */
    public static IFTTTContainer newByTypeId(int typeId) {
        switch (typeId) {
            case 110:
                return new This.Minecraft.RedStoneInput();
            case 120:
                return new This.RTM.SimpleDetectTrain();
            case 121:
                return new This.RTM.Cars();
            case 122:
                return new This.RTM.Speed();
            case 124:
                return new This.RTM.TrainDataMap();
            case 125:
                return new This.RTM.TrainDirection();
            case 130:
                return new This.ATSAssist.CrossingObstacleDetection();
            case 210:
                return new That.Minecraft.RedStoneOutput();
            case 211:
                return new That.Minecraft.PlaySound();
            case 212:
                return new That.Minecraft.ExecuteCommand();
            case 213:
                return new That.Minecraft.SetBlock();
            case 221:
                return new That.RTM.DataMap();
            case 223:
                return new That.RTM.TrainSignal();
            case 230:
                return new That.ATSAssist.JavaScript();
            default:
                return null;
        }
    }

    /** GUI の編集用クローン (本家 SerializationUtils.clone 相当)。 */
    public IFTTTContainer copy() {
        return fromNbt(this.toNbt());
    }

    private static String i18n(String key) {
        return Component.translatable(key).getString();
    }

    //================================================================ This (条件)

    public abstract static class This extends IFTTTContainer {

        public abstract boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train);

        public abstract static class Minecraft {

            /** RS信号入力 (ON/OFF/比較)。 */
            public static class RedStoneInput extends This {

                public enum ModeType {
                    ON("ON", false),
                    OFF("OFF", false),
                    EQUAL("==", true),
                    GREATER_THAN(">", true),
                    GREATER_EQUAL(">=", true),
                    LESS_THAN("<", true),
                    LESS_EQUAL("<=", true),
                    NOT_EQUAL("!=", true);

                    public final String name;
                    public final boolean needStr;

                    ModeType(String name, boolean needStr) {
                        this.name = name;
                        this.needStr = needStr;
                    }
                }

                private int value;
                private ModeType mode;

                public RedStoneInput() {
                    this.mode = ModeType.ON;
                    this.value = 0;
                }

                public ModeType getMode() {
                    return this.mode;
                }

                public int getValue() {
                    return this.value;
                }

                public void setMode(ModeType mode) {
                    this.mode = mode;
                }

                public void setValue(int value) {
                    this.value = value;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.Minecraft.RedStoneInput;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"RSInput" + this.mode.name + (this.mode.needStr ? this.value : "")};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setValue(gui.getTextFieldInt(0));
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    int power = tile.getLevel().getBestNeighborSignal(tile.getBlockPos());
                    switch (this.mode) {
                        case ON:
                            return power > 0;
                        case OFF:
                            return power == 0;
                        case EQUAL:
                            return power == this.value;
                        case GREATER_THAN:
                            return power > this.value;
                        case GREATER_EQUAL:
                            return power >= this.value;
                        case LESS_THAN:
                            return power < this.value;
                        case LESS_EQUAL:
                            return power <= this.value;
                        case NOT_EQUAL:
                            return power != this.value;
                        default:
                            return false;
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("Mode", this.mode.name());
                    tag.putInt("Value", this.value);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    try {
                        this.mode = ModeType.valueOf(tag.getString("Mode"));
                    } catch (IllegalArgumentException e) {
                        this.mode = ModeType.ON;
                    }
                    this.value = tag.getInt("Value");
                }
            }
        }

        public abstract static class RTM {

            /** 単純列車検知 (全車/先頭車/最後尾/レール内)。 */
            public static class SimpleDetectTrain extends This {

                public enum DetectMode {
                    All("ATSAssistMod.IFTTT.DetectMode.0"),
                    FirstCar("ATSAssistMod.IFTTT.DetectMode.1"),
                    LastCar("ATSAssistMod.IFTTT.DetectMode.2"),
                    OnRail("ATSAssistMod.IFTTT.DetectMode.3");

                    public final String name;

                    DetectMode(String name) {
                        this.name = name;
                    }

                    public String getDisplayName() {
                        return i18n(this.name);
                    }
                }

                private DetectMode detectMode;

                public SimpleDetectTrain() {
                    this.detectMode = DetectMode.All;
                }

                public DetectMode getDetectMode() {
                    return detectMode;
                }

                public void setDetectMode(DetectMode detectMode) {
                    this.detectMode = detectMode;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.RTM.OnTrain;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{i18n("ATSAssistMod.IFTTT.DetectMode.name") + ": " + i18n(this.detectMode.name)};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    switch (this.detectMode) {
                        case All:
                            return train != null;
                        case FirstCar:
                            return train != null && (train.getFormation().size() == 1
                                    || train.getConnectedTrain(train.getTrainDirection()) == null);
                        case LastCar:
                            return train != null && (train.getFormation().size() == 1
                                    || train.getConnectedTrain(1 - train.getTrainDirection()) == null);
                        case OnRail:
                            Level level = tile.getLevel();
                            BlockPos above = tile.getBlockPos().above();
                            if (level.getBlockState(above).getBlock() instanceof BlockLargeRailBase) {
                                BlockEntity aboveTile = level.getBlockEntity(above);
                                if (aboveTile instanceof TileEntityLargeRailBase rail) {
                                    return rail.isTrainOnRail();
                                }
                            }
                            return false;
                    }
                    return false;
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("DetectMode", this.detectMode.name());
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    try {
                        this.detectMode = DetectMode.valueOf(tag.getString("DetectMode"));
                    } catch (IllegalArgumentException e) {
                        this.detectMode = DetectMode.All;
                    }
                }
            }

            /** 両数比較。 */
            public static class Cars extends This {
                private int value;
                private ComparisonManager.Integer comparisonType;

                public Cars() {
                    this.value = 0;
                    this.comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                }

                public ComparisonManager.Integer getMode() {
                    return this.comparisonType;
                }

                public int getValue() {
                    return this.value;
                }

                public void setMode(ComparisonManager.Integer mode) {
                    this.comparisonType = mode;
                }

                public void setValue(int value) {
                    this.value = value;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.RTM.Cars;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"Cars" + this.comparisonType.getName() + this.value};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setValue(gui.getTextFieldInt(0));
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    return train != null && train.getFormation() != null
                            && this.comparisonType.isTrue(train.getFormation().size(), this.value);
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("Comparison", this.comparisonType.name());
                    tag.putInt("Value", this.value);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    try {
                        this.comparisonType = ComparisonManager.Integer.valueOf(tag.getString("Comparison"));
                    } catch (IllegalArgumentException e) {
                        this.comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                    }
                    this.value = tag.getInt("Value");
                }
            }

            /** 速度比較 (km/h)。 */
            public static class Speed extends This {
                private int value;
                private ComparisonManager.Integer comparisonType;

                public Speed() {
                    this.value = 0;
                    this.comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                }

                public ComparisonManager.Integer getMode() {
                    return this.comparisonType;
                }

                public int getValue() {
                    return this.value;
                }

                public void setMode(ComparisonManager.Integer mode) {
                    this.comparisonType = mode;
                }

                public void setValue(int value) {
                    this.value = value;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.RTM.Speed;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"Speed" + this.comparisonType.getName() + this.value};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setValue(gui.getTextFieldInt(0));
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    return train != null && this.comparisonType.isTrue(Math.round(train.getSpeed() * 72), this.value);
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("Comparison", this.comparisonType.name());
                    tag.putInt("Value", this.value);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    try {
                        this.comparisonType = ComparisonManager.Integer.valueOf(tag.getString("Comparison"));
                    } catch (IllegalArgumentException e) {
                        this.comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                    }
                    this.value = tag.getInt("Value");
                }
            }

            /** 列車 DataMap の値比較。 */
            public static class TrainDataMap extends This {
                private DataType dataType;
                private String key;
                private String value;
                private ComparisonManager.ComparisonBase<?> comparisonType;

                public TrainDataMap() {
                    this.key = "";
                    this.value = "";
                    this.setDataType(DataType.BOOLEAN);
                }

                public DataType getDataType() {
                    return dataType;
                }

                public void setDataType(DataType dataType) {
                    this.dataType = dataType;
                    switch (dataType) {
                        case INT:
                            this.comparisonType = ComparisonManager.Integer.EQUAL;
                            break;
                        case DOUBLE:
                            this.comparisonType = ComparisonManager.Double.EQUAL;
                            break;
                        case STRING:
                            this.comparisonType = ComparisonManager.String.EQUAL;
                            break;
                        case BOOLEAN:
                        default:
                            this.comparisonType = ComparisonManager.Boolean.TRUE;
                            break;
                    }
                }

                public void nextDataType() {
                    this.setDataType(KaizUtils.getNextEnum(this.dataType));
                }

                @SuppressWarnings({"unchecked", "rawtypes"})
                public void nextComparisonType() {
                    this.comparisonType = (ComparisonManager.ComparisonBase<?>)
                            KaizUtils.getNextEnum((Enum) this.comparisonType);
                }

                public ComparisonManager.ComparisonBase<?> getComparisonType() {
                    return this.comparisonType;
                }

                public String getKey() {
                    return key;
                }

                public void setKey(String key) {
                    this.key = key;
                }

                public String getValue() {
                    return value;
                }

                public void setValue(String value) {
                    this.value = value;
                }

                @Override
                public String getTitle() {
                    return this.getType().getName() + " " + this.dataType.key;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.RTM.TrainDataMap;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{
                            "Key: " + this.key,
                            "Value" + this.comparisonType.getName()
                                    + ((this.dataType == DataType.BOOLEAN) ? "" : this.value)
                    };
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setKey(gui.getTextFieldText(0));
                    this.setValue(gui.getTextFieldText(1));
                }

                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    if (train == null) {
                        return false;
                    }
                    ResourceState resourceState = train.getResourceState();
                    if (resourceState == null) {
                        return false;
                    }
                    jp.ngt.rtm.modelpack.state.DataMap dataMap = resourceState.getDataMap();
                    Object dv;
                    switch (this.dataType) {
                        case INT:
                            dv = dataMap.getInt(this.key);
                            break;
                        case DOUBLE:
                            dv = dataMap.getDouble(this.key);
                            break;
                        case STRING:
                            dv = dataMap.getString(this.key);
                            break;
                        case BOOLEAN:
                            dv = dataMap.getBoolean(this.key);
                            break;
                        default:
                            return false;
                    }
                    ComparisonManager.ComparisonBase cb = this.comparisonType;
                    return cb.isTrue(dv, cb.parseT(this.value));
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("DataType", this.dataType.name());
                    tag.putString("Key", this.key);
                    tag.putString("Value", this.value);
                    tag.putString("Comparison", ((Enum<?>) this.comparisonType).name());
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.setDataType(DataType.byName(tag.getString("DataType")));
                    this.key = tag.getString("Key");
                    this.value = tag.getString("Value");
                    //比較種別は dataType に対応する enum クラス内で名前検索
                    String cmp = tag.getString("Comparison");
                    Enum<?> base = (Enum<?>) this.comparisonType;
                    for (Enum<?> e : base.getDeclaringClass().getEnumConstants()) {
                        if (e.name().equals(cmp)) {
                            this.comparisonType = (ComparisonManager.ComparisonBase<?>) e;
                            break;
                        }
                    }
                }
            }

            /** 進行方角 (NORTH/EAST/SOUTH/WEST)。 */
            public static class TrainDirection extends This {
                private CardinalDirection direction;

                public TrainDirection() {
                    this.direction = CardinalDirection.NORTH;
                }

                public CardinalDirection getDirection() {
                    return direction;
                }

                public void setDirection(CardinalDirection direction) {
                    this.direction = direction;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.RTM.TrainDirection;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"Train heading " + this.direction.name()};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    return train != null && this.direction.isInDirection(train);
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("Direction", this.direction.name());
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.direction = CardinalDirection.getDirection(tag.getString("Direction"));
                }
            }
        }

        public abstract static class ATSAssist {

            /** 踏切障害物検知 (範囲内に列車以外のエンティティ)。 */
            public static class CrossingObstacleDetection extends This {
                private int[] startCC = new int[]{0, 0, 0};
                private int[] endCC = new int[]{0, 0, 0};

                public void setStartCC(int x, int y, int z) {
                    this.startCC = new int[]{x, y, z};
                }

                public int[] getStartCC() {
                    return this.startCC;
                }

                public void setEndCC(int x, int y, int z) {
                    this.endCC = new int[]{x, y, z};
                }

                public int[] getEndCC() {
                    return this.endCC;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.This.ATSAssist.CODD;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{
                            String.format("x:%s, y:%s, z:%s", this.startCC[0], this.startCC[1], this.startCC[2]),
                            String.format("x:%s, y:%s, z:%s", this.endCC[0], this.endCC[1], this.endCC[2])
                    };
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setStartCC(gui.getTextFieldInt(0), gui.getTextFieldInt(1), gui.getTextFieldInt(2));
                    this.setEndCC(gui.getTextFieldInt(3), gui.getTextFieldInt(4), gui.getTextFieldInt(5));
                }

                @Override
                public boolean isCondition(IFTTTBlockEntity tile, EntityTrainBase train) {
                    AABB box = new AABB(
                            Math.min(this.startCC[0], this.endCC[0]),
                            Math.min(this.startCC[1], this.endCC[1]),
                            Math.min(this.startCC[2], this.endCC[2]),
                            Math.max(this.startCC[0], this.endCC[0]),
                            Math.max(this.startCC[1], this.endCC[1]),
                            Math.max(this.startCC[2], this.endCC[2]));
                    return tile.getLevel().getEntitiesOfClass(Entity.class, box).stream()
                            .filter(obj -> !(obj instanceof EntityTrainBase))
                            .filter(obj -> !(obj instanceof EntityVehiclePart))
                            .filter(obj -> !(obj instanceof EntityBogie))
                            .filter(obj -> !(obj instanceof ItemEntity))
                            .filter(obj -> !(obj.getVehicle() instanceof EntityTrainBase))
                            .filter(obj -> !(obj.getVehicle() instanceof EntityVehiclePart))
                            .count() > 0;
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putIntArray("StartCC", this.startCC);
                    tag.putIntArray("EndCC", this.endCC);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    int[] s = tag.getIntArray("StartCC");
                    int[] e = tag.getIntArray("EndCC");
                    if (s.length == 3) {
                        this.startCC = s;
                    }
                    if (e.length == 3) {
                        this.endCC = e;
                    }
                }
            }
        }
    }

    //================================================================ That (アクション)

    public abstract static class That extends IFTTTContainer {

        public abstract void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first);

        /** 条件が成立しなくなった時 (本家 finish)。 */
        public void finish(IFTTTBlockEntity tile, EntityTrainBase train) {
        }

        public abstract static class Minecraft {

            /** RS信号出力 (固定レベル or 両数)。 */
            public static class RedStoneOutput extends That {
                private boolean trainCarsOutput;
                private int outputLevel;

                public void setTrainCarsOutput(boolean value) {
                    this.trainCarsOutput = value;
                }

                public boolean isTrainCarsOutput() {
                    return this.trainCarsOutput;
                }

                public void setOutputLevel(int outputLevel) {
                    this.outputLevel = outputLevel;
                }

                public int getOutputLevel() {
                    return outputLevel;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.Minecraft.RedStoneOutput;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{i18n("ATSAssistMod.gui.IFTTTMaterial.210.1") + ": "
                            + (this.isTrainCarsOutput() ? i18n("ATSAssistMod.gui.IFTTTMaterial.210.0") : this.outputLevel)};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setOutputLevel(gui.getTextFieldInt(0));
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if (this.isTrainCarsOutput()) {
                        if (train != null && train.getFormation() != null) {
                            tile.setRedStoneOutput(train.getFormation().size());
                        } else {
                            tile.setRedStoneOutput(0);
                        }
                    } else {
                        tile.setRedStoneOutput(this.getOutputLevel());
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putBoolean("TrainCarsOutput", this.trainCarsOutput);
                    tag.putInt("OutputLevel", this.outputLevel);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.trainCarsOutput = tag.getBoolean("TrainCarsOutput");
                    this.outputLevel = tag.getInt("OutputLevel");
                }
            }

            /** 音声再生 (once=一度のみ / 通常はループして条件解除で停止)。 */
            public static class PlaySound extends That {
                private String soundName = "";
                private int[] pos = new int[]{0, 0, 0};
                private int radius = 1;

                public PlaySound() {
                }

                public PlaySound(BlockPos tilePos) {
                    this.pos = new int[]{tilePos.getX(), tilePos.getY(), tilePos.getZ()};
                }

                public void setSoundName(String soundName) {
                    this.soundName = soundName;
                }

                public String getSoundName() {
                    return this.soundName;
                }

                public void setPos(int x, int y, int z) {
                    this.pos = new int[]{x, y, z};
                }

                public int[] getPos() {
                    return pos;
                }

                public void setRadius(int radius) {
                    this.radius = radius;
                }

                public int getRadius() {
                    return radius;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.Minecraft.PlaySound;
                }

                @Override
                public String[] getExplanation() {
                    ResourceLocation sound = this.createResourceLocation();
                    return sound == null ? new String[]{""}
                            : new String[]{sound.getNamespace(), sound.getPath()};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setSoundName(gui.getTextFieldText(0));
                    this.setRadius(gui.getTextFieldInt(1));
                    this.setPos(gui.getTextFieldInt(2), gui.getTextFieldInt(3), gui.getTextFieldInt(4));
                }

                public ResourceLocation createResourceLocation() {
                    if (this.soundName != null && this.soundName.matches(".*:.+")) {
                        return ResourceLocation.tryParse(this.soundName.trim());
                    }
                    return null;
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    ResourceLocation sound = this.createResourceLocation();
                    if (sound != null && first) {
                        PacketDistributor.sendToAllPlayers(new IFTTTPlaySoundPayload(
                                tile.getBlockPos(), false,
                                this.pos[0], this.pos[1], this.pos[2],
                                sound.toString(), !this.once, this.radius));
                    }
                }

                @Override
                public void finish(IFTTTBlockEntity tile, EntityTrainBase train) {
                    if (!this.once) {
                        PacketDistributor.sendToAllPlayers(new IFTTTPlaySoundPayload(
                                tile.getBlockPos(), true, 0, 0, 0, "", false, 0));
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("SoundName", this.soundName == null ? "" : this.soundName);
                    tag.putIntArray("Pos", this.pos);
                    tag.putInt("Radius", this.radius);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.soundName = tag.getString("SoundName");
                    int[] p = tag.getIntArray("Pos");
                    if (p.length == 3) {
                        this.pos = p;
                    }
                    this.radius = tag.getInt("Radius");
                }
            }

            /** コマンド実行 (ブロック位置からコマブロ相当の権限で実行)。 */
            public static class ExecuteCommand extends That {
                private String command = "";
                private String displayName = "";

                public void setCommand(String command) {
                    this.command = command;
                }

                public String getCommand() {
                    return command;
                }

                public void setDisplayName(String displayName) {
                    this.displayName = displayName;
                }

                public String getDisplayName() {
                    return this.displayName == null ? "" : this.displayName;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.Minecraft.ExecuteCommand;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{
                            !this.getDisplayName().isEmpty()
                                    ? this.displayName
                                    : (i18n("ATSAssistMod.gui.IFTTTMaterial.212.1") + ": " + this.command)};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setDisplayName(gui.getTextFieldText(0));
                    this.setCommand(gui.getTextFieldText(1));
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if ((!this.once || first) && this.command != null && !this.command.isEmpty()
                            && tile.getLevel() instanceof ServerLevel serverLevel) {
                        MinecraftServer server = serverLevel.getServer();
                        BlockPos pos = tile.getBlockPos();
                        String name = String.format("ATSA IFTTT Executer(%s, %s, %s)",
                                pos.getX(), pos.getY(), pos.getZ());
                        net.minecraft.commands.CommandSourceStack source = new net.minecraft.commands.CommandSourceStack(
                                CommandSource.NULL, Vec3.atCenterOf(pos), Vec2.ZERO, serverLevel,
                                2, name, Component.literal(name), server, null).withSuppressedOutput();
                        server.getCommands().performPrefixedCommand(source, this.command);
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("Command", this.command == null ? "" : this.command);
                    tag.putString("DisplayName", this.getDisplayName());
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.command = tag.getString("Command");
                    this.displayName = tag.getString("DisplayName");
                }
            }

            /**
             * ブロック設置。1.21 は数値 id が無いためブロック名 (例 minecraft:stone) 指定。
             * 各行は x, y, z, ブロック名。
             */
            public static class SetBlock extends That {
                public static final int FIELDS_PER_ROW = 4;

                private final List<Entry> posList = new ArrayList<>();

                public record Entry(int x, int y, int z, String blockId) {
                }

                public SetBlock() {
                    this.posList.add(new Entry(0, 0, 0, ""));
                }

                public List<Entry> getPosList() {
                    return posList;
                }

                public void clearPosList() {
                    this.posList.clear();
                }

                public void addPos(Entry entry) {
                    this.posList.add(entry);
                }

                public void addPos(Entry entry, int index) {
                    this.posList.add(index, entry);
                }

                public void setPos(Entry entry, int index) {
                    this.posList.set(index, entry);
                }

                public void removePos(int index) {
                    this.posList.remove(index);
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.Minecraft.SetBlock;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{i18n("ATSAssistMod.gui.IFTTTMaterial.213.1") + ": " + this.posList.size()};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.clearPosList();
                    int length = gui.textFieldLength();
                    for (int i = 0; i + FIELDS_PER_ROW <= length; i += FIELDS_PER_ROW) {
                        int x = gui.getTextFieldInt(i);
                        int y = gui.getTextFieldInt(i + 1);
                        int z = gui.getTextFieldInt(i + 2);
                        String block = gui.getTextFieldText(i + 3);
                        this.addPos(new Entry(x, y, z, block));
                    }
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if (!this.once || first) {
                        Level level = tile.getLevel();
                        for (Entry entry : this.posList) {
                            ResourceLocation id = ResourceLocation.tryParse(
                                    entry.blockId() == null ? "" : entry.blockId().trim());
                            if (id == null) {
                                continue;
                            }
                            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                            if (block == null) {
                                continue;
                            }
                            level.setBlock(new BlockPos(entry.x(), entry.y(), entry.z()),
                                    block.defaultBlockState(), 3);
                        }
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    ListTag list = new ListTag();
                    for (Entry entry : this.posList) {
                        CompoundTag t = new CompoundTag();
                        t.putInt("X", entry.x());
                        t.putInt("Y", entry.y());
                        t.putInt("Z", entry.z());
                        t.putString("Block", entry.blockId() == null ? "" : entry.blockId());
                        list.add(t);
                    }
                    tag.put("PosList", list);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.posList.clear();
                    ListTag list = tag.getList("PosList", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag t = list.getCompound(i);
                        this.posList.add(new Entry(t.getInt("X"), t.getInt("Y"), t.getInt("Z"),
                                t.getString("Block")));
                    }
                    if (this.posList.isEmpty()) {
                        this.posList.add(new Entry(0, 0, 0, ""));
                    }
                }
            }
        }

        public abstract static class RTM {

            /** 列車 DataMap への書き込み。 */
            public static class DataMap extends That {
                private DataType dataType;
                private String key, value;

                public DataMap() {
                    this.dataType = DataType.STRING;
                    this.key = "";
                    this.value = "";
                }

                public DataType getDataType() {
                    return dataType;
                }

                public void setDataType(DataType dataType) {
                    this.dataType = dataType;
                }

                public void nextDataType() {
                    this.dataType = KaizUtils.getNextEnum(this.dataType);
                }

                public String getKey() {
                    return key;
                }

                public void setKey(String key) {
                    this.key = key;
                }

                public String getValue() {
                    return value;
                }

                public void setValue(String value) {
                    this.value = value;
                }

                @Override
                public String getTitle() {
                    return this.getType().getName() + " " + this.dataType.key;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.RTM.TrainDataMap;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"Key: " + this.key, "Value: " + this.value};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setKey(gui.getTextFieldText(0));
                    this.setValue(gui.getTextFieldText(1));
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if (train == null) {
                        return;
                    }
                    ResourceState resourceState = train.getResourceState();
                    if (resourceState == null) {
                        return;
                    }
                    jp.ngt.rtm.modelpack.state.DataMap dataMap = resourceState.getDataMap();
                    try {
                        switch (this.dataType) {
                            case BOOLEAN:
                                dataMap.setBoolean(this.key, Boolean.parseBoolean(this.value), 1);
                                break;
                            case DOUBLE:
                                dataMap.setDouble(this.key, Double.parseDouble(this.value), 1);
                                break;
                            case INT:
                                dataMap.setInt(this.key, Integer.parseInt(this.value), 1);
                                break;
                            case STRING:
                                dataMap.setString(this.key, this.value, 1);
                                break;
                            default:
                                break;
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("DataType", this.dataType.name());
                    tag.putString("Key", this.key);
                    tag.putString("Value", this.value);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.dataType = DataType.byName(tag.getString("DataType"));
                    this.key = tag.getString("Key");
                    this.value = tag.getString("Value");
                }
            }

            /** 車内信号の直接設定。 */
            public static class TrainSignal extends That {
                private int signal;

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.RTM.Signal;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{"SetSignal:" + this.signal};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setSignal(gui.getTextFieldInt(0));
                }

                public int getSignal() {
                    return this.signal;
                }

                public void setSignal(int signal) {
                    this.signal = signal;
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if (train != null) {
                        train.setSignal(this.signal);
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putInt("Signal", this.signal);
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.signal = tag.getInt("Signal");
                }
            }
        }

        public abstract static class ATSAssist {

            /**
             * JavaScript 実行。doThat(tile, train, first) 関数を毎 tick 呼ぶ (本家仕様)。
             * エラー時は設定したプレイヤーへ通知して停止する。
             */
            public static class JavaScript extends That {
                private transient ScriptEngine scriptEngine;
                private String jsText = "";
                private boolean error;
                private UUID uuid;
                private String scriptName = "";

                public String getJSText() {
                    return jsText == null ? "" : jsText;
                }

                /** GUI から設定 (設定者の UUID を記録してエラー通知先にする)。 */
                public void setJSText(String jsText, UUID setter) {
                    this.uuid = setter;
                    this.jsText = jsText;
                    this.error = false;
                    this.scriptEngine = null;
                }

                public String getScriptName() {
                    return scriptName == null ? "" : scriptName;
                }

                public void setScriptName(String scriptName) {
                    this.scriptName = scriptName;
                }

                public boolean isError() {
                    return this.error;
                }

                @Override
                public IFTTTType.IFTTTEnumBase getType() {
                    return IFTTTType.That.ATSAssist.JavaScript;
                }

                @Override
                public String[] getExplanation() {
                    return new String[]{this.getScriptName() + " " + (this.error ? "Script Error!" : "")};
                }

                @Override
                public void setFromGui(IFTTTGuiFields gui) {
                    this.setScriptName(gui.getTextFieldText(0));
                    //UUID は IFTTTScreen 側で setJSText を直接呼んで設定する
                    this.jsText = gui.getTextFieldText(1);
                    this.error = false;
                    this.scriptEngine = null;
                }

                @Override
                public void doThat(IFTTTBlockEntity tile, EntityTrainBase train, boolean first) {
                    if (this.error) {
                        return;
                    }
                    try {
                        if (scriptEngine == null) {
                            scriptEngine = ScriptUtil.doScript(this.getJSText());
                        }
                        ScriptUtil.doScriptFunction(scriptEngine, "doThat", tile, train, first);
                        this.error = false;
                    } catch (Throwable e) {
                        BlockPos pos = tile.getBlockPos();
                        jp.kaiz.atsassistmod.ATSAssistCore.LOGGER.warn(
                                "[ATSA Notice] X:{} Y:{} Z:{} IFTTTBlock Script Error!",
                                pos.getX(), pos.getY(), pos.getZ(), e);
                        if (tile.getLevel() instanceof ServerLevel serverLevel && this.uuid != null) {
                            Player player = serverLevel.getServer().getPlayerList().getPlayer(this.uuid);
                            if (player instanceof ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("文法は以下を参考にしてください。"));
                                sp.sendSystemMessage(Component.literal(
                                                "https://github.com/Kai-Z-JP/ATSAssistMod/blob/develop/MANUAL.md")
                                        .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(
                                                ClickEvent.Action.OPEN_URL,
                                                "https://github.com/Kai-Z-JP/ATSAssistMod/blob/develop/MANUAL.md"))));
                                sp.sendSystemMessage(Component.literal(String.format(
                                        "[ATSA Notice] X:%s Y:%s Z:%s Script Error!",
                                        pos.getX(), pos.getY(), pos.getZ())));
                                if (e.getMessage() != null) {
                                    sp.sendSystemMessage(Component.literal(e.getMessage()));
                                }
                                if (e.getCause() != null && e.getCause().getMessage() != null) {
                                    sp.sendSystemMessage(Component.literal(e.getCause().getMessage()));
                                }
                            }
                        }
                        this.error = true;
                        tile.setChangedAndSync();
                    }
                }

                @Override
                protected void writeExtra(CompoundTag tag) {
                    tag.putString("JsText", this.getJSText());
                    tag.putBoolean("Error", this.error);
                    tag.putString("ScriptName", this.getScriptName());
                    if (this.uuid != null) {
                        tag.putUUID("Uuid", this.uuid);
                    }
                }

                @Override
                protected void readExtra(CompoundTag tag) {
                    this.jsText = tag.getString("JsText");
                    this.error = tag.getBoolean("Error");
                    this.scriptName = tag.getString("ScriptName");
                    this.uuid = tag.hasUUID("Uuid") ? tag.getUUID("Uuid") : null;
                }
            }
        }
    }
}
