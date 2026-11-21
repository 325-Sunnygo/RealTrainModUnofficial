var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.org.lwjgl.input);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.io);

var TYPE_CRUSADER = 0;
var TYPE_T34 = 1;

function init(par1, par2)
{
	var modelName = par1.getConfig().getName();
	model = (modelName == "Crusader") ? TYPE_CRUSADER : TYPE_T34;
	
	turret = renderer.registerParts(new Parts("turret1", "turret2", "turret3"));
	body = (model == TYPE_CRUSADER) ? renderer.registerParts(new Parts("body1", "body2", "body3", "body4", "body5", "body6", "wb1"))
		: renderer.registerParts(new Parts("body1", "body2", "body3", "body4", "body5", "wb1"));
	crawler = renderer.registerParts(new Parts("crL1", "crR1"));
	wheel1 = renderer.registerParts(new Parts("whL1", "whR1"));
	wheel2 = renderer.registerParts(new Parts("whL2", "whR2"));
	wheel7 = renderer.registerParts(new Parts("whL7", "whR7"));

	//履帯上部と下部は平行になるよう転輪配置すること
	//下部転輪のほうがRが大きくなるようにすること

	//--------------------------------------------------------
	//転輪,前上
	wfu_y = (model == TYPE_CRUSADER) ?  0.8 : 0.7;//Y
	wfu_z = 2.1;//Z
	wfu_r = 0.3;//半径
	//転輪,前下
	wfd_y = 0.48;
	wfd_z = 1.35;
	wfd_r = 0.43;
	//転輪,後下
	wbd_y = 0.48;
	wbd_z = -2.25;
	wbd_r = 0.43;
	//動輪,後上
	wbu_y = (model == TYPE_CRUSADER) ? 0.7 : 0.6;
	wbu_z = (model == TYPE_CRUSADER) ?  -3.08 : -3.1;
	wbu_r = 0.4;

	crlLen = 0.125;//履帯部品長さ

	//--------------------------------------------------------
	dif_fy = wfu_y - wfd_y;
	dif_fz = wfu_z - wfd_z;
	dif_by = wbu_y - wbd_y;
	dif_bz = wbd_z - wbu_z;//※前後逆
	dif_fr = wfd_r - wfu_r;
	dif_br = wbd_r - wbu_r;
	len_f2sq = dif_fy * dif_fy + dif_fz * dif_fz;
	len_b2sq = dif_by * dif_by + dif_bz * dif_bz;
	len_f = Math.sqrt(len_f2sq - dif_fr * dif_fr);//前上-前下の履帯長
	len_b = Math.sqrt(len_b2sq - dif_br * dif_br);//後下-後上の履帯長

	//各転輪の弧部分の角度
	wfu_p = NGTMath.getAtan2D(dif_fz, dif_fy) + NGTMath.getAtan2D(len_f, dif_fr);
	wfd_p = 180.0 - wfu_p;
	wbu_p = NGTMath.getAtan2D(dif_bz, dif_by) + NGTMath.getAtan2D(len_b, dif_br);
	wbd_p = 180.0 - wbu_p;

	//履帯長
	a = 2.0 * Math.PI * wfu_r * (wfu_p / 360.0);
	b = len_f;
	c = 2.0 * Math.PI * wfd_r * (wfd_p / 360.0);
	d = wfd_z - wbd_z;//履帯長,下
	e0 = 2.0 * Math.PI * wbd_r * (wbd_p / 360.0);
	f = len_b;
	g = 2.0 * Math.PI * wbu_r * (wbu_p / 360.0);
	h = wfu_z - wbu_z;//履帯長,上

	ab = a + b;
	abc = ab + c;
	abcd = abc + d;
	abcde = abcd + e0;
	abcdef = abcde + f;
	abcdefg = abcdef + g;
	abcdefgh = abcdefg + h;

	crlCount = (abcdefgh / crlLen);// + 0.5;
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		body.render(renderer);

		//砲塔---------------------------------------------------------------------------
		GL11.glPushMatrix();
		var yaw = 0.0;
		if(entity != null)
		{
			var dataMap = entity.getResourceState().getDataMap();
			yaw = dataMap.getDouble("turret_yaw");
			if(renderer.isRidden(entity))
			{
				yaw = renderer.getPlayerYaw(entity) - renderer.getYaw(entity);
				dataMap.setDouble("turret_yaw", yaw, 1);
			}
		}
		GL11.glRotatef(yaw, 0.0, 1.0, 0.0);
		turret.render(renderer);
		GL11.glPopMatrix();

		//転輪,履帯---------------------------------------------------------------------------
		var amount = 0.0;
		if(entity != null)
		{
			var dataMap = entity.getResourceState().getDataMap();
			var msec = renderer.getSystemTimeMillis() % 60000;
			var prevTime = dataMap.getInt("prev_time");
			amount = dataMap.getDouble("mov_amount");
			var speed = entity.getSpeed();// + entity.getMoveDir()
			var dif = msec - prevTime;
			amount += speed * (dif / 50) * 0.25;//speed更新はtickごとなので補正, 0.25:適当
			if(amount >= abcdefgh || amount <= -abcdefgh)
			{
				amount = 0.0;
			}
			dataMap.setDouble("mov_amount", amount, 0);
			dataMap.setInt("prev_time", msec, 0);
		}
		renderCrowler(entity, pass, par3, amount);
		renderWheels(entity, pass, par3, amount);

		onRenderTick(entity);
	}

	GL11.glPopMatrix();
}

//転輪描画
function renderWheels(entity, pass, par3, amount)
{
	var wheelCount = 5;
	var wheelGap = 0.9;
	
	renderWheel(entity, pass, par3, amount, wheel1, wfu_y, wfu_z, wfu_r);
	GL11.glPushMatrix();
	for(var i = 0; i < wheelCount; ++ i)
	{
		renderWheel(entity, pass, par3, amount, wheel2, wfd_y, wfd_z, wfd_r);
		GL11.glTranslatef(0.0, 0.0, -wheelGap);
	}
	GL11.glPopMatrix();
	renderWheel(entity, pass, par3, amount, wheel7, wbu_y, wbu_z, wbu_r);
}

//履帯描画
function renderCrowler(entity, pass, par3, amount)
{
	for(var i = 0; i < crlCount; ++i)
	{
		GL11.glPushMatrix();

		var mov = amount + i * crlLen;
		if(mov < 0)
		{
			mov += abcdefgh;
		}
		else if(mov >= abcdefgh)
		{
			mov -= abcdefgh;
		}

		if(mov >= 0 && mov < a)//A
		{
			GL11.glTranslatef(0.0, wfu_y, wfu_z);
			var pitch = wfu_p * (mov / a);
			GL11.glRotatef(pitch, 1.0, 0.0, 0.0);
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);
		}
		else if(mov < ab)//B
		{
			GL11.glTranslatef(0.0, wfu_y, wfu_z);
			GL11.glRotatef(wfu_p, 1.0, 0.0, 0.0);
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);
			GL11.glTranslatef(0.0, 0.0, mov - a);
		}
		else if(mov < abc)//C
		{
			GL11.glTranslatef(0.0, wfd_y, wfd_z);
			var pitch = wfd_p * ((mov - ab) / c);
			GL11.glRotatef(pitch + wfu_p, 1.0, 0.0, 0.0);
			//GL11.glTranslatef(0.0, -wfd_y, -wfd_z);
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);

			GL11.glTranslatef(0.0, wfd_r - wfu_r, 0.0);//転輪半径違うため
		}
		else if(mov < abcd)//D
		{
			GL11.glTranslatef(0.0, wfd_y, wfd_z);
			GL11.glRotatef(180.0, 1.0, 0.0, 0.0);
			//GL11.glTranslatef(0.0, -wfd_y - (wfu_y - wfd_y), -wfd_z - (wfu_z - wfd_z));
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);
			GL11.glTranslatef(0.0, 0.0, mov - abc);

			GL11.glTranslatef(0.0, wfd_r - wfu_r, 0.0);//転輪半径違うため
		}
		else if(mov < abcde)//E
		{
			GL11.glTranslatef(0.0, wbd_y, wbd_z);
			var pitch = wbd_p * ((mov - abcd) / e0);
			GL11.glRotatef(pitch + 180.0, 1.0, 0.0, 0.0);
			//GL11.glTranslatef(0.0, -wbd_y - (wfu_y - wbd_y), -wbd_z - (wfu_z - wbd_z));
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);

			GL11.glTranslatef(0.0, wfd_r - wfu_r, 0.0);//転輪半径違うため
		}
		else if(mov < abcdef)//F
		{
			GL11.glTranslatef(0.0, wbd_y, wbd_z);
			GL11.glRotatef(180.0 + wbd_p, 1.0, 0.0, 0.0);
			//GL11.glTranslatef(0.0, -wbd_y - 0.5, -wbd_z - 3.75);
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);
			GL11.glTranslatef(0.0, 0.0, mov - abcde);

			GL11.glTranslatef(0.0, wfd_r - wfu_r, 0.0);//転輪半径違うため
		}
		else if(mov < abcdefg)//G
		{
			GL11.glTranslatef(0.0, wbu_y, wbu_z);
			var pitch = wbu_p * ((mov - abcdef) / g);
			GL11.glRotatef(pitch - wbu_p, 1.0, 0.0, 0.0);
			//GL11.glTranslatef(0.0, -wbu_y, -wbu_z - 4.5);
			GL11.glTranslatef(0.0, -wfu_y, -wfu_z);

			GL11.glTranslatef(0.0, wbu_r - wfu_r, 0.0);//転輪半径違うため
		}
		else if(mov < abcdefgh)//H
		{
			if(model == TYPE_T34)
			{
				GL11.glTranslatef(0.0, 0.0, mov - abcdefgh);
			}
			else
			{
				GL11.glPopMatrix();//履帯上部分が隠れているモデルは描画スキップ
				continue;
			}
		}

		crawler.render(renderer);

		GL11.glPopMatrix();
	}
}

function renderWheel(entity, pass, par3, amount, wheelParts, wy, wz, wr)
{
	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, wy, wz);
	var pitch = 360.0 * (amount / (2.0 * Math.PI * wr));
	GL11.glRotatef(pitch, 1.0, 0.0, 0.0);
	GL11.glTranslatef(0.0, -wy, -wz);
	wheelParts.render(renderer);
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
		var vecMuzzle = (model == TYPE_CRUSADER) ? PooledVec3.create(0.0, 1.77, 2.6) : PooledVec3.create(0.0, 2.2, 4.55);
		vecMuzzle = vecMuzzle.rotateAroundY(yaw + renderer.getYaw(entity));

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
		NGTLog.debug("Count:" + dataMap.getInt("total_fire_count"));
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
