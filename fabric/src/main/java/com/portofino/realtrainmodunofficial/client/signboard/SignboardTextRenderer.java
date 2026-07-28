package com.portofino.realtrainmodunofficial.client.signboard;

import com.portofino.realtrainmodunofficial.client.signboard.tt.StationTimeTable;
import com.portofino.realtrainmodunofficial.signboard.SignboardAnimeType;
import com.portofino.realtrainmodunofficial.signboard.SignboardText;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 本家 SignboardText の描画/アニメ部の移植 (クライアント専用)。
 * <p>
 * {@link SignboardText} はデータだけを持つので、こちらがフォント画像 ({@link FontImage}) の
 * 生成とアニメーションの進行を受け持つ。看板ブロックごとではなく SignboardText ごとに
 * 状態を持たせたいので、SignboardText を弱参照キーにしたキャッシュで対応付けている。
 * <p>
 * サーバから同期が来るたび (チャンク読み込みのたび) に BlockEntity は SignboardText を
 * 作り直すので、古い状態は GC に任せる必要がある。そのため
 * <b>この値クラスは SignboardText を一切参照しない</b> — WeakHashMap の値がキーを強参照
 * すると、キーが弱到達にならずエントリが永久に残ってしまうため。描画対象の SignboardText は
 * 毎回引数で受け取る。
 */
public final class SignboardTextRenderer {
    private static final Map<SignboardText, SignboardTextRenderer> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final float TO_SEC = 0.001F;
    /**
     * フォント画像の高さ(px)。UV の切り出し幅の計算に使う。
     */
    private static final float IMAGE_SIZE = SignboardText.IMAGE_SIZE;

    /**
     * 表示候補。通常は '|' 区切り、時刻表モードでは列車ごとの文字列 + 末尾に空白。
     */
    private FontImage[] images = new FontImage[0];
    /**
     * images を作ったときの見た目キー。変わったら焼き直す。
     */
    private String builtKey = "";
    /**
     * images を作ったときの時刻表設定。変わったら焼き直す。
     */
    private String builtTtSetting = "";
    /**
     * images を作ったときの FontImage キャッシュ世代。ワールドを抜けてテクスチャが
     * 解放されたら作り直す。
     */
    private int builtGeneration = -1;
    private StationTimeTable stationTimeTable;

    private int index;
    private long prevTime;
    private float prevMinU;

    private SignboardTextRenderer() {
    }

    /**
     * この文字の「今このフレームで描くべき内容」を返す。
     *
     * @param ttSetting 看板の時刻表設定 ("tt=...,station=...,track=...")
     */
    public static Frame frameFor(SignboardText text, String ttSetting) {
        return CACHE.computeIfAbsent(text, k -> new SignboardTextRenderer()).nextFrame(text, ttSetting);
    }

    private Frame nextFrame(SignboardText text, String ttSetting) {
        rebuildIfNeeded(text, ttSetting);
        FontImage image = current(text);
        if (image == null || !image.isReady()) {
            return Frame.HIDDEN;
        }

        float minU = 0.0F;
        float maxU = 1.0F;
        float quadWidth = text.width;
        long time = System.currentTimeMillis();
        float difSec = (time - prevTime) * TO_SEC;
        boolean visible = true;

        if (text.animeType == SignboardAnimeType.SCROLL) {
            //本家: animeSpeed 秒で UV を1周させる。
            float u = prevMinU + difSec / Math.max(0.001F, text.animeSpeed);
            minU = u % 1.0F;
            float tw = IMAGE_SIZE * text.width / text.size / image.getWidth();
            maxU = minU + tw;
            prevTime = time;
            prevMinU = minU;
        } else if (text.animeType == SignboardAnimeType.FLASH) {
            //本家: animeSpeed 秒 表示 → animeSpeed 秒 非表示 の繰り返し。
            if (difSec >= text.animeSpeed) {
                visible = false;
                if (difSec >= text.animeSpeed * 2.0F) {
                    prevTime = time;
                }
            }
        } else {
            if (text.animeType == SignboardAnimeType.SWITCH && difSec >= text.animeSpeed && images.length > 0) {
                index = (index + 1) % images.length;
                prevTime = time;
                image = current(text);
                if (image == null || !image.isReady()) {
                    return Frame.HIDDEN;
                }
            }
            //本家: 指定した幅に収まるぶんだけ UV を切り出す。文字が幅より短ければ
            //自然な縦横比になるようにクアッド側を縮める。
            int tw = (int) (IMAGE_SIZE * text.width / text.size);
            maxU = (float) tw / image.getWidth();
            if (maxU > 1.0F) {
                maxU = 1.0F;
                quadWidth = text.size * image.getWidth() / image.getHeight();
            }
        }

        return new Frame(image, minU, maxU, quadWidth, visible);
    }

    private FontImage current(SignboardText text) {
        if (images.length == 0) {
            return null;
        }
        if (stationTimeTable != null) {
            //時刻表モード: 「次に発車する列車」を基準に offset 本先を出す。
            int idx = stationTimeTable.getMatchTrainIndex(stationTimeTable.track) + text.getTimeTableOffset();
            if (idx >= images.length) {
                idx = images.length - 1;
            }
            if (idx < 0) {
                idx = 0;
            }
            return images[idx];
        }
        return images[Math.floorMod(index, images.length)];
    }

    private void rebuildIfNeeded(SignboardText text, String ttSetting) {
        String key = text.appearanceKey();
        String tt = ttSetting == null ? "" : ttSetting;
        int generation = FontImage.generation();
        if (key.equals(builtKey) && tt.equals(builtTtSetting) && generation == builtGeneration && images.length > 0) {
            return;
        }
        builtKey = key;
        builtTtSetting = tt;
        builtGeneration = generation;
        index = 0;
        prevTime = System.currentTimeMillis();
        prevMinU = 0.0F;

        if (text.isTimeTableMode()) {
            StationTimeTable stt = StationTimeTable.parse(tt);
            this.stationTimeTable = stt;
            int col = text.getTimeTableColumn();
            int size = stt.getSize();
            FontImage[] built = new FontImage[size + 1];
            for (int i = 0; i < size; i++) {
                String s = stt.getData(i, col);
                built[i] = FontImage.create(s, text.font, text.style, text.color, SignboardText.IMAGE_SIZE);
            }
            //本家: 末尾は「該当列車なし」用の空白。
            built[size] = FontImage.create(" ", text.font, text.style, text.color, SignboardText.IMAGE_SIZE);
            this.images = built;
        } else {
            this.stationTimeTable = null;
            String[] parts = text.splitTexts();
            FontImage[] built = new FontImage[parts.length];
            for (int i = 0; i < parts.length; i++) {
                built[i] = FontImage.create(parts[i], text.font, text.style, text.color, SignboardText.IMAGE_SIZE);
            }
            this.images = built;
        }
    }

    /**
     * このフレームで描くべき内容。
     *
     * @param image   貼るフォント画像
     * @param minU    UV の左端 (SCROLL で動く)
     * @param maxU    UV の右端
     * @param width   クアッドの幅(ブロック単位)。文字が短いときは自然比まで縮む。
     * @param visible FLASH の消灯中は false
     */
    public record Frame(FontImage image, float minU, float maxU, float width, boolean visible) {
        static final Frame HIDDEN = new Frame(null, 0.0F, 1.0F, 0.0F, false);

        public boolean shouldDraw() {
            return visible && image != null && image.isReady();
        }
    }
}
