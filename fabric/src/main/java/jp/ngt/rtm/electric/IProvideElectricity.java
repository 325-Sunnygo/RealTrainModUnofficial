package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.IProvideElectricity の忠実移植。
 */
public interface IProvideElectricity {
    /**
     * 送信する信号の取得
     */
    int getElectricity();

    /**
     * 信号受信
     */
    void setElectricity(int x, int y, int z, int level);
}
