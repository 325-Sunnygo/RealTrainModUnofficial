# 柱に取り付けた信号の破壊修正

NeoForge 1.21.1。ブロック削除中のonRemoveから元ブロックを再設置していた処理を、onDestroyedByPlayerへ移動した。

信号だけを外す際には元のBlockStateとタイルNBTを復元し、元がRTMU架線柱と同じInstalledObjectBlockでもブロックエンティティを新しく生成する。同一BlockStateでもクライアントへ更新を通知し、信号のネットワーク登録を解除する。元の柱へ接続した配線はこの処理では撤去しない。サバイバルの信号ドロップは1個、クリエイティブでは0個。復元した柱へ通常の信号採掘ドロップ処理を重ねない。

ユーザーが自身で試すとの指示により、追加の実機テストは行わない。指示前に実施した隔離GameTestサーバーの3件は成功（クリエイティブの柱・配線保持、サバイバルの柱保持と1個ドロップ、別Block型の元ブロック復元）。クライアントでの見え方と連続クリックの確認はユーザーの試験に委ねる。

GameTestはbuild/signal-gametestのみを使用。開発用音声・画像データのダウンロードは、サーバー試験には不要なので除外した。再実行する場合: Java 21で `./gradlew :runGameTestServer -x neoFormJoined1.21.1-20240808.144430DownloadAssets --no-parallel --max-workers=1 --no-configuration-cache`。

JAR生成成功。ランチャーへの自動配置は除外。テストクラスを配布JARへ含めない。

成果物: `outputs/neoforge-signal-removal-fix/realtrainmodunofficial-1.0.19.jar`

SHA-256: `f410946c1668dd4cfbe3b1a2aa70a8dcf1ad6e811025f01eacebe4c0a1688839`
