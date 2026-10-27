# NeoForge モデル・スクリプト互換性の追加監査（2026-10-26）

対象は同梱のKaizPatchX-master参照ソースとNeoForge 1.21.1実装。直前の半透明パスの深度書き込み修正を維持した上で、追加の不具合を修正した。

## 修正内容

1. **LOW精度を無視していたVertex/TextureCoordinate**。本家のVertexはfloor→short、UVは切り捨て→shortで0.0005単位に復元する。create/copy/setVec/setUVに反映。±0の頂点比較・ハッシュも本家に合わせた。
2. **モデル読み込み引数の無視**。精度とTRIANGLES/QUADS指定をモデル・グループ・面の構築に反映し、キャッシュキーにも含めた。Java配列、Nashorn配列からの描画モード指定とFile入力にも対応。
3. **共有頂点が面ごとに複製されていた**。同じMQO頂点を使う面は同じVertexを保持する。スクリプトの変形が隣接面へ反映される。読み込み時の境界サイズも設定する。
4. **量子化した法線の平滑化判定**。内積だけで比較していた処理を、法線の長さを考慮した角度比較へ変更。ミラー頂点と平滑化法線にも指定精度を使用する。
5. **実行中のモデル変更が描画キャッシュに反映されない**。ライブの頂点・UV・法線・面数をキャッシュと照合し、変更時に再構築する。textureCoordinates配列の要素置換も実際の描画へ反映する。変更のないメッシュは既存配列を再利用する。
6. **車両smoothing=falseが無視される**。車体・スクリプトなし車体・車両部品の読み込みに設定値を渡し、キャッシュを分離する。
7. **スクリプトへ渡す車両設定の欠落**。trainType、smoothing、doCulling、offset、scale、notDisplayCab、isSingleTrainを反映。運転台なし車両の速度配列が初期化で標準5段へ置換される問題も修正。
8. **後方台車の探索範囲の末端を除外**。本家の包括的な範囲に合わせ、最終点と単一点の探索を修正。

参照した主な本家ファイル: jp/ngt/ngtlib/renderer/model/{Vertex,TextureCoordinate,ModelLoader,MqoModel,Face,PolygonModel}.java、jp/ngt/rtm/entity/train/EntityBogie.java、jp/ngt/rtm/modelpack/cfg/TrainConfig.java。

## 検証

- モデル精度・引数・共有頂点・UV: 35件成功（実Nashorn配列、実File入力を含む）。
- 実レンダラーの可変メッシュキャッシュ: 4件成功。
- 車両JSON→定義→スクリプト設定: 5件成功。
- レール・半透明回帰: 19件成功。台車の端点・単一点・逆方向探索を含む。
- インベントリ42件、レールプロパティ39件、遅延半透明8件成功。
- 同梱・参照モデル219個、472,904面の読み込み・ベイク成功。
- E131の13モデル、642,208三角形でスクリプトグラフとネイティブメッシュの位置・UV・巻き方向・重複数・提出順が一致。
- 実Minecraft ShaderInstanceの4プログラム、カメラFBO5件、RenderType/BufferUploaderの色・深度40件、サンプラー4組合せ成功。
- 元PNGと実DynamicTextureのGPU読み戻しで1,048,576画素のRGBAが一致。
- その他の既存スクリプト、カメラ、描画・音声APIの回帰検査も成功。
- SRBXの大型レンダラー7本で読み込み・init・プレビューの両パス成功（ワールド操作コールバックは未実行）。
- 同梱スクリプト82本で構文・準備・prelude整合性の失敗0。独立JVMではゲームのModレジストリを起動しないため、preludeのレジストリ参照警告は82本に出る。この検査をゲーム内全動作の成功とは扱わない。

## 残る監査・実機確認

これで全パック・全APIの完全互換を証明したわけではない。以下は別途実装比較と検証が必要。

- スクリプト用ModelLoaderのOBJ/MTL経路（ネイティブOBJベイクとは別）。
- 車両設定全体の網羅、ネイティブ車体経路への精度設定の伝達。
- KaizPatchXの先頭台車RailTransitionResolver、およびレールautoSplitの全挙動。
- ModelLoaderの未検出・破損モデルに対する例外仕様とResourceLocationのドメイン識別。
- SRBXのワールドを書き換える操作、サーバー同期、ゲーム画面での最終表示。

ゲーム・ワールドは起動していない。実行用modsへの自動コピーもしていない。GPU検査は独立した非表示コンテキストで実施。

## 成果物

outputs/neoforge-model-script-parity-audit/realtrainmodunofficial-1.0.19.jar

SHA-256: feb118c5bc35ca94a8459191e73cfe250dd615bab8d769dcb4623202617c1efe

JARのassets配下に大文字パスなし。削除済みの独自編成アイテム実装が再混入していないことを確認。
