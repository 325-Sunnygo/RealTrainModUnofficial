package com.myname.legacyloader.bridge;

import java.util.HashMap;
import java.util.Map;

public class Mappings {
    private static final Map<String, String> CLASS_MAP = new HashMap<>();

    static {

        // ==========================================
        //           Client / Rendering / Sound
        // ==========================================
        add("net/minecraft/block/Block$SoundType", "com/myname/legacyloader/bridge/block/LegacySoundType");
        add("net/minecraft/world/level/block/SoundType", "com/myname/legacyloader/bridge/block/LegacySoundType");
        // ==========================================
        //           Core / FML
        // ==========================================
        add("cpw/mods/fml/common/FMLCommonHandler", "com/myname/legacyloader/bridge/fml/LegacyFMLCommonHandler");
        add("cpw/mods/fml/common/ICrashCallable", "com/myname/legacyloader/bridge/fml/LegacyICrashCallable");
        add("cpw/mods/fml/common/versioning/ArtifactVersion", "com/myname/legacyloader/bridge/fml/versioning/LegacyArtifactVersion");
        add("cpw/mods/fml/common/EnhancedRuntimeException", "com/myname/legacyloader/bridge/fml/LegacyEnhancedRuntimeException");
        add("cpw/mods/fml/common/EnhancedRuntimeException$WrappedPrintStream", "com/myname/legacyloader/bridge/fml/LegacyEnhancedRuntimeException$WrappedPrintStream");
        add("cpw/mods/fml/common/IFMLHandledException", "com/myname/legacyloader/bridge/fml/LegacyIFMLHandledException");
        add("cpw/mods/fml/client/CustomModLoadingErrorDisplayException", "com/myname/legacyloader/bridge/fml/LegacyCustomModLoadingErrorDisplayException");
        add("cpw/mods/fml/common/eventhandler/Event", "com/myname/legacyloader/bridge/fml/LegacyEvent");
        add("cpw/mods/fml/common/eventhandler/Event$Result", "com/myname/legacyloader/bridge/fml/LegacyEvent$Result");
        add("cpw/mods/fml/common/eventhandler/EventBus", "com/myname/legacyloader/bridge/fml/BridgeEventBus");
        add("cpw/mods/fml/common/eventhandler/EventPriority", "net/neoforged/bus/api/EventPriority");
        add("cpw/mods/fml/common/eventhandler/SubscribeEvent", "net/neoforged/bus/api/SubscribeEvent");
        add("cpw/mods/fml/common/eventhandler/Cancelable", "com/myname/legacyloader/bridge/fml/LegacyCancelable");
        add("cpw/mods/fml/common/eventhandler/Event$HasResult", "com/myname/legacyloader/bridge/fml/LegacyHasResult");
        add("cpw/mods/fml/common/registry/GameRegistry", "com/myname/legacyloader/bridge/registry/LegacyGameRegistry");
        add("cpw/mods/fml/common/registry/EntityRegistry", "com/myname/legacyloader/bridge/registry/LegacyEntityRegistry");
        add("cpw/mods/fml/common/registry/IEntityAdditionalSpawnData", "com/myname/legacyloader/bridge/registry/LegacyEntityAdditionalSpawnData");
        add("cpw/mods/fml/common/registry/VillagerRegistry", "com/myname/legacyloader/bridge/registry/LegacyVillagerRegistry");
        add("cpw/mods/fml/common/registry/VillagerRegistry$IVillageTradeHandler", "com/myname/legacyloader/bridge/registry/LegacyVillagerRegistry$IVillageTradeHandler");
        add("cpw/mods/fml/common/Loader", "com/myname/legacyloader/bridge/fml/LegacyLoader");
        add("cpw/mods/fml/common/event/FMLPreInitializationEvent", "com/myname/legacyloader/bridge/event/LegacyPreInitEvent");
        add("cpw/mods/fml/common/event/FMLInitializationEvent", "com/myname/legacyloader/bridge/event/LegacyInitEvent");
        add("cpw/mods/fml/common/event/FMLPostInitializationEvent", "com/myname/legacyloader/bridge/event/LegacyPostInitEvent");
        add("cpw/mods/fml/common/event/FMLServerAboutToStartEvent", "com/myname/legacyloader/bridge/event/LegacyServerAboutToStartEvent");
        add("cpw/mods/fml/common/event/FMLServerStartedEvent", "com/myname/legacyloader/bridge/event/LegacyServerStartedEvent");
        add("cpw/mods/fml/common/event/FMLServerStartingEvent", "com/myname/legacyloader/bridge/event/LegacyServerStartingEvent");
        add("cpw/mods/fml/common/event/FMLServerStoppingEvent", "com/myname/legacyloader/bridge/event/LegacyServerStoppingEvent");
        add("cpw/mods/fml/common/event/FMLServerStoppedEvent", "com/myname/legacyloader/bridge/event/LegacyServerStoppedEvent");
        add("cpw/mods/fml/common/event/FMLMissingMappingsEvent", "com/myname/legacyloader/bridge/event/LegacyMissingMappingsEvent");
        add("cpw/mods/fml/common/event/FMLMissingMappingsEvent$MissingMapping", "com/myname/legacyloader/bridge/event/LegacyMissingMappingsEvent$MissingMapping");
        add("cpw/mods/fml/common/event/FMLMissingMappingsEvent$Action", "com/myname/legacyloader/bridge/event/LegacyMissingMappingsEvent$Action");
        add("cpw/mods/fml/common/event/FMLInterModComms", "com/myname/legacyloader/bridge/event/LegacyFMLInterModComms");
        add("cpw/mods/fml/common/event/FMLInterModComms$IMCEvent", "com/myname/legacyloader/bridge/event/LegacyFMLInterModComms$IMCEvent");
        add("cpw/mods/fml/common/event/FMLInterModComms$IMCMessage", "com/myname/legacyloader/bridge/event/LegacyFMLInterModComms$IMCMessage");
        add("cpw/mods/fml/common/IWorldGenerator", "com/myname/legacyloader/bridge/fml/IWorldGenerator");
        add("cpw/mods/fml/common/IFuelHandler", "com/myname/legacyloader/bridge/fml/LegacyIFuelHandler");
        add("net/minecraft/world/chunk/IChunkProvider", "com/myname/legacyloader/bridge/world/IChunkProvider");
        add("net/minecraftforge/common/util/EnumHelper", "com/myname/legacyloader/bridge/util/LegacyEnumHelper");
        add("net/minecraftforge/oredict/OreDictionary", "com/myname/legacyloader/bridge/oredict/LegacyOreDictionary");
        add("net/minecraftforge/common/config/Configuration", "com/myname/legacyloader/bridge/config/LegacyConfiguration");
        add("net/minecraftforge/common/config/ConfigCategory", "com/myname/legacyloader/bridge/config/LegacyConfigCategory");
        add("net/minecraftforge/common/config/Property", "com/myname/legacyloader/bridge/config/LegacyProperty");
        add("net/minecraftforge/common/config/Property$Type", "com/myname/legacyloader/bridge/config/LegacyProperty$Type");
        add("net/minecraftforge/common/MinecraftForge", "com/myname/legacyloader/bridge/forge/LegacyMinecraftForge");
        add("net/minecraftforge/common/ForgeHooks", "com/myname/legacyloader/bridge/forge/LegacyForgeHooks");
        add("net/minecraftforge/common/util/BlockSnapshot", "com/myname/legacyloader/bridge/forge/LegacyBlockSnapshot");
        add("net/minecraftforge/client/MinecraftForgeClient", "com/myname/legacyloader/bridge/client/renderer/item/LegacyMinecraftForgeClient");
        add("net/minecraftforge/client/IItemRenderer", "com/myname/legacyloader/bridge/client/renderer/item/LegacyIItemRenderer");
        add("net/minecraftforge/client/IItemRenderer$ItemRenderType", "com/myname/legacyloader/bridge/client/renderer/item/LegacyItemRenderType");
        add("net/minecraftforge/client/IItemRenderer$ItemRendererHelper", "com/myname/legacyloader/bridge/client/renderer/item/LegacyItemRendererHelper");
        add("net/minecraft/network/PacketBuffer", "net/minecraft/network/FriendlyByteBuf");
        add("net/minecraftforge/common/IPlantable", "com/myname/legacyloader/bridge/forge/LegacyIPlantable");
        add("net/minecraftforge/common/util/ForgeDirection", "com/myname/legacyloader/bridge/forge/LegacyForgeDirection");
        add("net/minecraftforge/common/util/FakePlayer", "net/minecraft/world/entity/player/Player");
        add("net/minecraftforge/common/util/FakePlayerFactory", "com/myname/legacyloader/bridge/forge/LegacyFakePlayerFactory");
        add("net/minecraftforge/common/ForgeChunkManager", "com/myname/legacyloader/bridge/forge/chunk/LegacyForgeChunkManager");
        add("net/minecraftforge/common/ForgeChunkManager$LoadingCallback", "com/myname/legacyloader/bridge/forge/chunk/LegacyForgeChunkManagerLoadingCallback");
        add("net/minecraftforge/common/ForgeChunkManager$OrderedLoadingCallback", "com/myname/legacyloader/bridge/forge/chunk/LegacyForgeChunkManagerOrderedLoadingCallback");
        add("net/minecraftforge/common/ForgeChunkManager$Ticket", "com/myname/legacyloader/bridge/forge/chunk/LegacyTicket");
        add("net/minecraftforge/common/ForgeChunkManager$Type", "com/myname/legacyloader/bridge/forge/chunk/LegacyChunkManagerType");
        add("net/minecraftforge/event/entity/player/PlayerEvent", "com/myname/legacyloader/bridge/fml/LegacyPlayerEvent");
        add("net/minecraftforge/event/entity/player/BonemealEvent", "com/myname/legacyloader/bridge/fml/LegacyBonemealEvent");
        add("net/minecraftforge/event/world/WorldEvent", "com/myname/legacyloader/bridge/event/LegacyWorldEvent");
        add("net/minecraftforge/event/world/WorldEvent$Unload", "com/myname/legacyloader/bridge/event/LegacyWorldEvent$Unload");
        add("net/minecraftforge/event/world/BlockEvent", "com/myname/legacyloader/bridge/event/LegacyBlockEvent");
        add("net/minecraftforge/event/world/BlockEvent$PlaceEvent", "com/myname/legacyloader/bridge/event/LegacyBlockEvent$PlaceEvent");
        add("net/minecraftforge/event/world/BlockEvent$BreakEvent", "com/myname/legacyloader/bridge/event/LegacyBlockEvent$BreakEvent");
        add("net/minecraftforge/event/terraingen/PopulateChunkEvent", "com/myname/legacyloader/bridge/forge/event/LegacyPopulateChunkEvent");
        add("net/minecraftforge/event/terraingen/PopulateChunkEvent$Pre", "com/myname/legacyloader/bridge/forge/event/LegacyPopulateChunkEvent$Pre");
        add("net/minecraftforge/event/terraingen/PopulateChunkEvent$Post", "com/myname/legacyloader/bridge/forge/event/LegacyPopulateChunkEvent$Post");
        add("net/minecraftforge/event/terraingen/PopulateChunkEvent$Populate", "com/myname/legacyloader/bridge/forge/event/LegacyPopulateChunkEvent$Populate");
        add("net/minecraftforge/event/terraingen/PopulateChunkEvent$Populate$EventType", "com/myname/legacyloader/bridge/forge/event/LegacyPopulateChunkEvent$Populate$EventType");
        add("net/minecraftforge/event/terraingen/TerrainGen", "com/myname/legacyloader/bridge/forge/event/LegacyTerrainGen");
        add("net/minecraftforge/fluids/Fluid", "com/myname/legacyloader/bridge/fluids/LegacyFluid");
        add("net/minecraftforge/fluids/FluidStack", "com/myname/legacyloader/bridge/fluids/LegacyFluidStack");
        add("net/minecraftforge/fluids/FluidEvent", "com/myname/legacyloader/bridge/fluids/LegacyFluidEvent");
        add("net/minecraftforge/fluids/FluidEvent$FluidSpilledEvent", "com/myname/legacyloader/bridge/fluids/LegacyFluidEvent$FluidSpilledEvent");
        add("net/minecraftforge/fluids/FluidRegistry", "com/myname/legacyloader/bridge/fluids/LegacyFluidRegistry");
        add("net/minecraftforge/fluids/FluidContainerRegistry", "com/myname/legacyloader/bridge/fluids/LegacyFluidContainerRegistry");
        add("net/minecraftforge/fluids/IFluidBlock", "com/myname/legacyloader/bridge/fluids/LegacyIFluidBlock");
        add("net/minecraftforge/fluids/IFluidHandler", "com/myname/legacyloader/bridge/fluids/LegacyFluidHandler");
        add("net/minecraftforge/fluids/IFluidTank", "com/myname/legacyloader/bridge/fluids/LegacyFluidTank");
        add("net/minecraftforge/fluids/FluidTank", "com/myname/legacyloader/bridge/fluids/LegacyFluidTankImpl");
        add("net/minecraftforge/fluids/FluidTankInfo", "com/myname/legacyloader/bridge/fluids/LegacyFluidTankInfo");
        add("net/minecraftforge/oredict/ShapedOreRecipe", "com/myname/legacyloader/bridge/item/crafting/LegacyShapedOreRecipe");
        add("net/minecraftforge/oredict/ShapelessOreRecipe", "com/myname/legacyloader/bridge/item/crafting/LegacyShapelessOreRecipe");
        add("net/minecraftforge/oredict/RecipeSorter$Category", "com/myname/legacyloader/bridge/oredict/LegacyRecipeSorter$Category");
        add("net/minecraft/item/crafting/IRecipe", "com/myname/legacyloader/bridge/item/crafting/LegacyRecipe");
        add("cpw/mods/fml/common/FMLLog", "com/myname/legacyloader/bridge/fml/LegacyFMLLog");
        add("org/apache/logging/log4j/Level", "com/myname/legacyloader/bridge/fml/LegacyLogLevel");
        add("net/minecraft/world/World", "net/minecraft/world/level/Level");
        add("net/minecraft/world/WorldServer", "net/minecraft/server/level/ServerLevel");
        add("net/minecraft/world/WorldSavedData", "net/minecraft/world/level/saveddata/SavedData");
        add("net/minecraft/world/IBlockAccess", "net/minecraft/world/level/BlockGetter");
        add("net/minecraft/world/WorldProvider", "com/myname/legacyloader/bridge/world/LegacyWorldProvider");
        add("net/minecraft/world/WorldProviderSurface", "com/myname/legacyloader/bridge/world/LegacyWorldProviderSurface");
        add("net/minecraft/world/biome/BiomeGenBase", "com/myname/legacyloader/bridge/world/biome/LegacyBiomeGenBase");
        add("net/minecraft/world/biome/BiomeGenBase$Height", "com/myname/legacyloader/bridge/world/biome/LegacyBiomeGenBase$Height");
        add("net/minecraft/world/biome/BiomeGenDesert", "com/myname/legacyloader/bridge/world/biome/LegacyBiomeGenDesert");
        add("net/minecraft/world/biome/BiomeGenOcean", "com/myname/legacyloader/bridge/world/biome/LegacyBiomeGenOcean");
        add("net/minecraftforge/common/BiomeDictionary", "com/myname/legacyloader/bridge/forge/LegacyBiomeDictionary");
        add("net/minecraftforge/common/BiomeDictionary$Type", "com/myname/legacyloader/bridge/forge/LegacyBiomeDictionary$Type");
        add("net/minecraft/world/gen/feature/WorldGenMinable", "com/myname/legacyloader/bridge/world/gen/LegacyWorldGenMinable");
        add("net/minecraft/util/AxisAlignedBB", "net/minecraft/world/phys/AABB");
        add("net/minecraft/util/IChatComponent", "com/myname/legacyloader/bridge/util/LegacyIChatComponent");
        add("net/minecraft/util/ChatComponentText", "com/myname/legacyloader/bridge/util/LegacyChatComponentText");
        add("net/minecraft/util/ChatComponentTranslation", "com/myname/legacyloader/bridge/util/LegacyChatComponentTranslation");
        add("net/minecraft/util/ChatStyle", "com/myname/legacyloader/bridge/util/LegacyChatStyle");
        add("net/minecraft/util/EnumChatFormatting", "com/myname/legacyloader/bridge/util/LegacyEnumChatFormatting");
        add("net/minecraft/util/IntHashMap", "com/myname/legacyloader/bridge/util/LegacyIntHashMap");
        add("net/minecraft/util/LongHashMap", "com/myname/legacyloader/bridge/util/LegacyLongHashMap");
        add("net/minecraft/entity/Entity", "net/minecraft/world/entity/Entity");
        add("net/minecraft/entity/EntityLiving", "net/minecraft/world/entity/Mob");
        add("net/minecraft/entity/EntityLivingBase", "net/minecraft/world/entity/LivingEntity");
        add("net/minecraft/entity/item/EntityItem", "com/myname/legacyloader/bridge/entity/LegacyEntityItem");
        add("net/minecraft/entity/item/EntityItemFrame", "net/minecraft/world/entity/decoration/ItemFrame");
        add("net/minecraft/entity/item/EntityTNTPrimed", "net/minecraft/world/entity/item/PrimedTnt");
        add("net/minecraft/entity/player/EntityPlayer", "net/minecraft/world/entity/player/Player");
        add("net/minecraft/entity/player/EntityPlayerMP", "net/minecraft/server/level/ServerPlayer");
        add("net/minecraft/entity/player/EntityPlayerSP", "net/minecraft/client/player/LocalPlayer");
        add("net/minecraft/entity/player/InventoryPlayer", "net/minecraft/world/entity/player/Inventory");
        add("net/minecraft/entity/player/PlayerCapabilities", "net/minecraft/world/entity/player/Abilities");
        add("net/minecraft/entity/projectile/EntityArrow", "com/myname/legacyloader/bridge/entity/LegacyEntityArrow");
        add("net/minecraft/entity/projectile/EntityThrowable", "net/minecraft/world/entity/projectile/ThrowableProjectile");
        add("net/minecraft/inventory/IInventory", "com/myname/legacyloader/bridge/inventory/LegacyInventory");
        add("net/minecraft/inventory/ISidedInventory", "com/myname/legacyloader/bridge/inventory/LegacySidedInventory");
// ==========================================
//           Network System
// ==========================================
        add("cpw/mods/fml/common/network/NetworkRegistry", "com/myname/legacyloader/bridge/network/LegacyNetworkRegistry");
        add("cpw/mods/fml/common/network/FMLEmbeddedChannel", "com/myname/legacyloader/bridge/network/LegacyFMLEmbeddedChannel");
        add("cpw/mods/fml/common/network/FMLOutboundHandler", "com/myname/legacyloader/bridge/network/LegacyFMLOutboundHandler");
        add("cpw/mods/fml/common/network/FMLOutboundHandler$OutboundTarget", "com/myname/legacyloader/bridge/network/LegacyFMLOutboundHandler$OutboundTarget");
        add("cpw/mods/fml/common/network/internal/FMLProxyPacket", "com/myname/legacyloader/bridge/network/LegacyFMLProxyPacket");
        add("cpw/mods/fml/common/network/simpleimpl/SimpleNetworkWrapper", "com/myname/legacyloader/bridge/network/LegacySimpleNetworkWrapper");
        add("cpw/mods/fml/common/network/simpleimpl/IMessage", "com/myname/legacyloader/bridge/network/LegacyMessage");
        add("cpw/mods/fml/common/network/simpleimpl/IMessageHandler", "com/myname/legacyloader/bridge/network/LegacyMessageHandler");
        add("cpw/mods/fml/common/network/simpleimpl/MessageContext", "com/myname/legacyloader/bridge/network/LegacyMessageContext");
        add("cpw/mods/fml/common/network/IGuiHandler", "com/myname/legacyloader/bridge/network/IGuiHandler");
        add("cpw/mods/fml/common/network/NetworkCheckHandler", "com/myname/legacyloader/bridge/network/LegacyNetworkRegistry");
        add("cpw/mods/fml/client/event/ConfigChangedEvent", "com/myname/legacyloader/bridge/config/LegacyConfigChangedEvent");
        add("cpw/mods/fml/client/event/ConfigChangedEvent$OnConfigChangedEvent", "com/myname/legacyloader/bridge/config/LegacyConfigChangedEvent$OnConfigChangedEvent");
        add("cpw/mods/fml/client/event/ConfigChangedEvent$PostConfigChangedEvent", "com/myname/legacyloader/bridge/config/LegacyConfigChangedEvent$PostConfigChangedEvent");
        add("net/minecraftforge/client/event/TextureStitchEvent", "com/myname/legacyloader/bridge/client/event/LegacyTextureStitchEvent");
        add("net/minecraftforge/client/event/TextureStitchEvent$Pre", "com/myname/legacyloader/bridge/client/event/LegacyTextureStitchEvent$Pre");
        add("net/minecraftforge/client/event/TextureStitchEvent$Post", "com/myname/legacyloader/bridge/client/event/LegacyTextureStitchEvent$Post");
        // ==========================================
//           Mod Metadata
// ==========================================
        add("cpw/mods/fml/common/ModMetadata", "com/myname/legacyloader/bridge/fml/LegacyModMetadata");
        add("cpw/mods/fml/common/ModContainer", "com/myname/legacyloader/bridge/fml/LegacyModMetadata");
        add("cpw/mods/fml/common/MinecraftDummyContainer", "com/myname/legacyloader/bridge/fml/LegacyModMetadata");
        // Mod Metadata 繧｢繝弱ユ繝ｼ繧ｷ繝ｧ繝ｳ
        add("cpw/mods/fml/common/Mod$Metadata", "com/myname/legacyloader/bridge/fml/LegacyModMetadataAnnotation");
        add("cpw/mods/fml/common/Mod$Instance", "com/myname/legacyloader/bridge/fml/LegacyModInstanceAnnotation");
        add("cpw/mods/fml/common/Mod$EventHandler", "com/myname/legacyloader/bridge/fml/LegacyModEventHandlerAnnotation");
        add("cpw/mods/fml/common/SidedProxy", "com/myname/legacyloader/bridge/fml/LegacySidedProxy");
        add("cpw/mods/fml/relauncher/SideOnly", "com/myname/legacyloader/bridge/network/LegacySideOnly");
        add("net/minecraft/client/resources/LanguageManager", "com/myname/legacyloader/bridge/client/resources/LegacyLanguageManager");
        add("net/minecraft/client/resources/Language", "com/myname/legacyloader/bridge/client/resources/LegacyLanguage");
        add("cpw/mods/fml/relauncher/ReflectionHelper", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper");
        add("cpw/mods/fml/relauncher/ReflectionHelper$UnableToAccessFieldException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToAccessFieldException");
        add("cpw/mods/fml/relauncher/ReflectionHelper$UnableToFindFieldException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToFindFieldException");
        add("cpw/mods/fml/relauncher/ReflectionHelper$UnableToFindMethodException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToFindMethodException");
        add("net/minecraftforge/fml/relauncher/ReflectionHelper", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper");
        add("net/minecraftforge/fml/relauncher/ReflectionHelper$UnableToAccessFieldException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToAccessFieldException");
        add("net/minecraftforge/fml/relauncher/ReflectionHelper$UnableToFindFieldException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToFindFieldException");
        add("net/minecraftforge/fml/relauncher/ReflectionHelper$UnableToFindMethodException", "com/myname/legacyloader/bridge/fml/LegacyReflectionHelper$UnableToFindMethodException");
// ==========================================
//           Command System
// ==========================================
        add("net/minecraft/command/ICommand", "com/myname/legacyloader/bridge/command/LegacyICommand");
        add("net/minecraft/command/CommandBase", "com/myname/legacyloader/bridge/command/LegacyCommand");
        add("net/minecraft/command/ICommandSender", "com/myname/legacyloader/bridge/command/LegacyCommandSender");
        add("net/minecraft/command/CommandException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        // ==========================================
        //           Base Classes (Vanilla)
        // ==========================================
        // 笘・ｿｮ豁｣: 繝舌ル繝ｩ繧ｯ繝ｩ繧ｹ縺ｸ繝槭ャ繝斐Φ繧ｰ (Legacy繧ｯ繝ｩ繧ｹ縺ｧ縺ｯ縺ｪ縺・
        add("net/minecraft/block/Block", "net/minecraft/world/level/block/Block");
        add("net/minecraft/block/BlockLiquid", "net/minecraft/world/level/block/LiquidBlock");
        add("net/minecraft/block/BlockDynamicLiquid", "net/minecraft/world/level/block/LiquidBlock");
        add("net/minecraft/block/BlockLever", "net/minecraft/world/level/block/LeverBlock");
        add("net/minecraft/block/BlockButton", "net/minecraft/world/level/block/ButtonBlock");
        add("net/minecraft/block/BlockBasePressurePlate", "net/minecraft/world/level/block/PressurePlateBlock");
        add("net/minecraft/block/IGrowable", "net/minecraft/world/level/block/BonemealableBlock");
        add("net/minecraft/block/BlockChest", "net/minecraft/world/level/block/ChestBlock");
        add("net/minecraft/block/BlockBed", "net/minecraft/world/level/block/BedBlock");
        add("net/minecraft/block/BlockTorch", "net/minecraft/world/level/block/TorchBlock");
        add("net/minecraft/block/ITileEntityProvider", "com/myname/legacyloader/bridge/block/LegacyITileEntityProvider");
        add("net/minecraft/item/Item", "net/minecraft/world/item/Item");
        add("net/minecraft/item/ItemStack", "net/minecraft/world/item/ItemStack");
        add("net/minecraft/tileentity/TileEntity", "com/myname/legacyloader/bridge/tileentity/LegacyTileEntity");
        add("net/minecraft/stats/Achievement", "com/myname/legacyloader/bridge/stats/LegacyAchievement");
        add("net/minecraft/stats/StatBase", "com/myname/legacyloader/bridge/stats/LegacyStatBase");
        add("net/minecraftforge/common/AchievementPage", "com/myname/legacyloader/bridge/stats/LegacyAchievementPage");

        // Block Subclasses -> Legacy Wrapper or Vanilla
        // 邯呎価髢｢菫ゅｒ邯ｭ謖√☆繧九◆繧√√％繧後ｉ縺ｯLegacyBlock繧堤ｶ呎価縺励◆閾ｪ菴懊け繝ｩ繧ｹ縺ｸ繝槭ャ繝励☆繧九・縺檎炊諠ｳ縺ｧ縺吶′縲・
        // 蜊倡ｴ斐↑繧ｭ繝｣繧ｹ繝医お繝ｩ繝ｼ繧帝亟縺舌◆繧√∽ｸ譌ｦ繝舌ル繝ｩ縺ｸ騾・′縺吶°縲´egacyBlock縺ｸ髮・ｴ・＠縺ｾ縺吶・
        // 縺薙％縺ｧ縺ｯ螳牙・遲悶→縺励※縲｀od縺後ｈ縺冗ｶ呎価縺吶ｋ繧ｯ繝ｩ繧ｹ縺ｯ LegacyBlock 邉ｻ縺ｫ繝槭ャ繝励＠縺ｾ縺吶・

        add("net/minecraft/block/BlockSlab", "com/myname/legacyloader/bridge/block/LegacyBlockSlab");
        add("net/minecraft/block/BlockStairs", "com/myname/legacyloader/bridge/block/LegacyBlockStairs");
        add("net/minecraft/block/BlockCompressed", "com/myname/legacyloader/bridge/block/LegacyBlockCompressed");
        // 縺昴ｌ莉･螟悶・ LegacyBlock (縺薙ｌ縺ｯ net.minecraft.world.level.block.Block 繧堤ｶ呎価縺励※縺・ｋ)
        add("net/minecraft/block/BlockContainer", "com/myname/legacyloader/bridge/block/LegacyContainerBlock");
        add("net/minecraft/block/BlockOre", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockLog", "com/myname/legacyloader/bridge/block/LegacyBlockPillar"); // Log -> Pillar
        add("net/minecraft/block/BlockFalling", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPane", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFence", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockTrapDoor", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDoor", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCommandBlock", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockBush", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCrops", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFlower", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockReed", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockLeaves", "com/myname/legacyloader/bridge/block/LegacyBlock");

        // Item Subclasses
        add("net/minecraft/item/ItemBlock", "com/myname/legacyloader/bridge/item/LegacyBlockItem");
        add("net/minecraft/item/ItemBlockWithMetadata", "com/myname/legacyloader/bridge/item/LegacyItemBlockWithMetadata");
        // 繝・・繝ｫ鬘槭・Legacy繧ｯ繝ｩ繧ｹ縺ｸ (繝舌ル繝ｩItem繧堤ｶ呎価縺励※縺・ｋ)
        add("net/minecraft/item/ItemSword", "com/myname/legacyloader/bridge/item/LegacyItemSword");
        add("net/minecraft/item/ItemPickaxe", "com/myname/legacyloader/bridge/item/LegacyItemPickaxe");
        add("net/minecraft/item/ItemAxe", "com/myname/legacyloader/bridge/item/LegacyItemAxe");
        add("net/minecraft/item/ItemHoe", "com/myname/legacyloader/bridge/item/LegacyItemHoe");
        add("net/minecraft/item/ItemSpade", "com/myname/legacyloader/bridge/item/LegacyItemSpade");
        add("net/minecraft/item/ItemArmor", "com/myname/legacyloader/bridge/item/LegacyItemArmor");
        add("net/minecraft/item/ItemFood", "com/myname/legacyloader/bridge/item/LegacyItemFood");
        add("net/minecraft/item/ItemBucket", "com/myname/legacyloader/bridge/item/LegacyItemBucket");

        // Materials / Tiers
        add("net/minecraft/block/material/Material", "com/myname/legacyloader/bridge/block/LegacyMaterial");
        add("net/minecraft/block/material/MaterialLiquid", "com/myname/legacyloader/bridge/block/LegacyMaterial");
        add("net/minecraft/block/material/MaterialLogic", "com/myname/legacyloader/bridge/block/LegacyMaterial");
        add("net/minecraft/block/material/MaterialPortal", "com/myname/legacyloader/bridge/block/LegacyMaterial");
        add("net/minecraft/block/material/MaterialTransparent", "com/myname/legacyloader/bridge/block/LegacyMaterial");
        add("net/minecraft/block/material/MapColor", "com/myname/legacyloader/bridge/block/LegacyMapColor");
        add("net/minecraftforge/fluids/BlockFluidBase", "com/myname/legacyloader/bridge/fluids/LegacyBlockFluidClassic");
        add("net/minecraftforge/fluids/BlockFluidClassic", "com/myname/legacyloader/bridge/fluids/LegacyBlockFluidClassic");
        add("net/minecraft/item/Item$ToolMaterial", "com/myname/legacyloader/bridge/item/LegacyTier");
        add("net/minecraft/item/ItemArmor$ArmorMaterial", "com/myname/legacyloader/bridge/item/LegacyArmorMaterial");
        add("net/minecraft/creativetab/CreativeTabs", "com/myname/legacyloader/bridge/item/LegacyCreativeTab");

        // Init Constants
        add("net/minecraft/init/Blocks", "com/myname/legacyloader/bridge/init/LegacyBlocks");
        add("net/minecraft/init/Items", "com/myname/legacyloader/bridge/init/LegacyItems");

        // Client
        add("net/minecraft/client/renderer/texture/IIconRegister", "com/myname/legacyloader/bridge/client/LegacyIconRegister");
        add("net/minecraft/client/renderer/texture/TextureMap", "com/myname/legacyloader/bridge/client/LegacyTextureMap");
        add("net/minecraft/client/renderer/texture/TextureAtlasSprite", "com/myname/legacyloader/bridge/client/LegacyIcon");
        add("net/minecraft/client/gui/GuiScreen", "com/myname/legacyloader/bridge/client/gui/LegacyGuiScreen");
        add("net/minecraft/client/gui/GuiErrorScreen", "com/myname/legacyloader/bridge/client/gui/LegacyGuiScreen");
        add("net/minecraft/client/settings/GameSettings", "com/myname/legacyloader/bridge/client/settings/LegacyGameSettings");
        add("net/minecraft/client/settings/GameSettings$Options", "com/myname/legacyloader/bridge/client/settings/LegacyGameSettings$Options");
        add("net/minecraft/client/gui/FontRenderer", "net/minecraft/client/gui/Font");
        add("net/minecraft/client/particle/EffectRenderer", "net/minecraft/client/particle/ParticleEngine");
        add("net/minecraft/client/particle/EntityFX", "com/myname/legacyloader/bridge/client/particle/LegacyEntityFX");
        add("net/minecraft/client/particle/EntityBreakingFX", "com/myname/legacyloader/bridge/client/particle/LegacyEntityBreakingFX");
        add("net/minecraft/client/particle/EntityDiggingFX", "net/minecraft/client/particle/TerrainParticle");
        add("cpw/mods/fml/client/IModGuiFactory", "com/myname/legacyloader/bridge/client/config/LegacyModGuiFactory");
        add("cpw/mods/fml/client/IModGuiFactory$RuntimeOptionCategoryElement", "com/myname/legacyloader/bridge/client/config/LegacyModGuiFactory$RuntimeOptionCategoryElement");
        add("cpw/mods/fml/client/IModGuiFactory$RuntimeOptionGuiHandler", "com/myname/legacyloader/bridge/client/config/LegacyModGuiFactory$RuntimeOptionGuiHandler");
        add("cpw/mods/fml/client/config/GuiConfig", "com/myname/legacyloader/bridge/client/config/LegacyGuiConfig");
        add("net/minecraft/client/resources/I18n", "com/myname/legacyloader/bridge/client/config/LegacyI18n");
        add("net/minecraft/client/resources/IResourceManager", "com/myname/legacyloader/bridge/client/resources/LegacyIResourceManager");
        add("net/minecraft/client/resources/IResource", "com/myname/legacyloader/bridge/client/resources/LegacyResource");
        add("net/minecraft/util/IIcon", "com/myname/legacyloader/bridge/client/LegacyIcon");
        add("net/minecraft/util/ResourceLocation", "net/minecraft/resources/ResourceLocation");
        add("net/minecraft/util/RegistryNamespaced", "com/myname/legacyloader/bridge/registry/LegacyRegistryNamespaced");
        add("net/minecraft/network/Packet", "com/myname/legacyloader/bridge/network/LegacyMinecraftPacket");
        add("net/minecraft/network/INetHandler", "com/myname/legacyloader/bridge/network/LegacyNetHandler");
        add("net/minecraft/nbt/NBTBase", "net/minecraft/nbt/Tag");
        add("net/minecraft/nbt/NBTTagByte", "net/minecraft/nbt/ByteTag");
        add("net/minecraft/nbt/NBTTagCompound", "net/minecraft/nbt/CompoundTag");
        add("net/minecraft/nbt/NBTTagDouble", "net/minecraft/nbt/DoubleTag");
        add("net/minecraft/nbt/NBTTagFloat", "net/minecraft/nbt/FloatTag");
        add("net/minecraft/nbt/NBTTagInt", "net/minecraft/nbt/IntTag");
        add("net/minecraft/nbt/NBTTagList", "net/minecraft/nbt/ListTag");
        add("net/minecraft/nbt/NBTTagLong", "net/minecraft/nbt/LongTag");
        add("net/minecraft/nbt/NBTTagShort", "net/minecraft/nbt/ShortTag");
        add("net/minecraft/nbt/NBTTagString", "net/minecraft/nbt/StringTag");
        add("net/minecraft/nbt/CompressedStreamTools", "net/minecraft/nbt/NbtIo");
        add("net/minecraft/nbt/NBTSizeTracker", "net/minecraft/nbt/NbtAccounter");
        add("net/minecraft/client/multiplayer/WorldClient", "net/minecraft/client/multiplayer/ClientLevel");
        add("net/minecraft/client/entity/EntityClientPlayerMP", "net/minecraft/client/player/LocalPlayer");
        add("net/minecraft/client/entity/EntityPlayerSP", "net/minecraft/client/player/LocalPlayer");
        add("net/minecraft/client/audio/ISound", "com/myname/legacyloader/bridge/client/audio/LegacyISound");
        add("net/minecraft/client/audio/ISound$AttenuationType", "com/myname/legacyloader/bridge/client/audio/LegacyISound$AttenuationType");
        add("net/minecraft/client/audio/MovingSound", "com/myname/legacyloader/bridge/client/audio/LegacyMovingSound");
        add("net/minecraft/client/audio/SoundHandler", "com/myname/legacyloader/bridge/client/audio/LegacySoundHandler");
        add("cpw/mods/fml/relauncher/Side", "com/myname/legacyloader/bridge/network/LegacySide");
        add("net/minecraft/client/model/ModelBase", "com/myname/legacyloader/bridge/client/model/LegacyModelBase");
        add("net/minecraft/client/model/ModelSlime", "com/myname/legacyloader/bridge/client/model/LegacyModelSlime");
        add("net/minecraft/client/model/ModelChicken", "com/myname/legacyloader/bridge/client/model/LegacyModelChicken");
        add("net/minecraft/client/model/ModelRenderer", "com/myname/legacyloader/bridge/client/model/LegacyModelRenderer");
        add("net/minecraft/client/model/ModelBiped", "com/myname/legacyloader/bridge/client/model/LegacyModelBiped");
        add("net/minecraft/client/model/PositionTextureVertex", "com/myname/legacyloader/bridge/client/model/LegacyPositionTextureVertex");
        add("net/minecraft/client/model/TexturedQuad", "com/myname/legacyloader/bridge/client/model/LegacyTexturedQuad");
        add("net/minecraft/client/renderer/RenderBlocks", "com/myname/legacyloader/bridge/client/renderer/LegacyRenderBlocks");
        add("net/minecraft/client/renderer/Tessellator", "com/myname/legacyloader/bridge/client/renderer/LegacyTessellator");
        add("net/minecraft/client/renderer/entity/RenderItem", "com/myname/legacyloader/bridge/client/renderer/entity/LegacyRenderItem");
        add("net/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher", "com/myname/legacyloader/bridge/client/renderer/tileentity/LegacyTileEntityRendererDispatcher");
        add("org/lwjgl/opengl/GL11", "com/myname/legacyloader/bridge/client/renderer/LegacyGL11");
        add("net/minecraft/client/renderer/entity/Render", "com/myname/legacyloader/bridge/client/renderer/entity/LegacyRender");
        add("net/minecraft/client/renderer/entity/RenderManager", "com/myname/legacyloader/bridge/client/renderer/entity/LegacyRenderManager");
        add("cpw/mods/fml/client/registry/ISimpleBlockRenderingHandler", "com/myname/legacyloader/bridge/client/registry/LegacySimpleBlockRenderingHandler");
        add("cpw/mods/fml/client/registry/RenderingRegistry", "com/myname/legacyloader/bridge/client/registry/LegacyRenderingRegistry");

        // ==========================================
        //           Methods Mapping (To Helpers)
        // ==========================================
        // 笘・ｿｮ豁｣: 繝｡繧ｽ繝・ラ蜻ｼ縺ｳ蜃ｺ縺励ｒ Helper 繧ｯ繝ｩ繧ｹ縺ｮ static 繝｡繧ｽ繝・ラ縺ｸ繝ｪ繝繧､繝ｬ繧ｯ繝・
        // 縺薙ｌ縺ｫ繧医ｊ縲√う繝ｳ繧ｹ繧ｿ繝ｳ繧ｹ縺・LegacyBlock 縺ｧ縺ｪ縺上※繧ゑｼ医ヰ繝九ΛBlock縺ｧ繧ゑｼ牙虚菴懊＠縺ｾ縺・

        String itemHelper = "com/myname/legacyloader/bridge/item/LegacyItemHelper";
        String blockHelper = "com/myname/legacyloader/bridge/block/LegacyBlockHelper";
        String itemClass = "net/minecraft/item/Item";
        String blockClass = "net/minecraft/block/Block";

        // Item Methods
        CLASS_MAP.put(itemClass + ".func_77655_b", itemHelper + ".setUnlocalizedName");
        CLASS_MAP.put(itemClass + ".setUnlocalizedName", itemHelper + ".setUnlocalizedName");

        CLASS_MAP.put(itemClass + ".func_111206_d", itemHelper + ".setTextureName");
        CLASS_MAP.put(itemClass + ".setTextureName", itemHelper + ".setTextureName");

        CLASS_MAP.put(itemClass + ".func_77637_a", itemHelper + ".setCreativeTab");
        CLASS_MAP.put(itemClass + ".setCreativeTab", itemHelper + ".setCreativeTab");

        // Block Methods
        CLASS_MAP.put(blockClass + ".func_149663_c", blockHelper + ".setBlockName");
        CLASS_MAP.put(blockClass + ".setBlockName", blockHelper + ".setBlockName");

        CLASS_MAP.put(blockClass + ".func_149658_d", blockHelper + ".setBlockTextureName");
        CLASS_MAP.put(blockClass + ".setBlockTextureName", blockHelper + ".setBlockTextureName");

        CLASS_MAP.put(blockClass + ".func_149647_a", blockHelper + ".setCreativeTab");
        CLASS_MAP.put(blockClass + ".setCreativeTab", blockHelper + ".setCreativeTab");

        CLASS_MAP.put(blockClass + ".func_149711_c", blockHelper + ".setHardness");
        CLASS_MAP.put(blockClass + ".setHardness", blockHelper + ".setHardness");

        CLASS_MAP.put(blockClass + ".func_149752_b", blockHelper + ".setResistance");
        CLASS_MAP.put(blockClass + ".setResistance", blockHelper + ".setResistance");

        CLASS_MAP.put(blockClass + ".func_149672_a", blockHelper + ".setStepSound");
        CLASS_MAP.put(blockClass + ".setStepSound", blockHelper + ".setStepSound");

        CLASS_MAP.put(blockClass + ".func_149713_g", blockHelper + ".setLightOpacity");
        CLASS_MAP.put(blockClass + ".setLightOpacity", blockHelper + ".setLightOpacity");

        CLASS_MAP.put(blockClass + ".func_149715_a", blockHelper + ".setLightLevel");
        CLASS_MAP.put(blockClass + ".setLightLevel", blockHelper + ".setLightLevel");

        CLASS_MAP.put(blockClass + ".func_149676_a", blockHelper + ".setBlockBounds");
        CLASS_MAP.put(blockClass + ".setBlockBounds", blockHelper + ".setBlockBounds");

        // CreativeTabs
        String tabClass = "net/minecraft/creativetab/CreativeTabs";
        CLASS_MAP.put(tabClass + ".func_78016_d", "getTabIconItem");
        CLASS_MAP.put(tabClass + ".getTabIconItem", "getTabIconItem");
        CLASS_MAP.put(tabClass + ".func_151244_d", "getIconItemStack");
        CLASS_MAP.put(tabClass + ".getIconItemStack", "getIconItemStack");

        CLASS_MAP.put("net/minecraft/util/ResourceLocation.func_110624_b", "getNamespace");
        CLASS_MAP.put("net/minecraft/util/ResourceLocation.func_110623_a", "getPath");

        // ==========================================
        //           Entity / Mob classes
        // ==========================================
        add("net/minecraft/entity/EntityLivingBase", "net/minecraft/world/entity/LivingEntity");
        add("net/minecraft/entity/ai/EntityAIBase", "com/myname/legacyloader/bridge/entity/LegacyEntityAIBase");
        add("net/minecraft/entity/monster/EntityMob", "net/minecraft/world/entity/monster/Monster");
        add("net/minecraft/entity/monster/EntityCreature", "net/minecraft/world/entity/PathfinderMob");
        add("net/minecraft/entity/passive/EntityAnimal", "net/minecraft/world/entity/animal/Animal");
        add("net/minecraft/entity/passive/EntityAgeable", "net/minecraft/world/entity/AgeableMob");
        add("net/minecraft/entity/EntityCreature", "net/minecraft/world/entity/PathfinderMob");

        // ==========================================
        //           Inventory / Container
        // ==========================================
        add("net/minecraft/inventory/Container", "com/myname/legacyloader/bridge/inventory/LegacyContainer");
        add("net/minecraft/inventory/InventoryCrafting", "com/myname/legacyloader/bridge/inventory/LegacyInventoryCrafting");
        add("net/minecraft/inventory/Slot", "com/myname/legacyloader/bridge/inventory/LegacySlot");
        add("net/minecraft/inventory/SlotFurnaceOutput", "com/myname/legacyloader/bridge/inventory/LegacySlotFurnaceOutput");

        // ==========================================
        //           World generation (BuildCraft)
        // ==========================================
        add("net/minecraft/world/gen/feature/WorldGenerator", "com/myname/legacyloader/bridge/world/gen/LegacyWorldGenerator");
        add("net/minecraft/world/gen/structure/StructureBoundingBox", "com/myname/legacyloader/bridge/world/gen/LegacyStructureBoundingBox");

        // ==========================================
        //           Math / Util
        // ==========================================
        add("net/minecraft/util/Vec3", "com/myname/legacyloader/bridge/util/LegacyVec3");
        add("net/minecraft/util/Vec3i", "net/minecraft/core/Vec3i");
        add("net/minecraft/util/MathHelper", "net/minecraft/util/Mth");
        add("net/minecraft/util/MovingObjectPosition", "com/myname/legacyloader/bridge/util/LegacyMovingObjectPosition");
        add("net/minecraft/util/MovingObjectPosition$MovingObjectType", "com/myname/legacyloader/bridge/util/LegacyMovingObjectPosition$MovingObjectType");

        // ==========================================
        //           BlockPos / Direction
        // ==========================================
        add("net/minecraft/util/BlockPos", "net/minecraft/core/BlockPos");
        add("net/minecraft/util/EnumFacing", "com/myname/legacyloader/bridge/forge/LegacyForgeDirection");

        // ==========================================
        //           Pipe / Tile entity (BuildCraft)
        // ==========================================
        add("buildcraft/api/core/ISerializable", "com/myname/legacyloader/bridge/buildcraft/ILegacySerializable");
        add("buildcraft/api/transport/IPipeTile", "com/myname/legacyloader/bridge/buildcraft/LegacyIPipeTile");
        add("buildcraft/api/transport/IPipeTile$PipeType", "com/myname/legacyloader/bridge/buildcraft/LegacyIPipeTile$PipeType");

        // ==========================================
        //           Extra block classes
        // ==========================================
        add("net/minecraft/block/BlockRotatedPillar", "com/myname/legacyloader/bridge/block/LegacyBlockPillar");
        add("net/minecraft/block/BlockDirectional", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockBreakable", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockGlass", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStainedGlass", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSlime", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockHalfStoneSlab", "com/myname/legacyloader/bridge/block/LegacyBlockSlab");
        add("net/minecraft/block/BlockHalfWoodSlab", "com/myname/legacyloader/bridge/block/LegacyBlockSlab");
        add("net/minecraft/block/BlockDoubleStoneSlab", "com/myname/legacyloader/bridge/block/LegacyBlockSlab");
        add("net/minecraft/block/BlockDoubleWoodSlab", "com/myname/legacyloader/bridge/block/LegacyBlockSlab");

        // ==========================================
        //           Extra Item classes
        // ==========================================
        add("net/minecraft/item/ItemTool", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemStack$Func", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/Item$ToolMaterial", "com/myname/legacyloader/bridge/item/LegacyTier");

        // ==========================================
        //           Extra utility classes
        // ==========================================
        add("net/minecraft/util/StatCollector", "com/myname/legacyloader/bridge/client/config/LegacyI18n");
        add("net/minecraft/server/MinecraftServer", "com/myname/legacyloader/bridge/server/LegacyMinecraftServer");
        add("net/minecraft/profiler/Profiler", "com/myname/legacyloader/bridge/util/LegacyProfiler");

        // ==========================================
        // Auto-generated broad 1.7.10 fallback mappings (from provided source)
        // These prefer behaviour-preserving bridge bases over raw runtime stubs.
        // ==========================================
        add("net/minecraft/block/BlockAir", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockAnvil", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockBeacon", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockBookshelf", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockBrewingStand", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockButtonStone", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockButtonWood", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCactus", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCake", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCarpet", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCarrot", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCauldron", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockClay", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCocoa", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockColored", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockCompressedPowered", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDaylightDetector", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDeadBush", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDirt", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDispenser", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDoublePlant", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDragonEgg", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockDropper", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockEnchantmentTable", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockEndPortal", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockEndPortalFrame", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockEnderChest", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockEventData", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFarmland", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFenceGate", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFire", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFlowerPot", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockFurnace", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockGlowstone", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockGrass", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockGravel", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockHardenedClay", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockHay", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockHopper", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockHugeMushroom", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockIce", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockJukebox", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockLadder", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockLeavesBase", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockLilyPad", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockMelon", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockMobSpawner", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockMushroom", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockMycelium", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockNetherWart", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockNetherrack", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockNewLeaf", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockNewLog", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockNote", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockObsidian", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockOldLeaf", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockOldLog", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPackedIce", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPistonBase", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPistonExtension", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPistonMoving", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPortal", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPotato", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPressurePlate", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPressurePlateWeighted", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockPumpkin", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockQuartz", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRail", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRailBase", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRailDetector", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRailPowered", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneComparator", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneDiode", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneLight", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneOre", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneRepeater", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneTorch", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockRedstoneWire", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSand", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSandStone", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSapling", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSign", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSilverfish", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSkull", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSnow", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSnowBlock", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSoulSand", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSourceImpl", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockSponge", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStainedGlassPane", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStaticLiquid", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStem", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStone", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStoneBrick", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockStoneSlab", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockTNT", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockTallGrass", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockTripWire", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockTripWireHook", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockVine", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockWall", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockWeb", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockWood", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockWoodSlab", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/block/BlockWorkbench", "com/myname/legacyloader/bridge/block/LegacyBlock");
        add("net/minecraft/inventory/InventoryBasic", "com/myname/legacyloader/bridge/inventory/LegacyInventoryBasic");
        add("net/minecraft/item/ItemAnvilBlock", "com/myname/legacyloader/bridge/item/LegacyBlockItem");
        add("net/minecraft/item/ItemAppleGold", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemBed", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemBoat", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemBook", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemBow", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemBucketMilk", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemCarrotOnAStick", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemCloth", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemCoal", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemColored", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemDoor", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemDoublePlant", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemDye", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEditableBook", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEgg", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEmptyMap", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEnchantedBook", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEnderEye", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemEnderPearl", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemExpBottle", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFireball", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFirework", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFireworkCharge", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFishFood", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFishingRod", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemFlintAndSteel", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemGlassBottle", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemHangingEntity", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemLead", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemLeaves", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemLilyPad", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemMap", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemMapBase", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemMinecart", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemMonsterPlacer", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemMultiTexture", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemNameTag", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemPiston", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemPotion", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemRecord", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemRedstone", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemReed", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSaddle", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSeedFood", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSeeds", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemShears", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSign", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSimpleFoiled", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSkull", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSlab", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSnow", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSnowball", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemSoup", "com/myname/legacyloader/bridge/item/LegacyItem");
        add("net/minecraft/item/ItemWritableBook", "com/myname/legacyloader/bridge/item/LegacyItem");

        // Target-mod completion pass: annotations, client registry, extra utility bridges
        add("cpw/mods/fml/common/Mod", "com/myname/legacyloader/bridge/fml/LegacyMod");
        add("cpw/mods/fml/common/API", "com/myname/legacyloader/bridge/fml/LegacyAPI");
        add("cpw/mods/fml/common/Optional$Method", "com/myname/legacyloader/bridge/fml/LegacyOptionalMethod");
        add("cpw/mods/fml/common/Optional/Method", "com/myname/legacyloader/bridge/fml/LegacyOptionalMethod");
        add("cpw/mods/fml/common/event/FMLEvent", "com/myname/legacyloader/bridge/event/LegacyFMLEvent");
        add("cpw/mods/fml/client/FMLClientHandler", "com/myname/legacyloader/bridge/fml/LegacyFMLClientHandler");
        add("cpw/mods/fml/client/registry/ClientRegistry", "com/myname/legacyloader/bridge/client/registry/LegacyClientRegistry");
        add("net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer", "com/myname/legacyloader/bridge/client/renderer/tileentity/LegacyTileEntitySpecialRenderer");
        // Forge OBJ ローダ (AsphaltMod 等のカスタム 3D ブロックが TESR で描く .obj モデル)
        add("net/minecraftforge/client/model/AdvancedModelLoader", "com/myname/legacyloader/bridge/client/model/LegacyAdvancedModelLoader");
        add("net/minecraftforge/client/model/IModelCustom", "com/myname/legacyloader/bridge/client/model/LegacyModelCustom");
        add("cpw/mods/fml/common/network/ByteBufUtils", "com/myname/legacyloader/bridge/network/LegacyByteBufUtils");
        add("net/minecraft/world/EnumSkyBlock", "com/myname/legacyloader/bridge/world/LegacyEnumSkyBlock");
        add("net/minecraft/client/renderer/IconFlipped", "com/myname/legacyloader/bridge/client/LegacyIconFlipped");
        add("net/minecraftforge/client/event/DrawBlockHighlightEvent", "com/myname/legacyloader/bridge/client/event/LegacyDrawBlockHighlightEvent");
        add("net/minecraftforge/event/ForgeEventFactory", "com/myname/legacyloader/bridge/forge/event/LegacyForgeEventFactory");
        add("net/minecraftforge/common/DimensionManager", "com/myname/legacyloader/bridge/forge/LegacyDimensionManager");
        add("net/minecraftforge/common/IShearable", "com/myname/legacyloader/bridge/forge/LegacyIShearable");
        add("net/minecraftforge/oredict/RecipeSorter", "com/myname/legacyloader/bridge/oredict/LegacyRecipeSorter");
        // Ha10gen/H10Lib focused mappings
        add("net/minecraft/util/Facing", "com/myname/legacyloader/bridge/util/LegacyFacing");
        add("net/minecraft/world/ColorizerGrass", "com/myname/legacyloader/bridge/world/LegacyColorizerGrass");
        add("net/minecraft/world/GameRules", "com/myname/legacyloader/bridge/world/LegacyGameRules");
        add("net/minecraft/world/chunk/Chunk", "com/myname/legacyloader/bridge/world/LegacyChunk");
        add("net/minecraftforge/common/config/ConfigElement", "com/myname/legacyloader/bridge/config/LegacyConfigElement");
        add("net/minecraftforge/event/world/ChunkEvent", "com/myname/legacyloader/bridge/event/LegacyChunkEvent");
        add("net/minecraft/command/NumberInvalidException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        add("net/minecraft/command/PlayerNotFoundException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        add("net/minecraft/command/WrongUsageException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        add("net/minecraft/command/CommandNotFoundException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        add("net/minecraft/command/SyntaxErrorException", "com/myname/legacyloader/bridge/command/LegacyCommandException");
        add("net/minecraft/command/ICommandManager", "com/myname/legacyloader/bridge/command/LegacyCommandManager");
        add("net/minecraft/command/IAdminCommand", "com/myname/legacyloader/bridge/command/LegacyIAdminCommand");
        add("net/minecraft/command/IEntitySelector", "com/myname/legacyloader/bridge/command/LegacyIEntitySelector");
        add("net/minecraft/command/PlayerSelector", "com/myname/legacyloader/bridge/command/LegacyPlayerSelector");
        add("net/minecraft/command/CommandHandler", "com/myname/legacyloader/bridge/command/LegacyCommandHandler");
        add("net/minecraft/command/ServerCommandManager", "com/myname/legacyloader/bridge/command/LegacyServerCommandManager");
        add("net/minecraft/command/ServerCommand", "com/myname/legacyloader/bridge/command/LegacyServerCommand");
        add("net/minecraft/command/server/CommandBlockLogic", "com/myname/legacyloader/bridge/command/LegacyCommandBlockLogic");
        add("net/minecraft/command/CommandClearInventory", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandDebug", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandDefaultGameMode", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandDifficulty", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandEffect", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandEnchant", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandGameMode", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandGameRule", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandGive", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandHelp", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandKill", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandPlaySound", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandServerKick", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandSetPlayerTimeout", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandSetSpawnpoint", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandShowSeed", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandSpreadPlayers", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandTime", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandToggleDownfall", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandWeather", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/CommandXP", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandAchievement", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandBanIp", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandBanPlayer", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandBroadcast", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandDeOp", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandEmote", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandListBans", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandListPlayers", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandMessage", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandMessageRaw", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandNetstat", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandOp", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandPardonIp", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandPardonPlayer", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandPublishLocalServer", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSaveAll", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSaveOff", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSaveOn", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandScoreboard", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSetBlock", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSetDefaultSpawnpoint", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandStop", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandSummon", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandTeleport", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandTestFor", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandTestForBlock", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/command/server/CommandWhitelist", "com/myname/legacyloader/bridge/command/LegacyNoOpCommand");
        add("net/minecraft/item/crafting/FurnaceRecipes", "com/myname/legacyloader/bridge/item/crafting/LegacyFurnaceRecipes");
        add("net/minecraft/stats/StatList", "com/myname/legacyloader/bridge/stats/LegacyStatList");
        add("net/minecraft/stats/StatisticsFile", "com/myname/legacyloader/bridge/stats/LegacyStatisticsFile");
        add("net/minecraft/util/DamageSource", "com/myname/legacyloader/bridge/util/LegacyDamageSource");
        add("net/minecraft/util/IJsonSerializable", "com/myname/legacyloader/bridge/util/LegacyIJsonSerializable");
        add("net/minecraft/util/JsonSerializableSet", "com/myname/legacyloader/bridge/util/LegacyJsonSerializableSet");
        add("net/minecraft/enchantment/Enchantment", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentArrowDamage", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentArrowFire", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentArrowInfinite", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentArrowKnockback", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentDamage", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentData", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantmentData");
        add("net/minecraft/enchantment/EnchantmentDigging", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentDurability", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentFireAspect", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentFishingSpeed", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentHelper", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantmentHelper");
        add("net/minecraft/enchantment/EnchantmentKnockback", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentLootBonus", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentOxygen", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentProtection", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentThorns", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentUntouching", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnchantmentWaterWorker", "com/myname/legacyloader/bridge/enchantment/LegacyEnchantment");
        add("net/minecraft/enchantment/EnumEnchantmentType", "com/myname/legacyloader/bridge/enchantment/LegacyEnumEnchantmentType");
        add("net/minecraft/crash/CrashReport", "com/myname/legacyloader/bridge/crash/LegacyCrashReport");
        add("net/minecraft/crash/CrashReportCategory", "com/myname/legacyloader/bridge/crash/LegacyCrashReportCategory");
        add("net/minecraft/dispenser/BehaviorDefaultDispenseItem", "com/myname/legacyloader/bridge/dispenser/LegacyBehaviorDefaultDispenseItem");
        add("net/minecraft/dispenser/BehaviorProjectileDispense", "com/myname/legacyloader/bridge/dispenser/LegacyBehaviorProjectileDispense");
        add("net/minecraft/dispenser/IBehaviorDispenseItem", "com/myname/legacyloader/bridge/dispenser/LegacyIBehaviorDispenseItem");
        add("net/minecraft/dispenser/IBlockSource", "com/myname/legacyloader/bridge/dispenser/LegacyIBlockSource");
        add("net/minecraft/dispenser/ILocatableSource", "com/myname/legacyloader/bridge/dispenser/LegacyILocatableSource");
        add("net/minecraft/dispenser/ILocation", "com/myname/legacyloader/bridge/dispenser/LegacyILocation");
        add("net/minecraft/dispenser/IPosition", "com/myname/legacyloader/bridge/dispenser/LegacyIPosition");
        add("net/minecraft/dispenser/PositionImpl", "com/myname/legacyloader/bridge/dispenser/LegacyPositionImpl");
        add("net/minecraft/entity/EntityList", "com/myname/legacyloader/bridge/entity/LegacyEntityList");
        add("net/minecraft/client/renderer/EntityRenderer", "com/myname/legacyloader/bridge/client/renderer/LegacyEntityRenderer");
        add("net/minecraft/client/renderer/OpenGlHelper", "com/myname/legacyloader/bridge/client/renderer/LegacyOpenGlHelper");
        add("net/minecraft/client/shader/TesselatorVertexState", "com/myname/legacyloader/bridge/client/renderer/LegacyTesselatorVertexState");
        add("net/minecraft/client/gui/inventory/GuiContainer", "com/myname/legacyloader/bridge/client/gui/LegacyGuiContainer");
    }

    private static void add(String oldName, String newName) {
        CLASS_MAP.put(oldName, newName);
    }

    public static Map<String, String> getMap() {
        return CLASS_MAP;
    }
}
