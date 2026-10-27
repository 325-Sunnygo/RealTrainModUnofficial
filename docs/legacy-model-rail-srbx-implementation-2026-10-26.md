# ModelLoader・車両設定・レール分割・SRBXの実装と検証

対象: NeoForge 1.21.1。参照: ../reference/KaizPatchX-master/src/main/java。Fabricは変更していない。

## 依頼された項目と結果

| 項目 | 実装・検証 |
| --- | --- |
| スクリプト用OBJ/MTL | OBJのブロック単位座標、前向き三角形化、共有頂点、V反転、材質ID、60度の平滑化を追加。MTLは本家MtlParserと同じくnewmtlの順序で材質を作る。File、ResourceLocation、InputStream[]、Nashorn可変長引数に対応。 |
| 車両設定全体 | 本家のResourceConfig→ModelConfig→VehicleBaseConfig→TrainConfig/VehicleConfigとIConfigWithTypeを移植。元JSONを保持して同じinitを適用する。列車ノッチと自動車の地上／空中プロファイルを分離し、モデル取得・ダミー判定の型も修正。 |
| ネイティブ精度 | 本家ModelPackManagerの「未指定/MEDIUMはMEDIUM、それ以外はLOW」を使用。車体・部品・スクリプトグラフへ伝達。LOWのMQO/OBJは同じモデルグラフからネイティブ頂点・UV・法線を構築し、別精度のキャッシュを混用しない。 |
| RailTransitionResolver | 範囲を限定した最近点探索、端点方向と越境判定、隣接区間、接続先探索、進入点インデックス、同じ論理レールを保持する処理を移植。 |
| autoSplit | 本家と同じチャンク境界検出・区間コア配置・元曲線への委譲を追加。ベジェ曲線の弧長と退化点での方向計算も参照実装に合わせた。区間NBT、論理レールID、全区間の更新・削除、端面描画制御を実装。 |
| autoSplitの選択UI | 本家と同じ位置に「Disable Auto Split」を追加。アイテムNBTとモデル選択パケットで保持する。旧NBTに項目がなければ有効。 |
| 未検出・破損モデル | 空モデルを返して隠す処理を変更し、ModelFormatExceptionと原因を返す。OBJのエラー行、MQOの頂点・面・UVの不整合を検出。未対応拡張子は本家どおりnull。 |
| ドメイン識別 | ResourceLocationのドメインをモデル・スクリプト・アセット検索とキャッシュに維持する。minecraftの不足アセットを別ドメインの同名ファイルで補う誤解決も除去。 |
| SRBXのワールド操作・同期 | 実際のパックのコードを検証用専用サーバーで実行。欠けていたワールドAPI、区間コアAPI、材質設定取得、保存データ、旧PacketNBTのサーバー配信を追加。 |
| 通常クライアントの最終画面 | 未確認。独立GPU検査と実サーバー検証を、プレイヤーが見る最終画面の確認とは区別する。 |

## SRBXで具体的に修正した不足

- WorldCompat: チャンク読み込み判定、空気判定、タイル削除、チャンク内タイル列挙、ワールドの保存データ。
- RailProperty.getModelSet/getConfig、区間情報のreadSectionData/writeSectionData、Kotlin式のgetStartRatio/getStartRP等の公開API。
- MapGenStructureDataのFeatures NBTを1.21のSavedDataへ接続し、道路床の所有権メタデータを保持。
- NGTCore.NETWORK_WRAPPER.sendToAll(PacketNBT)を追加。サーバー側の実ブロック状態→NBTの順に、同じディメンションのクライアントへ配信。
- CarEntityの生成直後のワールド参照、getModelName/getModelType/getModelSet/setModelName、ResourceStateの設定取得を追加。
- SRBXがMethodHandlesで要求するModelPackManager.getModelSet(String,String):ModelSetBaseの実際の戻り型と、モデル未検出時のisDummyを修正。
- サーバー用プリリュードから任意のクライアント描画・音声クラスの初期化を除去。最後のGameTest実行でinvalid distエラーなし。

## 自動検証

- OBJ/MTL・例外・ドメイン: 13件成功。実アセット索引でドメイン間の混入も検査。
- 精度・可変モデル・Nashorn引数: 36件成功。
- 車両JSON・設定型・モデル取得・autoSplit保存とパケット: 21件成功。
- ネイティブ/スクリプトのLOW頂点・UV・法線: 65件成功。MQOとOBJの両方を含む。
- 区間分割・台車遷移: 96件成功。負座標、区間の連続性、位置・高さ・カントの一致を検査。
- 既存スクリプト・カメラ・描画音声API・レール・インベントリの回帰検査成功。
- 同梱・参照モデル219個、472,904面の読み込み・ベイク成功。
- E131: 13モデル、642,208三角形で、頂点位置・UV・巻き方向・重複数・提出順が一致。
- 実GPU: 描画シェーダー4本、カメラFBO5件、半透明の色・深度40件、サンプラー4種類成功。元PNGとGPUの1,048,576画素のRGBAが一致。
- 同梱スクリプト82本: 構文・準備・prelude整合性の失敗0。ゲーム未起動の独立JVMに由来するレジストリ束縛警告は残る。
- 実SRBX描画スクリプト7種類: load/init/両描画パスのプレビュー成功。今回から元のVehicleConfigを渡して検査。
- 実SRBXサーバースクリプト8種類: 読み込みと初期onUpdate成功。
- NeoForge GameTest: 6件すべて成功。既存の信号破壊3件と、autoSplit有効/無効、実SRBXの操作検査を含む。

### 実ワールドで確認した範囲

専用のbuild/signal-gametestで作られる検証用ワールドを使用。区間付きレールの敷設、NBT保存・復元、標準更新パケットに区間データが含まれること、グループへの信号・モデル更新、全体削除を検査。

実パックのSRBXApiCompatとserver_builder1.jsを読み込み、敷設・Undo・分割・分割Undo・カント編集・レール移動、実CarEntityを渡すprocessRequestとonUpdateを実行した。CarScriptDataSyncPayloadの実StreamCodecで設定データを往復し、受信側の適用結果も一致した。

## 検証の限界

通常クライアントのウィンドウを起動して最終画面を目視する確認、および別プロセスのプレイヤークライアントを接続した通信検証はしていない。全分岐形状・全設置条件・各ツールの全操作組合せを網羅したものでもない。コピー・ホーム・分岐ツールの固有のワールド編集は、8種類の初期更新検査と共通レールAPIの検査だけで全動作を証明したとは扱わない。

したがって「全スクリプト完全互換」「全操作の完全動作」の達成宣言ではない。ユーザーの普段のセーブデータとmodsフォルダは変更していない。

## 成果物

outputs/neoforge-legacy-model-rail-srbx/realtrainmodunofficial-1.0.19.jar

SHA-256: 5a4049575b6660d0869ed64f92da879e51be6e8a622230bd7b51634acf3d7e2c

JARのassets配下に大文字パスなし。削除済み独自編成アイテム実装の再混入なし。自動インストールなし。
