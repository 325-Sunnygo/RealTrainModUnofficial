package jp.masa.signalcontrollermod;

/**
 * SignalControllerMod (作者: masa300, https://github.com/masa300/SignalControllerMod)
 * の 1.21.1 移植。ロジックは原作のまま。
 * 信号の現示段数タイプ (2灯式A/B, 3灯式, 4灯式A/B, 5灯式A/B, 6灯式)。
 */
public enum SignalType {
    signal2a {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 2) ? 3 : signalLevel;
        }
    },
    signal2b {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 2) ? 5 : signalLevel;
        }
    },
    signal3 {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 4) ? 5 : (signalLevel == 2) ? 3 : signalLevel;
        }
    },
    signal4a {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 4) ? 5 : signalLevel;
        }
    },
    signal4b {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 5) ? 5 : (signalLevel == 2) ? 3 : signalLevel;
        }
    },
    signal5a {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 5) ? 5 : signalLevel;
        }
    },
    signal5b {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 6) ? 6 : (signalLevel == 2) ? 3 : (signalLevel == 4) ? 5 : signalLevel;
        }
    },
    signal6 {
        public int upSignalLevel(int signalLevel) {
            return (++signalLevel >= 6) ? 6 : (signalLevel == 2) ? 3 : signalLevel;
        }
    };

    public abstract int upSignalLevel(int oldSignalLevel);

    public static SignalType getType(String s) {
        for (SignalType type : SignalType.values()) {
            if (type.toString().equals(s)) {
                return type;
            }
        }
        return SignalType.signal2a;
    }
}
