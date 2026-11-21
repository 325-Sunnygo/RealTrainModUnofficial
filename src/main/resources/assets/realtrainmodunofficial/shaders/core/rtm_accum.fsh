#version 150

uniform sampler2D Sampler0;   // 今のフレーム
uniform sampler2D Sampler1;   // 10フレームごとの保存画像
uniform float Blend;          // 保存画像を残す割合 (本家の感度)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 cur  = texture(Sampler0, texCoord).rgb;
    vec3 prev = texture(Sampler1, texCoord).rgb;
    fragColor = vec4(mix(cur, prev, Blend), 1.0);
}
