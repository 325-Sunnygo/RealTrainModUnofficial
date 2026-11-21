package jp.masa.signalcontrollermod;

/** SignalControllerMod (作者: masa300, https://github.… */
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
