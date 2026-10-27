package jp.ngt.rtm.modelpack.modelset;
import jp.ngt.rtm.modelpack.cfg.RailConfig;
public final class ModelSetRail extends ModelSetBase<RailConfig> {
    public ModelSetRail() {super();}
    public ModelSetRail(RailConfig config) {super(config);}
    public RailConfig getDummyConfig() {return RailConfig.getDummy();}
}
