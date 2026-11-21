#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec3 sky=texture(Sampler0,texCoord).rgb;
    vec3 world=texture(Sampler1,texCoord).rgb;
    float depth=texture(Sampler2,texCoord).r;
    bool geometry=depth<0.999999 || max(max(world.r,world.g),world.b)>0.0001;
    fragColor=vec4(geometry?world:sky,1.0);
}
