package jp.ngt.rtm.entity.ai;

import jp.ngt.rtm.entity.npc.EntityMotorman;
import jp.ngt.rtm.entity.train.util.EnumNotch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本家 jp.ngt.rtm.entity.ai.EntityAIDrivingWithDiagram の移植。
 * 「本と羽根ペン」に書いたダイヤ (1 行 = {@code 時刻 コマンド x y z}) に従って運転する。
 * <pre>
 *   3000 start 0 0 0        … ワールド時刻 3000 に発車 (信号が開いていれば)
 *   4000 pass 120 64 -40    … 時刻 4000 に地点 (120,-40) を通過するよう加減速
 *   5000 stop 300 64 -40    … 時刻 5000 に地点 (300,-40) に停車
 * </pre>
 * コマンドを消化したら次の行へ。信号現示による制限速度は常に優先される。
 */
public class DrivingWithDiagramGoal extends DrivingWithSignalGoal {

    private final List<TrainDiagram> diagram = new ArrayList<>();

    public static class TrainDiagram {
        public final int time;
        public final String command;
        public final int pointX;
        public final int pointY;
        public final int pointZ;

        public TrainDiagram(int par1Time, String par2Command, int par3X, int par4Y, int par5Z) {
            this.time = par1Time;
            this.command = par2Command;
            this.pointX = par3X;
            this.pointY = par4Y;
            this.pointZ = par5Z;
        }
    }

    public DrivingWithDiagramGoal(EntityMotorman motorman) {
        super(motorman);
    }

    @Override
    public boolean canUse() {
        return this.motorman.hasDiagram() && super.canUse();
    }

    @Override
    public void start() {
        super.start();
        this.diagram.clear();
        for (String s : bookToStrings(this.motorman.getDiagram())) {
            String[] tokens = s.split(" ");
            int t = 0;
            String com = "";
            int pX = 0;
            int pY = 0;
            int pZ = 0;
            try {
                t = Integer.parseInt(tokens[0]);
                com = tokens[1];
                pX = Integer.parseInt(tokens[2]);
                pY = Integer.parseInt(tokens[3]);
                pZ = Integer.parseInt(tokens[4]);
            } catch (NumberFormatException e) {
                this.diagram.clear();
                this.diagram.add(new TrainDiagram(0, "finish", 0, 0, 0));
                return;
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
            this.diagram.add(new TrainDiagram(t, com, pX, pY, pZ));
        }
        this.diagram.add(new TrainDiagram(0, "finish", 0, 0, 0));
    }

    /** 本家 ItemUtil.bookToStrings 相当: 本の全ページを行に分解。 */
    private static String[] bookToStrings(ItemStack book) {
        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content == null) {
            return new String[0];
        }
        String joined = content.getPages(false).collect(Collectors.joining("\n"));
        return joined.split("\n");
    }

    @Override
    public void tick() {
        if (this.train == null || this.diagram.isEmpty()) {
            return;
        }
        this.runTrain();
    }

    private void runTrain() {
        TrainDiagram td = this.diagram.get(0);

        if (td.command.equals("finish") || td.command.isEmpty()) {
            return;
        }

        int worldTime = (int) (this.motorman.level().getDayTime() % 24000L); //0~23999, 1=3.6sec
        int signalLevel = this.train.getSignal();

        if (td.command.equals("start")) {
            if (worldTime >= td.time - 2 && worldTime <= td.time + 2) {
                if (signalLevel >= 0 && signalLevel < 5) {
                    this.train.setNotch(4);
                    this.diagram.remove(0);
                }
            }
            return;
        }

        float distance = this.getDistanceTrain(this.train, td.pointX, td.pointZ);
        float margin = (float) (td.time - worldTime);
        float prevSpeed = this.train.getSpeed();
        float ac1; //目標加速度
        int notch = 0;
        int notchS = 0;
        if (signalLevel > 0) {
            notchS = EnumNotch.getNotchFromSignal(signalLevel).id;
        }

        switch (td.command) {
            case "set_speed": {
                float speed = (float) td.pointX / 72.0F;
                ac1 = (speed - prevSpeed) / margin;
                notch = EnumNotch.getSuitableNotchFromAcceleration(ac1).id;
                break;
            }
            case "pass": {
                ac1 = 2 * (distance - prevSpeed * margin) / (margin * margin);
                if (ac1 > 0) {
                    float sp0 = prevSpeed + ac1 * margin; //目標速度
                    notch = EnumNotch.getSuitableNotchFromSpeed(sp0).id;
                } else if (ac1 < 0) {
                    notch = EnumNotch.getSuitableNotchFromAcceleration(ac1).id;
                }
                break;
            }
            case "stop": {
                if (distance <= 360.0F) {
                    ac1 = -prevSpeed / margin;
                    if (0.5F * prevSpeed * margin < distance) {
                        ac1 += 0.00075F;
                    }
                    notch = EnumNotch.getSuitableNotchFromAcceleration(ac1).id;
                } else {
                    distance -= 360.0F;
                    margin -= 600.0F;
                    ac1 = 2 * (distance - (prevSpeed * margin)) / (margin * margin);
                    if (ac1 >= 0.0F) {
                        if (ac1 < EnumNotch.accelerate_1.acceleration && prevSpeed >= EnumNotch.accelerate_4.max_speed) {
                            notch = EnumNotch.inertia.id;
                        } else {
                            notch = EnumNotch.accelerate_4.id;
                        }
                    } else if (ac1 > EnumNotch.brake_1.acceleration) {
                        notch = EnumNotch.inertia.id;
                    } else {
                        notch = EnumNotch.getSuitableNotchFromAcceleration(ac1).id;
                    }
                }

                if (worldTime >= td.time - 2 && worldTime <= td.time + 2) {
                    notch = -4;
                }
                break;
            }
            default:
                break;
        }

        if (signalLevel > 0) {
            notch = Math.min(notch, notchS);
        }
        this.train.setNotch(notch);
        if (worldTime >= td.time - 2 && worldTime <= td.time + 2) {
            this.diagram.remove(0);
        }
    }

    private float getDistanceTrain(Entity entity, double par1, double par2) {
        float f1 = (float) (entity.getX() - par1);
        float f3 = (float) (entity.getZ() - par2);
        return Mth.sqrt(f1 * f1 + f3 * f3);
    }
}
