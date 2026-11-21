package jp.kaiz.atsassistmod.block;

/**
 * 本家 jp.kaiz.atsassistmod.block.GroundUnitType の移植 (地上子の種類)。
 * id はブロックの見た目 (unit_type ステート = 本家のメタデータ) と一致する。
 */
public enum GroundUnitType {
    None(0) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.None();
        }
    },
    ATC_SpeedLimit_Notice(1) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATCSpeedLimitNotice();
        }
    },
    ATC_SpeedLimit_Cancel(2) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATCSpeedLimitCancel();
        }
    },
    ATC_SpeedLimit_Reset(3) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATCSpeedLimitReset();
        }
    },
    TASC_StopPotion_Notice(4) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.TASCStopPositionNotice();
        }
    },
    TASC_Cancel(5) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.TASCDisable();
        }
    },
    TASC_StopPotion_Correction(6) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.TASCStopPositionCorrection();
        }
    },
    TASC_StopPotion(7) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.TASCStopPosition();
        }
    },
    ATO_Departure_Signal(9) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATODepartureSignal();
        }
    },
    ATO_Cancel(10) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATODisable();
        }
    },
    ATO_Change_Speed(11) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATOChangeSpeed();
        }
    },
    TrainState_Set(13) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.TrainStateSet();
        }
    },
    CHANGE_TP(14) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ChangeTrainProtection();
        }
    },
    ATACS_Disable(15) {
        @Override
        public GroundUnitLogic newLogic() {
            return new GroundUnitLogic.ATACSDisable();
        }
    };

    public final int id;

    GroundUnitType(int id) {
        this.id = id;
    }

    public static GroundUnitType getType(int par1) {
        for (GroundUnitType type : GroundUnitType.values()) {
            if (type.id == par1) {
                return type;
            }
        }
        return None;
    }

    public abstract GroundUnitLogic newLogic();
}
