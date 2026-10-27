var renderClass = "jp.ngt.rtm.render.MachinePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.ngtlib.renderer);
importPackage(Packages.jp.ngt.ngtlib.util);
importPackage(Packages.jp.ngt.rtm.render);

var MODE_INPUT   = 0;
var MODE_OPEN    = 1;
var MODE_CLOSE   = 2;
var MODE_FAILED  = 3;
var MODE_WARNING = 4;

var PST_CHAR = 300;//msec
var PST_KEY = 300;

function init(par1, par2)
{
	main         = renderer.registerParts(new Parts("hashira", "nuki", "shimagi", "kasagi"));
	monitor_sub1 = renderer.registerParts(new Parts("monitor_sub1"));
	monitor_sub2 = renderer.registerParts(new Parts("monitor_sub2"));
	tomoe        = renderer.registerParts(new Parts("tomoe"));
	monitor_num = renderer.registerParts(new Parts("key"));

	monitor_main = renderer.registerParts(new ActionParts(ActionType.TOGGLE, "monitor_main"));
	keys = [];
	for(var i = 0; i < 10; ++i)
	{
		keys[i] = renderer.registerParts(new ActionParts(ActionType.TOGGLE, "key"));
	}
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	var mode = MODE_CLOSE;
	if(entity != null)
	{
		var dataMap = entity.getResourceState().getDataMap();
		mode = dataMap.getInt("mode");
	}

	if(pass == RenderPass.NORMAL.id)
	{
		main.render(renderer);
	}
	else if(pass == RenderPass.TRANSPARENT.id)
	{
		GLHelper.disableLighting();

		var time = renderer.getSystemTimeMillis();
		var rotation = (time * 15.0 / 1000.0) % 360.0;
		var color = getColor(mode);
		var alpha = 0xE0;

		GLHelper.setColor(color, alpha);
		renderMonitorSub(mode, rotation);

		GLHelper.setColor(0x0000CD, alpha);
		renderTomoe(mode, rotation);
		
		GLHelper.setColor(color, alpha);
		renderUI(entity, mode, false);
		
		GLHelper.setColor(0xFFFFFF, 0xFF);
		GLHelper.enableLighting();
	}
	else if(pass == RenderPass.OUTLINE.id)
	{
		;
	}
	else if(pass == RenderPass.PICK.id)
	{
		renderUI(entity, mode, true);
	}

	GL11.glPopMatrix();
}

function onRightClick(entity, parts)
{
	var dataMap = entity.getResourceState().getDataMap();
	var mode = dataMap.getInt("mode");
	var password = dataMap.getInt("password");
	var input_num = dataMap.getInt("input_num");

	if(parts.equals(monitor_main))
	{
		var newMode = mode;
		switch(mode)
		{
		//case MODE_INPUT:   newMode = MODE_INPUT;break;
		case MODE_OPEN:    newMode = MODE_CLOSE;break;
		case MODE_CLOSE:   newMode = MODE_INPUT;break;
		case MODE_FAILED:  newMode = MODE_CLOSE;break;
		case MODE_WARNING: newMode = MODE_INPUT;break;
		}
		setMode(entity, newMode);
	}
	else
	{
		for(var i = 0; i < 10; ++i)
		{
			if(parts.equals(keys[i]))
			{
				if(input_num == 0)
				{
					input_num = 10;
				}
				else
				{
					input_num *= 10;
				}
				input_num += i;

				if(input_num >= 10000)//4桁入力完了
				{
					if(password == 0)//パスワード未設定
					{
						dataMap.setInt("password", input_num - 10000, 3);
						setMode(entity, MODE_OPEN);
					}
					else if((input_num - 10000) == password)//パスワード比較
					{
						setMode(entity, MODE_OPEN);
					}
					else
					{
						setMode(entity, MODE_FAILED);
					}
					dataMap.setInt("input_num", 0, 3);
				}
				else//4桁未入力
				{
					dataMap.setInt("input_num", input_num, 3);
					var time = renderer.getSystemTimeMillis() % 3600000;//1h区切り
					dataMap.setInt("start_time", time, 1);//同期のみ
				}
					
				return;
			}
		}
	}
}

function setMode(entity, mode)
{
	var dataMap = entity.getResourceState().getDataMap();
	dataMap.setInt("mode", mode, 3);
	var time = renderer.getSystemTimeMillis() % 3600000;//1h区切り
	dataMap.setInt("start_time", time, 1);//同期のみ
}

function getPhaseShiftTime(entity, maxTime)
{
	var dataMap = entity.getResourceState().getDataMap();
	var startTime = dataMap.getInt("start_time");
	var time = renderer.getSystemTimeMillis() % 3600000;//1h区切り
	if(startTime == 0 || time >= startTime + maxTime)
	{
		return 1.0;
	}
	else
	{
		return 1.0 - ((startTime + maxTime - time) / maxTime);
	}
}

function getColor(mode)
{
	switch(mode)
	{
	case MODE_INPUT:   return 0xE08000;
	case MODE_OPEN:    return 0x32cd32;
	case MODE_CLOSE:   return 0xE08000;
	case MODE_FAILED:  return 0xE08000;
	case MODE_WARNING:
		var time = renderer.getSystemTimeMillis() % 1200;
		return (mode == MODE_WARNING && time >= 600) ? 0xFF0000 : 0xCC0000;
	}
}

function renderMonitorSub(mode, rotation)
{
	var moveU = 0.0;
	switch(mode)
	{
	case MODE_INPUT:   moveU = 0.0;break;
	case MODE_OPEN:    moveU = 0.5;break;
	case MODE_CLOSE:   moveU = 0.25;break;
	case MODE_FAILED:  moveU = 0.25;break;
	case MODE_WARNING: moveU = 0.75;break;
	}
	GLHelper.preMoveTexUV(moveU, 0.0);
	GL11.glPushMatrix();
	GL11.glRotatef(rotation, 0.0, 1.0, 0.0);
	var split = 6;
	for(var i = 0; i < split; ++i)
	{
		GL11.glRotatef(360.0 / split, 0.0, 1.0, 0.0);
		monitor_sub1.render(renderer);
	}
	GL11.glPopMatrix();
	GLHelper.postMoveTexUV();

	GL11.glPushMatrix();
	GL11.glRotatef(-rotation, 0.0, 1.0, 0.0);
	monitor_sub2.render(renderer);
	GL11.glPopMatrix();
}

function renderTomoe(mode, rotation)
{
	GL11.glPushMatrix();
	renderer.rotate(-rotation, 'Z', 0.0, 2.5, 0.0);
	tomoe.render(renderer);
	GL11.glPopMatrix();
}

function renderUI(entity, mode, isPickMode)
{
	switch(mode)
	{
	case MODE_INPUT:   renderUIInput(entity, isPickMode);break;
	case MODE_OPEN:    renderUIOpen(entity, isPickMode);break;
	case MODE_CLOSE:   renderUIClose(entity, isPickMode);break;
	case MODE_FAILED:  renderUIFailed(entity, isPickMode);break;
	case MODE_WARNING: renderUIWarning(entity, isPickMode);break;
	}
}

function renderUIInput(entity, isPickMode)
{
	var input_num = 0;
	if(entity != null)
	{
		var dataMap = entity.getResourceState().getDataMap();
		input_num = dataMap.getInt("input_num");
	}
	var currentKey = input_num % 10;
	var keyScale = 0.75 + getPhaseShiftTime(entity, PST_KEY) * 0.25;
	var keyPos = (input_num == 0) ? getPhaseShiftTime(entity, PST_CHAR) : 1.0;

	//数字キー0
	GL11.glPushMatrix();
	GL11.glTranslatef(0.0, -1.6 * keyPos, 0.0);
	if(input_num > 0 && currentKey == 0)
	{
		GL11.glTranslatef(0.0, 2.5, 0.0);
		GL11.glScalef(keyScale, keyScale, 1.0);
		GL11.glTranslatef(0.0, -2.5, 0.0);
	}
	keys[0].render(renderer);
	GL11.glPopMatrix();

	//数字キー1~9
	for(var i = 1; i < 10; ++i)
	{
		GLHelper.preMoveTexUV(i * 0.0625, 0.0);
		GL11.glPushMatrix();
		var posX = (0.8 * ((i - 1) % 3) - 0.8) * keyPos;
		var posY = (0.8 * parseInt((i - 1) / 3) - 0.8) * keyPos;
		GL11.glTranslatef(posX, posY, 0.0);
		if(currentKey == i)
		{
			GL11.glTranslatef(0.0, 2.5, 0.0);
			GL11.glScalef(keyScale, keyScale, 1.0);
			GL11.glTranslatef(0.0, -2.5, 0.0);
		}
		keys[i].render(renderer);
		GL11.glPopMatrix();
		GLHelper.postMoveTexUV();
	}

	if(!isPickMode)
	{
		//入力値4桁
		GL11.glPushMatrix();
		GL11.glTranslatef(1.2, 1.6, 0.0);
		var tmp = 1;
		for(var i = 0; i < 4; ++i)
		{
			tmp *= 10;
			var u = (input_num >= tmp) ? 12 : 11;
			GLHelper.preMoveTexUV(u * 0.0625, 0.0);
			monitor_num.render(renderer);
			GL11.glTranslatef(-0.8, 0.0, 0.0);//1->1000の位
			GLHelper.postMoveTexUV();
		}
		GL11.glPopMatrix();
	}
}

function renderUIOpen(entity, isPickMode)
{
	GLHelper.preMoveTexUV(1 * 0.125, 0.0);
	rescaleUI(entity);
	monitor_main.render(renderer);
	GLHelper.postMoveTexUV();
}

function renderUIClose(entity, isPickMode)
{
	GLHelper.preMoveTexUV(0 * 0.125, 0.0);
	rescaleUI(entity);
	monitor_main.render(renderer);
	GLHelper.postMoveTexUV();

	if(entity != null && !isPickMode)
	{
		var distanceSq = MCWrapper.getDistanceSq(entity, MCWrapperClient.getPlayer());
		if(distanceSq < 3.5 * 3.5)
		{
			var dataMap = entity.getResourceState().getDataMap();
			setMode(entity, MODE_WARNING);
		}
	}
}

function renderUIFailed(entity, isPickMode)
{
	GLHelper.preMoveTexUV(2 * 0.125, 0.0);
	rescaleUI(entity);
	monitor_main.render(renderer);
	GLHelper.postMoveTexUV();

	if(entity != null && !isPickMode)
	{
		var distanceSq = MCWrapper.getDistanceSq(entity, MCWrapperClient.getPlayer());
		if(distanceSq < 3.5 * 3.5)
		{
			var dataMap = entity.getResourceState().getDataMap();
			setMode(entity, MODE_WARNING);
		}
	}
}

function renderUIWarning(entity, isPickMode)
{
	GLHelper.preMoveTexUV(3 * 0.125, 0.0);
	rescaleUI(entity);
	monitor_main.render(renderer);
	GLHelper.postMoveTexUV();

	if(entity != null && !isPickMode)
	{
		var distanceSq = MCWrapper.getDistanceSq(entity, MCWrapperClient.getPlayer());
		if(distanceSq < 1.0)
		{
			MCWrapperClient.execCommand("summon Lightning_Bolt @p");
		}
		else if(distanceSq > 3.5 * 3.5)
		{
			var dataMap = entity.getResourceState().getDataMap();
			setMode(entity, MODE_CLOSE);
		}
	}
}

function rescaleUI(entity)
{
	GL11.glTranslatef(0.0, 2.5, 0.0);
	var scale = 0.75 + getPhaseShiftTime(entity, PST_CHAR) * 0.25;
	GL11.glScalef(scale, scale, 1.0);
	GL11.glTranslatef(0.0, -2.5, 0.0);
}
