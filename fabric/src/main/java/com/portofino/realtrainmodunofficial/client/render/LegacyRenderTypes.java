package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.ngt.ngtlib.renderer.LegacyRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Deferred, batching-safe GPU realization of fixed-function states captured by a script. */
public final class LegacyRenderTypes extends RenderType {
    private LegacyRenderTypes() { super("legacy_unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    private static ShaderInstance shader;
    private record Key(ResourceLocation texture, LegacyRenderState.Snapshot state, boolean blend,
                       boolean cull, boolean depthWrite, VertexFormat.Mode mode, int source, int destination, boolean points, float vertexAlpha) {}
    private static final Map<Key,RenderType> TYPES = new ConcurrentHashMap<>();

    private static final Map<Key,RenderType> SHADER_TYPES = new ConcurrentHashMap<>();
    public static void forgetTexture(ResourceLocation texture) {SHADER_TYPES.keySet().removeIf(key->key.texture.equals(texture));}
    private static Map<Key,RenderType> types() {return com.portofino.realtrainmodunofficial.client.ShaderCompat.active()?SHADER_TYPES:TYPES;}
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial","legacy_entity"),
                DefaultVertexFormat.NEW_ENTITY, loaded -> { shader=loaded; TYPES.clear(); SHADER_TYPES.clear(); LegacyPointRenderType.reset(); });
    }

    public static RenderType select(RenderType inherited, ResourceLocation texture, boolean blend, boolean cull,
                                    boolean depthWrite, VertexFormat.Mode mode) {
        return select(inherited,texture,blend,cull,depthWrite,mode,770,771);
    }

    public static RenderType select(RenderType inherited, ResourceLocation texture, boolean blend, boolean cull,
                                    boolean depthWrite, VertexFormat.Mode mode, int source, int destination) {
        LegacyRenderState.Snapshot state=LegacyRenderState.playback();
        if((state==null || state.empty()) && inherited.mode()==mode && !DynamicTextureRegistry.blur(texture)) return inherited;
        if(state==null) state=new LegacyRenderState().snapshot();
        return types().computeIfAbsent(new Key(texture,state,blend,cull,depthWrite,mode,source,destination,false,1F),LegacyRenderTypes::createState);
    }

    public static RenderType vehicle(RenderType inherited,ResourceLocation texture,boolean blend,boolean cull,boolean depthWrite,VertexFormat.Mode mode) {
        return vehicle(inherited,texture,blend,cull,depthWrite,mode,1F);
    }
    public static RenderType vehicle(RenderType inherited,ResourceLocation texture,boolean blend,boolean cull,boolean depthWrite,VertexFormat.Mode mode,float vertexAlpha) {
        var state=LegacyRenderState.playback();
        if(state==null) state=new LegacyRenderState().snapshot();
        // Forge 1.7.10 calls entity pass 1 before restoring glDepthMask(true).
        // RenderVehicleBase inherits that disabled mask, unless a script overrides it.
        state=state.withDefaultAlpha(blend?513:514,1.0F);
        return types().computeIfAbsent(new Key(texture,state,blend,cull,depthWrite && !blend,mode,770,771,false,vertexAlpha),LegacyRenderTypes::createState);
    }

    /** Native model light passes share the body's vertex program and depth positions. */
    public static RenderType modelLight(ResourceLocation texture,boolean lightOn,boolean cull) {
        return modelLight(texture, lightOn, cull, com.portofino.realtrainmodunofficial.client.ShaderCompat.active() ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES);
    }
    public static RenderType modelLight(ResourceLocation texture,boolean lightOn,boolean cull,VertexFormat.Mode mode) {
        LegacyRenderState state=new LegacyRenderState();
        state.alpha(516,0.1F);
        state.enable(0x0B50,!lightOn);
        return types().computeIfAbsent(new Key(texture,state.snapshot(),lightOn,cull,true,
                mode,770,771,false,1F),LegacyRenderTypes::createState);
    }

    public static RenderType points(ResourceLocation texture, boolean additive) {
        var state=LegacyRenderState.playback();
        if(state==null) state=new LegacyRenderState().snapshot();
        return types().computeIfAbsent(new Key(texture,state,true,false,!additive,VertexFormat.Mode.QUADS,770,additive?1:771,true,1F),LegacyRenderTypes::createState);
    }

    private static RenderType createState(Key key) {
        var state=key.state;
        boolean blend=state.enabled(0x0BE2,key.blend);
        boolean depth=state.enabled(0x0B71,true);
        boolean cull=!key.points && state.enabled(0x0B44,key.cull);
        boolean write=state.depthWrite()==null ? key.depthWrite : state.depthWrite();
        int source=state.sourceBlend()==null ? key.source : state.sourceBlend();
        int destination=state.destinationBlend()==null ? key.destination : state.destinationBlend();
        var transparency=new TransparencyStateShard("legacy_blend_"+source+"_"+destination,
                ()->{ if(blend) { RenderSystem.enableBlend(); RenderSystem.blendFuncSeparate(source,destination,
                        state.sourceAlphaBlend()==null ? source : state.sourceAlphaBlend(),
                        state.destinationAlphaBlend()==null ? destination : state.destinationAlphaBlend()); }
                      else RenderSystem.disableBlend();
                      org.lwjgl.opengl.GL11.glCullFace(state.cullFace()==null ? 1029 : state.cullFace()); },
                ()->{ RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); org.lwjgl.opengl.GL11.glCullFace(1029); });
        boolean shaderPack=com.portofino.realtrainmodunofficial.client.ShaderCompat.active();
        ResourceLocation texture=shaderPack?IrisTexturePasses.select(state.enabled(0x0DE1,true)?key.texture:IrisTexturePasses.white(),state,key.vertexAlpha):key.texture;
        // Vanilla suppliers are intercepted by Iris, including its gbuffer/shadow framebuffer routing.
        // GL_LIGHTING=false means that fixed-function directional lighting is disabled;
        // it does not mean that the texture is an entity eye/glow material. Iris maps
        // the emissive supplier to ENTITIES_EYES_TRANS, which makes entire interiors
        // bloom. Keep their recorded lightmap and use the ordinary entity pipeline.
        ShaderStateShard program=shaderPack
                ? shaderPackProgram(blend,write,state.enabled(0x0B50,true))
                : new ShaderStateShard(()->configuredShader(state));
        var pipeline=CompositeState.builder()
                .setShaderState(program)
                .setTextureState(new TextureStateShard(texture,DynamicTextureRegistry.blur(key.texture),false))
                .setTransparencyState(transparency)
                .setDepthTestState(depth ? state.depthFunction()==null ? LEQUAL_DEPTH_TEST : legacyDepth(state.depthFunction()) : NO_DEPTH_TEST)
                .setWriteMaskState(write ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                .setCullState(cull ? CULL : NO_CULL)
                .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                .setLineState(new LineStateShard(java.util.OptionalDouble.of(state.lineWidth()==null ? 1 : state.lineWidth())));
        RenderType type=create("rtmu_legacy_state",DefaultVertexFormat.NEW_ENTITY,key.mode,1536,true,false,pipeline.createCompositeState(true));
        return key.points ? new LegacyPointRenderType(type,state.pointSize()==null?1F:state.pointSize()) : type;
    }

    static ShaderStateShard shaderPackProgram(boolean blend,boolean depthWrite,boolean lighting) {
        return blend && !depthWrite ? RENDERTYPE_ENTITY_SOLID_SHADER : RENDERTYPE_ENTITY_CUTOUT_SHADER;
    }

    private static DepthTestStateShard legacyDepth(int function) {
        return new DepthTestStateShard("legacy_depth",function) {
            @Override public void setupRenderState() { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(function); }
            @Override public void clearRenderState() { RenderSystem.disableDepthTest(); RenderSystem.depthFunc(515); }
        };
    }

    private static ShaderInstance configuredShader(LegacyRenderState.Snapshot state) {
        if(shader==null) throw new IllegalStateException("Legacy script shader has not been registered");
        shader.safeGetUniform("LegacyUvOffset").set(state.textureU(),state.textureV());
        shader.safeGetUniform("LegacyLighting").set(state.enabled(0x0B50,true) ? 1:0);
        shader.safeGetUniform("LegacyTexture").set(state.enabled(0x0DE1,true) ? 1:0);
        shader.safeGetUniform("LegacyFog").set(state.enabled(0x0B60,true) ? 1:0);
        shader.safeGetUniform("LegacyFlat").set(state.shadeModel()!=null && state.shadeModel()==7424 ? 1:0);
        shader.safeGetUniform("LegacyAlphaFunction").set(!state.enabled(0x0BC0,true) ? 519
                : state.alphaFunction()==null ? 516 : state.alphaFunction());
        shader.safeGetUniform("LegacyAlphaReference").set(state.alphaReference()==null ? 0.1F : state.alphaReference());
        return shader;
    }
}
