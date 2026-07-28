var renderClass = "jp.ngt.rtm.render.MachinePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		var scale = 1.0;
		var pos = "";
		if(entity != null)
		{
			var dm = entity.getResourceState().getDataMap();
			scale = dm.getDouble("scale");
			//var mapType = dm.getString("type");//グレースケールとか路線図とか変更したい

			pos = entity.getX() + "," + entity.getY() + "," + entity.getZ();
		}

		GL11.glScalef(scale, scale, 1.0);
		body.render(renderer);

		GL11.glTranslatef(0.0, 1.0, -0.375);
		if(pos != "")
		{
			NGTRenderer.renderMap(2.88, 1.62, true, pos);
		}
	}

	GL11.glPopMatrix();
}
