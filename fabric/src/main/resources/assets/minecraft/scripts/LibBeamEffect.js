var insideColor;
var insideAlpha;
var outsideColor;
var outsideAlpha;

var insideRadius;//rep=1のときは無効
var outsideRadius;
var repeat;
var length;
var section;

var centerNoiseX;//長さ方向のスケール
var centerNoiseY;//R方向のスケール
var radiusNoiseX;
var radiusNoiseY;

var currentTime;
var fadeOutTime;//時間経過で半径を小さくする、0で無効、単位ms
var speedX;//noiseが0のときは無効、単位m/s

var rootLen;
var rootSpl = 16;

var vtxBuf = new Array(16);
var vtxBufOld = new Array(16);
var TRIG_TABLE = new Array(16);

//初期化
{
	for(var i = 0; i < 16; ++i)
	{
		vtxBuf[i] = [0.0, 0.0, 0.0];
		vtxBufOld[i] = [0.0, 0.0, 0.0];
		
		var f0 = 360.0 * (i / 16);
		TRIG_TABLE[i] = [NGTMath.getSinD(f0), 0.0, NGTMath.getCosD(f0)];
	}

	clearParameters();
}

function clearParameters()
{
	insideColor = 0x000000;
	insideAlpha = 0x00;
	outsideColor = 0x000000;
	outsideAlpha = 0x00;
	insideRadius = 1.0;
	outsideRadius = 1.0;
	repeat = 1;
	length = 64.0;
	section = 1.0;
	centerNoiseX = 0.0;
	centerNoiseY = 0.0;
	radiusNoiseX = 0.0;
	radiusNoiseY = 0.0;
	currentTime = 0;
	fadeOutTime = 0;
	speedX = 0.0;
}

function setColor(insideColorP, insideAlphaP, outsideColorP, outsideAlphaP)
{
	insideColor = restrict(insideColorP, 0x000000, 0xFFFFFF);
	insideAlpha = restrict(insideAlphaP, 0x00, 0xFF);
	outsideColor = restrict(outsideColorP, 0x000000, 0xFFFFFF);
	outsideAlpha = restrict(outsideAlphaP, 0x00, 0xFF);
}

function setSize(insideRadiusP, outsideRadiusP, repeatP, lengthP, sectionP)
{
	insideRadius = restrict(insideRadiusP, 0.0, outsideRadiusP);
	outsideRadius = restrict(outsideRadiusP, insideRadius, 256.0);
	repeat = restrict(repeatP, 1, 16);
	length = restrict(lengthP, 0.0, 256.0);
	section = restrict(sectionP, 0.0625, length);
}

function setNoise(centerNoiseXP, centerNoiseYP, radiusNoiseXP, radiusNoiseYP)
{
	centerNoiseX = restrict(centerNoiseXP, 0.0, 256.0);
	centerNoiseY = restrict(centerNoiseYP, 0.0, 256.0);
	radiusNoiseX = restrict(radiusNoiseXP, 0.0, 256.0);
	radiusNoiseY = restrict(radiusNoiseYP, 0.0, 256.0);
}

function setAnumation(currentTimeP, fadeOutTimeP, speedXP)
{
	currentTime = restrict(currentTimeP, 0, (1000 * 60 * 60 * 24));
	fadeOutTime = restrict(fadeOutTimeP, 0, 60000);
	speedX = restrict(speedXP, 0.0, 256.0);
}

/**外部呼び出し用*/
function renderBeam()
{
	rootLen = outsideRadius * 2.0;

	GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glShadeModel(GL11.GL_SMOOTH);
    GL11.glDisable(GL11.GL_ALPHA_TEST);
	GL11.glEnable(GL11.GL_BLEND);
	//GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);//アルファブレンド
	GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
	GL11.glDepthMask(false);

	var tessellator = NGTTessellator.instance;
	tessellator.startDrawing(GL11.GL_TRIANGLE_STRIP);

	for(var i = 0; i < repeat; ++i)
	{
		var r = outsideRadius;
		var color = outsideColor;
		var alpha = outsideAlpha;
		if(repeat > 1)
		{
			r = interpolate(insideRadius, outsideRadius, i, repeat);
			alpha = interpolate(insideAlpha, outsideAlpha, i, repeat);
			var cr = interpolate(ColorUtil.getR(insideColor), ColorUtil.getR(outsideColor), i, repeat);
			var cg = interpolate(ColorUtil.getG(insideColor), ColorUtil.getG(outsideColor), i, repeat);
			var cb = interpolate(ColorUtil.getB(insideColor), ColorUtil.getB(outsideColor), i, repeat);
			color = ColorUtil.encode(cr, cg, cb);
		}
		renderBeamParts(tessellator, r, color, alpha);
	}
	
	tessellator.draw();

	GL11.glDepthMask(true);
    GL11.glDisable(GL11.GL_BLEND);
    GL11.glShadeModel(GL11.GL_FLAT);
    GL11.glEnable(GL11.GL_ALPHA_TEST);
    GL11.glEnable(GL11.GL_TEXTURE_2D);
	GL11.glColor4f(1.0, 1.0, 1.0, 1.0);

	clearParameters();
}

function renderBeamParts(tessellator, r, color, alpha)
{
	tessellator.setColorRGBA_I(Math.floor(color), Math.floor(alpha));

	initVtxBuf(vtxBuf);
	initVtxBuf(vtxBufOld);
	
	var split = (length / section) + rootSpl;
	for(var i = 1; i < split; ++i)
	{
		calcVtx(vtxBuf, i, r);

		tessellator.addVertex(vtxBuf[0][0], vtxBuf[0][1], vtxBuf[0][2]);//縮退三角形用
		
		for(var j = 0; j < 16; ++j)
		{
			tessellator.addVertex(vtxBuf[j][0], vtxBuf[j][1], vtxBuf[j][2]);
			tessellator.addVertex(vtxBufOld[j][0], vtxBufOld[j][1], vtxBufOld[j][2]);
		}

		tessellator.addVertex(vtxBuf[0][0], vtxBuf[0][1], vtxBuf[0][2]);
		tessellator.addVertex(vtxBufOld[0][0], vtxBufOld[0][1], vtxBufOld[0][2]);
		tessellator.addVertex(vtxBufOld[0][0], vtxBufOld[0][1], vtxBufOld[0][2]);//縮退三角形用

		copyVtxBuf(vtxBuf, vtxBufOld);
	}
}

function calcVtx(buf, idx, rad)
{
	var y = 0.0;
	var r = 0.0;
	if(idx < rootSpl)
	{
		var f0 = idx / rootSpl;
		y = rootLen * f0;
		r = rad * Math.sqrt(f0);//根本の太さをすぼめる
	}
	else
	{
		y = (idx - rootSpl) * section + rootLen;
		r = rad;
	}

	var py = y;
	if(speedX > 0.0 && currentTime > 0)
	{
		py -= speedX * (currentTime / 1000.0);//長さ方向にずらす
	}

	if(radiusNoiseX > 0.0 && radiusNoiseY > 0.0)
	{
		r += PerlinNoise.octavePerlin(0.0, py * radiusNoiseX, 0.0, 3, 1.3) * radiusNoiseY;
		r = r < 0.0 ? 0.0 : r;
	}

	if(fadeOutTime > 0 && currentTime > 0)
	{
		var f0 = currentTime < fadeOutTime ? (currentTime / fadeOutTime) : 1.0;
		r -= (rad + radiusNoiseY) * f0;//時間経過で細くする
		r = r < 0.0 ? 0.0 : r;
	}

	var nx = 0.0;
	var nz = 0.0;
	if(centerNoiseX > 0.0 && centerNoiseY > 0.0)
	{
		var noiseY = py * centerNoiseX;
		nx = PerlinNoise.octavePerlin(0.0, noiseY, 0.0, 3, 1.3) * centerNoiseY;
		nz = PerlinNoise.octavePerlin(0.0, -noiseY, 0.0, 3, 1.3) * centerNoiseY;
	}

	for(var i = 0; i < 16; ++i)
	{
		buf[i][0] = TRIG_TABLE[i][0] * r + nx;
		buf[i][1] = y;
		buf[i][2] = TRIG_TABLE[i][2] * r + nz;
	}
}

function initVtxBuf(buf)
{
	for(var i = 0; i < 16; ++i)
	{
		for(var j = 0; j < 3; ++j)
		{
			buf[i][j] = 0.0;
		}
	}
}

function copyVtxBuf(newBuf, oldBuf)
{
	for(var i = 0; i < 16; ++i)
	{
		for(var j = 0; j < 3; ++j)
		{
			oldBuf[i][j] = newBuf[i][j];
		}
	}
}

/*線形補間*/
function interpolate(min, max, idx, split)
{
	return (max - min) * (idx / split) + min;
}

/*上下限値で制限*/
function restrict(value, min, max)
{
	return value < min ? min : (value > max ? max : value);
}
