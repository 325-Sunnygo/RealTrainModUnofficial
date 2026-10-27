package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.GLHelper のスクリプト互換ファサード。
 * 発光系 (setLightmapMaxBrightness/setBrightness) は GLRecorder に BRIGHTNESS として記録し、
 * 再生側が packedLight として使用する (1.7.10 の輝度パック形式は 1.21 と同一レイアウト)。
 */
@SuppressWarnings("unused")
public final class GLHelper {
    private GLHelper() {
    }

    public static final GLHelper INSTANCE=new GLHelper();
    public static final jp.ngt.ngtlib.util.Locker LOCKER=new jp.ngt.ngtlib.util.Locker();

    public static void disableLighting() { GL11Facade.glDisable(GL11Facade.GL_LIGHTING); }
    public static void enableLighting() { GL11Facade.glEnable(GL11Facade.GL_LIGHTING); }

    public static void setLightmapMaxBrightness() {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(0xF000F0);
        }
    }

    public static void setBrightness(int packed) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(packed);
        }
    }

    public static void setLightmapCoords(float u, float v) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.brightness(((int) v << 16) | (int) u);
        }
    }

    public static void startMousePicking(float range) { LegacySelection.start(range); }
    public static int finishMousePicking() { return LegacySelection.finish(); }
    public static double getPickedObjDepth(int index) { return LegacySelection.hit(index).depth(); }
    public static int getPickedObjId(int index) { return LegacySelection.hit(index).id(); }

    // ---- ディスプレイリスト (NGTO Builder のミニチュアプレビューが使用) ----
    // 本家の GL ディスプレイリストを GLRecorder のサブ記録として実装する:
    // generateGLList → 非 0 ハンドル (0 を返すと JS 側の `!glList` 判定が毎フレーム真になり
    // callList 分岐が永久に実行されず、プレビューが表示されない)。
    private static final java.util.Map<Integer, GLRecorder> GL_LISTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<DisplayList> LIST_HANDLES=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static DisplayList handle(int value) { DisplayList list=new DisplayList(value); LIST_HANDLES.add(list); return list; }
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_LIST_ID =
        new java.util.concurrent.atomic.AtomicInteger(1);
    private record Compilation(GLRecorder previous, int id) {}
    private static final ThreadLocal<java.util.Deque<Compilation>> COMPILATIONS=ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public static DisplayList generateGLList() {
        return handle(NEXT_LIST_ID.getAndIncrement());
    }

    /** 非 old 分岐は generateGLList(null) と呼ぶ (引数は未使用)。 */
    public static DisplayList generateGLList(Object previous) {
        return previous instanceof DisplayList list && GL_LISTS.containsKey(list.value) ? handle(list.value) : generateGLList();
    }

    public static void startCompile(DisplayList list) { startCompile(list.value); }

    public static void startCompile(int list) {
        if(list<=0) throw new IllegalArgumentException("Invalid display list");
        COMPILATIONS.get().push(new Compilation(GLRecorder.active(),list));
        GLRecorder rec = new GLRecorder();
        GL_LISTS.put(list, rec);
        GLRecorder.activate(rec);
    }

    public static void endCompile() {
        var stack=COMPILATIONS.get();
        if(stack.isEmpty()) throw new IllegalStateException("endCompile without startCompile");
        GLRecorder previous=stack.pop().previous();
        if(previous!=null) GLRecorder.activate(previous); else GLRecorder.deactivate();
        if(stack.isEmpty()) COMPILATIONS.remove();
    }

    public static void callList(DisplayList list) { if(list!=null) callList(list.value); }

    public static void callList(int list) {
        GLRecorder current = GLRecorder.active();
        GLRecorder stored = GL_LISTS.get(list);
        if (current != null && stored != null) {
            current.appendFrom(stored);
        }
    }

    public static void deleteGLList(DisplayList list) { if(list!=null) { deleteGLList(list.value); list.value=0; } }

    public static void deleteGLList(int list) {
        GL_LISTS.remove(list);
    }

    public static void transform(double x, double y, double z, float yaw, float pitch, float roll) {
        GLRecorder r = GLRecorder.active();
        if (r != null) {
            r.translate((float) x, (float) y, (float) z);
            r.rotate(yaw, 0.0F, 1.0F, 0.0F);
            r.rotate(-pitch, 1.0F, 0.0F, 0.0F);
            r.rotate(roll, 0.0F, 0.0F, 1.0F);
        }
    }

    // ---- テクスチャ UV スクロール (方向幕/回転計器) ----
    // テクスチャ行列の平行移動をコマンド状態と専用シェーダーへ保存する。
    public static void preMoveTexUV(float u, float v) {
        LegacyRenderState.current().pushTexture(u,v);
    }

    public static void postMoveTexUV() {
        LegacyRenderState.current().popTexture();
    }

    /** 本家setColor: 0xRRGGBB + alpha(0-255) を現在色に設定 (GLRecorder経由)。 */
    public static void setColor(int rgb, int alpha) {
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float a = alpha / 255.0F;
        jp.ngt.ngtlib.renderer.GLRecorder rec = jp.ngt.ngtlib.renderer.GLRecorder.active();
        if (rec != null) {
            rec.color(r, g, b, a);
        }
    }

    /** 本家clearGLList: 1.21はGLリスト非使用のためno-op。 */
    public static void clearGLList() {
    }

    /** リソース再読込時に記録済みリストを破棄する。 */
    public static void initGLList() {
        LIST_HANDLES.forEach(list->list.value=0); LIST_HANDLES.clear();
        GL_LISTS.clear();
    }

    /** 現在のスレッドが表示リストを記録しているか。 */
    public static boolean isCompiling() {
        return !COMPILATIONS.get().isEmpty();
    }

    /** 本家isValid(DisplayList)。 */
    public static boolean isValid(Object par1) {
        return par1 instanceof DisplayList list ? list.value>0 : par1 instanceof Number number && number.intValue()>0;
    }

    /** Compile, link and validate source strings in the active render context. */
    public static int getShaderProgram(String vsh, String fsh) {
        return LegacyShaderProgram.create(vsh,fsh);
    }

}
