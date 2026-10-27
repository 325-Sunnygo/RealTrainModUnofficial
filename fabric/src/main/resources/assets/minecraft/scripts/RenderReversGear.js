var renderClass = "jp.ngt.rtm.render.MechanismPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	shaft_m_n = renderer.registerParts(new Parts("shaft_m_n", "gear1_ms", "gear2_ms", "clutch_n"));
	shaft_s = renderer.registerParts(new Parts("shaft_s", "gear1_ss", "gear2_ss", "gear1_sd", "gear2_sd"));
	bearing = renderer.registerParts(new Parts("bearing"));
	clutch = renderer.registerParts(new Parts("clutch_p"));
	shaft_r = renderer.registerParts(new Parts("shaft_r", "gear1_r", "gear2_r"));
	shaft_m_p = renderer.registerParts(new Parts("shaft_m_p", "gear1_md", "gear2_md"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		var rotation;

		rotation = renderer.getRotation(entity, Axis.NEGATIVE_Y);
		GL11.glPushMatrix();
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		shaft_m_n.render(renderer);//入力シャフト
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		renderer.rotate(-(rotation + 9.0), 'Y', 0.0, 0.0, -0.3);
		shaft_s.render(renderer);//カウンターシャフト
		GL11.glPopMatrix();

		rotation = renderer.getRotation(entity, Axis.POSITIVE_Y);
		GL11.glPushMatrix();
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		shaft_m_p.render(renderer);//出力シャフト
		GL11.glPopMatrix();

		var rotationR = rotation;
		if(renderer.isPowered(entity))
		{
			GL11.glTranslatef(0.0, 0.1, 0.0);
		}
		else
		{
			rotationR = 0.0;
		}

		bearing.render(renderer);//軸受

		GL11.glPushMatrix();
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		clutch.render(renderer);//クラッチ
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		renderer.rotate(-rotationR, 'Y', 0.13, 0.0, -0.15);
		shaft_r.render(renderer);//逆転シャフト
		GL11.glPopMatrix();
	}

	GL11.glPopMatrix();
}
