var renderClass = "jp.ngt.rtm.render.MachinePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.rtm.render);

//include <scripts/LibBeamEffect.js>

function init(par1, par2)
{
	base = renderer.registerParts(new Parts("base"));
	body = renderer.registerParts(new Parts("body"));
	body_L = renderer.registerParts(new Parts("ptn1", "ptn2", "ptn3", "ptn4"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	var meta = renderer.getMetadata(entity);
	var pitch = renderer.getPitch(entity);
	switch(meta)
	{
	case 0:
		pitch = -pitch;
		break;
	case 1:
		break;
	case 2:
		pitch -= 90.0;
		break;
	case 3:
		pitch -= 90.0;
		break;
	case 4:
		pitch -= 90.0;
		break;
	case 5:
		pitch -= 90.0;
		break;
	}

	var state = renderer.getLightState(entity);
	var color = renderer.getColor(entity);
	if(color <= 0)
	{
		color = 0xFFFFFF;
	}

	if(pass == RenderPass.NORMAL.id)
	{
		base.render(renderer);
		
		renderer.rotate(90.0 + pitch, 'X', 0.0, 0.5, 0.0);
		body.render(renderer);

		if(state == -1)//RSオフ
		{
			NGTRenderHelper.setColor(color);
			body_L.render(renderer);
			NGTRenderHelper.setColor(0xFFFFFF);
		}
	}
	else if(pass == RenderPass.LIGHT.id)
	{
		var currentTime = 0;
		if(entity != null)
		{
			var dataMap = entity.getResourceState().getDataMap();
			var startTime = dataMap.getInt("startTime");
			if(state == -1)//オフ
			{
				if(startTime > 0)
				{
					dataMap.setInt("startTime", 0, 0);
				}
			}
			else if(state == 1)//オン
			{
				var time = renderer.getSystemTimeMillis() % (1000 * 60 * 60 * 24);//1dayでリセット
				if(startTime <= 0)
		 		{
					dataMap.setInt("startTime", time, 0);
				}
				currentTime = time - startTime;
			}
		}
		
		if(state == 1)//RSオン
		{
			renderer.rotate(90.0 + pitch, 'X', 0.0, 0.5, 0.0);

			NGTRenderHelper.setColor(color);
			body_L.render(renderer);
			NGTRenderHelper.setColor(0xFFFFFF);

			GL11.glTranslatef(0.0, 0.5, 0.0);

			if(entity != null)
			{
				renderEffect(entity, currentTime);
			}
		}
	}

	GL11.glPopMatrix();
}

function renderEffect(entity, currentTime)
{
	var dataMap = entity.getResourceState().getDataMap();

	var insideColorP = dataMap.getHex("insideColor");
	var insideAlphaP = dataMap.getHex("insideAlpha");
	var outsideColorP = dataMap.getHex("outsideColor");
	var outsideAlphaP = dataMap.getHex("outsideAlpha");
	setColor(insideColorP, insideAlphaP, outsideColorP, outsideAlphaP);

	var insideRadiusP = dataMap.getDouble("insideRadius");
	var outsideRadiusP = dataMap.getDouble("outsideRadius");
	var repeatP = dataMap.getInt("repeat");
	var lengthP = dataMap.getDouble("length");
	var sectionP = dataMap.getDouble("section");
	setSize(insideRadiusP, outsideRadiusP, repeatP, lengthP, sectionP);

	var centerNoiseXP = dataMap.getDouble("centerNoiseX");
	var centerNoiseYP = dataMap.getDouble("centerNoiseY");
	var radiusNoiseXP = dataMap.getDouble("radiusNoiseX");
	var radiusNoiseYP = dataMap.getDouble("radiusNoiseY");
	setNoise(centerNoiseXP, centerNoiseYP, radiusNoiseXP, radiusNoiseYP);

	var fadeOutTimeP = dataMap.getInt("fadeOutTime");
	var speedXP = dataMap.getDouble("speedX");
	setAnumation(currentTime, fadeOutTimeP, speedXP);
	
	renderBeam();
}
