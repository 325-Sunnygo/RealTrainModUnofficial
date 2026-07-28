package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.kaiz.atsassistmod.ifttt.IFTTTGuiFields;
import jp.kaiz.atsassistmod.ifttt.IFTTTType;
import jp.kaiz.atsassistmod.network.IFTTTUpdatePayload;
import jp.kaiz.atsassistmod.utils.CardinalDirection;
import jp.kaiz.atsassistmod.utils.ComparisonManager;
import jp.kaiz.atsassistmod.utils.KaizUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.gui.GUIIFTTTMaterial (694行) の移植。
 * ページ構成は本家準拠: メイン (This/That 一覧 + AnyMatch) → 種別選択 (100/200) → 各設定ページ。
 * テクスチャ (iftttbaselayer 等) も本家のものをそのまま使う。
 * SetBlock はブロック名指定 (1.21 に数値 id がないため) に変更。
 */
public class IFTTTScreen extends Screen implements IFTTTGuiFields {

    private static final ResourceLocation BASE_LAYER = tex("iftttbaselayer");
    private static final ResourceLocation CONTAINER_THIS = tex("iftttcontainer0");
    private static final ResourceLocation CONTAINER_THAT = tex("iftttcontainer1");
    private static final ResourceLocation ADD_BUTTON = tex("addbutton");
    private static final ResourceLocation EDIT_BUTTON = tex("editbutton");
    private static final ResourceLocation DELETE_BUTTON = tex("deletebutton");
    private static final ResourceLocation EXIT_BUTTON = tex("exit");
    private static final ResourceLocation EXIT_BUTTON_RED = tex("exitred");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("atsassistmod", "textures/gui/" + name + ".png");
    }

    private final BlockPos pos;
    private final List<EditBox> textFieldList = new ArrayList<>();
    /** スロット描画情報 (メインページ)。 */
    private final List<SlotBox> slotBoxes = new ArrayList<>();

    private IFTTTType.IFTTTEnumBase type = null;
    private IFTTTContainer ifcb = null;
    private int ifcbIndex = -1;

    //動的ラベルのサイクルボタン (本家 id 1000/1001)
    private Button cycleButton0;
    private Button cycleButton1;

    public IFTTTScreen(BlockPos pos) {
        super(Component.literal("IFTTT"));
        this.pos = pos;
    }

    private IFTTTBlockEntity tile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(this.pos) instanceof IFTTTBlockEntity be) {
            return be;
        }
        return null;
    }

    //------------------------------------------------------------ init (本家 initGui)

    @Override
    protected void init() {
        this.clearWidgets();
        this.textFieldList.clear();
        this.slotBoxes.clear();
        this.cycleButton0 = null;
        this.cycleButton1 = null;

        IFTTTBlockEntity tile = tile();
        if (tile == null) {
            onClose();
            return;
        }

        //終了ボタン (本家 GuiButtonExit, 右上)
        addRenderableWidget(new TexButton(this.width / 2 + 175, this.height / 2 - 101, 7, 7,
                EXIT_BUTTON, EXIT_BUTTON_RED, b -> onClose()));

        if (this.type == null) {
            initMainPage(tile);
        } else {
            initEditPage();
        }
    }

    /** メインページ: This/That 一覧 + AnyMatch。 */
    private void initMainPage(IFTTTBlockEntity tile) {
        List<IFTTTContainer> thisList = tile.getThisList();
        int thisSize = thisList.size();
        for (int i = 0; i < thisSize && i < IFTTTBlockEntity.MAX_LIST; i++) {
            addSlot(thisList.get(i), i, thisSize, this.width / 2 - 110, this.height / 2 - 100, true);
        }
        if (thisSize < IFTTTBlockEntity.MAX_LIST) {
            addAddButton(thisSize, this.width / 2 - 73, this.height / 2 - 86, true);
        }

        List<IFTTTContainer> thatList = tile.getThatList();
        int thatSize = thatList.size();
        for (int i = 0; i < thatSize && i < IFTTTBlockEntity.MAX_LIST; i++) {
            addSlot(thatList.get(i), i, thatSize, this.width / 2 - 110, this.height / 2 + 5, false);
        }
        if (thatSize < IFTTTBlockEntity.MAX_LIST) {
            addAddButton(thatSize, this.width / 2 - 73, this.height / 2 + 19, false);
        }

        //AnyMatch チェックボックス (本家 id 300)
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(this.width / 2 - 138, this.height / 2 - 20)
                .selected(tile.isAnyMatch())
                .onValueChange((box, value) -> {
                    IFTTTBlockEntity t = tile();
                    if (t != null) {
                        t.setAnyMatch(value);
                    }
                    PacketDistributor.sendToServer(new IFTTTUpdatePayload(
                            this.pos, 3, -1, new CompoundTag(), value));
                })
                .build());
    }

    /** 一覧のスロット (箱 + Edit/Del ボタン)。座標式は本家 addDetail 準拠。 */
    private void addSlot(IFTTTContainer container, int number, int size, int widthBase, int heightBase, boolean isThis) {
        int x = widthBase + (number < 3 ? 95 * number : 95 * (number - 3));
        int y = heightBase + (number < 3 ? (size < 3 ? 25 : 0) : 50);
        this.slotBoxes.add(new SlotBox(x, y, container, isThis));

        addRenderableWidget(new TexButton(x + 41, y + 34, 22, 9, EDIT_BUTTON, null, b -> {
            IFTTTBlockEntity t = tile();
            if (t == null) {
                return;
            }
            List<IFTTTContainer> list = isThis ? t.getThisList() : t.getThatList();
            if (number < list.size()) {
                this.ifcb = list.get(number).copy();
                this.type = this.ifcb.getType();
                this.ifcbIndex = number;
                rebuildWidgets();
            }
        }).label("Edit"));

        addRenderableWidget(new TexButton(x + 65, y + 34, 22, 9, DELETE_BUTTON, null, b -> {
            IFTTTBlockEntity t = tile();
            if (t == null) {
                return;
            }
            List<IFTTTContainer> list = isThis ? t.getThisList() : t.getThatList();
            if (number < list.size()) {
                IFTTTContainer removed = list.get(number);
                PacketDistributor.sendToServer(new IFTTTUpdatePayload(
                        this.pos, 2, number, removed.toNbt(), t.isAnyMatch()));
                list.remove(number);
                rebuildWidgets();
            }
        }).label("Del"));
    }

    /** 追加ボタン (本家 addAddButton 準拠座標)。 */
    private void addAddButton(int number, int widthBase, int heightBase, boolean isThis) {
        int size = tileListSize(isThis);
        int x = widthBase + 95 * (number < 3 ? number : (number - 3));
        //本家: heightBase += number < 3 ? size < 3 ? 25 : 0 : 50 (size=現在の要素数)
        int y = heightBase + (number < 3 ? (size < 3 ? 25 : 0) : 50);

        addRenderableWidget(new TexButton(x, y, 18, 18, ADD_BUTTON, null, b -> {
            this.type = isThis ? IFTTTType.This.Select : IFTTTType.That.Select;
            this.ifcb = null;
            this.ifcbIndex = -1;
            rebuildWidgets();
        }));
    }

    private int tileListSize(boolean isThis) {
        IFTTTBlockEntity t = tile();
        if (t == null) {
            return 0;
        }
        return isThis ? t.getThisList().size() : t.getThatList().size();
    }

    /** 種別選択/設定ページ。分岐は本家 initGui の switch 準拠。 */
    private void initEditPage() {
        int w2 = this.width / 2;
        int h2 = this.height / 2;
        switch (this.type.getId()) {
            case 100: //This 選択
                addSelectButtons(IFTTTType.This.Minecraft.values(), w2 - 170, h2 - 75);
                addSelectButtons(IFTTTType.This.RTM.values(), w2 - 170, h2 - 10);
                addSelectButtons(IFTTTType.This.ATSAssist.values(), w2 - 170, h2 + 55);
                addBackButton(w2 - 50, this.height - 25);
                break;
            case 200: //That 選択
                addSelectButtons(IFTTTType.That.Minecraft.values(), w2 - 170, h2 - 75);
                addSelectButtons(IFTTTType.That.RTM.values(), w2 - 170, h2 - 10);
                addSelectButtons(IFTTTType.That.ATSAssist.values(), w2 - 170, h2 + 55);
                addBackButton(w2 - 50, this.height - 25);
                break;
            case 110: { //RedStoneInput
                IFTTTContainer.This.Minecraft.RedStoneInput c = (IFTTTContainer.This.Minecraft.RedStoneInput) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    c.setMode(KaizUtils.getNextEnum(c.getMode()));
                }).bounds(w2 - 15, h2 - 30, 30, 20).build());
                addGuiTextField(c.getValue(), w2 + 30, h2 - 30, 2, 30);
                addBottomCommon();
                break;
            }
            case 120: { //単純列検
                IFTTTContainer.This.RTM.SimpleDetectTrain c = (IFTTTContainer.This.RTM.SimpleDetectTrain) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    c.setDetectMode(KaizUtils.getNextEnum(c.getDetectMode()));
                }).bounds(w2 + 30, h2 - 30, 60, 20).build());
                addBottomCommon();
                break;
            }
            case 121: { //両数
                IFTTTContainer.This.RTM.Cars c = (IFTTTContainer.This.RTM.Cars) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    c.setMode(KaizUtils.getNextEnum(c.getMode()));
                }).bounds(w2 - 15, h2 - 30, 30, 20).build());
                addGuiTextField(c.getValue(), w2 + 30, h2 - 30, 127, 50);
                addBottomCommon();
                break;
            }
            case 122: { //速度
                IFTTTContainer.This.RTM.Speed c = (IFTTTContainer.This.RTM.Speed) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    //本家: >= と <= のトグル
                    c.setMode(c.getMode() == ComparisonManager.Integer.GREATER_EQUAL
                            ? ComparisonManager.Integer.LESS_EQUAL : ComparisonManager.Integer.GREATER_EQUAL);
                }).bounds(w2 - 15, h2 - 30, 30, 20).build());
                addGuiTextField(c.getValue(), w2 + 30, h2 - 30, 127, 50);
                addBottomCommon();
                break;
            }
            case 124: { //TrainDataMap (条件)
                IFTTTContainer.This.RTM.TrainDataMap c = (IFTTTContainer.This.RTM.TrainDataMap) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> c.nextDataType())
                        .bounds(w2 + 30, h2 - 55, 30, 20).build());
                this.cycleButton1 = addRenderableWidget(Button.builder(Component.empty(), b -> c.nextComparisonType())
                        .bounds(w2 - 15, h2 - 5, 30, 20).build());
                addGuiTextField(c.getKey(), w2 + 30, h2 - 30, 127, 50);
                addGuiTextField(c.getValue(), w2 + 30, h2 - 5, 127, 50);
                addBottomCommon();
                break;
            }
            case 125: { //TrainDirection
                IFTTTContainer.This.RTM.TrainDirection c = (IFTTTContainer.This.RTM.TrainDirection) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    c.setDirection(KaizUtils.getNextEnum(c.getDirection()));
                }).bounds(w2 - 15, h2 - 30, 60, 20).build());
                addBottomCommon();
                break;
            }
            case 130: { //踏切障検
                IFTTTContainer.This.ATSAssist.CrossingObstacleDetection c =
                        (IFTTTContainer.This.ATSAssist.CrossingObstacleDetection) this.ifcb;
                addGuiTextField(c.getStartCC()[0], w2 - 50, h2 - 30, 127, 30);
                addGuiTextField(c.getStartCC()[1], w2 - 15, h2 - 30, 127, 30);
                addGuiTextField(c.getStartCC()[2], w2 + 20, h2 - 30, 127, 30);
                addRenderableWidget(Button.builder(Component.literal("V"), b -> {
                    int[] p = getPosFromClipboard();
                    if (p != null) {
                        setFieldTexts(0, p);
                    }
                }).bounds(w2 + 55, h2 - 30, 20, 20).build());
                addGuiTextField(c.getEndCC()[0], w2 - 50, h2 - 5, 127, 30);
                addGuiTextField(c.getEndCC()[1], w2 - 15, h2 - 5, 127, 30);
                addGuiTextField(c.getEndCC()[2], w2 + 20, h2 - 5, 127, 30);
                addRenderableWidget(Button.builder(Component.literal("V"), b -> {
                    int[] p = getPosFromClipboard();
                    if (p != null) {
                        setFieldTexts(3, p);
                    }
                }).bounds(w2 + 55, h2 - 5, 20, 20).build());
                addBottomCommon();
                break;
            }
            case 210: { //RedStoneOutput
                IFTTTContainer.That.Minecraft.RedStoneOutput c = (IFTTTContainer.That.Minecraft.RedStoneOutput) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b ->
                                c.setTrainCarsOutput(!c.isTrainCarsOutput()))
                        .bounds(w2 + 30, h2 - 55, 30, 20).build());
                addGuiTextField(c.getOutputLevel(), w2 + 30, h2 - 30, 127, 50);
                addBottomCommon();
                break;
            }
            case 211: { //PlaySound
                IFTTTContainer.That.Minecraft.PlaySound c = (IFTTTContainer.That.Minecraft.PlaySound) this.ifcb;
                addOnceCheckbox(w2 + 45, h2 - 80);
                addGuiTextField(c.getSoundName(), w2 - 50, h2 - 55, 127, 100);
                addGuiTextField(c.getRadius(), w2 - 85, h2 - 5, 127, 30);
                addGuiTextField(c.getPos()[0], w2 - 50, h2 - 5, 127, 30);
                addGuiTextField(c.getPos()[1], w2 - 15, h2 - 5, 127, 30);
                addGuiTextField(c.getPos()[2], w2 + 20, h2 - 5, 127, 30);
                addRenderableWidget(Button.builder(Component.literal("V"), b -> {
                    int[] p = getPosFromClipboard();
                    if (p != null) {
                        setFieldTexts(2, p);
                    }
                }).bounds(w2 + 55, h2 - 5, 20, 20).build());
                addBottomCommon();
                break;
            }
            case 212: { //ExecuteCommand
                IFTTTContainer.That.Minecraft.ExecuteCommand c = (IFTTTContainer.That.Minecraft.ExecuteCommand) this.ifcb;
                addOnceCheckbox(w2 + 45, h2 - 80);
                addGuiTextField(c.getDisplayName(), w2 - 100, h2 - 40, 127, 200);
                addGuiTextField(c.getCommand(), w2 - 100, h2 - 5, 127, 200);
                addBottomCommon();
                break;
            }
            case 213: { //SetBlock (1.21: x, y, z, ブロック名)
                IFTTTContainer.That.Minecraft.SetBlock c = (IFTTTContainer.That.Minecraft.SetBlock) this.ifcb;
                addOnceCheckbox(w2 + 45, h2 - 80);
                int h = h2 - 30;
                List<IFTTTContainer.That.Minecraft.SetBlock.Entry> posList = c.getPosList();
                for (int i = 0, posListSize = posList.size(); i < posListSize; i++) {
                    IFTTTContainer.That.Minecraft.SetBlock.Entry entry = posList.get(i);
                    addGuiTextField(entry.x(), w2 - 100, h, 127, 30);
                    addGuiTextField(entry.y(), w2 - 65, h, 127, 30);
                    addGuiTextField(entry.z(), w2 - 30, h, 127, 30);
                    addGuiTextField(entry.blockId(), w2 + 5, h, 127, 80);
                    final int rowIndex = i;
                    if (posListSize < 5) {
                        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                            applySetBlockFields(c);
                            c.addPos(new IFTTTContainer.That.Minecraft.SetBlock.Entry(0, 0, 0, ""), rowIndex);
                            rebuildWidgets();
                        }).bounds(w2 + 90, h, 20, 20).build());
                    }
                    if (posListSize > 1) {
                        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                            applySetBlockFields(c);
                            c.removePos(rowIndex);
                            rebuildWidgets();
                        }).bounds(w2 + 115, h, 20, 20).build());
                    }
                    addRenderableWidget(Button.builder(Component.literal("V"), b -> {
                        int[] p = getPosFromClipboard();
                        if (p != null) {
                            setFieldTexts(rowIndex * IFTTTContainer.That.Minecraft.SetBlock.FIELDS_PER_ROW, p);
                        }
                    }).bounds(w2 + 140, h, 20, 20).build());
                    h += 25;
                }
                addBottomCommon();
                break;
            }
            case 221: { //DataMap (アクション)
                IFTTTContainer.That.RTM.DataMap c = (IFTTTContainer.That.RTM.DataMap) this.ifcb;
                this.cycleButton0 = addRenderableWidget(Button.builder(Component.empty(), b -> c.nextDataType())
                        .bounds(w2 + 30, h2 - 55, 30, 20).build());
                addGuiTextField(c.getKey(), w2 + 30, h2 - 30, 127, 50);
                addGuiTextField(c.getValue(), w2 + 30, h2 - 5, 127, 50);
                addBottomCommon();
                break;
            }
            case 223: { //TrainSignal
                IFTTTContainer.That.RTM.TrainSignal c = (IFTTTContainer.That.RTM.TrainSignal) this.ifcb;
                addGuiTextField(c.getSignal(), w2 + 30, h2 - 30, 3, 50);
                addBottomCommon();
                break;
            }
            case 230: { //JavaScript
                IFTTTContainer.That.ATSAssist.JavaScript c = (IFTTTContainer.That.ATSAssist.JavaScript) this.ifcb;
                addRenderableWidget(Button.builder(Component.literal("V"), b -> {
                    //クリップボードからスクリプト貼り付け (本家仕様)
                    String clipBoard = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (Minecraft.getInstance().player != null) {
                        c.setJSText(clipBoard, Minecraft.getInstance().player.getUUID());
                    }
                    if (this.textFieldList.size() > 1) {
                        this.textFieldList.get(1).setValue(clipBoard);
                    }
                }).bounds(w2 + 80, h2 - 25, 20, 20).build());
                addGuiTextField(c.getScriptName(), w2 - 100, h2 - 60, 32767, 175);
                addGuiTextField(c.getJSText(), w2 - 100, h2 - 25, 32767, 175);
                addBottomCommon();
                break;
            }
        }
    }

    /** SetBlock: 現在のテキスト欄から posList を読み戻す (+/- 押下時)。 */
    private void applySetBlockFields(IFTTTContainer.That.Minecraft.SetBlock c) {
        c.setFromGui(this);
        if (c.getPosList().isEmpty()) {
            c.addPos(new IFTTTContainer.That.Minecraft.SetBlock.Entry(0, 0, 0, ""));
        }
    }

    private void setFieldTexts(int startIndex, int[] values) {
        for (int i = 0; i < values.length && startIndex + i < this.textFieldList.size(); i++) {
            this.textFieldList.get(startIndex + i).setValue(String.valueOf(values[i]));
        }
    }

    private void addSelectButtons(IFTTTType.IFTTTEnumBase[] types, int baseWidth, int baseHeight) {
        List<IFTTTType.IFTTTEnumBase> list = Arrays.asList(types);
        for (int i = 0; i < list.size() && i < 6; i++) {
            IFTTTType.IFTTTEnumBase t = list.get(i);
            int x = baseWidth + 120 * (i < 3 ? i : i - 3);
            int y = baseHeight + (i < 3 ? 0 : 25);
            addRenderableWidget(Button.builder(Component.literal(t.getName()), b -> {
                IFTTTContainer created = t.getId() == 211
                        ? new IFTTTContainer.That.Minecraft.PlaySound(this.pos)
                        : IFTTTContainer.newByTypeId(t.getId());
                if (created != null) {
                    this.ifcb = created;
                    this.type = created.getType();
                    this.ifcbIndex = -1;
                    rebuildWidgets();
                }
            }).bounds(x, y, 100, 20).build());
        }
    }

    private void addBackButton(int x, int y) {
        addRenderableWidget(Button.builder(
                        Component.translatable("ATSAssistMod.gui.IFTTTMaterial.common.button.990"), b -> {
                    this.type = null;
                    this.ifcb = null;
                    this.ifcbIndex = -1;
                    rebuildWidgets();
                })
                .bounds(x, y, 100, 20).build());
    }

    /** 下部共通: 追加/変更 (91) + 戻る (990)。 */
    private void addBottomCommon() {
        addRenderableWidget(Button.builder(
                        Component.translatable("ATSAssistMod.gui.IFTTTMaterial.common.button.91."
                                + (this.ifcbIndex == -1 ? 0 : 1)), b -> {
                    this.ifcb.setFromGui(this);
                    IFTTTBlockEntity t = tile();
                    boolean anyMatch = t != null && t.isAnyMatch();
                    PacketDistributor.sendToServer(new IFTTTUpdatePayload(
                            this.pos, 0, this.ifcbIndex, this.ifcb.toNbt(), anyMatch));
                    onClose();
                })
                .bounds(this.width / 2 - 110, this.height - 30, 100, 20).build());
        addBackButton(this.width / 2 + 10, this.height - 30);
    }

    /** 「一度のみ」チェックボックス (本家 GuiCheckBox id=1000)。 */
    private void addOnceCheckbox(int x, int y) {
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(x, y)
                .selected(this.ifcb.isOnce())
                .onValueChange((box, value) -> this.ifcb.setOnce(value))
                .build());
    }

    private void addGuiTextField(Object str, int x, int y, int maxLength, int width) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(String.valueOf(str));
        this.textFieldList.add(box);
        addRenderableWidget(box);
    }

    //------------------------------------------------------------ IFTTTGuiFields

    @Override
    public int getTextFieldInt(int number) {
        return parseIntOrZero(getTextFieldText(number));
    }

    @Override
    public String getTextFieldText(int number) {
        if (number < 0 || number >= this.textFieldList.size()) {
            return "";
        }
        String text = this.textFieldList.get(number).getValue();
        return text == null ? "" : text;
    }

    @Override
    public int textFieldLength() {
        return this.textFieldList.size();
    }

    private int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] getPosFromClipboard() {
        String clipboardText = Minecraft.getInstance().keyboardHandler.getClipboard();
        return clipboardText.matches("^-?(\\d+ *,? +){2}-?\\d+$")
                ? Arrays.stream(clipboardText.split(" *,? +")).mapToInt(this::parseIntOrZero).toArray()
                : null;
    }

    //------------------------------------------------------------ 描画

    /** ブラー無効化 + メインページの下敷きテクスチャ。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC0101010);
        if (this.type == null) {
            //本家: 750/2 x 422/2 のベースレイヤー (テクスチャは正方形描画)
            int tw = 750 / 2;
            int th = 422 / 2;
            int x = (this.width - tw) / 2;
            int y = (this.height - th) / 2;
            g.blit(BASE_LAYER, x, y, tw, tw, 0, 0, 1024, 1024, 1024, 1024);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateDynamicWidgets();
        super.render(g, mouseX, mouseY, partialTick);

        int w2 = this.width / 2;
        int h2 = this.height / 2;

        if (this.type == null) {
            //スロット箱 + AnyMatch ラベル (本家 drawScreen)
            for (SlotBox box : this.slotBoxes) {
                box.render(g);
            }
            String anyMatch = "AnyMatch";
            g.drawString(this.font, anyMatch,
                    w2 - this.font.width(anyMatch) / 2 - 161,
                    h2 - this.font.lineHeight / 2 - 15, 0xFFFFFF, true);
            return;
        }

        g.drawString(this.font, "IFTTT : " + (this.type.getId() < 200 ? "This" : "That") + " : " + this.type.getName(),
                this.width / 4, 20, 0xFFFFFF, false);

        switch (this.type.getId()) {
            case 100:
            case 200:
                g.drawString(this.font, "Minecraft", w2 - 170, h2 - 90, 0xFFFFFF, false);
                g.drawString(this.font, "RTM", w2 - 170, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "ATSAssist", w2 - 170, h2 + 40, 0xFFFFFF, false);
                break;
            case 110:
                g.drawString(this.font, "Input", w2 - 50, h2 - 25, 0xFFFFFF, false);
                break;
            case 120:
                g.drawString(this.font, i18n("ATSAssistMod.IFTTT.DetectMode.name"), w2 - 75, h2 - 25, 0xFFFFFF, false);
                break;
            case 121:
                g.drawString(this.font, "Cars", w2 - 50, h2 - 25, 0xFFFFFF, false);
                break;
            case 122:
                g.drawString(this.font, "Speed", w2 - 50, h2 - 25, 0xFFFFFF, false);
                break;
            case 124:
                g.drawString(this.font, "DataType", w2 - 50, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, "Key", w2 - 50, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "Value", w2 - 50, h2, 0xFFFFFF, false);
                break;
            case 125:
                g.drawString(this.font, "Train heading", w2 - 50, h2 - 50, 0xFFFFFF, false);
                break;
            case 130:
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.130.0"), w2 - 75, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.130.1"), w2 - 75, h2, 0xFFFFFF, false);
                g.drawString(this.font, "X", w2 - 40, h2 - 45, 0xFFFFFF, false);
                g.drawString(this.font, "Y", w2 - 2, h2 - 45, 0xFFFFFF, false);
                g.drawString(this.font, "Z", w2 + 33, h2 - 45, 0xFFFFFF, false);
                break;
            case 210:
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.210.0"), w2 - 50, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.210.1"), w2 - 50, h2 - 25, 0xFFFFFF, false);
                break;
            case 211:
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.211.0"), w2 - 100, h2 - 75, 0xFFFFFF, false);
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.211.1"), w2 - 100, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.211.2"), w2 - 72, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "X", w2 - 37, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "Y", w2 - 2, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "Z", w2 + 33, h2 - 25, 0xFFFFFF, false);
                break;
            case 212:
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.212.0"), w2 - 100, h2 - 75, 0xFFFFFF, false);
                g.drawString(this.font, "DisplayName", w2 - 100, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.212.1"), w2 - 100, h2 - 15, 0xFFFFFF, false);
                break;
            case 213:
                g.drawString(this.font, i18n("ATSAssistMod.gui.IFTTTMaterial.213.0"), w2 - 100, h2 - 75, 0xFFFFFF, false);
                g.drawString(this.font, "x", w2 - 97, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, "y", w2 - 62, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, "z", w2 - 27, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, "block", w2 + 8, h2 - 50, 0xFFFFFF, false);
                break;
            case 221:
                g.drawString(this.font, "DataType", w2 - 50, h2 - 50, 0xFFFFFF, false);
                g.drawString(this.font, "Key", w2 - 50, h2 - 25, 0xFFFFFF, false);
                g.drawString(this.font, "Value", w2 - 50, h2, 0xFFFFFF, false);
                break;
            case 223:
                g.drawString(this.font, "SignalLevel", w2 - 50, h2 - 25, 0xFFFFFF, false);
                break;
            case 230:
                g.drawString(this.font, "Script Name", w2 - 100, h2 - 70, 0xFFFFFF, false);
                g.drawString(this.font, "Script Text", w2 - 100, h2 - 35, 0xFFFFFF, false);
                break;
        }
    }

    /** サイクルボタンのラベルと入力欄の表示/非表示を毎フレーム更新 (本家 drawScreen 準拠)。 */
    private void updateDynamicWidgets() {
        if (this.type == null || this.ifcb == null) {
            return;
        }
        switch (this.type.getId()) {
            case 110: {
                IFTTTContainer.This.Minecraft.RedStoneInput c = (IFTTTContainer.This.Minecraft.RedStoneInput) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getMode().name));
                }
                this.textFieldList.forEach(f -> f.setVisible(c.getMode().needStr));
                break;
            }
            case 120: {
                IFTTTContainer.This.RTM.SimpleDetectTrain c = (IFTTTContainer.This.RTM.SimpleDetectTrain) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getDetectMode().getDisplayName()));
                }
                break;
            }
            case 121: {
                IFTTTContainer.This.RTM.Cars c = (IFTTTContainer.This.RTM.Cars) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getMode().getName()));
                }
                break;
            }
            case 122: {
                IFTTTContainer.This.RTM.Speed c = (IFTTTContainer.This.RTM.Speed) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getMode().getName()));
                }
                break;
            }
            case 124: {
                IFTTTContainer.This.RTM.TrainDataMap c = (IFTTTContainer.This.RTM.TrainDataMap) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getDataType().key));
                }
                if (this.cycleButton1 != null) {
                    this.cycleButton1.setMessage(Component.literal(c.getComparisonType().getName()));
                }
                //Boolean 比較のとき Value 欄を隠す (本家仕様)
                boolean showValue = !(c.getComparisonType() instanceof ComparisonManager.Boolean);
                if (this.textFieldList.size() > 1) {
                    this.textFieldList.get(1).setVisible(showValue);
                }
                break;
            }
            case 125: {
                IFTTTContainer.This.RTM.TrainDirection c = (IFTTTContainer.This.RTM.TrainDirection) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getDirection().name()));
                }
                break;
            }
            case 210: {
                IFTTTContainer.That.Minecraft.RedStoneOutput c = (IFTTTContainer.That.Minecraft.RedStoneOutput) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.translatable(
                            "ATSAssistMod.gui.IFTTTMaterial.210.button." + (c.isTrainCarsOutput() ? "enable" : "disable")));
                }
                this.textFieldList.forEach(f -> f.setVisible(!c.isTrainCarsOutput()));
                break;
            }
            case 221: {
                IFTTTContainer.That.RTM.DataMap c = (IFTTTContainer.That.RTM.DataMap) this.ifcb;
                if (this.cycleButton0 != null) {
                    this.cycleButton0.setMessage(Component.literal(c.getDataType().key));
                }
                break;
            }
        }
    }

    private static String i18n(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //------------------------------------------------------------ ウィジェット

    /** 一覧のコンテナ箱 (本家 GuiDummyButtonIFTTTContainer、クリック不可)。 */
    private class SlotBox {
        final int x, y;
        final IFTTTContainer container;
        final boolean isThis;

        SlotBox(int x, int y, IFTTTContainer container, boolean isThis) {
            this.x = x;
            this.y = y;
            this.container = container;
            this.isThis = isThis;
        }

        void render(GuiGraphics g) {
            //本家: 90x90 (scale 90/256) で正方形描画
            g.blit(isThis ? CONTAINER_THIS : CONTAINER_THAT, x, y, 90, 90, 0, 0, 512, 512, 512, 512);
            g.drawString(IFTTTScreen.this.font, this.container.getTitle(), x + 3, y, 0xFFFFFF, true);
            String[] explanation = this.container.getExplanation();
            if (explanation.length >= 1) {
                g.drawString(IFTTTScreen.this.font, explanation[0], x + 10, y + 11, 0x000000, false);
            }
            if (explanation.length >= 2) {
                g.drawString(IFTTTScreen.this.font, explanation[1], x + 10, y + 21, 0x000000, false);
            }
        }
    }

    /** テクスチャボタン (本家 GuiButtonAdd/Edit/Delete/Exit 相当)。 */
    private class TexButton extends Button {
        private final ResourceLocation texture;
        private final ResourceLocation hoverTexture;
        private String smallLabel;

        TexButton(int x, int y, int width, int height,
                  ResourceLocation texture, ResourceLocation hoverTexture, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.texture = texture;
            this.hoverTexture = hoverTexture;
        }

        TexButton label(String label) {
            this.smallLabel = label;
            return this;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            ResourceLocation tex = this.hoverTexture != null && this.isHovered() ? this.hoverTexture : this.texture;
            //本家は正方形テクスチャの引き伸ばし描画 (Edit/Del は 22.5px 相当を 22x9 の当たり判定で使用)
            int drawSize = Math.max(this.width, this.height);
            g.blit(tex, this.getX(), this.getY(), drawSize, drawSize, 0, 0, 256, 256, 256, 256);
            if (this.smallLabel != null) {
                int color = this.isHovered() ? 0xFFFFA0 : 0xE0E0E0;
                g.drawCenteredString(IFTTTScreen.this.font, this.smallLabel,
                        this.getX() + (this.width - 1) / 2, this.getY() + (this.height - 11) / 2 + 2, color);
            }
        }
    }
}
