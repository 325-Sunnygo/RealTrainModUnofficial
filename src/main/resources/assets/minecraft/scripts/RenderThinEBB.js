var renderClass = "jp.ngt.rtm.render.MachinePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	name = par1.getConfig().getName();
	isEBB = (name.indexOf("EBB") >= 0);
	
	body = renderer.registerParts(new Parts("body"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		var picture = "";
		var resolution = 16;
		var sizeX = 1.0;
		var sizeY = 1.0;
		var offsetU = 0;
		var offsetV = 0;
		if(entity != null)
		{
			var dm = entity.getResourceState().getDataMap();
			picture = dm.getString("picture");
			resolution = dm.getInt("resolution");
			sizeX = dm.getDouble("sizeX");
			sizeY = dm.getDouble("sizeY");
			offsetU = dm.getInt("offsetU");
			offsetV = dm.getInt("offsetV");
		}

		GL11.glPushMatrix();
		GL11.glScalef(sizeX, sizeY, 1.0);
		body.render(renderer);
		GL11.glPopMatrix();

		GL11.glTranslatef(0.0, sizeY * 0.5, -0.4375);
		if(!picture.isEmpty())
		{
			if(isEBB)
			{
				NGTRenderer.renderEBB(sizeX, sizeY, true, picture, resolution, offsetU, offsetV);
			}
			else
			{
				NGTRenderer.renderPicture(sizeX, sizeY, true, picture, 0.0, 0.0, 1.0, 1.0);
			}
		}
	}

	GL11.glPopMatrix();
}
