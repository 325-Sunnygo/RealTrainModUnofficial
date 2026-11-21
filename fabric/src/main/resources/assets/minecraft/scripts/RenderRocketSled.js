var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importClass(Packages.org.lwjgl.opengl.GL11);
importClass(Packages.jp.ngt.ngtlib.math.NGTMath);
importClass(Packages.jp.ngt.ngtlib.renderer.NGTRenderer);
importClass(Packages.jp.ngt.rtm.render.Parts);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body1", "body2", "rocket"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		body.render(renderer);
	}
	else if(pass == 1 && entity != null)
	{
		GL11.glTranslatef(0.0, 0.55, -9.0);
		GL11.glRotatef(180.0, 0.0, 1.0, 0.0);
		var msec = renderer.getSystemTimeMillis() % 60000;
		var rotation = msec * 0.36;//1回転/s
		var scaleZ = ((NGTMath.sin(rotation) + NGTMath.sin(rotation * 1.7)) * 0.25) + 0.5;
		var notch = entity.getNotch();
		var bs1 = entity.getResourceState().getDataMap().getInt("Button0");
		var bs2 = entity.getResourceState().getDataMap().getInt("Button1");
		if(bs1 == 1 || notch > 0)
		{
			if(bs2 == 0 && notch < 5)
			{
				NGTRenderer.renderFire(0.5, 0.5 * scaleZ + 2.5, 0xFF9000, 25);
			}
			else
			{
				NGTRenderer.renderFire(1.0, 2.0 * scaleZ + 10.0, 0xA000FF, 25);
			}
		}
		
		/*switch(notch)
		{
		case 1:NGTRenderer.renderFire(0.3, 0.5 * scaleZ + 0.5, 0xFF9000, 25);break;
		case 2:NGTRenderer.renderFire(0.5, 1.0 * scaleZ + 2.0, 0xFF3030, 25);break;
		case 3:NGTRenderer.renderFire(0.7, 1.0 * scaleZ + 4.0, 0xFF0060, 25);break;
		case 4:NGTRenderer.renderFire(0.9, 1.0 * scaleZ + 7.0, 0xFF00FF, 25);break;
		case 5:NGTRenderer.renderFire(1.0, 2.0 * scaleZ + 10.0, 0xB000FF, 25);break;
		}*/
	}

	GL11.glPopMatrix();
}
