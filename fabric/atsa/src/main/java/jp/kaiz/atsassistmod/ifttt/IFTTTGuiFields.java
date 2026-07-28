package jp.kaiz.atsassistmod.ifttt;

/**
 * IFTTT 設定 GUI のテキスト欄アクセス (本家 GUIIFTTTMaterial の getTextField* 相当)。
 * 共通コード (IFTTTContainer.setFromGui) がクライアントクラスへ直接依存しないための橋渡し。
 */
public interface IFTTTGuiFields {

    int getTextFieldInt(int number);

    String getTextFieldText(int number);

    int textFieldLength();
}
