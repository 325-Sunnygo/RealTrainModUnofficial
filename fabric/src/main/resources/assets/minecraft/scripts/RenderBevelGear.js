var renderClass = "jp.ngt.rtm.render.MechanismPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	shaft_ny = renderer.registerParts(new Parts("shaft_ny", "gear1_ny", "gear2_ny"));
	shaft_px = renderer.registerParts(new Parts("shaft_px", "gear1_px", "gear2_px"));
	renderNX = false;
	renderPY = false;

	var modelName = par1.getConfig().getName();
	if(modelName.contains("nx"))
	{
		shaft_nx = renderer.registerParts(new Parts("shaft_nx"));
		renderNX = true;
	}
	else if(modelName.contains("py"))
	{
		shaft_py = renderer.registerParts(new Parts("shaft_py"));
		renderPY = true;
	}
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
		shaft_ny.render(renderer);
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		rotation = renderer.getRotation(entity, Axis.POSITIVE_X);
		GL11.glTranslatef(0.0, 0.5, 0.0);
		GL11.glRotatef(rotation, 1.0, 0.0, 0.0);
		GL11.glTranslatef(0.0, -0.5, 0.0);
		shaft_px.render(renderer);
		GL11.glPopMatrix();

		if(renderNX)
		{
			GL11.glPushMatrix();
			rotation = renderer.getRotation(entity, Axis.NEGATIVE_X);
			GL11.glTranslatef(0.0, 0.5, 0.0);
			GL11.glRotatef(rotation, 1.0, 0.0, 0.0);
			GL11.glTranslatef(0.0, -0.5, 0.0);
			shaft_nx.render(renderer);
			GL11.glPopMatrix();
		}

		if(renderPY)
		{
			GL11.glPushMatrix();
			rotation = renderer.getRotation(entity, Axis.POSITIVE_Y);
			GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
			shaft_py.render(renderer);
			GL11.glPopMatrix();
		}
	}

	GL11.glPopMatrix();
}
