importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.rtm.entity.train.util);

function onUpdate(entity, scriptExecuter)
{
	var doorState = entity.getVehicleState(TrainState.TrainStateType.Door);

	if((doorState & 1) > 0)
	{
		entity.getResourceState().addExclusionParts("door_RF", "door_RB");
	}
	else
	{
		entity.getResourceState().removeExclusionParts("door_RF", "door_RB");
	}

	if((doorState & 2) > 0)
	{
		entity.getResourceState().addExclusionParts("door_LF", "door_LB");
	}
	else
	{
		entity.getResourceState().removeExclusionParts("door_LF", "door_LB");
	}
}
