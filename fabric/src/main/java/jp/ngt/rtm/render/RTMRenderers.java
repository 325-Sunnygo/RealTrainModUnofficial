package jp.ngt.rtm.render;

import javax.script.ScriptEngine;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;

import jp.ngt.rtm.modelpack.ModelPackManager;

/**
 * 本家 jp.ngt.rtm.render.RTMRenderers の受け皿。
 * 1.7.10 (KaizPatchX) では getRendererWithScript は
 * PartsRenderer#getRendererWithScript にあり、RTMRenderers というクラスは
 * 1.12.2 で分離されたものだが、パック側の mc1710 ターゲットがこの名前で呼んでくる:
 */
public final class RTMRenderers {

    private RTMRenderers() {
    }

    /**
     * 本家 getRendererWithScript(ResourceLocation par1, String... args)。
     * リソースからスクリプトを読み、その renderClass で PartsRenderer を生成する。
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
