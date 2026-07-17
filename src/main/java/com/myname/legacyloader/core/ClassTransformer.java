package com.myname.legacyloader.core;

import com.myname.legacyloader.bridge.Mappings;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.util.ArrayList;
import java.util.List;

public class ClassTransformer {

    public static byte[] transform(byte[] basicClass) {
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

            ClassVisitor fixVisitor = new FixingClassVisitor(Opcodes.ASM9, writer);
            SimpleRemapper remapper = new SimpleRemapper(Mappings.getMap());
            ClassVisitor remappingVisitor = new ClassRemapper(fixVisitor, remapper);

            reader.accept(remappingVisitor, 0);

            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("LegacyLoader: Error transforming class");
            e.printStackTrace();
            return basicClass;
        }
    }

    private static class FixingClassVisitor extends ClassVisitor {
        private String currentClassName;
        private String currentSuperClass;

        public FixingClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.currentClassName = name;
            this.currentSuperClass = superName;

            if (!isLegacyLoaderClass(name)) {
                superName = fixSuperClass(superName);
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        private boolean isLegacyLoaderClass(String name) {
            return name != null && name.startsWith("com/myname/legacyloader/");
        }

        private String fixSuperClass(String superName) {
            if (superName == null) return null;
            if (isLegacyLoaderClass(superName)) return superName;
            if (!superName.startsWith("net/minecraft/")) return superName;

            if ("net/minecraft/world/level/block/Block".equals(superName)) {
                return "com/myname/legacyloader/bridge/block/LegacyBlock";
            }
            if (superName.contains("BlockContainer") || superName.equals("com/myname/legacyloader/bridge/block/LegacyContainerBlock")) {
                return "com/myname/legacyloader/bridge/block/LegacyContainerBlock";
            }
            if ("net/minecraft/world/item/Item".equals(superName)) {
                return "com/myname/legacyloader/bridge/item/LegacyItem";
            }
            if (superName.contains("StairBlock") || superName.contains("BlockStairs")) {
                return "com/myname/legacyloader/bridge/block/LegacyBlockStairs";
            }
            if (superName.contains("SlabBlock") || superName.contains("BlockSlab")) {
                return "com/myname/legacyloader/bridge/block/LegacyBlockSlab";
            }
            if (superName.contains("SwordItem") || superName.contains("ItemSword")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemSword";
            }
            if (superName.contains("PickaxeItem") || superName.contains("ItemPickaxe")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemPickaxe";
            }
            if (superName.contains("AxeItem") || superName.contains("ItemAxe")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemAxe";
            }
            if (superName.contains("ShovelItem") || superName.contains("ItemSpade")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemSpade";
            }
            if (superName.contains("HoeItem") || superName.contains("ItemHoe")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemHoe";
            }
            if (superName.contains("ArmorItem") || superName.contains("ItemArmor")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemArmor";
            }
            if (superName.contains("FoodItem") || superName.contains("ItemFood")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemFood";
            }
            if (superName.contains("BucketItem") || superName.contains("ItemBucket")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemBucket";
            }
            if (superName.contains("BlockItem") || superName.contains("ItemBlock")) {
                return "com/myname/legacyloader/bridge/item/LegacyBlockItem";
            }

            return superName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // 笘・ｿｽ蜉: 繧ｹ繝ｩ繝悶け繝ｩ繧ｹ縺ｮ繧ｳ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ縺ｯ繝・ぅ繧ｹ繧ｯ繝ｪ繝励ち繧剃ｿｮ豁｣
            if ("<init>".equals(name) && isSlabClass(currentSuperClass)) {
                descriptor = fixSlabConstructorDescriptor(descriptor);
            }

            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if (isLegacyLoaderClass(currentClassName)) {
                return mv;
            }

            return new BufferingMethodFixer(Opcodes.ASM9, mv, currentClassName, currentSuperClass);
        }

        private boolean isSlabClass(String superName) {
            return superName != null && (
                    superName.contains("SlabBlock") ||
                            superName.contains("BlockSlab") ||
                            superName.equals("com/myname/legacyloader/bridge/block/LegacyBlockSlab")
            );
        }

        private String fixSlabConstructorDescriptor(String descriptor) {
            // Material 繧・LegacyMaterial 縺ｫ螟画鋤
            return descriptor
                    .replace("Lnet/minecraft/block/material/Material;",
                            "Lcom/myname/legacyloader/bridge/block/LegacyMaterial;")
                    .replace("Lnet/minecraft/world/level/material/Material;",
                            "Lcom/myname/legacyloader/bridge/block/LegacyMaterial;");
        }
    }

    // 笘・・笘・繝舌ャ繝輔ぃ繝ｪ繝ｳ繧ｰ逕ｨ繝・・繧ｿ繧ｯ繝ｩ繧ｹ 笘・・笘・
    private static abstract class BufferedInsn {
        abstract void emit(MethodVisitor mv);
    }

    private static class FieldInsnData extends BufferedInsn {
        final int opcode;
        final String owner;
        final String name;
        final String descriptor;

        FieldInsnData(int opcode, String owner, String name, String descriptor) {
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    private static class TypeInsnData extends BufferedInsn {
        final int opcode;
        final String type;

        TypeInsnData(int opcode, String type) {
            this.opcode = opcode;
            this.type = type;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitTypeInsn(opcode, type);
        }
    }

    private static class VarInsnData extends BufferedInsn {
        final int opcode;
        final int varIndex;

        VarInsnData(int opcode, int varIndex) {
            this.opcode = opcode;
            this.varIndex = varIndex;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitVarInsn(opcode, varIndex);
        }
    }

    private static class IntInsnData extends BufferedInsn {
        final int opcode;
        final int operand;

        IntInsnData(int opcode, int operand) {
            this.opcode = opcode;
            this.operand = operand;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitIntInsn(opcode, operand);
        }
    }

    private static class InsnData extends BufferedInsn {
        final int opcode;

        InsnData(int opcode) {
            this.opcode = opcode;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitInsn(opcode);
        }
    }

    private static class LdcInsnData extends BufferedInsn {
        final Object value;

        LdcInsnData(Object value) {
            this.value = value;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitLdcInsn(value);
        }
    }

    private static class MethodInsnData extends BufferedInsn {
        final int opcode;
        final String owner;
        final String name;
        final String descriptor;
        final boolean isInterface;

        MethodInsnData(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.isInterface = isInterface;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    private static class IincInsnData extends BufferedInsn {
        final int varIndex;
        final int increment;

        IincInsnData(int varIndex, int increment) {
            this.varIndex = varIndex;
            this.increment = increment;
        }

        @Override
        void emit(MethodVisitor mv) {
            mv.visitIincInsn(varIndex, increment);
        }
    }

    private static class BufferingMethodFixer extends MethodVisitor {
        private static final String TARGET_ITEM_STACK = "net/minecraft/world/item/ItemStack";
        private static final String OLD_ITEM_STACK = "net/minecraft/item/ItemStack";
        private static final String TARGET_RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
        private static final String OLD_RESOURCE_LOCATION = "net/minecraft/util/ResourceLocation";
        private static final String TARGET_ATTRIBUTE_KEY = "io/netty/util/AttributeKey";
        private static final String TARGET_BLOCK = "net/minecraft/world/level/block/Block";
        private static final String TARGET_ITEM = "net/minecraft/world/item/Item";
        private static final String ITEM_LIKE = "net/minecraft/world/level/ItemLike";

        private static final String LEGACY_BLOCK = "com/myname/legacyloader/bridge/block/LegacyBlock";
        private static final String LEGACY_CONTAINER_BLOCK = "com/myname/legacyloader/bridge/block/LegacyContainerBlock";
        private static final String LEGACY_BLOCK_SLAB = "com/myname/legacyloader/bridge/block/LegacyBlockSlab";
        private static final String LEGACY_ITEM = "com/myname/legacyloader/bridge/item/LegacyItem";
        private static final String LEGACY_MATERIAL = "com/myname/legacyloader/bridge/block/LegacyMaterial";
        private static final String LEGACY_MAP_COLOR = "com/myname/legacyloader/bridge/block/LegacyMapColor";
        private static final String LEGACY_BLOCKS = "com/myname/legacyloader/bridge/init/LegacyBlocks";
        private static final String LEGACY_ITEMS = "com/myname/legacyloader/bridge/init/LegacyItems";
        private static final String LEGACY_SOUND_TYPE = "com/myname/legacyloader/bridge/block/LegacySoundType";
        private static final String LEGACY_GAME_SETTINGS = "com/myname/legacyloader/bridge/client/settings/LegacyGameSettings";
        private static final String LEGACY_ENTITY = "com/myname/legacyloader/bridge/entity/LegacyEntity";
        private static final String BLOCK_HELPER = "com/myname/legacyloader/bridge/block/LegacyBlockHelper";
        private static final String WORLD_HELPER = "com/myname/legacyloader/bridge/world/LegacyWorldHelper";
        private static final String ITEM_HELPER = "com/myname/legacyloader/bridge/item/LegacyItemHelper";
        private static final String ITEM_STACK_HELPER = "com/myname/legacyloader/bridge/item/LegacyItemStackHelper";
        private static final String LEGACY_REGISTRY = "com/myname/legacyloader/bridge/registry/LegacyRegistryNamespaced";
        private static final String NBT_HELPER = "com/myname/legacyloader/bridge/nbt/LegacyNBTHelper";
        private static final String TARGET_COMPOUND_TAG = "net/minecraft/nbt/CompoundTag";
        private static final String TARGET_LIST_TAG = "net/minecraft/nbt/ListTag";
        private static final String TARGET_FRIENDLY_BUF = "net/minecraft/network/FriendlyByteBuf";
        private static final String TARGET_VEC3 = "net/minecraft/world/phys/Vec3";
        private static final String LEGACY_VEC3 = "com/myname/legacyloader/bridge/util/LegacyVec3";

        // 笘・ｿｽ蜉: 1.7.10縺ｮMaterial 繧ｯ繝ｩ繧ｹ
        private static final String OLD_MATERIAL = "net/minecraft/block/material/Material";
        private static final String NEW_MATERIAL = "net/minecraft/world/level/material/Material";
        private static final String OLD_MAP_COLOR = "net/minecraft/block/material/MapColor";
        private static final String NEW_MAP_COLOR = "net/minecraft/world/level/material/MapColor";

        private final String currentClassName;
        private final String currentSuperClass;

        private boolean inItemStackConstruction = false;
        private boolean inResourceLocationConstruction = false;
        private boolean inAttributeKeyConstruction = false;
        private final List<BufferedInsn> bufferedInstructions = new ArrayList<>();

        public BufferingMethodFixer(int api, MethodVisitor methodVisitor, String currentClassName, String currentSuperClass) {
            super(api, methodVisitor);
            this.currentClassName = currentClassName;
            this.currentSuperClass = currentSuperClass;
        }

        private void flushBuffer() {
            if (inItemStackConstruction) {
                super.visitTypeInsn(Opcodes.NEW, TARGET_ITEM_STACK);
                super.visitInsn(Opcodes.DUP);
                for (BufferedInsn insn : bufferedInstructions) {
                    insn.emit(mv);
                }
            } else if (inResourceLocationConstruction) {
                super.visitTypeInsn(Opcodes.NEW, TARGET_RESOURCE_LOCATION);
                super.visitInsn(Opcodes.DUP);
                for (BufferedInsn insn : bufferedInstructions) {
                    insn.emit(mv);
                }
            } else if (inAttributeKeyConstruction) {
                super.visitTypeInsn(Opcodes.NEW, TARGET_ATTRIBUTE_KEY);
                super.visitInsn(Opcodes.DUP);
                for (BufferedInsn insn : bufferedInstructions) {
                    insn.emit(mv);
                }
            }
            bufferedInstructions.clear();
            inItemStackConstruction = false;
            inResourceLocationConstruction = false;
            inAttributeKeyConstruction = false;
        }

        private boolean isBufferingConstruction() {
            return inItemStackConstruction || inResourceLocationConstruction || inAttributeKeyConstruction;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (isBufferingConstruction()) {
                if (opcode == Opcodes.NEW && (isItemStackType(type) || isResourceLocationType(type) || TARGET_ATTRIBUTE_KEY.equals(type))) {
                    flushBuffer();
                } else {
                    bufferedInstructions.add(new TypeInsnData(opcode, fixNewType(type)));
                    return;
                }
            }

            if (opcode == Opcodes.NEW) {
                if (isItemStackType(type)) {
                    inItemStackConstruction = true;
                    return;
                }
                if (isResourceLocationType(type)) {
                    inResourceLocationConstruction = true;
                    return;
                }
                if (TARGET_ATTRIBUTE_KEY.equals(type)) {
                    inAttributeKeyConstruction = true;
                    return;
                }

                flushBuffer();
                type = fixNewType(type);
            } else {
                flushBuffer();
            }

            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.DUP && isBufferingConstruction() && bufferedInstructions.isEmpty()) {
                return;
            }

            if (isBufferingConstruction()) {
                bufferedInstructions.add(new InsnData(opcode));
            } else {
                super.visitInsn(opcode);
            }
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (isBufferingConstruction()) {
                bufferedInstructions.add(new VarInsnData(opcode, varIndex));
            } else {
                super.visitVarInsn(opcode, varIndex);
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (isBufferingConstruction()) {
                bufferedInstructions.add(new IntInsnData(opcode, operand));
            } else {
                super.visitIntInsn(opcode, operand);
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (isBufferingConstruction()) {
                bufferedInstructions.add(new LdcInsnData(value));
            } else {
                super.visitLdcInsn(value);
            }
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            if (isBufferingConstruction()) {
                bufferedInstructions.add(new IincInsnData(varIndex, increment));
            } else {
                super.visitIincInsn(varIndex, increment);
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String fixedOwner = owner;
            String fixedDescriptor = descriptor;

            if (opcode == Opcodes.GETSTATIC) {
                if (isLegacySoundTypeField(name, descriptor)) {
                    fixedOwner = LEGACY_SOUND_TYPE;
                    fixedDescriptor = "L" + LEGACY_SOUND_TYPE + ";";
                }
                else if (isBlockRegistryField(owner, name)) {
                    fixedOwner = LEGACY_REGISTRY;
                    fixedDescriptor = "L" + LEGACY_REGISTRY + ";";
                    name = "BLOCKS";
                }
                else if (isItemRegistryField(owner, name)) {
                    fixedOwner = LEGACY_REGISTRY;
                    fixedDescriptor = "L" + LEGACY_REGISTRY + ";";
                    name = "ITEMS";
                }
                else if (isVanillaBlocksClass(owner) || (name.startsWith("field_150") && isMinecraftPackage(owner))) {
                    emitStaticLookup(LEGACY_BLOCKS, "getByField",
                            "(Ljava/lang/String;)Lnet/minecraft/world/level/block/Block;", name);
                    return;
                }
                else if (isVanillaMapColorClass(owner)) {
                    fixedOwner = LEGACY_MAP_COLOR;
                    fixedDescriptor = "field_76281_a".equals(name)
                            ? "[L" + LEGACY_MAP_COLOR + ";"
                            : "L" + LEGACY_MAP_COLOR + ";";
                }
                else if (isVanillaItemsClass(owner) || (name.startsWith("field_151") && isMinecraftPackage(owner))) {
                    if (isMaterialField(name)) {
                        fixedOwner = LEGACY_MATERIAL;
                        fixedDescriptor = "L" + LEGACY_MATERIAL + ";";
                    } else {
                        emitStaticLookup(LEGACY_ITEMS, "getByField",
                                "(Ljava/lang/String;)Lnet/minecraft/world/item/Item;", name);
                        return;
                    }
                }
                else if ((name.startsWith("field_1497") || name.startsWith("field_185")) && isMinecraftPackage(owner)) {
                    fixedOwner = LEGACY_SOUND_TYPE;
                    fixedDescriptor = "L" + LEGACY_SOUND_TYPE + ";";
                }
                else if (isVanillaMaterialClass(owner)) {
                    fixedOwner = LEGACY_MATERIAL;
                    fixedDescriptor = "L" + LEGACY_MATERIAL + ";";
                }
            }

            if (opcode == Opcodes.GETFIELD && owner.contains("net/minecraft/client/Minecraft")) {
                if ("field_71441_e".equals(name) || "theWorld".equals(name)) {
                    flushBuffer();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                            "getClientLevel",
                            "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/multiplayer/ClientLevel;", false);
                    return;
                }
                if ("field_71439_g".equals(name) || "thePlayer".equals(name)) {
                    flushBuffer();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                            "getClientPlayer",
                            "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/player/LocalPlayer;", false);
                    return;
                }
                //mcDataDir (ゲームディレクトリ)。AsphaltMod は同じフィールドを別 SRG 名
                //field_71412_D で参照するため両方を getMcDataDir へ橋渡しする。
                if ("field_71409_v".equals(name) || "field_71412_D".equals(name) || "mcDataDir".equals(name)) {
                    flushBuffer();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                            "getMcDataDir",
                            "(Lnet/minecraft/client/Minecraft;)Ljava/io/File;", false);
                    return;
                }
                if ("field_71474_y".equals(name) || "gameSettings".equals(name)) {
                    flushBuffer();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                            "getGameSettings",
                            "(Lnet/minecraft/client/Minecraft;)L" + LEGACY_GAME_SETTINGS + ";", false);
                    return;
                }
                if ("field_71452_i".equals(name) || "effectRenderer".equals(name)) {
                    flushBuffer();
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                            "getParticleEngine",
                            "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/particle/ParticleEngine;", false);
                    return;
                }
            }

            if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC)
                    && "net/minecraft/world/entity/Entity".equals(owner)
                    && "field_70152_a".equals(name)) {
                fixedOwner = LEGACY_ENTITY;
                fixedDescriptor = "I";
            }

            if ((opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC)
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && ("field_71444_a".equals(name) || "memoryReserve".equals(name))) {
                fixedOwner = "com/myname/legacyloader/bridge/client/LegacyClientHelper";
                fixedDescriptor = "[B";
                name = "field_71444_a";
            }

            // Vec3 legacy fields: xCoord/yCoord/zCoord -> x/y/z on modern Vec3.
            // Limit this to the mapped vanilla Vec3 bridge. Some mods ship their
            // own Vec3 helper classes with the same field names.
            if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
                    && isLegacyVec3FieldOwner(owner)) {
                if ("xCoord".equals(name)) { name = "x"; fixedDescriptor = "D"; }
                else if ("yCoord".equals(name)) { name = "y"; fixedDescriptor = "D"; }
                else if ("zCoord".equals(name)) { name = "z"; fixedDescriptor = "D"; }
                if ("x".equals(name) || "y".equals(name) || "z".equals(name)) {
                    fixedOwner = TARGET_VEC3;
                }
            }

            if (opcode == Opcodes.GETFIELD && "field_72995_K".equals(name)) {
                flushBuffer();
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/world/LegacyWorldHelper",
                        "isRemote",
                        "(Lnet/minecraft/world/level/Level;)Z", false);
                return;
            }

            if (opcode == Opcodes.GETFIELD && "field_73008_k".equals(name)) {
                flushBuffer();
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/world/LegacyWorldHelper",
                        "getSkylightSubtracted",
                        "(Lnet/minecraft/world/level/Level;)I", false);
                return;
            }

            if (owner.equals(TARGET_ITEM_STACK) && "field_77994_a".equals(name)) {
                flushBuffer();
                if (opcode == Opcodes.GETFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_190916_E",
                            "(L" + TARGET_ITEM_STACK + ";)I", false);
                    return;
                }
                if (opcode == Opcodes.PUTFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "setCount",
                            "(L" + TARGET_ITEM_STACK + ";I)V", false);
                    return;
                }
            }

            // field_73011_w (1.7.10 WorldProvider) 竊・LegacyWorldHelper.getDimensionProvider()
            if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD)
                    && TARGET_BLOCK.equals(owner)
                    && "field_149762_H".equals(name)) {
                fixedOwner = LEGACY_BLOCK;
                fixedDescriptor = "L" + LEGACY_SOUND_TYPE + ";";
            }

            if (opcode == Opcodes.GETFIELD && "field_73011_w".equals(name)) {
                flushBuffer();
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/world/LegacyWorldHelper",
                        "getDimensionProvider",
                        "(Lnet/minecraft/world/level/Level;)Lcom/myname/legacyloader/bridge/world/LegacyWorldProvider;", false);
                return;
            }

            if (fixedDescriptor.contains("Block$SoundType")) {
                fixedDescriptor = fixedDescriptor.replace("Lnet/minecraft/block/Block$SoundType;", "L" + LEGACY_SOUND_TYPE + ";");
            }

            // 笘・ｿｽ蜉: Material 蝙九・繝輔ぅ繝ｼ繝ｫ繝峨ｒ LegacyMaterial 縺ｫ螟画鋤
            fixedDescriptor = fixMaterialDescriptor(fixedDescriptor);
            fixedDescriptor = fixMapColorDescriptor(fixedDescriptor);

            if (isBufferingConstruction()) {
                bufferedInstructions.add(new FieldInsnData(opcode, fixedOwner, name, fixedDescriptor));
            } else {
                super.visitFieldInsn(opcode, fixedOwner, name, fixedDescriptor);
            }
        }

        // 笘・・笘・Material 繝・ぅ繧ｹ繧ｯ繝ｪ繝励ち菫ｮ豁｣ 笘・・笘・
        private String fixMaterialDescriptor(String descriptor) {
            return descriptor
                    .replace("L" + OLD_MATERIAL + ";", "L" + LEGACY_MATERIAL + ";")
                    .replace("L" + NEW_MATERIAL + ";", "L" + LEGACY_MATERIAL + ";");
        }

        private String fixMapColorDescriptor(String descriptor) {
            return descriptor
                    .replace("L" + OLD_MAP_COLOR + ";", "L" + LEGACY_MAP_COLOR + ";")
                    .replace("L" + NEW_MAP_COLOR + ";", "L" + LEGACY_MAP_COLOR + ";");
        }

        private boolean isMinecraftPackage(String owner) {
            return owner != null && (
                    owner.startsWith("net/minecraft/") ||
                            owner.startsWith("com/myname/legacyloader/bridge/")
            );
        }

        private boolean isVanillaBlocksClass(String owner) {
            return "net/minecraft/init/Blocks".equals(owner) ||
                    "net/minecraft/world/level/block/Blocks".equals(owner) ||
                    "com/myname/legacyloader/bridge/init/LegacyBlocks".equals(owner);
        }

        private boolean isVanillaItemsClass(String owner) {
            return "net/minecraft/init/Items".equals(owner) ||
                    "net/minecraft/world/item/Items".equals(owner) ||
                    "com/myname/legacyloader/bridge/init/LegacyItems".equals(owner);
        }

        private boolean isVanillaMaterialClass(String owner) {
            return "net/minecraft/block/material/Material".equals(owner) ||
                    "net/minecraft/world/level/material/Material".equals(owner) ||
                    "com/myname/legacyloader/bridge/block/LegacyMaterial".equals(owner);
        }

        private boolean isVanillaMapColorClass(String owner) {
            return OLD_MAP_COLOR.equals(owner) ||
                    NEW_MAP_COLOR.equals(owner) ||
                    LEGACY_MAP_COLOR.equals(owner);
        }

        private boolean isLegacySoundTypeField(String name, String descriptor) {
            return name != null && name.startsWith("field_1497") && descriptor != null &&
                    (descriptor.contains("Block$SoundType") || descriptor.contains("SoundType") ||
                            descriptor.contains("LegacySoundType"));
        }

        private boolean isMaterialField(String name) {
            return name.equals("field_151576_e") || name.equals("field_151579_a") ||
                    name.equals("field_151577_b") || name.equals("field_151578_c") ||
                    name.equals("field_151575_d") || name.equals("field_151573_f") ||
                    name.equals("field_151574_g") || name.equals("field_151586_h") ||
                    name.equals("field_151587_i") || name.equals("field_151584_j") ||
                    name.equals("field_151585_k") || name.equals("field_151582_l") ||
                    name.equals("field_151583_m") || name.equals("field_151580_n") ||
                    name.equals("field_151581_o") || name.equals("field_151595_p") ||
                    name.equals("field_151594_q") || name.equals("field_151568_F") ||
                    name.equals("field_151592_s") || name.equals("field_151590_u") ||
                    name.equals("field_151591_v") || name.equals("field_151566_D") ||
                    name.equals("field_151598_x") || name.equals("field_151596_z") ||
                    name.equals("field_151588_w") || name.equals("field_151589_v") ||
                    name.equals("field_151597_y") || name.equals("field_151593_t") ||
                    name.equals("field_151565_H") || name.equals("field_151567_E") ||
                    name.equals("field_151570_A") || name.equals("field_151571_B") ||
                    name.equals("field_151572_C");
        }

        private String fixNewType(String type) {
            if (type.equals(TARGET_BLOCK)) return LEGACY_BLOCK;
            if (type.equals(TARGET_ITEM)) return LEGACY_ITEM;
            if (type.contains("BlockContainer") || type.equals(LEGACY_CONTAINER_BLOCK)) {
                return LEGACY_CONTAINER_BLOCK;
            }
            if (type.equals("net/minecraft/block/BlockStairs") || type.equals("net/minecraft/world/level/block/StairBlock")) {
                return "com/myname/legacyloader/bridge/block/LegacyBlockStairs";
            }
            if (type.equals("net/minecraft/block/BlockSlab") || type.equals("net/minecraft/world/level/block/SlabBlock") || type.equals(LEGACY_BLOCK_SLAB)) {
                return LEGACY_BLOCK_SLAB;
            }
            return type;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (isBufferingConstruction() && !isBufferedTargetConstructor(opcode, owner, name)) {
                bufferedInstructions.add(new MethodInsnData(opcode, owner, name, descriptor, isInterface));
                return;
            }

            if (opcode == Opcodes.INVOKESPECIAL && isResourceLocationType(owner) && "<init>".equals(name)) {
                if (inResourceLocationConstruction) {
                    for (BufferedInsn insn : bufferedInstructions) {
                        insn.emit(mv);
                    }
                    bufferedInstructions.clear();
                    inResourceLocationConstruction = false;

                    if ("(Ljava/lang/String;)V".equals(descriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "com/myname/legacyloader/bridge/util/LegacyResourceLocationHelper", "create",
                                "(Ljava/lang/String;)L" + TARGET_RESOURCE_LOCATION + ";", false);
                        return;
                    }
                    if ("(Ljava/lang/String;Ljava/lang/String;)V".equals(descriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "com/myname/legacyloader/bridge/util/LegacyResourceLocationHelper", "create",
                                "(Ljava/lang/String;Ljava/lang/String;)L" + TARGET_RESOURCE_LOCATION + ";", false);
                        return;
                    }
                }
            }

            if (opcode == Opcodes.INVOKESPECIAL && TARGET_ATTRIBUTE_KEY.equals(owner) && "<init>".equals(name)) {
                if (inAttributeKeyConstruction) {
                    for (BufferedInsn insn : bufferedInstructions) {
                        insn.emit(mv);
                    }
                    bufferedInstructions.clear();
                    inAttributeKeyConstruction = false;

                    if ("(Ljava/lang/String;)V".equals(descriptor)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET_ATTRIBUTE_KEY, "valueOf",
                                "(Ljava/lang/String;)L" + TARGET_ATTRIBUTE_KEY + ";", false);
                        return;
                    }
                }
            }

            // ItemStack 繧ｳ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ繧帝撕逧・ヵ繧｡繧ｯ繝医Μ縺ｫ螟画鋤
            if (opcode == Opcodes.INVOKESPECIAL && isItemStackType(owner) && "<init>".equals(name)) {
                if (inItemStackConstruction) {
                    for (BufferedInsn insn : bufferedInstructions) {
                        insn.emit(mv);
                    }
                    bufferedInstructions.clear();
                    inItemStackConstruction = false;

                    String helperDesc = convertToFactoryDescriptor(descriptor);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "create", helperDesc, false);
                    return;
                }
            }

            flushBuffer();

            if (opcode == Opcodes.INVOKESTATIC
                    && "net/minecraft/world/phys/AABB".equals(owner)
                    && "func_72330_a".equals(name)
                    && "(DDDDDD)Lnet/minecraft/world/phys/AABB;".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/util/LegacyAABBHelper",
                        "func_72330_a",
                        descriptor,
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && isVanillaBlockClass(owner)
                    && "func_149634_a".equals(name)
                    && descriptor.equals("(Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/level/block/Block;")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        BLOCK_HELPER,
                        "func_149634_a",
                        descriptor,
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && isVanillaBlockClass(owner)
                    && "func_149682_b".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        BLOCK_HELPER,
                        "func_149682_b",
                        "(Lnet/minecraft/world/level/block/Block;)I",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && isVanillaBlockClass(owner)
                    && "func_149683_c".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        BLOCK_HELPER,
                        "func_149683_c",
                        "(I)Lnet/minecraft/world/level/block/Block;",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && isVanillaItemClass(owner)
                    && "func_150891_b".equals(name)
                    && descriptor.equals("(Lnet/minecraft/world/item/Item;)I")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        ITEM_HELPER,
                        "func_150891_b",
                        descriptor,
                        false);
                return;
            }

            // CompoundTag SRG method redirections
            if (opcode == Opcodes.INVOKEVIRTUAL && TARGET_COMPOUND_TAG.equals(owner) && isNBTCompoundSRGMethod(name)) {
                String newDesc = "(L" + TARGET_COMPOUND_TAG + ";" + descriptor.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, NBT_HELPER, name, newDesc, false);
                return;
            }

            // ListTag SRG method redirections
            if (opcode == Opcodes.INVOKEVIRTUAL && TARGET_LIST_TAG.equals(owner) && isNBTListSRGMethod(name)) {
                String newDesc = "(L" + TARGET_LIST_TAG + ";" + descriptor.substring(1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, NBT_HELPER, name, newDesc, false);
                return;
            }

            // FriendlyByteBuf: guard empty ItemStack encoding
            if (opcode == Opcodes.INVOKEVIRTUAL && TARGET_FRIENDLY_BUF.equals(owner)) {
                if ("writeItem".equals(name) || "func_179249_a".equals(name) || "writeItemStackToBuffer".equals(name)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/network/LegacyPacketBufferHelper",
                            "writeItem",
                            "(L" + TARGET_FRIENDLY_BUF + ";Lnet/minecraft/world/item/ItemStack;)L" + TARGET_FRIENDLY_BUF + ";",
                            false);
                    return;
                }
                if ("readItem".equals(name) || "func_179258_d".equals(name) || "readItemStackFromBuffer".equals(name)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            "com/myname/legacyloader/bridge/network/LegacyPacketBufferHelper",
                            "readItem",
                            "(L" + TARGET_FRIENDLY_BUF + ";)Lnet/minecraft/world/item/ItemStack;",
                            false);
                    return;
                }
            }

            if ((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL)
                    && "org/apache/logging/log4j/Logger".equals(owner)
                    && "log".equals(name)
                    && "(Lcom/myname/legacyloader/bridge/fml/LegacyLogLevel;Ljava/lang/String;)V".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/fml/LegacyLogHelper",
                        "log",
                        "(Lorg/apache/logging/log4j/Logger;Lcom/myname/legacyloader/bridge/fml/LegacyLogLevel;Ljava/lang/String;)V",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && ("func_71410_x".equals(name) || "getMinecraft".equals(name))
                    && "()Lnet/minecraft/client/Minecraft;".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "getMinecraft",
                        descriptor,
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESTATIC
                    && "com/myname/legacyloader/bridge/client/registry/LegacyRenderingRegistry".equals(owner)
                    && "registerEntityRenderingHandler".equals(name)
                    && descriptor.equals("(Ljava/lang/Class;Lcom/myname/legacyloader/bridge/client/renderer/entity/LegacyRender;)V")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name,
                        "(Ljava/lang/Class;Ljava/lang/Object;)V", false);
                return;
            }

            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && "func_147118_V".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "getSoundHandler",
                        "(Lnet/minecraft/client/Minecraft;)Lcom/myname/legacyloader/bridge/client/audio/LegacySoundHandler;",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && "func_110442_L".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "getResourceManager",
                        "(Lnet/minecraft/client/Minecraft;)Lcom/myname/legacyloader/bridge/client/resources/LegacyIResourceManager;",
                        false);
                return;
            }

            // Minecraft.func_135016_M() = getLanguageManager()。現行 MC には無いので橋渡し。
            // (AsphaltMod の isJapanese() 等が言語判定に使用。未対応だと NoSuchMethodError で
            //  ClientProxy.init が中断し、ISBRH ブロック描画の登録まで失われていた。)
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && "func_135016_M".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "getLanguageManager",
                        "(Lnet/minecraft/client/Minecraft;)Lcom/myname/legacyloader/bridge/client/resources/LegacyLanguageManager;",
                        false);
                return;
            }

            // Minecraft.func_147108_a(GuiScreen) = displayGuiScreen。引数を Object 化して橋渡し。
            // スタブ GUI (GuiConfirmOpenLink 等) をそのまま渡すとモダン Screen と非互換で
            // VerifyError になり、そのメソッドを含むブロッククラス (AsphaltMod の電光掲示板等) が
            // ロード不能 → フィールド null → init NPE で以降の登録が失われていた。
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/Minecraft".equals(owner)
                    && "func_147108_a".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "displayGuiScreen",
                        "(Lnet/minecraft/client/Minecraft;Ljava/lang/Object;)V",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/multiplayer/ClientLevel".equals(owner)
                    && "func_72869_a".equals(name)
                    && "(Ljava/lang/String;DDDDDD)V".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/myname/legacyloader/bridge/client/LegacyClientHelper",
                        "spawnParticle",
                        "(Lnet/minecraft/client/multiplayer/ClientLevel;Ljava/lang/String;DDDDDD)V",
                        false);
                return;
            }

            // 笘・・笘・繧ｹ繝ｩ繝悶・super()繧ｳ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ蜻ｼ縺ｳ蜃ｺ縺励ｒ菫ｮ豁｣ 笘・・笘・
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraft/client/particle/ParticleEngine".equals(owner)
                    && ("func_78873_a".equals(name) || "addEffect".equals(name))
                    && "(Lcom/myname/legacyloader/bridge/client/particle/LegacyEntityFX;)V".equals(descriptor)) {
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                        owner,
                        "add",
                        "(Lnet/minecraft/client/particle/Particle;)V",
                        false);
                return;
            }

            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
                if (isSlabSuperClass(owner)) {
                    // 繧ｹ繝ｩ繝悶・繧ｳ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ蜻ｼ縺ｳ蜃ｺ縺・
                    String fixedDescriptor = fixSlabConstructorDescriptor(descriptor);
                    super.visitMethodInsn(opcode, LEGACY_BLOCK_SLAB, name, fixedDescriptor, isInterface);
                    return;
                }

                // 莉悶・繧ｳ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ
                owner = fixConstructorOwner(owner);
                descriptor = fixMaterialDescriptor(descriptor);
            }

            if (opcode == Opcodes.INVOKESPECIAL && TARGET_ITEM.equals(owner) && !"<init>".equals(name)) {
                owner = LEGACY_ITEM;
            }

            // getMaterial / func_149688_o 縺ｮ謌ｻ繧雁､繧剃ｿｮ豁｣
            if ((name.equals("getMaterial") || name.equals("func_149688_o")) &&
                    descriptor.endsWith(")L" + OLD_MATERIAL + ";")) {
                descriptor = descriptor.replace(
                        ")L" + OLD_MATERIAL + ";",
                        ")L" + LEGACY_MATERIAL + ";"
                );
            }
            if ((name.equals("getMaterial") || name.equals("func_149688_o")) &&
                    descriptor.endsWith(")L" + NEW_MATERIAL + ";")) {
                descriptor = descriptor.replace(
                        ")L" + NEW_MATERIAL + ";",
                        ")L" + LEGACY_MATERIAL + ";"
                );
            }

            // ItemStack 縺ｮ 1.7.10 SRG繝｡繧ｽ繝・ラ繧・LegacyItemStackHelper 縺ｮstatic繝｡繧ｽ繝・ラ縺ｫ繝ｪ繝繧､繝ｬ繧ｯ繝・
            if (opcode == Opcodes.INVOKEVIRTUAL && TARGET_ITEM_STACK.equals(owner)) {
                switch (name) {
                    case "func_77973_b": // getItem()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77973_b",
                                "(L" + TARGET_ITEM_STACK + ";)Lnet/minecraft/world/item/Item;", false);
                        return;
                    case "func_190916_E": // getCount()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_190916_E",
                                "(L" + TARGET_ITEM_STACK + ";)I", false);
                        return;
                    case "func_77960_j": // getDamageValue() / getItemDamage()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77960_j",
                                "(L" + TARGET_ITEM_STACK + ";)I", false);
                        return;
                    case "func_77964_b": // setDamageValue(int) / setItemDamage(int)
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77964_b",
                                "(L" + TARGET_ITEM_STACK + ";I)V", false);
                        return;
                    case "func_190926_b": // isEmpty()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_190926_b",
                                "(L" + TARGET_ITEM_STACK + ";)Z", false);
                        return;
                    case "func_77982_d": // setTagCompound(CompoundTag)
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77982_d",
                                "(L" + TARGET_ITEM_STACK + ";L" + TARGET_COMPOUND_TAG + ";)V", false);
                        return;
                    case "func_77978_p": // getTagCompound()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77978_p",
                                "(L" + TARGET_ITEM_STACK + ";)L" + TARGET_COMPOUND_TAG + ";", false);
                        return;
                    case "func_77969_a": // 1.7.10 isItemEqual(ItemStack)
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77969_a",
                                "(L" + TARGET_ITEM_STACK + ";L" + TARGET_ITEM_STACK + ";)Z", false);
                        return;
                    case "func_77942_o": // 1.7.10 hasTagCompound()
                    case "hasTagCompound":
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_STACK_HELPER, "func_77942_o",
                                "(L" + TARGET_ITEM_STACK + ";)Z", false);
                        return;
                }
            }

            // 笘・・笘・莉悶・繝悶Ο繝・け/繧｢繧､繝・Β縺ｸ縺ｮ繝｡繧ｽ繝・ラ蜻ｼ縺ｳ蜃ｺ縺励Μ繝繧､繝ｬ繧ｯ繝・笘・・笘・
            if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
                if (isWorldAccessClass(owner)) {
                    switch (name) {
                        case "func_147439_a":
                        case "getBlock":
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getBlock",
                                    "(Lnet/minecraft/world/level/BlockGetter;III)L" + TARGET_BLOCK + ";", false);
                            return;
                        case "func_72805_g":
                        case "getBlockMetadata":
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getBlockMetadata",
                                    "(Lnet/minecraft/world/level/BlockGetter;III)I", false);
                            return;
                        case "func_147437_c":
                        case "isAirBlock":
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "isAirBlock",
                                    "(Lnet/minecraft/world/level/BlockGetter;III)Z", false);
                            return;
                        case "func_147438_o":
                        case "getTileEntity":
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getLegacyTileEntity",
                                    "(Lnet/minecraft/world/level/BlockGetter;III)Lcom/myname/legacyloader/bridge/tileentity/LegacyTileEntity;", false);
                            return;
                        case "func_72802_i":
                        case "getLightBrightnessForSkyBlocks":
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getLightBrightnessForSkyBlocks",
                                    "(Lnet/minecraft/world/level/BlockGetter;IIII)I", false);
                            return;
                    }
                }

                if (isWorldClass(owner)) {
                    if (name.equals("func_72921_c") || name.equals("setBlockMetadataWithNotify")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "setBlockMetadata",
                                "(Lnet/minecraft/world/level/Level;IIIII)Z", false);
                        return;
                    }
                    if (name.equals("func_147465_d") || name.equals("setBlock")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "setBlock",
                                "(Lnet/minecraft/world/level/Level;IIIL" + TARGET_BLOCK + ";II)Z", false);
                        return;
                    }
                    if (name.equals("func_147472_a") || name.equals("canPlaceEntityOnSide")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "canPlaceEntityOnSide",
                                "(Lnet/minecraft/world/level/Level;L" + TARGET_BLOCK + ";IIIZILnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Z", false);
                        return;
                    }
                    if (name.equals("func_147468_f") || name.equals("setBlockToAir")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "setBlockToAir",
                                "(Lnet/minecraft/world/level/Level;III)Z", false);
                        return;
                    }
                    if (name.equals("func_147471_g") || name.equals("markBlockForUpdate")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "markBlockForUpdate",
                                "(Lnet/minecraft/world/level/Level;III)V", false);
                        return;
                    }
                    if (name.equals("func_147444_c") || name.equals("notifyBlockChange")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "notifyBlockChange",
                                "(Lnet/minecraft/world/level/Level;IIIL" + TARGET_BLOCK + ";)V", false);
                        return;
                    }
                    if (name.equals("func_147464_a") || name.equals("scheduleBlockUpdate")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "scheduleBlockUpdate",
                                "(Lnet/minecraft/world/level/Level;IIIL" + TARGET_BLOCK + ";I)V", false);
                        return;
                    }
                    if (name.equals("func_72929_e") || name.equals("getCelestialAngle")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getCelestialAngle",
                                "(Lnet/minecraft/world/level/Level;F)F", false);
                        return;
                    }
                    if (name.equals("func_72972_b") || name.equals("getSavedLightValue")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getSavedLightValue",
                                "(Lnet/minecraft/world/level/BlockGetter;Ljava/lang/Object;III)I", false);
                        return;
                    }
                    if (name.equals("func_72957_l") || name.equals("getBlockLightValue")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "getBlockLightValue",
                                "(Lnet/minecraft/world/level/BlockGetter;III)I", false);
                        return;
                    }
                    if (name.equals("func_72937_j") || name.equals("canBlockSeeTheSky")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "canBlockSeeTheSky",
                                "(Lnet/minecraft/world/level/BlockGetter;III)Z", false);
                        return;
                    }
                    if (name.equals("func_72908_a") || name.equals("playSoundEffect")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "playSoundEffect",
                                "(Lnet/minecraft/world/level/Level;DDDLjava/lang/String;FF)V", false);
                        return;
                    }
                    if (name.equals("func_147455_a") || name.equals("setTileEntity")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "setTileEntity",
                                "(Lnet/minecraft/world/level/Level;IIILjava/lang/Object;)V", false);
                        return;
                    }
                    if (name.equals("func_147475_p") || name.equals("removeTileEntity")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "removeTileEntity",
                                "(Lnet/minecraft/world/level/Level;III)V", false);
                        return;
                    }
                    if (name.equals("func_72838_d") || name.equals("spawnEntityInWorld") || name.equals("addEntity")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "spawnEntityInWorld",
                                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;)Z", false);
                        return;
                    }
                }

                if (isVanillaBlockClass(owner) && isBlockMethod(name)) {
                    String newDesc = "(L" + TARGET_BLOCK + ";" + descriptor.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BLOCK_HELPER, name, newDesc, false);
                    return;
                }

                if (isLegacyBlockSubclassCall(owner, name)) {
                    String newDesc = "(L" + TARGET_BLOCK + ";" + descriptor.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BLOCK_HELPER, name, newDesc, false);
                    return;
                }

                if (isVanillaBlockClass(owner) && (name.equals("getMaterial") || name.equals("func_149688_o"))) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, BLOCK_HELPER, "getMaterial",
                            "(L" + TARGET_BLOCK + ";)L" + LEGACY_MATERIAL + ";", false);
                    return;
                }

                if (isVanillaItemClass(owner) && isItemMethod(name)) {
                    String newDesc = "(L" + TARGET_ITEM + ";" + descriptor.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_HELPER, name, newDesc, false);
                    return;
                }

                if (isLegacyItemSubclassCall(owner, name)) {
                    String newDesc = "(L" + TARGET_ITEM + ";" + descriptor.substring(1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, ITEM_HELPER, name, newDesc, false);
                    return;
                }
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private boolean isBufferedTargetConstructor(int opcode, String owner, String name) {
            return opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name) &&
                    ((inItemStackConstruction && isItemStackType(owner)) ||
                            (inResourceLocationConstruction && isResourceLocationType(owner)) ||
                            (inAttributeKeyConstruction && TARGET_ATTRIBUTE_KEY.equals(owner)));
        }

        private boolean isItemStackType(String type) {
            return TARGET_ITEM_STACK.equals(type) || OLD_ITEM_STACK.equals(type);
        }

        private boolean isResourceLocationType(String type) {
            return TARGET_RESOURCE_LOCATION.equals(type) || OLD_RESOURCE_LOCATION.equals(type);
        }

        // 笘・・笘・繧ｹ繝ｩ繝冶ｦｪ繧ｯ繝ｩ繧ｹ蛻､螳・笘・・笘・
        private boolean isLegacyVec3FieldOwner(String owner) {
            return LEGACY_VEC3.equals(owner) || TARGET_VEC3.equals(owner);
        }

        private boolean isSlabSuperClass(String owner) {
            return owner != null && (
                    owner.equals("net/minecraft/block/BlockSlab") ||
                            owner.equals("net/minecraft/world/level/block/SlabBlock") ||
                            owner.equals(LEGACY_BLOCK_SLAB)
            );
        }

        private boolean isBlockRegistryField(String owner, String name) {
            return "field_149771_c".equals(name) && ("net/minecraft/block/Block".equals(owner)
                    || "net/minecraft/world/level/block/Block".equals(owner));
        }

        private boolean isItemRegistryField(String owner, String name) {
            return "field_150901_e".equals(name) && ("net/minecraft/item/Item".equals(owner)
                    || "net/minecraft/world/item/Item".equals(owner));
        }

        // 笘・・笘・繧ｹ繝ｩ繝悶さ繝ｳ繧ｹ繝医Λ繧ｯ繧ｿ繝・ぅ繧ｹ繧ｯ繝ｪ繝励ち菫ｮ豁｣ 笘・・笘・
        private String fixSlabConstructorDescriptor(String descriptor) {
            // (ZL.../Material;)V -> (ZL.../LegacyMaterial;)V
            String fixed = descriptor
                    .replace("L" + OLD_MATERIAL + ";", "L" + LEGACY_MATERIAL + ";")
                    .replace("L" + NEW_MATERIAL + ";", "L" + LEGACY_MATERIAL + ";");

            // (ZL.../Block;I)V -> (ZL.../Block;I)V (縺昴・縺ｾ縺ｾ - LegacyBlockSlab縺悟ｯｾ蠢・
            // Block蝙九ｂ蟇ｾ蠢懊☆繧句ｿ・ｦ√′縺ゅｋ
            fixed = fixed
                    .replace("Lnet/minecraft/block/Block;", "L" + TARGET_BLOCK + ";");

            return fixed;
        }

        private boolean isVanillaBlockClass(String owner) {
            if (owner == null) return false;
            if (owner.startsWith("com/myname/legacyloader/")) return false;
            if (!owner.startsWith("net/minecraft/")) return false;
            return owner.equals(TARGET_BLOCK) || owner.contains("/block/");
        }

        private boolean isWorldAccessClass(String owner) {
            return owner != null && (owner.equals("net/minecraft/world/level/BlockGetter")
                    || owner.equals("net/minecraft/world/IBlockAccess")
                    || owner.equals("net/minecraft/world/level/Level")
                    || owner.equals("net/minecraft/world/World"));
        }

        private boolean isWorldClass(String owner) {
            return owner != null && (owner.equals("net/minecraft/world/level/Level")
                    || owner.equals("net/minecraft/world/World"));
        }

        private boolean isVanillaItemClass(String owner) {
            if (owner == null) return false;
            if (owner.startsWith("com/myname/legacyloader/")) return false;
            if (!owner.startsWith("net/minecraft/")) return false;
            return owner.equals(TARGET_ITEM) || (owner.contains("/item/") && !owner.contains("ItemStack"));
        }

        private boolean isLegacyBlockSubclassCall(String owner, String name) {
            if (owner == null || owner.startsWith("net/minecraft/") || owner.startsWith("com/myname/legacyloader/")) {
                return false;
            }
            return isBlockMethod(name) && (owner.contains("/Block") || owner.endsWith("Block"));
        }

        private boolean isLegacyItemSubclassCall(String owner, String name) {
            if (owner == null || owner.startsWith("net/minecraft/") || owner.startsWith("com/myname/legacyloader/")) {
                return false;
            }
            return isItemMethod(name) && (owner.contains("/Item") || owner.endsWith("Item") || owner.contains("Wrench"));
        }

        private String convertToFactoryDescriptor(String desc) {
            String result = desc;
            result = result.replace(")V", ")L" + TARGET_ITEM_STACK + ";");
            result = result.replace("(L" + TARGET_BLOCK + ";", "(Ljava/lang/Object;");
            result = result.replace("(L" + TARGET_ITEM + ";", "(Ljava/lang/Object;");
            result = result.replace("(L" + LEGACY_BLOCK + ";", "(Ljava/lang/Object;");
            result = result.replace("(L" + LEGACY_ITEM + ";", "(Ljava/lang/Object;");
            result = result.replace("(L" + ITEM_LIKE + ";", "(Ljava/lang/Object;");
            return result;
        }

        private String fixConstructorOwner(String owner) {
            if (owner.startsWith("com/myname/legacyloader/")) return owner;

            if (owner.equals(TARGET_BLOCK)) return LEGACY_BLOCK;
            if (owner.contains("BlockContainer") || owner.equals(LEGACY_CONTAINER_BLOCK)) return LEGACY_CONTAINER_BLOCK;
            if (owner.equals(TARGET_ITEM)) return LEGACY_ITEM;
            if (owner.equals("net/minecraft/block/BlockStairs") || owner.equals("net/minecraft/world/level/block/StairBlock")) {
                return "com/myname/legacyloader/bridge/block/LegacyBlockStairs";
            }
            if (owner.equals("net/minecraft/block/BlockSlab") || owner.equals("net/minecraft/world/level/block/SlabBlock") || owner.equals(LEGACY_BLOCK_SLAB)) {
                return LEGACY_BLOCK_SLAB;
            }
            if (owner.equals("net/minecraft/item/ItemSword") || owner.equals("net/minecraft/world/item/SwordItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemSword";
            }
            if (owner.equals("net/minecraft/item/ItemPickaxe") || owner.equals("net/minecraft/world/item/PickaxeItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemPickaxe";
            }
            if (owner.equals("net/minecraft/item/ItemAxe") || owner.equals("net/minecraft/world/item/AxeItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemAxe";
            }
            if (owner.equals("net/minecraft/item/ItemSpade") || owner.equals("net/minecraft/world/item/ShovelItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemSpade";
            }
            if (owner.equals("net/minecraft/item/ItemHoe") || owner.equals("net/minecraft/world/item/HoeItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemHoe";
            }
            if (owner.equals("net/minecraft/item/ItemArmor") || owner.equals("net/minecraft/world/item/ArmorItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemArmor";
            }
            if (owner.equals("net/minecraft/item/ItemFood") || owner.equals("net/minecraft/world/item/FoodItem")) {
                return "com/myname/legacyloader/bridge/item/LegacyItemFood";
            }
            return owner;
        }

        private void emitStaticLookup(String owner, String method, String desc, String fieldName) {
            if (isBufferingConstruction()) {
                bufferedInstructions.add(new LdcInsnData(fieldName));
                bufferedInstructions.add(new MethodInsnData(Opcodes.INVOKESTATIC, owner, method, desc, false));
            } else {
                flushBuffer();
                super.visitLdcInsn(fieldName);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, method, desc, false);
            }
        }

        private boolean isBlockMethod(String name) {
            return name.equals("setBlockName") || name.equals("setBlockTextureName") ||
                    name.equals("setHardness") || name.equals("setResistance") ||
                    name.equals("setLightLevel") || name.equals("setStepSound") ||
                    name.equals("setLightOpacity") || name.equals("setCreativeTab") ||
                    name.equals("getTextureName") ||
                    name.equals("func_149663_c") || name.equals("func_149658_d") ||
                    name.equals("func_149647_a") || name.equals("func_149711_c") ||
                    name.equals("func_149752_f") || name.equals("func_149752_b") ||
                    name.equals("func_149715_a") || name.equals("func_149672_a") ||
                    name.equals("func_149713_g") || name.equals("func_149641_N") ||
                    name.equals("func_149739_a") || name.equals("func_149722_s") ||
                    name.equals("func_149649_H") || name.equals("func_149675_a") ||
                    name.equals("func_149676_a") || name.equals("setBlockBounds") ||
                    name.equals("func_149691_a") || name.equals("getIcon") ||
                    name.equals("func_149733_h") || name.equals("getBlockTextureFromSide") ||
                    name.equals("func_149673_e") || name.equals("func_149720_d") ||
                    name.equals("getLightValue") || name.equals("func_149646_a") ||
                    name.equals("func_149662_c") || name.equals("func_149686_d") ||
                    name.equals("func_149645_b") || name.equals("func_149655_b") ||
                    name.equals("func_149668_a") || name.equals("func_149633_g") ||
                    name.equals("func_149719_a") || name.equals("func_149743_a") ||
                    name.equals("func_149727_a") || name.equals("func_149689_a") ||
                    name.equals("func_149695_a") || name.equals("func_149674_a") ||
                    name.equals("func_149745_a") || name.equals("func_149650_a") ||
                    name.equals("func_149692_a") || name.equals("func_149744_f") ||
                    name.equals("func_149709_b") || name.equals("func_149721_r") ||
                    name.equals("func_149637_q") || name.equals("func_149677_c") ||
                    name.equals("onBlockAdded") || name.equals("breakBlock") ||
                    name.equals("func_149726_b") || name.equals("func_149749_a") ||
                    name.equals("func_149700_E") ||
                    name.equals("hasTileEntity");
        }

        private boolean isNBTCompoundSRGMethod(String name) {
            switch (name) {
                // writes
                case "func_74774_a": case "func_74777_a": case "func_74783_a": case "func_74780_a":
                case "func_74779_a": case "func_74757_a": case "func_74781_a": case "func_74778_a":
                case "func_74768_a": case "func_74773_a": case "func_74776_a": case "func_74782_a":
                case "func_74775_a":
                // reads
                case "func_74762_e": case "func_74766_f": case "func_74771_a": case "func_74769_d":
                case "func_74760_g": case "func_74763_f": case "func_74737_b": case "func_74767_n":
                case "func_74770_e": case "func_74759_k": case "func_74781_b": case "func_74775_b":
                case "func_150295_c": case "func_74764_b": case "func_150297_b":
                    return true;
                default:
                    return false;
            }
        }

        private boolean isNBTListSRGMethod(String name) {
            switch (name) {
                case "func_74742_a": case "func_150305_b": case "func_150307_f":
                case "func_74745_c": case "func_150306_c":
                    return true;
                default:
                    return false;
            }
        }

        private boolean isItemMethod(String name) {
            return name.equals("setUnlocalizedName") || name.equals("setTextureName") ||
                    name.equals("setCreativeTab") || name.equals("setMaxStackSize") ||
                    name.equals("setMaxDamage") || name.equals("setFull3D") ||
                    name.equals("setNoRepair") || name.equals("setContainerItem") ||
                    name.equals("setHarvestLevel") ||
                    name.equals("func_77655_b") || name.equals("func_111206_d") ||
                    name.equals("func_77637_a") || name.equals("func_77625_d") ||
                    name.equals("func_77656_e") || name.equals("func_77668_e") ||
                    name.equals("func_77612_l") || name.equals("func_77642_a") ||
                    name.equals("func_77658_a") || name.equals("func_77664_n");
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            flushBuffer();
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLabel(Label label) {
            flushBuffer();
            super.visitLabel(label);
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            flushBuffer();
            super.visitFrame(type, numLocal, local, numStack, stack);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            flushBuffer();
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            flushBuffer();
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            flushBuffer();
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            flushBuffer();
            super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public void visitEnd() {
            flushBuffer();
            super.visitEnd();
        }
    }
}
