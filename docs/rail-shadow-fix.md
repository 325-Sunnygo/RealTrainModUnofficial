# レール端部と影材質の修正（NeoForge）

ユーザー提供の2枚の画像と、2026-10-25 12:54台のPrism起動ログを調査した。ゲーム内確認はユーザー自身が行う方針を維持し、こちらではクライアントやワールドを操作していない。

## レール

起動ログで `1067mm_overpass_LR_slab` と `1067mm_Wood` のRailScriptRenderers初期化が `NoSuchMethodException: jp.ngt.rtm.render.RailPartsRenderer.<init>()` で失敗していた。可変長引数のコンストラクタしかないため、引数なし反射生成に失敗し、shouldRenderObjectによる部品の選択が機能しない代替経路へ落ちていた。通常線に分岐用部品まで出る原因となる。

- 本家と同じ引数なしコンストラクタを追加。
- shouldRenderObjectのlenへ分割数ではなく論理サンプル数（split+1、複数マップでは合計）を渡す。posも全体の論理番号に合わせる。
- 本家tessellatePartsと同様、sideから始まる端面グループは全長の端だけに出す。start/end cap設定も反映。
- 分割数は本家のfloat長×2の切り捨てへ統一。minimumSplit/endOffsetの入口を追加し、renderStaticPartsへ反映。
- 端点を0.25mずらす代替経路を無効化し、描画位置への微小Yばらつきも0にした。
- RailPackLoaderが捨てていたtexturesのAlphaBlend/Light等のオプションと追加ライト名を保存する。

提供KaizPatchXソースのRailRenderSampling.kt、RailRenderGeometry.kt、RailPartsRenderer.tessellatePartsを基準とする。任意の端平面で全ポリゴンを切る独自クリッパーを追加したものではなく、本家のサンプル位置と部品選択に合わせる修正。

## 影

E131パックのモデルとparts.pngを読み取り、影の材質mat2にAlphaBlendがあり、該当UV領域に不透明中心から低アルファ端までの勾配が存在することを確認した。画像の中心には実際にalpha=255の暗い画素もあり、これを勝手に薄めない。

- 通常モデルでは元のテクスチャを使用し、全モデルへalpha=255のみの画像マスクを強制する経路を撤去。レール等の通常カットアウトも元画像を保持する。
- 車両のスクリプト記録へ、本家RenderVehicleBaseの通常パスGL_EQUAL/1、半透明パスGL_LESS/1を設定。スクリプトからの明示変更は保持する。
- スクリプトなし車両にも同じ比較を専用シェーダーへ設定する。従来の汎用entityTranslucentにある0.1未満discardで、影の薄い縁が欠ける経路を修正。
- 影グループの削除や形状・名前による推測除外を復活させない。作者の画像・設定に従う。

## 検証

回帰222件成功（既存206件＋レール/alpha16件）。実際のRenderRailStandard.jsをinclude展開し、NashornからrenderClass反射生成とinitを実行、base/railLは保持しL0/R1の分岐用部品は通常線で出さないことを検査した。

GPU945件成功（既存433件＋影の全alpha 256段階×2パス=512件）。不透明・半透明の比較と合成結果をフレームバッファから読み戻し、薄い縁も含めて確認。標準82本の準備・構文失敗0。ただしゲーム外のバインド警告は残る。

NeoForgeコンパイル・JAR生成成功。ランチャーへの自動配置は除外。提供画像とゲーム内での最終的な見た目の一致は未確認。任意の旧スクリプトと全パックの完全互換を達成したという結果ではない。

成果物: `outputs/neoforge-rail-shadow-fix/realtrainmodunofficial-1.0.19.jar`

SHA-256: `23d4f39c5252ba7d33a417c028d163c41bab6d56b31cdad53b6a9950b2e1730d`
