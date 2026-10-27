# NeoForge 1.7.10スクリプト互換の調査・修正記録

初回記録: 2026-10-24。参照元はユーザー提供の `KaizPatchX-master.zip`。今回の優先対象はNeoForge 1.21.1。

**完全互換は未達です。** 以下の修正と自動検証は完了していますが、任意の1.7.10用スクリプトの動作、実機での描画一致、ワールド操作の一致まで保証する結果ではありません。既知の不足を末尾に記載します。


最新更新（2026-10-25）: 点の四角形展開を廃止し、ネイティブGL_POINTSへ移行。整数ピクセル境界を含む420条件と半透明重なり1条件でGPU比較が一致。旧GL即時描画・NBTの追加APIも実装。最新成果物と制限は末尾。以下の各節は作業履歴であり、以前の残件には後続の修正で解消したものを含む。

## 影オブジェクトの自動削除を撤去

- MQOの面を高さ・大きさ・平面性・オブジェクト名で影板と推測して捨てる処理を削除。
- `shadow`、`shadow_*`、`*_shadow`、`影`、`_kage`の名前による描画除外を削除。
- 車両パックの名前によって影などのグループを特別に消す処理を削除。
- スクリプト側のグループ選択・ベイク済みモデル描画の両方から影除外を削除。
- モデルキャッシュの形式番号を更新。以前の面削除済みキャッシュは再利用しない。
- 作者がスクリプトや `ResourceState.exclusionParts` で明示的に隠す動作は維持。

## ポリゴンと陰影

- 本家の三角形化と同じ頂点順へ変更。四角形は `0,3,2 / 0,2,1`。以前は対角線が異なっていた。
- UVも同じ元頂点の順序で展開する。
- 非平面ポリゴンの面法線は、各三角形の単位法線を加算して正規化する。
- ベイク時のスムージングは、分割三角形数で隣接面を重複加算せず、元のポリゴンを単位にする。
- スクリプト向けモデルも同じ三角形リストを持つ。描画時にそのリストを再び多角形として扇形分割していた処理を修正。
- スクリプト描画で面法線／頂点法線をテッセレータへ設定。
- 元の重複面を独自判定で削除しない。ミラー面をスクリプト向けモデルにも生成。
- グループ名からカットアウトや頂点押し出しを推測する処理を、通常のバッチ描画経路から除去。

## テクスチャと材質

- 材質名→JSON指定→`default`という本家の対応付けに統一。別材質の先頭テクスチャ、数値名、MQO埋め込みパスを独自の代替にしない。
- スクリプトに渡す `Material.id` と `ModelObject.textures` の添字を元の材質番号に揃える。Mapの列挙順へ依存しない。
- 車両・設置物・架線・レールのスクリプト向け材質設定を共通化。
- `Light` / `AlphaBlend` は1.7.10の大文字小文字を区別する部分一致として解釈。
- 明示的なライト用テクスチャの数と順序を維持。無指定時は `_light0`〜`_light2`。
- 発光テクスチャが欠けても後続の添字を詰めない。
- MQOの `col` を独自にテクスチャへ乗算する処理を廃止。本家のMaterialはIDとテクスチャを保持する。
- 画像の中間アルファだけからAlphaBlendを推測しない。通常パス／半透明パス用マスクでは元の画素のRGBと中間アルファ値を維持。
- 半透明の通常バッチ経路を、深度書き込みを持つ本家相当のブレンド状態へ変更。
- 動的テクスチャの識別子にパックの識別情報を含める。同じ画像パスを持つ別パック同士の上書きを防止。
- 任意ライト画像のCPU処理を描画スレッドへの所有権移動より先に済ませ、登録も共通の描画スレッド経路へ統一。
- 指定画像が見つからない場合はMinecraftのmissing textureを使う。

## 共通スクリプト処理

- includeを先頭へ集めず指定位置へ展開。同じincludeの複数回利用、循環・欠落の検出。
- UTF-8 BOMとMS932の日本語文字。
- `Packages`、直接修飾名、`Java.type`の旧クラス名変換とクラス名の境界判定。
- 本文の互換変換で文字列・コメント・一般的な正規表現リテラルを保護。
- 任意イベントの `execScript(selector, eventName, args...)` とモデルの `serverSE` 呼び出し。
- `onUpdate`が例外／未定義でもtickを進める。毎回callerと座標を更新する。
- 後から定義される関数を負のキャッシュで呼べなくしない。
- コマンドブロック設定とコマンド実行権限。
- `ChunkCoordinates`のMCP/SRGフィールド。
- 実際のサウンドコンテキストを `SoundUpdaterTrain` の派生型に変更。
- `ColorUtil`と`PerlinNoise`を本家から移植。
- 足場の引数違い、レールのオフセット保存・座標・描画API、NPCアニメーション、設置物／NPCのサーバーイベント経路を修正。

## 自動検証結果

| 検証 | 結果 |
|---|---|
| NeoForgeのコンパイルとJAR作成 | 成功 |
| スクリプト前処理・文字コード等 | 40件成功 |
| 実パーサー／ベイク・材質・法線・影オブジェクト | 26件成功 |
| 実Nashornによるイベント・tick・座標等 | 31件成功 |
| ローカルに存在するMQO/MQOZ | 219ファイル・472,904面、読み込み／頂点／法線検査失敗0 |
| RTMU標準スクリプト | 82ファイル、include展開後の構文失敗0 |
| 提供KaizPatchX標準スクリプト | 46ファイル、構文失敗0 |
| fix-rtm同梱スクリプト | 1ファイル、構文失敗0 |
| E257同梱スクリプト | 1ファイル、構文失敗0 |

構文検査はコンパイルを使用し、実行時例外を「構文成功」の根拠にしないよう検査処理自体も修正した。ゲーム外ではレジストリに依存するプリリュードのクラス初期化が失敗するため、バインド警告は残る。これは成功した実機検証として扱わない。Minecraftの起動、ユーザー画像の車両の実描画、サーバーワールドでの動作は未検証。

全Java宣言の静的調査は `legacy-script-api-audit.json`。javacでpublic/protectedの名前・引数数・コンストラクタ・フィールド・解決可能な継承元を調べる。745宣言を対象とする。ゲーム本体の未移植クラス、別名への移植、可変長引数なども含むため、差分件数は互換率ではない。

## 残る既知の不足

- GL状態はコマンド記録と専用シェーダーへ接続したが、任意の旧OpenGL APIは網羅していない。glCullFace/glDepthFunc等の全状態、線・点プリミティブ、CURRENT_BITのテクスチャ座標などには残件がある。外部シェーダーパックとの組合せも未検証。
- `atsSound`は実サウンドへ接続し、停止・音量・ピッチのハンドル操作を検査した。実ゲームでの警報開始条件、音源リソース解決、車両追従音の聴取確認は未実施。
- 旧Minecraftの全クラス／フィールド／メソッドをエミュレートしていない。静的API差分の全件と、ワールド・NBT・外部MODへの操作は追加の実装／動作検証が必要。
- JavaScript本文変換は完全な構文木変換ではない。動的クラス名、複雑な式のレシーバ、テンプレート式など全構文での互換は未達。
- 純粋なCPU検査だけではGPU・シェーダー・描画順の視覚的一致を保証できない。
- Fabricは今回の最終対象外。先行して行った共通部分の修正は残しているが、最終NeoForge描画修正の反映と全体ビルド検証は未実施。元からのNeoForge専用ModelData参照などのビルド問題も残る。

## 再実行

Java 21を `JAVA_HOME` に設定して、プロジェクト直下で実行する。

```sh
./gradlew :legacyScriptRegression :legacyModelCorpus :scriptPreflight --no-parallel --max-workers=1 --no-configuration-cache
python3 tools/audit_legacy_script_api.py
./gradlew :jar -x copyJarToModrinthMods --no-parallel --max-workers=1 --no-configuration-cache
```

既存のビルド設定にはランチャーへJARを自動配置するfinalizerがある。今回のJAR作成ではそのタスクを明示的に除外し、ランチャーのmodsは変更していない。

成果物: `build/libs/realtrainmodunofficial-1.0.19.jar`

SHA-256: `5ad5a18e934118f44db0a4537780e5aa6d73425379dc93638a31f3e9dbbe4240`

## Missing Objectの追加修正

材質定義のないMQOで材質名がnullになると、不変Mapに対するget(null)がNullPointerExceptionを投げ、モデル読込が失敗する回帰を再現した。材質名なしの場合はdefaultを直接参照するよう修正。空の不変Mapとdefaultを持つ不変Mapの両方、および材質チャンクが実際に存在しないMQOのベイクを回帰テストへ追加した。

全219モデルの検査を、スクリプト向けモデルのパースだけでなく実際の描画バッチのベイクまで拡張した。Model_none.mqoは面を持たない意図的な空モデルとして扱う。219モデルすべてでベイク例外なし。回帰テストは40+26+31件成功。検査では画像のGPU登録を行わない。ユーザーのゲーム起動ログは未入手なので、報告された実際のMissing Object表示と今回再現した例外が同じ原因かは未確認。

追加修正版: `outputs/neoforge-missing-object-fix/realtrainmodunofficial-1.0.19.jar`

SHA-256: `31e5985b23890f559cd8f8bdaba85b1070143d58c0b66fe7f828a48a65d82d3c`

GL状態接続の途中保存は `.codex-wip/gl-state/`。以下の追加修正版には、そこから引き継いだ描画・音声の実装を含む。


## 描画状態・警報ハンドルの追加修正

NeoForge側のGL11Facadeについて、enable/disable、blendFunc、個別のRGB/alpha合成、depthMask、alphaFunc、shadeModel、lineWidth、属性スタックの状態を各描画コマンドに保存し、再生時のRenderTypeと専用GLSLへ接続した。表示リスト複製でも状態を保持し、ネスト再生後は前の状態へ復帰する。CURRENT_BITでは色・法線を復帰する。NGTTessellatorは頂点法線を保持し、三角形ストリップ・四角形ストリップ・ポリゴンを展開する。発光パスにも状態を反映し、加算合成を引き継ぐ。

SoundUpdaterTrainを車両ごとに保持し、atsSoundはダミー配列からMovingSoundEntityの実ハンドルへ変更。旧リフレクションからのstop/setVolume/setPitchをネイティブのTickableSoundInstanceへ接続した。モデル変更・エンティティ消滅で停止する。初期音量はSoundManagerへ渡す前に設定する。

追加検証:
- スクリプト40件、モデル26件、実行環境31件、描画・音声状態26件の回帰テスト成功。
- 219モデル・472,904面の再読込と実ベイク失敗0。
- 標準82スクリプトの構文・準備失敗0（ゲーム外バインド警告82件あり）。
- 専用シェーダーをvanillaのincludeと展開し、glslangValidatorでコンパイル・リンク成功。
- Apple M2の非表示OpenGLコンテキストに実際の専用シェーダーをロードし、頂点alpha適用後の8種類のalpha比較結果をフレームバッファから読み戻して成功。
- NeoForgeコンパイル・JAR作成成功。ランチャーへの自動配置は除外。

このGPU検査はMinecraft本体のワールド描画を起動していない。車両画像との一致、ユーザー環境のMissing Object解消、全パックの実行時互換を保証する検査ではない。完全互換の残件は上記のとおり。

再検査: `python3 tools/verify_legacy_gpu.py`（Java 21、macOS arm64のLWJGL 3.3.3キャッシュ、glslangValidatorが必要）。

最新成果物: `outputs/neoforge-compatibility-fix/realtrainmodunofficial-1.0.19.jar`

SHA-256: `6b9d79842e97f063d6bfbdabe6d1434298ed005acc9a1ff2272bcb14ed1d7c8f`


## スクリプト互換の追加修正（Missing Objectは対象外）

ユーザーの訂正により、Missing Objectは今回の調査対象から除外。NeoForgeの1.7.10スクリプトAPIを本家ソースと照合した。

- NGTRenderHelperの名前だけの代理描画を置換。渡されたモデルの面を描き、matId・smoothing・except・描画モードを適用する。名前は本家と同じ完全一致、空のexceptリストも本家に合わせる。
- IRenderer、PolygonRenderer、IModelNGT、FileType、DisplayList、Lockerを追加。モデルの読み込み元名とgetTypeを保存。
- Parts.objNamesを公開し、getObjectsは見つからない名前の位置をnullのまま保持する。
- GLHelperの表示リストを本家同様のvalue付きハンドルへ変更。isCompiling、isValid、再利用、削除、再読込時の無効化を実装。入れ子のコンパイルも元の記録先へ復帰する。
- GLHelperとPartsRendererのlighting切替を実際の記録状態へ接続。lightmap値を勝手にリセットしない。
- preMoveTexUV/postMoveTexUVを空実装から、ネスト対応のUV平行移動状態とGLSLへ接続。

検証: 回帰テスト149件成功（スクリプト40、モデル26、描画・音声26、描画API26、実行環境31）。Nashornで本家シグネチャの可変長引数、DisplayList.value、リストと材質指定描画の組合せを実行した。標準82本と提供KaizPatchX46本の準備・構文検査で失敗0。ただしゲーム外のプリリュードバインド警告は残る。GPU検査は10件成功し、UV移動前後の赤・緑テクセルの読戻しも含む。NeoForgeビルド成功。

NGTRenderHelper、GLHelper、Partsの静的API棚卸しでは、名前・引数数・フィールドの欠落候補が0になった。ただし型・実装・任意の外部パックの動作の完全一致を意味しない。

既知の残件: PartsRendererのインベントリ取得・アイテム描画には空実装がある。マウスピック、任意の旧Minecraft API、独自GLSL、未対応GL状態もあるため、**スクリプト完全互換は未達**。全パックのゲーム内イベントと描画を実行した検証ではない。

成果物: `outputs/neoforge-script-compatibility/realtrainmodunofficial-1.0.19.jar`

SHA-256: `3f5f9c4d5242fcdb1e5ea75977a968704957ef804dba515546e67f3dca869a06`


## インベントリ・ピック・描画再生の追加実装

- PartsRendererのgetInventoryItem/getStackSize/renderItemを接続。ネイティブContainer、Playerインベントリ、NeoForge IItemHandlerから取得し、空と範囲外はnull、スタック数は実数を返す。描画はItemStackのコピーとseedを記録し、Minecraft ItemRendererのFIXEDコンテキストで再生する。
- ScriptModelRenderer側の同APIも接続し、インベントリ依存のスクリプト再生キャッシュを無効化する。NGTRenderHelper.getItemRendererはnullでなく旧renderItem入口を返す。
- 通常再生経路で欠けていたMULT_MATRIXを実装。tess専用経路でもPoseStackの法線行列を更新する。
- LegacySelectionでGL_SELECT相当のCPU選択を実装。glLoadNameとActionPartsのID、記録済み行列、モデルの面とtessの三角形／ストリップ等を処理し、中央のピック矩形・近遠クリップでIDと0..1の最小深度を返す。VehicleScriptRenderersの実model-view/projection/viewportをスコープで渡す。
- glDepthFuncとglCullFaceを状態記録・RenderTypeへ接続。GL_ALWAYSでも深度試験を有効にし、深度書込みが消えないよう専用状態を使用する。
- LINES、LINE_STRIP、LINE_LOOPをネイティブのDEBUG_LINESへ展開する。
- GLHelper.getShaderProgramはソースのコンパイル・リンク・検証を実行し、途中の失敗時はGPUオブジェクトを破棄する。正常時は実プログラムIDを返す。
- NGTObjectRendererの空入口をNGTRendererへ接続。SoundUpdaterTrain.getStateは旧・新双方の車両状態を参照する。

検証: スクリプト40、モデル26、描画・音声状態28、描画API26、選択8、実行環境31、インベントリ／実描画再生14の合計173件成功。インベントリ検査は別JVMで空のLoadingModListとvanillaレジストリを初期化し、実Items.STONE/ItemStack/ContainerとNashornを使用する。実リプレイに捕捉VertexConsumerを渡し、線ループの6頂点と行列変換後の座標も検査した。これはゲーム本体での描画試験ではない。

標準82本と提供KaizPatchX46本は準備・構文失敗0（ゲーム外のバインド警告は残る）。実GPU検査は12件成功（alpha比較8、UV移動2、独自プログラム正常生成1、不正ソース拒否1）。NeoForgeコンパイルとJAR生成成功。自動デプロイは除外した。

**残る不足:** 任意の旧Minecraft／外部MODの全クラス・APIは未移植。GL_POINTSや任意の固定機能GL、旧GLSLの互換プロファイル専用組込み変数は網羅していない。CPU選択は現在VehicleScriptRenderersからの行列コンテキストに対応し、線・点の選択、カリングや全name-stack操作、他描画経路の同時選択は未対応。レガシーGUI用RenderItemの全オーバーロードは未実装。ネイティブアイテム描画に任意のスクリプトGL状態を全面適用する検証も残る。複雑なJavaScript式の完全な構文木変換、全パックのゲーム内イベント・音声・視覚的一致は未確認。従って**完全互換は未達**である。

追加の再検査: `./gradlew :legacyScriptRegression :legacyInventoryRegression`（Java 21）。GPUは`python3 tools/verify_legacy_gpu.py`。

最新成果物: `outputs/neoforge-script-compatibility-extended/realtrainmodunofficial-1.0.19.jar`

SHA-256: `0374c60ee755a52034b8b0bf517a88ab48ded84c31b25a13b65b1ea1801962ec`


## 点・GUI・色・NBT書込みの追加修正

NeoForgeのみを更新。Missing Objectを対象に戻していない。

- GL_POINTSを画面ピクセルサイズの四角形へ展開。glPointSizeとGL_POINT_BITの保存・復帰を追加し、viewportからサイズを計算する。中心がクリップ外の点は描かず、描画時にはGPUのaliased point size範囲へ制限する。
- LegacyItemRendererへ旧renderItemIntoGUI、renderItemAndEffectIntoGUI、renderItemOverlayIntoGUIを追加。座標・zLevel・スタックのコピー・明示テキスト・glintの有無を記録し、GuiGraphicsで再生する。記録外ではGuiGraphicsコンテキストを使用でき、なければネイティブのGUIバッファを使う。
- glColor4fの色が通常モデル／発光モデルへ届かない経路を修正。LegacyColorBufferで提出頂点へ色を反映し、NGTTessellatorの明示頂点色と現在色を区別する。頂点状態とキャッシュ比較にもこの区別を保持する。
- ItemStackのCUSTOM_DATAから返す旧NBTを編集してもネイティブへ残らない問題を修正。複合タグ・入れ子リストの変更を観測してコンポーネントを書き戻す。弱参照の共有ビューで同じスタックのタグ参照を共有し、setTagCompound(null)や置換後は旧参照を切り離す。copyは独立する。
- 旧NBTの非難読化メソッド名とbyte/short/long/配列等のSRG入口を追加。ItemStackのstackSizeとfield_77994_aを実個数へ接続し、コピー・分割・個数・耐久度APIを追加した。

検証:
- 回帰194件成功（スクリプト40、モデル26、描画状態28、描画API26、選択8、実行環境31、ネイティブ在庫・再生・GUIコマンド・NBT35）。
- GUIコマンドの座標、zLevel、overlay文字列、glintの違い、Nashornの旧オーバーロードを検査。GUIのゲーム内表示試験ではない。
- 同じItemStackのタグ同一性、入れ子の書込み、コピー独立性、削除後の旧参照、Nashornの旧フィールド代入と耐久度値を検査。
- GPU16件成功。既存12件に加えて、画面座標がピクセル境界にない1/4/7pxの点をネイティブGL_POINTSと全ピクセル比較し、クリップ外の点も検査した。
- 標準82本は準備・構文失敗0。提供KaizPatchX46本も同じ検査を実行。ゲーム外のプリリュードバインド警告は残る。
- NeoForgeコンパイルとJAR生成成功。ランチャーへの自動配置は除外。

**未達・制限:** Apple M2では整数画面座標のピクセル境界で、ネイティブGL_POINTSと四角形展開のラスタライズ位置に差を確認した。この境界ケースは解消済みと扱わない。旧GUIの実機表示、任意の旧Minecraft／外部MOD API、旧メタデータと現行アイテム種類の全面対応、全パックの実行イベント・音声・描画の一致は未検証／未実装が残る。NBTは旧ラッパーの変更メソッドを観測し、ネイティブTagや配列を直接操作する全経路を自動追跡するものではない。前節の選択コンテキスト／固定機能GL等の残件も継続する。完全互換達成の報告ではない。

最新成果物: `outputs/neoforge-script-point-gui-nbt/realtrainmodunofficial-1.0.19.jar`

SHA-256: `927e58ccf63935b65f3502fc5c85ba0169da60ded0fbe9fe4db05d437caabecc`


## 点の境界差を解消・旧即時描画とNBT APIを追加

- 近似的な四角形展開とシェーダー内のピクセル丸めを廃止。専用RenderTypeが同一中心の4頂点を1点へ詰め直し、Minecraftの頂点レイアウト・シェーダー・テクスチャ・行列を使ってネイティブglDrawArrays(GL_POINTS)で描く。ドライバー固有の境界丸めとサイズ制限は本来のOpenGLへ委ねる。
- 固定点サイズとGL_PROGRAM_POINT_SIZEの状態を保存・復帰。専用動的VBOを再利用し、シェーダー再登録時に破棄する。
- glBegin/glEnd、glVertex2f/2d/3f/3d、glTexCoord2f/2d、glColor3d/4d、glNormal3dを追加。記録経路で頂点ごとの色・UV・法線を保持し、CURRENT_BITでUVも復帰する。スクリプト例外でglEndが欠けた場合は記録終了時に破棄し、次のスクリプトへ持ち越さない。
- 旧NBTのgetTag/getTagId/getId/merge、およびリストの型・float/double値取得・要素置換・コピーを追加。SRG別名も提供する。

検証:
- GPU433件成功。点について12種類のX座標（整数境界・直前直後・クリップ境界外を含む）×5種類のY座標×7サイズで420条件の全ピクセルを従来のGL_POINTSと比較した。点サイズ1.5/2.5の丸めも含む。重なる半透明3点の比較1件も成功し、4重描画による合成の変化がないことを確認。残り12件は既存のalpha/UV/独自プログラム検査。
- 回帰206件成功（スクリプト40、モデル26、描画状態28、描画API26、選択8、即時描画8、実行環境31、ネイティブ在庫・GUI・NBT39）。旧GL11のFQNを準備処理で変換し、Nashornから直接即時描画APIを呼んで確認した。
- 標準82本の準備・構文失敗0。ゲーム外のバインド警告は残る。
- NeoForgeビルドとJAR生成成功。ランチャーへの自動配置は除外。

前節で記録したApple M2の点描画境界差は、今回のネイティブ化で検証対象全条件において解消した。Minecraftのワールド描画・外部シェーダーパックとの組合せは未検証。即時描画APIはGLRecorderを使う経路を対象とし、任意の固定機能GLや旧Minecraft API全体を移植した報告ではない。メタデータ変換、選択の全経路、GUIの実機表示等の残件は継続する。

最新成果物: `outputs/neoforge-native-points-api/realtrainmodunofficial-1.0.19.jar`

SHA-256: `e9ec15d11da126f91cd3024accb674d06285cbdd2631eb0dc7087d9012b6134c`
