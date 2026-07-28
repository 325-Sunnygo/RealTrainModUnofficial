package jp.ngt.rtm.render;

import javax.script.ScriptEngine;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;

import jp.ngt.rtm.modelpack.ModelPackManager;

/**
 * 本家 {@code jp.ngt.rtm.render.RTMRenderers} の受け皿。
 *
 * <p>1.7.10 (KaizPatchX) では {@code getRendererWithScript} は
 * {@link PartsRenderer#getRendererWithScript} にあり、{@code RTMRenderers} というクラスは
 * 1.12.2 で分離されたものだが、パック側の <b>mc1710 ターゲット</b>がこの名前で呼んでくる:
 *
 * <pre>
 * // SR1-200-test.zip!.../__targets__/mc1710/scripts/hi03_lib/lib_RTMApiCompatClient.compat.js:94
 * Packages.jp.ngt.rtm.render.RTMRenderers.getRendererWithScript.apply(
 *     Packages.jp.ngt.rtm.render.RTMRenderers, [resource, ...args]);
 * </pre>
 *
 * <p>この名前が無いと Nashorn は例外を投げず<b>無音で JavaPackage を返す</b>ため
 * (未解決 FQN の既知の落とし穴)、呼び出しが静かに壊れる。実体は本家同様
 * {@link PartsRenderer#getRendererWithScript} へ委譲する。
 */
public final class RTMRenderers {

    private RTMRenderers() {
    }

    /**
     * 本家 {@code getRendererWithScript(ResourceLocation par1, String... args)}。
     * リソースからスクリプトを読み、その {@code renderClass} で PartsRenderer を生成する。
     *
     * @param resource スクリプトのリソース (ResourceLocation 相当。文字列パスも受ける)
     */
    public static PartsRenderer getRendererWithScript(Object resource, String... args) {
        String script = ModelPackManager.INSTANCE.getScript(resource);
        if (script == null || script.isBlank()) {
            RealTrainModUnofficial.LOGGER.warn("[RTMRenderers] スクリプトが読めません: {}", resource);
            return null;
        }
        try {
            ScriptEngine se = jp.ngt.ngtlib.io.ScriptUtil.doScript(
                    com.portofino.realtrainmodunofficial.script.PackScriptSource.PRELUDE
                            + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(script, String.valueOf(resource)));
            return PartsRenderer.getRendererWithScript(se, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            RealTrainModUnofficial.LOGGER.warn("[RTMRenderers] レンダラ生成に失敗: {}", resource, e);
            return null;
        }
    }
}
