package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import jp.ngt.ngtlib.renderer.LegacyRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Shader-pack fallback for legacy alpha tests; the original PNG and normal rendering stay untouched. */
public final class IrisTexturePasses {
    private IrisTexturePasses() {}
    private record Key(ResourceLocation source,DynamicTexture texture,int function,float reference,float vertexAlpha) {}
    private static final Map<Key,ResourceLocation> MASKS=new HashMap<>();
    private static ResourceLocation white;
    public static ResourceLocation white() {
        if(white==null){white=ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial","shader_white");var image=new NativeImage(1,1,false);image.setPixelRGBA(0,0,-1);DynamicTextureRegistry.register(white,image);}return white;
    }
    public static boolean accepts(float alpha,int function,float reference) {
        return switch(function){case 512->false;case 513->alpha<reference;case 514->alpha==reference;
            case 515->alpha<=reference;case 516->alpha>reference;case 517->alpha!=reference;case 518->alpha>=reference;default->true;};
    }
    public static ResourceLocation select(ResourceLocation source,LegacyRenderState.Snapshot state,float vertexAlpha) {
        if(source==null||!state.enabled(0x0BC0,true)||state.alphaFunction()==null||state.alphaFunction()==519)return source;
        var manager=Minecraft.getInstance().getTextureManager();
        if(!(manager.getTexture(source) instanceof DynamicTexture dynamic)||dynamic.getPixels()==null)return source;
        var key=new Key(source,dynamic,state.alphaFunction(),state.alphaReference()==null?.1F:state.alphaReference(),vertexAlpha);
        return MASKS.computeIfAbsent(key,k->{
            NativeImage image=dynamic.getPixels();NativeImage masked=new NativeImage(image.getWidth(),image.getHeight(),false);boolean changed=false;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int pixel=image.getPixelRGBA(x,y);
                if(!accepts((pixel>>>24)/255F*vertexAlpha,k.function,k.reference)) {pixel&=0x00ffffff;changed=true;}
                masked.setPixelRGBA(x,y,pixel);
            }
            if(!changed){masked.close();return source;}
            var target=ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial","shader_alpha/"+java.util.UUID.randomUUID());
            DynamicTextureRegistry.register(target,masked,DynamicTextureRegistry.sampling(source));return target;
        });
    }
    public static void forget(ResourceLocation source) {
        var manager=Minecraft.getInstance().getTextureManager();var iterator=MASKS.entrySet().iterator();
        while(iterator.hasNext()) {var entry=iterator.next();if(entry.getKey().source.equals(source)) {
            if(!entry.getValue().equals(source)) {manager.release(entry.getValue());DynamicTextureRegistry.forget(entry.getValue());}
            iterator.remove();
        }}
    }
}
