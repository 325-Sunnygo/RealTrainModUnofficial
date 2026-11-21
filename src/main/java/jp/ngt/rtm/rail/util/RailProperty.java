package jp.ngt.rtm.rail.util;

import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** 本家 jp.ngt.rtm.rail.util.RailProperty の忠実移植。 */
public final class RailProperty {
    public final boolean autoSplit;
    public final String railModel;
    public final Block block;
    public final int blockMetadata;
    public final float blockHeight;
    public final String unlocalizedName;

    /** 本家では ItemRail.getDefaultProperty。 */
    public static RailProperty getDefaultProperty() {
        return new RailProperty("1067mm_Wood", Blocks.GRAVEL, 0, 0.0625F);
    }

    public RailProperty(String par1, Block par2, int par3, float par4) {this(par1,par2,par3,par4,true);}
    public RailProperty(String par1, Block par2, int par3, float par4,boolean autoSplit) {
        this.autoSplit=autoSplit;
        this.railModel = par1;
        this.block = par2;
        this.blockMetadata = par3;
        this.blockHeight = (par4 <= 0.0F) ? 0.0625F : par4;
        this.unlocalizedName = par2.getDescriptionId();
    }

    public static RailProperty readFromNBT(CompoundTag nbt) {
        String s0 = nbt.getString("RailModel");
        String s1 = nbt.getString("BlockName");
        int i0 = nbt.getInt("BlockMetadata");
        Block block = resolveBlock(s1,i0);
        float b0 = nbt.getFloat("BlockHeight");
        return new RailProperty(s0, block, i0, b0,!nbt.contains("AutoSplit") || nbt.getBoolean("AutoSplit"));
    }

    /** Resolve legacy names such as carpet + color metad… */
    public static Block resolveBlock(String name,int metadata) {
        ResourceLocation key=ResourceLocation.tryParse(name);
        if(key==null) return Blocks.AIR;
        if(!key.getNamespace().equals("minecraft")) return BuiltInRegistries.BLOCK.get(key);
        return jp.ngt.ngtlib.block.LegacyBlockStates.toState(key.toString(),metadata).getBlock();
    }

    public net.minecraft.world.level.block.state.BlockState blockState() {
        ResourceLocation key=BuiltInRegistries.BLOCK.getKey(block);
        return key.getNamespace().equals("minecraft")
                ? jp.ngt.ngtlib.block.LegacyBlockStates.toState(key.toString(),blockMetadata)
                : block.defaultBlockState();
    }

    public void writeToNBT(Object nbt) {writeToNBT(java.util.Objects.requireNonNull(jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt)));}
    public static RailProperty readFromNBT(Object nbt) {return readFromNBT(java.util.Objects.requireNonNull(jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt)));}
    public void writeToNBT(CompoundTag nbt) {
        nbt.putBoolean("AutoSplit",autoSplit);
        nbt.putString("RailModel", this.railModel);
        String s1 = BuiltInRegistries.BLOCK.getKey(this.block).toString();
        nbt.putString("BlockName", s1);
        nbt.putInt("BlockMetadata", this.blockMetadata);
        nbt.putFloat("BlockHeight", this.blockHeight);
    }

    /** 道床幅。本家では getModelSet.getConfig.ballastWidth。 */
    /** モデル定義ごとに 1 個だけ ModelSet を作る (serverSE/guiSE の二重読み… */
    private static final java.util.Map<String, jp.ngt.rtm.modelpack.modelset.ModelSetRail> MODEL_SET_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearModelSetCache() {
        MODEL_SET_CACHE.clear();
    }

    public jp.ngt.rtm.modelpack.modelset.ModelSetRail getModelSet() {
        String key = this.railModel + "|" + this.getBallastWidth() + "|" + this.blockHeight;
        jp.ngt.rtm.modelpack.modelset.ModelSetRail cached = MODEL_SET_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        var config = new jp.ngt.rtm.modelpack.cfg.RailConfig();
        config.railName = this.railModel;
        config.ballastWidth = this.getBallastWidth();
        var definition = RailRegistry.getById(this.railModel);
        if (definition != null) {
            config.model = new jp.ngt.rtm.modelpack.cfg.ModelConfig.ModelSource();
            config.model.modelFile = definition.getModelFile();
            config.model.rendererPath = definition.getScriptPath();
            config.scale = definition.getModelScale();
            var offset = definition.getModelOffset();
            config.offset = new float[]{(float) offset.x, (float) offset.y, (float) offset.z};
        }
        config.init();
        jp.ngt.rtm.modelpack.modelset.ModelSetRail created =
                new jp.ngt.rtm.modelpack.modelset.ModelSetRail(config);
        jp.ngt.rtm.modelpack.modelset.ModelSetRail previous = MODEL_SET_CACHE.putIfAbsent(key, created);
        return previous != null ? previous : created;
    }

    public int getBallastWidth() {
        RailDefinition def = RailRegistry.getById(this.railModel);
        int width = def != null ? def.getBallastWidth() : 3;
        if (width < 1) {
            width = 3;
        }
        // 本家 RailConfig は奇数を強制する
        if (width % 2 == 0) {
            width += 1;
        }
        return width;
    }
}
