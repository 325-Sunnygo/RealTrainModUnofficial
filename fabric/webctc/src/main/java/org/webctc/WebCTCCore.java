package org.webctc;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMU-WebCTC_1.21.1 — 本家 WebCTC (org.webctc / Kaiz_JP, masa0300 / AGPL-3.0) の 1.21.1 移植。
 *
 * <p>サーバー起動時に Web サーバー (既定 :8080、{@code -Dwebctc.port=} で変更) を立ち上げ、
 * ブラウザから列車・レール・信号の閲覧と列車操作 (ノッチ等) ができる。
 * ダッシュボード: <a href="http://127.0.0.1:8080/">http://127.0.0.1:8080/</a>
 *
 * <p>API (本家互換の形):
 * <ul>
 *   <li>GET /api/trains — 列車一覧 (位置/速度/ノッチ/ドア/保安装置)</li>
 *   <li>POST /api/trains/&lt;entityId&gt;/notch — ノッチ設定</li>
 *   <li>POST /api/trains/&lt;entityId&gt;/state — 車両状態設定</li>
 *   <li>GET /api/rails, /api/formations, /api/signals</li>
 *   <li>GET/POST /api/waypoints, /api/railgroups, /api/tecons (ワールドに永続化)</li>
 * </ul>
 */
@Mod(WebCTCCore.MODID)
public class WebCTCCore {

    public static final String MODID = "webctc";
    public static final Logger LOGGER = LoggerFactory.getLogger("WebCTC");

    public WebCTCCore() {
        NeoForge.EVENT_BUS.addListener(WebCTCServer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WebCTCServer::onServerStopping);
        //RailGroup (連動装置) + TeCon (運行盤) — 本家 WebCTCEventHandler
        NeoForge.EVENT_BUS.addListener(WebCTCEventHandler::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WebCTCEventHandler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(WebCTCEventHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(WebCTCEventHandler::onBreakBlock);
        //本家 CommandWebCTC / CommandRailGroup
        NeoForge.EVENT_BUS.addListener(WebCTCCommands::onRegisterCommands);
        LOGGER.info("RTMU-WebCTC loaded (original WebCTC by Kaiz_JP, masa0300)");
    }
}
