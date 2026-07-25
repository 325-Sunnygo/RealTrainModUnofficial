package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.block.MarkerBlock;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import com.portofino.realtrainmodunofficial.item.RailItem;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * SuperRailBuilder3 の 1.12.2 RTM レール API を RTMU のネイティブ敷設へ橋渡しする。
 *
 * <p>スクリプト本体は改変できないため、{@code createRailPosition}/{@code getPlayerRail}/
 * {@code buildNormalRail}/{@code buildBranchRail}/{@code deleteRail} の各関数を後注入の JS で
 * このブリッジ呼び出しへ差し替える(TrainScriptSystem)。GUI・制御フロー・render はそのまま。</p>
 */
public final class SrbRailBridge {

    /**
     * SRB が敷設に使うレールマップの版。
     * <p>SRB は非 fixRTM 環境では {@code new RailMapBasic(start, end)} = <b>版 0</b> で敷く。
     * 版 1 以上には「アンカーの向きが dir から導かれる値と違えば必ずベジェにする」条件が入り、
     * SRB 独自のアンカー規約と噛み合わない。マーカー用の版 (現行) を渡していたため、
     * 直線 61 ブロックの区間が 83.75 に膨らみ、分岐のトング付近が潰れていた。
     */
    private static final int RAIL_MAP_VERSION = 0;

    /** SRB の createRailPosition 相当。data の各値から RTMU の RailPosition を生成する。 */
    public RailPosition createRailPosition(int blockX, int blockY, int blockZ, int markerDir,
                                           double switchType, double anchorLength, double anchorPitch,
                                           double anchorYaw, double cantCenter, double cantEdge, double height) {
        //★SRB が渡す値は<b>そのまま使う</b>。補正しない。
        //以前はアンカー長や向きを RTM の流儀へ「直して」いたが、それは敷設に使う
        //レールマップの版が違っていたのを辻褄合わせしていただけだった (下の RAIL_MAP_VERSION)。
        //版さえ合えば SRB の値は最初から正しく、補正するとかえって形が悪くなる
        //(実測: 直線区間 61.00 が正、補正ありだと分岐が 41.60 → 56.05 に伸びる)。
        RailPosition rp = new RailPosition(blockX, blockY, blockZ, markerDir, (int) switchType);
        rp.anchorLengthHorizontal = (float) anchorLength;
        rp.anchorLengthVertical = (float) anchorLength;
        rp.anchorPitch = (float) anchorPitch;
        rp.anchorYaw = (float) anchorYaw;
        rp.cantCenter = (float) cantCenter;
        rp.cantEdge = (float) cantEdge;
        rp.setHeight((byte) (int) height);
        rp.init();
        return rp;
    }

    /** SRB の buildNormalRail 相当(2点間に通常レール)。 */
    public boolean buildNormalRail(Object world, RailPosition start, RailPosition end, Object modelId) {
        Level level = toLevel(world);
        if (level == null || start == null || end == null) {
            return false;
        }
        List<RailPosition> rps = new ArrayList<>(2);
        rps.add(start);
        rps.add(end);
        boolean ok = buildRailFaithful(level, rps, toModelId(modelId));
        if (!ok) {
            notifyFailure(level, "レールを敷けませんでした (2点間)。設置場所を変えてみてください。");
        }
        return ok;
    }

    /**
     * jp.ngt.rtm.rail (Phase 1 本家忠実システム) でレールを構築する。
     * 本家 BlockMarker.createRail 相当。
     */
    private static boolean buildRailFaithful(Level level, List<RailPosition> rps, String modelId) {
        if (rps.isEmpty()) {
            return false;
        }
        jp.ngt.rtm.rail.util.RailProperty prop = new jp.ngt.rtm.rail.util.RailProperty(
            modelId == null ? "" : modelId, net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F);
        RailPosition first = rps.get(0);
        return jp.ngt.rtm.rail.BlockMarker.createRail(
            level, first.blockX, first.blockY, first.blockZ, rps, prop, true, true, RAIL_MAP_VERSION);
    }

    /** 直前に出した診断。同じ内容は繰り返さない (毎 tick 呼ばれるため)。 */
    private static String lastDebug = "";

    /** スクリプトから状態を 1 行出す。内容が変わった時だけログに残る。 */
    public void debug(String msg) {
        if (msg == null || msg.equals(lastDebug)) {
            return;
        }
        lastDebug = msg;
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info("[SRB] {}", msg);
    }

    /** SRB スクリプトが敷設失敗を通知するのに使う。 */
    public void logError(String msg) {
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("[SRB] {}", msg);
    }

    /**
     * 敷設に失敗したことをその場のプレイヤーへ伝える。
     * <p>SRB 自身のメッセージは「An error occurred while generating rails」だけで、
     * 何が起きたのか分からない。RTMU 側で分かっている理由はチャットにも出す。
     */
    private static void notifyFailure(Level level, String reason) {
        try {
            if (level != null && level.getServer() != null) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c[RTMU] " + reason), false);
            }
        } catch (Throwable ignored) {
            //通知に失敗しても敷設処理には影響させない
        }
    }

    /** SRB の buildBranchRail 相当(3点以上で分岐レール)。 */
    public boolean buildBranchRail(Object world, List<?> rpsRaw, Object modelId) {
        Level level = toLevel(world);
        if (level == null || rpsRaw == null || rpsRaw.size() < 2) {
            logError("buildBranchRail: level=" + (level != null) + " rps=" + (rpsRaw == null ? -1 : rpsRaw.size()));
            return false;
        }
        List<RailPosition> rps = new ArrayList<>(rpsRaw.size());
        for (Object o : rpsRaw) {
            if (o instanceof RailPosition rp) {
                rps.add(rp);
            }
        }
        if (rps.size() != rpsRaw.size()) {
            logError("buildBranchRail: RailPosition 変換に失敗 " + rps.size() + "/" + rpsRaw.size()
                + " first=" + (rpsRaw.isEmpty() ? "-" : rpsRaw.get(0).getClass().getName()));
        }
        boolean ok = buildRailFaithful(level, rps, toModelId(modelId));
        if (!ok) {
            StringBuilder sb = new StringBuilder("buildBranchRail 失敗 model=").append(toModelId(modelId)).append(" rps=");
            for (RailPosition rp : rps) {
                sb.append('(').append(rp.blockX).append(',').append(rp.blockY).append(',').append(rp.blockZ)
                  .append(" dir=").append(rp.direction).append(" sw=").append(rp.switchType).append(") ");
            }
            logError(sb.toString());
            notifyFailure(level, "分岐レールを敷けませんでした (地点 " + rps.size()
                + ")。分岐の角度・間隔が本家の許容範囲を超えている可能性があります。");
        }
        return ok;
    }

    /**
     * レールアイテムのモデル ID。
     * <p>★アイテム個別の選択が入っていなければ<b>選択中のレール</b>へ落とす
     * (マーカーで敷くときと同じ既定: {@code BlockMarker.getRailProperty})。
     * ここで空文字を返すと SRB 側は「レールを持っていない」と判断して敷設処理を
     * 丸ごと飛ばすため、レールを持っているのに何も建たなくなる。
     */
    private static String modelIdOf(ItemStack stack) {
        String id = LegacyItemStackBridge.getSelectedModelId(stack);
        if (id != null && !id.isBlank()) {
            return id;
        }
        com.portofino.realtrainmodunofficial.rail.RailDefinition def =
            com.portofino.realtrainmodunofficial.rail.RailRegistry.getSelected();
        return def != null ? def.getId() : "";
    }

    /** SRB の deleteRail 相当。(x,y,z) にレール(コア or 当たり判定)があれば撤去し true。 */
    public boolean deleteRail(Object world, int x, int y, int z) {
        Level level = toLevel(world);
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        // jp.ngt.rtm.rail (Phase 1 本家忠実システム): レールベース/コアどこを消しても breakRail が伝播
        if (block instanceof jp.ngt.rtm.rail.BlockLargeRailBase) {
            level.removeBlock(pos, false);
            return true;
        }
        if (block instanceof LargeRailCoreBlock) {
            level.removeBlock(pos, false); // onRemove が当たり判定ブロックも掃除する
            return true;
        }
        if (block instanceof RailCollisionBlock) {
            BlockPos corePos = null;
            if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity be) {
                corePos = be.getCorePos();
            }
            if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                level.removeBlock(corePos, false);
                return true;
            }
            level.removeBlock(pos, false);
            return true;
        }
        return false;
    }

    /** SRB の getPlayerRail 相当。プレイヤーが持つレールアイテムの選択モデルIDを返す(無ければ "")。 */
    public String heldRailModelId(Object playerObj) {
        //★スクリプトへ渡るプレイヤーは RTMU のラッパー (jp.ngt.mccompat.PlayerCompat) のことがある。
        //1.12 のフィールド名 (field_71071_by など) をスクリプトへ見せるための包みで、
        //生の Player ではない。ここで剥がさないと「レールを持っていない」と誤判定し、
        //SRB の敷設条件から外れて何も建たなくなる (Q も敷設ブロックの後ろなので効かなく見える)。
        Player player = jp.ngt.mccompat.PlayerCompat.unwrap(playerObj);
        if (player == null) {
            debug("手持ち判定: プレイヤーが取れない → "
                + (playerObj == null ? "null" : playerObj.getClass().getName()));
            return "";
        }
        ItemStack main = player.getMainHandItem();
        if (main != null && main.getItem() instanceof RailItem) {
            return modelIdOf(main);
        }
        ItemStack off = player.getOffhandItem();
        if (off != null && off.getItem() instanceof RailItem) {
            return modelIdOf(off);
        }
        return "";
    }

    /** NGTLog.sendChatMessage(player, msg) 相当。プレイヤーにシステムメッセージを送る。 */
    public void chat(Object playerObj, String msg) {
        if (playerObj instanceof Player p && msg != null) {
            try {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * (x,y,z) のレール関連ブロックをレールコア(LargeRailCoreBlockEntity)に解決して返す。
     *
     * <p>RTMU はレールコアを始点1ブロックにしか置かず、レール沿いは当たり判定(RailCollisionBlock)
     * /道床(BallastBlock)が並ぶ。SRB の接続検出 getAroundTileEntity は {@code instanceof
     * TileEntityLargeRailBase} でコアしか拾えないため、コアから離れた位置(レール終端側など)では
     * 接続が検出されず、接続マーカーが接線ロックされない(本家では端のどちらでも接続できる)。
     * そこで当たり判定/道床ブロックは getCorePos からコアを辿って返し、レール全長で接続検出を効かせる。
     * レール以外の BlockEntity(看板等)はそのまま返す。</p>
     */
    public Object railCoreAt(Object world, int x, int y, int z) {
        Level level = toLevel(world);
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        // jp.ngt.rtm.rail (Phase 1 本家忠実システム): ベースならコアへ解決
        if (be instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase newBase) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore newCore = newBase.getRailCore();
            return newCore != null ? newCore : be;
        }
        if (be instanceof com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity) {
            return be;
        }
        BlockPos corePos = null;
        if (be instanceof com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity rbe) {
            corePos = rbe.getCorePos();
        } else if (be instanceof com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity bbe) {
            corePos = bbe.getCorePos();
        }
        if (corePos != null
            && level.getBlockEntity(corePos) instanceof com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity core) {
            debugCore("collision->core", core);
            return core;
        }
        if (be instanceof com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity coreDirect) {
            debugCore("direct-core", coreDirect);
        }
        return be;
    }

    private static long lastCoreLog = 0L;

    /** [RTM-DBG] レール接続検証用: 解決したコアの RailPositions(anchorYaw/posX/Z) をスロットル出力。 */
    private static void debugCore(String tag, com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity core) {
        long now = System.currentTimeMillis();
        if (now - lastCoreLog < 1000L) {
            return;
        }
        lastCoreLog = now;
        try {
            RailPosition[] rps = core.getRailPositions();
            if (rps == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rps.length; i++) {
                RailPosition rp = rps[i];
                if (rp == null) { sb.append("[").append(i).append("]=null "); continue; }
                sb.append(String.format("[%d]pos(%.2f,%.2f,%.2f) yaw=%.1f pitch=%.1f dir=%d ",
                    i, rp.posX, rp.posY, rp.posZ, rp.anchorYaw, rp.anchorPitch, rp.direction));
            }
        } catch (Throwable t) {
        }
    }

    /** BlockEntity の座標を返す(SRB の getTileEntityPos が MCP 名 func_174877_v を使うため代替)。 */
    public int[] tilePos(Object tile) {
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            BlockPos p = be.getBlockPos();
            return new int[]{p.getX(), p.getY(), p.getZ()};
        }
        return new int[]{0, 0, 0};
    }

    private static Level toLevel(Object world) {
        if (world instanceof Level level) {
            return level;
        }
        if (world instanceof jp.ngt.mccompat.WorldCompat compat) {
            return compat.getLevel();
        }
        return null;
    }

    /**
     * SRB から渡された「レール」からモデル ID を取り出す。
     * <p>差し替え後の {@code getPlayerRail} は ID の文字列を返すが、差し替えが効いていない
     * 経路から本家の状態オブジェクトが来ることもあるので両方受ける。どちらでも読めなければ
     * <b>選択中のレール</b>へ落とす (マーカーで敷くときと同じ既定)。
     */
    private static String toModelId(Object modelId) {
        if (modelId instanceof CharSequence cs) {
            String s = cs.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        } else if (modelId instanceof jp.ngt.rtm.modelpack.state.ResourceState state) {
            String s = state.getResourceName();
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        com.portofino.realtrainmodunofficial.rail.RailDefinition def =
            com.portofino.realtrainmodunofficial.rail.RailRegistry.getSelected();
        return def != null ? def.getId() : null;
    }
}
