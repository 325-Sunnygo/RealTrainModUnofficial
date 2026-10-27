var renderClass = "jp.ngt.rtm.render.FirearmPartsRenderer";
importClass(Packages.org.lwjgl.opengl.GL11);
importClass(Packages.jp.ngt.ngtlib.math.NGTMath);
importClass(Packages.jp.ngt.ngtlib.renderer.NGTRenderer);
importClass(Packages.jp.ngt.rtm.render.Parts);

function init(par1, par2)
{
	base = renderer.registerParts(new Parts("base1", "base2"));
	partsY = renderer.registerParts(new Parts("table", "radder", "fixture1", "fixture2"));
	partsX = renderer.registerParts(new Parts("box1", "radar", "ram1", "ram2", "ram3"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		if(entity == null)
		{
			base.render(renderer);
			partsY.render(renderer);
			partsX.render(renderer);
		}
		else
		{
			base.render(renderer);
			
			GL11.glRotatef(entity.getBarrelYaw(), 0.0, 1.0, 0.0);

			partsY.render(renderer);

	        GL11.glTranslatef(0.0, 2.5, 0.0);
			GL11.glRotatef(entity.getBarrelPitch(), 1.0, 0.0, 0.0);
			GL11.glTranslatef(0.0, -2.5, 0.0);
			
			partsX.render(renderer);
		}
	}

	GL11.glPopMatrix();
}
