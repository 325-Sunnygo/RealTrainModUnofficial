# 影描画の本家ソース再照合

対象はNeoForge。Fabricのソースは変更していない。ゲーム・ワールドの起動や操作、実行中インスタンスへのJARコピーはしていない。

## 実際に確認した本家のコード

`../reference/KaizPatchX-master/src/main/java/` 以下:

- `jp/ngt/ngtlib/renderer/model/MqoModel.java`: parsePolygonは頂点0を始点に逆順で三角形化。UVは同じ頂点番号を使う。座標はfloatで0.01倍。
- `jp/ngt/ngtlib/renderer/model/Face.java`: ミラー面は反射座標と逆の面順で生成。
- `jp/ngt/rtm/render/BasicPartsRenderer.java`: モデルの全グループをPartsに登録。影の名前を理由に除外しない。
- `jp/ngt/rtm/render/ModelObject.java`: 材質順に描画。pass1はAlphaBlend指定材質のみ。
- `jp/ngt/rtm/entity/vehicle/RenderVehicleBase.java`: 通常の車体全体を描いた後、半透明の車体全体を描く。通常はalpha==1、半透明はalpha<1、SRC_ALPHA/ONE_MINUS_SRC_ALPHA。

## 今回の修正

1. RtmTrainRendererとTrainEntityRendererに残っていたshadow/影/_ms/_kageの自動非表示を削除。前回までの読み込み側の削除だけでは不十分だった。
2. RtmTrainRendererの静的部分・可動部分を別々に通常→半透明で描く構成を廃止。1回のモデル描画に統合し、全不透明部分→全半透明部分の順にする。各可動グループの変換は引き続き適用する。
3. アルファ判定の説明を、実際に照合したKaizPatchX 1.7.10の関数・定数に訂正。

## E131パックの実データ

ユーザー環境の `[RTM]SERIES_E131v2.0.zip` を読み取り専用で調査した。パック内容はリポジトリに取り込んでいない。

`T-ONREC/E131/mari/Mc.mqo` のalphaオブジェクトには、水平な影面2枚だけでなく、床下を囲む縦の影面6枚もある。縦の面のY範囲は約0.026〜-0.990ブロック、Xは約±1.120ブロック。parts.pngのUVは約V=0.68334〜0.69246。対応する画像には不透明な暗色から低アルファへの勾配がある。縦の面そのものをローダーが捏造したという根拠はない。影面を削除・移動・薄める変更は行っていない。

`legacyPackGeometryRegression`は外部ZIPを受け取り、実際のスクリプトモデルと描画メッシュの三角形を比較する。位置、UV、巻き方向、重複数を照合し、縮退クワッドの4頂点目だけは描画上の包装として除く。三角形の循環移動は許すが逆順は許さない。E131パックの13モデル・642,208三角形で一致。これは2つの現行経路の一致の検査であり、本家ゲームの最終画面とのピクセル一致を証明するものではない。

回帰検査では両方の車両レンダラーについて8種類の影グループ名が通過することも検査した（16件追加）。スクリプト等の既存回帰と合わせ199件成功。実ゲームでスクリーンショットの症状が解消したかは未確認。完全互換達成とは判定していない。
