var renderClass = "jp.ngt.rtm.render.OrnamentPartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.rtm.block);

//size(x=229, y=180, z=342)
//slot(w=9, h=6)
//228(38x6), 190(38x5), 342(38x9)

function init(par1, par2)
{
	main = renderer.registerParts(new Parts("p_side1", "p_side2", "bottom", "frame", "back", "side"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	if(pass == 0)
	{
		main.render(renderer);

		var isize = 0.38;//ブロックの描画サイズ

		for(var w = 0; w < 6; ++w)
		{
			for(var d = 0; d < 9; ++d)
			{
				var slotIndex = w * 9 + d;
				var item = renderer.getInventoryItem(entity, slotIndex);
				if(item != null)
				{
					var size = renderer.getStackSize(item);
					var hCount = (size / 64) * 5;//64/5個ごとに1段増える
					for(var h = 0; h < hCount; ++h)
					{
						GL11.glPushMatrix();
						var moveX = (w - 2.5) * isize;
						var moveY = (h + 1) * isize;
						var moveZ = (d - 4) * isize;
						GL11.glTranslatef(moveX, moveY, moveZ);
						GL11.glScalef(isize, isize, isize);
						renderer.renderItem(entity, item)
						GL11.glPopMatrix();
					}
				}
			}
		}
	}
	
	GL11.glPopMatrix();
}
