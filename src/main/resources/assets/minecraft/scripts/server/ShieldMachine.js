importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.ngtlib.math);

function onUpdate(entity, scriptExecuter)
{
	var bs1 = entity.getResourceState().getDataMap().getInt("Button0");
	if(entity.getSpeed() <= 0.0 || bs1 == 0){return;}
	
	var r = 4;//カッターの半径
	var l = 4.15;//カッターまでの距離
	var posX = MCWrapper.getPosX(entity);
	var posY = MCWrapper.getPosY(entity);
	var posZ = MCWrapper.getPosZ(entity);
	var entityYaw = MCWrapper.getYaw(entity);
	var yawRad = NGTMath.toRadians(entityYaw);
	var digCenterX = posX + NGTMath.getSin(yawRad) * l;
	var digCenterY = posY + r;
	var digCenterZ = posZ + NGTMath.getCos(yawRad) * l;

	for(i = -r; i < r; ++i)//x
	{
		for(j = -r; j < r; ++j)//y
		{
			for(k = -r; k < r; ++k)//z
			{
				var len = getLength(i + 0.5, j + 0.5, k + 0.5);
				var inSphere = len <= r * r;//半径rの球の範囲内
				if(inSphere)
				{
					var targetYaw = Math.atan2(i + 0.5, k + 0.5);
					var dif = getDif(entityYaw, NGTMath.toDegrees(targetYaw));
					var d0 = getLength(i + 0.5, 0.0, k + 0.5) / (r * r);//0.0~1.0
					var d1 = 65.0 * (1.0 - d0) + 10.0;//±10に加え中央ほど許容角度を大きく取る
					if(dif < d1)
					{
						cutBlock(entity, digCenterX + i + 0.5, digCenterY + j + 0.5, digCenterZ + k + 0.5);
					}
				}
			}
		}
	}
}

function getLength(x, y, z)
{
	return x * x + y * y + z * z;
}

/**yaw2つの角が90±a以内か*/
function getDif(yaw1, yaw2)
{
	var dif = Math.abs(yaw1 - yaw2);
	while(dif > 180.0)
	{
		dif -= 180.0;
	}
	var dif2 = Math.abs(90.0 - dif);
	return dif2;
}

function cutBlock(entity, x, y, z)
{
	var world = MCWrapper.getWorld(entity);
	MCWrapper.breakBlock(entity, x, y, z);
}
