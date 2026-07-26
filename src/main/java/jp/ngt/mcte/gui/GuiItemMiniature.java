package jp.ngt.mcte.gui;

/**
 * 本家 MCTE のミニチュア GUI。RTMU には GUI 実体が無いが、パック側が
 * <b>型の存在だけ</b>を必要とするため受け皿を置く。
 *
 * <pre>
 * // SR1-200-test.zip!.../__targets__/kaizpatch/scripts/hi03_lib/lib_RTMApiCompatClient.compat.js:31
 * RTMApiCompatClient.isMiniatureGui = function (screen) {
 *     return screen instanceof Packages.jp.ngt.mcte.gui.GuiItemMiniature;
 * };
 * </pre>
 *
 * <p>この型が無いと Nashorn は例外を投げず<b>無音で JavaPackage を返し</b>、
 * {@code instanceof} が常に false になるだけで原因が掴めない。
 * RTMU では該当 GUI を開けないので false 相当で正しく、型が在ることが重要。
 */
public class GuiItemMiniature {
}
