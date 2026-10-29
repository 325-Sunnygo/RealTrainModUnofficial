#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
flat in vec4 flatVertexColor;
uniform int LegacyAlphaFunction;
uniform float LegacyAlphaReference;
uniform int LegacyTexture;
uniform int LegacyFlat;
uniform int LegacyFog;

// Fixed-function alpha tests compare values rounded to the framebuffer's
// normalized alpha precision. Raw float equality loses opaque pixels on GPUs
// whose varying interpolation returns values just below/above 1.0.
bool legacyAlphaPass(float alpha) {
    alpha = floor(clamp(alpha, 0.0, 1.0) * 255.0 + 0.5);
    float reference = floor(clamp(LegacyAlphaReference, 0.0, 1.0) * 255.0 + 0.5);
    if (LegacyAlphaFunction == 512) return false;
    if (LegacyAlphaFunction == 513) return alpha < reference;
    if (LegacyAlphaFunction == 514) return alpha == reference;
    if (LegacyAlphaFunction == 515) return alpha <= reference;
    if (LegacyAlphaFunction == 516) return alpha > reference;
    if (LegacyAlphaFunction == 517) return alpha != reference;
    if (LegacyAlphaFunction == 518) return alpha >= reference;
    return true;
}
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = LegacyTexture == 0 ? vec4(1.0) : texture(Sampler0, texCoord0);
    color *= (LegacyFlat == 1 ? flatVertexColor : vertexColor) * ColorModulator;
    if (!legacyAlphaPass(color.a)) discard;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = LegacyFog == 0 ? color : linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
