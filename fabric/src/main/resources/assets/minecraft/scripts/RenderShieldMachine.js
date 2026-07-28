var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body1", "body2"));
	cutter = renderer.registerParts(new Parts("frame1", "frame2", "bit1", "bit2", "bit3", "bit4"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		GL11.glTranslatef(0.0, 3.5, 0.0);
		body.render(renderer);

		GL11.glPushMatrix();

		if(entity != null)
		{
			var bs1 = entity.getResourceState().getDataMap().getInt("Button0");
			var bs2 = entity.getResourceState().getDataMap().getInt("Button1");

			if(bs1 == 1)
			{
				//var msec = renderer.getSystemMillisecond() + (renderer.getSystemSecond() * 60);
				var msec = renderer.getSystemTimeMillis() % 60000;
				var roll = msec * 0.006;//1回転/分
				if(bs2 == 1)
				{
					roll *= -1.0;
				}
				
				GL11.glRotatef(roll, 0.0, 0.0, 1.0);
			}
		}

		cutter.render(renderer);
		GL11.glPopMatrix();
	}

	GL11.glPopMatrix();
}
