var renderClass = "jp.ngt.rtm.render.WirePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.ngtlib.renderer);

function init(par1, par2)
{
	name = par1.getConfig().getName();
	modelType = name.equals("illumination") ? 0 : 1;
	wire = renderer.registerParts(new Parts("wire"));
	light = renderer.registerParts(new Parts("light"));
}

function renderWireStatic(entity, connection, vec, par8, pass)
{
	;
}

function renderWireDynamic(entity, connection, vec, par8, pass)
{
	GL11.glPushMatrix;

	var color = connection.getResourceState().color;
	if(color <= 0)
	{
		color = 0xFFFFFF;
	}

	if(pass == RenderPass.NORMAL.id)
	{
		if(modelType == 0)
		{
			GLHelper.setColor(color, 0xFF);
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, light);
			GLHelper.setColor(0xFFFFFF, 0xFF);
		}
		else if(modelType == 1)
		{
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, light);
		}
	}
	else if(pass == RenderPass.TRANSPARENT.id)
	{
		if(modelType == 0)
		{
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, wire);
		}
		else if(modelType == 1)
		{
			GLHelper.setColor(color, 0xFF);
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, wire);
			GLHelper.setColor(0xFFFFFF, 0xFF);
		}
	}
	else if(pass == RenderPass.LIGHT.id)
	{
		if(modelType == 0)
		{
			GLHelper.setColor(color, 0xFF);
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, light);
			GLHelper.setColor(0xFFFFFF, 0xFF);
		}
		else if(modelType == 1)
		{
			renderer.renderWireDeflection(entity, connection, vec, par8, pass, light);
		}
	}

	GL11.glPopMatrix;
}

function shouldRenderObject(entity, len, pos, pass)
{
	if(pass == RenderPass.TRANSPARENT.id)
	{
		return true;
	}
	else
	{
		var flagTime = ((renderer.getSystemTimeMillis() % 2000) < 1000);
		var flagPos = ((pos % 2) == 0);
		var flagLight = (flagTime && flagPos) || (!flagTime && !flagPos);
		return (flagLight && pass == (RenderPass.LIGHT.id)) || (!flagLight && (pass == RenderPass.NORMAL.id));
	}
}
