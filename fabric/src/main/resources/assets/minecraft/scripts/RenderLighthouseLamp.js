var renderClass = "jp.ngt.rtm.render.MachinePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	base = renderer.registerParts(new Parts("base", "body"));
	light = renderer.registerParts(new Parts("light"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	var state = renderer.getLightState(entity);
	var tick = renderer.getTick(entity);
	var rotation = (tick % 60) * 6.0;
	var color = renderer.getColor(entity);
	if(color <= 0)
	{
		color = 0x0D0DFF;
	}

	if(pass == 0)
	{
		if(state == 1)
		{
			GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
		}
		else if(state == -1)
		{
			light.render(renderer);
		}

		base.render(renderer);
	}
	else if(pass == 2)
	{
		if(state == 1)//light ON
		{
			GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
			light.render(renderer);

			GL11.glTranslatef(0.0, 0.5, 0.0);

			for(var i = 0; i < 2; i++)
			{
				GL11.glPushMatrix();
				if(i == 1)
				{
					GL11.glRotatef(180.0, 0.0, 1.0, 0.0);
				}

				GL11.glTranslatef(0.0, 0.0, 0.28);

				var yaw = renderer.getYaw(entity);
				var normal = renderer.getNormal(entity, 0.0, 0.0, 1.0, 0.0, yaw + rotation);
				var lPos = renderer.getLightPos(entity, 0.0, 0.5, 0.28, 0.0, yaw + rotation);

				//0xD0D0FF
				renderer.renderLightEffect(
					normal, lPos,
					16.0, 0.9, 128.0, color, 1, false
				);

				renderer.renderLightEffect(
					normal, lPos,
					0.9, 0.0, 0.0, color, 0, false
				);
				
				GL11.glPopMatrix();
			}
		}
	}

	GL11.glPopMatrix();
}
