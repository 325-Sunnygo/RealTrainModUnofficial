package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.modelpack.IModelSelector;
import jp.ngt.rtm.modelpack.ScriptExecuter;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;

import javax.script.ScriptEngine;
import java.util.Objects;

/** Behavior checks using production ScriptUtil/ScriptExecuter, without launching a world. */
public final class LegacyRuntimeRegression {
    private static int checks;

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static final class Selector implements IModelSelector {
        private final ModelSetBase<String> model = new ModelSetBase<>("test") {
            @Override public String getDummyConfig() { return "test"; }
        };
        public Selector(ScriptEngine engine) { model.serverSE = engine; }
        @Override public Object getResourceState() { return null; }
        @Override public String getModelName() { return "test"; }
        @Override public void setModelName(String name) {}
        @Override public Object getModelSet() { return model; }
    }

    public static void main(String[] args) throws Exception {
        LegacyScriptRegression.main(args);
        LegacyFastIncludeRegression.main(args);
        LegacyModelRegression.main(args);
        LegacyPrecisionRegression.main(args);
        LegacyObjLoadingRegression.main(args);
        LegacyRailSectionsRegression.main(args);
        LegacyCameraRegression.main(args);
        com.portofino.realtrainmodunofficial.client.render.LegacyVertexTransformRegression.main(args);
        LegacyRenderStateRegression.main(args);
        LegacyRenderApiRegression.main(args);
        LegacySelectionRegression.main(args);
        LegacyImmediateRegression.main(args);
        LegacyConfigRegression.main(args);
        LegacyConfigRuntimeRegression.main(args);
        LegacyRemainingConfigRegression.main(args);
        LegacyRailAndAlphaRegression.main(args);
        ScriptEngine engine = ScriptUtil.createEngine();
        ScriptExecuter executer = new ScriptExecuter();
        executer.updateScript(engine, null);
        equal(1L, executer.count); // missing callback must still advance time
        equal(false, ScriptUtil.hasFunction(engine, "onUpdate"));
        engine.eval("var observed = -1; function onUpdate(entity, exec) { observed=exec.count; throw new Error('expected regression failure'); }");
        equal(true, ScriptUtil.hasFunction(engine, "onUpdate"));
        executer.updateScript(engine, null);
        equal(2L, executer.count); // throwing callbacks must still advance time
        equal(1L, ((Number) engine.get("observed")).longValue());
        engine.eval("function onUpdate(entity, exec) { observed=exec.count; }");
        executer.updateScript(engine, null);
        equal(3L, executer.count);
        equal(2L, ((Number) engine.get("observed")).longValue());
        engine.eval("function event(a,b) { return a+b; }");
        Selector selector = new Selector(engine);
        equal(42, ((Number) executer.execScript(selector, "event", 20, 22)).intValue());
        equal(3L, executer.count); // named events do not advance onUpdate time
        executer.execScript(selector);
        equal(4L, executer.count); // getModelSet().serverSE route
        equal(3L, ((Number) engine.get("observed")).longValue());
        ScriptUtil.doScriptIgnoreError(engine, "late", 1);
        engine.eval("function late(x) { return x + 1; }");
        equal(42, ((Number) ScriptUtil.doScriptIgnoreError(engine, "late", 41)).intValue());
        engine.eval("late = null;");
        equal(false, ScriptUtil.hasFunction(engine, "late"));
        equal(0, executer.getPlayerCoordinates().posX);
        equal(true, executer.func_70003_b(2, "say"));
        equal(false, executer.func_70003_b(3, "stop"));
        jp.ngt.rtm.rail.util.RailPosition rail = new jp.ngt.rtm.rail.util.RailPosition(10, 64, 20, (byte) 0);
        rail.setPosition(10.75D, 64.5D, 20.25D);
        rail.init();
        equal(10.75D, rail.posX);
        equal(64.5D, rail.posY);
        equal(20.25D, rail.posZ);
        jp.ngt.rtm.rail.util.RailPosition restored = jp.ngt.rtm.rail.util.RailPosition.readFromNBT(rail.writeToNBT());
        equal(rail.posX, restored.posX);
        equal(rail.posY, restored.posY);
        equal(rail.posZ, restored.posZ);
        rail.addHeight(0.0625D);
        equal(64.5625D, rail.posY);
        jp.ngt.rtm.rail.util.RailPosition free = jp.ngt.rtm.rail.util.RailPosition.free(0, 64, 0, 0, 0, 0);
        free.setPosition(1.25D, 64.75D, -2.5D);
        equal(1.25D, free.posX);
        equal(64.75D, free.posY);
        equal(-2.5D, free.posZ);
        SoundScriptExecutor sound = new SoundScriptExecutor(null);
        equal(true, sound instanceof jp.ngt.rtm.sound.SoundUpdaterTrain);
        equal(0.0F, sound.getSpeed());
        engine.put("tileType", jp.ngt.rtm.block.tileentity.TileEntityScaffold.TYPE);
        equal(false, engine.eval("null instanceof tileType"));
        String originalText = "var message='x.seatRotation x.func_74737_b()'; /* x.seatRotation */";
        equal(originalText, PackScriptSource.prepare(originalText, "regression.js", path -> null));
        engine.eval(PackScriptSource.prepare("var RL=Java.type('net.minecraft.util.ResourceLocation'); var resource=new RL('test:sample');", "regression.js", path -> null));
        equal("test:sample", engine.get("resource").toString());
        System.out.println("Legacy runtime regressions: " + checks + " passed");
    }
}
