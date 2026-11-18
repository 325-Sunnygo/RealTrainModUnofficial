# クライアント詳細設定・車体雨粒・揺れ・追従

## RTMU 設定

RTMU 独自のゲーム内設定画面・設定ボタンは削除済み。RTM.cfg は本家 RTM と同じ
24 項目のみで、`config/RTM.cfg` を直接編集する。車両表示 (雨粒/揺れ/追従) は
`config/realtrainmodunofficial/vehicle_visual.json` の既定値で動作する。

## 車内放送 (肉声)

- 乗車中に `M` キーを押している間だけマイクを録音する (push-to-talk)
- 8kHz / mono / 16bit PCM を 20ms フレームでサーバーへ送信
- サーバーは同じ編成に乗っているプレイヤーへ配信する
- クライアントは Java Sound の `SourceDataLine` で複数話者をミックス再生
- マイク/出力デバイスが無い場合は 5 秒間リトライを止めてログを 1 回だけ出す

## 車体の雨粒

`VehicleRainLayer` を追加。

- 雨のとき、車両の屋根・左右側面に少量の雨粒を描く。
- エンティティを増やさず、1 回のバッチで描く軽量実装。
- 雨粒は車体表面に固定され、上から下へ流れる。
- 車両の描画距離・雨密度・粒サイズ・不透明度・速度を設定可能。
- 車体全体の向き付き AABB (`TrainEntity#getRainOcclusionBox`) を使い、
  バニラ雨は車両の存在する XZ 列だけスキップする。
- カメラが車内にあるときは `VehicleRainLayer` 自体を描かない
  (室内に雨粒が浮くのを防ぐ)。グローバルな雨レベル改変はしない。

## 車体の揺れ

`TrainEntityRenderer` に速度依存の微小な揺れを追加。

- 左右動・上下動・ロール・ピッチ・ヨーイング
- 停止時は 0、速度が上がるほど自然に増える
- 設定で ON/OFF、強度、速度を変更可能
- 参考: C-TREC & 月島重工「RTMBodyMotion」の考え方
  （曲線・軌道狂い・分岐・制動・乗客荷重を分けて扱う）
- 現在は軽量なプロシージャル実装。RTMBodyMotion の JS をそのまま
  実行する方式ではない。

## 車両追従

`TrainEntity` のクライアント補間を `ClientMotionBuffer` に接続。

- 到着順に位置・姿勢を貯め、設定した遅延で等速再生
- 前後 1 点を使う Catmull-Rom でカーブの折れを低減
- 設定: `vehicle smooth follow delay ms`（既定 100ms）

## 音の流れ (本家準拠)

- `SoundUpdaterTrain` は同じ domain/path の音を 1 つだけ保持し、
  既存音があれば音量・ピッチ更新のみ行う (`SoundUpdaterVehicle#playSound` と同じ)
- 停止済みの残骸を除去してから再生
- 自動走行音からスクリプト音へ切り替わるとき、古い自動ループを停止
- `stopSound` は最初の 1 本を停止して登録から外す
- 異なる ID の音は同時再生可能
- `RTMCore.proxy.playSound` の一発音は本家 `playSoundAtEntity` と同じく、
  呼ばれた回数だけ重ねて鳴らす

## 検証

- `compileJava` / `compileTestJava` / `jar`: 成功
- `scriptPreflight`: 82 スクリプト、構文失敗 0
- `legacyScriptRegression`: 全項目成功
- `legacyInventoryRegression`: 全項目成功
- ゲーム・ワールドは起動していない
