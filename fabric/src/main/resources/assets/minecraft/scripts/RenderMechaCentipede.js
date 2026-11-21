var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importClass(Packages.org.lwjgl.opengl.GL11);
importClass(Packages.jp.ngt.ngtlib.math.NGTMath);
importClass(Packages.jp.ngt.ngtlib.renderer.NGTRenderer);
importClass(Packages.jp.ngt.ngtlib.renderer.GLHelper);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.rtm.entity.train.util);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body", "leg"));
	body_light = renderer.registerParts(new Parts("body_light"));
	head = renderer.registerParts(new Parts("head"));
	head_light = renderer.registerParts(new Parts("head_light"));
	tail = renderer.registerParts(new Parts("tail"));
	joint_f = renderer.registerParts(new Parts("joint_f"));
	joint_b = renderer.registerParts(new Parts("joint_b"));
	leg_r = renderer.registerParts(new Parts("leg_r"));
	leg_l = renderer.registerParts(new Parts("leg_l"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	var fPos = 0;
	var rotation = 0.0;
	var isFrontEmpty = true;
	var isBackEmpty = true;
	if(entity != null)
	{
		rotation = entity.wheelRotationR;
		fPos = entity.getVehicleState(TrainState.TrainStateType.Destination);
		var dir = entity.getTrainDirection();
		isFrontEmpty = (entity.getConnectedTrain(dir) == null);
		isBackEmpty = (entity.getConnectedTrain(1 - dir) == null);
	}

	if(pass == RenderPass.NORMAL.id)
	{
		body.render(renderer);

		if(isFrontEmpty)
		{
			head.render(renderer);
		}

		if(isBackEmpty)
		{
			tail.render(renderer);
		}

		rotation -= fPos * 15.0;
		renderLeg(leg_r, NGTMath.normalizeAngle(rotation));
		renderLeg(leg_l, NGTMath.normalizeAngle(rotation + 180.0));
	}
	else if(pass == RenderPass.LIGHT.id)
	{
		GLHelper.disableLighting();
		
		body_light.render(renderer);

		if(isFrontEmpty)
		{
			head_light.render(renderer);
		}
		else
		{
			joint_f.render(renderer);
		}

		if(!isBackEmpty)
		{
			joint_b.render(renderer);
		}

		GLHelper.enableLighting();
	}

	GL11.glPopMatrix();
}

function renderLeg(parts, rotation)
{
	GL11.glPushMatrix();

	var f0 = NGTMath.getSin(rotation);
	var movX = (f0 >= 0.0 ? f0 : f0 * -2.0) * 0.26795 * (0.6 / 2.0);//2-√3
	GL11.glTranslatef(0.0, movX, 0.0);
	var roX = NGTMath.getCos(rotation) * 30.0;
	renderer.rotate(roX, 'X', 0.0, 0.4, 0.0);
	parts.render(renderer);

	GL11.glPopMatrix();
}
