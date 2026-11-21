var renderClass = "jp.ngt.rtm.render.MechanismPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	clutch_s = renderer.registerParts(new Parts("shaft_ny", "clutch_s"));
	clutch_d = renderer.registerParts(new Parts("clutch_d"));
	shaft_d = renderer.registerParts(new Parts("shaft_py"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		var rotation;
		
		GL11.glPushMatrix();
		rotation = renderer.getRotation(entity, Axis.NEGATIVE_Y);
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		clutch_s.render(renderer);
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		rotation = renderer.getRotation(entity, Axis.POSITIVE_Y);
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		shaft_d.render(renderer);
		if(renderer.isPowered(entity))
		{
			GL11.glTranslatef(0.0, 0.1, 0.0);
		}
		clutch_d.render(renderer);
		GL11.glPopMatrix();
	}

	GL11.glPopMatrix();
}
