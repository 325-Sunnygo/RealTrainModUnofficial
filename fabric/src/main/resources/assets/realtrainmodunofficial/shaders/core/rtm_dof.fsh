#version 150

uniform sampler2D Sampler0;   // 色
uniform sampler2D Sampler1;   // 深度 (メインターゲットのもの)

uniform vec2  InSize;         // 画面サイズ (px)
uniform vec2  BlurDir;        // (1,0)=横 / (0,1)=縦
uniform float FocusDepth;     // ピント距離 (m)
uniform float Bokeh;          // ボケの強さ 0..1 (F値と焦点距離から算出)
uniform float NearPlane;
uniform float FarPlane;
uniform float MaxRadius;      // 最大ぼかし半径 (px)

in vec2 texCoord;
out vec4 fragColor;

const int TAPS = 12;

// 深度バッファの値 → 実距離 (m)
float linearDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - z * (FarPlane - NearPlane));
}

// 錯乱円: ピント面からどれだけ外れているか (0=合焦, 1=最大ボケ)
float coc(vec2 uv) {
    float lin = linearDepth(texture(Sampler1, uv).r);
    // 被写界深度は近距離ほど浅い。分母に FocusDepth を入れて、
    // 「20m にピントを合わせたとき 40m はボケるが、200m のとき 220m はボケない」を再現する。
    float dev = abs(lin - FocusDepth) / (FocusDepth * 0.6 + 1.5);
    return clamp(dev, 0.0, 1.0) * Bokeh;
}

void main() {
    float centerCoc = coc(texCoord);
    float radius = centerCoc * MaxRadius;

    if (radius < 0.75) {
        fragColor = texture(Sampler0, texCoord);
        return;
    }

    vec2 stepUv = BlurDir / InSize;
    vec4 sum = vec4(0.0);
    float wsum = 0.0;

    for (int i = -TAPS; i <= TAPS; ++i) {
        float fi = float(i);
        vec2 uv = texCoord + stepUv * (fi / float(TAPS)) * radius;

        // ガウス重み
        float w = exp(-0.5 * (fi * fi) / (float(TAPS) * 0.45 * float(TAPS) * 0.45));

        // 合焦している手前の被写体が、後ろのボケへにじみ出さないようにする。
        // (これをやらないと、ピントの合った列車の輪郭が背景に溶けてにじむ)
        float sampleCoc = coc(uv);
        w *= max(sampleCoc, 0.08);

        sum += texture(Sampler0, uv) * w;
        wsum += w;
    }

    fragColor = wsum > 0.0001 ? vec4(sum.rgb / wsum, 1.0) : texture(Sampler0, texCoord);
}
