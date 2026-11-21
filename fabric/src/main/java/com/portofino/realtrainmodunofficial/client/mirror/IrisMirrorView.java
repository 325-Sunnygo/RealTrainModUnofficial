package com.portofino.realtrainmodunofficial.client.mirror;

import com.mojang.blaze3d.pipeline.*;
import net.minecraft.client.Minecraft;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/** Optional Iris secondary views. Preserve the primary pipeline and its uniforms/buffers. */
public final class IrisMirrorView implements AutoCloseable {
    private static Object sourcePipeline,reflectionPipeline,vanillaSkyPipeline;
    private static boolean vanillaView;
    public static boolean vanillaView(){return vanillaView;}
    private static TextureTarget scratch,nativeSky;
    private final boolean nativePass;
    private final Object manager,dimension,previous;
    private final Field pipelineField,rendererPipelineField;
    private final Object previousRendererPipeline;
    private final Map<Object,Object> pipelines;
    private final Object previousDimension;
    private final Object captured;
    private final Map<Field,Object> capturedValues=new LinkedHashMap<>();
    private IrisMirrorView(int size,boolean nativePass) throws ReflectiveOperationException {
        this.nativePass=nativePass;
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
        manager=iris.getMethod("getPipelineManager").invoke(null);
        dimension=iris.getMethod("getCurrentDimension").invoke(null);
        pipelineField=field(manager.getClass(),"pipeline");previous=pipelineField.get(manager);
        rendererPipelineField=field(Minecraft.getInstance().levelRenderer.getClass(),"pipeline");
        previousRendererPipeline=rendererPipelineField.get(Minecraft.getInstance().levelRenderer);
        @SuppressWarnings("unchecked") var map=(Map<Object,Object>)field(manager.getClass(),"pipelinesPerDimension").get(manager);pipelines=map;
        previousDimension=pipelines.get(dimension);
        captured=Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState").getField("INSTANCE").get(null);
        for(var f:captured.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers())) {
            f.setAccessible(true);Object value=f.get(captured);
            if(value instanceof org.joml.Matrix4fc m)value=new org.joml.Matrix4f(m);
            else if(value instanceof org.joml.Vector3dc v)value=new org.joml.Vector3d(v);
            capturedValues.put(f,value);
        }
        if(sourcePipeline!=previous||scratch==null||scratch.width!=size) {
            clear();sourcePipeline=previous;scratch=new TextureTarget(size,size,true,Minecraft.ON_OSX);nativeSky=new TextureTarget(size,size,true,Minecraft.ON_OSX);
            var mc=Minecraft.getInstance();var targetAccess=(MirrorTargetAccess)mc;var original=targetAccess.rtmu$getMainTarget();
            try {
                targetAccess.rtmu$setMainTarget(scratch);
                @SuppressWarnings("unchecked") var factory=(Function<Object,Object>)field(manager.getClass(),"pipelineFactory").get(manager);
                reflectionPipeline=factory.apply(dimension);
            } finally {targetAccess.rtmu$setMainTarget(original);}
        }
        if(vanillaSkyPipeline==null) {
            Object settings=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings").getField("INSTANCE").get(null);
            var saved=new LinkedHashMap<Field,Object>();
            for(var f:settings.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers())){f.setAccessible(true);saved.put(f,f.get(settings));}
            try{vanillaSkyPipeline=Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor().newInstance();}
            finally{for(var entry:saved.entrySet())entry.getKey().set(settings,entry.getValue());}
        }
        Object selected=nativePass?vanillaSkyPipeline:reflectionPipeline;
        pipelines.put(dimension,selected);pipelineField.set(manager,selected);vanillaView=nativePass;
    }
    private static Field field(Class<?> type,String name) throws ReflectiveOperationException {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    public static IrisMirrorView open(int size,boolean nativePass) {
        if(!com.portofino.realtrainmodunofficial.client.ShaderCompat.active())return null;
        try{return new IrisMirrorView(size,nativePass);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Iris secondary-view API is incompatible",failure);}
    }
    public RenderTarget target(){return nativePass?nativeSky:scratch;}
    public RenderTarget skyTarget(){return nativeSky;}
    public int depthTexture() {
        try{Object targets=field(reflectionPipeline.getClass(),"renderTargets").get(reflectionPipeline);return (Integer)targets.getClass().getMethod("getDepthTexture").invoke(targets);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Could not read Iris secondary depth",failure);}
    }
    @Override public void close() {
        vanillaView=false;
        try {
            pipelineField.set(manager,previous);
            rendererPipelineField.set(Minecraft.getInstance().levelRenderer,previousRendererPipeline);
            if(previousDimension==null)pipelines.remove(dimension);else pipelines.put(dimension,previousDimension);
            for(var entry:capturedValues.entrySet())entry.getKey().set(captured,entry.getValue());
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Could not restore Iris primary view",failure);}
    }
    public static void clear() {
        if(reflectionPipeline!=null) {
            try{reflectionPipeline.getClass().getMethod("destroy").invoke(reflectionPipeline);}
            catch(ReflectiveOperationException failure){com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Could not release Iris mirror pipeline",failure);}
        }
        reflectionPipeline=null;sourcePipeline=null;
        if(scratch!=null){scratch.destroyBuffers();scratch=null;}
        if(nativeSky!=null){nativeSky.destroyBuffers();nativeSky=null;}
    }
}
