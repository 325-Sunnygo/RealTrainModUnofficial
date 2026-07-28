importPackage(Packages.jp.ngt.ngtlib.util);

function onUpdate(entity, scriptExecuter)
{
	if(entity.isGettingPower && (scriptExecuter.count % 100 == 0))
	{
		//ジャンプ増強5秒
		scriptExecuter.execCommand("effect @a jump_boost 5");
	}
}
