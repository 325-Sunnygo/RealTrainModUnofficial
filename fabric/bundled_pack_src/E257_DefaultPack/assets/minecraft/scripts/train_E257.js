//E257系0番台 描画スクリプト (デフォルトパック)
//1つの E257.mqo に全号車のオブジェクトが入っており、号車ごとに表示グループを選択する。
//グループ規則は同梱の編成表/メモに従う (末尾[obj]=グループ空オブジェクト、中身は グループ名_***)。
var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";

//GL11: 本家(1.12.2)は LWJGL を import して使う。RTMU(1.21) はスクリプト環境が先に
//GL11 = GL11Facade を束縛しており、importPackage は未定義名しか束縛しないので上書きされない。
//=> この 1 行で両対応になる。
importPackage(Packages.org.lwjgl.opengl);

var PartsClass = Java.type("jp.ngt.rtm.render.Parts");

//発光まわり (室内灯のフルブライト) は本家に renderer.setBrightness/enableLighting が無いので、
//両方に存在する GLHelper へ逃がす。前照灯/尾灯は MOD 側の発光パスが描くので
//スクリプトからテクスチャを貼る必要はない (貼ると自動発光が無効化される)。
var GLHelperClass = null;
try { GLHelperClass = Java.type("jp.ngt.ngtlib.renderer.GLHelper"); } catch (e3) {}

/** 自己発光 (フルブライト) にする */
function setFullBright() {
    try { renderer.setBrightness(FULLBRIGHT); return; } catch (err) {}
    try { if (GLHelperClass != null) GLHelperClass.setLightmapMaxBrightness(); } catch (err2) {}
}

/** 環境光へ戻す (RTMU の GLHelper.enableLighting は no-op なので renderer 側を優先) */
function restoreLight() {
    try { renderer.enableLighting(); return; } catch (err) {}
    try { if (GLHelperClass != null) GLHelperClass.enableLighting(); } catch (err2) {}
}

//号車ごとの構成定義 (1/3/4/6/11号車のみ)
//prefixes: この前置詞で始まるオブジェクトを表示
//exacts:   完全一致で表示
//excludes: prefixes に一致しても除外 (0番台でない座席バリエーション等)
var CARS = {
    "E257_01": {
        prefixes: ["ex0_", "roll_ex0_", "ex1a_", "ex1cp_", "ex1d1_", "ex1p_", "ex1d2_", "ex0end2_",
                   "in1cp0_", "in0cp0_", "in1cpall_", "in1d1_", "in1p_", "in1d2_"],
        exacts: ["under_panel", "under_01", "mark_01", "mark_cp"],
        excludes: ["ex0end2_step11", "2000"],
        doorsL: ["doorL01a", "doorL01b"], doorsR: ["doorR01a", "doorR01b"], doorDir: -1.0,
        mascon: "plain", panta: false,
        lightsHead: ["ex0_light", "ex0_light_top"], lightsTail: ["ex0_light_rear"],
        seatZ: 5.4, seatRows: 13,
        offsetZ: -0.25
    },
    "E257_03": {
        prefixes: ["ex100_", "ex100b_", "roll_ex100_", "ex1a_", "ex1cp_", "ex1d1_", "ex1p_", "ex1d2_", "ex0end2_",
                   "in1cp100_", "in0cp100_", "in1cpall_", "in1d1_", "in1p_", "in1d2_"],
        exacts: ["sub_seat100", "seat7", "seat8", "seat9", "seat_base100", "box_all100",
                 "under_panel", "under_03", "mark_03", "mark_cp"],
        excludes: ["ex0end2_step11", "in1cp100_door_No_joint"],
        doorsL: ["doorL01a", "doorL01b"], doorsR: ["doorR01a", "doorR01b"], doorDir: -1.0,
        mascon: "b", panta: false,
        lightsHead: ["ex100_front_light"], lightsTail: ["ex100_rear_light"],
        seatZ: 5.4, seatRows: 13,
        offsetZ: -0.25
    },
    "E257_04": {
        prefixes: ["ex4_", "ex0end1_", "ex0end2_", "in4p_", "in4d_", "panta_"],
        exacts: ["under_panel", "under_04", "mark_04"],
        excludes: ["ex0end2_step11", "panta_C"],
        doorsL: ["doorL04"], doorsR: ["doorR04"], doorDir: 1.0,
        mascon: "none", panta: true,
        seatZ: 9.0, seatRows: 18,
        offsetZ: 0.0
    },
    "E257_06": {
        prefixes: ["ex4_", "ex0end1_", "ex0end2_", "in4p_", "in4d_", "panta_"],
        exacts: ["under_panel", "under_06", "mark_06"],
        excludes: ["ex0end2_step11", "panta_C"],
        doorsL: ["doorL04"], doorsR: ["doorR04"], doorDir: 1.0,
        mascon: "none", panta: true,
        seatZ: 9.0, seatRows: 18,
        offsetZ: 0.0
    },
    "E257_11": {
        prefixes: ["ex0_", "roll_ex0_", "ex1a_", "ex1cp_", "ex1d1_", "ex11p_", "ex0end2_",
                   "in1cp0_", "in0cp0_", "in1cpall_", "in1d1_", "in11p_"],
        exacts: ["under_panel", "under_11", "mark_11", "mark_cp"],
        excludes: ["ex0end2_step0", "2000"],
        doorsL: ["doorL01a"], doorsR: ["doorR01a"], doorDir: -1.0,
        mascon: "plain", panta: false,
        lightsHead: ["ex0_light", "ex0_light_top"], lightsTail: ["ex0_light_rear"],
        seatZ: 5.4, seatRows: 16,
        offsetZ: -0.25
    }
};

//パンタグラフ可動部 (memo.txt の pantograph_front 記述、本家 VehicleParts と同形式)
//pos=回転中心、rot=全上昇時の X 軸回転角。モデルは下降状態で格納されている。
var PANTA = {
    objects: ["panta_C1"], pos: [0.0, 2.85, 6.76], rot: -32.42,
    children: [
        {
            objects: ["panta_C2"], pos: [0.0, 3.49, 5.91], rot: 54.25,
            children: [
                { objects: ["panta_C3_Nm", "panta_C3_30", "panta_C3_65"], pos: [0.0, 4.03, 7.075], rot: -21.83, children: [] },
                { objects: ["panta_C5"], pos: [0.0, 3.35, 5.79], rot: -61.18, children: [] }
            ]
        },
        { objects: ["panta_C4"], pos: [0.0, 3.43, 5.945], rot: 56.14, children: [] }
    ]
};

var SEAT_PITCH = 0.96;
var DOOR_MOVE = 0.89;
//転換座席の回転中心 (memo: x±0.8)。init でモデル実座標から確定する。
var seatPivotL = -0.8;
var seatPivotR = 0.8;

var FULLBRIGHT = 15728880; //0xF000F0

var car = null;
var bodyParts = null;
var interiorParts = null; //内装 (室内灯ONでフルブライト)
var doorLParts = null;
var doorRParts = null;
var lampLParts = null;   //ドアランプ (開扉中のみ点灯表示)
var lampRParts = null;
var rollSideParts = null;   //側面方向幕 (単一オブジェクト、テクスチャ交互)
var rollFront1Parts = null; //前面方向幕 表示1 (front1/front2 は交互にどちらか一方のみ表示)
var rollFront2Parts = null;
var seatBaseL = null;    //固定台座 (memo: p_seat_base のみ固定)
var seatBaseR = null;
var seatTopL = null;     //回転部
var seatTopR = null;
//マスコン: レバーサー/ノッチ別の単一オブジェクト Parts
var leverParts = {};
var notchParts = {};
//パンタ可動 Parts (PANTA 構造に対応)
var pantaParts = null;

function clamp(v, min, max) {
    if (v < min) return min;
    if (v > max) return max;
    return v;
}

function toParts(list) {
    return renderer.registerParts(new PartsClass(Java.to(list, "java.lang.String[]")));
}

function buildPantaParts(node) {
    return {
        parts: toParts(node.objects),
        pos: node.pos,
        rot: node.rot,
        children: node.children.map(buildPantaParts)
    };
}

//名前でグループを探す。
//本家の PolygonModel には getGroupObject(String) が無く groupObjects (List) しか無いので、
//両対応のためリストから線形に探す。
function findGroup(model, name) {
    var groups = model.groupObjects;
    if (groups == null) return null;
    for (var i = 0; i < groups.size(); i++) {
        var g = groups.get(i);
        if (g != null && g.name == name) return g;
    }
    return null;
}

//グループの頂点 X 中心 (座席の回転ピボット確定用)
function groupCenterX(model, name) {
    var g = findGroup(model, name);
    if (g == null || g.faces.size() == 0) return null;
    var min = 1.0e9;
    var max = -1.0e9;
    for (var i = 0; i < g.faces.size(); i++) {
        var verts = g.faces.get(i).vertices;
        for (var v = 0; v < verts.length; v++) {
            var x = verts[v].x;
            if (x < min) min = x;
            if (x > max) max = x;
        }
    }
    return (min + max) * 0.5;
}

function init(modelSet, modelObj) {
    var name = renderer.getModelName();
    car = CARS[name];
    if (car == null) {
        //trainName が一致しないとき: 1号車設定で描画 (何も出ないよりまし)
        car = CARS["E257_01"];
    }

    //MQO の全オブジェクト名から号車の表示対象を選ぶ
    var body = [];
    var interior = [];
    var lampL = [];
    var lampR = [];
    var rollsSide = [];
    var rollsFront1 = [];
    var rollsFront2 = [];
    var groups = modelObj.model.groupObjects;
    for (var i = 0; i < groups.size(); i++) {
        var objName = groups.get(i).name;
        if (objName == null) continue;
        var lower = objName.toLowerCase();
        //空グループ ([obj]) と可動パーツは除外
        if (lower.indexOf("[obj]") >= 0) continue;
        if (lower.indexOf("door") == 0 || lower.indexOf("p_seat_") == 0) continue;
        if (lower.indexOf("lever_") == 0 || lower.indexOf("notch_") == 0 || lower.indexOf("brake_") == 0) continue;
        //※前照灯/尾灯のレンズは本体に含めたままにする。
        //  消灯時は基本テクスチャの「消えたレンズ」として描かれ、点灯時は MOD 側の発光パスが
        //  同じ面に light1/light2 を重ねる。本体から外すと消灯時にレンズが穴になる。

        var excluded = false;
        for (var e = 0; e < car.excludes.length; e++) {
            if (objName.indexOf(car.excludes[e]) >= 0) { excluded = true; break; }
        }
        if (excluded) continue;

        var hit = false;
        for (var p = 0; p < car.prefixes.length; p++) {
            if (objName.indexOf(car.prefixes[p]) == 0) { hit = true; break; }
        }
        if (!hit) {
            for (var x = 0; x < car.exacts.length; x++) {
                if (objName == car.exacts[x]) { hit = true; break; }
            }
        }
        if (!hit) continue;

        //ドアランプ (***_doorlamp_Lon / Ron) は開扉中のみ表示
        if (lower.indexOf("doorlamp_lon") >= 0) { lampL.push(objName); continue; }
        if (lower.indexOf("doorlamp_ron") >= 0) { lampR.push(objName); continue; }
        //前面方向幕: front1/front2 は同位置の交互表示ペアなので本体から外して片方ずつ描く
        if (lower.indexOf("roll_") == 0) {
            if (lower.indexOf("front1") >= 0) { rollsFront1.push(objName); continue; }
            if (lower.indexOf("front2") >= 0) { rollsFront2.push(objName); continue; }
        }
        //側面方向幕は単一オブジェクト: 本体にも描き、発光テクスチャを交互に重ねる
        if (lower.indexOf("_side_roll") >= 0) rollsSide.push(objName);
        //内装 (in***_ プレフィックス + 運転席の座席/ボックス類) は室内灯で発光させる
        if (lower.indexOf("in") == 0 || lower.indexOf("seat") == 0
                || lower.indexOf("sub_seat") == 0 || lower.indexOf("box_") == 0) {
            interior.push(objName);
        } else {
            body.push(objName);
        }
    }
    bodyParts = toParts(body);
    interiorParts = toParts(interior);
    lampLParts = lampL.length > 0 ? toParts(lampL) : null;
    lampRParts = lampR.length > 0 ? toParts(lampR) : null;
    rollSideParts = rollsSide.length > 0 ? toParts(rollsSide) : null;
    rollFront1Parts = rollsFront1.length > 0 ? toParts(rollsFront1) : null;
    rollFront2Parts = rollsFront2.length > 0 ? toParts(rollsFront2) : null;

    doorLParts = toParts(car.doorsL);
    doorRParts = toParts(car.doorsR);
    seatBaseL = toParts(["p_seat_base_L"]);
    seatBaseR = toParts(["p_seat_base_R"]);
    seatTopL = toParts(["p_seat_a_L", "p_seat_arm_L", "p_seat_frame10_L", "p_seat_b_L"]);
    seatTopR = toParts(["p_seat_a_R", "p_seat_arm_R", "p_seat_frame10_R", "p_seat_b_R"]);
    //転換ピボットは実モデルの座席中心 X から確定 (memo: ±0.8 のはずだが左右の符号を推測しない)
    var pl = groupCenterX(modelObj.model, "p_seat_base_L");
    var pr = groupCenterX(modelObj.model, "p_seat_base_R");
    if (pl != null) seatPivotL = pl;
    if (pr != null) seatPivotR = pr;

    //マスコン (運転台のある号車のみ)
    if (car.mascon != "none") {
        var suffix = (car.mascon == "b") ? "100" : "";
        var nsuffix = (car.mascon == "b") ? "b" : "";
        leverParts["f"] = toParts(["lever_f" + suffix]);
        leverParts["n"] = toParts(["lever_n" + suffix]);
        leverParts["b"] = toParts(["lever_b" + suffix]);
        for (var nt = 0; nt <= 5; nt++) {
            notchParts["n" + nt] = toParts(["notch_" + nt + nsuffix]);
        }
        for (var bk = 0; bk <= 8; bk++) {
            notchParts["b" + bk] = toParts(["brake_" + bk + nsuffix]);
        }
    }

    if (car.panta) {
        pantaParts = buildPantaParts(PANTA);
    }
}

//本家 BasicVehiclePartsRenderer.renderParts と同じ入れ子変換
function renderPantaNode(node, move) {
    GL11.glPushMatrix();
    GL11.glTranslatef(node.pos[0], node.pos[1], node.pos[2]);
    GL11.glRotatef(node.rot * move, 1.0, 0.0, 0.0);
    GL11.glTranslatef(-node.pos[0], -node.pos[1], -node.pos[2]);
    node.parts.render(renderer);
    for (var i = 0; i < node.children.length; i++) {
        renderPantaNode(node.children[i], move);
    }
    GL11.glPopMatrix();
}

function render(entity, pass, partialTicks) {
    if (car == null || bodyParts == null) return;

    GL11.glPushMatrix();
    if (car.offsetZ != 0.0) {
        GL11.glTranslatef(0.0, 0.0, car.offsetZ);
    }

    //車体 (固定部)
    bodyParts.render(renderer);

    //前面方向幕の交互表示 (約3秒周期、front1/front2 を片方だけ表示)。
    //mat3 のテクスチャは _light 版も同一画像 (交互はオブジェクト切替のみの設計) なので
    //発光テクスチャの重ね描きはしない — 側面幕はそのまま本体描画 (rollSideParts は本体に含む)。
    var phase = Math.floor(new Date().getTime() / 3000) % 2;
    var frontRoll = (phase == 0) ? rollFront1Parts : rollFront2Parts;
    if (frontRoll != null) {
        frontRoll.render(renderer);
    }

    //ドア (memo: 1/3/11号車は-Z方向、4/6号車は+Z方向、移動量0.89。本家同様 sigmoid 補間)
    var dL = renderer.sigmoid(renderer.getDoorMovementL(entity));
    var dR = renderer.sigmoid(renderer.getDoorMovementR(entity));
    if (dL > 0.0) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0, 0.0, car.doorDir * DOOR_MOVE * dL);
        doorLParts.render(renderer);
        GL11.glPopMatrix();
    } else {
        doorLParts.render(renderer);
    }
    if (dR > 0.0) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0, 0.0, car.doorDir * DOOR_MOVE * dR);
        doorRParts.render(renderer);
        GL11.glPopMatrix();
    } else {
        doorRParts.render(renderer);
    }
    //ドアランプ点灯 (開扉中)
    if (dL > 0.0 && lampLParts != null) lampLParts.render(renderer);
    if (dR > 0.0 && lampRParts != null) lampRParts.render(renderer);

    //レバーサー (回転方向): 座席の向きにも使う
    //
    //getTrainStateData(10) は「1 - レバーサー値」なので:
    //   前進 (レバーサー +1) -> 0
    //   中立 (レバーサー  0) -> 1
    //   後進 (レバーサー -1) -> 2
    //以前は 0=中立 / 1=前進 と取り違えていて、前進と中立が入れ替わっていた
    //(後進=2 だけ偶然合っていた)。
    var DIR_FORWARD = 0;
    var DIR_NEUTRAL = 1;
    var DIR_BACKWARD = 2;

    var dir = DIR_FORWARD;
    var notch = 0;
    var interiorLit = false;
    if (entity != null) {
        try {
            dir = entity.getTrainStateData(10);
            notch = entity.getNotch();
            interiorLit = entity.getTrainStateData(11) != 0;
        } catch (err) {
        }
    }

    //内装: 室内灯ON中はフルブライト (車内だけ光る特殊発光)
    if (interiorLit) setFullBright();
    interiorParts.render(renderer);

    //客席 (memo: 基本座席を z=0 に格納、シートピッチ 0.96 で号車ごとに並べる。
    //台座 p_seat_base のみ固定、他は回転中心 x±0.8 で転換)
    //
    //転換クロスシートは本家 (小田急30000形 render_seat) と同じ方式で回す:
    //  ・entity.seatRotation は -45 〜 0 〜 45 の連続値 (public フィールド) で、進行方向が
    //    変わると毎 tick 1 ずつ動く。45 で割って -1〜1 にし、0〜1 に直して 180° に写す。
    //    ※ getSeatRotation() のような同名 getter を MOD 側に足してはいけない。Nashorn は
    //      フィールドより getter を優先するので、entity.seatRotation の意味が変わってしまう。
    //  ・奇数列と偶数列で位相をずらし (係数 1.739 / 遅延 0.425)、全席が同時に回らず
    //    波打つように転換する。実車の転換動作に近く、隣の座席と干渉しにくい。
    //  ・左右は回転方向が逆 (L は +θ、R は -θ)。
    //  ・回転は「±2θ を 0.05 ずらした 2 つの軸で相殺してから θ」で掛ける。回転量は
    //    差し引き θ のままだが、軸のずれのぶん座席が横に少し滑る。本家がそうしている
    //    ので、そのまま踏襲する (転換中に背もたれが隣とめり込まない)。
    var seatState = 0.0;
    if (entity != null) {
        seatState = entity.seatRotation / 45.0;
    }
    seatState = (seatState + 1.0) / 2.0;  // -1〜1 → 0〜1

    var seatRotate1 = clamp(seatState * 1.739, 0.0, 1.0) * 180.0;
    var seatRotate2 = clamp((seatState - 0.425) * 1.739, 0.0, 1.0) * 180.0;

    for (var row = 0; row < car.seatRows; row++) {
        var z = car.seatZ - SEAT_PITCH * row;
        var a = (row % 2 == 0) ? seatRotate1 : seatRotate2;

        //台座は回らない
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0, 0.0, z);
        seatBaseL.render(renderer);
        seatBaseR.render(renderer);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        renderer.rotate(a * -2.0, 'Y', seatPivotL - 0.05, 0.0, z);
        renderer.rotate(a * 2.0, 'Y', seatPivotL, 0.0, z);
        renderer.rotate(a, 'Y', seatPivotL, 0.0, z);
        GL11.glTranslatef(0.0, 0.0, z);
        seatTopL.render(renderer);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        renderer.rotate(a * 2.0, 'Y', seatPivotR + 0.05, 0.0, z);
        renderer.rotate(a * -2.0, 'Y', seatPivotR, 0.0, z);
        renderer.rotate(-a, 'Y', seatPivotR, 0.0, z);
        GL11.glTranslatef(0.0, 0.0, z);
        seatTopR.render(renderer);
        GL11.glPopMatrix();
    }

    //マスコン (レバーサー: getTrainStateData(10)、ノッチ: getNotch())
    if (car.mascon != "none" && entity != null) {
        var lever = (dir == DIR_BACKWARD) ? leverParts["b"]
                : ((dir == DIR_NEUTRAL) ? leverParts["n"] : leverParts["f"]);
        if (lever != null) lever.render(renderer);
        var notchKey = (notch >= 0) ? ("n" + Math.min(notch, 5)) : ("b" + Math.min(-notch - 1, 8));
        var notchP = notchParts[notchKey];
        if (notchP != null) notchP.render(renderer);
    }

    //内装フルブライト終了 (以降は環境光に戻す)
    //
    //setBrightness(-1) は「復帰」ではなく packedLight = -1 (0xFFFFFFFF = 全部最大) を
    //セットしてしまうので、室内灯ON中はこれ以降 (マスコン/前照灯/パンタ) も
    //フルブライトのままになっていた。環境光へ戻すのは enableLighting()。
    if (interiorLit) restoreLight();

    //前照灯/尾灯は<b>スクリプトでは描かない</b>。
    //
    //JSON のマテリアル定義が
    //    ["mat2", "textures/mat2_0.png", "Light", ***_light0, ***_light1, ***_light2]
    //のように "Light" 型なので、MOD 側が発光パスで自動的に重ねる:
    //    pass 2 -> light0 (室内灯) / pass 3 -> light1 (前照灯) / pass 4 -> light2 (尾灯)
    //light1/light2 は 99% が透明のオーバーレイで、ランプの部分だけが光っている。
    //
    //ここで renderer.bindTexture() を使って手動で貼ると、MOD 側の
    //「スクリプトがテクスチャを貼っていない場合のみ発光テクスチャを使う」条件に引っかかって
    //自動発光が無効化され、しかもテクスチャ解決に失敗すると真っ白なフォールバックが
    //貼られてしまう (= 白い塊)。前照灯/尾灯の点灯とヘッド/テールの出し分けは MOD 側
    //(TrainEntity.getLightMode / getTrainDirection) が判定する。

    //パンタグラフ可動 (4/6号車、本家 childParts 方式の入れ子回転)
    if (pantaParts != null) {
        var move = renderer.sigmoid(renderer.getPantographMovementFront(entity));
        renderPantaNode(pantaParts, move);
    }

    GL11.glPopMatrix();
}
