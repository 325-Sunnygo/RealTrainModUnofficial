package jp.ngt.rtm.sound;

/**
 * 本家 jp.ngt.rtm.sound.SoundUpdaterTrain のプレースホルダ (名前解決用)。
 * ATS 系サウンドスクリプト (MugenTrainSoundLib 等) が
 * NGTUtil.getField(Packages.jp.ngt.rtm.sound.SoundUpdaterTrain.class, su, ["atsSound"])
 * とクラスリテラルを参照する (×5 スクリプト)。
 */
@SuppressWarnings("unused")
public class SoundUpdaterTrain {
    /** 本家: MovingSoundEntity[2]。getField の探索先として存在だけさせる。 */
    public Object[] atsSound;
}
