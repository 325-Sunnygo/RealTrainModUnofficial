importPackage(Packages.org.lwjgl.opengl);

function renderGui(entity, gui)
{
	var halfW = gui.getWidth() / 2;
	//x,y,u,v,w,h,pxl
	gui.drawRectangle(halfW - 208, gui.getHeight() - 256, 0, 0, 416, 256, 512);
}