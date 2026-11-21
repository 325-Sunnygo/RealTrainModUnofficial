importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.math);

function onUpdate(entity, scriptExecuter)
{
	if(entity == null){return;}

	var speed = 1.25;
	var dataMap = entity.getResourceState().getDataMap();
	var modelName = entity.getResourceState().getResourceName();
	var fired = dataMap.getBoolean("fired");
	var yaw = dataMap.getDouble("turret_yaw");
	if(fired)
	{
		var world = MCWrapper.getWorld(entity);
		var x = MCWrapper.getPosX(entity);
		var y = MCWrapper.getPosY(entity);
		var z = MCWrapper.getPosZ(entity);
		var yaw = yaw + MCWrapper.getYaw(entity);
		var vecMuzzle = (modelName == "Crusader") ? PooledVec3.create(0.0, 1.77, 2.6) : PooledVec3.create(0.0, 2.2, 4.55);
		vecMuzzle = vecMuzzle.rotateAroundY(yaw);
		var vecMotion = PooledVec3.create(0.0, 0.0, speed);
		vecMotion = vecMotion.rotateAroundY(yaw);

		scriptExecuter.fireBullet(world, entity, "cannon_40cm", x + vecMuzzle.getX(), y + vecMuzzle.getY(), z + vecMuzzle.getZ(),
			vecMotion.getX(), vecMotion.getY(), vecMotion.getZ());

		dataMap.setBoolean("fired", false, 0);//同期しない、Clientは各自オフに
		dataMap.setInt("total_fire_count", dataMap.getInt("total_fire_count") + 1, 1);
	}
}
