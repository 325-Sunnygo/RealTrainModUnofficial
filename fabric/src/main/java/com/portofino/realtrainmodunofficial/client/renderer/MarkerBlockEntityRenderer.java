package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.ngt.rtm.RTMConfig;
import jp.ngt.rtm.rail.BlockMarker;
import jp.ngt.rtm.rail.TileEntityMarker;
import jp.ngt.rtm.rail.util.MarkerState;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 本家 RenderMarkerBlock(Base) の忠実移植 (1.21 BER 版)。
 * - renderDistanceMark: マーカー単体で向き方向に 10m 毎の目盛り (直進+左右45°の3方向、
 *   色付き四角 + "10m".."100m" テキスト)。設置した瞬間に表示される。
 * - renderLine: プレビュー確立後、各 RailMap に沿ったライン + 中央に総延長表示。
 */
public class MarkerBlockEntityRenderer implements BlockEntityRenderer<TileEntityMarker> {
    private final Font font;

    public MarkerBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(TileEntityMarker marker, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = marker.getBlockState();
        if (!(state.getBlock() instanceof BlockMarker markerBlock)) {
            return;
        }

        //レンチの「アンカー移動」モード中のみアンカー線を表示し、線を右クリックで掴んで編集
        //(coreMarker はクライアントで null のことがあるため RailMaps の有無で判定)
        if (marker.getRailMaps() != null && marker.getRailMaps().length > 0 && marker.getMarkerRP() != null) {
            if (isAnchorWrenchHeld()) {
                this.changeAnchor(marker);
                this.updateHover(marker);
                this.renderAnchor(marker, poseStack, buffer);
            } else if (editingMarker == marker) {
                //モードを離れたら編集中断
                marker.editMode = 0;
                editingMarker = null;
                TileEntityMarker.clientEditingMarker = null;
            }
        }

        if (!marker.displayDistance || !marker.getState(MarkerState.DISTANCE)) {
            return;
        }

        this.renderDistanceMark(marker, markerBlock, state, poseStack, buffer);

        RailMap[] maps = marker.getRailMaps();
        if (maps != null && maps.length > 0) {
            this.renderLine(marker, maps, poseStack, buffer);
        }
    }

    //---- 本家 RenderMarkerBlock1122: アンカー編集 (線を右クリックで掴んで動かす) ----

    /**
     * 編集対象の要素 (ordinal = marker.editMode)。本家 1122 と同順。
     */
    public enum MarkerElement {
        NONE(0x000000),
        HORIZONTIAL(0x00FF20),
        VERTICAL(0xFF8800),
        CANT_EDGE(0xFF00FF),
        CANT_CENTER(0xFF00FF),
        HEIGHT(0xFF1E00);

        public final int color;

        MarkerElement(int color) {
            this.color = color;
        }
    }

    /**
     * 現在編集中のマーカー (クライアント)
     */
    public static TileEntityMarker editingMarker;
    /**
     * 直近フレームでカーソルが乗っている線
     */
    public static TileEntityMarker hoveredMarker;
    public static MarkerElement hoveredElement = MarkerElement.NONE;
    private static long hoveredNanos;
    /**
     * ホバー中の線上最近点までの視点距離 (ブロック優先判定用)
     */
    private static double hoveredEyeDist = Double.MAX_VALUE;

    /**
     * 右クリック時に TrainControlKeyHandler から呼ばれる。
     * 本家 1122: 編集中なら確定 (サーバー送信)、線にカーソルが乗っていれば掴む。
     *
     * @return true = クリックを消費した
     */
    /**
     * レンチの「アンカー移動」モードを持っているか
     */
    public static boolean isAnchorWrenchHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        net.minecraft.world.item.ItemStack stack = mc.player.getMainHandItem();
        return stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.RtmWrenchItem
                && com.portofino.realtrainmodunofficial.item.RtmWrenchItem.getMode(stack)
                        == com.portofino.realtrainmodunofficial.item.RtmWrenchItem.MODE_ANCHOR;
    }

    public static boolean onRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        if (!isAnchorWrenchHeld()) {
            return false;
        }
        if (editingMarker != null) {
            //確定 → RailPosition をサーバーへ (本家 PacketMarkerRPClient)
            TileEntityMarker marker = editingMarker;
            marker.editMode = 0;
            editingMarker = null;
            TileEntityMarker.clientEditingMarker = null;
            if (!marker.isRemoved() && marker.getMarkerRP() != null) {
                sendAnchor(marker.getBlockPos(), marker.getMarkerRP());
                RailPosition opposite = getOppositeRailStatic(marker);
                if (opposite != null) {
                    sendAnchor(new net.minecraft.core.BlockPos(opposite.blockX, opposite.blockY, opposite.blockZ), opposite);
                }
            }
            return true;
        }
        //マーカーブロック自体を狙っている場合はブロック操作 (レンチのマーカー操作等) を優先。
        //その他のブロックは「線より手前」にある場合のみ優先 (背景の地面越しには掴める)。
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level != null) {
            if (mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof jp.ngt.rtm.rail.BlockMarker) {
                return false;
            }
            double blockDist = mc.player.getEyePosition().distanceTo(bhr.getLocation());
            if (blockDist < hoveredEyeDist - 0.5D) {
                return false;
            }
        }
        if (hoveredMarker != null && hoveredElement != MarkerElement.NONE
                && System.nanoTime() - hoveredNanos < 200_000_000L && !hoveredMarker.isRemoved()) {
            //掴む
            TileEntityMarker marker = hoveredMarker;
            marker.editMode = hoveredElement.ordinal();
            marker.startPlayerPitch = mc.player.getXRot();
            marker.startPlayerYaw = mc.player.getYHeadRot();
            marker.startMarkerHeight = marker.getMarkerRP() != null ? marker.getMarkerRP().height : 0;
            editingMarker = marker;
            TileEntityMarker.clientEditingMarker = marker;
            return true;
        }
        return false;
    }

    private static void sendAnchor(net.minecraft.core.BlockPos pos, RailPosition rp) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.portofino.realtrainmodunofficial.network.MarkerAnchorPayload(pos, rp.writeToNBT()));
    }

    /**
     * 本家 renderAnchorLine (isPickMode): カーソルが乗っている線をレイ-線分距離で判定
     */
    private void updateHover(TileEntityMarker marker) {
        if (editingMarker != null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RailPosition rp = marker.getMarkerRP();
        if (mc.player == null || rp == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0F);

        //ユーザー要望: 掴めるのは緑 (水平) の線のみ
        MarkerElement best = MarkerElement.NONE;
        double bestDist = Double.MAX_VALUE;
        double bestEyeDist = Double.MAX_VALUE;
        for (net.minecraft.world.phys.Vec3[] seg : anchorSegments(marker, rp, MarkerElement.HORIZONTIAL)) {
            double[] d = raySegmentDistance(eye, look, seg[0], seg[1]);
            if (d[0] < bestDist) {
                bestDist = d[0];
                bestEyeDist = d[1];
                best = MarkerElement.HORIZONTIAL;
            }
        }
        double threshold = 0.2D + bestEyeDist * 0.01D;
        if (best != MarkerElement.NONE && bestDist < threshold) {
            hoveredMarker = marker;
            hoveredElement = best;
            hoveredNanos = System.nanoTime();
            hoveredEyeDist = bestEyeDist;
        } else if (hoveredMarker == marker) {
            hoveredMarker = null;
            hoveredElement = MarkerElement.NONE;
            hoveredEyeDist = Double.MAX_VALUE;
        }
    }

    /**
     * 各アンカー線のワールド座標線分 (本家 renderAnchorLine の変換と一致させる)
     */
    private java.util.List<net.minecraft.world.phys.Vec3[]> anchorSegments(TileEntityMarker marker, RailPosition rp, MarkerElement elm) {
        java.util.List<net.minecraft.world.phys.Vec3[]> out = new java.util.ArrayList<>(2);
        net.minecraft.world.phys.Vec3 base = new net.minecraft.world.phys.Vec3(rp.posX, rp.posY, rp.posZ);
        float yawRad = rp.anchorYaw * Mth.DEG_TO_RAD;
        switch (elm) {
            case HEIGHT -> out.add(new net.minecraft.world.phys.Vec3[]{base, base.add(0.0D, 1.0D, 0.0D)});
            case HORIZONTIAL -> {
                float len = displayLen(rp);
                out.add(new net.minecraft.world.phys.Vec3[]{base,
                        base.add(Mth.sin(yawRad) * len, 0.0D, Mth.cos(yawRad) * len)});
            }
            case VERTICAL -> {
                float pitchRad = rp.anchorPitch * Mth.DEG_TO_RAD;
                float len = rp.anchorLengthVertical < 0.0F ? -5.0F : 5.0F;
                double h = Mth.cos(pitchRad) * len;
                out.add(new net.minecraft.world.phys.Vec3[]{base,
                        base.add(Mth.sin(yawRad) * h, Mth.sin(pitchRad) * len, Mth.cos(yawRad) * h)});
            }
            case CANT_EDGE -> {
                float cantRad = rp.cantEdge * Mth.DEG_TO_RAD;
                //R_Y(yaw) * R_Z(cant) * (±1,0,0)
                double lx = Mth.cos(cantRad);
                double ly = Mth.sin(cantRad);
                net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(
                        lx * Mth.cos(yawRad), ly, -lx * Mth.sin(yawRad));
                out.add(new net.minecraft.world.phys.Vec3[]{base, base.add(v)});
                out.add(new net.minecraft.world.phys.Vec3[]{base, base.subtract(v)});
            }
            case CANT_CENTER -> {
                net.minecraft.world.phys.Vec3[] mid = cantCenterSegmentBase(marker, rp);
                if (mid != null) {
                    out.add(new net.minecraft.world.phys.Vec3[]{mid[0], mid[0].add(mid[1])});
                    out.add(new net.minecraft.world.phys.Vec3[]{mid[0], mid[0].subtract(mid[1])});
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * カント(中央) 線: レール中央位置 {位置, ±方向ベクトル} (コアマーカー + 単一 RailMap のみ)
     */
    private net.minecraft.world.phys.Vec3[] cantCenterSegmentBase(TileEntityMarker marker, RailPosition rp) {
        if (marker.getRailMaps() == null || marker.getRailMaps().length != 1) {
            return null;
        }
        RailMap rm = marker.getRailMaps()[0];
        int max = (int) (rm.getLength() * 2.0F);
        if (max < 2) {
            return null;
        }
        int index = max / 2;
        double[] pos = rm.getRailPos(max, index);
        double h = rm.getRailHeight(max, index);
        float yawMid = rm.getRailYaw(max, index) * Mth.DEG_TO_RAD;
        float cantRad = rp.cantCenter * Mth.DEG_TO_RAD;
        double lx = Mth.cos(cantRad);
        double ly = Mth.sin(cantRad);
        return new net.minecraft.world.phys.Vec3[]{
                new net.minecraft.world.phys.Vec3(pos[1], h, pos[0]),
                new net.minecraft.world.phys.Vec3(lx * Mth.cos(yawMid), ly, -lx * Mth.sin(yawMid))};
    }

    /**
     * レイと線分の最短距離 {距離, 線分側最近点までの視点距離}
     */
    private static double[] raySegmentDistance(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 dir,
                                               net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
        net.minecraft.world.phys.Vec3 u = dir.normalize();
        net.minecraft.world.phys.Vec3 v = b.subtract(a);
        double vLen = v.length();
        if (vLen < 1.0e-6D) {
            return new double[]{distancePointToRay(origin, u, a), origin.distanceTo(a)};
        }
        net.minecraft.world.phys.Vec3 vn = v.scale(1.0D / vLen);
        net.minecraft.world.phys.Vec3 w0 = origin.subtract(a);
        double bb = u.dot(vn);
        double dd = u.dot(w0);
        double ee = vn.dot(w0);
        double denom = 1.0D - bb * bb;
        double s;
        double t;
        if (Math.abs(denom) < 1.0e-8D) {
            s = 0.0D;
            t = ee;
        } else {
            s = (bb * ee - dd) / denom;
            t = (ee - bb * dd) / denom;
        }
        s = Math.max(0.0D, Math.min(64.0D, s));
        t = Math.max(0.0D, Math.min(vLen, t));
        net.minecraft.world.phys.Vec3 p1 = origin.add(u.scale(s));
        net.minecraft.world.phys.Vec3 p2 = a.add(vn.scale(t));
        return new double[]{p1.distanceTo(p2), origin.distanceTo(p2)};
    }

    private static double distancePointToRay(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 u,
                                             net.minecraft.world.phys.Vec3 p) {
        double t = Math.max(0.0D, p.subtract(origin).dot(u));
        return origin.add(u.scale(t)).distanceTo(p);
    }

    /**
     * 本家 1122 changeAnchor: 掴んでいる線を毎フレーム視線に追従させる
     */
    private void changeAnchor(TileEntityMarker marker) {
        if (editingMarker != marker || marker.editMode == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        MarkerElement curElm = MarkerElement.values()[Math.min(marker.editMode, MarkerElement.values().length - 1)];
        RailPosition rp = marker.getMarkerRP();
        if (rp == null) {
            return;
        }
        float pitchDif = mc.player.getXRot() - marker.startPlayerPitch;

        if (curElm == MarkerElement.HEIGHT) {
            int height = marker.startMarkerHeight + (int) (-pitchDif / 1.0F);
            height = Mth.clamp(height, 0, 15);
            if (height != rp.height) {
                rp.height = (byte) height;
                rp.init();
                marker.onChangeRailShape();
            }
            return;
        }
        if (curElm == MarkerElement.CANT_EDGE) {
            float cantLimit = 80.0F;
            float cant = Mth.clamp(pitchDif, -cantLimit, cantLimit);
            RailPosition neighborRP = this.getNeighborRail(marker);
            if (neighborRP != null && marker.fitNeighbor) {
                cant = -neighborRP.cantEdge;
            }
            rp.cantEdge = cant;
            RailMap[] maps = marker.getRailMaps();
            if (maps != null && maps.length > 0) {
                RailMap map = maps[0];
                float cantCenter = (map.getStartRP().cantEdge + -map.getEndRP().cantEdge) * 0.5F;
                map.getStartRP().cantCenter = map.getEndRP().cantCenter =
                        cantCenter * ((rp.cantCenter == map.getStartRP().cantCenter) ? 1 : -1);
            }
            marker.onChangeRailShape();
            return;
        }
        if (curElm == MarkerElement.CANT_CENTER) {
            float cantLimit = 80.0F;
            float cantCenter = Mth.clamp(pitchDif, -cantLimit, cantLimit);
            RailMap[] maps = marker.getRailMaps();
            if (maps != null && maps.length > 0) {
                RailMap map = maps[0];
                map.getStartRP().cantCenter = map.getEndRP().cantCenter =
                        cantCenter * ((rp.cantCenter == map.getStartRP().cantCenter) ? 1 : -1);
                marker.onChangeRailShape();
            }
            return;
        }

        //水平/勾配: 視線先のブロックへ (本家: MOP 128m)
        net.minecraft.world.phys.HitResult target = mc.player.pick(128.0D, 0.0F, true);
        if (!(target instanceof net.minecraft.world.phys.BlockHitResult)) {
            return;
        }
        net.minecraft.world.phys.Vec3 targetVec = target.getLocation();
        boolean fitOpposite = false;
        RailPosition oppositeRP = getOppositeRailStatic(marker);
        if (oppositeRP != null) {
            double dx0 = targetVec.x - oppositeRP.posX;
            double dz0 = targetVec.z - oppositeRP.posZ;
            if (dx0 * dx0 + dz0 * dz0 <= 4.0D) {
                targetVec = new net.minecraft.world.phys.Vec3(oppositeRP.posX, oppositeRP.posY, oppositeRP.posZ);
                fitOpposite = true;
            }
        }
        //ANCHOR21: 制御長を 2/3 に
        if (marker.getState(MarkerState.ANCHOR21)) {
            double d0 = 2.0D / 3.0D;
            targetVec = new net.minecraft.world.phys.Vec3(
                    (targetVec.x - rp.posX) * d0 + rp.posX,
                    (targetVec.y - rp.posY) * d0 + rp.posY,
                    (targetVec.z - rp.posZ) * d0 + rp.posZ);
        }
        double dx = targetVec.x - rp.posX;
        double dz = targetVec.z - rp.posZ;
        if (dx != 0.0D && dz != 0.0D) {
            RailPosition neighborRP = this.getNeighborRail(marker);
            float dirRad = (float) Math.atan2(dx, dz);
            float length = (float) (dx / Math.sin(dirRad));
            float yaw = (float) Math.toDegrees(dirRad);
            if (curElm == MarkerElement.HORIZONTIAL) {
                if (neighborRP != null && marker.fitNeighbor) {
                    yaw = Mth.wrapDegrees(neighborRP.anchorYaw + 180.0F);
                }
                rp.anchorYaw = yaw;
                rp.anchorLengthHorizontal = length;
                rp.anchorManual = true;
            } else if (curElm == MarkerElement.VERTICAL) {
                float pitch = Mth.wrapDegrees(yaw - rp.anchorYaw);
                if (neighborRP != null && marker.fitNeighbor) {
                    pitch = -neighborRP.anchorPitch;
                } else if (fitOpposite) {
                    double dy = targetVec.y - rp.posY;
                    pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                }
                rp.anchorPitch = pitch;
                rp.anchorLengthVertical = length;
            }
            marker.onChangeRailShape();
        }
    }

    /**
     * 本家 getOppositeRail: プレビューの反対側マーカーの RP
     */
    private static RailPosition getOppositeRailStatic(TileEntityMarker marker) {
        if (marker.getRailMaps() == null) {
            return null;
        }
        RailPosition rp = marker.getMarkerRP();
        for (RailMap map : marker.getRailMaps()) {
            if (map.getStartRP().equals(rp)) {
                return map.getEndRP();
            } else if (map.getEndRP().equals(rp)) {
                return map.getStartRP();
            }
        }
        return null;
    }

    /**
     * 本家 getNeighborRail: マーカー隣接位置の既設レールの最寄り RP (接続スナップ用)
     */
    private RailPosition getNeighborRail(TileEntityMarker marker) {
        RailPosition markerRP = marker.getMarkerRP();
        if (markerRP == null || marker.getLevel() == null) {
            return null;
        }
        int[] pos = markerRP.getNeighborPos();
        var tile = marker.getLevel().getBlockEntity(new net.minecraft.core.BlockPos(pos[0], pos[1], pos[2]));
        if (!(tile instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase railBase)) {
            return null;
        }
        jp.ngt.rtm.rail.TileEntityLargeRailCore core = railBase.getRailCore();
        if (core == null || core.getAllRailMaps() == null) {
            return null;
        }
        double distanceSq = Double.MAX_VALUE;
        RailPosition rp = null;
        for (RailMap map : core.getAllRailMaps()) {
            double d2 = sq(markerRP.posX - map.getStartRP().posX) + sq(markerRP.posZ - map.getStartRP().posZ);
            if (d2 < distanceSq) {
                distanceSq = d2;
                rp = map.getStartRP();
            }
            d2 = sq(markerRP.posX - map.getEndRP().posX) + sq(markerRP.posZ - map.getEndRP().posZ);
            if (d2 < distanceSq) {
                distanceSq = d2;
                rp = map.getEndRP();
            }
        }
        return rp;
    }

    private static double sq(double d) {
        return d * d;
    }

    /**
     * アンカー線の表示長: 未編集 (自動制御長) は 5 ブロック固定
     * (制御長そのままだとプレビュー線と重なって見分けがつかないため)、
     * 手動編集済み (anchorManual) は実際の制御長 — 3〜4 ブロック等の短いアンカーも見た通りに表示する。
     */
    private static float displayLen(RailPosition rp) {
        if (rp.anchorManual && rp.anchorLengthHorizontal > 0.0F) {
            return rp.anchorLengthHorizontal;
        }
        return rp.anchorLengthHorizontal < 0.0F ? -5.0F : 5.0F;
    }

    /**
     * 太いライン描画用 RenderType (バニラ lines() の線幅 5px 版)
     */
    private static final class AnchorRenderTypes extends RenderType {
        private AnchorRenderTypes(String name, com.mojang.blaze3d.vertex.VertexFormat format,
                                  com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int bufferSize,
                                  boolean affectsCrumbling, boolean sortOnUpload,
                                  Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        static final RenderType THICK_LINES = create("rtmu_anchor_lines",
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, 1536, false, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(java.util.OptionalDouble.of(5.0D)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * アンカー線の描画 — ユーザー要望で緑 (水平) のみ。
     * 通常は 5 ブロック固定、掴んでいる間は実際の制御長まで伸びる (本家同様)。
     */
    private void renderAnchor(TileEntityMarker marker, PoseStack poseStack, MultiBufferSource buffer) {
        RailPosition rp = marker.getMarkerRP();
        if (rp == null) {
            return;
        }
        boolean editing = editingMarker == marker && marker.editMode == MarkerElement.HORIZONTIAL.ordinal();
        boolean active = editing || (hoveredMarker == marker && hoveredElement == MarkerElement.HORIZONTIAL);

        VertexConsumer lines = buffer.getBuffer(AnchorRenderTypes.THICK_LINES);

        double ox = rp.posX - marker.getBlockPos().getX();
        double oy = rp.posY - marker.getBlockPos().getY();
        double oz = rp.posZ - marker.getBlockPos().getZ();

        //掴んでいる間は制御長そのまま (視線先まで伸びる)、通常は displayLen (未編集=5固定/編集済み=実長)
        float len = editing ? rp.anchorLengthHorizontal : displayLen(rp);

        poseStack.pushPose();
        poseStack.translate(ox, oy, oz);
        poseStack.mulPose(new Quaternionf().rotationY(rp.anchorYaw * Mth.DEG_TO_RAD));
        drawLine(lines, poseStack, 0, 0, 0, 0, 0, len,
                colorOf(MarkerElement.HORIZONTIAL, active ? MarkerElement.HORIZONTIAL : MarkerElement.NONE));
        poseStack.popPose();
    }

    private static int colorOf(MarkerElement elm, MarkerElement active) {
        if (elm == active) {
            //本家: ColorUtil.multiplicating — 減光でハイライト
            int r = (int) (((elm.color >> 16) & 0xFF) * 0.75F);
            int g = (int) (((elm.color >> 8) & 0xFF) * 0.75F);
            int b = (int) ((elm.color & 0xFF) * 0.75F);
            return (r << 16) | (g << 8) | b;
        }
        return elm.color;
    }

    private static void drawLine(VertexConsumer lines, PoseStack poseStack,
                                 float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        //LINES (POSITION_COLOR_NORMAL): 法線 = 線の方向 (lines シェーダが太さ展開に使用)
        lines.addVertex(m, x0, y0, z0).setColor(r, g, b, 1.0F).setNormal(pose, dx, dy, dz);
        lines.addVertex(m, x1, y1, z1).setColor(r, g, b, 1.0F).setNormal(pose, dx, dy, dz);
    }

    private void renderDistanceMark(TileEntityMarker marker, BlockMarker block, BlockState state,
                                    PoseStack poseStack, MultiBufferSource buffer) {
        int color = block.markerType == 1 ? 0x0000FF : (block.markerType == 0 ? 0xFF0000 : 0xEC008C);
        float dir = BlockMarker.getMarkerDir(state) * 45.0F;
        int count = RTMConfig.markerDisplayDistance / 10;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0625D, 0.5D);
        poseStack.mulPose(new Quaternionf().rotationY(dir * Mth.DEG_TO_RAD));

        //目盛り四角 (本家: GL_QUADS サイズ0.4)。debugQuads (position_color)
        VertexConsumer quads = buffer.getBuffer(RenderType.debugQuads());
        Matrix4f m = poseStack.last().pose();
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float size = 0.4F;
        for (int i = 1; i < count; i++) {
            float moveZ = i * 10.0F;
            for (int k = -1; k <= 1; k++) {
                float moveX = moveZ * k;
                quads.addVertex(m, -size + moveX, 0.01F, size + moveZ).setColor(r, g, b, 1.0F);
                quads.addVertex(m, -size + moveX, 0.01F, -size + moveZ).setColor(r, g, b, 1.0F);
                quads.addVertex(m, size + moveX, 0.01F, -size + moveZ).setColor(r, g, b, 1.0F);
                quads.addVertex(m, size + moveX, 0.01F, size + moveZ).setColor(r, g, b, 1.0F);
            }
        }

        //距離テキスト "10m".."100m"
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        for (int j = 0; j < count; j++) {
            float moveZ = (j + 1) * 10.0F;
            String s = (j + 1) * 10 + "m";
            for (int k = -1; k <= 1; k++) {
                float moveX = moveZ * k;
                poseStack.pushPose();
                poseStack.translate(moveX, 2.5F, moveZ);
                //親の向き回転を打ち消してからカメラビルボード (バニラのネームタグ方式)
                poseStack.mulPose(new Quaternionf().rotationY(-dir * Mth.DEG_TO_RAD));
                poseStack.mulPose(cameraRot);
                poseStack.scale(0.25F, -0.25F, 0.25F);
                Matrix4f tm = poseStack.last().pose();
                float x = -this.font.width(s) / 2.0F;
                this.font.drawInBatch(s, x, 0.0F, color | 0xFF000000, false, tm, buffer,
                        Font.DisplayMode.NORMAL, 0, 0xF000F0);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    /**
     * 本家 renderLine: RailMap に沿ったライン + 中央に総延長 (m)。
     */
    private void renderLine(TileEntityMarker marker, RailMap[] maps, PoseStack poseStack, MultiBufferSource buffer) {
        RailPosition rp = marker.getMarkerRP();
        if (rp == null) {
            return;
        }
        float baseX = (float) (rp.posX - marker.getBlockPos().getX());
        float baseY = (float) (rp.posY - marker.getBlockPos().getY());
        float baseZ = (float) (rp.posZ - marker.getBlockPos().getZ());

        //緑のプレビュー曲線はユーザー要望で非表示 (マーカー設置直後から出て邪魔なため)。
        //アンカー編集モード中のみ描画してカーブ形状の確認に使う。
        if (isAnchorWrenchHeld()) {
            VertexConsumer lines = buffer.getBuffer(RenderType.debugLineStrip(2.0D));
            for (RailMap rm : maps) {
                if (rm == null) continue;
                poseStack.pushPose();
                poseStack.translate(baseX, baseY, baseZ);
                float x0 = (float) (rm.getStartRP().posX - rp.posX);
                float y0 = (float) (rm.getStartRP().posY - rp.posY);
                float z0 = (float) (rm.getStartRP().posZ - rp.posZ);
                poseStack.translate(x0, y0, z0);
                Matrix4f m = poseStack.last().pose();
                int max = (int) ((float) rm.getLength() * 2.0F);
                if (max < 1) max = 1;
                double[] p2 = rm.getRailPos(max, 0);
                double h2 = rm.getRailHeight(max, 0);
                for (int i = 0; i < max + 1; ++i) {
                    double[] p1 = rm.getRailPos(max, i);
                    lines.addVertex(m, (float) (p1[1] - p2[1]), (float) (rm.getRailHeight(max, i) - h2), (float) (p1[0] - p2[0]))
                            .setColor(0.0F, 0.75F, 0.0F, 1.0F);
                }
                poseStack.popPose();
            }
        }

        //総延長テキスト (本家: 中央、緑 0x00EE00)
        Quaternionf cameraRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        for (RailMap rm : maps) {
            if (rm == null) continue;
            poseStack.pushPose();
            poseStack.translate(baseX, baseY, baseZ);
            int split = (int) (rm.getLength() * 4.0D);
            if (split < 2) split = 2;
            double[] pos = rm.getRailPos(split, split / 2);
            float x0 = (float) (pos[1] - rp.posX);
            float y0 = (float) ((rm.getStartRP().posY + rm.getEndRP().posY) / 2 - rp.posY);
            float z0 = (float) (pos[0] - rp.posZ);
            poseStack.translate(x0, y0 + 3.0F, z0);
            poseStack.mulPose(cameraRot);
            //本家は 0.05 だが視認性向上のため少し大きめ + "m" 付き (バニラのネームタグ方式)
            poseStack.scale(0.1F, -0.1F, 0.1F);
            Matrix4f tm = poseStack.last().pose();
            String s = String.format("%.2fm", rm.getLength());
            float x = -this.font.width(s) / 2.0F;
            this.font.drawInBatch(s, x, 0.0F, 0xFF00EE00, false, tm, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
            poseStack.popPose();
        }
    }

    @Override
    public int getViewDistance() {
        return Math.max(128, (int) RTMConfig.markerDisplayDistance + 32);
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityMarker marker) {
        return true;
    }

    public AABB getRenderBoundingBox(TileEntityMarker marker) {
        return jp.ngt.mccompat.AABBCompat.INFINITE;
    }
}
