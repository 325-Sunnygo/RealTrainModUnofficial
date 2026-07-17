package com.myname.legacyloader.bridge.block;

import com.myname.legacyloader.bridge.client.LegacyIcon;
import com.myname.legacyloader.bridge.client.LegacyIconRegister;
import com.myname.legacyloader.bridge.item.LegacyCreativeTab;
import com.myname.legacyloader.bridge.item.LegacyItemStackHelper;
import com.myname.legacyloader.bridge.core.RegistryNameHelper;
import com.myname.legacyloader.bridge.registry.LegacyGameRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * 1.7.10莠呈鋤縺ｮ蝓ｺ譛ｬ繝悶Ο繝・け繧ｯ繝ｩ繧ｹ
 * 笘・㍾隕・ 縺薙・繧ｯ繝ｩ繧ｹ閾ｪ菴薙・BlockState縺ｫMETADATA繝励Ο繝代ユ繧｣繧呈戟縺溘↑縺・
 * 繝｡繧ｿ繝・・繧ｿ縺悟ｿ・ｦ√↑蝣ｴ蜷医・LegacyMetadataBlock繧剃ｽｿ逕ｨ
 * 縺溘□縺励｀OD縺ｮ1.7.10繧ｳ繝ｼ繝会ｼ・lockColoredWoodenMortar遲会ｼ峨・縺薙・繧ｯ繝ｩ繧ｹ繧・
 * 邯呎価縺励※繝｡繧ｿ繝・・繧ｿ繧剃ｽｿ逕ｨ縺吶ｋ 竊・繝・け繧ｹ繝√Ε諠・ｱ縺ｯTEXTURE_OVERRIDES邨檎罰縺ｧ邂｡逅・
 */
public class LegacyBlock extends Block implements ILegacyBlock {

    // Reflection fields for mutating BlockBehaviour.Properties at runtime
    private static final java.lang.reflect.Field PROPS_DESTROY_TIME = getPropsField("destroyTime");
    private static final java.lang.reflect.Field PROPS_EXPLOSION_RESISTANCE = getPropsField("explosionResistance");
    private static final java.lang.reflect.Field PROPS_SOUND_TYPE = getPropsField("soundType");
    private static final java.lang.reflect.Field PROPS_LIGHT_EMISSION = getPropsField("lightEmission");

    private static java.lang.reflect.Field getPropsField(String name) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.level.block.state.BlockBehaviour.Properties.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            System.err.println("LegacyLoader: Could not access Properties field '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private void setPropsField(java.lang.reflect.Field field, Object value) {
        if (field == null) return;
        try {
            field.set(this.properties, value);
        } catch (Exception ignored) {}
    }

    public static final List<LegacyBlock> ALL_INSTANCES = new ArrayList<>();
    public static final IntegerProperty METADATA = IntegerProperty.create("metadata", 0, 15);
    public final LegacyMaterial legacyMaterial;
    public static final Map<ResourceLocation, String> TEXTURE_OVERRIDES = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, Map<Integer, String>> METADATA_TEXTURES = new ConcurrentHashMap<>();

    // 笘・ｿｽ蜉: 髫取ｮｵ縺ｮ繧ｽ繝ｼ繧ｹ繝｡繧ｿ繝・・繧ｿ 竊・繝・け繧ｹ繝√Ε縺ｮ繝槭ャ繝斐Φ繧ｰ
    public static final Map<ResourceLocation, Integer> STAIRS_SOURCE_META = new ConcurrentHashMap<>();

    // SRG Constants for SoundType
    public static final LegacySoundType field_149769_e = LegacySoundType.STONE;
    public static final LegacySoundType field_149766_f = LegacySoundType.WOOD;
    public static final LegacySoundType field_149767_g = LegacySoundType.GROUND;
    public static final LegacySoundType field_149779_h = LegacySoundType.PLANT;
    public static final LegacySoundType field_149777_j = LegacySoundType.METAL;
    public static final LegacySoundType field_149778_k = LegacySoundType.GLASS;
    public static final LegacySoundType field_149776_m = LegacySoundType.CLOTH;
    public static final LegacySoundType field_149775_l = LegacySoundType.SAND;
    public static final LegacySoundType field_149774_o = LegacySoundType.SNOW;
    public static final LegacySoundType field_149772_h = LegacySoundType.LADDER;
    public static final LegacySoundType field_149773_n = LegacySoundType.ANVIL;
    public LegacySoundType field_149762_H = LegacySoundType.STONE;

    public String legacyTextureName;
    public String legacyUnlocalizedName;
    public LegacyIcon field_149761_L;
    private LegacyCreativeTab assignedTab;
    protected int metadataCount = 1;
    private boolean markedForRegistration = false;
    protected double minX = 0.0D;
    protected double minY = 0.0D;
    protected double minZ = 0.0D;
    protected double maxX = 1.0D;
    protected double maxY = 1.0D;
    protected double maxZ = 1.0D;
    protected double field_149759_B = 0.0D;
    protected double field_149760_C = 0.0D;
    protected double field_149754_D = 0.0D;
    protected double field_149755_E = 1.0D;
    protected double field_149756_F = 1.0D;
    protected double field_149757_G = 1.0D;

    // 1.7.10 Block mutable state kept by old mods.  Modern BlockBehaviour stores most
    // of this in immutable properties, so the bridge mirrors the values here and uses
    // them from compatibility methods.
    protected int legacyLightOpacity = 0;
    protected boolean legacyUseNeighborBrightness = false;
    protected boolean legacyEnableStats = true;
    protected boolean legacyTickRandomly = false;
    protected int legacyRenderPass = 0;
    protected float legacyBlockParticleGravity = 1.0F;
    public float field_149765_K = 0.6F; // slipperiness

    public LegacyBlock(LegacyMaterial material) {
        super(BlockBehaviour.Properties.of()
                .mapColor(material != null ? material.getColor() : LegacyMaterial.ROCK.getColor())
                .strength(1.5f, 10.0f));
        this.legacyMaterial = material != null ? material : LegacyMaterial.ROCK;
        this.registerDefaultState(this.stateDefinition.any().setValue(METADATA, 0));
        LegacyGameRegistry.trackBlock(this);
        ALL_INSTANCES.add(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(METADATA);
    }

    public BlockState getStateFromMeta(int meta) {
        return this.defaultBlockState().setValue(METADATA, Math.max(0, Math.min(15, meta)));
    }

    public int getMetaFromState(BlockState state) {
        return state.hasProperty(METADATA) ? state.getValue(METADATA) : 0;
    }

    public LegacyBlock() {
        this(LegacyMaterial.WOOD);
    }

    public LegacyBlock(boolean isDouble, LegacyMaterial material) {
        this(material);
    }

    // 引数順が (Material, boolean) の版 (Bamboo 等が使用)
    public LegacyBlock(LegacyMaterial material, boolean flag) {
        this(material);
    }

    public LegacyBlock(String textureName, LegacyMaterial material) {
        this(material);
        this.legacyTextureName = textureName;
    }

    public LegacyBlock(String textureName, String sideTextureName, LegacyMaterial material, boolean canDrop) {
        this(material);
        this.legacyTextureName = textureName != null ? textureName : sideTextureName;
    }

    public LegacyBlock(Block modelBlock, int meta) {
        this(LegacyBlockHelper.getMaterial(modelBlock));
        if (modelBlock instanceof LegacyBlock) {
            LegacyBlock lb = (LegacyBlock) modelBlock;
            if (lb.legacyTextureName != null) this.legacyTextureName = lb.legacyTextureName;
        }
    }

    public void markForRegistration() { this.markedForRegistration = true; }
    public boolean isMarkedForRegistration() { return this.markedForRegistration; }

    @Override public LegacyMaterial getMaterial() { return this.legacyMaterial; }
    @Override public String getTextureName() { return this.legacyTextureName; }

    @Override
    public Block setBlockTextureName(String name) {
        this.legacyTextureName = name;
        if (name != null && !name.isEmpty()) {
            ResourceLocation rl = RegistryNameHelper.getRegistryName(this);
            if (rl != null) {
                TEXTURE_OVERRIDES.put(rl, name);
            }
        }
        return this;
    }

    @Override
    public Block setBlockName(String name) {
        this.legacyUnlocalizedName = name;
        if (name != null && !name.isEmpty()) {
            String path = name.replace("tile.", "").replace(":", "_").toLowerCase(Locale.ROOT);
            String modid = "legacy_mod";
            if (com.myname.legacyloader.LegacyLoaderMod.CURRENT_LOADING_MODID != null) {
                modid = com.myname.legacyloader.LegacyLoaderMod.CURRENT_LOADING_MODID;
            }
            modid = modid.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            @SuppressWarnings("removal")
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(modid, path);
            RegistryNameHelper.setRegistryName(this, rl);
            if (legacyTextureName != null && !legacyTextureName.isEmpty()) {
                TEXTURE_OVERRIDES.put(rl, legacyTextureName);
            }
        }
        return this;
    }

    @Override
    public Block setCreativeTab(LegacyCreativeTab tab) {
        this.assignedTab = tab;
        return this;
    }

    public LegacyCreativeTab getCreativeTab() { return this.assignedTab; }

    @Override
    public Block setLightOpacity(int opacity) {
        this.legacyLightOpacity = Math.max(0, Math.min(255, opacity));
        return this;
    }

    @Override
    public Block setHardness(float hardness) {
        if (hardness < 0) {
            setPropsField(PROPS_DESTROY_TIME, -1.0f);
            setPropsField(PROPS_EXPLOSION_RESISTANCE, 3600000.0f);
        } else {
            setPropsField(PROPS_DESTROY_TIME, hardness);
        }
        return this;
    }

    @Override
    public Block setResistance(float resistance) {
        // 1.7.10 setResistance(30) = stone; modern stone = 6; ratio = 30/5 = 6
        setPropsField(PROPS_EXPLOSION_RESISTANCE, resistance / 5.0f);
        return this;
    }

    @Override
    public Block setLightLevel(float value) {
        final int lightValue = Math.round(value * 15.0f);
        setPropsField(PROPS_LIGHT_EMISSION, (ToIntFunction<net.minecraft.world.level.block.state.BlockState>) state -> lightValue);
        return this;
    }

    @Override
    public Block setStepSound(LegacySoundType sound) {
        if (sound != null) {
            this.field_149762_H = sound;
            setPropsField(PROPS_SOUND_TYPE, sound);
        }
        return this;
    }

    public Block setStepSound(Object sound) {
        if (sound instanceof LegacySoundType s) setStepSound(s);
        return this;
    }
    @Override public void setHarvestLevel(String toolClass, int level) {}
    @Override public void setHarvestLevel(String toolClass, int level, int metadata) {}
    @Override public void registerBlockIcons(LegacyIconRegister reg) {}
    public void func_149651_a(LegacyIconRegister register) { registerBlockIcons(register); }
    public void func_149651_a(Object register) {
        if (register instanceof LegacyIconRegister) registerBlockIcons((LegacyIconRegister) register);
    }
    public LegacyIcon func_149691_a(int side, int meta) { return getIcon(side, meta); }
    public LegacyIcon getIcon(int side, int meta) { return field_149761_L; }
    public LegacyIcon func_149673_e(BlockGetter world, int x, int y, int z, int side) {
        return getIcon(side, getMetaFromState(world.getBlockState(new BlockPos(x, y, z))));
    }
    public int func_149720_d(BlockGetter world, int x, int y, int z) { return 0xFFFFFF; }
    public int getLightValue(BlockGetter world, int x, int y, int z) {
        return defaultBlockState().getLightEmission(world, new BlockPos(x, y, z));
    }
    public Item func_149650_a(int side, java.util.Random random, int fortune) { return Item.byBlock(this); }
    public int func_149745_a(java.util.Random random) { return 1; }
    public void func_149676_a(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public void func_149683_g() {
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }
    public void setBlockBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = clamp01(minX);
        this.minY = clamp01(minY);
        this.minZ = clamp01(minZ);
        this.maxX = clamp01(maxX);
        this.maxY = clamp01(maxY);
        this.maxZ = clamp01(maxZ);
    }
    public boolean func_149662_c() { return (legacyLightOpacity >= 255 || legacyMaterial.isOpaque()) && func_149645_b() == 0; }
    public boolean func_149686_d() { return func_149662_c() && func_149645_b() == 0; }
    public boolean isOpaqueCube() { return func_149662_c(); }
    public boolean renderAsNormalBlock() { return func_149686_d(); }
    public int func_149645_b() { return 0; }
    public Block func_149675_a(boolean tickRandomly) { this.legacyTickRandomly = tickRandomly; return this; }
    public boolean func_149653_t() { return legacyTickRandomly; }
    public boolean getTickRandomly() { return legacyTickRandomly; }
    public int func_149738_a(Level world) { return 1; }
    public void func_149674_a(Level world, int x, int y, int z, Random random) {}
    public void updateTick(Level world, int x, int y, int z, Random random) { func_149674_a(world, x, y, z, random); }
    public void func_149734_b(Level world, int x, int y, int z, Random random) {}
    public void randomDisplayTick(Level world, int x, int y, int z, Random random) { func_149734_b(world, x, y, z, random); }
    public void func_149695_a(Level world, int x, int y, int z, Block neighbor) {}
    public void onNeighborBlockChange(Level world, int x, int y, int z, Block neighbor) { func_149695_a(world, x, y, z, neighbor); }
    public boolean func_149826_e(Object world, int x, int y, int z) { return true; }
    public boolean func_149655_b(Object world, int x, int y, int z) { return true; }
    public boolean func_149646_a(Object world, int x, int y, int z, int side) { return true; }
    public Object func_149633_g(Object world, int x, int y, int z) { return makeAABB(x, y, z); }
    public void func_149719_a(Object world, int x, int y, int z) {}
    public boolean func_149826_e(BlockGetter world, int x, int y, int z) { return true; }
    public boolean func_149655_b(BlockGetter world, int x, int y, int z) { return true; }
    public boolean func_149646_a(BlockGetter world, int x, int y, int z, int side) { return true; }
    public AABB func_149633_g(Level world, int x, int y, int z) { return makeAABB(x, y, z); }
    public void func_149719_a(BlockGetter world, int x, int y, int z) {}
    public AABB func_149668_a(Level world, int x, int y, int z) { return makeAABB(x, y, z); }
    public AABB getCollisionBoundingBoxFromPool(Level world, int x, int y, int z) { return func_149633_g(world, x, y, z); }
    public AABB getSelectedBoundingBoxFromPool(Level world, int x, int y, int z) { return func_149668_a(world, x, y, z); }
    public int func_149660_a(Level world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata) { return metadata; }
    public boolean func_149727_a(Level world, int x, int y, int z, Object player, int side, float hitX, float hitY, float hitZ) { return false; }
    public void func_149689_a(Level world, int x, int y, int z, Object entity, ItemStack stack) {}
    public void func_149664_b(Level world, int x, int y, int z, Object player) {}
    public void func_149681_a(Level world, int x, int y, int z, int meta, Object explosion) {}
    public int func_149701_w() { return legacyRenderPass; }
    public int getRenderBlockPass() { return legacyRenderPass; }
    public Block setBlockUnbreakable() { return setHardness(-1.0F); }
    public boolean func_149698_L() { return true; }
    public boolean canDropFromExplosion(Object explosion) { return true; }
    public boolean func_149730_j() { return func_149662_c() && func_149686_d(); }
    public boolean isBlockNormalCube() { return func_149730_j(); }
    public boolean isNormalCube() { return func_149730_j(); }
    public int getLightOpacity() { return legacyLightOpacity; }
    public boolean getUseNeighborBrightness() { return legacyUseNeighborBrightness; }
    public Block setUseNeighborBrightness(boolean value) { this.legacyUseNeighborBrightness = value; return this; }
    public boolean getEnableStats() { return legacyEnableStats; }
    public Block disableStats() { this.legacyEnableStats = false; return this; }
    public float getAmbientOcclusionLightValue() { return func_149662_c() ? 0.2F : 1.0F; }
    public int getMobilityFlag() { return 0; }
    public boolean hasComparatorInputOverride() { return false; }
    public int getComparatorInputOverride(Level world, int x, int y, int z, int side) { return 0; }
    public boolean canProvidePower() { return false; }
    public int isProvidingWeakPower(BlockGetter world, int x, int y, int z, int side) { return 0; }
    public int isProvidingStrongPower(BlockGetter world, int x, int y, int z, int side) { return 0; }

    // 1.7.10 aliases still used by several custom renderers (Ha10BM wire/line renderers etc.)
    public boolean func_149637_q() { return func_149730_j(); }
    public int func_149677_c(BlockGetter world, int x, int y, int z) { return 15728880; }
    public int getMixedBrightnessForBlock(BlockGetter world, int x, int y, int z) { return func_149677_c(world, x, y, z); }

    // Bridge modern block callbacks back into legacy override points. Without this,
    // blocks that recompute metadata/bounds from neighbours behave like inert cubes.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        try { func_149695_a(level, pos.getX(), pos.getY(), pos.getZ(), neighborBlock); } catch (Throwable ignored) {}
        updateWireLineMetadata(level, pos, state);
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        try { onBlockAdded(level, pos.getX(), pos.getY(), pos.getZ()); } catch (Throwable ignored) {}
        updateWireLineMetadata(level, pos, state);
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    public void onBlockAdded(Level world, int x, int y, int z) {}
    public void func_149726_b(Level world, int x, int y, int z) { onBlockAdded(world, x, y, z); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        try { breakBlock(level, pos.getX(), pos.getY(), pos.getZ(), this, getMetaFromState(state)); } catch (Throwable ignored) {}
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public void breakBlock(Level world, int x, int y, int z, Block block, int meta) {}
    public void func_149749_a(Level world, int x, int y, int z, Block block, int meta) { breakBlock(world, x, y, z, block, meta); }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        try { func_149674_a(level, pos.getX(), pos.getY(), pos.getZ(), new Random(random.nextLong())); } catch (Throwable ignored) {}
        super.tick(state, level, pos, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean handled = false;
        try {
            Vec3 hitLocation = hit.getLocation();
            float hitX = (float) (hitLocation.x - pos.getX());
            float hitY = (float) (hitLocation.y - pos.getY());
            float hitZ = (float) (hitLocation.z - pos.getZ());
            handled = func_149727_a(level, pos.getX(), pos.getY(), pos.getZ(), player, hit.getDirection().ordinal(),
                    hitX, hitY, hitZ);
        } catch (Throwable ignored) {}
        return handled ? InteractionResult.SUCCESS : super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int metadata = LegacyItemStackHelper.getMetadata(context.getItemInHand());
        try {
            BlockPos pos = context.getClickedPos();
            Vec3 hitLocation = context.getClickLocation();
            float hitX = (float) (hitLocation.x - pos.getX());
            float hitY = (float) (hitLocation.y - pos.getY());
            float hitZ = (float) (hitLocation.z - pos.getZ());
            metadata = func_149660_a(context.getLevel(), pos.getX(), pos.getY(), pos.getZ(),
                    context.getClickedFace().ordinal(), hitX, hitY, hitZ, metadata);
            if (isPlainWireLineBlock()) {
                metadata = wireLineMetadata(context.getLevel(), pos);
            }
        } catch (Throwable ignored) {
        }
        return getStateFromMeta(metadata);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        boolean handled = false;
        try {
            Vec3 hitLocation = hit.getLocation();
            float hitX = (float) (hitLocation.x - pos.getX());
            float hitY = (float) (hitLocation.y - pos.getY());
            float hitZ = (float) (hitLocation.z - pos.getZ());
            handled = func_149727_a(level, pos.getX(), pos.getY(), pos.getZ(), player,
                    hit.getDirection().ordinal(), hitX, hitY, hitZ);
        } catch (Throwable ignored) {}
        return handled ? ItemInteractionResult.SUCCESS : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    private AABB makeAABB(int x, int y, int z) {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return null;
        return new AABB(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        updateLegacyBounds(level, pos);
        return currentShape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        updateLegacyBounds(level, pos);
        return currentShape();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        if (!legacyRendersAsFullCube()) {
            return Shapes.empty();
        }
        updateLegacyBounds(level, pos);
        return currentShape();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return !legacyRendersAsFullCube();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !legacyRendersAsFullCube() || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return legacyRendersAsFullCube() ? super.getShadeBrightness(state, level, pos) : 1.0F;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return legacyRendersAsFullCube() ? super.getVisualShape(state, level, pos, context) : Shapes.empty();
    }

    private void updateLegacyBounds(BlockGetter level, BlockPos pos) {
        try {
            func_149719_a(level, pos.getX(), pos.getY(), pos.getZ());
        } catch (Throwable ignored) {
        }
    }

    private VoxelShape currentShape() {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return Shapes.empty();
        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean legacyRendersAsFullCube() {
        try {
            return func_149662_c() && func_149686_d();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isPlainWireLineBlock() {
        String name = getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("wireline") && !name.contains("colored");
    }

    private void updateWireLineMetadata(Level level, BlockPos pos, BlockState state) {
        if (!isPlainWireLineBlock() || state == null || !state.hasProperty(METADATA)) return;
        int metadata = wireLineMetadata(level, pos);
        if (metadata != state.getValue(METADATA)) {
            try {
                level.setBlock(pos, state.setValue(METADATA, metadata), 3);
            } catch (Throwable ignored) {
            }
        }
    }

    private int wireLineMetadata(BlockGetter level, BlockPos pos) {
        int metadata = 0;
        if (isWireLineAt(level, pos.west())) metadata |= 1;
        if (isWireLineAt(level, pos.east())) metadata |= 2;
        if (isWireLineAt(level, pos.north())) metadata |= 4;
        if (isWireLineAt(level, pos.south())) metadata |= 8;
        return metadata;
    }

    private boolean isWireLineAt(BlockGetter level, BlockPos pos) {
        try {
            Block block = level.getBlockState(pos).getBlock();
            return block != null && block.getClass().getName().toLowerCase(Locale.ROOT).contains("wireline");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    @Override
    public void getSubBlocks(Item item, LegacyCreativeTab tab, List<ItemStack> list) {
        list.add(new ItemStack(item));
    }

    @Override public int damageDropped(int meta) { return 0; }
    public int quantityDropped(Random random) { return func_149745_a(random); }
    public int quantityDropped(int meta, int fortune, Random random) { return quantityDroppedWithBonus(fortune, random); }
    public int quantityDroppedWithBonus(int fortune, Random random) { return func_149745_a(random); }
    public Item getItemDropped(int meta, Random random, int fortune) { return func_149650_a(meta, random, fortune); }
    public Item getItem(Level world, int x, int y, int z) { return Item.byBlock(this); }
    public int getDamageValue(Level world, int x, int y, int z) { return com.myname.legacyloader.bridge.world.LegacyWorldHelper.getBlockMetadata(world, x, y, z); }
    public float getExplosionResistance(Object entity) { return 10.0F; }
    public boolean canSilkHarvest() { return false; }
    public boolean canSilkHarvest(Level world, Object player, int x, int y, int z, int meta) { return canSilkHarvest(); }
    public boolean canHarvestBlock(Object player, int meta) { return true; }
    public boolean isReplaceable(BlockGetter world, int x, int y, int z) { return legacyMaterial == LegacyMaterial.AIR; }
    public boolean isAir(BlockGetter world, int x, int y, int z) { return this == net.minecraft.world.level.block.Blocks.AIR || legacyMaterial == LegacyMaterial.AIR; }
    public boolean isFlammable(BlockGetter world, int x, int y, int z, Object side) { return false; }
    public int getFlammability(BlockGetter world, int x, int y, int z, Object side) { return 0; }
    public int getFireSpreadSpeed(BlockGetter world, int x, int y, int z, Object side) { return 0; }
    public boolean isFireSource(Level world, int x, int y, int z, Object side) { return false; }
    public boolean isLadder(BlockGetter world, int x, int y, int z, Object entity) { return false; }
    public boolean isSideSolid(BlockGetter world, int x, int y, int z, Object side) { return func_149730_j(); }
    public boolean hasTileEntity() { return false; }
    public boolean hasTileEntity(int meta) { return hasTileEntity(); }
    public Object createTileEntity(Level world, int meta) { return null; }
    public java.util.ArrayList<ItemStack> getDrops(Level world, int x, int y, int z, int meta, int fortune) {
        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<>();
        Item item = getItemDropped(meta, new Random(), fortune);
        int count = quantityDropped(meta, fortune, new Random());
        if (item != null && count > 0) drops.add(new ItemStack(item, count));
        return drops;
    }

    @Override
    public boolean hasSubtypes() { return metadataCount > 1; }

    @Override
    public int getMaxMetadata() { return metadataCount; }

    public void setMetadataCount(int count) { this.metadataCount = count; }

    @Override
    public String getDescriptionId() {
        if (this.legacyUnlocalizedName != null) return "tile." + this.legacyUnlocalizedName;
        return super.getDescriptionId();
    }

    // === SRG Method Aliases ===
    public Block func_149663_c(String name) { return setBlockName(name); }
    public Block func_149658_d(String name) { return setBlockTextureName(name); }
    public Block func_149647_a(LegacyCreativeTab tab) { return setCreativeTab(tab); }
    public Block func_149647_a(Object tab) {
        if (tab instanceof LegacyCreativeTab) return setCreativeTab((LegacyCreativeTab) tab);
        return this;
    }
    public Block func_149711_c(float hardness) { return setHardness(hardness); }
    public Block func_149752_b(float resistance) { return setResistance(resistance); }
    public Block func_149672_a(LegacySoundType sound) { return setStepSound(sound); }
    public Block func_149672_a(Object sound) { return setStepSound(sound); }
    public Block func_149713_g(int opacity) { return setLightOpacity(opacity); }
    public Block func_149715_a(float value) { return setLightLevel(value); }
    public LegacyMaterial func_149688_o() { return getMaterial(); }
    public String func_149641_N() { return getTextureName(); }

    public void func_149666_a(Item item, LegacyCreativeTab tab, List<ItemStack> list) {
        getSubBlocks(item, tab, list);
    }

    public void func_149666_a(Item item, net.minecraft.creativetab.CreativeTabs tab, List<ItemStack> list) {
        LegacyCreativeTab legacyTab = null;
        if (tab instanceof LegacyCreativeTab) legacyTab = (LegacyCreativeTab) tab;
        else if (tab != null) legacyTab = tab.getLinkedTab();
        getSubBlocks(item, legacyTab, list);
    }

    public int func_149692_a(int meta) { return damageDropped(meta); }
}
