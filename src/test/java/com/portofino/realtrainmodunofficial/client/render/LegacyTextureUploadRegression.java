package com.portofino.realtrainmodunofficial.client.render;

import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Actual Minecraft PNG decode/upload, independently checked against ImageIO and GPU readback. */
public final class LegacyTextureUploadRegression {
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        java.util.List<net.minecraft.client.renderer.ShaderInstance> programs=new java.util.ArrayList<>();
        if(!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        long window=0;
        try(var zip=new ZipFile(args[0])) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,GLFW.GLFW_TRUE);
            window=GLFW.glfwCreateWindow(16,16,"Native texture readback",0,0);
            if(window==0) throw new AssertionError("GL context unavailable");
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities(); RenderSystem.initRenderThread();
            var pack=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(
                    LegacyTextureUploadRegression.class.getClassLoader(),new Class<?>[]{net.minecraft.server.packs.PackResources.class},
                    (proxy,method,values)->method.getName().equals("packId")?"regression":method.getReturnType()==boolean.class?false:null);
            net.minecraft.server.packs.resources.ResourceProvider provider=location -> {
                String path="/assets/"+location.getNamespace()+"/"+location.getPath();
                var url=LegacyTextureUploadRegression.class.getResource(path);
                return url==null?java.util.Optional.empty():java.util.Optional.of(new net.minecraft.server.packs.resources.Resource(pack,url::openStream));
            };
            for(String shaderName:new String[]{"legacy_entity","rtm_dof","rtm_accum","rtm_blit"}) {
                var format=shaderName.equals("legacy_entity")?com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY:com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX;
                var shader=new net.minecraft.client.renderer.ShaderInstance(provider,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial",shaderName),format);
                programs.add(shader);
                if(!shaderName.equals("legacy_entity")) {
                    String fieldName=shaderName.equals("rtm_dof")?"dofShader":shaderName.equals("rtm_accum")?"accumShader":"blitShader";
                    var field=com.portofino.realtrainmodunofficial.client.camera.CameraPostProcessor.class.getDeclaredField(fieldName);
                    field.setAccessible(true);field.set(null,shader);
                }
                if(shaderName.equals("rtm_dof")) shader.safeGetUniform("Weights").set(com.portofino.realtrainmodunofficial.client.camera.CameraState.gaussian(12));
            }
            for(String[] vanilla:new String[][]{{"rendertype_entity_solid","rendertypeEntitySolidShader"},{"rendertype_entity_cutout","rendertypeEntityCutoutShader"},{"rendertype_entity_translucent_emissive","rendertypeEntityTranslucentEmissiveShader"}}) {
                var shader=new net.minecraft.client.renderer.ShaderInstance(provider,vanilla[0],com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);programs.add(shader);
                var field=net.minecraft.client.renderer.GameRenderer.class.getDeclaredField(vanilla[1]);field.setAccessible(true);field.set(null,shader);
            }
            System.out.println("Actual Minecraft ShaderInstance initialization: seven render/camera/fallback programs passed");
            CameraFramebufferRegression.run();
            LegacyMatrixPlaybackRegression.run();
            LegacyProductionBlendRegression.run(programs.get(0));
            for(boolean blur:new boolean[]{false,true}) for(boolean clamp:new boolean[]{false,true}) {
                String json="{\"texture\":{\"blur\":"+blur+",\"clamp\":"+clamp+"}}";
                var sampling=LegacyTextureSampling.read(new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                try(var texture=new DynamicTexture(new NativeImage(2,2,false))) {
                    sampling.apply(texture);
                    int filter=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER);
                    int wrap=GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S);
                    if(filter!=(blur?GL11.GL_LINEAR:GL11.GL_NEAREST) || wrap!=(clamp?org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE:GL11.GL_REPEAT)) throw new AssertionError("Legacy texture metadata sampler mismatch");
                }
            }
            System.out.println("Actual texture metadata sampler states: 4 passed");
            String name="assets/minecraft/textures/train/T-ONREC/E131/parts.png";
            java.awt.image.BufferedImage reference;
            try(var input=zip.getInputStream(zip.getEntry(name))) { reference=ImageIO.read(input); }
            NativeImage image;
            try(var input=zip.getInputStream(zip.getEntry(name))) { image=NativeImage.read(input); }
            int width=image.getWidth(),height=image.getHeight();
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                int abgr=image.getPixelRGBA(x,y),argb=reference.getRGB(x,y);
                if((abgr>>>24)!=(argb>>>24)) throw new AssertionError("PNG alpha decode changed at "+x+","+y);
            }
            try(var texture=new DynamicTexture(image)) {
                RenderSystem.bindTexture(texture.getId());
                var data=BufferUtils.createByteBuffer(width*height*4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
                for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                    int offset=(y*width+x)*4,argb=reference.getRGB(x,y);
                    int rgba=(data.get(offset+3)&255)<<24 | (data.get(offset)&255)<<16
                            | (data.get(offset+1)&255)<<8 | data.get(offset+2)&255;
                    if(rgba!=argb) throw new AssertionError("GPU texture changed at "+x+","+y+": "+Integer.toHexString(argb)+" -> "+Integer.toHexString(rgba));
                }
                System.out.println("Actual PNG decode and DynamicTexture GPU upload: "+width*height+" RGBA pixels identical");
            }
        } catch(Throwable failure) {
            if(!System.getProperty("rtmu.e259Pack","").isBlank()){failure.printStackTrace();System.exit(1);}throw failure;
        } finally {
            // ImageIO's GIF/AWT path starts AppKit; GLFW teardown then deadlocks on macOS.
            // The isolated test JVM releases its native window on process termination.
            if(!System.getProperty("rtmu.e259Pack","").isBlank())System.exit(0);
            for(var program:programs) program.close();if(window!=0) GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
}
