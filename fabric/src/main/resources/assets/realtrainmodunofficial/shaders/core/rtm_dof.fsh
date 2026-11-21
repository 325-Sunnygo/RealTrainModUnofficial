#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 InSize;
uniform float Weights[24];
uniform int Pass;
uniform float Threshold;
in vec2 texCoord;
out vec4 fragColor;
vec4 sampleColor(vec2 uv) {
    if(Pass==0) return texture(Sampler1,uv).r<=Threshold?vec4(texture(Sampler0,uv).rgb,1):vec4(0);
    return texture(Sampler0,uv);
}
void main() {
    vec4 color=vec4(0);
    for(int i=-23;i<=23;i++) {
        vec2 delta=Pass==0?vec2(float(i)/InSize.x,0):vec2(0,float(i)/InSize.y);
        color+=sampleColor(texCoord+delta)*Weights[abs(i)];
    }
    fragColor=color;
}
