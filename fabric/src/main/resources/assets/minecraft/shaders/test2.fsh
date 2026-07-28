uniform sampler2D texture;
 
varying vec4 position;
varying vec3 normal;

const int LIGHT_ID = 7;
const vec3 LIGHT_POS = vec3(10.0, 100.0, 10.0);

/* フォーンシェーディング */
void main(){
	vec4 color = texture2DProj(texture, gl_TexCoord[0]);
	vec3 fnormal = normalize(normal);
	vec3 light = LIGHT_POS - position.xyz;
	//vec3 light = gl_LightSource[LIGHT_ID].position.xyz - position.xyz;

	/* 光源までの距離 */
    float dis = length(light);

	light = normalize(light);

	/* 減衰係数 */
	float attenuation = 1.0 / (
		0.0 +
		0.0 * dis +
		1.0 * dis * dis);
	/*float attenuation = 1.0 / (
		gl_LightSource[LIGHT_ID].constantAttenuation +
		gl_LightSource[LIGHT_ID].linearAttenuation * dis +
		gl_LightSource[LIGHT_ID].quadraticAttenuation * dis * dis);*/
	//一定減衰率、一次減衰率、二次減衰率

	/* ディフューズ */
	float diffuse = dot(light, fnormal);

	/* アンビエント */
    //gl_FragColor = color * gl_FrontLightProduct[0].ambient;
	gl_FragColor = color * 0.7;
	
	if (diffuse > 0.0){
		/* 反射ベクトル */
		vec3 viewVec = normalize(-position.xyz);
		vec3 reflectVec = reflect(-light, fnormal);
		float shininess = 0.6;//gl_FrontMaterial.shininess;
		float specular = pow(max(dot(viewVec, reflectVec), 0.0), shininess);
		/*gl_FragColor += 
			gl_FrontLightProduct[0].diffuse * diffuse * attenuation +
			gl_FrontLightProduct[0].specular * specular * attenuation;*/
		gl_FragColor += 
			0.9 * diffuse * attenuation +
			0.8 * specular * attenuation;
    }
}
