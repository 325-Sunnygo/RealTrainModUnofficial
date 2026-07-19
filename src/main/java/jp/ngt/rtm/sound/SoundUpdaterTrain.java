package jp.ngt.rtm.sound;

/**
 * 本家 jp.ngt.rtm.sound.SoundUpdaterTrain のプレースホルダ (名前解決用)。
 *
 * <p>ATS 系サウンドスクリプト (MugenTrainSoundLib 等) が
 * {@code NGTUtil.getField(Packages.jp.ngt.rtm.sound.SoundUpdaterTrain.class, su, ["atsSound"])}
 * と<b>クラスリテラル</b>を参照する (×5 スクリプト)。クラスが無いと {@code .class} アクセスで
 * TypeError になりサウンドスクリプト全体が停止する。
 *
 * <p>RTMU のサウンド実行体は SoundScriptExecutor で atsSound フィールドを持たないため、
 * getField は null を返し、スクリプト側の {@code if (atsSound)} ガードで安全にスキップされる
 * (ATS 音の直接停止だけが効かない。停止は登録制サウンド側の寿命管理で行われる)。
 */
@SuppressWarnings("unused")
public class SoundUpdaterTrain {
    /** 本家: MovingSoundEntity[2]。getField の探索先として存在だけさせる。 */
    public Object[] atsSound;
}
