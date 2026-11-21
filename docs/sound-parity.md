# 音システム KaizPatchX 互換メモ

参照: `../reference/KaizPatchX-master/src/main/java/jp/ngt/rtm/sound/` と
`src/main/resources/assets/rtm/sounds.json`、`ModelTrain_*.json` / `ModelVehicle_*.json`。

## 移植・修正したクラス

- `SoundUpdaterTrain` を KaizPatchX の `SoundUpdaterVehicle` + `SoundUpdaterTrain` 相当へ再実装
  - `ModelSetVehicleBaseClient.se` があれば `onUpdate(su)` を 1 本だけ呼ぶ
  - 無ければ `sound_Stop` / `sound_S_A` / `sound_Acceleration` /
    `sound_Deceleration` / `sound_D_S` を速度・ノッチ・加速度で選ぶ
  - ATS チャイム / ATS ベル（signal=1 の走行中）
  - `getSpeed` / `getMaxSpeed` / `getNotch` / `getState` / `getData` / `setData`
  - `inTunnel`、圧縮機状態、コンプレッサーカウント
  - `playSound` / `stopSound` / `stopAllSounds`
- `MovingSoundVehicle` / `MovingSoundTrain` を追加
  - `MovingSoundTrain` は `TrainConfig.maxSpeed` で走行音ピッチを変更
- `SoundUpdaterVehicle` を追加（自動車用。既存の列車用更新器を継承）
- `TrainEntity` / `EntityTrainBase` の走行音を `SoundUpdater*.update()` に統一
- `CarEntity` にも `SoundUpdaterVehicle` を接続
- `MovingSoundCustom` / `MovingSoundEntity` / `MovingSoundTileEntity` は本家同様の
  `setVolume` / `setPitch` / `stop` / `func_73660_a` を維持

## JSON・サウンド定義

- `sound_Stop` / `sound_S_A` / `sound_Acceleration` / `sound_Deceleration` /
  `sound_D_S` / `sound_Horn` / `sound_DoorOpen` / `sound_DoorClose` /
  `sound_ATSChime` / `sound_ATSBell` / `sound_BrakeRelease` / `sound_BrakeRelease2` /
  `sound_CpFin` / `sound_Joint` / `sound_JointReverb` / `sound_Notch` /
  `sound_Reversal` / `sound_Announcement` / `soundScriptPath` を読み込む。
- 同梱の `ModelTrain_*.json` / `ModelVehicle_*.json` の `sound_*` から
  RTMU が付けた `rtm:` 接頭辞を外し、KaizPatchX の JSON 表記へ戻した
  （`TrainConfig.fixSoundPath` / `ModelSetBase.getSoundResource` が従来どおり補完する）。
- 踏切の `sound_Running` を元の `block.rtm_crossingGate0` に戻した。
  `CrossingGateSoundManager` は旧名を `rtm:block.crossing_gate` に解決する。
- `sounds.json` は 1.21 の小文字制約に合わせてキーを正規化し、
  `rtm:` 名前空間付きの `name` を維持している。
- 外部パックの `sounds.json` / `.ogg` は `ExternalSoundPackBridge` が
  KaizPatchX の `RTMSoundHandler` 相当として収集・生成する。

## 前提パック欠落時の挙動

`ModelPackManager.loadScript` は、`//include <...>` の参照先が見つからない場合に
空文字へ置換せず、**明示的に失敗**するようにした。

例:
`Missing script include: scripts/hi03exsoundlib/sound_e233.js
(required by scripts/E233_5000_door_cut_sound_0.js)`

この例外は `ModelSetBase` / `ModelSetVehicleBaseClient` / `ModelPackManager` で捕捉し、
`ERROR` ログに不足パック名を出したうえで、そのモデルはスクリプト無しとして扱う。
そのため、前提パックが無い車両を置いてもサーバーの tick が落ちて
ワールドに入れなくなることはない。KaizPatchX の「include 欠落を黙って無視しない」
仕様は維持しつつ、ワールドを壊さないようにしている。

## 検証

- `compileJava` / `compileTestJava` / `jar`: 成功
- `scriptPreflight`: 82 スクリプト、構文失敗 0
- `legacyScriptRegression`: 全項目成功
- `legacyInventoryRegression`: 全項目成功
- ゲーム・ワールドは起動していない


## 可聴範囲 (KaizPatchX と同じ機構)

- 本家 MovingSoundCustom と同じ: 減衰は LINEAR、初期音量 = range/16。
- `setVolume(vol)` が呼ばれると音量 = vol × `trainSoundVol` に上書きされる
  (スクリプトの playSound / RTMCore.proxy.playSound が該当)。
- 自動走行音 (JSON) は setVolume しないので音量 = trainRunningSoundRange/16 のまま。
- `RTMCore.proxy.playSound` のサーバー側は 256 ブロック以内の全プレイヤーへ
  PacketPlaySound 相当を送り、クライアントで entity/TE 追従の MovingSound として鳴らす。
  送信半径で音量を切らない (減衰はクライアント側の音量で決まる)。
