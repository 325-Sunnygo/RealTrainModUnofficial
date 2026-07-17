package com.myname.legacyloader.core;

import com.myname.legacyloader.bridge.Mappings;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.net.URLClassLoader;

public class LegacyClassLoader extends URLClassLoader {

    public LegacyClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    // 笘・ｿｽ蜉: 繧ｯ繝ｩ繧ｹ繝ｭ繝ｼ繝芽ｦ∵ｱゅｒ繝輔ャ繧ｯ縺励※繝ｪ繝阪・繝繧定｡後≧
    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 縺吶〒縺ｫ繝ｭ繝ｼ繝峨＆繧後※縺・ｋ縺狗｢ｺ隱・
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                // 繝槭ャ繝斐Φ繧ｰ繝・・繝悶Ν繧堤｢ｺ隱阪＠縲∵立蜷阪↑繧画眠蜷阪↓螟画鋤
                // (萓・ net.minecraft.block.Block -> net.minecraft.world.level.block.Block)
                String mappedName = Mappings.getMap().get(name.replace('.', '/'));
                if (mappedName != null) {
                    // '/' 繧・'.' 縺ｫ謌ｻ縺励※繝ｪ繝阪・繝蠕後・繧ｯ繝ｩ繧ｹ蜷阪〒繝ｭ繝ｼ繝峨ｒ隧ｦ縺ｿ繧・
                    try {
                        c = super.loadClass(mappedName.replace('/', '.'), false);
                    } catch (ClassNotFoundException e) {
                        // 螟ｱ謨励＠縺溘ｉ蜈・・繝輔Ο繝ｼ縺ｸ
                    }
                }

                if (c == null) {
                    try {
                        c = super.loadClass(name, false);
                    } catch (ClassNotFoundException e) {
                        // Generate stub for 1.7.10 Minecraft/Forge classes removed in 1.21.1
                        if (isLegacyMinecraftClass(name)) {
                            c = generateStubClass(name);
                        } else if (isStubbableMissingClass(name)) {
                            // このクラスローダは 1.7.10 mod 専用。ここに来る未解決クラスは、
                            // その mod が参照する別 mod の任意連携 API 等 (例: Bamboo →
                            // shift.sextiarysector.api.IDrink) で、環境に無いだけ。以前は
                            // NoClassDefFoundError で mod ごとロード失敗 (ブロック未登録) して
                            // いたが、スタブを生成して「連携機能だけ無効・本体は動く」形にする。
                            c = generateStubClass(name);
                        } else {
                            throw e;
                        }
                    } catch (RuntimeException e) {
                        // NeoForge RuntimeDistCleaner throws RuntimeException when a
                        // client-only class is loaded on the server. Generate a stub class
                        // with the correct name so bytecode verification doesn't crash.
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("invalid dist")) {
                            c = generateStubClass(name);
                        } else {
                            throw e;
                        }
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    private static boolean isLegacyMinecraftClass(String name) {
        return name.startsWith("net.minecraft.") ||
               name.startsWith("net.minecraftforge.") ||
               name.startsWith("cpw.mods.fml.") ||
               name.startsWith("cpw.mods.fml.common.") ||
               name.startsWith("cpw.mods.fml.relauncher.") ||
               name.startsWith("cpw.mods.fml.client.");
    }

    /**
     * 1.7.10 mod が参照する「環境に無い別 mod のクラス」をスタブ化してよいか。
     * このローダは legacy mod jar 専用なので、java./自分自身以外の未解決クラスは
     * 任意連携の不足とみなしてスタブ化する (本体機能は生かす)。
     */
    private static boolean isStubbableMissingClass(String name) {
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.")
                && !name.startsWith("sun.")
                && !name.startsWith("com.myname.legacyloader.");
    }

    /** スタブをインターフェースとして生成すべきか (実装/継承の食い違い回避)。 */
    private static boolean isLikelyInterfaceName(String internalName) {
        String simple = internalName;
        int slash = simple.lastIndexOf('/');
        if (slash >= 0) simple = simple.substring(slash + 1);
        int dollar = simple.lastIndexOf('$');
        if (dollar >= 0) simple = simple.substring(dollar + 1);
        // I + 大文字始まり (IDrink, IFluidHandler 等) は 1.7.10 の慣習でインターフェース。
        // .api. パッケージ配下も連携インターフェースが多い。
        return simple.length() >= 2 && simple.charAt(0) == 'I'
                && Character.isUpperCase(simple.charAt(1));
    }

    /**
     * Runtime fallback for 1.7.10 classes that are still not mapped to a real bridge.
     *
     * The previous implementation emitted an empty Object subclass. That prevented
     * ClassNotFoundException, but the first field/method access usually crashed with
     * NoSuchFieldError / NoSuchMethodError. This version reads a generated signature
     * table made from the provided 1.7.10 sources and emits fields/methods with the
     * same JVM descriptors. The methods intentionally return safe default values; this
     * is not a behaviour implementation, but it allows non-critical legacy API calls to
     * fail soft while we add real bridge classes one by one.
     */
    /**
     * 署名テーブル (legacy_1_7_10_signatures.tsv) が無い環境では spec が空になり、合成スタブが
     * すべて class になる。1.7.10 で <b>interface</b> だったクラスを class として合成すると、
     * それを {@code implements} する mod クラスが {@code IncompatibleClassChangeError} で落ち、
     * PreInit が途中終了して以降のブロック登録が止まる。ブリッジが無く合成に回る既知の
     * 1.7.10 interface はここで interface として合成させる (出るたびに追記)。
     */
    private static final java.util.Set<String> FORCE_INTERFACE = java.util.Set.of(
        "net/minecraft/client/gui/GuiYesNoCallback"
    );

    private Class<?> generateStubClass(String name) {
        String internalName = name.replace('.', '/');
        StubSpec spec = getStubSpecs().get(internalName);
        boolean isInterface = FORCE_INTERFACE.contains(internalName)
            || (spec != null && ("interface".equals(spec.kind) || "@interface".equals(spec.kind)))
            // シグネチャ表に無い未知クラス (別 mod API 等) は名前からインターフェース推定。
            // implements IDrink 等を満たすため。
            || (spec == null && isLikelyInterfaceName(internalName));
        boolean isEnum = spec != null && "enum".equals(spec.kind);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int access = Opcodes.ACC_PUBLIC;
        String superName = "java/lang/Object";
        String[] interfaces = null;

        // 例外型のスタブは Throwable のサブクラスにする。
        // 1.7.10 mod のコードが `catch (SomeException e)` を持つと、JVM の検証器は
        // catch 型が Throwable のサブクラスであることを要求する。java/lang/Object 継承の
        // スタブだと "Catch type is not a subclass of Throwable" で VerifyError になり、
        // そのメソッドを含むクラス (= ブロック登録処理など) が丸ごと失敗して
        // ブロックが登録されず、テクスチャも付かない (ピンクブロック) 原因になっていた。
        // RuntimeException を親にすると unchecked なので、throws 宣言の有無に依らず
        // どのメソッドからでも throw/catch できて検証を通る。
        boolean isThrowable = !isInterface && !isEnum && isExceptionLikeName(internalName, spec);

        if (isInterface) {
            access |= Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        } else if (isEnum) {
            // Keep enum stubs loadable. They are not real Java enums, but they satisfy
            // most descriptor/linkage checks better than a missing class.
            access |= Opcodes.ACC_SUPER;
        } else {
            access |= Opcodes.ACC_SUPER;
            if (isThrowable) {
                superName = "java/lang/RuntimeException";
            }
        }

        cw.visit(Opcodes.V21, access, internalName, null, superName, interfaces);

        if (spec != null) {
            for (String f : spec.fields) {
                if (f.isEmpty()) continue;
                String[] parts = f.split(":", 3);
                if (parts.length < 3) continue;
                int faccess = Opcodes.ACC_PUBLIC;
                if ("S".equals(parts[2])) faccess |= Opcodes.ACC_STATIC;
                cw.visitField(faccess, parts[0], parts[1], null, null).visitEnd();
            }
        }

        if (!isInterface) {
            // 引数なしコンストラクタ (super の <init>()V を呼ぶ)
            MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            init.visitCode();
            init.visitVarInsn(Opcodes.ALOAD, 0);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
            init.visitInsn(Opcodes.RETURN);
            init.visitMaxs(1, 1);
            init.visitEnd();

            if (isThrowable) {
                // 例外はメッセージ付きで new されることが多い (new ModSortingException("...") 等)。
                // (String) / (String,Throwable) / (Throwable) の 3 コンストラクタを用意する。
                emitThrowableCtor(cw, superName, "(Ljava/lang/String;)V");
                emitThrowableCtor(cw, superName, "(Ljava/lang/String;Ljava/lang/Throwable;)V");
                emitThrowableCtor(cw, superName, "(Ljava/lang/Throwable;)V");
            }
        }

        if (spec != null) {
            for (String m : spec.methods) {
                if (m.isEmpty()) continue;
                int flag = m.lastIndexOf(':');
                if (flag <= 0) continue;
                String sig = m.substring(0, flag);
                String mode = m.substring(flag + 1);
                int descStart = sig.indexOf('(');
                if (descStart <= 0) continue;
                String methodName = sig.substring(0, descStart);
                String desc = sig.substring(descStart);
                if ("<init>".equals(methodName) || "<clinit>".equals(methodName)) continue;

                int maccess = Opcodes.ACC_PUBLIC;
                if (isInterface && !"S".equals(mode)) {
                    maccess |= Opcodes.ACC_ABSTRACT;
                    cw.visitMethod(maccess, methodName, desc, null, null).visitEnd();
                } else {
                    if ("S".equals(mode)) maccess |= Opcodes.ACC_STATIC;
                    MethodVisitor mv = cw.visitMethod(maccess, methodName, desc, null, null);
                    mv.visitCode();
                    emitDefaultReturn(mv, desc);
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                }
            }
        }

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        return defineClass(name, bytes, 0, bytes.length);
    }

    /** スタブ名から例外型か判定 (Throwable を継承させる対象)。 */
    private static boolean isExceptionLikeName(String internalName, StubSpec spec) {
        if (spec != null && ("exception".equals(spec.kind) || "throwable".equals(spec.kind))) {
            return true;
        }
        String simple = internalName;
        int slash = simple.lastIndexOf('/');
        if (slash >= 0) simple = simple.substring(slash + 1);
        int dollar = simple.lastIndexOf('$');
        if (dollar >= 0) simple = simple.substring(dollar + 1);
        return simple.endsWith("Exception") || simple.endsWith("Error") || simple.equals("Throwable");
    }

    /** 例外スタブ用コンストラクタ (引数を捨てて super の空引数コンストラクタを呼ぶ)。 */
    private void emitThrowableCtor(ClassWriter cw, String superName, String desc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        // RuntimeException() を呼ぶ (メッセージ引数は無視。cause 連携は不要な用途)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 3);
        mv.visitEnd();
    }

    private static void emitDefaultReturn(MethodVisitor mv, String desc) {
        int r = desc.lastIndexOf(')');
        String ret = r >= 0 ? desc.substring(r + 1) : "V";
        switch (ret.charAt(0)) {
            case 'V' -> mv.visitInsn(Opcodes.RETURN);
            case 'J' -> { mv.visitInsn(Opcodes.LCONST_0); mv.visitInsn(Opcodes.LRETURN); }
            case 'F' -> { mv.visitInsn(Opcodes.FCONST_0); mv.visitInsn(Opcodes.FRETURN); }
            case 'D' -> { mv.visitInsn(Opcodes.DCONST_0); mv.visitInsn(Opcodes.DRETURN); }
            case 'Z', 'B', 'C', 'S', 'I' -> { mv.visitInsn(Opcodes.ICONST_0); mv.visitInsn(Opcodes.IRETURN); }
            default -> { mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN); }
        }
    }

    private static class StubSpec {
        final String kind;
        final String[] fields;
        final String[] methods;
        StubSpec(String kind, String[] fields, String[] methods) {
            this.kind = kind;
            this.fields = fields;
            this.methods = methods;
        }
    }

    private static volatile Map<String, StubSpec> STUB_SPECS;

    private static Map<String, StubSpec> getStubSpecs() {
        Map<String, StubSpec> cached = STUB_SPECS;
        if (cached != null) return cached;
        Map<String, StubSpec> map = new HashMap<>();
        try (InputStream in = LegacyClassLoader.class.getClassLoader().getResourceAsStream("legacy_1_7_10_signatures.tsv")) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : text.split("\\R")) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\\t", -1);
                    if (p.length >= 4) {
                        map.put(p[0], new StubSpec(p[1], p[2].isEmpty() ? new String[0] : p[2].split("\\|"), p[3].isEmpty() ? new String[0] : p[3].split("\\|")));
                    }
                }
            }
        } catch (Throwable ignored) {}
        STUB_SPECS = map;
        return map;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // ... (譌｢蟄倥・繧ｳ繝ｼ繝・ Mod蜀・・繧ｯ繝ｩ繧ｹ繝ｭ繝ｼ繝牙・逅・ ...
        // 螟画峩縺ｪ縺・
        if (name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("sun.") ||
                name.startsWith("net.minecraft.") ||
                name.startsWith("com.myname.legacyloader.")) {
            return super.findClass(name);
        }

        String path = name.replace('.', '/').concat(".class");
        try (InputStream is = getResourceAsStream(path)) {
            if (is == null) {
                return super.findClass(name);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] classBytes = baos.toByteArray();

            byte[] transformedBytes = ClassTransformer.transform(classBytes);

            return defineClass(name, transformedBytes, 0, transformedBytes.length);

        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to load class: " + name, e);
        }
    }
}