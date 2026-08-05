package jp.ngt.rtm.block.decoration;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 装飾ブロックのモデル置き場。本家 {@code jp.ngt.rtm.block.decoration.DecorationStore} の移植。
 *
 * <p>本家と同じくワールドフォルダの {@code ngt/rtm/decoration/*.json} に保存する。
 * サーバーが登録・保存し、全クライアントへ json を配って {@link #setModel} で反映する
 * (本家は PacketNotice、RTMU は DecorationSyncPayload)。
 */
public final class DecorationStore {
    public static final DecorationStore INSTANCE = new DecorationStore();

    public static final String FILE_SUFFIX = ".json";
    public static final String SAVE_DIR = "ngt/rtm/decoration";

    /** クライアント側 (描画・GUI が読む)。 */
    private final Map<String, DecorationModel> modelMap = new HashMap<>();
    /** サーバー側。 */
    private final Map<String, DecorationModel> serverModelMap = new HashMap<>();

    private DecorationStore() {
    }

    /** S2C 同期パケットから。 */
    public void setModel(String json) {
        try {
            DecorationModel model = DecorationModel.fromJson(json);
            if (model != null && model.name != null) {
                this.modelMap.put(model.name, model);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** C2S 登録パケットから (GUI の save)。 */
    public void registerModel(String json, MinecraftServer server) {
        try {
            DecorationModel model = DecorationModel.fromJson(json);
            if (model != null && model.name != null) {
                this.registerModel(model, server);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerModel(DecorationModel model, MinecraftServer server) {
        this.serverModelMap.put(model.name, model);
        this.saveModel(model, server);
        //全クライアントへ配布
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
            new com.portofino.realtrainmodunofficial.network.DecorationSyncPayload(model.toJson()));
    }

    public void saveModel(DecorationModel model, MinecraftServer server) {
        try {
            Path folder = this.getSaveFolder(server);
            Files.writeString(folder.resolve(model.name + FILE_SUFFIX), model.toJson());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** サーバー起動時。ワールドフォルダから全モデルを読む。 */
    public void loadModels(MinecraftServer server) {
        this.serverModelMap.clear();
        try {
            Path folder = this.getSaveFolder(server);
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX)).forEach(p -> {
                    try {
                        DecorationModel model = DecorationModel.fromJson(Files.readString(p));
                        if (model != null && model.name != null) {
                            this.serverModelMap.put(model.name, model);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** ログイン時: 手持ちの全モデルをこのプレイヤーへ送る。 */
    public void syncAllTo(net.minecraft.server.level.ServerPlayer player) {
        for (DecorationModel model : this.serverModelMap.values()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.portofino.realtrainmodunofficial.network.DecorationSyncPayload(model.toJson()));
        }
    }

    public Path getSaveFolder(MinecraftServer server) throws IOException {
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve(SAVE_DIR);
        Files.createDirectories(folder);
        return folder;
    }

    /** クライアント参照。無ければ既定モデル (本家と同じ)。 */
    public DecorationModel getModel(String name) {
        DecorationModel model = this.modelMap.get(name);
        return model != null ? model : DecorationModel.DEFAULT_MODEL;
    }

    public List<DecorationModel> getModels() {
        List<DecorationModel> list = new ArrayList<>(this.modelMap.values());
        list.sort(Comparator.comparing(m -> m.name));
        return list;
    }
}
