package jp.ngt.mccompat;

import java.awt.image.BufferedImage;

/**
 * パックスクリプト互換: 1.7.10/1.12 の DynamicTexture(BufferedImage)。
 */
public class DynamicTexture {
    public final BufferedImage image;

    public DynamicTexture(BufferedImage image) {
        this.image = image;
    }
}
