uniform sampler2D texture;

varying vec4 position;
varying vec3 normal;

const int LIGHT_ID = 7;

void main(){
	vec4 color = texture2DProj(texture, gl_TexCoord[0]);
	vec3 light = normalize((gl_LightSource[LIGHT_ID].position * position.w - gl_LightSource[LIGHT_ID].position.w * position).xyz);
	vec3 fnormal = normalize(normal);
	float diffuse = max(dot(light, fnormal), 0.0);

	vec3 view = -normalize(position.xyz);
	vec3 halfway = normalize(light + view);
	float specular = pow(max(dot(fnormal, halfway), 0.0), gl_FrontMaterial.shininess);
	gl_FragColor = color * gl_LightSource[LIGHT_ID].diffuse * diffuse 
		+ gl_FrontLightProduct[0].specular * specular
		+ color * gl_LightSource[LIGHT_ID].ambient;
}