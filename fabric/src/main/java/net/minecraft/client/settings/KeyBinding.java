package net.minecraft.client.settings;

/**
 * 1.7.10 の net.minecraft.client.settings.KeyBinding スタブ。
 * 1.21 では net.minecraft.client.KeyMapping に置き換わり、net.minecraft.client.settings
 * パッケージ自体が存在しない (=モジュール衝突しない)。
 */
public class KeyBinding implements Comparable<KeyBinding> {
    private final String description;
    private int keyCode;
    private final String category;
    private boolean pressed;

    public KeyBinding(String description, int keyCode, String category) {
        this.description = description;
        this.keyCode = keyCode;
        this.category = category;
    }

    // --- SRG 名 (1.7.10 mod のバイトコードが直接呼ぶ) ---
    public boolean func_151468_f() { return isPressedConsume(); } // isPressed (消費)
    public boolean func_151470_d() { return this.pressed; }        // getIsKeyPressed
    public int func_151463_i() { return this.keyCode; }            // getKeyCode
    public void func_151462_b(int keyCode) { this.keyCode = keyCode; } // setKeyCode
    public String func_151464_g() { return this.description; }     // getKeyDescription
    public String func_151466_e() { return this.category; }        // getKeyCategory

    // --- 可読名 ---
    public boolean isPressed() { return isPressedConsume(); }
    public boolean getIsKeyPressed() { return this.pressed; }
    public int getKeyCode() { return this.keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
    public String getKeyDescription() { return this.description; }
    public String getKeyCategory() { return this.category; }

    private boolean isPressedConsume() {
        boolean p = this.pressed;
        this.pressed = false;
        return p;
    }

    @Override
    public int compareTo(KeyBinding other) {
        return this.description.compareTo(other.description);
    }
}
