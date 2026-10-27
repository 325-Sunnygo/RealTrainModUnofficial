package jp.ngt.rtm.block.decoration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 装飾ブロックのモデル。本家 {@code DecorationModel} の移植。JSON で保存する。
 */
public class DecorationModel implements Cloneable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final DecorationModel DEFAULT_MODEL = getDefaultModel("default");

    public String name;
    public Element[] elements;

    @Override
    public DecorationModel clone() {
        DecorationModel model = new DecorationModel();
        model.elements = new Element[this.elements.length];
        for (int i = 0; i < model.elements.length; ++i) {
            model.elements[i] = this.elements[i].clone();
        }
        model.name = this.name + "_copy";
        return model;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static DecorationModel fromJson(String json) {
        return GSON.fromJson(json, DecorationModel.class);
    }

    /** 通常形状のブロックを返す。 */
    public static DecorationModel getDefaultModel(String name) {
        DecorationModel model = new DecorationModel();
        model.name = name;
        model.elements = new Element[]{Element.getDefaultElement()};
        return model;
    }
}
