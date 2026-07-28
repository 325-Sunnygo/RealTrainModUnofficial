importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.rtm.entity.train.util);

function onUpdate(entity, scriptExecuter)
{
	var doorState = entity.getVehicleState(TrainState.TrainStateType.Door);

	if((doorState & 1) > 0)
	{
		entity.getResourceState().addExclusionParts(
			"door_br", "door_fr", "door_br_223", "door_fr_223");
	}
	else
	{
		entity.getResourceState().removeExclusionParts(
			"door_br", "door_fr", "door_br_223", "door_fr_223");
	}

	if((doorState & 2) > 0)
	{
		entity.getResourceState().addExclusionParts(
			"door_bl", "door_fl", "door_bl_223", "door_fl_223");
	}
	else
	{
		entity.getResourceState().removeExclusionParts(
			"door_bl", "door_fl", "door_bl_223", "door_fl_223");
	}
}
