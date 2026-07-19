package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.Connection のスクリプト互換 (種別のみ)。
 *
 * <p>NGTO Builder の Wire ツールが
 * {@code tileEntity.setConnectionTo(x, y, z, Connection.ConnectionType.WIRE, resourceState)} と
 * ワイヤー種別を渡す。RTMU 側の {@code InstalledObjectBlockEntity.setConnectionTo} は種別を
 * 受け取るだけで (現状は WIRE のみ描画)、この enum は名前解決と引数用途で足りる。
 */
public final class Connection {
    private Connection() {
    }

    public enum ConnectionType {
        WIRE, WIRE2, RELAY, DUMMY
    }
}
