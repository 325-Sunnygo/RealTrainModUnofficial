package jp.kaiz.atsassistmod.ifttt;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.ifttt.IFTTTType の移植。
 * IFTTT の条件 (This: 100番台) とアクション (That: 200番台) の種別 id 管理。
 * 本家で未実装だった種別 (時間/周辺光/TrainState 等) は本家同様に除外している。
 */
public final class IFTTTType {

    private IFTTTType() {
    }

    public interface IFTTTEnumBase {
        int getId();

        default String getName() {
            return Component.translatable("ATSAssistMod.IFTTTType." + this.getId()).getString();
        }
    }

    /** 全種別 (メニュー用 Select 含む) を id から引く。 */
    public static IFTTTEnumBase getType(int id) {
        return ALL.stream().filter(e -> e.getId() == id).findFirst().orElse(null);
    }

    public static final class This {
        /** 種別選択メニュー (id=100)。 */
        public static final IFTTTEnumBase Select = () -> 100;

        public enum Minecraft implements IFTTTEnumBase {
            RedStoneInput(110);

            private final int id;

            Minecraft(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }

        public enum RTM implements IFTTTEnumBase {
            OnTrain(120),
            Cars(121),
            Speed(122),
            TrainDataMap(124),
            TrainDirection(125);

            private final int id;

            RTM(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }

        public enum ATSAssist implements IFTTTEnumBase {
            CODD(130);

            private final int id;

            ATSAssist(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }
    }

    public static final class That {
        /** 種別選択メニュー (id=200)。 */
        public static final IFTTTEnumBase Select = () -> 200;

        public enum Minecraft implements IFTTTEnumBase {
            RedStoneOutput(210),
            PlaySound(211),
            ExecuteCommand(212),
            SetBlock(213);

            private final int id;

            Minecraft(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }

        public enum RTM implements IFTTTEnumBase {
            TrainDataMap(221),
            Signal(223);

            private final int id;

            RTM(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }

        public enum ATSAssist implements IFTTTEnumBase {
            JavaScript(230);

            private final int id;

            ATSAssist(int id) {
                this.id = id;
            }

            @Override
            public int getId() {
                return this.id;
            }
        }
    }

    private static final List<IFTTTEnumBase> ALL = new ArrayList<>();

    static {
        ALL.add(This.Select);
        ALL.addAll(Arrays.asList(This.Minecraft.values()));
        ALL.addAll(Arrays.asList(This.RTM.values()));
        ALL.addAll(Arrays.asList(This.ATSAssist.values()));
        ALL.add(That.Select);
        ALL.addAll(Arrays.asList(That.Minecraft.values()));
        ALL.addAll(Arrays.asList(That.RTM.values()));
        ALL.addAll(Arrays.asList(That.ATSAssist.values()));
    }
}
