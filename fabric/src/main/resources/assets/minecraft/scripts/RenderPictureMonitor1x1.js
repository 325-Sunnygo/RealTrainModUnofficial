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
		var minU = 0.0;
		var minV = 0.0;
		var maxU = 1.0;
		var maxV = 1.0;
		var urlStr = "";
		if(entity != null)
		{
			var dm = entity.getResourceState().getDataMap();
			minU = dm.getDouble("minU");
			minV = dm.getDouble("minV");
			maxU = dm.getDouble("maxU");
			maxV = dm.getDouble("maxV");
			urlStr = dm.getString("url");
		}

		body.render(renderer);

		GL11.glTranslatef(0.0, 0.5, 0.5);
		if(urlStr != "")
		{
			NGTRenderer.renderPicture(1.0, 1.0, true, urlStr, minU, minV, maxU, maxV);
		}
	}

	GL11.glPopMatrix();
}
