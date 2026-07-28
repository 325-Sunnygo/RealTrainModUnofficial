var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.rtm.entity.train.util);

function init(par1, par2)
{
	var name = par1.getConfig().getName();
	hasPantograph = (name.indexOf("M") >= 0);
	isCtrlCar = (name.indexOf("c") >= 0) || (name.indexOf("kiha127") >= 0);

	body = renderer.registerParts(new Parts("roof", "aircon", "floor", "uf_unit", "side_door",
		"side_win2", "side_win_b", "back", "tdoor_b", "cover_b", "cover2_b", "sepalator", "roof2", "strap_holder", "strap", "rack"));

	if(name.indexOf("c") >= 0)//223先頭車
	{
		if(name.indexOf("5000") >= 0)
		{
			head = renderer.registerParts(new Parts("uf_unit_f_223", "board", "ctrl", "head_223-5000", "head_door", "head_door_223-5000"));
			window = renderer.registerParts(new Parts("window", "window_h_223-5000"));
		}
		else
		{
			head = renderer.registerParts(new Parts("uf_unit_f_223", "ctrl", "head_223-2000", "head_door", "head_door_223-2000"));
			window = renderer.registerParts(new Parts("window", "window_h_223-2000"));
		}
		room_light = renderer.registerParts(new Parts("room_light"));
		rollsign_type = renderer.registerParts(new Parts("rollsign_type_f", "rollsign_type_s"));
		rollsign_dest = renderer.registerParts(new Parts("rollsign_dest_f", "rollsign_dest_s"));
	}
	else if(name.indexOf("kiha127") >= 0)
	{
		head = renderer.registerParts(new Parts("uf_unit_f_k127", "board", "ctrl", "head_k127", "head_door", "head_door_223-5000"));
		window = renderer.registerParts(new Parts("window", "window_k127", "window_h_223-5000"));
		room_light = renderer.registerParts(new Parts("room_light"));
		rollsign_type = renderer.registerParts(new Parts("rollsign_type_f", "rollsign_type_s"));
		rollsign_dest = renderer.registerParts(new Parts("rollsign_dest_f", "rollsign_dest_s"));
	}
	else
	{
		head = renderer.registerParts(new Parts("roof_f", "floor_f", "side_win_f", "front", "tdoor_f",
			"cover_f", "cover2_f", "sepalator_f", "roof2_f", "strap_f", "rack_f"));//中間車の前面パーツ
		window = renderer.registerParts(new Parts("window", "window_f"));
		room_light = renderer.registerParts(new Parts("room_light", "room_light_f"));
		rollsign_type = renderer.registerParts(new Parts("rollsign_type_s"));
		rollsign_dest = renderer.registerParts(new Parts("rollsign_dest_s"));
	}
	rollsign_num = renderer.registerParts(new Parts("rollsign_num_s"));
	
	panto_base = renderer.registerParts(new Parts("panto_base", "panto_base2"));
	panto_df   = renderer.registerParts(new Parts("panto_df"));
	panto_db   = renderer.registerParts(new Parts("panto_db"));
	panto_uf   = renderer.registerParts(new Parts("panto_uf"));
	panto_ub   = renderer.registerParts(new Parts("panto_ub"));
	panto_top  = renderer.registerParts(new Parts("panto_top"));

	if(name.indexOf("223") >= 0)
	{
		body2  = renderer.registerParts(new Parts("side_door_223", "side_win_223", "win_frame_223"));
		door_bl = renderer.registerParts(new Parts("door_bl", "door_bl_223"));
		door_br = renderer.registerParts(new Parts("door_br", "door_br_223"));
		door_fl = renderer.registerParts(new Parts("door_fl", "door_fl_223"));
		door_fr = renderer.registerParts(new Parts("door_fr", "door_fr_223"));

		if(name.indexOf("c") >= 0)
		{
			coupler_f = renderer.registerParts(new Parts("coupler_f", "coupler_f_223"));
		}
		else
		{
			coupler_f = renderer.registerParts(new Parts("coupler_f"));
		}
	}
	else
	{
		body2 = renderer.registerParts(new Parts("side_win_k127", "win_frame_k127", "side_win2_k127", "exaust_pipe"));
		coupler_f = renderer.registerParts(new Parts("coupler_f"));
		door_bl = renderer.registerParts(new Parts("door_bl"));
		door_br = renderer.registerParts(new Parts("door_br"));
		door_fl = renderer.registerParts(new Parts("door_fl"));
		door_fr = renderer.registerParts(new Parts("door_fr"));
	}
	coupler_b = renderer.registerParts(new Parts("coupler_b"));

	lever_p = renderer.registerParts(new Parts("lever_p"));
	lever_b = renderer.registerParts(new Parts("lever_b"));

	head_light_off = renderer.registerParts(new Parts("head_light_off"));
	head_light_on = renderer.registerParts(new Parts("head_light_on"));
	tail_light_off = renderer.registerParts(new Parts("tail_light_off"));
	tail_light_on = renderer.registerParts(new Parts("tail_light_on"));

	seat = renderer.registerParts(new Parts("seat1", "seat2"));
	backrest = renderer.registerParts(new Parts("backrest"));
	if(name.indexOf("kiha127") >= 0)
	{
		seatPos = [
			-8.96, -9.58,
			-2.16, -2.78, -3.40, -4.02, -4.64,
			-1.24, -0.62, 0.0, 0.62, 1.24,
			2.16, 2.78, 3.40, 4.02, 4.64
		];
	}
	else if(name.indexOf("c") >= 0)
	{
		seatPos = [
			-8.96, -9.58,
			-2.16, -2.78, -3.40, -4.02, -4.64,
			2.16, 2.78, 3.40, 4.02, 4.64
		];
	}
	else
	{
		seatPos = [
			-8.96, -9.58,
			-2.16, -2.78, -3.40, -4.02, -4.64,
			2.16, 2.78, 3.40, 4.02, 4.64,
			8.96, 9.58
		];
	}
	//シート間0.62
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();
	
	if(pass == RenderPass.NORMAL.id)
	{
		updateClient(entity);

		body.render(renderer);
		body2.render(renderer);
		head.render(renderer);

		if(isCtrlCar)
		{
			renderController(entity, pass, par3);//通常描画用
			//renderConSw(entity, pass, par3);
		}

		renderDoor(entity, pass, par3);
		renderCoupler(entity, pass, par3);

		if(hasPantograph)
		{
			renderPantograph(entity, pass, par3);
		}

		renderSeat(entity, pass, par3);
	}
	else if(pass == RenderPass.TRANSPARENT.id)
	{
		window.render(renderer);
		renderDoor(entity, pass, par3);
	}
	else if(pass >= RenderPass.LIGHT.id && pass <= RenderPass.LIGHT_BACK.id)
	{
		if(pass == RenderPass.LIGHT.id)
		{
			room_light.render(renderer);
			renderRollsign(entity, pass, par3);
			renderHeadLight(entity, pass, par3);
		}
	}
	else if(pass == RenderPass.OUTLINE.id)
	{
		if(isCtrlCar)
		{
			//renderController(entity, pass, par3);//輪郭線描画用
			//renderConSw(entity, pass, par3);
		}
	}
	else if(pass == RenderPass.PICK.id)
	{
		if(isCtrlCar)
		{
			//renderController(entity, pass, par3);//右クリック操作判定用
			//renderConSw(entity, pass, par3);
		}
	}

	GL11.glPopMatrix();
}

//未使用
function onRightClick(entity, parts)
{
	var doorState = entity.getVehicleState(TrainState.TrainStateType.Door);
	if(parts.equals(con_sw_RF) || parts.equals(con_sw_RB))
	{
		doorState ^= 1;
	}
	else if(parts.equals(con_sw_LF) || parts.equals(con_sw_LB))
	{
		doorState ^= 2;
	}
	entity.syncVehicleState(TrainState.TrainStateType.Door, doorState);
}

//move:右クリック開始時を0としたマウスの相対移動量
function onRightDrag(entity, parts, move)
{
	var notch = entity.getNotch();
	var dataMap = entity.getResourceState().getDataMap();
	if(move == 0)
	{
		dataMap.setInt("start_notch", notch, 0);
		return;
	}
	var startNotch = dataMap.getInt("start_notch");
	var newNotch = startNotch + Math.floor(-move / 20);//マウス動き反転 & 20pxlごとに1ノッチ変更
	if(parts.equals(mascon_F) || parts.equals(mascon_B))
	{
		newNotch = newNotch < 0 ? 0 : (newNotch > 5 ? 5 : newNotch);
	}
	else if(parts.equals(brake_F) || parts.equals(brake_B))
	{
		newNotch = newNotch < -8 ? -8 : (newNotch > 0 ? 0 : newNotch);
	}
	entity.syncNotch(newNotch - notch);
}

function renderController(entity, pass, par3)
{
	var rotationMF = 0.0;
	var rotationBF = 0.0;
	var angle = -60;
	
	if(entity != null)
	{
		var dirForward = (entity.getTrainDirection() == 0);
		var notch = entity.getNotch();
		var notchM = (notch < 0 ? 0 : notch) / 5;
		var notchB = ((notch > 0 ? 0 : notch) + 8) / 8;
		rotationMF = dirForward ? (notchM * angle) : 0.0;
		rotationBF = dirForward ? (notchB * angle) : 0.0;
	}

	GL11.glPushMatrix();
	renderer.rotate(rotationMF, 'X', 0.8325, 0.8668, 9.259);
	lever_p.render(renderer);
	GL11.glPopMatrix();
	
	GL11.glPushMatrix();
	renderer.rotate(rotationBF, 'X', 0.8325, 0.8668, 9.259);
	lever_b.render(renderer);
	GL11.glPopMatrix();
}

//未使用
function renderConSw(entity, pass, par3)
{
	var stateRF = 0;
	var stateLF = 0;
	var stateRB = 0;
	var stateLB = 0;
	if(entity != null)
	{
		var doorState = entity.getVehicleState(TrainState.TrainStateType.Door);
		var dirForward = (entity.getTrainDirection() == 0);
		var doorROpen = (doorState & 1) == 1;
		var doorLOpen = (doorState & 2) == 2;
		stateRF = (dirForward && doorROpen) ? 1 : 0;
		stateLF = (dirForward && doorLOpen) ? 1 : 0;
		stateRB = (!dirForward && doorROpen) ? 1 : 0;
		stateLB = (!dirForward && doorLOpen) ? 1 : 0;
	}

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.03 * stateRF, 0.0);
	con_sw_RF.render(renderer);
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.03 * stateLF, 0.0);
	con_sw_LF.render(renderer);
	GL11.glPopMatrix();

	if(!hasOneCab)
	{
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 0.03 * stateRB, 0.0);
		con_sw_RB.render(renderer);
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 0.03 * stateLB, 0.0);
		con_sw_LB.render(renderer);
		GL11.glPopMatrix();
	}
}

function renderDoor(entity, pass, par3)
{
	var moveL = (entity == null ? 0.0 : renderer.sigmoid(renderer.getDoorMovementL(entity)));
	var moveR = (entity == null ? 0.0 : renderer.sigmoid(renderer.getDoorMovementR(entity)));
	var size = 0.65;
	
    GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.0, size * moveL);
	door_fl.render(renderer);
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.0, size * -moveL);
	door_bl.render(renderer);
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.0, size * moveR);
	door_fr.render(renderer);
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, 0.0, size * -moveR);
	door_br.render(renderer);
	GL11.glPopMatrix();
}

function renderPantograph(entity, pass, par3)
{
	//h=sin(r)*(lenD+lenU), r=asin(h/(lenD+lenU))
	//hMin=2.82, len=2.34
	
	var move = 1.0 - (entity == null ? 0.0 : renderer.getPantographMovementBack(entity));
	var rMin = 4.411725;//h=3
	var rMax = 30.283137;//h=4
	var rDif = rMax - rMin;

	panto_base.render(renderer);

	GL11.glPushMatrix();
	renderer.rotate(-rDif * move, 'X', 0.0, 2.755, -7.17);
	panto_df.render(renderer);
	{
		GL11.glPushMatrix();
		renderer.rotate(rDif * 2.0 * move, 'X', 0.0, 2.755, -5.815);
		panto_uf.render(renderer);
		{
			GL11.glPushMatrix();
			renderer.rotate(-rDif * move, 'X', 0.0, 2.755, -6.8);
			panto_top.render(renderer);
			GL11.glPopMatrix();
		}
		GL11.glPopMatrix();
	}
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	renderer.rotate(rDif * move, 'X', 0.0, 2.755, -6.43);
	panto_db.render(renderer);
	{
		GL11.glPushMatrix();
		renderer.rotate(-rDif * 2.0 * move, 'X', 0.0, 2.755, -7.785);
		panto_ub.render(renderer);
		GL11.glPopMatrix();
	}
	GL11.glPopMatrix();
}

function renderRollsign(entity, pass, par3)
{
	var moveT = 0.0;
	var moveD = 0.0;
	var moveN = 0.0;
	var uSize = 96.0 / 2048.0;

	if(entity != null)
	{
		moveT = entity.getRollsignAnimation() * uSize;
		moveD = entity.getResourceState().getDataMap().getInt("Button0") * uSize;
		moveN = entity.getResourceState().getDataMap().getInt("Button1") * uSize;
	}

	GLHelper.preMoveTexUV(0.0, moveT);
	rollsign_type.render(renderer);
	GLHelper.postMoveTexUV();

	GLHelper.preMoveTexUV(0.0, moveD);
	rollsign_dest.render(renderer);
	GLHelper.postMoveTexUV();

	GLHelper.preMoveTexUV(0.0, moveN);
	rollsign_num.render(renderer);
	GLHelper.postMoveTexUV();
}

function renderCoupler(entity, pass, par3)
{
	var yawF = 0.0;
	var yawB = 0.0;

	if(entity != null)
	{
		yawF = entity.getCouplerYaw(0);
		yawB = entity.getCouplerYaw(1);
	}

	GL11.glPushMatrix();
	renderer.rotate(yawF, 'Y', 0.0, 0.0, 9.0);
	coupler_f.render(renderer);
	GL11.glPopMatrix();

	GL11.glPushMatrix();
	renderer.rotate(yawB, 'Y', 0.0, 0.0, -9.0);
	coupler_b.render(renderer);
	GL11.glPopMatrix();
}

function renderHeadLight(entity, pass, par3)
{
	var headLight = false;
	var tailLight = false;
	
	if(entity != null)
	{
		mode = entity.getVehicleState(TrainState.TrainStateType.Light);////0:消灯,1:前照灯,2:尾灯
		
		if(mode > 0)
		{
			dir = entity.getTrainDirection();
			emptyF = entity.getConnectedTrain(dir) == null;//front空き
			emptyB = entity.getConnectedTrain(1 - dir) == null;//back空き

			headLight = ((mode == 1) && (dir == 0) && emptyF) || ((mode == 2) && (dir == 1) && emptyB);
			tailLight = ((mode == 2) && (dir == 0) && emptyB) || ((mode == 1) && (dir == 1) && emptyF);
		}
	}

	if(headLight)
	{
		head_light_on.render(renderer);
	}
	else
	{
		head_light_off.render(renderer);
	}

	if(tailLight)
	{
		tail_light_on.render(renderer);
	}
	else
	{
		tail_light_off.render(renderer);
	}
}

function renderSeat(entity, pass, par3)
{
	var rotation = -15.0;
	if(entity != null)
	{
		rotation = entity.getSeatRotation() * 15.0;
	}

	for(var i = 0; i < seatPos.length; ++i)
	{
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0, 0.0, seatPos[i]);
		seat.render(renderer);
		renderer.rotate(rotation, 'X', 0.0, 0.0, 0.0);
		backrest.render(renderer);
		GL11.glPopMatrix();
	}
}

function updateClient(entity)
{
	if(entity == null){return;}

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
