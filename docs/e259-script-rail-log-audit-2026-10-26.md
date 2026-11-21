# E259描画・SRBXログ監査（2026-10-26）

対象: NeoForge 1.21.1。実パックE259-1.7.10.zipとSuperRailBuilderX-v1.0.0.zip。前回の高速化JARからの追加変更。

## ユーザーログで確認した事実

- 19:16起動: SRBXの分割・移動・分岐スクリプトは準備、コンパイル、初期化、GUI状態復元まで成功。前回のglGetErrorエラーは見当たらない。
- 19:18:18: 分岐生成がaction=create,result=invalid_requestで失敗。Java例外ではなく、パック側の入力検証で拒否されている。
- 19:19起動: E259O-1の描画・サーバースクリプトの初期化成功を確認。画像の上下反転を示す角度や行列のログはない。スクリプト例外も記録されていないため、画像の症状をログだけで解決済みとは扱わない。

## 修正した不具合

### NGTTessellatorのオーバーロード競合

実E259のinit後、エンティティ相当のデータを与えて通常描画を実行すると、CustomAnimatorの頂点描画で以下を再現した。

`Can't unambiguously select between ... (double,double,double,double,double), (float,float,float,float,float) ... addVertexWithUV ... [Float,Float,Float,Double,Double]`

本家NGTTessellator.addVertexWithUVは固定引数のfloat版（参照ソース361行）。RTMUでは同じ引数数のdouble版も公開しており、座標がJava Float、UVがJS numberの組み合わせでNashornが選べなくなっていた。

本家の固定float引数を優先するようにし、Java側のdouble呼び出しは可変長の橋渡しへ変更した。値は本家どおりfloatへ変換する。E259の実スクリプトが通常・半透明両パスの最後まで通ることと、車体描画時の上向きベクトルおよび終了時の変換復元を検査した。

これは検査で再現した描画停止を直したもの。ユーザー画像の上下反転の直接原因だと証明した変更ではない。

### レール端点変更後の古い曲線

本家TileEntityLargeRailCore.setRailPositionsはrailmapをnullにするが、RTMUは配列の代入だけで古い曲線を保持していた。本家どおりにキャッシュを破棄するよう修正した。実ServerLevel上で端点を変更し、次のgetRailMapが別の曲線と新しい長さを返すことを確認した。

SRBXのinvalid_requestをこの変更で解消したとは扱わない。パック側createBranchBuilderRailは、branchStart/branchEndのkind、座標、アンカー、core/indexなどの検証に失敗するとinvalid_requestを返す。実際に拒否されたリクエスト本文が元ログにないため、どの値が原因かは未確定。

## 次の実機ログへ追加した診断

- 車両描画時にmodel、tick、yaw、pitch、roll、previousRoll、scale、モデルboundsを初期と更新後に出す。
- CarサーバースクリプトがRequestを処理してinvalid_requestを返した場合、モデル名・キー・結果・送信内容（最大8192文字）を出す。パック内容の書き換えはしない。
- サーバーonUpdate例外は原因スタックも記録する。

## 検証

- スクリプト・モデル・設定・レール・インベントリの既存回帰成功。混在したFloat座標とJS数値UVの追加回帰成功（immediate計9件）。
- E259実init/GIF・テクスチャ読み込み後、テスト用エンティティの通常・半透明描画、車体の上向き、終了時の変換復元に成功。ゲーム画面全体のピクセル比較ではない。
- E259設定を使った実車両を検査用ServerLevelへ設置し、設置時と40tick後のyaw/pitch/roll=0を確認。画像の反転はこの条件では再現しなかった。
- 実SRBXの7描画エントリのload/init/preview/HUD記録成功。GameTest必須7件成功（SRBX8サーバーエントリ・作成/取り消し/分割/カント/移動等の従来検査を含む）。
- 実GPUで既存の投影復元、半透明色・深度、サンプラー、PNG全1,048,576画素一致なども成功。JAR生成成功。

E259の実画面での反転とSRBX分岐invalid_requestの解消は未確認。全パック・全スクリプトの完全互換達成とは扱わない。実機での再現ログを追える診断を追加した修正版。

ユーザーのmods、パック、ワールド、設定は変更していない。検査用ワールドと出力はbuild/以下のみ。

成果物: outputs/neoforge-e259-script-rail-audit/realtrainmodunofficial-1.0.19-script-numeric-rail-fix.jar

SHA-256: 8e725f12d10a4caa1d7f95c583cfff8f67bc09b1679816f819b313e788dbadf0
