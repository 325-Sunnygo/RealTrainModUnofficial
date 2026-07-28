package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 既存の描画コードが「実際に吐いた頂点」をそのまま捕まえて、1 つのメッシュ (VBO) に統合する。
 *
 * 本家 RTM 1.7.10 はレール 1 本をディスプレイリストに 1 回焼いて GPU に置きっぱなしにしていた。
 * 1.21 移植ではそれが「毎フレーム、0.5m 刻みの位置ごとにモデルを描画呼び出し」になっており、
 * 50m のレール 1 本で 900 回以上の draw call (setupRenderState + ユニフォーム転送 + bind/draw)
 * を毎フレーム発行していた (実測 2.83ms/本)。ここで 1 本 = 1〜2 draw call に統合する。
 *
 * 重要: 頂点の中身 (座標・法線・UV・色・ライト・RenderType の選択) は既存の描画コードに
 * そのまま生成させ、ここでは受け取るだけ。判断ロジックを複製しないので見た目は変わらない。
 */
public final class MeshCapture {

    private MeshCapture() {
    }

    /**
     * 捕まえた頂点を RenderType ごとに貯める MultiBufferSource。
     */
    public static final class Source implements MultiBufferSource {
        private final Map<RenderType, Sink> sinks = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return sinks.computeIfAbsent(renderType, t -> new Sink());
        }

        public long totalVertices() {
            long total = 0L;
            for (Sink sink : sinks.values()) {
                total += sink.count;
            }
            return total;
        }

        /**
         * 貯めた頂点を RenderType ごとに VBO へアップロードする (描画スレッドで呼ぶこと)。
         */
        public List<Section> upload() {
            List<Section> sections = new ArrayList<>();
            for (Map.Entry<RenderType, Sink> entry : sinks.entrySet()) {
                RenderType type = entry.getKey();
                Sink sink = entry.getValue();
                if (sink.count <= 0) {
                    continue;
                }
                //RenderType 自身の format/mode で BufferBuilder を作る。
                //シェーダー MOD (Iris) は RenderType の頂点フォーマットを拡張し、
                //BufferBuilder 経由で接線などを補完するため、この経路なら追従できる。
                BufferBuilder builder = Tesselator.getInstance().begin(type.mode(), type.format());
                for (int i = 0; i < sink.count; i++) {
                    int f = i * 8;
                    int m = i * 3;
                    int color = sink.meta[m];
                    int light = sink.meta[m + 1];
                    int overlay = sink.meta[m + 2];
                    builder.addVertex(sink.data[f], sink.data[f + 1], sink.data[f + 2])
                        .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                        .setUv(sink.data[f + 3], sink.data[f + 4])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(sink.data[f + 5], sink.data[f + 6], sink.data[f + 7]);
                }
                MeshData mesh = builder.build();
                if (mesh == null) {
                    continue;
                }
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(mesh);
                VertexBuffer.unbind();
                sections.add(new Section(type, vbo, sink.count));
            }
            return sections;
        }
    }

    /**
     * 1 つの RenderType 分の統合済みメッシュ。
     */
    public record Section(RenderType renderType, VertexBuffer vbo, int vertexCount) {
        public void close() {
            if (vbo != null && !vbo.isInvalid()) {
                vbo.close();
            }
        }
    }

    /**
     * VertexConsumer として呼ばれた内容をそのまま配列に貯める。
     * 呼び出し側は addVertex(...).setColor(...).setUv(...).setOverlay(...).setLight(...).setNormal(...)
     * の順で使う (バニラの標準的な並び)。setOverlay/setLight は既定実装で setUv1/setUv2 に落ちる。
     */
    private static final class Sink implements VertexConsumer {
        //x,y,z,u,v,nx,ny,nz
        private float[] data = new float[8 * 4096];
        //color(ARGB), light, overlay
        private int[] meta = new int[3 * 4096];
        private int count;
        private int cur = -1;

        private void ensureCapacity() {
            if ((count + 1) * 8 > data.length) {
                float[] nd = new float[data.length * 2];
                System.arraycopy(data, 0, nd, 0, data.length);
                data = nd;
                int[] nm = new int[meta.length * 2];
                System.arraycopy(meta, 0, nm, 0, meta.length);
                meta = nm;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            ensureCapacity();
            cur = count++;
            int f = cur * 8;
            data[f] = x;
            data[f + 1] = y;
            data[f + 2] = z;
            //既定値 (呼ばれなかった場合の保険)
            int m = cur * 3;
            meta[m] = 0xFFFFFFFF;
            meta[m + 1] = 0;
            meta[m + 2] = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (cur >= 0) {
                meta[cur * 3] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (cur >= 0) {
                data[cur * 8 + 3] = u;
                data[cur * 8 + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            //オーバーレイ (setOverlay の既定実装から来る)
            if (cur >= 0) {
                meta[cur * 3 + 2] = (v << 16) | (u & 0xFFFF);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            //ライト (setLight の既定実装から来る)
            if (cur >= 0) {
                meta[cur * 3 + 1] = (v << 16) | (u & 0xFFFF);
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (cur >= 0) {
                data[cur * 8 + 5] = x;
                data[cur * 8 + 6] = y;
                data[cur * 8 + 7] = z;
            }
            return this;
        }
    }
}
