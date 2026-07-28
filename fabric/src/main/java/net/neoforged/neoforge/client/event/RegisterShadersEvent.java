package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * シム: コアシェーダーの登録。
 * 1.21.1 のバニラにはコアシェーダーを mod から足す仕組みが無い。
 */
public class RegisterShadersEvent extends Event {

    /** 欲しいシェーダー 1 件。 */
    public record Declaration(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> sink) {
    }

    private final List<Declaration> declarations = new ArrayList<>();

    public void registerShader(ResourceLocation id, VertexFormat format, Consumer<ShaderInstance> sink) {
        this.declarations.add(new Declaration(id, format, sink));
    }

    /** エントリポイントが Fabric へ流すために読む。 */
    public List<Declaration> getDeclarations() {
        return this.declarations;
    }
}
