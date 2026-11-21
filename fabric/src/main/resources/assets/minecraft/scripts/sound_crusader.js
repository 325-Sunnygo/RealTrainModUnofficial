importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.math);

function onUpdate(su)
{
	var entity = su.getEntity();
	var dataMap = entity.getResourceState().getDataMap();
	
	//var speed = su.getSpeed();
	//var maxSpeed = 0.8 * 72.0;
	var acForward = entity.getAccelerationForward()
	var acStrafe = entity.getAccelerationStrafe();
	var engRo = dataMap.getInt("eng_rotation");
	var prevEngRo = engRo;
	var maxEngRo = 326;

	if(acForward > 0.0)
	{
		if(engRo < maxEngRo)
		{
			engRo += 1;
		}
	}
	else if(acForward < 0.0)
	{
		if(engRo > -maxEngRo)
		{
			engRo -= 1;
		}
	}
	else if(acStrafe > 0.0)
	{
		if(engRo < 70)
		{
			engRo += 2;
		}
	}
	else if(acStrafe < 0.0)
	{
		if(engRo > -70)
		{
			engRo -= 2;
		}
	}
	else
	{
		if(engRo > 0)
		{
			engRo -= 4;
		}
		else if(engRo < 0)
		{
			engRo += 4;
		}

		if(engRo > -3 && engRo < 3)
		{
			engRo = 0;
		}
	}

	var vol = 1.0;
	var pitch = 1.0 + (Math.abs(engRo) / maxEngRo);

	su.playSound("rtm", "train.dc110_stn", vol, pitch);

	dataMap.setInt("eng_rotation", engRo, 0);

	updateEffect(entity, engRo, prevEngRo);
}

function updateEffect(entity, engRo, prevEngRo)
{
	var absEngRo = Math.abs(engRo);
	if(absEngRo > 0 && absEngRo <= 30 && (absEngRo - Math.abs(prevEngRo)) > 0)
	{
		var world = MCWrapper.getWorld(entity);
		var x = MCWrapper.getPosX(entity);
		var y = MCWrapper.getPosY(entity);
		var z = MCWrapper.getPosZ(entity);
		var yaw = MCWrapper.getYaw(entity);
		var pitch = MCWrapper.getPitch(entity);
		var modelName = entity.getResourceState().getResourceName();

		var vec = (modelName == "Crusader") ? PooledVec3.create(1.2, 1.45, -3.38) : PooledVec3.create(0.6375, 1.151, -3.139);
		vec = vec.rotateAroundX(pitch);
		vec = vec.rotateAroundY(yaw);
		smoke(world, x + vec.getX(), y + vec.getY(), z + vec.getZ(), 3);

		vec = (modelName == "Crusader") ? PooledVec3.create(-1.2, 1.45, -3.38) : PooledVec3.create(-0.6375, 1.151, -3.139);
		vec = vec.rotateAroundX(pitch);
		vec = vec.rotateAroundY(yaw);
		smoke(world, x + vec.getX(), y + vec.getY(), z + vec.getZ(), 3);
	}
}

function smoke(world, x, y, z, rep)
{
	var rand = MCWrapper.getRandom(world);

	for(var i = 0; i < rep; ++i)
	{
		var scale = 0.125;
		var xRand = rand.nextGaussian() * scale;
		var yRand = rand.nextGaussian() * scale;
		var zRand = rand.nextGaussian() * scale;
		MCWrapperClient.spawnParticle(world, "largesmoke",
				x + xRand, y + yRand, z + zRand, 0.0, 0.0, 0.0);
	}
}
