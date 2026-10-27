var renderClass = "jp.ngt.rtm.render.FirearmPartsRenderer";
importClass(Packages.org.lwjgl.opengl.GL11);
importClass(Packages.jp.ngt.ngtlib.math.NGTMath);
importClass(Packages.jp.ngt.ngtlib.renderer.NGTRenderer);
importClass(Packages.jp.ngt.rtm.render.Parts);

function init(par1, par2)
{
	base = renderer.registerParts(new Parts("base1", "base3"));
	body = renderer.registerParts(new Parts("base2", "rocket",
		"barrel_c", "box1_c", "box2_c",
		"barrel_r", "box1_r", "box2_r",
		"barrel_l", "box1_l", "box2_l"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		if(entity == null)
		{
			base.render(renderer);
			body.render(renderer);
		}
		else
		{
			GL11.glRotatef(entity.getBarrelYaw(), 0.0, 1.0, 0.0);

			base.render(renderer);

	        GL11.glTranslatef(0.0, 0.6, -1.15);
			GL11.glRotatef(entity.getBarrelPitch(), 1.0, 0.0, 0.0);
			GL11.glTranslatef(0.0, -0.6, 1.15);
			
			body.render(renderer);
		}
	}

	GL11.glPopMatrix();
}
