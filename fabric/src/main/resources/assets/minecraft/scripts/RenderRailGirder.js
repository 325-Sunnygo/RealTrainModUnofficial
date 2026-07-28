var renderClass = "jp.ngt.rtm.render.RailPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);

function init(par1, par2)
{
	allParts = renderer.registerParts(new Parts("rail", "side", "guide", "guide_p", "guide_n", "fixture", "base", "girder_x1", "girder_x2", "girder_z1p", "girder_z2p", "girder_z1n", "girder_z2n"));
}

function renderRailStatic(tileEntity, posX, posY, posZ, par8, pass)
{
	renderer.renderStaticParts(tileEntity, posX, posY, posZ);
}

function renderRailDynamic(tileEntity, posX, posY, posZ, par8, pass){}

function shouldRenderObject(tileEntity, objName, len, pos)
{
	if(objName == "guide_p")
	{
		return (pos == len - 3);
	}
	else if(objName == "guide_n")
	{
		return (pos == 2);
	}
	else if(objName == "guide")
	{
		return (pos >= 2 && pos <= len - 3);
	}
	else if(objName == "girder_x1" || objName == "girder_x2")
	{
		return (pos == 0 || pos == len - 1 || (pos % 3 == 0));
	}
	else if(objName == "girder_z1p" || objName == "girder_z2p")
	{
		return (pos != len - 1);
	}
	else if(objName == "girder_z1n" || objName == "girder_z2n")
	{
		return (pos != 0);
	}
	return true;
}
