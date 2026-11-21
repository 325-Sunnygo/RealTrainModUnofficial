package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.client.renderer.DecorationRenderer;
import com.portofino.realtrainmodunofficial.network.DecorationRegisterPayload;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.decoration.DecorationStore;
import jp.ngt.rtm.block.decoration.Element;
import jp.ngt.rtm.block.decoration.Face;
import jp.ngt.rtm.block.decoration.Face.FaceType;
import jp.ngt.rtm.item.ItemDecoration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 装飾ブロックのモデル編集画面。本家 {@code GuiDecorationBlock} の移植。
 *
 * <p>本家と同じ 3 列構成 (Element / Face / Vertex+UV)。列の背景パネル・選択ハイライトも
 * 本家の配色 (0x707070 / 0xA0A0A0 / 0xD0D0D0 + 赤枠)。プレビューは左側、右ドラッグで回転。
 */
public class DecorationEditScreen extends Screen {

    private static final int W_ELEMENT = 60;
    private static final int W_FACE = 60;
    private static final int W_VERTEX = 60;
    private static final int W_UV = 40;
    private static final int H_FIELD = 20;
    private static final int THICKNESS = 3;
    private static final int Y_START = 35;

    private final Player player;

    private DecorationModel model;
    private EditBox modelNameField;

    private final float[] moveVec = new float[3];
    private EditBox moveVecField;
    private boolean lockUV = true;

    private final List<EditBox> elementList = new ArrayList<>();
    private Element selectedElement;

    private final List<EditBox> faceList = new ArrayList<>();
    private Face selectedFace;

    private EditBox faceShadowField;
    private final List<EditBox> vertexList = new ArrayList<>();
    private final List<EditBox> uvList = new ArrayList<>();

    private float[] selectedVertex;
    /** move の対象 (Element / Face / float[])。本家 selectedObject。 */
    private Object selectedObject;

    //初期角は見やすい斜め (本家は 0,0 だが正面だと 1 面しか見えない)
    private float rotationYaw = -45.0F;
    private float rotationPitch = 25.0F;

    public DecorationEditScreen(Player player) {
        super(Component.literal("Decoration"));
        this.player = player;
        DecorationModel stored = DecorationStore.INSTANCE.getModel(
            ItemDecoration.getModelName(player.getMainHandItem()));
        DecorationModel copy = stored.clone();
        copy.name = stored.name;   //clone は "_copy" を付けるので戻す
        this.setModel(copy);
    }

    public void setModel(DecorationModel par1) {
        this.model = par1;
        this.selectedElement = this.model.elements[0];
        this.selectedFace = this.selectedElement.faces[0];
        this.selectedVertex = this.selectedFace.vertex[0];
        this.selectedObject = null;
    }

    /** 選択画面から戻ったとき用。 */
    void applyLoadedModel(DecorationModel loaded) {
        DecorationModel copy = loaded.clone();
        copy.name = loaded.name;
        this.setModel(copy);
        if (this.minecraft != null) {
            this.rebuild();
        }
    }

    void setFaceTexture(String name) {
        this.selectedFace.texture = name;
    }

    private int columnsStartX() {
        return this.width - (10 + W_ELEMENT + W_FACE + W_VERTEX + W_UV + THICKNESS * 5);
    }

    @Override
    protected void init() {
        int hw = this.width / 2;
        this.clearWidgets();

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.saveModel();
            this.onClose();
        }).bounds(hw - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());

        int startX = this.columnsStartX();
        int buttonY = Y_START - 20 - THICKNESS * 2;
        int hInc = H_FIELD + THICKNESS * 2;

        //左上: load/save + モデル名 (本家と同配置)
        this.addRenderableWidget(Button.builder(Component.literal("load model"), b -> this.openLoadModelGUI())
            .bounds(30, Y_START - 22, 62, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("save model"), b -> this.saveModel())
            .bounds(94, Y_START - 22, 62, 20).build());
        this.modelNameField = this.addField(30, Y_START, 126, H_FIELD, this.model.name);

        //左下: move + lock UV
        this.addRenderableWidget(Button.builder(Component.literal("move"), b -> {
            this.saveFieldToModel();
            this.strToVtx(this.moveVecField.getValue(), this.moveVec, 0);
            this.moveObject();
            this.rebuild();
        }).bounds(30, this.height - 82, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("lock UV : " + this.lockUV), b -> {
            this.lockUV ^= true;
            b.setMessage(Component.literal("lock UV : " + this.lockUV));
        }).bounds(72, this.height - 82, 84, 20).build());
        this.moveVecField = this.addField(30, this.height - 60, 126, H_FIELD, this.vtxToStr(this.moveVec, 0, 3));

        //Element 列
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.editElement(0))
            .bounds(startX, buttonY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("C"), b -> this.editElement(1))
            .bounds(startX + 20, buttonY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> this.editElement(2))
            .bounds(startX + 40, buttonY, 20, 20).build());

        this.elementList.clear();
        for (int i = 0; i < this.model.elements.length; ++i) {
            this.elementList.add(this.addField(startX, Y_START + i * hInc, W_ELEMENT, H_FIELD,
                this.model.elements[i].name));
        }

        //Face 列
        int faceX = startX + W_ELEMENT + THICKNESS * 2;
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.editFace(0))
            .bounds(faceX, buttonY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("C"), b -> this.editFace(1))
            .bounds(faceX + 20, buttonY, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> this.editFace(2))
            .bounds(faceX + 40, buttonY, 20, 20).build());

        this.faceList.clear();
        for (int i = 0; i < this.selectedElement.faces.length; ++i) {
            this.faceList.add(this.addField(faceX, Y_START + i * hInc, W_FACE, H_FIELD,
                this.selectedElement.faces[i].name));
        }

        //Vertex 列: テクスチャサムネ (32x32 ボタン) + 面タイプ + shadow + 頂点/UV
        int vtxX = faceX + W_FACE + THICKNESS * 2;
        this.addRenderableWidget(Button.builder(Component.empty(), b -> this.openIconSelectGUI())
            .bounds(vtxX, Y_START - 12, 32, 32)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("texture: " + this.selectedFace.texture)))
            .build());
        this.addRenderableWidget(Button.builder(
            Component.literal(this.selectedFace.type == null ? "-" : this.selectedFace.type.toString()), b -> {
                int next = (this.selectedFace.type == null ? 0 : this.selectedFace.type.ordinal() + 1)
                    % FaceType.values().length;
                this.selectedFace.type = FaceType.values()[next];
                b.setMessage(Component.literal(this.selectedFace.type.toString()));
            }).bounds(vtxX + 35, Y_START, W_UV + 25, 20).build());
        this.faceShadowField = this.addField(vtxX, Y_START + hInc, 60, H_FIELD,
            String.valueOf(this.selectedFace.shadow));

        this.vertexList.clear();
        this.uvList.clear();
        int uvX = vtxX + W_VERTEX + THICKNESS * 2;
        for (int i = 0; i < this.selectedFace.vertex.length; ++i) {
            float[] vtx = this.selectedFace.vertex[i];
            this.vertexList.add(this.addField(vtxX, Y_START + (2 + i) * hInc, W_VERTEX, H_FIELD,
                this.vtxToStr(vtx, 0, 3)));
            this.uvList.add(this.addField(uvX, Y_START + (2 + i) * hInc, W_UV, H_FIELD,
                this.vtxToStr(vtx, 3, 2)));
        }
    }

    private EditBox addField(int x, int y, int w, int h, String text) {
        EditBox field = new EditBox(this.font, x, y, w, h, Component.empty());
        field.setMaxLength(256);
        field.setValue(text);
        this.addRenderableWidget(field);
        return field;
    }

    /** クリックで行選択 (本家 setElement / setFace / setVertex)。 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < this.elementList.size(); ++i) {
                if (this.elementList.get(i).isMouseOver(mouseX, mouseY)) {
                    Element element = this.model.elements[i];
                    if (element != this.selectedElement || element != this.selectedObject) {
                        this.saveFieldToModel();
                        this.selectedElement = element;
                        this.selectedObject = element;
                        this.selectedFace = element.faces[0];
                        this.selectedVertex = this.selectedFace.vertex[0];
                        this.rebuild();
                        return true;
                    }
                }
            }
            for (int i = 0; i < this.faceList.size(); ++i) {
                if (this.faceList.get(i).isMouseOver(mouseX, mouseY)) {
                    Face face = this.selectedElement.faces[i];
                    if (face != this.selectedFace || face != this.selectedObject) {
                        this.saveFieldToModel();
                        this.selectedFace = face;
                        this.selectedObject = face;
                        this.selectedVertex = face.vertex[0];
                        this.rebuild();
                        return true;
                    }
                }
            }
            for (int i = 0; i < this.vertexList.size(); ++i) {
                if (this.vertexList.get(i).isMouseOver(mouseX, mouseY)) {
                    float[] vtx = this.selectedFace.vertex[i];
                    if (vtx != this.selectedVertex || vtx != this.selectedObject) {
                        this.saveFieldToModel();
                        this.selectedVertex = vtx;
                        this.selectedObject = vtx;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 本家 saveFieldToModel。 */
    private void saveFieldToModel() {
        this.selectedFace.shadow = this.getFloat(this.faceShadowField.getValue(), this.selectedFace.shadow);
        for (int i = 0; i < this.selectedFace.vertex.length; ++i) {
            this.strToVtx(this.vertexList.get(i).getValue(), this.selectedFace.vertex[i], 0);
            this.strToVtx(this.uvList.get(i).getValue(), this.selectedFace.vertex[i], 3);
        }
        for (int i = 0; i < this.selectedElement.faces.length && i < this.faceList.size(); ++i) {
            this.selectedElement.faces[i].name = this.faceList.get(i).getValue();
        }
        for (int i = 0; i < this.model.elements.length && i < this.elementList.size(); ++i) {
            this.model.elements[i].name = this.elementList.get(i).getValue();
        }
        this.model.name = this.modelNameField.getValue();
    }

    private void rebuild() {
        this.init(this.minecraft, this.width, this.height);
    }

    private String vtxToStr(float[] vtx, int start, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + len; ++i) {
            sb.append(vtx[i]);
            if (i < start + len - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private void strToVtx(String s, float[] fa, int start) {
        String[] sa = s.split(",");
        for (int i = 0; i < sa.length && i + start < fa.length; ++i) {
            fa[i + start] = this.getFloat(sa[i], fa[i + start]);
        }
    }

    private float getFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 本家 editElement: 0=追加 1=複製 2=削除。 */
    private void editElement(int par1) {
        this.saveFieldToModel();
        if (par1 == 0 || par1 == 1) {
            List<Element> list = new ArrayList<>(List.of(this.model.elements));
            Element newElement = par1 == 0 ? Element.getDefaultElement() : this.selectedElement.clone();
            newElement.name = "element" + (list.size() + 1);
            list.add(newElement);
            this.model.elements = list.toArray(new Element[0]);
        } else if (this.model.elements.length >= 2) {
            List<Element> list = new ArrayList<>(List.of(this.model.elements));
            list.remove(this.selectedElement);
            this.model.elements = list.toArray(new Element[0]);
            this.selectedElement = this.model.elements[0];
            this.selectedFace = this.selectedElement.faces[0];
            this.selectedVertex = this.selectedFace.vertex[0];
            this.selectedObject = null;
        }
        this.rebuild();
    }

    /** 本家 editFace。 */
    private void editFace(int par1) {
        this.saveFieldToModel();
        if (par1 == 0 || par1 == 1) {
            List<Face> list = new ArrayList<>(List.of(this.selectedElement.faces));
            Face newFace = par1 == 0 ? Face.getDefaultFace() : this.selectedFace.clone();
            newFace.name = "face" + (list.size() + 1);
            list.add(newFace);
            this.selectedElement.faces = list.toArray(new Face[0]);
        } else if (this.selectedElement.faces.length >= 2) {
            List<Face> list = new ArrayList<>(List.of(this.selectedElement.faces));
            list.remove(this.selectedFace);
            this.selectedElement.faces = list.toArray(new Face[0]);
            this.selectedFace = this.selectedElement.faces[0];
            this.selectedVertex = this.selectedFace.vertex[0];
            this.selectedObject = null;
        }
        this.rebuild();
    }

    private void moveObject() {
        if (this.selectedObject instanceof Element element) {
            element.addVec(this.moveVec, this.lockUV);
        } else if (this.selectedObject instanceof Face face) {
            face.addVec(this.moveVec, this.lockUV);
        } else if (this.selectedObject instanceof float[] vtx) {
            Face.addVecToVertex(vtx, this.selectedFace.type, this.moveVec, this.lockUV);
        }
    }

    private void openLoadModelGUI() {
        this.saveFieldToModel();
        this.minecraft.setScreen(new DecorationSelectScreen(this, DecorationSelectScreen.Mode.MODEL));
    }

    private void openIconSelectGUI() {
        this.saveFieldToModel();
        this.minecraft.setScreen(new DecorationSelectScreen(this, DecorationSelectScreen.Mode.TEXTURE));
    }

    /** 本家 saveModel: サーバーへ登録 + 手持ちへモデル名。 */
    private void saveModel() {
        this.saveFieldToModel();
        PacketDistributor.sendToServer(new DecorationRegisterPayload(this.model.toJson()));
        DecorationStore.INSTANCE.setModel(this.model.toJson());
        ItemDecoration.setModel(this.player.getMainHandItem(), this.model.name);
    }

    /** 列の背景パネル (本家 drawScreen の配色)。ウィジェットより下に描く。 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int t2 = THICKNESS * 2;
        int hInc = H_FIELD + THICKNESS * 2;
        int startX = this.columnsStartX() - THICKNESS;
        int y2 = 20 + t2;
        int startY = Y_START - THICKNESS - y2;

        //Element 列
        graphics.fill(startX, startY, startX + W_ELEMENT + t2,
            startY + this.elementList.size() * hInc + y2 + THICKNESS, 0xFF707070);
        //Face 列
        int faceX = startX + W_ELEMENT + t2;
        graphics.fill(faceX, startY, faceX + W_FACE + t2,
            startY + this.faceList.size() * hInc + y2 + THICKNESS, 0xFFA0A0A0);
        //Vertex + UV 列
        int vtxX = faceX + W_FACE + t2;
        graphics.fill(vtxX, startY, vtxX + W_VERTEX + t2 + W_UV + t2,
            startY + (this.vertexList.size() + 2) * hInc + y2 + THICKNESS, 0xFF8A8A8A);

        //選択行のハイライト
        int elemIndex = indexOf(this.model.elements, this.selectedElement);
        if (elemIndex >= 0 && elemIndex < this.elementList.size()) {
            EditBox f = this.elementList.get(elemIndex);
            graphics.fill(startX, f.getY() - THICKNESS, startX + W_ELEMENT + t2,
                f.getY() + H_FIELD + THICKNESS, 0xFFD0D0D0);
        }
        int faceIndex = indexOf(this.selectedElement.faces, this.selectedFace);
        if (faceIndex >= 0 && faceIndex < this.faceList.size()) {
            EditBox f = this.faceList.get(faceIndex);
            graphics.fill(faceX, f.getY() - THICKNESS, faceX + W_FACE + t2,
                f.getY() + H_FIELD + THICKNESS, 0xFFD0D0D0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int startX = this.columnsStartX();
        int faceX = startX + W_ELEMENT + THICKNESS * 2;
        int vtxX = faceX + W_FACE + THICKNESS * 2;

        //選択中オブジェクト (move の対象) の赤枠
        EditBox target = null;
        int targetW = W_ELEMENT;
        int elemIndex = indexOf(this.model.elements, this.selectedElement);
        int faceIndex = indexOf(this.selectedElement.faces, this.selectedFace);
        int vtxIndex = indexOf(this.selectedFace.vertex, this.selectedVertex);
        if (this.selectedObject instanceof Element && elemIndex >= 0 && elemIndex < this.elementList.size()) {
            target = this.elementList.get(elemIndex);
        } else if (this.selectedObject instanceof Face && faceIndex >= 0 && faceIndex < this.faceList.size()) {
            target = this.faceList.get(faceIndex);
            targetW = W_FACE;
        } else if (this.selectedObject instanceof float[] && vtxIndex >= 0 && vtxIndex < this.vertexList.size()) {
            target = this.vertexList.get(vtxIndex);
            targetW = W_VERTEX;
        }
        if (target != null) {
            graphics.renderOutline(target.getX() - 2, target.getY() - 2, targetW + 4, H_FIELD + 4, 0xFFFF0000);
        }

        //面テクスチャのサムネイル (32x32 のボタンの上へ直バインドで貼る)
        ResourceLocation texture = DecorationRenderer.toTexture(this.selectedFace.texture);
        MultiBufferSource.BufferSource buffer = this.minecraft.renderBuffers().bufferSource();
        var pose = graphics.pose().last();
        var consumer = buffer.getBuffer(RenderType.text(texture));
        int tx = vtxX;
        int ty = Y_START - 12;
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, tx, ty, 200).setColor(255, 255, 255, 255).setUv(0, 0).setLight(light);
        consumer.addVertex(pose, tx, ty + 32, 200).setColor(255, 255, 255, 255).setUv(0, 1).setLight(light);
        consumer.addVertex(pose, tx + 32, ty + 32, 200).setColor(255, 255, 255, 255).setUv(1, 1).setLight(light);
        consumer.addVertex(pose, tx + 32, ty, 200).setColor(255, 255, 255, 255).setUv(1, 0).setLight(light);
        buffer.endBatch();

        //面テクスチャ名
        graphics.drawString(this.font, this.selectedFace.texture == null ? "-" : this.selectedFace.texture,
            30, this.height - 96, 0xA0A0A0);

        //プレビュー: 右ドラッグで回転 (本家はポインタ位置、こちらはドラッグ量で回す)
        long window = this.minecraft.getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS) {
            this.rotationYaw = (this.width / 2.0F - mouseX) * 1.0F;
            this.rotationPitch = (this.height / 2.0F - mouseY) * 1.0F;
        }
        int previewX = 30 + (startX - 40) / 2;
        int previewY = Y_START + (this.height - Y_START - 110) / 2;
        float scale = Math.max(30.0F, Math.min(70.0F, (startX - 80) * 0.35F));
        drawModelPreview(graphics, this.model, previewX, previewY, scale, this.rotationPitch, this.rotationYaw);
    }

    /** モデルの平行投影プレビュー。選択画面のモデル格子とも共用。 */
    static void drawModelPreview(GuiGraphics graphics, DecorationModel model,
                                 int x, int y, float scale, float pitch, float yaw) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5F, -0.5F, -0.5F);
        MultiBufferSource.BufferSource buffer =
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        DecorationRenderer.renderModel(model, pose, buffer, LightTexture.FULL_BRIGHT);
        buffer.endBatch();
        pose.popPose();
    }

    private static <T> int indexOf(T[] array, T value) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(float[][] array, float[] value) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
