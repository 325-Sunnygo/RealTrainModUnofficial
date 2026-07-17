package com.myname.legacyloader;

import com.myname.legacyloader.bridge.block.*;
import com.myname.legacyloader.bridge.client.LegacyIcon;
import com.myname.legacyloader.bridge.client.LegacyIconRegister;
import com.myname.legacyloader.bridge.client.LegacyTextureMap;
import com.myname.legacyloader.bridge.client.event.LegacyTextureStitchEvent;
import com.myname.legacyloader.bridge.client.registry.LegacyRenderingRegistry;
import com.myname.legacyloader.bridge.client.registry.LegacySimpleBlockRenderingHandler;
import com.myname.legacyloader.bridge.client.renderer.LegacyISBRHBakedModel;
import com.myname.legacyloader.bridge.client.renderer.LegacyRenderBlocks;
import com.myname.legacyloader.bridge.client.renderer.LegacyTessellator;
import com.myname.legacyloader.bridge.event.LegacyInitEvent;
import com.myname.legacyloader.bridge.event.LegacyPostInitEvent;
import com.myname.legacyloader.bridge.event.LegacyPreInitEvent;
import com.myname.legacyloader.bridge.fml.IWorldGenerator;
import com.myname.legacyloader.bridge.fml.LegacyFMLCommonHandler;
import com.myname.legacyloader.bridge.fml.LegacyModMetadata;
import com.myname.legacyloader.bridge.fml.LegacyModMetadataAnnotation;
import com.myname.legacyloader.bridge.fml.LegacySidedProxy;
import com.myname.legacyloader.bridge.fml.LegacyModInstanceAnnotation;
import com.myname.legacyloader.bridge.forge.LegacyMinecraftForge;
import com.myname.legacyloader.bridge.item.LegacyCreativeTab;
import com.myname.legacyloader.bridge.item.LegacyItem;
import com.myname.legacyloader.bridge.item.crafting.LegacyIFuelHandler;
import com.myname.legacyloader.bridge.item.crafting.LegacyRecipeManager;
import com.myname.legacyloader.bridge.core.RegistryNameHelper;
import com.myname.legacyloader.bridge.launchwrapper.LegacyLaunch;
import com.myname.legacyloader.bridge.registry.LegacyGameRegistry;
import com.myname.legacyloader.core.LegacyClassLoader;
import com.myname.legacyloader.core.ModScanner;

import com.myname.legacyloader.bridge.tileentity.LegacyTileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Mod("legacyloader")
public class LegacyLoaderMod {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final String FOLDER_NAME = "minecraft1.7.10mods";
    public static String CURRENT_LOADING_MODID = "unknown";
    private static final Map<ResourceLocation, Map<Integer, String[]>> BLOCK_FACE_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, Map<Integer, List<LegacyRenderBlocks.CapturedElement>>> BLOCK_RENDER_ELEMENTS = new HashMap<>();

    private final List<File> loadedJars = new ArrayList<>();
    private final Map<File, String> jarToModIdMap = new HashMap<>();
    private final List<ModContainer> modContainers = new ArrayList<>();

    private boolean modsLoaded = false;
    private boolean preInitExecuted = false;

    private LegacyClassLoader legacyClassLoader;

    private static class ModContainer {
        final String modId;
        final Object instance;
        ModContainer(String modId, Object instance) {
            this.modId = modId;
            this.instance = instance;
        }
    }

    @SuppressWarnings("removal")
    public LegacyLoaderMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        modBus.addListener(EventPriority.HIGHEST, this::onRegister);
        modBus.addListener(this::addPackFinders);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(this::onModifyBakingResult);
            modBus.addListener(this::onRegisterBlockEntityRenderers);
            modBus.addListener(this::onRegisterClientExtensions);
        }
        NeoForge.EVENT_BUS.register(this);
        scanLegacyMods();
    }

    /**
     * 共有 LegacyTileEntity 型に、1.7.10 TESR を毎フレーム呼ぶディスパッチャを登録する。
     * これで AsphaltMod のカラーコーン/道路灯/電光掲示板等 (OBJ を TESR で描く 3D ブロック) が
     * 現行 1.21 でも立体表示される。
     */
    private void onRegisterBlockEntityRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.myname.legacyloader.bridge.tileentity.LegacyTileEntity.LEGACY_TYPE,
                ctx -> new com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyBlockEntityDispatcher());
    }

    /**
     * TESR 専用ブロック (getRenderType()==-1) の BlockItem に BEWLR
     * ({@link com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer}) を登録。
     * インベントリモデルは {@link #onModifyBakingResult} で isCustomRenderer=true に差し替えるので、
     * ItemRenderer がこの BEWLR を呼んで OBJ を立体表示する。
     */
    private void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        java.util.List<Item> tesrItems = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof net.minecraft.world.item.BlockItem blockItem
                    && LegacyRenderingRegistry.getRenderType(blockItem.getBlock()) == -1) {
                tesrItems.add(item);
            }
        }
        if (tesrItems.isEmpty()) return;
        var extensions = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer();
                }
                return renderer;
            }
        };
        event.registerItem(extensions, tesrItems.toArray(new Item[0]));
        LOGGER.info("LegacyLoader: Registered TESR item renderer for {} items", tesrItems.size());
    }

    private void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        int tesrItemModels = 0;
        net.minecraft.client.renderer.block.model.ItemTransforms blockTransforms = standardBlockTransforms(event);
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            int renderId = LegacyRenderingRegistry.getRenderType(block);
            if (renderId == 0) continue;
            if (renderId == -1) {
                // TESR 専用ブロック: ブロックモデルは空 (OBJ は TESR が描く)。アイテムは
                // isCustomRenderer=true のモデルに差し替え、BEWLR (LegacyTesrItemRenderer) に描かせる。
                ModelResourceLocation invId = new ModelResourceLocation(id, "inventory");
                BakedModel original = event.getModels().get(invId);
                if (original != null
                        && !(original instanceof com.myname.legacyloader.bridge.client.renderer.LegacyTesrItemBakedModel)) {
                    event.getModels().put(invId,
                            new com.myname.legacyloader.bridge.client.renderer.LegacyTesrItemBakedModel(
                                    original, blockTransforms));
                    tesrItemModels++;
                }
                continue;
            }
            LegacySimpleBlockRenderingHandler handler = LegacyRenderingRegistry.getBlockHandler(renderId);
            if (handler == null) continue;
            for (int meta = 0; meta < 16; meta++) {
                if (wrapLegacyModel(event, id, block, renderId, handler, "metadata=" + meta)) wrapped++;
            }
            if (wrapLegacyModel(event, id, block, renderId, handler, "")) wrapped++;
            if (wrapLegacyModel(event, id, block, renderId, handler, "inventory")) wrapped++;
        }
        LOGGER.info("LegacyLoader: Wrapped {} legacy ISBRH baked block models, {} TESR item models",
                wrapped, tesrItemModels);
    }

    /** 標準ブロック (stone) の表示変換を借りる。GUI での 30/225 度回転・0.625 縮小など。 */
    private net.minecraft.client.renderer.block.model.ItemTransforms standardBlockTransforms(
            ModelEvent.ModifyBakingResult event) {
        BakedModel stone = event.getModels().get(new ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), "inventory"));
        return stone != null ? stone.getTransforms()
                : net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
    }

    private boolean wrapLegacyModel(ModelEvent.ModifyBakingResult event, ResourceLocation id, Block block, int renderId,
                                 LegacySimpleBlockRenderingHandler handler, String variant) {
        ModelResourceLocation modelId = new ModelResourceLocation(id, variant);
        BakedModel original = event.getModels().get(modelId);
        if (original == null || original instanceof LegacyISBRHBakedModel) return false;
        event.getModels().put(modelId, new LegacyISBRHBakedModel(original, id, block, renderId, handler, event.getTextureGetter()));
        return true;
    }

    @SubscribeEvent
    public void onChunkDataLoad(ChunkDataEvent.Load event) {
        // Do not run legacy generators from chunk-load events. Existing worlds can contain many
        // untagged chunks, and synchronous retro-generation here can leave world loading at 0%.
    }

    @SubscribeEvent
    public void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        for (LegacyIFuelHandler handler : LegacyGameRegistry.getFuelHandlers()) {
            try {
                int time = handler.getBurnTime(event.getItemStack());
                if (time > 0) {
                    event.setBurnTime(time);
                    return;
                }
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: FuelHandler error", e);
            }
        }
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        LegacyRecipeManager.injectInto(event.getServer().getRecipeManager());
    }

    private void scanLegacyMods() {
        LOGGER.info("LegacyLoader: Scanning for 1.7.10 mods...");
        File modDir = new File(FMLPaths.GAMEDIR.get().toFile(), FOLDER_NAME);
        if (!modDir.exists()) { modDir.mkdirs(); return; }
        File[] jarFiles = modDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) return;

        List<URL> urls = new ArrayList<>();
        for (File jar : jarFiles) {
            try {
                urls.add(jar.toURI().toURL());
                loadedJars.add(jar);
            } catch (MalformedURLException e) { e.printStackTrace(); }
        }
        legacyClassLoader = new LegacyClassLoader(urls.toArray(new URL[0]), this.getClass().getClassLoader());
        LegacyLaunch.init(legacyClassLoader);
    }

    private void loadLegacyMods() {
        if (modsLoaded) return;
        modsLoaded = true;
        if (legacyClassLoader == null) return;

        for (File jarFile : loadedJars) {
            try {
                URL url = jarFile.toURI().toURL();
                List<ModScanner.ModInfo> modInfos = ModScanner.scanForModInfos(url);
                if (!modInfos.isEmpty()) {
                    jarToModIdMap.put(jarFile, safeNamespace(modInfos.get(0).modId));
                    for (ModScanner.ModInfo modInfo : modInfos) {
                        String modId = modInfo.modId.toLowerCase(Locale.ROOT);
                        CURRENT_LOADING_MODID = modId;
                        LOGGER.info("LegacyLoader: Found legacy mod {} in {}", modId, jarFile.getName());
                        try {
                            Class<?> modClass = legacyClassLoader.loadClass(modInfo.mainClass);
                            Object instance = modClass.getDeclaredConstructor().newInstance();
                            injectModInstanceField(instance, modClass);
                            injectSidedProxy(instance, modClass, legacyClassLoader);
                            injectModMetadata(instance, modClass, modId);
                            modContainers.add(new ModContainer(modId, instance));
                        } catch (Exception e) {
                            LOGGER.error("LegacyLoader: Failed to load " + modInfo.mainClass, e);
                        }
                    }
                } else {
                    String fallbackId = safeNamespace("legacy_" + jarFile.getName().toLowerCase(Locale.ROOT)
                            .replace(".jar", ""));
                    jarToModIdMap.put(jarFile, fallbackId);
                }
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Error processing jar: " + jarFile.getName(), e);
            }
        }
        modContainers.sort(Comparator.comparingInt(container -> legacyModPriority(container.modId)));
        injectStandaloneSidedProxies();
        CURRENT_LOADING_MODID = "unknown";
    }

    private void injectStandaloneSidedProxies() {
        String[][] proxies = {
                {"buildcraft.core.proxy.CoreProxy", "buildcraft.core.proxy.CoreProxyClient", "buildcraft.core.proxy.CoreProxy"},
                {"buildcraft.factory.FactoryProxy", "buildcraft.factory.FactoryProxyClient", "buildcraft.factory.FactoryProxy"},
                {"buildcraft.builders.BuilderProxy", "buildcraft.builders.BuilderProxyClient", "buildcraft.builders.BuilderProxy"},
                {"buildcraft.transport.TransportProxy", "buildcraft.transport.TransportProxyClient", "buildcraft.transport.TransportProxy"},
                {"buildcraft.energy.EnergyProxy", "buildcraft.energy.EnergyProxyClient", "buildcraft.energy.EnergyProxy"},
                {"buildcraft.silicon.SiliconProxy", "buildcraft.silicon.SiliconProxyClient", "buildcraft.silicon.SiliconProxy"},
                {"buildcraft.robotics.RoboticsProxy", "buildcraft.robotics.RoboticsProxyClient", "buildcraft.robotics.RoboticsProxy"}
        };
        for (String[] proxy : proxies) {
            try {
                Class<?> holder = legacyClassLoader.loadClass(proxy[0]);
                Field field = holder.getDeclaredField("proxy");
                field.setAccessible(true);
                if (field.get(null) != null) continue;
                // proxy[1] = client-side proxy, proxy[2] = server-side proxy
                String target = (FMLEnvironment.dist == Dist.CLIENT) ? proxy[1] : proxy[2];
                try {
                    Class<?> proxyClass = legacyClassLoader.loadClass(target);
                    field.set(null, proxyClass.getDeclaredConstructor().newInstance());
                } catch (Throwable t) {
                    // Fall back to server proxy if client proxy fails to load
                    Class<?> proxyClass = legacyClassLoader.loadClass(proxy[2]);
                    field.set(null, proxyClass.getDeclaredConstructor().newInstance());
                }
            } catch (Throwable e) {
                LOGGER.warn("LegacyLoader: Failed standalone proxy injection for " + proxy[0], e);
            }
        }
    }

    private int legacyModPriority(String modId) {
        if (modId == null) return 1000;
        String id = modId.toLowerCase(Locale.ROOT);
        if ("buildcraft|core".equals(id)) return 0;
        if (id.startsWith("buildcraft|")) return 100;
        return 500;
    }

    private void executePreInit() {
        if (preInitExecuted) return;
        preInitExecuted = true;
        for (ModContainer container : modContainers) {
            try {
                CURRENT_LOADING_MODID = container.modId;
                invokeEventHandler(container.instance, "PreInit", new LegacyPreInitEvent());
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Failed PreInit for mod: " + container.modId, e);
            }
        }
        CURRENT_LOADING_MODID = "unknown";
    }

    private void injectSidedProxy(Object modInstance, Class<?> modClass, ClassLoader loader) {
        for (Field field : modClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(LegacySidedProxy.class)) {
                LegacySidedProxy annotation = field.getAnnotation(LegacySidedProxy.class);
                String targetClassName = FMLEnvironment.dist == Dist.CLIENT ?
                        annotation.clientSide() : annotation.serverSide();
                if (targetClassName == null || targetClassName.isEmpty())
                    targetClassName = annotation.serverSide();
                if (targetClassName == null || targetClassName.isEmpty()) continue;
                try {
                    Class<?> proxyClass = loader.loadClass(targetClassName);
                    Object proxyInstance = proxyClass.getDeclaredConstructor().newInstance();
                    field.setAccessible(true);
                    field.set(modInstance, proxyInstance);
                } catch (Exception e) {
                    LOGGER.error("LegacyLoader: Failed to inject SidedProxy: " + targetClassName, e);
                }
            }
        }
    }

    private void injectModMetadata(Object modInstance, Class<?> modClass, String modId) {
        for (Field field : modClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(LegacyModMetadataAnnotation.class)) {
                try {
                    LegacyModMetadata metadata = new LegacyModMetadata();
                    metadata.modId = modId;
                    metadata.name = modId;
                    metadata.description = "Loaded via LegacyLoader";
                    metadata.version = "1.0.0";
                    field.setAccessible(true);
                    field.set(modInstance, metadata);
                } catch (Exception e) {
                    LOGGER.error("LegacyLoader: Failed to inject ModMetadata", e);
                }
            }
        }
    }

    private void injectModInstanceField(Object modInstance, Class<?> modClass) {
        for (Field field : modClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(LegacyModInstanceAnnotation.class)) {
                try {
                    field.setAccessible(true);
                    field.set(null, modInstance);
                } catch (Exception e) {
                    LOGGER.error("LegacyLoader: Failed to inject Mod Instance", e);
                }
            }
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        for (ModContainer container : modContainers) {
            try {
                CURRENT_LOADING_MODID = container.modId;
                invokeEventHandler(container.instance, "Init", new LegacyInitEvent());
                invokeEventHandler(container.instance, "PostInit", new LegacyPostInitEvent());
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Error during lifecycle event for " + container.modId, e);
            }
        }
        CURRENT_LOADING_MODID = "unknown";
        fireLegacyTextureStitchEvents();
    }

    private void fireLegacyTextureStitchEvents() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        LegacyTextureStitchEvent.Pre blockPre = new LegacyTextureStitchEvent.Pre(new LegacyTextureMap(0));
        LegacyTextureStitchEvent.Pre itemPre = new LegacyTextureStitchEvent.Pre(new LegacyTextureMap(1));
        LegacyTextureStitchEvent.Post blockPost = new LegacyTextureStitchEvent.Post(new LegacyTextureMap(0));
        LegacyTextureStitchEvent.Post itemPost = new LegacyTextureStitchEvent.Post(new LegacyTextureMap(1));
        for (var bus : List.of(LegacyMinecraftForge.EVENT_BUS, LegacyFMLCommonHandler.instance().bus())) {
            try {
                bus.post(blockPre);
                bus.post(itemPre);
                bus.post(blockPost);
                bus.post(itemPost);
            } catch (Throwable t) {
                LOGGER.warn("LegacyLoader: Failed to post legacy texture stitch event", t);
            }
        }
    }

    private void invokeEventHandler(Object mod, String phaseName, Object eventObject) {
        for (Method method : mod.getClass().getMethods()) {
            if (method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isAssignableFrom(eventObject.getClass())) {
                try {
                    method.invoke(mod, eventObject);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    String msg = cause != null ? cause.getMessage() : null;
                    // Schematic duplicate registration is non-fatal; log as warning and continue
                    if (cause instanceof RuntimeException && msg != null && msg.contains("already associated")) {
                        LOGGER.warn("LegacyLoader [{}] {}: {}", phaseName, cause.getClass().getSimpleName(), msg);
                        continue;
                    }
                    // VerifyError from client-only stub classes is non-fatal on the server
                    if (cause instanceof VerifyError) {
                        LOGGER.warn("LegacyLoader [{}] VerifyError (client-only class stub): {}", phaseName, msg);
                        continue;
                    }
                    LOGGER.error("LegacyLoader: Error invoking {} for {}", phaseName, mod.getClass().getName(), cause != null ? cause : ite);
                } catch (VerifyError ve) {
                    LOGGER.warn("LegacyLoader [{}] VerifyError (client-only class stub): {}", phaseName, ve.getMessage());
                } catch (Exception e) {
                    LOGGER.error("LegacyLoader: Error invoking {} for {}", phaseName, mod.getClass().getName(), e);
                }
            }
        }
    }

    private void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            if (!modsLoaded) loadLegacyMods();
            if (!preInitExecuted) executePreInit();
            LegacyGameRegistry.onRegister(event);
            harvestBlockTextures();
        }
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            LegacyGameRegistry.onRegister(event);
            harvestItemTextures();
        }
        if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            // Register the shared LegacyTileEntity type so world save/load works correctly
            @SuppressWarnings("removal")
            ResourceLocation legacyTeId = ResourceLocation.fromNamespaceAndPath("legacyloader", "legacy_tile_entity");
            event.register(Registries.BLOCK_ENTITY_TYPE, legacyTeId, () -> LegacyTileEntity.LEGACY_TYPE);
        }
        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            if (!modsLoaded) loadLegacyMods();
            if (!preInitExecuted) executePreInit();
            LegacyCreativeTab.registerTabs(event);
        }
    }

    /**
     * 笘・・髱｢譖ｸ縺咲峩縺・ 繝・け繧ｹ繝√Ε蜿朱寔
     * 髫取ｮｵ繝悶Ο繝・け縺ｯ繧ｽ繝ｼ繧ｹ繝｡繧ｿ繝・・繧ｿ縺九ｉ繝・け繧ｹ繝√Ε繧呈ｱｺ螳・
     * 繝｡繧ｿ繝・・繧ｿ繝悶Ο繝・け縺ｯregisterBlockIcons縺ｧ蜿朱寔縺励◆繝・け繧ｹ繝√Ε繧剃ｽｿ逕ｨ
     * 蜊倡ｴ斐ヶ繝ｭ繝・け縺ｯlegacyTextureName繧剃ｽｿ逕ｨ
     */
    private void harvestBlockTextures() {
        for (LegacyGameRegistry.BlockEntry entry : LegacyGameRegistry.PENDING_BLOCKS) {
            try {
                if (!(entry.block instanceof ILegacyBlock)) continue;
                ILegacyBlock block = (ILegacyBlock) entry.block;

                String modid = entry.modid.toLowerCase(Locale.ROOT);
                String safeModId = modid.equals("unknown") ? "legacy_mod" : modid;
                String name = entry.name.toLowerCase(Locale.ROOT);

                @SuppressWarnings("removal")
                ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(safeModId, name);

                String legacyTex = block.getLegacyTextureName();
                String baseTextureName = legacyTex != null ? cleanTextureName(legacyTex) : null;

                // 笘・嚴谿ｵ繝悶Ο繝・け縺ｮ蜃ｦ逅・
                if (entry.block instanceof LegacyBlockStairs) {
                    LegacyBlockStairs stairs = (LegacyBlockStairs) entry.block;
                    int sourceMeta = stairs.getSourceMetadata();
                    String resolvedTex = null;
                    Map<Integer, String> stairTextures = new HashMap<>();
                    collectTextures(block, stairTextures);
                    collectFaceTextures(rl, block);

                    // 縺ｾ縺壹た繝ｼ繧ｹ繝悶Ο繝・け縺ｮMETADATA_TEXTURES繧貞盾辣ｧ・医Γ繧ｿ繝・・繧ｿ莉倥″濶ｲ髫取ｮｵ縺ｮ蝣ｴ蜷茨ｼ・
                    Block srcBlock = stairs.getSourceBlock();
                    if (srcBlock != null) {
                        ResourceLocation srcRL = com.myname.legacyloader.bridge.core.RegistryNameHelper.getRegistryName(srcBlock);
                        if (srcRL != null) {
                            Map<Integer, String> srcMeta = LegacyBlock.METADATA_TEXTURES.get(srcRL);
                            if (srcMeta != null && srcMeta.containsKey(sourceMeta)) {
                                resolvedTex = srcMeta.get(sourceMeta);
                            }
                        }
                    }

                    // 繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ: 閾ｪ霄ｫ縺ｮ繝・け繧ｹ繝√Ε蜷・+ _NN 繧ｵ繝輔ぅ繝・け繧ｹ
                    if (resolvedTex == null && baseTextureName != null) {
                        boolean sourceHasVariants = srcBlock instanceof ILegacyBlock
                                && ((ILegacyBlock) srcBlock).getMaxMetadata() > 1;
                        resolvedTex = (sourceHasVariants || sourceMeta > 0)
                                ? baseTextureName + "_" + String.format("%02d", sourceMeta)
                                : baseTextureName;
                    }
                    if (resolvedTex == null) resolvedTex = name;

                    LegacyBlock.TEXTURE_OVERRIDES.put(rl, resolvedTex);
                    collectRendererElements(rl, entry.block, 16);
                    LOGGER.debug("Stairs texture: " + rl + " -> " + resolvedTex);
                    continue;
                }

                // 笘・せ繝ｩ繝悶ヶ繝ｭ繝・け縺ｮ蜃ｦ逅・ｼ医Γ繧ｿ繝・・繧ｿ繝舌Μ繧ｨ繝ｼ繧ｷ繝ｧ繝ｳ縺ｪ縺・= 蜊倅ｸ繝・け繧ｹ繝√Ε繧ｹ繝ｩ繝厄ｼ・
                if (entry.block instanceof LegacyBlockSlab) {
                    LegacyBlockSlab slab = (LegacyBlockSlab) entry.block;

                    // registerBlockIcons縺九ｉ繝・け繧ｹ繝√Ε諠・ｱ繧貞庶髮・
                    Map<Integer, String> metaTextures = new HashMap<>();
                    collectTextures(block, metaTextures);
                    collectFaceTextures(rl, block);
                    int detectedCount = detectMetadataVariantCount(entry.block, block, metaTextures);
                    slab.setMaxVariants(Math.max(1, detectedCount));
                    if (detectedCount > 1 && baseTextureName != null) {
                        for (int i = 0; i < detectedCount; i++) {
                            metaTextures.putIfAbsent(i, baseTextureName + "_" + String.format("%02d", i));
                        }
                    }

                    if (detectedCount > 1) {
                        LegacyBlock.METADATA_TEXTURES.put(rl, metaTextures);
                        // 繝・ヵ繧ｩ繝ｫ繝医ユ繧ｯ繧ｹ繝√Ε
                        String defaultTex = metaTextures.getOrDefault(0, baseTextureName);
                        if (defaultTex != null) {
                            LegacyBlock.TEXTURE_OVERRIDES.put(rl, defaultTex);
                        }
                    } else if (baseTextureName != null) {
                        LegacyBlock.TEXTURE_OVERRIDES.put(rl, baseTextureName);
                    } else {
                        LegacyBlock.TEXTURE_OVERRIDES.put(rl, name);
                    }
                    collectRendererElements(rl, entry.block, Math.max(1, detectedCount));
                    continue;
                }

                // 笘・壼ｸｸ繝悶Ο繝・け繝ｻ繝｡繧ｿ繝・・繧ｿ繝悶Ο繝・け縺ｮ蜃ｦ逅・
                Map<Integer, String> metaTextures = new HashMap<>();
                collectTextures(block, metaTextures);
                collectFaceTextures(rl, block);

                int detectedCount = detectMetadataVariantCount(entry.block, block, metaTextures);

                // getSubBlocks縺九ｉ繧ゅΓ繧ｿ繝・・繧ｿ謨ｰ繧呈､懷・
                if (detectedCount > 1 && baseTextureName != null) {
                    for (int i = 0; i < detectedCount; i++) {
                        metaTextures.putIfAbsent(i, baseTextureName + "_" + String.format("%02d", i));
                    }
                }

                if (detectedCount > 1) {
                    LegacyBlock.METADATA_TEXTURES.put(rl, metaTextures);
                    if (entry.block instanceof LegacyBlock) {
                        ((LegacyBlock) entry.block).setMetadataCount(detectedCount);
                    }
                    String defaultTex = metaTextures.getOrDefault(0, baseTextureName);
                    if (defaultTex != null) {
                        LegacyBlock.TEXTURE_OVERRIDES.put(rl, defaultTex);
                    }
                } else {
                    // 蜊倅ｸ繝・け繧ｹ繝√Ε
                    String tex = metaTextures.getOrDefault(0, baseTextureName);
                    if (tex == null) tex = name;
                    LegacyBlock.TEXTURE_OVERRIDES.put(rl, tex);
                }
                collectRendererElements(rl, entry.block, Math.max(1, detectedCount));

            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Error harvesting texture", e);
            }
        }
    }

    /**
     * registerBlockIcons繧貞他縺ｳ蜃ｺ縺励※繝・け繧ｹ繝√Ε繧貞庶髮・
     */
    private void collectTextures(ILegacyBlock block, Map<Integer, String> metaTextures) {
        final int[] textureIndex = {0};

        LegacyIconRegister capturer = new LegacyIconRegister() {
            @Override
            public LegacyIcon registerIcon(String textureName) {
                String cleanName = cleanTextureName(textureName);
                int index = textureIndex[0]++;
                metaTextures.put(index, cleanName);
                return new LegacyIcon() {
                    @Override public int getIconWidth() { return 16; }
                    @Override public int getIconHeight() { return 16; }
                    @Override public float getMinU() { return 0; }
                    @Override public float getMaxU() { return 1; }
                    @Override public float getMinV() { return 0; }
                    @Override public float getMaxV() { return 1; }
                    @Override public String getIconName() { return cleanName; }
                };
            }
        };

        try { block.registerBlockIcons(capturer); } catch (Throwable e) { /* ignore */ }
        if (textureIndex[0] == 0) {
            try { block.func_149651_a(capturer); } catch (Throwable e) { /* ignore */ }
        }
    }

    private void collectFaceTextures(ResourceLocation rl, ILegacyBlock block) {
        Map<Integer, String[]> byMeta = new HashMap<>();
        for (int meta = 0; meta < 16; meta++) {
            String[] faces = new String[6];
            boolean found = false;
            for (int side = 0; side < 6; side++) {
                try {
                    Method method = block.getClass().getMethod("func_149691_a", int.class, int.class);
                    Object value = method.invoke(block, side, meta);
                    LegacyIcon icon = value instanceof LegacyIcon ? (LegacyIcon) value : null;
                    if (icon != null && icon.getIconName() != null && !icon.getIconName().isBlank()) {
                        faces[side] = cleanTextureName(icon.getIconName());
                        found = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (found) {
                byMeta.put(meta, faces);
            }
        }
        if (!byMeta.isEmpty()) {
            BLOCK_FACE_TEXTURES.put(rl, byMeta);
        }
    }

    private void collectRendererElements(ResourceLocation rl, Block block, int metadataCount) {
        int renderId = LegacyRenderingRegistry.getRenderType(block);
        if (renderId == 0) return;
        LegacySimpleBlockRenderingHandler handler = LegacyRenderingRegistry.getBlockHandler(renderId);
        if (handler == null) return;

        Map<Integer, List<LegacyRenderBlocks.CapturedElement>> byMeta = new HashMap<>();
        int count = Math.max(1, Math.min(16, metadataCount));
        for (int meta = 0; meta < count; meta++) {
            try {
                LegacyTessellator.clearCapturedElements();
                LegacyRenderBlocks renderer = new LegacyRenderBlocks();
                renderer.setCaptureMetadata(meta);
                handler.renderInventoryBlock(block, meta, renderId, renderer);
                // Some 1.7.10 ISBRH implementations only emit geometry from renderWorldBlock
                // (common for conveyor/escalator/pipe-like blocks). Capture that path too with
                // a lightweight metadata-aware BlockGetter so the baked fallback model is not empty.
                try {
                    renderer.setCaptureMetadata(meta);
                    com.myname.legacyloader.bridge.world.LegacySingleBlockAccess access = createCaptureAccess(block, meta);
                    updateLegacyBoundsForCapture(block, access, 0, 0, 0);
                    LegacyTessellator.beginCaptureAt(0, 0, 0);
                    renderer.setBlockAccess(access, 0, 0, 0);
                    handler.renderWorldBlock(access, 0, 0, 0, block, renderId, renderer);
                } catch (Throwable ignored) {
                }

                List<LegacyRenderBlocks.CapturedElement> elements = new ArrayList<>(renderer.getCapturedElements());
                elements.addAll(LegacyTessellator.consumeCapturedElements());
                List<LegacyRenderBlocks.CapturedElement> normalized = normalizeCapturedElements(elements);
                if (!normalized.isEmpty()) {
                    byMeta.put(meta, normalized);
                }
            } catch (Throwable t) {
                LOGGER.debug("LegacyLoader: Failed to capture legacy renderer for " + rl + " meta " + meta, t);
            } finally {
                LegacyTessellator.clearCapturedElements();
            }
        }
        if (!byMeta.isEmpty()) {
            BLOCK_RENDER_ELEMENTS.put(rl, byMeta);
        }
    }

    private static void updateLegacyBoundsForCapture(Block block, com.myname.legacyloader.bridge.world.LegacySingleBlockAccess access,
                                                     int x, int y, int z) {
        if (block instanceof LegacyBlock legacyBlock) {
            try {
                legacyBlock.func_149719_a(access, x, y, z);
            } catch (Throwable ignored) {
            }
        }
    }

    private static com.myname.legacyloader.bridge.world.LegacySingleBlockAccess createCaptureAccess(Block block, int meta) {
        String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
        boolean wireLine = className.contains("wireline");
        boolean mimicry = className.contains("mimicry");
        return new com.myname.legacyloader.bridge.world.LegacySingleBlockAccess(block, meta, wireLine, mimicry);
    }

    private List<LegacyRenderBlocks.CapturedElement> normalizeCapturedElements(List<LegacyRenderBlocks.CapturedElement> elements) {
        Map<String, LegacyRenderBlocks.CapturedElement> dedup = new LinkedHashMap<>();
        for (LegacyRenderBlocks.CapturedElement element : elements) {
            if (element == null) continue;
            if (element.maxX <= element.minX || element.maxY <= element.minY || element.maxZ <= element.minZ) continue;
            String key = roundElementCoord(element.minX) + "," + roundElementCoord(element.minY) + "," +
                    roundElementCoord(element.minZ) + "," + roundElementCoord(element.maxX) + "," +
                    roundElementCoord(element.maxY) + "," + roundElementCoord(element.maxZ);
            dedup.putIfAbsent(key, element);
            if (dedup.size() >= 96) break;
        }
        return new ArrayList<>(dedup.values());
    }

    private String roundElementCoord(double value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private void harvestItemTextures() {
        for (LegacyItem item : LegacyItem.ALL_INSTANCES) {
            try {
                ResourceLocation rl = RegistryNameHelper.getRegistryName(item);
                if (rl == null) continue;

                Map<Integer, String> metaTextures = new HashMap<>();
                collectItemTextures(item, metaTextures);
                int detectedCount = Math.max(metaTextures.size(), detectMetadataCountFromSubItems(item));
                for (int meta = 0; meta < Math.max(1, detectedCount); meta++) {
                    String iconTexture = resolveItemIconTexture(item, meta);
                    if (iconTexture != null && !iconTexture.isBlank()) {
                        metaTextures.put(meta, iconTexture);
                    }
                }

                String baseTextureName = item.legacyTextureName != null ? cleanTextureName(item.legacyTextureName) : rl.getPath();
                if (detectedCount > 1) {
                    if (baseTextureName != null) {
                        for (int i = 0; i < detectedCount; i++) {
                            metaTextures.putIfAbsent(i, baseTextureName + "_" + String.format("%02d", i));
                        }
                    }
                    LegacyItem.METADATA_TEXTURES.put(rl, metaTextures);
                    LegacyItem.TEXTURE_OVERRIDES.put(rl, metaTextures.getOrDefault(0, baseTextureName));
                } else {
                    LegacyItem.TEXTURE_OVERRIDES.put(rl, baseTextureName);
                }
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Error harvesting item texture", e);
            }
        }
        for (Map.Entry<Item, ResourceLocation> entry : LegacyGameRegistry.getPendingItemRegistryNames().entrySet()) {
            try {
                Item item = entry.getKey();
                ResourceLocation rl = entry.getValue();
                if (item == null || rl == null || LegacyItem.TEXTURE_OVERRIDES.containsKey(rl)) continue;
                String legacyTex = readLegacyItemTextureName(item);
                String iconTexture = resolveItemIconTexture(item, 0);
                String baseTextureName = iconTexture != null ? iconTexture : (legacyTex != null ? cleanTextureName(legacyTex) : rl.getPath());
                LegacyItem.TEXTURE_OVERRIDES.put(rl, baseTextureName);
            } catch (Exception e) {
                LOGGER.error("LegacyLoader: Error harvesting generic item texture", e);
            }
        }
    }

    private String readLegacyItemTextureName(Item item) {
        Class<?> current = item.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField("legacyTextureName");
                field.setAccessible(true);
                Object value = field.get(item);
                if (value instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
        try {
            Method method = item.getClass().getMethod("func_111208_A");
            Object value = method.invoke(item);
            if (value instanceof String s && !s.isEmpty()) return s;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void collectItemTextures(LegacyItem item, Map<Integer, String> metaTextures) {
        final int[] textureIndex = {0};
        LegacyIconRegister capturer = new LegacyIconRegister() {
            @Override
            public LegacyIcon registerIcon(String textureName) {
                String cleanName = cleanTextureName(textureName);
                metaTextures.put(textureIndex[0]++, cleanName);
                return new LegacyIcon() {
                    @Override public int getIconWidth() { return 16; }
                    @Override public int getIconHeight() { return 16; }
                    @Override public float getMinU() { return 0; }
                    @Override public float getMaxU() { return 1; }
                    @Override public float getMinV() { return 0; }
                    @Override public float getMaxV() { return 1; }
                    @Override public String getIconName() { return cleanName; }
                };
            }
        };

        try { item.func_94581_a(capturer); } catch (Throwable e) { /* ignore */ }
    }

    private String resolveItemIconTexture(Item item, int metadata) {
        if (item == null) return null;
        for (String methodName : List.of("func_77617_a", "getIconFromDamage")) {
            try {
                Method method = item.getClass().getMethod(methodName, int.class);
                Object value = method.invoke(item, metadata);
                if (value instanceof LegacyIcon icon && icon.getIconName() != null && !icon.getIconName().isBlank()) {
                    return cleanTextureName(icon.getIconName());
                }
            } catch (Throwable ignored) {
            }
        }
        String buildCraftPipeIcon = resolveBuildCraftPipeIconTexture(item);
        if (buildCraftPipeIcon != null) return buildCraftPipeIcon;
        return null;
    }

    private String resolveBuildCraftPipeIconTexture(Item item) {
        try {
            if (!item.getClass().getName().equals("buildcraft.transport.ItemPipe")) return null;
            int index = readIntField(item, "pipeIconIndex", -1);
            if (index < 0) return null;
            Class<?> typeClass = Class.forName("buildcraft.transport.PipeIconProvider$TYPE", false, item.getClass().getClassLoader());
            Object[] values = typeClass.getEnumConstants();
            if (values == null || index >= values.length) return null;
            String iconTag = readStringField(values[index], "iconTag");
            if (iconTag == null || iconTag.isBlank()) return null;
            String fullName = iconTag.contains(":") ? "buildcraft" + iconTag : "buildcrafttransport:pipes/" + iconTag;
            return cleanTextureName(fullName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int readIntField(Object target, String fieldName, int fallback) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) return fallback;
            field.setAccessible(true);
            return ((Number) field.get(target)).intValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String readStringField(Object target, String fieldName) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) return null;
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof String s ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private int detectMetadataCountFromSubItems(LegacyItem item) {
        try {
            List<ItemStack> testList = new ArrayList<>();
            for (Method method : item.getClass().getMethods()) {
                if (!method.getName().equals("func_150895_a") && !method.getName().equals("getSubItems")) continue;
                Class<?>[] paramTypes = method.getParameterTypes();
                try {
                    if (paramTypes.length == 3 && List.class.isAssignableFrom(paramTypes[2])) {
                        method.invoke(item, item, null, testList);
                    } else if (paramTypes.length == 0 && List.class.isAssignableFrom(method.getReturnType())) {
                        Object result = method.invoke(item);
                        if (result instanceof List<?>) testList.addAll((List<ItemStack>) result);
                    }
                } catch (Throwable t) { /* ignore */ }
                if (!testList.isEmpty()) return testList.size();
            }
        } catch (Throwable t) { /* ignore */ }
        return item.getHasSubtypes() ? 16 : 1;
    }

    private int detectMetadataCountFromSubBlocks(Block block) {
        try {
            List<ItemStack> testList = new ArrayList<>();
            String[] methodNames = {"func_149666_a", "getSubBlocks"};

            for (String methodName : methodNames) {
                Class<?> current = block.getClass();
                while (current != null && current != Object.class) {
                    String className = current.getName();
                    if (className.startsWith("com.myname.legacyloader.bridge") ||
                            className.startsWith("net.minecraft.world.level.block.Block")) break;
                    try {
                        for (Method method : current.getDeclaredMethods()) {
                            if (!method.getName().equals(methodName)) continue;
                            Class<?>[] paramTypes = method.getParameterTypes();
                            if (paramTypes.length != 3 || !List.class.isAssignableFrom(paramTypes[2])) continue;
                            method.setAccessible(true);
                            try {
                                method.invoke(block, Items.STONE, null, testList);
                                if (!testList.isEmpty()) return testList.size();
                            } catch (Throwable t) { /* ignore */ }
                        }
                    } catch (Throwable t) { /* ignore */ }
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable t) { /* ignore */ }
        return 1;
    }

    private int detectMetadataVariantCount(Block blockInstance, ILegacyBlock legacyBlock, Map<Integer, String> metaTextures) {
        int detectedCount = detectMetadataCountFromSubBlocks(blockInstance);
        if (legacyBlock != null) {
            detectedCount = Math.max(detectedCount, legacyBlock.getMaxMetadata());
        }
        if (detectedCount <= 1 && looksLikeMetadataTextureSet(metaTextures)) {
            detectedCount = metaTextures.size();
        }
        return detectedCount;
    }

    private boolean looksLikeMetadataTextureSet(Map<Integer, String> metaTextures) {
        if (metaTextures == null || metaTextures.size() <= 1) return false;
        for (String tex : metaTextures.values()) {
            if (tex != null && tex.matches(".*_\\d{2}$")) return true;
        }
        return false;
    }

    private String cleanTextureName(String name) {
        if (name == null) return null;
        if (name.startsWith("/")) name = name.substring(1);
        if (name.contains(":")) {
            String[] split = name.split(":", 2);
            if (split.length > 1) name = split[1];
        }
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        name = name.toLowerCase(Locale.ROOT).replace("//", "/");
        // Strip legacy folder prefixes used in 1.7.10 texture paths
        if (name.startsWith("blocks/")) name = name.substring("blocks/".length());
        else if (name.startsWith("items/")) name = name.substring("items/".length());
        return name;
    }

    private static String safeNamespace(String id) {
        if (id == null || id.isBlank()) return "legacy_mod";
        String safe = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return safe.isBlank() ? "legacy_mod" : safe;
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            for (File jar : loadedJars) {
                String modId = safeNamespace(jarToModIdMap.getOrDefault(jar, "legacy_mod"));
                String packId = "legacy_" + modId;
                PackResources wrappedRes = new Legacy1710PackResources(packId, modId, jar);
                PackLocationInfo location = new PackLocationInfo(
                        packId,
                        Component.literal("Legacy 1.7.10: " + jar.getName()),
                        PackSource.BUILT_IN,
                        Optional.empty()
                );
                Pack pack = Pack.readMetaAndCreate(
                        location,
                        new Pack.ResourcesSupplier() {
                            @Override
                            public PackResources openPrimary(PackLocationInfo ignored) {
                                return wrappedRes;
                            }

                            @Override
                            public PackResources openFull(PackLocationInfo ignored, Pack.Metadata metadata) {
                                return wrappedRes;
                            }
                        },
                        PackType.CLIENT_RESOURCES,
                        new PackSelectionConfig(true, Pack.Position.TOP, true)
                );
                if (pack != null) {
                    event.addRepositorySource((consumer) -> consumer.accept(pack));
                }
            }
        }
    }

    // ========================================
    // 笘・・髱｢譖ｸ縺咲峩縺・ Legacy1710PackResources
    // ========================================

    private static class Legacy1710PackResources implements PackResources {
        private final String packId;
        private final String ownerModId;
        private final File jarFile;
        private final PackLocationInfo locationInfo;
        private final Set<String> availableNamespaces = new HashSet<>();
        private final Map<String, String> lowerToRealPath = new HashMap<>();
        private final Map<String, String> lowerNamespacedToRealPath = new HashMap<>();

        public Legacy1710PackResources(String packId, String modId, File jarFile) {
            this.packId = packId;
            this.ownerModId = modId;
            this.jarFile = jarFile;
            this.locationInfo = new PackLocationInfo(packId, Component.literal("Legacy Resources"), PackSource.BUILT_IN, Optional.empty());
            scanJarContents();
            this.availableNamespaces.add(this.ownerModId);
            this.availableNamespaces.addAll(namespaceCandidates(this.ownerModId));
            this.availableNamespaces.add("legacy_mod");
            // expose both 1.7.10 asset domains (buildcrafttransport) and synthetic registry domains
            // (buildcraft_transport) so generated blockstates/models can be discovered by 1.21.
            for (String ns : new ArrayList<>(this.availableNamespaces)) {
                this.availableNamespaces.addAll(namespaceCandidates(ns));
            }
        }

        private void scanJarContents() {
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String realPath = entry.getName();
                    String lowerPath = realPath.toLowerCase(Locale.ROOT);

                    if (lowerPath.startsWith("assets/")) {
                        String[] parts = lowerPath.split("/", 3);
                        if (parts.length == 3) {
                            String namespace = parts[1];
                            String internalPath = parts[2];
                            availableNamespaces.add(namespace);
                            putResourceAlias(namespace, internalPath, realPath);

                            // 1.7.10 竊・1.20.1 繝代せ螟画鋤
                            if (internalPath.startsWith("textures/blocks/")) {
                                putResourceAlias(namespace,
                                        internalPath.replace("textures/blocks/", "textures/block/"),
                                        realPath);
                            }
                            if (internalPath.startsWith("textures/items/")) {
                                putResourceAlias(namespace,
                                        internalPath.replace("textures/items/", "textures/item/"),
                                        realPath);
                            }
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        private void putResourceAlias(String namespace, String internalPath, String realPath) {
            String lowerNamespace = namespace.toLowerCase(Locale.ROOT);
            String lowerInternal = internalPath.toLowerCase(Locale.ROOT);
            lowerNamespacedToRealPath.put(lowerNamespace + ":" + lowerInternal, realPath);
            lowerToRealPath.putIfAbsent(lowerInternal, realPath);
        }

        private List<String> namespaceCandidates(String namespace) {
            String ns = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
            LinkedHashSet<String> out = new LinkedHashSet<>();
            if (!ns.isBlank()) out.add(ns);
            if (ns.startsWith("buildcraft_")) out.add(ns.replace("buildcraft_", "buildcraft"));
            if (ns.equals("buildcraftbuilders")) out.add("buildcraft_builders");
            if (ns.equals("buildcraftcore")) out.add("buildcraft_core");
            if (ns.equals("buildcrafttransport")) out.add("buildcraft_transport");
            if (ns.equals("buildcraftenergy")) out.add("buildcraft_energy");
            if (ns.equals("buildcraftfactory")) out.add("buildcraft_factory");
            if (ns.equals("buildcraftrobotics")) out.add("buildcraft_robotics");
            if (ns.equals("buildcraftsilicon")) out.add("buildcraft_silicon");
            if (ownerModId != null) out.add(ownerModId.toLowerCase(Locale.ROOT));
            out.add("legacy_mod");
            return new ArrayList<>(out);
        }

        /**
         * 笘・ｸ蠢・ 繝悶Ο繝・け蜷阪°繧峨ユ繧ｯ繧ｹ繝√Ε繧定ｧ｣豎ｺ
         * 髫取ｮｵ: 繧ｽ繝ｼ繧ｹ繝｡繧ｿ繝・・繧ｿ縺九ｉ繝・け繧ｹ繝√Ε蜷阪ｒ豎ｺ螳・
         * 繧ｹ繝ｩ繝・ 繝・け繧ｹ繝√Ε蜷阪ｒ縺昴・縺ｾ縺ｾ菴ｿ逕ｨ
         * 騾壼ｸｸ繝悶Ο繝・け: TEXTURE_OVERRIDES縺九ｉ蜿門ｾ・
         */
        private String resolveTexture(String ns, String blockName) {
            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);

            // TEXTURE_OVERRIDES縺九ｉ蜿門ｾ・
            String tex = LegacyBlock.TEXTURE_OVERRIDES.get(blockRL);
            if (tex != null) {
                // 繝・け繧ｹ繝√Ε繝輔ぃ繧､繝ｫ縺悟ｭ伜惠縺吶ｋ縺狗｢ｺ隱・
                if (hasTexture(ns, tex)) return tex;

                // _flip 繧ｵ繝輔ぅ繝・け繧ｹ縺ｪ縺励〒隧ｦ縺・
                String noFlip = tex.replace("_flip", "");
                if (!noFlip.equals(tex) && hasTexture(ns, noFlip)) return noFlip;
            }

            // 繝悶Ο繝・け蜷阪◎縺ｮ縺ｾ縺ｾ縺ｧ隧ｦ縺・
            if (hasTexture(ns, blockName)) return blockName;

            // 繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ: _00 繧ｵ繝輔ぅ繝・け繧ｹ繧定ｿｽ蜉
            if (hasTexture(ns, blockName + "_00")) return blockName + "_00";

            return tex != null ? tex : blockName;
        }

        private boolean hasTexture(String texName) {
            return lowerToRealPath.containsKey("textures/block/" + texName + ".png");
        }

        private boolean hasTexture(String namespace, String texName) {
            if (texName == null) return false;
            String localNamespace = namespace;
            String localName = cleanTextureName(texName);
            if (localName != null && localName.contains(":")) {
                String[] split = localName.split(":", 2);
                localNamespace = split[0];
                localName = split[1];
            }
            String path = "textures/block/" + localName + ".png";
            String oldPath = "textures/blocks/" + localName + ".png";
            for (String ns : namespaceCandidates(localNamespace)) {
                if (lowerNamespacedToRealPath.containsKey(ns + ":" + path)
                        || lowerNamespacedToRealPath.containsKey(ns + ":" + oldPath)) return true;
            }
            return lowerToRealPath.containsKey(path) || lowerToRealPath.containsKey(oldPath);
        }

        private String textureReference(String namespace, String texName, boolean item) {
            return textureReference(namespace, texName, item, null);
        }

        private String textureReference(String namespace, String texName, boolean item, String contextName) {
            String folder = item ? "item" : "block";
            String localNamespace = namespace;
            String localName = texName;
            if (localName == null || localName.isBlank()) localName = contextName;
            if (localName == null || localName.isBlank()) localName = "missing";
            localName = cleanTextureName(localName);

            if (localName != null && localName.contains(":")) {
                String[] split = localName.split(":", 2);
                localNamespace = split[0].toLowerCase(Locale.ROOT);
                localName = split.length > 1 ? split[1] : "";
            } else if (texName != null && texName.contains(":")) {
                String[] split = texName.split(":", 2);
                localNamespace = split[0].toLowerCase(Locale.ROOT);
                localName = cleanTextureName(split[1]);
            }

            String resolved = resolveLocalTextureName(localNamespace, localName, item);
            if (resolved != null) {
                return textureNamespaceFor(localNamespace, resolved, item) + ":" + folder + "/" + resolved;
            }
            if (item) {
                resolved = resolveLocalTextureName(localNamespace, localName, false);
                if (resolved != null) {
                    return textureNamespaceFor(localNamespace, resolved, false) + ":block/" + resolved;
                }
            }

            if (contextName != null) {
                for (String candidate : legacyTextureCandidates(contextName)) {
                    resolved = resolveLocalTextureName(localNamespace, candidate, item);
                    if (resolved != null) {
                        return textureNamespaceFor(localNamespace, resolved, item) + ":" + folder + "/" + resolved;
                    }
                    if (item) {
                        resolved = resolveLocalTextureName(localNamespace, candidate, false);
                        if (resolved != null) {
                            return textureNamespaceFor(localNamespace, resolved, false) + ":block/" + resolved;
                        }
                    }
                    String vanilla = item ? mapLegacyVanillaItemTexture(candidate) : mapLegacyVanillaBlockTexture(candidate);
                    if (vanilla != null) {
                        return "minecraft:" + folder + "/" + vanilla;
                    }
                }
            }

            String vanilla = item ? mapLegacyVanillaItemTexture(localName) : mapLegacyVanillaBlockTexture(localName);
            if (vanilla != null) {
                return "minecraft:" + folder + "/" + vanilla;
            }

            return item ? "minecraft:item/barrier" : "minecraft:block/stone";
        }

        private String textureNamespaceFor(String namespace, String texName, boolean item) {
            String localNamespace = namespace == null || namespace.isBlank() ? ownerModId : namespace.toLowerCase(Locale.ROOT);
            String localName = cleanTextureName(texName);
            if (localName != null && localName.contains(":")) {
                String[] split = localName.split(":", 2);
                localNamespace = split[0];
                localName = split.length > 1 ? split[1] : "";
            }
            if (localName == null || localName.isBlank()) return localNamespace;

            String modernPath = "textures/" + (item ? "item" : "block") + "/" + localName + ".png";
            String legacyPath = "textures/" + (item ? "items" : "blocks") + "/" + localName + ".png";
            for (String ns : namespaceCandidates(localNamespace)) {
                if (lowerNamespacedToRealPath.containsKey(ns + ":" + modernPath)
                        || lowerNamespacedToRealPath.containsKey(ns + ":" + legacyPath)) {
                    return ns;
                }
            }
            for (String namespacedPath : lowerNamespacedToRealPath.keySet()) {
                int separator = namespacedPath.indexOf(':');
                if (separator <= 0) continue;
                if (namespacedPath.endsWith(":" + modernPath) || namespacedPath.endsWith(":" + legacyPath)) {
                    return namespacedPath.substring(0, separator);
                }
            }
            return localNamespace;
        }

        private String resolveLocalTextureName(String namespace, String texName, boolean item) {
            if (texName == null || texName.isBlank() || "missing".equals(texName)) return null;
            if (item ? hasItemTexture(namespace, texName) : hasTexture(namespace, texName)) return texName;
            String normalized = normalizeAluminiumSpelling(texName);
            if (!normalized.equals(texName) && (item ? hasItemTexture(namespace, normalized) : hasTexture(namespace, normalized))) {
                return normalized;
            }
            String base = stripLegacyMetaSuffix(texName);
            if (!base.equals(texName) && (item ? hasItemTexture(namespace, base) : hasTexture(namespace, base))) {
                return base;
            }
            String noFlip = texName.replace("_flip", "");
            if (!noFlip.equals(texName) && (item ? hasItemTexture(namespace, noFlip) : hasTexture(namespace, noFlip))) {
                return noFlip;
            }
            if (!item) {
                for (String suffix : List.of("_side", "_top", "_front", "_bottom", "_off", "_on")) {
                    String candidate = texName + suffix;
                    if (hasTexture(namespace, candidate)) return candidate;
                }
                for (String suffix : List.of("_off", "_on")) {
                    if (texName.endsWith(suffix)) {
                        String stripped = texName.substring(0, texName.length() - suffix.length());
                        if (hasTexture(namespace, stripped)) return stripped;
                    }
                }
            }
            return null;
        }

        private String stripLegacyMetaSuffix(String texName) {
            return texName != null && texName.matches(".*_\\d{2}$")
                    ? texName.substring(0, texName.length() - 3)
                    : texName;
        }

        private List<String> legacyTextureCandidates(String contextName) {
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            String name = cleanTextureName(contextName);
            candidates.add(name);
            String stripped = stripGeneratedModelSuffixes(name);
            candidates.add(stripped);
            if (stripped.startsWith("mi_rot_")) {
                candidates.add(stripped.substring("mi_rot_".length()) + "_rot");
                candidates.add(stripped.substring("mi_rot_".length()));
            }
            for (String prefix : List.of("mi_rot_", "mi_", "h10_rot_", "h10_")) {
                if (stripped.startsWith(prefix)) {
                    candidates.add(stripped.substring(prefix.length()));
                }
            }
            List<String> expanded = new ArrayList<>(candidates);
            for (String candidate : expanded) {
                candidates.add(candidate.replace("thinroofplate", "tinroofplate"));
                candidates.add(candidate.replace("rot_thinroofplate", "tinroofplate"));
                candidates.add(stripLegacyMetaSuffix(candidate));
            }
            return new ArrayList<>(candidates);
        }

        private String stripGeneratedModelSuffixes(String name) {
            String result = name;
            for (String suffix : List.of(
                    "_wall_fence", "_half_vertical", "_half_double", "_double_slab",
                    "_vertical_slab", "_trapdoor", "_stairs", "_stair", "_slab",
                    "_fence", "_wall", "_half", "_vertical", "_double")) {
                if (result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    break;
                }
            }
            return result;
        }

        private String mapLegacyVanillaBlockTexture(String texName) {
            if (texName == null || texName.isBlank() || "missing".equals(texName)) return null;
            String tex = cleanTextureName(texName);
            if (tex != null && tex.contains(":")) tex = tex.split(":", 2)[1];
            String color;
            if (tex.startsWith("wool_colored_")) {
                color = mapLegacyDyeColor(tex.substring("wool_colored_".length()));
                return color != null ? color + "_wool" : null;
            }
            if (tex.startsWith("hardened_clay_stained_")) {
                color = mapLegacyDyeColor(tex.substring("hardened_clay_stained_".length()));
                return color != null ? color + "_terracotta" : null;
            }
            if (tex.startsWith("glass_")) {
                color = mapLegacyDyeColor(tex.substring("glass_".length()));
                return color != null ? color + "_stained_glass" : null;
            }
            if (tex.startsWith("planks_")) {
                String wood = mapLegacyWood(tex.substring("planks_".length()));
                return wood != null ? wood + "_planks" : null;
            }
            if (tex.startsWith("log_")) {
                String suffix = tex.endsWith("_top") ? "_top" : "";
                String wood = tex.substring("log_".length(), tex.length() - suffix.length());
                wood = mapLegacyWood(wood);
                return wood != null ? wood + "_log" + suffix : null;
            }
            if (tex.matches("(wheat|carrots|potatoes)_stage_\\d+")) {
                return tex.replace("_stage_", "_stage");
            }
            return switch (tex) {
                case "stone", "cobblestone", "sand", "gravel", "clay", "glass", "snow", "iron_block" -> tex;
                case "stonebrick" -> "stone_bricks";
                case "stonebrick_cracked" -> "cracked_stone_bricks";
                case "stonebrick_mossy" -> "mossy_stone_bricks";
                case "stonebrick_carved" -> "chiseled_stone_bricks";
                case "cobblestone_mossy" -> "mossy_cobblestone";
                case "hardened_clay" -> "terracotta";
                case "quartz_block_side" -> "quartz_block_side";
                case "stone_slab_side" -> "smooth_stone_slab_side";
                case "stone_slab_top" -> "smooth_stone";
                case "trapdoor" -> "oak_trapdoor";
                case "deadbush" -> "dead_bush";
                case "tallgrass" -> "short_grass";
                case "fern" -> "fern";
                case "reeds" -> "sugar_cane";
                case "flower_dandelion" -> "dandelion";
                case "flower_rose" -> "poppy";
                case "flower_allium" -> "allium";
                case "flower_blue_orchid" -> "blue_orchid";
                case "flower_houstonia" -> "azure_bluet";
                case "flower_oxeye_daisy" -> "oxeye_daisy";
                case "flower_tulip_red" -> "red_tulip";
                case "flower_tulip_orange" -> "orange_tulip";
                case "flower_tulip_white" -> "white_tulip";
                case "flower_tulip_pink" -> "pink_tulip";
                case "flower_paeonia" -> "peony_top";
                case "mushroom_brown" -> "brown_mushroom";
                case "mushroom_red" -> "red_mushroom";
                case "farmland_dry" -> "farmland";
                case "farmland_wet" -> "farmland_moist";
                case "sapling_oak" -> "oak_sapling";
                case "sapling_spruce" -> "spruce_sapling";
                case "sapling_birch" -> "birch_sapling";
                case "sapling_jungle" -> "jungle_sapling";
                case "sapling_acacia" -> "acacia_sapling";
                case "sapling_roofed_oak" -> "dark_oak_sapling";
                case "double_plant_sunflower_top" -> "sunflower_top";
                case "double_plant_sunflower_bottom" -> "sunflower_bottom";
                case "double_plant_sunflower_front" -> "sunflower_front";
                case "double_plant_sunflower_back" -> "sunflower_back";
                case "double_plant_syringa_top" -> "lilac_top";
                case "double_plant_syringa_bottom" -> "lilac_bottom";
                case "double_plant_grass_top" -> "tall_grass_top";
                case "double_plant_grass_bottom" -> "tall_grass_bottom";
                case "double_plant_fern_top" -> "large_fern_top";
                case "double_plant_fern_bottom" -> "large_fern_bottom";
                case "double_plant_rose_top" -> "rose_bush_top";
                case "double_plant_rose_bottom" -> "rose_bush_bottom";
                case "double_plant_paeonia_top" -> "peony_top";
                case "double_plant_paeonia_bottom" -> "peony_bottom";
                default -> null;
            };
        }

        private String mapLegacyVanillaItemTexture(String texName) {
            if (texName == null || texName.isBlank() || "missing".equals(texName)) return null;
            String tex = cleanTextureName(texName);
            if (tex != null && tex.contains(":")) tex = tex.split(":", 2)[1];
            return switch (tex) {
                case "iron_shovel" -> "iron_shovel";
                case "iron_pickaxe" -> "iron_pickaxe";
                case "iron_axe" -> "iron_axe";
                case "iron_hoe" -> "iron_hoe";
                case "iron_sword" -> "iron_sword";
                default -> null;
            };
        }

        private String mapLegacyDyeColor(String color) {
            return switch (color) {
                case "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                        "gray", "cyan", "purple", "blue", "brown", "green", "red", "black" -> color;
                case "silver" -> "light_gray";
                default -> null;
            };
        }

        private String mapLegacyWood(String wood) {
            return switch (wood) {
                case "oak", "spruce", "birch", "jungle", "acacia" -> wood;
                case "big_oak", "roofed_oak" -> "dark_oak";
                default -> null;
            };
        }

        private String resolveItemIconTexture(Item item, int metadata) {
            if (item == null) return null;
            for (String methodName : List.of("func_77617_a", "getIconFromDamage")) {
                try {
                    Method method = item.getClass().getMethod(methodName, int.class);
                    Object value = method.invoke(item, metadata);
                    if (value instanceof LegacyIcon icon && icon.getIconName() != null && !icon.getIconName().isBlank()) {
                        return cleanTextureName(icon.getIconName());
                    }
                } catch (Throwable ignored) {
                }
            }
            String buildCraftPipeIcon = resolveBuildCraftPipeIconTexture(item);
            if (buildCraftPipeIcon != null) return buildCraftPipeIcon;
            return null;
        }

        private String resolveBuildCraftPipeIconTexture(Item item) {
            try {
                if (!item.getClass().getName().equals("buildcraft.transport.ItemPipe")) return null;
                int index = readIntField(item, "pipeIconIndex", -1);
                if (index < 0) return null;
                Class<?> typeClass = Class.forName("buildcraft.transport.PipeIconProvider$TYPE", false, item.getClass().getClassLoader());
                Object[] values = typeClass.getEnumConstants();
                if (values == null || index >= values.length) return null;
                Object type = values[index];
                String iconTag = readStringField(type, "iconTag");
                if (iconTag == null || iconTag.isBlank()) return null;
                String fullName = iconTag.contains(":") ? "buildcraft" + iconTag : "buildcrafttransport:pipes/" + iconTag;
                return cleanTextureName(fullName);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private int readIntField(Object target, String fieldName, int fallback) {
            try {
                Field field = findField(target.getClass(), fieldName);
                if (field == null) return fallback;
                field.setAccessible(true);
                return ((Number) field.get(target)).intValue();
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private String readStringField(Object target, String fieldName) {
            try {
                Field field = findField(target.getClass(), fieldName);
                if (field == null) return null;
                field.setAccessible(true);
                Object value = field.get(target);
                return value instanceof String s ? s : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Field findField(Class<?> type, String fieldName) {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private String resolveItemTexture(String itemName) {
            if (hasItemTexture(itemName)) return itemName;
            if (hasItemTexture(itemName + "_00")) return itemName + "_00";
            String normalized = normalizeAluminiumSpelling(itemName);
            if (!normalized.equals(itemName)) {
                if (hasItemTexture(normalized)) return normalized;
                if (hasItemTexture(normalized + "_00")) return normalized + "_00";
            }
            String fileName = itemName + ".png";
            for (String path : lowerToRealPath.keySet()) {
                if ((path.startsWith("textures/item/") || path.startsWith("textures/items/"))
                        && path.endsWith("/" + fileName)) {
                    String base = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
                    return base;
                }
            }
            return null;
        }

        private String normalizeAluminiumSpelling(String name) {
            return name.replace("aliminium", "aluminium").replace("aluminum", "aluminium");
        }

        private boolean hasItemTexture(String texName) {
            return lowerToRealPath.containsKey("textures/item/" + texName + ".png")
                    || lowerToRealPath.containsKey("textures/items/" + texName + ".png");
        }

        private boolean hasItemTexture(String namespace, String texName) {
            if (texName == null) return false;
            String localNamespace = namespace;
            String localName = cleanTextureName(texName);
            if (localName != null && localName.contains(":")) {
                String[] split = localName.split(":", 2);
                localNamespace = split[0];
                localName = split[1];
            }
            String itemPath = "textures/item/" + localName + ".png";
            String itemsPath = "textures/items/" + localName + ".png";
            for (String ns : namespaceCandidates(localNamespace)) {
                if (lowerNamespacedToRealPath.containsKey(ns + ":" + itemPath)
                        || lowerNamespacedToRealPath.containsKey(ns + ":" + itemsPath)) return true;
            }
            return lowerToRealPath.containsKey(itemPath)
                    || lowerToRealPath.containsKey(itemsPath);
        }

        private String cleanTextureName(String name) {
            if (name == null) return null;
            name = name.trim();
            if (name.startsWith("/")) name = name.substring(1);
            String namespace = null;
            if (name.contains(":")) {
                String[] split = name.split(":", 2);
                namespace = split[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                name = split.length > 1 ? split[1] : "";
            }
            name = name.replace('\\', '/');
            if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
            name = name.toLowerCase(Locale.ROOT).replace("//", "/");
            // Strip legacy folder prefixes but keep subfolders and the original namespace.
            if (name.startsWith("textures/blocks/")) name = name.substring("textures/blocks/".length());
            else if (name.startsWith("textures/block/")) name = name.substring("textures/block/".length());
            else if (name.startsWith("textures/items/")) name = name.substring("textures/items/".length());
            else if (name.startsWith("textures/item/")) name = name.substring("textures/item/".length());
            else if (name.startsWith("blocks/")) name = name.substring("blocks/".length());
            else if (name.startsWith("block/")) name = name.substring("block/".length());
            else if (name.startsWith("items/")) name = name.substring("items/".length());
            else if (name.startsWith("item/")) name = name.substring("item/".length());
            return namespace != null && !namespace.isBlank() ? namespace + ":" + name : name;
        }

        @Override
        public void listResources(PackType type, String namespace, String path,
                                  ResourceOutput resourceOutput) {
            if (type != PackType.CLIENT_RESOURCES) return;
            if (!availableNamespaces.contains(namespace)) return;

            String searchPrefix = path.toLowerCase(Locale.ROOT);
            if (!searchPrefix.endsWith("/")) searchPrefix += "/";
            Set<String> requestedNamespaces = new HashSet<>(namespaceCandidates(namespace));
            Set<ResourceLocation> emittedRealResources = new HashSet<>();

            // 螳滄圀縺ｮ繝ｪ繧ｽ繝ｼ繧ｹ
            for (Map.Entry<String, String> entry : lowerNamespacedToRealPath.entrySet()) {
                String namespacedPath = entry.getKey();
                int separator = namespacedPath.indexOf(':');
                if (separator <= 0) continue;
                String resourceNamespace = namespacedPath.substring(0, separator);
                if (!namespace.equals(resourceNamespace)
                        && !requestedNamespaces.contains(resourceNamespace)
                        && !namespace.equals(ownerModId)
                        && !namespace.equals("legacy_mod")) {
                    continue;
                }
                String virtualPath = namespacedPath.substring(separator + 1);
                if (virtualPath.startsWith(searchPrefix)) {
                    @SuppressWarnings("removal")
                    ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(namespace, virtualPath);
                    if (emittedRealResources.add(loc)) {
                        resourceOutput.accept(loc, resolveResource(entry.getValue()));
                    }

                    // .lang 竊・.json 螟画鋤
                    if (virtualPath.endsWith(".lang")) {
                        String jsonPath = virtualPath.substring(0, virtualPath.length() - 5) + ".json";
                        @SuppressWarnings("removal")
                        ResourceLocation jsonLoc = ResourceLocation.fromNamespaceAndPath(namespace, jsonPath);
                        IoSupplier<InputStream> jsonSupplier = getResource(type, jsonLoc);
                        if (jsonSupplier != null && emittedRealResources.add(jsonLoc)) {
                            resourceOutput.accept(jsonLoc, jsonSupplier);
                        }
                    }
                }
            }

            // 1.7.10 mod が assets/minecraft/ にリソース (スクリプト等) を持つと、scanJarContents で
            // "minecraft" が availableNamespaces に入ってしまう。その名前空間で以下の「生成リソース」
            // (blockstate / block model / item model を BuiltInRegistries から自動生成) を吐くと、
            // 全バニラブロックのモデルを石スタブで上書きしてしまい、バニラブロックが全部石になる。
            // 生成は legacy mod 自身の名前空間だけで行い、minecraft では一切生成しない。
            // (実際にファイルとして同梱されたテクスチャ/スクリプト等は上のループで既に提供済み。)
            if ("minecraft".equals(namespace)) {
                return;
            }

            // 笘・虚逧・函謌舌Μ繧ｽ繝ｼ繧ｹ・亥・繝悶Ο繝・け・・
            Set<ResourceLocation> emittedGeneratedBlocks = new HashSet<>();
            for (Map.Entry<ResourceLocation, String> entry : LegacyBlock.TEXTURE_OVERRIDES.entrySet()) {
                ResourceLocation blockRL = entry.getKey();
                if (!blockRL.getNamespace().equals(namespace)) continue;
                emittedGeneratedBlocks.add(blockRL);

                String blockName = blockRL.getPath();
                Block block = BuiltInRegistries.BLOCK.get(blockRL);

                boolean isStairs = block instanceof StairBlock || block instanceof LegacyBlockStairs;
                boolean isSlab = block instanceof SlabBlock || block instanceof LegacyBlockSlab;

                // BlockState
                emitIfMatches(namespace, "blockstates/" + blockName + ".json",
                        searchPrefix, resourceOutput, type);

                // Block Models
                if (isStairs) {
                    emitIfMatches(namespace, "models/block/" + blockName + ".json",
                            searchPrefix, resourceOutput, type);
                    emitIfMatches(namespace, "models/block/" + blockName + "_inner.json",
                            searchPrefix, resourceOutput, type);
                    emitIfMatches(namespace, "models/block/" + blockName + "_outer.json",
                            searchPrefix, resourceOutput, type);
                } else if (isSlab) {
                    emitIfMatches(namespace, "models/block/" + blockName + ".json",
                            searchPrefix, resourceOutput, type);
                    emitIfMatches(namespace, "models/block/" + blockName + "_top.json",
                            searchPrefix, resourceOutput, type);
                    emitIfMatches(namespace, "models/block/" + blockName + "_double.json",
                            searchPrefix, resourceOutput, type);
                    Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
                    if (hasMetadataState(block) || (metaMap != null && metaMap.size() > 1)) {
                        int max = getMetadataVariantCount(block, metaMap);
                        for (int m = 0; m < max; m++) {
                            String metaName = blockName + "_" + String.format("%02d", m);
                            emitIfMatches(namespace, "models/block/" + metaName + ".json",
                                    searchPrefix, resourceOutput, type);
                            emitIfMatches(namespace, "models/block/" + metaName + "_top.json",
                                    searchPrefix, resourceOutput, type);
                            emitIfMatches(namespace, "models/block/" + metaName + "_double.json",
                                    searchPrefix, resourceOutput, type);
                        }
                    }
                } else {
                    // 騾壼ｸｸ繝悶Ο繝・け縺ｮ繝｢繝・Ν逕滓・
                    emitIfMatches(namespace, "models/block/" + blockName + ".json",
                            searchPrefix, resourceOutput, type);
                    // 繝｡繧ｿ繝・・繧ｿ繝舌Μ繧｢繝ｳ繝医′縺ゅｋ蝣ｴ蜷医・蜷・ヰ繝ｪ繧｢繝ｳ繝医・繝｢繝・Ν繧ら函謌・
                    Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
                    if (hasMetadataState(block) || (metaMap != null && metaMap.size() > 1)) {
                        int max = getMetadataVariantCount(block, metaMap);
                        for (int m = 0; m < max; m++) {
                            emitIfMatches(namespace, "models/block/" + blockName + "_" + String.format("%02d", m) + ".json",
                                    searchPrefix, resourceOutput, type);
                        }
                    }
                }

                // Item Model
                emitIfMatches(namespace, "models/item/" + blockName + ".json",
                        searchPrefix, resourceOutput, type);
            }

            for (ResourceLocation blockRL : BuiltInRegistries.BLOCK.keySet()) {
                if (!blockRL.getNamespace().equals(namespace) || emittedGeneratedBlocks.contains(blockRL)) continue;
                emitGeneratedBlockResources(namespace, blockRL, searchPrefix, resourceOutput, type);
            }

            for (Map.Entry<ResourceLocation, String> entry : LegacyItem.TEXTURE_OVERRIDES.entrySet()) {
                ResourceLocation itemRL = entry.getKey();
                if (!itemRL.getNamespace().equals(namespace)) continue;

                String itemName = itemRL.getPath();
                emitIfMatches(namespace, "models/item/" + itemName + ".json",
                        searchPrefix, resourceOutput, type);

                Map<Integer, String> metaMap = LegacyItem.METADATA_TEXTURES.get(itemRL);
                if (metaMap != null && metaMap.size() > 1) {
                    int max = Math.min(512, metaMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
                    for (int m = 0; m < max; m++) {
                        emitIfMatches(namespace, "models/item/" + itemName + "_" + String.format("%02d", m) + ".json",
                                searchPrefix, resourceOutput, type);
                    }
                }

                Map<Integer, String> blockMetaMap = LegacyBlock.METADATA_TEXTURES.get(itemRL);
                if (blockMetaMap != null && blockMetaMap.size() > 1) {
                    int max = Math.min(16, blockMetaMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
                    for (int m = 0; m < max; m++) {
                        emitIfMatches(namespace, "models/block/" + itemName + "_" + String.format("%02d", m) + ".json",
                                searchPrefix, resourceOutput, type);
                    }
                }
            }
        }

        private void emitGeneratedBlockResources(String namespace, ResourceLocation blockRL, String searchPrefix,
                                                 ResourceOutput resourceOutput, PackType type) {
            String blockName = blockRL.getPath();
            Block block = BuiltInRegistries.BLOCK.get(blockRL);
            boolean isStairs = block instanceof StairBlock || block instanceof LegacyBlockStairs;
            boolean isSlab = block instanceof SlabBlock || block instanceof LegacyBlockSlab;

            emitIfMatches(namespace, "blockstates/" + blockName + ".json", searchPrefix, resourceOutput, type);

            if (isStairs) {
                emitIfMatches(namespace, "models/block/" + blockName + ".json", searchPrefix, resourceOutput, type);
                emitIfMatches(namespace, "models/block/" + blockName + "_inner.json", searchPrefix, resourceOutput, type);
                emitIfMatches(namespace, "models/block/" + blockName + "_outer.json", searchPrefix, resourceOutput, type);
            } else if (isSlab) {
                emitIfMatches(namespace, "models/block/" + blockName + ".json", searchPrefix, resourceOutput, type);
                emitIfMatches(namespace, "models/block/" + blockName + "_top.json", searchPrefix, resourceOutput, type);
                emitIfMatches(namespace, "models/block/" + blockName + "_double.json", searchPrefix, resourceOutput, type);
                Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
                if (hasMetadataState(block) || (metaMap != null && metaMap.size() > 1)) {
                    int max = getMetadataVariantCount(block, metaMap);
                    for (int m = 0; m < max; m++) {
                        String metaName = blockName + "_" + String.format("%02d", m);
                        emitIfMatches(namespace, "models/block/" + metaName + ".json", searchPrefix, resourceOutput, type);
                        emitIfMatches(namespace, "models/block/" + metaName + "_top.json", searchPrefix, resourceOutput, type);
                        emitIfMatches(namespace, "models/block/" + metaName + "_double.json", searchPrefix, resourceOutput, type);
                    }
                }
            } else {
                emitIfMatches(namespace, "models/block/" + blockName + ".json", searchPrefix, resourceOutput, type);
                Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
                if (hasMetadataState(block) || (metaMap != null && metaMap.size() > 1)) {
                    int max = getMetadataVariantCount(block, metaMap);
                    for (int m = 0; m < max; m++) {
                        emitIfMatches(namespace, "models/block/" + blockName + "_" + String.format("%02d", m) + ".json",
                                searchPrefix, resourceOutput, type);
                    }
                }
            }

            emitIfMatches(namespace, "models/item/" + blockName + ".json", searchPrefix, resourceOutput, type);
        }

        private void emitIfMatches(String namespace, String resourcePath, String searchPrefix,
                                   ResourceOutput resourceOutput, PackType type) {
            if (resourcePath.startsWith(searchPrefix) || searchPrefix.equals("") ||
                    resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1).startsWith(searchPrefix) ||
                    searchPrefix.startsWith(resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1))) {
                @SuppressWarnings("removal")
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(namespace, resourcePath);
                IoSupplier<InputStream> supplier = getResource(type, loc);
                if (supplier != null) {
                    resourceOutput.accept(loc, supplier);
                }
            }
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            String namespace = location.getNamespace();
            String path = location.getPath();

            // 繝・け繧ｹ繝√Ε
            if (path.startsWith("textures/") && path.endsWith(".png")) {
                return findTextureResource(namespace, path);
            }

            // BlockState
            if (path.startsWith("blockstates/") && path.endsWith(".json")) {
                IoSupplier<InputStream> real = findRealResource(namespace, path);
                if (real != null) return real;

                String blockName = path.replace("blockstates/", "").replace(".json", "");
                @SuppressWarnings("removal")
                ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(namespace, blockName);
                Block block = BuiltInRegistries.BLOCK.get(blockRL);

                if (block instanceof StairBlock || block instanceof LegacyBlockStairs) {
                    return generateStairsBlockState(namespace, blockName);
                }
                if (block instanceof SlabBlock || block instanceof LegacyBlockSlab) {
                    return generateSlabBlockState(namespace, blockName);
                }
                // 笘・壼ｸｸ繝悶Ο繝・け・医Γ繧ｿ繝・・繧ｿ縺ｮ譛臥┌縺ｫ髢｢繧上ｉ縺壼酔縺假ｼ・
                return generateSimpleBlockState(namespace, blockName);
            }

            // Block Model
            if (path.startsWith("models/block/") && path.endsWith(".json")) {
                IoSupplier<InputStream> real = findRealResource(namespace, path);
                if (real != null) return real;

                String modelName = path.replace("models/block/", "").replace(".json", "");
                return generateBlockModel(namespace, modelName);
            }

            // Item Model
            if (path.startsWith("models/item/") && path.endsWith(".json")) {
                IoSupplier<InputStream> real = findRealResource(namespace, path);
                if (real != null) return real;

                String itemName = path.replace("models/item/", "").replace(".json", "");
                return generateItemModel(namespace, itemName);
            }

            // Lang
            if (path.startsWith("lang/") && path.endsWith(".json")) {
                return generateLangJson(namespace, path);
            }

            return findRealResource(namespace, path);
        }

        // ========================================
        // BlockState逕滓・
        // ========================================

        private IoSupplier<InputStream> generateSimpleBlockState(String ns, String blockName) {
            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);
            Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
            Block block = BuiltInRegistries.BLOCK.get(blockRL);

            if (hasMetadataState(block)) {
                int max = getMetadataVariantCount(block, metaMap);
                StringBuilder jsonBuilder = new StringBuilder("{\"variants\":{");
                for (int m = 0; m < max; m++) {
                    if (m > 0) jsonBuilder.append(",");
                    String modelName = metaMap != null && metaMap.containsKey(m)
                            ? blockName + "_" + String.format("%02d", m)
                            : blockName;
                    jsonBuilder.append("\"metadata=").append(m).append("\":{\"model\":\"")
                            .append(ns).append(":block/").append(modelName).append("\"}");
                }
                jsonBuilder.append("}}");
                String json = jsonBuilder.toString();
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            String model = ns + ":block/" + blockName;
            String json = "{\"variants\":{\"\":{\"model\":\"" + model + "\"}}}";
            return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        private IoSupplier<InputStream> generateStairsBlockState(String ns, String blockName) {
            String modelBase = ns + ":block/" + blockName;

            StringBuilder json = new StringBuilder();
            json.append("{\"variants\":{");

            String[] facings = {"east", "north", "south", "west"};
            String[] halves = {"bottom", "top"};
            String[] shapes = {"inner_left", "inner_right", "outer_left", "outer_right", "straight"};

            boolean first = true;
            for (String facing : facings) {
                for (String half : halves) {
                    for (String shape : shapes) {
                        for (String water : new String[]{"false", "true"}) {
                            if (!first) json.append(",");
                            first = false;

                            String model = modelBase;
                            if (shape.startsWith("inner")) model += "_inner";
                            else if (shape.startsWith("outer")) model += "_outer";

                            int y = switch (facing) {
                                case "east" -> 0;
                                case "south" -> 90;
                                case "west" -> 180;
                                case "north" -> 270;
                                default -> 0;
                            };

                            if (shape.equals("inner_left") || shape.equals("outer_left")) {
                                y = (y + 270) % 360;
                            }

                            int x = half.equals("top") ? 180 : 0;

                            json.append("\"facing=").append(facing)
                                    .append(",half=").append(half)
                                    .append(",shape=").append(shape)
                                    .append(",waterlogged=").append(water)
                                    .append("\":{\"model\":\"").append(model).append("\"");
                            if (x != 0) json.append(",\"x\":").append(x);
                            if (y != 0) json.append(",\"y\":").append(y);
                            json.append(",\"uvlock\":true}");
                        }
                    }
                }
            }

            json.append("}}");
            final String finalJson = json.toString();
            return () -> new ByteArrayInputStream(finalJson.getBytes(StandardCharsets.UTF_8));
        }

        private IoSupplier<InputStream> generateSlabBlockState(String ns, String blockName) {
            String modelBase = ns + ":block/" + blockName;

            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);
            Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(blockRL);
            Block block = BuiltInRegistries.BLOCK.get(blockRL);

            if (hasMetadataState(block)) {
                int max = getMetadataVariantCount(block, metaMap);
                StringBuilder jsonBuilder = new StringBuilder("{\"variants\":{");
                boolean first = true;
                for (int m = 0; m < max; m++) {
                    String metaModelBase = modelBase;
                    if (metaMap != null && metaMap.containsKey(m)) {
                        metaModelBase = ns + ":block/" + blockName + "_" + String.format("%02d", m);
                    }
                    for (String typeValue : new String[]{"bottom", "double", "top"}) {
                        for (String water : new String[]{"false", "true"}) {
                            if (!first) jsonBuilder.append(",");
                            first = false;
                            String model = switch (typeValue) {
                                case "double" -> metaModelBase + "_double";
                                case "top" -> metaModelBase + "_top";
                                default -> metaModelBase;
                            };
                            jsonBuilder.append("\"metadata=").append(m)
                                    .append(",type=").append(typeValue)
                                    .append(",waterlogged=").append(water)
                                    .append("\":{\"model\":\"").append(model).append("\"}");
                        }
                    }
                }
                jsonBuilder.append("}}");
                String json = jsonBuilder.toString();
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            String json = "{\"variants\":{" +
                    "\"type=bottom,waterlogged=false\":{\"model\":\"" + modelBase + "\"}," +
                    "\"type=bottom,waterlogged=true\":{\"model\":\"" + modelBase + "\"}," +
                    "\"type=double,waterlogged=false\":{\"model\":\"" + modelBase + "_double\"}," +
                    "\"type=double,waterlogged=true\":{\"model\":\"" + modelBase + "_double\"}," +
                    "\"type=top,waterlogged=false\":{\"model\":\"" + modelBase + "_top\"}," +
                    "\"type=top,waterlogged=true\":{\"model\":\"" + modelBase + "_top\"}" +
                    "}}";
            return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        private boolean hasMetadataState(Block block) {
            return block != null && block.defaultBlockState().getProperties().stream()
                    .anyMatch(prop -> "metadata".equals(prop.getName()));
        }

        private int getMetadataVariantCount(Block block, Map<Integer, String> metaMap) {
            if (hasMetadataState(block)) {
                return 16;
            }
            if (block instanceof ILegacyBlock legacyBlock) {
                return Math.max(1, Math.min(16, legacyBlock.getMaxMetadata()));
            }
            if (metaMap != null && !metaMap.isEmpty()) {
                return Math.max(1, Math.min(16, metaMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1));
            }
            return 16;
        }

        // ========================================
        // Model逕滓・
        // ========================================

        private IoSupplier<InputStream> generateBlockModel(String ns, String modelName) {
            // getRenderType()==-1 の TESR 専用ブロック (AsphaltMod の OBJ 3D ブロック等) は
            // 標準の立方体モデルを持たせない。cube を生成すると OBJ を箱で覆い隠してしまうため、
            // 要素なしの空モデルにして「TESR が描く OBJ だけ」が見えるようにする。
            {
                String probe = modelName;
                for (String sfx : new String[]{"_inner", "_outer", "_top", "_double"}) {
                    if (probe.endsWith(sfx)) {
                        probe = probe.substring(0, probe.length() - sfx.length());
                        break;
                    }
                }
                if (probe.length() > 3 && probe.charAt(probe.length() - 3) == '_'
                        && Character.isDigit(probe.charAt(probe.length() - 2))
                        && Character.isDigit(probe.charAt(probe.length() - 1))) {
                    probe = probe.substring(0, probe.length() - 3);
                }
                @SuppressWarnings("removal")
                ResourceLocation probeRL = ResourceLocation.fromNamespaceAndPath(ns, probe);
                Block probeBlock = BuiltInRegistries.BLOCK.get(probeRL);
                if (probeBlock != null && probeBlock != net.minecraft.world.level.block.Blocks.AIR
                        && com.myname.legacyloader.bridge.client.registry.LegacyRenderingRegistry
                                .getRenderType(probeBlock) == -1) {
                    String empty = "{\"textures\":{\"particle\":\"" + ns + ":block/" + probe + "\"},\"elements\":[]}";
                    return () -> new ByteArrayInputStream(empty.getBytes(StandardCharsets.UTF_8));
                }
            }
            // 繧ｵ繝輔ぅ繝・け繧ｹ蜃ｦ逅・
            String baseName = modelName;
            String suffix = "";

            if (modelName.endsWith("_inner")) {
                baseName = modelName.substring(0, modelName.length() - 6);
                suffix = "_inner";
            } else if (modelName.endsWith("_outer")) {
                baseName = modelName.substring(0, modelName.length() - 6);
                suffix = "_outer";
            } else if (modelName.endsWith("_top")) {
                baseName = modelName.substring(0, modelName.length() - 4);
                suffix = "_top";
            } else if (modelName.endsWith("_double")) {
                baseName = modelName.substring(0, modelName.length() - 7);
                suffix = "_double";
            }

            // _NN 繧ｵ繝輔ぅ繝・け繧ｹ・医Γ繧ｿ繝・・繧ｿ繝舌Μ繧｢繝ｳ繝茨ｼ峨ｒ讀懷・縺励※蟇ｾ蠢懊☆繧九ユ繧ｯ繧ｹ繝√Ε繧定ｧ｣豎ｺ
            // 萓・ mortar_colored_00 竊・隕ｪ繝悶Ο繝・け mortar_colored 縺ｮ METADATA_TEXTURES[0]
            String tex = null;
            String registryBlockName = baseName;
            int modelMeta = -1;
            @SuppressWarnings("removal")
            ResourceLocation exactBlockRL = ResourceLocation.fromNamespaceAndPath(ns, baseName);
            boolean exactBlockExists = BuiltInRegistries.BLOCK.containsKey(exactBlockRL);
            if (!exactBlockExists && baseName.matches(".*_\\d{2}$")) {
                int meta = Integer.parseInt(baseName.substring(baseName.length() - 2));
                modelMeta = meta;
                String parentName = baseName.substring(0, baseName.length() - 3);
                registryBlockName = parentName;
                @SuppressWarnings("removal")
                ResourceLocation parentRL = ResourceLocation.fromNamespaceAndPath(ns, parentName);
                Map<Integer, String> metaMap = LegacyBlock.METADATA_TEXTURES.get(parentRL);
                if (metaMap != null && metaMap.containsKey(meta)) {
                    tex = metaMap.get(meta);
                }
            }
            if (tex == null) {
                tex = resolveTexture(ns, baseName);
            }
            String texturePath = textureReference(ns, tex, false, baseName);

            // 繝悶Ο繝・け繧ｿ繧､繝怜愛螳・
            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, registryBlockName);
            Block block = BuiltInRegistries.BLOCK.get(blockRL);
            String renderHandlerName = LegacyRenderingRegistry.getRenderHandlerClassName(block);

            boolean isStairs = block instanceof StairBlock || block instanceof LegacyBlockStairs;
            boolean isSlab = block instanceof SlabBlock || block instanceof LegacyBlockSlab;
            String[] faceTextures = resolveFaceTexturePaths(ns, registryBlockName, tex, baseName, modelMeta);
            List<LegacyRenderBlocks.CapturedElement> capturedElements = getCapturedElementsForModel(blockRL, modelMeta);
            if (!capturedElements.isEmpty()) {
                String json = capturedElementModelJson(ns, baseName, faceTextures, capturedElements);
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            if (isCrossModel(block, registryBlockName, renderHandlerName)) {
                String json = "{\"parent\":\"block/cross\",\"textures\":{\"cross\":\"" + faceTextures[2] +
                        "\"},\"render_type\":\"cutout\"}";
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            if (isVerticalSlabModel(block, registryBlockName, renderHandlerName)) {
                String json = elementModelJson(faceTextures, verticalSlabBox(modelMeta));
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            if (isThinPanelModel(block, registryBlockName, renderHandlerName)) {
                String json = elementModelJson(faceTextures, thinPanelBox(modelMeta, registryBlockName));
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            String parent;
            String textures;

            if (isStairs) {
                parent = switch (suffix) {
                    case "_inner" -> "block/inner_stairs";
                    case "_outer" -> "block/outer_stairs";
                    default -> "block/stairs";
                };
                textures = "\"bottom\":\"" + faceTextures[0] + "\",\"top\":\"" + faceTextures[1] +
                        "\",\"side\":\"" + faceTextures[2] + "\"";
            } else if (isSlab) {
                if (suffix.equals("_double")) {
                    parent = "block/cube";
                    textures = faceTextureJson(faceTextures);
                } else {
                    parent = suffix.equals("_top") ? "block/slab_top" : "block/slab";
                    textures = "\"bottom\":\"" + faceTextures[0] + "\",\"top\":\"" + faceTextures[1] +
                            "\",\"side\":\"" + faceTextures[2] + "\"";
                }
            } else {
                parent = "block/cube";
                textures = faceTextureJson(faceTextures);
            }

            String json = "{\"parent\":\"" + parent + "\",\"textures\":{" + textures + "}}";
            return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        private String[] resolveFaceTexturePaths(String ns, String blockName, String defaultTex, String contextName, int meta) {
            String fallback = textureReference(ns, defaultTex, false, contextName);
            String[] result = new String[]{fallback, fallback, fallback, fallback, fallback, fallback};
            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);
            Map<Integer, String[]> byMeta = BLOCK_FACE_TEXTURES.get(blockRL);
            if (byMeta == null || byMeta.isEmpty()) return result;
            String[] faces = byMeta.getOrDefault(meta >= 0 ? meta : 0, byMeta.get(0));
            if (faces == null) return result;
            for (int side = 0; side < Math.min(6, faces.length); side++) {
                if (faces[side] != null) {
                    result[side] = textureReference(ns, faces[side], false, contextName);
                }
            }
            return result;
        }

        private String faceTextureJson(String[] faces) {
            return "\"particle\":\"" + faces[2] +
                    "\",\"down\":\"" + faces[0] +
                    "\",\"up\":\"" + faces[1] +
                    "\",\"north\":\"" + faces[2] +
                    "\",\"south\":\"" + faces[3] +
                    "\",\"west\":\"" + faces[4] +
                    "\",\"east\":\"" + faces[5] + "\"";
        }

        private List<LegacyRenderBlocks.CapturedElement> getCapturedElementsForModel(ResourceLocation blockRL, int meta) {
            Map<Integer, List<LegacyRenderBlocks.CapturedElement>> byMeta = BLOCK_RENDER_ELEMENTS.get(blockRL);
            int resolvedMeta = meta >= 0 ? meta : 0;
            if (byMeta != null && !byMeta.isEmpty()) {
                List<LegacyRenderBlocks.CapturedElement> exact = byMeta.get(resolvedMeta);
                if (exact != null && !exact.isEmpty()) return exact;
                List<LegacyRenderBlocks.CapturedElement> fallback = byMeta.get(0);
                if (fallback != null && !fallback.isEmpty()) return fallback;
            }

            // Render handlers are often registered during client init, after block registration.
            // Capture lazily during model generation so 1.7.10 ISBRH blocks (Ha10BM lines,
            // rails/panels, BuildCraft-style pipes) do not fall back to a full cube.
            try {
                Block block = BuiltInRegistries.BLOCK.get(blockRL);
                int renderId = LegacyRenderingRegistry.getRenderType(block);
                LegacySimpleBlockRenderingHandler handler = LegacyRenderingRegistry.getBlockHandler(renderId);
                if (handler == null || renderId == 0) return List.of();
                LegacyTessellator.clearCapturedElements();
                LegacyRenderBlocks worldRenderer = new LegacyRenderBlocks();
                worldRenderer.setCaptureMetadata(resolvedMeta);
                try {
                    com.myname.legacyloader.bridge.world.LegacySingleBlockAccess access = createCaptureAccess(block, resolvedMeta);
                    updateLegacyBoundsForCapture(block, access, 0, 0, 0);
                    LegacyTessellator.beginCaptureAt(0, 0, 0);
                    worldRenderer.setBlockAccess(access, 0, 0, 0);
                    handler.renderWorldBlock(access, 0, 0, 0, block, renderId, worldRenderer);
                } catch (Throwable ignored) {}
                List<LegacyRenderBlocks.CapturedElement> elements = new ArrayList<>(worldRenderer.getCapturedElements());
                elements.addAll(LegacyTessellator.consumeCapturedElements());
                List<LegacyRenderBlocks.CapturedElement> normalized = normalizeCapturedElementsStatic(elements);
                if (normalized.isEmpty()) {
                    LegacyTessellator.clearCapturedElements();
                    LegacyRenderBlocks inventoryRenderer = new LegacyRenderBlocks();
                    inventoryRenderer.setCaptureMetadata(resolvedMeta);
                    try { handler.renderInventoryBlock(block, resolvedMeta, renderId, inventoryRenderer); } catch (Throwable ignored) {}
                    elements = new ArrayList<>(inventoryRenderer.getCapturedElements());
                    elements.addAll(LegacyTessellator.consumeCapturedElements());
                    normalized = normalizeCapturedElementsStatic(elements);
                }
                if (!normalized.isEmpty()) {
                    BLOCK_RENDER_ELEMENTS.computeIfAbsent(blockRL, k -> new HashMap<>()).put(resolvedMeta, normalized);
                    return normalized;
                }
            } catch (Throwable ignored) {
            } finally {
                LegacyTessellator.clearCapturedElements();
            }
            return List.of();
        }

        private static List<LegacyRenderBlocks.CapturedElement> normalizeCapturedElementsStatic(List<LegacyRenderBlocks.CapturedElement> elements) {
            Map<String, LegacyRenderBlocks.CapturedElement> dedup = new LinkedHashMap<>();
            for (LegacyRenderBlocks.CapturedElement element : elements) {
                if (element == null) continue;
                if (element.maxX <= element.minX || element.maxY <= element.minY || element.maxZ <= element.minZ) continue;
                String key = String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f",
                        element.minX, element.minY, element.minZ, element.maxX, element.maxY, element.maxZ);
                dedup.putIfAbsent(key, element);
                if (dedup.size() >= 96) break;
            }
            return new ArrayList<>(dedup.values());
        }

        private String capturedElementModelJson(String ns, String contextName, String[] fallbackFaces,
                                                List<LegacyRenderBlocks.CapturedElement> elements) {
            String[] sideNames = new String[]{"down", "up", "north", "south", "west", "east"};
            StringBuilder textures = new StringBuilder(faceTextureJson(fallbackFaces));
            String[][] faceKeys = new String[elements.size()][6];
            for (int i = 0; i < elements.size(); i++) {
                LegacyRenderBlocks.CapturedElement element = elements.get(i);
                for (int side = 0; side < 6; side++) {
                    String face = element.faces[side];
                    if (face != null && !face.isBlank()) {
                        String key = "e" + i + "_" + sideNames[side];
                        textures.append(",\"").append(key).append("\":\"")
                                .append(textureReference(ns, cleanTextureName(face), false, contextName))
                                .append("\"");
                        faceKeys[i][side] = "#" + key;
                    } else {
                        faceKeys[i][side] = "#" + sideNames[side];
                    }
                }
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"render_type\":\"cutout\",\"textures\":{").append(textures).append("},\"elements\":[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) json.append(',');
                LegacyRenderBlocks.CapturedElement element = elements.get(i);
                json.append("{\"from\":[")
                        .append(modelCoord(element.minX)).append(',')
                        .append(modelCoord(element.minY)).append(',')
                        .append(modelCoord(element.minZ)).append("],\"to\":[")
                        .append(modelCoord(element.maxX)).append(',')
                        .append(modelCoord(element.maxY)).append(',')
                        .append(modelCoord(element.maxZ)).append("],\"faces\":{")
                        .append("\"down\":{\"texture\":\"").append(faceKeys[i][0]).append("\"},")
                        .append("\"up\":{\"texture\":\"").append(faceKeys[i][1]).append("\"},")
                        .append("\"north\":{\"texture\":\"").append(faceKeys[i][2]).append("\"},")
                        .append("\"south\":{\"texture\":\"").append(faceKeys[i][3]).append("\"},")
                        .append("\"west\":{\"texture\":\"").append(faceKeys[i][4]).append("\"},")
                        .append("\"east\":{\"texture\":\"").append(faceKeys[i][5]).append("\"}")
                        .append("}}");
            }
            json.append("]}");
            return json.toString();
        }

        private String modelCoord(double value) {
            double scaled = Math.max(-16.0D, Math.min(32.0D, value * 16.0D));
            double rounded = Math.rint(scaled * 1000.0D) / 1000.0D;
            if (Math.abs(rounded - Math.rint(rounded)) < 0.0001D) {
                return Integer.toString((int) Math.rint(rounded));
            }
            return String.format(Locale.ROOT, "%.3f", rounded).replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        private boolean isCrossModel(Block block, String blockName, String renderHandlerName) {
            String name = blockName.toLowerCase(Locale.ROOT);
            String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
            String handler = renderHandlerName != null ? renderHandlerName.toLowerCase(Locale.ROOT) : "";
            return className.contains("crosssquare")
                    || handler.contains("crosssquare")
                    || handler.contains("bush")
                    || handler.contains("lotus")
                    || handler.contains("riceplant")
                    || handler.contains("sunflower")
                    || className.contains("plant")
                    || className.contains("bamboo")
                    || className.contains("lotus")
                    || className.contains("crop")
                    || name.contains("plant")
                    || name.contains("bush")
                    || name.contains("flower")
                    || name.contains("sapling")
                    || name.contains("mushroom")
                    || name.contains("tallgrass")
                    || name.contains("deadbush")
                    || name.contains("cabbage")
                    || name.contains("rice")
                    || name.contains("bamboo")
                    || name.contains("lotus");
        }

        private boolean isVerticalSlabModel(Block block, String blockName, String renderHandlerName) {
            String name = blockName.toLowerCase(Locale.ROOT);
            String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
            String handler = renderHandlerName != null ? renderHandlerName.toLowerCase(Locale.ROOT) : "";
            return className.contains("verticalslab")
                    || handler.contains("verticalslab")
                    || name.contains("vertical_slab")
                    || name.contains("_half_vertical");
        }

        private boolean isThinPanelModel(Block block, String blockName, String renderHandlerName) {
            String name = blockName.toLowerCase(Locale.ROOT);
            String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
            String handler = renderHandlerName != null ? renderHandlerName.toLowerCase(Locale.ROOT) : "";
            return className.contains("trapdoor")
                    || className.contains("panel")
                    || className.contains("halftimber")
                    || className.contains("wire")
                    || className.contains("sign")
                    || className.contains("frame")
                    || handler.contains("trapdoor")
                    || handler.contains("panel")
                    || handler.contains("halftimber")
                    || handler.contains("wire")
                    || handler.contains("sign")
                    || handler.contains("ranma")
                    || handler.contains("lamination")
                    || name.contains("trapdoor")
                    || name.contains("panel")
                    || name.contains("half_timber")
                    || name.contains("wire")
                    || name.contains("exit_sign")
                    || name.contains("frame");
        }

        private int[] verticalSlabBox(int meta) {
            int rot = meta >= 0 ? meta & 3 : 0;
            return switch (rot) {
                case 0 -> new int[]{8, 0, 0, 16, 16, 16};
                case 1 -> new int[]{0, 0, 0, 8, 16, 16};
                case 2 -> new int[]{0, 0, 8, 16, 16, 16};
                default -> new int[]{0, 0, 0, 16, 16, 8};
            };
        }

        private int[] thinPanelBox(int meta, String blockName) {
            int thickness = blockName.toLowerCase(Locale.ROOT).contains("wire") ? 1 : 2;
            int rot = meta >= 0 ? meta & 3 : 0;
            return switch (rot) {
                case 0 -> new int[]{0, 0, 16 - thickness, 16, 16, 16};
                case 1 -> new int[]{0, 0, 0, 16, 16, thickness};
                case 2 -> new int[]{16 - thickness, 0, 0, 16, 16, 16};
                default -> new int[]{0, 0, 0, thickness, 16, 16};
            };
        }

        private String elementModelJson(String[] faces, int[] box) {
            String textures = faceTextureJson(faces);
            return "{\"render_type\":\"cutout\",\"textures\":{" + textures + "},\"elements\":[{\"from\":[" +
                    box[0] + "," + box[1] + "," + box[2] + "],\"to\":[" +
                    box[3] + "," + box[4] + "," + box[5] + "],\"faces\":{" +
                    "\"down\":{\"texture\":\"#down\",\"cullface\":\"down\"}," +
                    "\"up\":{\"texture\":\"#up\",\"cullface\":\"up\"}," +
                    "\"north\":{\"texture\":\"#north\"}," +
                    "\"south\":{\"texture\":\"#south\"}," +
                    "\"west\":{\"texture\":\"#west\"}," +
                    "\"east\":{\"texture\":\"#east\"}" +
                    "}}]}";
        }

        private IoSupplier<InputStream> generateItemModel(String ns, String blockName) {
            // 隕ｪ繝悶Ο繝・け繝｢繝・Ν繧貞盾辣ｧ
            return generateLegacyItemModel(ns, blockName);
        }

        private IoSupplier<InputStream> generateLegacyItemModel(String ns, String blockName) {
            @SuppressWarnings("removal")
            ResourceLocation itemRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);
            @SuppressWarnings("removal")
            ResourceLocation blockRL = ResourceLocation.fromNamespaceAndPath(ns, blockName);
            Item actualItem = BuiltInRegistries.ITEM.get(itemRL);
            Block block = BuiltInRegistries.BLOCK.get(blockRL);
            boolean hasLegacyBlockModel = LegacyBlock.TEXTURE_OVERRIDES.containsKey(blockRL)
                    || block instanceof StairBlock || block instanceof SlabBlock
                    || block instanceof LegacyBlock || block instanceof LegacyBlockStairs || block instanceof LegacyBlockSlab;

            Map<Integer, String> blockMeta = LegacyBlock.METADATA_TEXTURES.get(blockRL);
            if (hasLegacyBlockModel || blockMeta != null) {
                String baseModel = blockMeta != null && blockMeta.containsKey(0)
                        ? ns + ":block/" + blockName + "_00"
                        : ns + ":block/" + blockName;
                StringBuilder json = new StringBuilder("{\"parent\":\"").append(baseModel).append("\"");
                if (blockMeta != null && blockMeta.size() > 1) {
                    json.append(",\"overrides\":[");
                    int max = Math.min(16, blockMeta.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
                    for (int m = 1; m < max; m++) {
                        if (m > 1) json.append(",");
                        String model = blockMeta.containsKey(m)
                                ? ns + ":block/" + blockName + "_" + String.format("%02d", m)
                                : ns + ":block/" + blockName;
                        json.append("{\"predicate\":{\"custom_model_data\":").append(m)
                                .append("},\"model\":\"").append(model).append("\"}");
                    }
                    json.append("]");
                }
                json.append("}");
                String finalJson = json.toString();
                return () -> new ByteArrayInputStream(finalJson.getBytes(StandardCharsets.UTF_8));
            }

            if (blockName.matches(".*_\\d{2}$")) {
                int meta = Integer.parseInt(blockName.substring(blockName.length() - 2));
                String parentName = blockName.substring(0, blockName.length() - 3);
                @SuppressWarnings("removal")
                ResourceLocation parentRL = ResourceLocation.fromNamespaceAndPath(ns, parentName);
                Map<Integer, String> metaMap = LegacyItem.METADATA_TEXTURES.get(parentRL);
                String tex = metaMap != null ? metaMap.get(meta) : null;
                if (tex == null) tex = parentName + "_" + String.format("%02d", meta);
                String json = "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\""
                        + textureReference(ns, tex, true, parentName) + "\"}}";
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            Map<Integer, String> itemMeta = LegacyItem.METADATA_TEXTURES.get(itemRL);
            String itemTex = LegacyItem.TEXTURE_OVERRIDES.get(itemRL);
            if (itemTex != null || itemMeta != null) {
                String baseTex = itemMeta != null ? itemMeta.getOrDefault(0, itemTex) : itemTex;
                if (baseTex == null) baseTex = blockName;
                if (isBuildCraftPipeItem(actualItem)) {
                    String json = buildCraftPipeItemModelJson(ns, blockName, baseTex);
                    return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                }
                StringBuilder json = new StringBuilder("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"")
                        .append(textureReference(ns, baseTex, true, blockName)).append("\"}");
                if (itemMeta != null && itemMeta.size() > 1) {
                    json.append(",\"overrides\":[");
                    boolean first = true;
                    int max = Math.min(512, itemMeta.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
                    for (int m = 1; m < max; m++) {
                        if (!first) json.append(",");
                        first = false;
                        json.append("{\"predicate\":{\"custom_model_data\":").append(m)
                                .append("},\"model\":\"").append(ns).append(":item/")
                                .append(blockName).append("_").append(String.format("%02d", m)).append("\"}");
                    }
                    json.append("]");
                }
                json.append("}");
                String finalJson = json.toString();
                return () -> new ByteArrayInputStream(finalJson.getBytes(StandardCharsets.UTF_8));
            }

            String iconTex = resolveItemIconTexture(actualItem, 0);
            if (iconTex != null) {
                if (isBuildCraftPipeItem(actualItem)) {
                    String json = buildCraftPipeItemModelJson(ns, blockName, iconTex);
                    return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
                }
                String json = "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\""
                        + textureReference(ns, iconTex, true, blockName) + "\"}}";
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            String fallbackItemTex = resolveItemTexture(blockName);
            if (fallbackItemTex != null) {
                String json = "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\""
                        + textureReference(ns, fallbackItemTex, true, blockName) + "\"}}";
                return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }

            String json = "{\"parent\":\"" + ns + ":block/" + blockName + "\"}";
            return () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        private boolean isBuildCraftPipeItem(Item item) {
            return item != null && item.getClass().getName().equals("buildcraft.transport.ItemPipe");
        }

        private String buildCraftPipeItemModelJson(String ns, String itemName, String texture) {
            String tex = textureReference(ns, texture, true, itemName);
            return "{\"parent\":\"block/block\",\"textures\":{\"pipe\":\"" + tex + "\",\"particle\":\"" + tex + "\"},"
                    + "\"display\":{\"gui\":{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.82,0.82,0.82]},"
                    + "\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,3,0],\"scale\":[0.35,0.35,0.35]},"
                    + "\"fixed\":{\"rotation\":[0,180,0],\"translation\":[0,0,0],\"scale\":[0.5,0.5,0.5]},"
                    + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
                    + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"translation\":[0,0,0],\"scale\":[0.4,0.4,0.4]}},"
                    + "\"elements\":["
                    + "{\"from\":[4,0,4],\"to\":[12,16,12],\"faces\":{"
                    + "\"down\":{\"texture\":\"#pipe\"},\"up\":{\"texture\":\"#pipe\"},\"north\":{\"texture\":\"#pipe\"},"
                    + "\"south\":{\"texture\":\"#pipe\"},\"west\":{\"texture\":\"#pipe\"},\"east\":{\"texture\":\"#pipe\"}}}"
                    + "]}";
        }

        // ========================================
        // 繝・け繧ｹ繝√Ε隗｣豎ｺ
        // ========================================

        private IoSupplier<InputStream> findTextureResource(String namespace, String path) {
            // 逶ｴ謗･繝代せ縺ｧ讀懃ｴ｢
            IoSupplier<InputStream> direct = findRealResource(namespace, path);
            if (direct != null) return direct;
            IoSupplier<InputStream> spellingAlias = findSpellingAliasResource(namespace, path);
            if (spellingAlias != null) return spellingAlias;

            // _flip 繝・け繧ｹ繝√Ε縺ｪ縺ｩ縺ｮ繝輔か繝ｼ繝ｫ繝舌ャ繧ｯ
            if (path.endsWith(".png")) {
                String noExt = path.substring(0, path.length() - 4);

                // _00 繧ｵ繝輔ぅ繝・け繧ｹ繧定ｿｽ蜉
                IoSupplier<InputStream> withMeta0 = findRealResource(namespace, noExt + "_00.png");
                if (withMeta0 != null) return withMeta0;

                // 繝輔ぃ繧､繝ｫ蜷阪〒驛ｨ蛻・ｸ閾ｴ讀懃ｴ｢
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                Set<String> requestedNamespaces = new HashSet<>(namespaceCandidates(namespace));
                for (Map.Entry<String, String> entry : lowerNamespacedToRealPath.entrySet()) {
                    String key = entry.getKey();
                    int separator = key.indexOf(':');
                    String resourceNamespace = separator > 0 ? key.substring(0, separator) : "";
                    if ((requestedNamespaces.contains(resourceNamespace)
                            || namespace.equals("legacy_mod"))
                            && key.endsWith("/" + fileName)) {
                        return resolveResource(entry.getValue());
                    }
                }
            }

            return null;
        }

        private IoSupplier<InputStream> findSpellingAliasResource(String namespace, String path) {
            String alias = null;
            if (path.contains("aliminium")) {
                alias = path.replace("aliminium", "aluminium");
            } else if (path.contains("aluminum")) {
                alias = path.replace("aluminum", "aluminium");
            }
            return alias != null ? findRealResource(namespace, alias) : null;
        }

        private IoSupplier<InputStream> findRealResource(String path) {
            return findRealResource(ownerModId, path);
        }

        private IoSupplier<InputStream> findRealResource(String namespace, String path) {
            String lowerPath = path.toLowerCase(Locale.ROOT);
            String realPath = null;
            for (String ns : namespaceCandidates(namespace)) {
                realPath = lowerNamespacedToRealPath.get(ns + ":" + lowerPath);
                if (realPath != null) break;
            }
            // Last resort: 1.7.10 mods often reference textures by path only or with a domain
            // that differs from the synthetic 1.20 registry namespace.
            if (realPath == null) {
                realPath = lowerToRealPath.get(lowerPath);
            }
            if (realPath != null) return resolveResource(realPath);
            return null;
        }

        private IoSupplier<InputStream> resolveResource(String realPath) {
            return () -> {
                try (JarFile jar = new JarFile(jarFile)) {
                    JarEntry entry = jar.getJarEntry(realPath);
                    if (entry == null) throw new FileNotFoundException(realPath);
                    try (InputStream is = jar.getInputStream(entry)) {
                        return new ByteArrayInputStream(is.readAllBytes());
                    }
                }
            };
        }

        // ========================================
        // Lang螟画鋤
        // ========================================

        private IoSupplier<InputStream> generateLangJson(String namespace, String jsonPath) {
            String langName = jsonPath.replace("lang/", "").replace(".json", "");
            IoSupplier<InputStream> stream = findRealResource(namespace, "lang/" + langName + ".lang");
            if (stream == null) stream = findRealResource(namespace, "lang/en_US.lang");
            if (stream == null) stream = findRealResource(namespace, "lang/en_us.lang");
            if (stream == null) stream = findSharedLangResource(langName);
            if (stream == null) stream = findSharedLangResource("en_us");
            if (stream == null) stream = findSharedLangResource("en_US");

            if (stream != null) {
                final IoSupplier<InputStream> fs = stream;
                return () -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(fs.get(), StandardCharsets.UTF_8))) {
                        String l;
                        while ((l = r.readLine()) != null) {
                            l = l.trim();
                            if (l.isEmpty() || l.startsWith("#")) continue;
                            String[] p = l.split("=", 2);
                            if (p.length == 2) {
                                String key = p[0].trim();
                                String val = p[1].trim();
                                map.put(key, val);
                                if (key.endsWith(".name")) {
                                    map.put(key.substring(0, key.length() - 5), val);
                                }
                            }
                        }
                    }

                    StringBuilder sb = new StringBuilder("{\n");
                    int i = 0;
                    for (Map.Entry<String, String> e : map.entrySet()) {
                        sb.append("  \"").append(escape(e.getKey())).append("\": \"")
                                .append(escape(e.getValue())).append("\"");
                        if (i++ < map.size() - 1) sb.append(",");
                        sb.append("\n");
                    }
                    sb.append("}");
                    return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
                };
            }
            return null;
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        // ========================================
        // PackResources蠢・医Γ繧ｽ繝・ラ
        // ========================================

        @Override
        public Set<String> getNamespaces(PackType type) { return availableNamespaces; }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) { return null; }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
            if (deserializer == PackMetadataSection.TYPE) {
                return (T) new PackMetadataSection(Component.literal("Legacy Resources"), 34);
            }
            return null;
        }

        private IoSupplier<InputStream> findSharedLangResource(String langName) {
            String target = (":lang/" + langName + ".lang").toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : lowerNamespacedToRealPath.entrySet()) {
                if (entry.getKey().endsWith(target)) {
                    return resolveResource(entry.getValue());
                }
            }
            return null;
        }

        @Override public PackLocationInfo location() { return locationInfo; }
        @Override public String packId() { return packId; }
        @Override public void close() {}
    }
}
