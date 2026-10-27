package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.ngt.ngtlib.renderer.LegacyRenderState;
import java.lang.reflect.*;
import java.util.List;

/** Checks deferred ownership without opening a game or GL window. */
public final class LegacyDeferredAlphaRegression {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static Object get(Object value,String name) throws Exception {
        var method=value.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(value);
    }
    public static void main(String[] args) throws Exception {
        com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        Field active=VehicleScriptRenderers.class.getDeclaredField("levelRenderActive");active.setAccessible(true);
        Field queue=VehicleScriptRenderers.class.getDeclaredField("BAKED_ALPHA_QUEUE");queue.setAccessible(true);
        List<?> draws=(List<?>)queue.get(null);
        active.setBoolean(null,false);
        PoseStack pose=new PoseStack();pose.translate(3,4,5);
        check(!VehicleScriptRenderers.deferBakedAlpha(pose,(p,b)->{}),"GUI draw is immediate");
        check(draws.isEmpty(),"GUI preview does not leak into world");
        try {
            VehicleScriptRenderers.beginLevelRender();
            var state=new LegacyRenderState();state.alpha(513,1);
            try(var ignored=LegacyRenderState.use(state.snapshot())) {
                check(VehicleScriptRenderers.deferBakedAlpha(pose,(p,b)->{}),"world alpha is queued after rail draw");
            }
            check(draws.size()==1,"single alpha submission");
            pose.translate(10,20,30);
            PoseStack captured=(PoseStack)get(draws.get(0),"pose");
            check(captured.last().pose().m30()==3 && captured.last().pose().m31()==4
                    && captured.last().pose().m32()==5,"pose is copied before caller pops or modifies it");
            check(((LegacyRenderState.Snapshot)get(draws.get(0),"state")).alphaFunction()==513,"alpha state travels with draw");
            check(get(draws.get(0),"modelView")!=null,"global local-space transform is captured");
            var interior=InteriorLighting.snapshot();
            InteriorLighting.restore(new InteriorLighting.Snapshot(new float[]{1,2,3},0.2F,0.3F,0.4F));
            var saved=InteriorLighting.snapshot();InteriorLighting.end();InteriorLighting.restore(saved);
            check(InteriorLighting.isActive() && InteriorLighting.red()==0.2F,"interior lighting survives deferred replay");
            InteriorLighting.restore(interior);
        } finally { draws.clear();active.setBoolean(null,false); }
        System.out.println("Legacy deferred alpha regressions: "+checks+" passed");
    }
}
