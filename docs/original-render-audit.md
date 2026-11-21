# 本家描画実装の再照合と追加修正

対象: KaizPatchX 1.7.10とNeoForge版RTMU。Fabric未変更。ユーザーのゲーム・ワールドを起動・操作せず、実行用modsフォルダにも変更を加えていない。

## 確認できた相違と修正

### 1. ミラー面の提出順

本家MqoModel.parseLineは、parsePolygonが生成した面全体をfacesに追加してから、Face.getMirrorが生成した面全体を追加する。getMirrorは三角形リスト全体を先頭固定で逆順にする。

RTMUのネイティブベイクは三角形ごとに元とミラーを交互に出していた。位置・UV・巻き方向・重複数だけを比較する従来の検査では、この順序の差を検出できなかった。元の面全体→全体を反転したミラー面の順に修正し、キャッシュVERSIONを3へ変更した。

### 2. ネイティブモデルのプリミティブ

本家ModelObjectはGL_TRIANGLESでモデルをロードし、NGTRenderHelper.renderCustomModelEveryPartsもGL_TRIANGLESを使う。RTMUは内部で三角形を縮退クワッドに包装し、通常はQUADSで描いていた。

内部保存形式を維持したまま、提出時には三角形の3頂点を取り出し、通常・半透明ともTRIANGLESで提出する。実際の四角形を使うOBJ/ボクセルの保存データでは2三角形を残す。静的エンティティVBO経路も同じ三角形列を使用する。

### 3. テクスチャ指定の欄の混同

本家ModelObjectのtextures配列は、添字2の文字列全体にLight/AlphaBlendが含まれるかを調べ、添字3以降をカスタム画像名とする。

RTMUは各欄をカンマで結合した後で再分割していた。そのため実際のE131の `"AlphaBlend,Light"` はAlphaBlendフラグと `"Light"` というカスタム画像名に分かれ、標準の3枚のライト画像を正しく解決できなかった。

LegacyTextureDescriptorを追加し、元の欄を識別した段階で大小文字を区別してオプションを抽出する。VehiclePackLoader、VehicleModelPackManager、VehicleJsonLoader、InstalledObjectPackLoader、RailPackLoaderの5経路を統一した。カスタム画像数と順序を保持し、無関係な文字列からカンマを削って誤ってフラグを作らない。

### 4. 部品の描画順

本家BasicVehiclePartsRenderer.renderは、各材質に対してbody→door_left→door_rightの順で描き、各親部品の直後に子部品を描く。ネイティブ経路のMQOオブジェクト順だけの提出はこれと異なる。

LegacyVehiclePartOrderを追加し、材質を外側に維持して、車体と設定された左右部品・子部品の順に提出する。RtmTrainRendererとTrainEntityRendererに適用した。

### 5. 名前に基づく自動除外

本家BasicVehiclePartsRenderer/NGTRenderHelperには、名前がguide/atari/角度番号で終わるというだけで登録済みの面を隠す処理はない。両車両レンダラーの名前・距離による除外、および明示的に要求されたグループやdest/type/leverを省く処理を削除。パック・ResourceStateの明示的な除外は維持する。

## 検証

- 回帰検査275件成功。ミラー前後の並び、第三欄のカンマ入りフラグ、第四欄以降のライト画像、実際のTextureBinding、ネイティブ三角形提出、部品の親子順、部品除外を検査。
- E131: 13モデル・642,208三角形。京急: 29モデル・1,200,359三角形。スクリプトモデルと実際に提出するネイティブ三角形列の位置・UV・巻き方向・重複数に加え、グループ・材質ごとの提出順も一致。以前の検査が見落とした順序を追加した。
- 本体・同梱参照モデル219個、472,904面のパース・ベイク検査成功。
- 独立した非表示OpenGL 2.1コンテキストで、本家と同じ固定機能のGL_EQUAL/GL_LESS、SRC_ALPHA/ONE_MINUS_SRC_ALPHAによる全256アルファ値・2パスの結果を取得。現行の実シェーダーの出力RGBAと比較し、誤差1以内で一致。GPU検査全体947件成功。
- 実際のMinecraft NativeImage.readとDynamicTextureを非表示GLコンテキストで使用。E131のparts.pngをGPUから読み戻し、独立したImageIOの元画像と全1,048,576画素のRGBAが一致。
- NeoForge JAR生成成功。

これらは最終ゲーム画面を検証したものではない。床下の黒い壁の症状が解消したとはまだ判定できない。すべての旧APIや描画経路の完全互換を証明する検査でもない。パック内の縦の面が存在することだけを根拠に、移植側に問題がないとは判断しない。

成果物: `outputs/neoforge-original-render-audit/realtrainmodunofficial-1.0.19.jar`
SHA-256: `f5d0e367a75e64b66cbc0ec84a6bd7b7e23f5360f18e3d252b29e4914ac152c1`
