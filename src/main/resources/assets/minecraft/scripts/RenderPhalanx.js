var renderClass = "jp.ngt.rtm.render.FirearmPartsRenderer";
importClass(Packages.org.lwjgl.opengl.GL11);
importClass(Packages.jp.ngt.ngtlib.math.NGTMath);
importClass(Packages.jp.ngt.ngtlib.renderer.NGTRenderer);
importClass(Packages.jp.ngt.rtm.render.Parts);

function init(par1, par2)
{
	base = renderer.registerParts(new Parts("base1", "base2"));
	partsY = renderer.registerParts(new Parts("table", "radder", "fixture1", "fixture2"));
	partsX = renderer.registerParts(new Parts("magazine", "box1", "box2", "frame1", "frame2", "frame3", "radar"));
	barrel = renderer.registerParts(new Parts("barrel1", "barrel2"));
}

function render(entity, pass, partialTick)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		if(entity == null)
		{
			base.render(renderer);
			partsY.render(renderer);
			partsX.render(renderer);
			barrel.render(renderer);
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

			var roll = 60.0 * entity.getRecoil() * partialTick;
			GL11.glTranslatef(0.0, 2.5, 0.0);
			GL11.glRotatef(roll, 0.0, 0.0, 1.0);
			GL11.glTranslatef(0.0, -2.5, 0.0);

			barrel.render(renderer);
		}
	}

	GL11.glPopMatrix();
}
