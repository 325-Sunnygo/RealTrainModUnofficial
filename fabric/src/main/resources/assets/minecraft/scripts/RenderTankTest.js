var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
importPackage(Packages.org.lwjgl.opengl);
importPackage(Packages.jp.ngt.rtm.render);
importPackage(Packages.jp.ngt.ngtlib.math);

//幅10,R25,上角146.31,下角33.69, 56.31(50/75):33.69
//上辺450,上弧63.83978,横辺90.13878,下弧14.70003,下辺300
//全周1087.35718
var a = 0.6383978;
var b = 0.9013878;
var c = 0.1470003;
var d = 3.0;
var e0 = 4.5;
var ab = a + b;
var abc = ab + c;
var abcd = abc + d;
var abcdc = abcd + c;
var abcdcb = abcdc + b;
var abcdcba = abcdcb + a;
var abcdcbae = abcdcba + e0;
var angU = 146.31;
var angD = 33.69;
var wyfu = 0.75;
var wzfu = 2.25;
var wyfd = 0.25;
var wzfd = 1.5;
var wybd = 0.25;
var wzbd = -1.5;
var wybu = 0.75;
var wzbu = -2.25;
var crlCount = 109;
var crlLen = 0.1;

function init(par1, par2)
{
	body = renderer.registerParts(new Parts("body",
		"wheel_u1", "wheel_u2", "wheel_u3", "wheel_u4",
		"wheel_d1", "wheel_d2", "wheel_d3", "wheel_d4", "wheel_d5", "wheel_d6"));
	turret = renderer.registerParts(new Parts("turret1", "turret2"));
	crawler = renderer.registerParts(new Parts("crawler"));
}

function render(entity, pass, par3)
{
	GL11.glPushMatrix();

	GL11.glTranslatef(0.0, 0.05, 0.0);//履帯の高さ分
	
	if(pass == 0)
	{
		body.render(renderer);

		GL11.glPushMatrix();
		if(renderer.isRidden(entity))
		{
			var yaw = renderer.getPlayerYaw(entity) - renderer.getYaw(entity);
			GL11.glRotatef(yaw, 0.0, 1.0, 0.0);
		}
		turret.render(renderer);
		GL11.glPopMatrix();

		var amount = 0.0;
		if(entity != null)
		{
			var msec = renderer.getSystemTimeMillis() % 60000;
			var prevTime = entity.getResourceState().getDataMap().getInt("prev_time");
			amount = entity.getResourceState().getDataMap().getDouble("mov_amount");
			var speed = entity.getSpeed();// + entity.getMoveDir()
			var dif = msec - prevTime;
			amount += speed * (dif / 50);//speed更新はtickごとなので補正
			if(amount >= abcdcbae || amount <= -abcdcbae)
			{
				amount = 0.0;
			}
			entity.getResourceState().getDataMap().setDouble("mov_amount", amount, 0);
			entity.getResourceState().getDataMap().setInt("prev_time", msec, 0);
		}

		for(var i = 0; i < crlCount; ++i)
		{
			GL11.glPushMatrix();

			var mov = amount + i * crlLen;
			if(mov < 0)
			{
				mov += abcdcbae;
			}
			else if(mov >= abcdcbae)
			{
				mov -= abcdcbae;
			}

			if(mov >= 0 && mov < a)
			{
				GL11.glTranslatef(0.0, wyfu, wzfu);
				var pitch = angU * (mov / a);
				GL11.glRotatef(pitch, 1.0, 0.0, 0.0);
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
			}
			else if(mov >= a && mov < ab)
			{
				GL11.glTranslatef(0.0, wyfu, wzfu);
				GL11.glRotatef(angU, 1.0, 0.0, 0.0);
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
				GL11.glTranslatef(0.0, 0.0, mov - a);
			}
			else if(mov >= ab && mov < abc)
			{
				GL11.glTranslatef(0.0, wyfd, wzfd);
				var pitch = angD * ((mov - ab) / c);
				GL11.glRotatef(pitch + angU, 1.0, 0.0, 0.0);
				GL11.glTranslatef(0.0, -wyfd, -wzfd);
			}
			else if(mov >= abc && mov < abcd)
			{
				GL11.glTranslatef(0.0, wyfd, wzfd);
				GL11.glRotatef(180.0, 1.0, 0.0, 0.0);
				//GL11.glTranslatef(0.0, -wyfd - (wyfu - wyfd), -wzfd - (wzfu - wzfd));
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
				GL11.glTranslatef(0.0, 0.0, mov - abc);
			}
			else if(mov >= abcd && mov < abcdc)
			{
				GL11.glTranslatef(0.0, wybd, wzbd);
				var pitch = angD * ((mov - abcd) / c);
				GL11.glRotatef(pitch + 180.0, 1.0, 0.0, 0.0);
				//GL11.glTranslatef(0.0, -wybd - (wyfu - wybd), -wzbd - (wzfu - wzbd));
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
			}
			else if(mov >= abcdc && mov < abcdcb)
			{
				GL11.glTranslatef(0.0, wybd, wzbd);
				GL11.glRotatef(180.0 + angD, 1.0, 0.0, 0.0);
				//GL11.glTranslatef(0.0, -wybd - 0.5, -wzbd - 3.75);
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
				GL11.glTranslatef(0.0, 0.0, mov - abcdc);
			}
			else if(mov >= abcdcb && mov < abcdcba)
			{
				GL11.glTranslatef(0.0, wybu, wzbu);
				var pitch = angU * ((mov - abcdcb) / a);
				GL11.glRotatef(pitch - angU, 1.0, 0.0, 0.0);
				//GL11.glTranslatef(0.0, -wybu, -wzbu - 4.5);
				GL11.glTranslatef(0.0, -wyfu, -wzfu);
			}
			else if(mov >= abcdcba && mov < abcdcbae)
			{
				GL11.glTranslatef(0.0, 0.0, mov - abcdcbae);
			}

			crawler.render(renderer);

			GL11.glPopMatrix();
		}
	}

	GL11.glPopMatrix();
}
