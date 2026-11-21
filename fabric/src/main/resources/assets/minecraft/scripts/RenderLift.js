var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("seat1", "seat2", "grip2", "grip3", "pole"));
	grip = renderer.registerParts(new Parts("grip1"));

	var name = par1.getConfig().getName();
	invers = (name.contains("down"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		//GL11.glTranslatef(0.0, 3.5, 0.0);
		if(invers)
		{
			GL11.glRotatef(180.0, 0.0, 1.0, 0.0);
		}
		body.render(renderer);
		grip.render(renderer);
	}

	GL11.glPopMatrix();
}
