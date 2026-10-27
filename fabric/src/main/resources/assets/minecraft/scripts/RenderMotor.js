var renderClass = "jp.ngt.rtm.render.MechanismPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	var modelName = par1.getConfig().getName();
	if(modelName.contains("decelerator"))
	{
		shaft = renderer.registerParts(new Parts("shaft"));
	}
	else
	{
		shaft = renderer.registerParts(new Parts("shaft", "coil"));
	}
	body = renderer.registerParts(new Parts("body", "fixture", "bolt"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		body.render(renderer);
		var rotation = renderer.getRotation(entity, Axis.POSITIVE_Y);
		GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		shaft.render(renderer);
	}

	GL11.glPopMatrix();
}
