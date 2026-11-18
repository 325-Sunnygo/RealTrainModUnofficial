# ATC (EntityATC) KaizPatchX 互換メモ

参照: `../reference/KaizPatchX-master/src/main/java/jp/ngt/rtm/entity/EntityATC.java`
および `EntityElectricalWiring` / `EntityInstalledObject` / `RenderEntityInstalledObject` /
`TileEntityDummyEW` / `RenderElectricalWiring`。

## 実装した挙動

- ATC はブロックではなく `EntityATC`（`EntityElectricalWiring` 派生）としてレール上に生成。
  上面クリック + レールマップのある位置だけ設置。
- レール曲線上の位置・yaw・pitch・roll を本家 `setEntityOnRail` と同じ式で設定。
- `followRailAngle` を `ModelMachine_ATC_*.json` から読み、レンダラーで
  `yaw → pitch → roll` の順に適用。
- `getSubType()` = `"Antenna_Send"`。
- `getElectricity()` = `-1`（供給せず、受け取った信号をレールへ流すだけ）。
- `setElectricity(int)` は真下 8 マス以内の最初のレールコアへ `setSignal`。
- ダメージ時にレール信号を 0 に戻す。チャンクアンロードでは消さない。
- 破壊時に ATC アイテムをドロップ。
- 中クリックで、選択モデル ID と DataMap を保持した ATC アイテムを返す。
- スニーク + 素手でモデル選択 GUI を開き、決定したモデル / DataMap / 色を反映。
- 設置アイテムの DataMap / 色 / カスタム名を ATC へ引き継ぐ。
- 定義の `defaultValues` を DataMap へ初期化。
- `ResourceState` / `DataMap` の NBT 保存・復元を追加。
- `IEntityWithComplexSpawn` を実装し、正確な位置・向き・State を spawn data で送信。
- 設置物エンティティの描画ヨーを本家と同じ `+rotationYaw` に修正。
- 入力コネクタの取り付け先が ATC エンティティの場合も `setElectricity` を届ける。

## 直接配線（本家 TileEntityDummyEW / RenderElectricalWiring 相当）

- `EntityElectricalWiring` に本家 `TileEntityElectricalWiring.connections` 相当を追加。
  - root / 非 root 接続、TO_PLAYER、WIRE、TO_ENTITY、DIRECT を保持。
  - NBT 保存・復元。
  - `SynchedEntityData` でクライアントへ接続を同期。
  - `WireManager` の座標グラフへ ATC/検知器/車止めの位置をノードとして登録。
  - 信号伝播時は `WireManager.propagate` から `setElectricity` が呼ばれる。
- ワイヤーアイテムで ATC を直接右クリックして接続可能。
  - 1 回目: 始点を記録し、TO_PLAYER の仮ワイヤーを手元へ描画。
  - 2 回目: 相手へ WIRE / TO_ENTITY 接続を張り、アイテムを消費。
  - 同じ ATC を再クリック: キャンセル。
- `InstalledObjectEntityRenderer` で root 接続のワイヤーを
  `WireScriptRenderers` 経由で描画。TO_PLAYER の仮ワイヤーにも対応。
- ワイヤーが見えるよう、接続を持つエンティティは描画 AABB を接続先まで拡張。

## 検証

- `compileJava` / `compileTestJava` / `jar`: 成功
- `legacyScriptRegression`: 全項目成功
- `legacyInventoryRegression`: 全項目成功
- `scriptPreflight`: 82 スクリプト、構文失敗 0
- ゲーム・ワールドは起動していない
