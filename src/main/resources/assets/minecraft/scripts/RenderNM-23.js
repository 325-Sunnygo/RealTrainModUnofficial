var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.math);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.rtm.entity.train.util);

var MAX_TIME = 10000;//10s
var ROTATION_MISSILE = 90.0;
var ROTATION_COVER = 45.0;

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("base", "front", "side", "body", "silo3", "silo_base",
		"cyl_m_d2", "cyl_sl_d2", "cyl_sr_d2"));
	door = renderer.registerParts(new Parts("door"));

	missile = renderer.registerParts(new Parts("missile", "silo", "silo2", "cyl_m_u2"));//Y=0.5,Z=-6.7
	cyl_m_u = renderer.registerParts(new Parts("cyl_m_u1"));//Y=0.35,Z=0.0
	cyl_m_d = renderer.registerParts(new Parts("cyl_m_d1"));//Y=0.35,Z=-4.5

	cover_l = renderer.registerParts(new Parts("cover_l", "cyl_sl_u2"));//X=1.17,Y=1.9
	cyl_sl_u = renderer.registerParts(new Parts("cyl_sl_u1"));//X=0.2,Y=2.85
	cyl_sl_d = renderer.registerParts(new Parts("cyl_sl_d1"));//X=0.2,Y=0.2

	cover_r = renderer.registerParts(new Parts("cover_r", "cyl_sr_u2"));//X=-1.17,Y=1.9
	cyl_sr_u = renderer.registerParts(new Parts("cyl_sr_u1"));//X=-0.2,Y=2.85
	cyl_sr_d = renderer.registerParts(new Parts("cyl_sr_d1"));//X=-0.2,Y=0.2
}

function render(entity, pass, partialTick)
{
	GL11.glPushMatrix();
	
	if(pass == 0)
	{
		body.render(renderer);
		door.render(renderer);
		renderCover(entity, pass, partialTick);
		renderMissile(entity, pass, partialTick);
	}

	GL11.glPopMatrix();
}

function renderMissile(entity, pass, partialTick)
{
	var rotationMain = 0.0;
	if(entity != null)
	{
		var dataMap = entity.getResourceState().getDataMap();
		var bs1 = dataMap.getInt("Button1");
		var prevState = dataMap.getInt("PrevState1");
		var startTime = dataMap.getInt("StartTime1");
		var currentTime = renderer.getSystemTimeMillis() % 43200000;//12H区切り
		var dif = currentTime - startTime;
		
		if(bs1 == 0)
		{
			if(prevState == 1)
			{
				//同期, 保存なし
				dataMap.setInt("StartTime1", currentTime, 0);
				dataMap.setInt("PrevState1", bs1, 0);
				rotationMain = ROTATION_MISSILE;
			}
			else if(dif >= MAX_TIME)
			{
				rotationMain = 0.0;
			}
			else
			{
				rotationMain = ROTATION_MISSILE * (1.0 - (dif / MAX_TIME));
			}
		}
		else//bs1=1
		{
			if(prevState == 0)
			{
				//同期, 保存なし
				dataMap.setInt("StartTime1", currentTime, 0);
				dataMap.setInt("PrevState1", bs1, 0);
				rotationMain = 0.0;
			}
			else if(dif >= MAX_TIME)
			{
				rotationMain = ROTATION_MISSILE;
			}
			else
			{
				rotationMain = ROTATION_MISSILE * (dif / MAX_TIME);
			}
		}
	}

	var rotationCyl = 0.0;
	if(rotationMain > 0.0)
	{
		var vec = PooledVec3.create(0.0, 0.35 - 0.5, 0.0 + 6.7);
		vec = vec.rotateAroundX(rotationMain);
		vec = vec.add(0.0, 0.5 - 0.35, -6.7 + 4.5);
		rotationCyl = NGTMath.toDegrees(vec.getAngle(PooledVec3.create(0.0, 0.0, 4.5)));
	}

	var roU = -(rotationCyl - rotationMain);
	var roD = -rotationCyl;

	GL11.glPushMatrix();
	renderer.rotate(-rotationMain, 'X', 0.0, 0.5, -6.7);
	missile.render(renderer);
	{
		GL11.glPushMatrix();
		renderer.rotate(roU, 'X', 0.0, 0.35, 0.0);
		cyl_m_u.render(renderer);
		GL11.glPopMatrix();
	}
	GL11.glPopMatrix();
	
	GL11.glPushMatrix();
	renderer.rotate(roD, 'X', 0.0, 0.35, -4.5);
	cyl_m_d.render(renderer);
	GL11.glPopMatrix();
}

function renderCover(entity, pass, partialTick)
{
	var rotationMain = 0.0;
	if(entity != null)
	{
		var dataMap = entity.getResourceState().getDataMap();
		var bs0 = dataMap.getInt("Button0");
		var prevState = dataMap.getInt("PrevState0");
		var startTime = dataMap.getInt("StartTime0");
		var currentTime = renderer.getSystemTimeMillis() % 43200000;//12H区切り
		var dif = currentTime - startTime;
		
		if(bs0 == 0)
		{
			if(prevState == 1)
			{
				//同期, 保存なし
				dataMap.setInt("StartTime0", currentTime, 0);
				dataMap.setInt("PrevState0", bs0, 0);
				rotationMain = ROTATION_COVER;
			}
			else if(dif >= MAX_TIME)
			{
				rotationMain = 0.0;
			}
			else
			{
				rotationMain = ROTATION_COVER * (1.0 - (dif / MAX_TIME));
			}
		}
		else//bs0=1
		{
			if(prevState == 0)
			{
				//同期, 保存なし
				dataMap.setInt("StartTime0", currentTime, 0);
				dataMap.setInt("PrevState0", bs0, 0);
				rotationMain = 0.0;
			}
			else if(dif >= MAX_TIME)
			{
				rotationMain = ROTATION_COVER;
			}
			else
			{
				rotationMain = ROTATION_COVER * (dif / MAX_TIME);
			}
		}
	}

	var rotationCyl = 0.0;
	if(rotationMain > 0.0)
	{
		var vec = PooledVec3.create(0.2 - 1.17, 2.85 - 1.9, 0.0);
		vec = vec.rotateAroundZ(rotationMain);
		vec = vec.add(1.17 - 0.2, 1.9 - 0.2, 0.0);
		rotationCyl = NGTMath.toDegrees(vec.getAngle(PooledVec3.create(0.0, 2.85 - 0.2, 0.0)));
	}

	var roU = rotationMain - rotationCyl;
	var roD = -rotationCyl;

	//Cover L
	GL11.glPushMatrix();
	renderer.rotate(-rotationMain, 'Z', 1.17, 1.9, 0.0);
	cover_l.render(renderer);
	{
		GL11.glPushMatrix();
		renderer.rotate(roU, 'Z', 0.2, 2.85, 0.0);
		cyl_sl_u.render(renderer);
		GL11.glPopMatrix();
	}
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	renderer.rotate(roD, 'Z', 0.2, 0.2, 0.0);
	cyl_sl_d.render(renderer);
	GL11.glPopMatrix();

	//Cover R
	GL11.glPushMatrix();
	renderer.rotate(rotationMain, 'Z', -1.17, 1.9, 0.0);
	cover_r.render(renderer);
	{
		GL11.glPushMatrix();
		renderer.rotate(-roU, 'Z', -0.2, 2.85, 0.0);
		cyl_sr_u.render(renderer);
		GL11.glPopMatrix();
	}
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	renderer.rotate(-roD, 'Z', -0.2, 0.2, 0.0);
	cyl_sr_d.render(renderer);
	GL11.glPopMatrix();
}
