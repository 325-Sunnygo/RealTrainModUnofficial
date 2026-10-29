package com.portofino.realtrainmodunofficial.script;

import jp.ngt.rtm.render.*;
import jp.ngt.ngtlib.renderer.*;

public final class LegacyRailAndAlphaRegression {
    public static final class SwitchProbeRenderer extends RailPartsRenderer {
        @Override public net.minecraft.world.level.Level getWorld(Object tile) { return null; }
        @Override public boolean isSwitchRail(Object tile) { return true; }
    }
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        check(RailPartsRenderer.class.getDeclaredConstructor().newInstance()!=null,"standard rail renderClass has its reference no-arg constructor");
        check(LegacyRailSampling.split(Math.nextDown(10.5D),0)==21,"sample split uses reference float rounding");
        check(LegacyRailSampling.split(0.1,2)==2,"minimum split override");
        check(LegacyRailSampling.lastIndex(20,2)==18,"end offset removes samples rather than shifting models");
        check(!LegacyRailSampling.crossSection("sideL",1,21,true,true),"internal cross sections are excluded");
        check(LegacyRailSampling.crossSection("sideL",0,21,true,true) && LegacyRailSampling.crossSection("sideR",20,21,true,true),"end cross sections survive");
        check(!LegacyRailSampling.crossSection("sideL",0,21,false,true),"disabled start cap is excluded");
        check(LegacyRailSampling.crossSection("railL",10,21,false,false),"running rails are not guessed from geometry");
        var script=jp.ngt.ngtlib.io.ScriptUtil.createEngine();
        script.eval("function shouldRenderObject(tile,name,len,pos){return pos===len-1;}");
        var renderer=new RailPartsRenderer(); renderer.setScript(script);
        check(renderer.shouldRenderObject(null,"base",21,20) && !renderer.shouldRenderObject(null,"base",21,19),"script end trimming receives logical count and index");
        String standard;
        try(var input=LegacyRailAndAlphaRegression.class.getResourceAsStream("/assets/minecraft/scripts/RenderRailStandard.js")) {
            standard=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        String prepared=PackScriptSource.prepare(standard,"scripts/RenderRailStandard.js",path->{
            try(var input=LegacyRailAndAlphaRegression.class.getResourceAsStream("/assets/minecraft/"+path)) {
                return input==null?null:input.readAllBytes();
            } catch(java.io.IOException exception) { throw new RuntimeException(exception); }
        });
        var standardEngine=jp.ngt.ngtlib.io.ScriptUtil.doScript("var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n"+prepared);
        var standardRenderer=(RailPartsRenderer)Class.forName(String.valueOf(standardEngine.get("renderClass"))).getDeclaredConstructor().newInstance();
        standardRenderer.setScript(standardEngine); standardEngine.put("renderer",standardRenderer);
        standardRenderer.init(null,new ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[0]));
        check(standardEngine.get("staticParts")!=null,"actual standard rail script init succeeds");
        check(standardRenderer.shouldRenderObject(null,"base",21,20) && standardRenderer.shouldRenderObject(null,"railL",21,20),"actual rail script keeps the authored bed and running rails");
        check(!standardRenderer.shouldRenderObject(null,"L0",21,20) && !standardRenderer.shouldRenderObject(null,"R1",21,20),"actual rail script excludes switch tongues from plain track");
        var recorded = new GLRecorder();
        standardEngine.put("testMain", new jp.ngt.rtm.rail.util.RailMapBasic(
            jp.ngt.rtm.rail.util.RailPosition.free(0,64,0,0,0,0),
            jp.ngt.rtm.rail.util.RailPosition.free(0,64,20,180,0,0),1));
        standardEngine.put("testBranch", new jp.ngt.rtm.rail.util.RailMapBasic(
            jp.ngt.rtm.rail.util.RailPosition.free(0,64,0,0,0,0),
            jp.ngt.rtm.rail.util.RailPosition.free(6,64,20,180,0,0),1));
        var probe=new SwitchProbeRenderer(); probe.setScript(standardEngine); standardEngine.put("renderer",probe);
        probe.init(null,new ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[]{new jp.ngt.ngtlib.renderer.model.TextureSet(new jp.ngt.ngtlib.renderer.model.Material(null))}));
        GLRecorder.activate(recorded);
        try {
            standardEngine.eval("var testTile={getStartPoint:function(){return [0,64,0];},getRailPositions:function(){return [{direction:0,posX:0,posY:64,posZ:0,blockX:0,blockY:64,blockZ:0}];}}; renderRailMapDynamic(testTile,testMain,Java.type('jp.ngt.rtm.rail.util.RailDir').LEFT,true,0,4); renderRailMapDynamic(testTile,testBranch,Java.type('jp.ngt.rtm.rail.util.RailDir').RIGHT,true,0.35,4);");
        } finally { GLRecorder.deactivate(); }
        check(recorded.hasGeometry(), "actual standard switch script records running rails and tongues");
        check(com.portofino.realtrainmodunofficial.client.render.IrisTexturePasses.accepts(Math.nextDown(1F),514,1F), "opaque alpha survives interpolation roundoff");
        check(!com.portofino.realtrainmodunofficial.client.render.IrisTexturePasses.accepts(Math.nextDown(1F),513,1F), "opaque alpha does not leak to transparent pass");
        check(!com.portofino.realtrainmodunofficial.client.render.IrisTexturePasses.accepts(254F/255F,514,1F), "authored translucent texel remains excluded from opaque pass");
        check(com.portofino.realtrainmodunofficial.client.render.IrisTexturePasses.accepts(254F/255F,513,1F), "authored translucent texel remains in transparent pass");
        checkFullSwitchScript("RenderRailStandard.js");
        checkFullSwitchScript("RenderRailStandardHP.js");
        var base=new LegacyRenderState().snapshot();
        var opaque=base.withDefaultAlpha(514,1);
        var transparent=base.withDefaultAlpha(513,1);
        check(opaque.alphaFunction()==514 && opaque.alphaReference()==1,"vehicle normal alpha equals one");
        check(transparent.alphaFunction()==513 && transparent.alphaReference()==1,"vehicle translucent alpha less than one");
        check(base.alphaFunction()==null,"alpha defaults do not mutate recorded state");
        var explicit=new LegacyRenderState(); explicit.alpha(516,0.2F);
        check(explicit.snapshot().withDefaultAlpha(513,1).alphaFunction()==516,"authored alpha state overrides vehicle defaults");
        var start=jp.ngt.rtm.rail.util.RailPosition.free(0,64,0,0,0,0);
        var end=jp.ngt.rtm.rail.util.RailPosition.free(0,64,10,180,0,0);
        var line=new jp.ngt.rtm.rail.util.RailMapBasic(start,end,1);
        check(jp.ngt.rtm.entity.train.util.LegacyBogieSearch.trailingIndex(line,10,8,10,64,new double[]{0,64,0},10)==10,"trailing bogie can select final rail endpoint");
        check(jp.ngt.rtm.entity.train.util.LegacyBogieSearch.trailingIndex(line,10,10,10,64,new double[]{0,64,0},10)==10,"single-point endpoint interval does not reset index to zero");
        check(jp.ngt.rtm.entity.train.util.LegacyBogieSearch.trailingIndex(line,10,0,2,64,new double[]{0,64,10},10)==0,"reverse endpoint remains selectable");
        System.out.println("Legacy rail/alpha regressions: "+checks+" passed");
    }
    private static void checkFullSwitchScript(String file) throws Exception {
        String source;
        try(var input=LegacyRailAndAlphaRegression.class.getResourceAsStream("/assets/minecraft/scripts/"+file)) {
            source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var prepared=PackScriptSource.prepare(source,"scripts/"+file,path->{
            try(var input=LegacyRailAndAlphaRegression.class.getResourceAsStream("/assets/minecraft/"+path)) {return input==null?null:input.readAllBytes();}
            catch(java.io.IOException e){throw new RuntimeException(e);}
        });
        var engine=jp.ngt.ngtlib.io.ScriptUtil.doScript("var GL11=Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n"+prepared,file);
        var renderer=new SwitchProbeRenderer();renderer.setScript(engine);engine.put("renderer",renderer);
        renderer.init(null,new ModelObject(new jp.ngt.ngtlib.renderer.model.TextureSet[]{new jp.ngt.ngtlib.renderer.model.TextureSet(new jp.ngt.ngtlib.renderer.model.Material(null))}));
        var start=jp.ngt.rtm.rail.util.RailPosition.free(0,64,0,0,0,0);
        engine.put("testMain",new jp.ngt.rtm.rail.util.RailMapBasic(start,jp.ngt.rtm.rail.util.RailPosition.free(0,64,20,180,0,0),1));
        engine.put("testBranch",new jp.ngt.rtm.rail.util.RailMapBasic(start,jp.ngt.rtm.rail.util.RailPosition.free(6,64,20,180,0,0),1));
        engine.eval("var movement=0, positive=true, testDir=Java.type('jp.ngt.rtm.rail.util.RailDir');var testPoint={branchDir:testDir.LEFT,rmMain:testMain,rmBranch:testBranch,mainDirIsPositive:true,branchDirIsPositive:true,getMovement:function(){return movement;}};var testTile={getStartPoint:function(){return [0,64,0];},getRailPositions:function(){return [{direction:0,posX:0,posY:64,posZ:0,blockX:0,blockY:64,blockZ:0}];},getSwitch:function(){return {getPoints:function(){return [testPoint];}};}};");
        for(String direction:new String[]{"LEFT","RIGHT"})for(boolean positive:new boolean[]{true,false}) {
            java.util.Set<Integer> keys=new java.util.HashSet<>();
            for(double movement:new double[]{0,.5,1}) {
                engine.put("movement",movement);
                engine.eval("testPoint.branchDir=testDir."+direction+";testPoint.mainDirIsPositive="+positive+";testPoint.branchDirIsPositive="+positive+";");
                var record=new GLRecorder();GLRecorder.activate(record);
                try {jp.ngt.ngtlib.io.ScriptUtil.doScriptFunction(engine,"renderRailDynamic",engine.get("testTile"),0D,0D,0D,.5F,0);}
                finally {GLRecorder.deactivate();}
                check(record.hasGeometry(),file+" full dynamic callback produced no rails");
                var groups=new java.util.HashSet<String>();
                for(var command:record.getCommands())if(command.op==GLRecorder.Op.RENDER_GROUPS && command.payload instanceof java.util.Set<?> names)for(var name:names)groups.add(name.toString());
                check(groups.contains("raill")&&groups.contains("railr"),file+" omitted a running rail");
                check(groups.stream().anyMatch(n->n.matches("[lr][01]")),file+" omitted the switch tongues");
                keys.add(record.contentKey());
            }
            check(keys.size()==3,file+" switch movement was frozen");
        }
    }

}
