var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.org.lwjgl.input);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.renderer);

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body", "seat", "ctrler",
		"wing_f", "hp", "wing_b1", "wing_b2",
		"rocket1", "rocket2", "rocket3", "tank", "missile1", "missile2"));
	canopy = renderer.registerParts(new Parts("canopy"));
	canopyWin = renderer.registerParts(new Parts("window"));
	engine = renderer.registerParts(new Parts("eng1", "eng2", "eng3"));
	gun = renderer.registerParts(new Parts("gun1", "gun2"));
	lgfc1 = renderer.registerParts(new Parts("landg_fc1"));
	lgfc2 = renderer.registerParts(new Parts("landg_fc2"));
	lgf = renderer.registerParts(new Parts("landg_fw", "landg_fp"));
	lgbc1 = renderer.registerParts(new Parts("landg_bc1"));
	lgb = renderer.registerParts(new Parts("landg_bw", "landg_bp3"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		body.render(renderer);

		renderCanopy(entity, canopy);

		GL11.glPushMatrix();
		var playerYaw = renderer.getPlayerYaw(entity);
		GL11.glTranslatef(0.0, 1.1, 4.0);
		GL11.glRotatef(playerYaw, 0.0, 1.0, 0.0);
		GL11.glTranslatef(0.0, -1.1, -4.0);
		gun.render(renderer);
		GL11.glPopMatrix();

		renderEngine(entity, 0);

		//ランディングギア(デフォルトで展開状態)
		var movLg = renderer.getPantographMovementFront(entity);
		//前輪カバー前
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 1.0, 3.4);
		GL11.glRotatef(movLg * -70.0, 1.0, 0.0, 0.0);
		GL11.glTranslatef(0.0, -1.0, -3.4);
		lgfc1.render(renderer);
		GL11.glPopMatrix();

		//前輪カバー後
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 0.5, 2.25);
		//0.0~0.5~1.0 → 0.0~1.0~0.0
		var fcPitch = 90.0 * (movLg > 0.5 ? (1.0 - movLg) : movLg) * 2.0;
		GL11.glRotatef(fcPitch, 1.0, 0.0, 0.0);
		GL11.glTranslatef(0.0, -0.5, -2.25);
		lgfc2.render(renderer);
		GL11.glPopMatrix();

		//前輪
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 1.0, 3.3);
		GL11.glRotatef(movLg * -74.0 - 16.0, 1.0, 0.0, 0.0);
		GL11.glTranslatef(0.0, -1.0, -3.3);
		lgf.render(renderer);
		GL11.glPopMatrix();

		//後輪カバー左
		GL11.glPushMatrix();
		GL11.glTranslatef(0.8, 0.5, 0.0);
		GL11.glRotatef(movLg * -90.0, 0.0, 0.0, 1.0);
		GL11.glTranslatef(-0.8, -0.5, 0.0);
		lgbc1.render(renderer);
		GL11.glPopMatrix();

		//後輪カバー右
		GL11.glPushMatrix();
		GL11.glTranslatef(-0.8, 0.5, 0.0);
		GL11.glRotatef(movLg * 90.0, 0.0, 0.0, 1.0);
		GL11.glTranslatef(0.8 - 1.85, -0.5, 0.0);
		lgbc1.render(renderer);
		GL11.glPopMatrix();

		//後輪
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 0.8, -2.7);
		GL11.glRotatef(movLg * 90.0, 1.0, 0.0, 0.0);
		GL11.glTranslatef(0.0, -0.8, 2.7);
		lgb.render(renderer);
		GL11.glPopMatrix();

		//onRenderTick(entity);
	}
	else if(pass == 1)
	{
		renderCanopy(entity, canopyWin);
		renderEngine(entity, 1);
	}

	GL11.glPopMatrix();
}

function renderCanopy(entity, parts)
{
	GL11.glPushMatrix();
	var movCanopy = renderer.getDoorMovementR(entity);
	GL11.glTranslatef(0.0, 2.9, 0.6);
	GL11.glRotatef(movCanopy * -90.0, 1.0, 0.0, 0.0);
	GL11.glTranslatef(0.0, -2.9, -0.6);
	parts.render(renderer);
	GL11.glPopMatrix();
}

function renderEngine(entity, pass)
{
	var speed = 0.0;
	if(entity != null)
	{
		speed = entity.getSpeed();
	}

	var f0 = speed / 1.5;
	if(f0 > 1.0)
	{
		f0 = 1.0;
	}
	else if(f0 < -1.0)
	{
		f0 = -1.0;
	}
	var engPitch = -90.0 + f0 * 90.0;

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 2.75, -0.4);
	GL11.glRotatef(engPitch, 1.0, 0.0, 0.0);
	GL11.glTranslatef(0.0, -2.75, 0.4);
	if(pass == 0)
	{
		engine.render(renderer);
	}
	else if(pass == 1)
	{
		var onAir = false;
		if(entity != null)
		{
			onAir = entity.isOnGround();
		}

		if(speed != 0.0 || onAir)
		{
			var msec = renderer.getSystemTimeMillis() % 60000;
			var rotation = msec * 0.36;//1回転/s
			var scaleZ = ((NGTMath.sin(rotation) + NGTMath.sin(rotation * 1.7)) * 0.25) + 0.5;

			GL11.glPushMatrix();
			GL11.glTranslatef(6.5, 2.75, -1.05);
			GL11.glRotatef(180.0, 0.0, 1.0, 0.0);
			NGTRenderer.renderFire(0.85, 0.5 * scaleZ + 2.5, 0xA000FF, 25);
			GL11.glPopMatrix();

			GL11.glPushMatrix();
			GL11.glTranslatef(-6.5, 2.75, -1.05);
			GL11.glRotatef(180.0, 0.0, 1.0, 0.0);
			NGTRenderer.renderFire(0.85, 0.5 * scaleZ + 2.5, 0xA000FF, 25);
			GL11.glPopMatrix();
		}
	}
	GL11.glPopMatrix();
}

//フレームごとに描画以外の処理
function onRenderTick(entity)
{
	if(entity == null){return;}
	
	var dataMap = entity.getResourceState().getDataMap();
	var fired = dataMap.getBoolean("fired");
	var yaw = dataMap.getDouble("turret_yaw");

	//発射時エフェクト処理
	if(fired)
	{
		var x = renderer.getX(entity);
		var y = renderer.getY(entity);
		var z = renderer.getZ(entity);
		var vecMuzzle = new NGTVec(0.0, 1.77, 2.6);
		vecMuzzle = vecMuzzle.rotateAroundY(NGTMath.toRadians(yaw + renderer.getYaw(entity)));

		var world = MCWrapper.getWorld(entity);
		var rand = MCWrapper.getRandom(world);
		for(var i = 0; i < 8; ++i)
		{
			var xRand = rand.nextDouble() * 2.0 - 1.0;
			var yRand = rand.nextDouble() * 2.0 - 1.0;
			var zRand = rand.nextDouble() * 2.0 - 1.0;
			MCWrapperClient.playSound(world, "entity.generic.explode",
				x + vecMuzzle.getX() + xRand, y + vecMuzzle.getY() + yRand, z + vecMuzzle.getZ() + zRand, 1.0, 1.0, false);
			MCWrapperClient.spawnParticle(world, "largeexplode",
				x + vecMuzzle.getX() + xRand, y + vecMuzzle.getY() + yRand, z + vecMuzzle.getZ() + zRand, 0.0, 0.0, 0.0);
		}
		dataMap.setBoolean("fired", false, 0);//1回だけ鳴らすので、同期せずオフ
	}

	//発射判定(乗車時のみ)
	if(renderer.isRidden(entity))
	{
		var fireCount = dataMap.getInt("fire_count");
		if(fireCount <= 0)
		{
			if(Keyboard.isKeyDown(Keyboard.KEY_F))
			{
				dataMap.setBoolean("fired", true, 1);
				fireCount = 40;
			}
		}
		else
		{
			--fireCount;
		}
		dataMap.setInt("fire_count", fireCount, 0);//同期なし
	}
}
