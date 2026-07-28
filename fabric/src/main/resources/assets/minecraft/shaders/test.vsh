uniform int time;//24000x50

varying vec4 position;
varying vec3 normal;

const float PI = 3.1415927;

void main(){
	position = gl_ModelViewMatrix * gl_Vertex;
	normal = normalize(gl_NormalMatrix * gl_Normal);

	gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
	gl_Position = ftransform();//固定機能による座標変換
	
	//2s周期
	gl_Position.x += sin((2 * PI * time / 2000) + (PI * gl_Vertex.z)) * 0.125;
}