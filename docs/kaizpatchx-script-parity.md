# KaizPatchX スクリプト互換 再構築メモ

対象: NeoForge 1.21.1 / RTMU
参照: `../reference/KaizPatchX-master/src/main/java`（ユーザー提供 `KaizPatchX-master.zip`）
信号コントローラー (`jp.masa.signalcontrollermod`) は今回の変更対象外。

## 方針

KaizPatchX のスクリプト実行契約をそのまま使う。

- スクリプト本文は書き換えない。`//include <...>` の展開だけ行う。
- スクリプトエンジンはモデル定義ごとに 1 個。
- サーバースクリプトは `ModelSetBase.serverSE`、GUI は `guiSE`、走行音は
  `ModelSetVehicleBaseClient.se`。
- 実行は `ScriptExecuter.execScript(selector)` →
  `ScriptUtil.doScriptIgnoreError(serverSE, "onUpdate", entity, executer)`。
- 1.7.10 の `Packages.xxx` / `Java.type(...)` / `importPackage` / `importClass` は
  Nashorn の実行時リゾルバで互換クラスへ回す（ソース改変しない）。

## 旧実装から削除したもの

- `CarServerScripts` / `NpcServerScripts` / `InstalledObjectServerScripts`
  （定義ごとの独自スクリプトキャッシュ）
- `AsyncScriptCompiler`（独自の非同期コンパイル）
- `TypeScriptTranspiler` / `LegacySourceMask` / `LegacyClassReferences`
  （本文の TS 変換・FQN 置換・フィールド置換）
- `PackScriptSource` の FQN リマップ / NBT メソッド置換 / フィールド置換 /
  `Block` static 置換
- スクリプト失敗回数による自動停止（KaizPatchX には存在しない）
- `VehicleGuiScripts` の独自スクリプトキャッシュ（`ModelSetBase.guiSE` へ統合）

## 新規・変更した主なクラス

| クラス | 内容 |
| --- | --- |
| `ModelSetBase` | 構築時に `serverSE` / `guiSE` / `guiTexture` を本家どおり読み込む。`getSoundResource`、`dataFormatter` を追加。 |
| `ModelSetVehicleBase` | `sound_doorOpen` / `sound_doorClose` を追加。 |
| `ModelSetVehicleBaseClient` | 車両のボタン画像・方向幕画像・走行音・`se` を追加。 |
| `ModelSetTrain` / `ModelSetTrainClient` | 本家の継承関係を再現。`sound_BrakeRelease` も保持。 |
| `ModelSetVehicle` / `ModelSetVehicleClient` | 本家の継承関係を再現。 |
| `ModelSetScriptOnly` | NPC / 設置物 / 機械用の最小 ModelSet。`serverSE` / `guiSE` を本家同様に構築。 |
| `ModelSetBase.dataFormatter` / `DataFormatter` | `defaultValues` を DataMap へ初期化。 |
| `ScriptExecuter` | `getModelSet().serverSE` のみを使う本家シグネチャへ統一。 |
| `TrainEntity` | `ResourceState` / `DataMap` を本家 API へ。`scriptData` ブリッジ。サーバー tick は `scriptExecuter.execScript(this)`。 |
| `CarEntity` / `EntityNPC` / `EntityInstalledObject` / `InstalledObjectBlockEntity` | `ModelSet` の `serverSE` と `ScriptExecuter` へ統一。 |
| `TrainSpeedManager` | `train.getModelSet().serverSE` を使用。 |
| `TrainEntity` の状態値 | Kaiz の `TrainStateType` と同じ id/意味へ修正（Signal=2, DoorCut=3, ChunkLoader=7, Direction=10, Type=12）。`getSignal` / `setSignal` / `setSignal2` も追加。 |
| `TrainEntity.FormationCompat` | `isFrontCar(entity)` を追加。 |
| `PartsRenderer.getColor` | `ResourceState.color` を読むよう修正。 |
| `SoundUpdaterTrain` | public クラス・public メソッドへ変更（スクリプトから `su.getSpeed()` 等を呼べるように）。 |
| `TrainScriptDataMap` / `CarEntity.ScriptDataMap` | `getHex/setHex`、`getVec/setVec`、`getArray/setArray`、`getArg/setArg` を追加。 |
| `BasicPartsRenderer` / `BasicVehiclePartsRenderer` / `BasicRailPartsRenderer` / `PartsWithChildren` | KaizPatchX の既定レンダラを移植。 |
| `PackScriptSource` | `//include` 展開と、Nashorn 実行時互換リゾルバ（`Packages` / `Java.type`）だけに整理。 |

## 検証

- `scriptPreflight`: 82 スクリプト、構文失敗 0、プリリュード sanity 失敗 0。
- `legacyScriptRegression`: script 28、fast include 10、model 81、precision 36、
  OBJ/MTL 13、rail section 96、camera 1063、render state 28、render API 26、
  selection 8、immediate 9、config 24+90+925、rail/alpha 104、runtime 31 成功。
- `legacyInventoryRegression`: vehicle config 21、mesh cache 4、LOW geometry 65、
  rail property 39、deferred alpha 8、inventory 42 成功。
- `legacyModelCorpus`: 219 ファイル / 472,904 面 / ベイク 219 / 失敗 0。
- `compileJava` / `compileTestJava` / `jar`: 成功。
- ゲーム・ワールドは起動していない。

## 残る差異（1.21 側の描画アダプタ）

以下はスクリプト本文の互換ではなく、1.21 の描画・データ構造へ接続するための
RTMU 側アダプタとして残している。KaizPatchX の 1.7.10 クライアントを
そのままコンパイルすることはできないため、完全な同一ソースではない。

- `TrainScriptSystem` の `ScriptModelRenderer` / `LegacyScriptExecutor`
  （GLRecorder へ記録し、PoseStack / RenderType で再生するための 1.21 層）
- `MqoModelLoader` の 1.21 メッシュ・VBO・半透明パス
- `DataMap` は文字列ベースの既存保存形式とのブリッジを持つ
  （型付き `DataEntry` / namespace の完全再現ではない）
- `EntityInstalledObject` / `InstalledObjectBlockEntity` の `ResourceStateCompat`
  は既存の `scriptData` 形式を維持
- `ModelSetMachine` / `ModelSetSignal` / `ModelSetNPC` / `ModelSetWire` の
  型別クラスは `ModelSetScriptOnly` で代替
- `SoundUpdaterVehicle` は `SoundUpdaterTrain` + `SoundScriptExecutor` で代替
- `BasicSignalPartsRenderer` / `DynamicRailPartsRenderer` / `MCModelRenderer` は未移植
- `EntityTransponder` と `onTrainPass` イベントは未移植（RTMU の ATC は別実装）
- `ResourceState` の NBT 直接入出力、`DataMap` の namespace / 型付き `DataEntry` は未移植
- 1.12 系の `MechanismPartsRenderer` / `FirearmPartsRenderer` は未移植
  （同梱の `RenderGear.js` / `RenderPhalanx.js` 等は RTMU 側のフォールバック描画になる）
- 実バニラ `CompoundTag` に対する旧 SRG メソッド呼び出しは、互換 NBT ラッパー経由のみ対応

信号コントローラーは変更していない。
