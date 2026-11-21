// RTMU パックスクリプト用の型定義。
// 使い方: 描画スクリプトと同じフォルダに置き、tsconfig.json の "include" に入れる。
//   { "compilerOptions": { "target": "ES2015", "lib": ["ES2015"], "noEmit": true }, "include": ["**/*.ts"] }
// ★noEmit で構わない。ゲーム内では RTMU が .ts をそのまま読んで型を落とす。
//
// このファイルは RTMU のソースから自動生成している。手で直さないこと。

declare function importPackage(pkg: any): void;
declare const Packages: any;

/** モデルのグループ (MQO のオブジェクト) をまとめて描くための単位。 */
declare class Parts {
  constructor(...groupNames: string[]);
  render(renderer: PartsRenderer): void;
  getNames(): string[];
  getObjNames(): string[];
  containsName(objName: string): boolean;
}
declare class ActionParts extends Parts {}

/** 描画スクリプトに束縛される描画器。renderClass で実体が決まる。 */
declare interface PartsRenderer {
  registerParts<T extends Parts>(parts: T): T;
  bindTexture(texture: any): void;
  debug(msg: string): void;
  disableLighting(): void;
  enableLighting(): void;
  getBrightness(world: any, x: number, y: number, z: number): number;
  getColor(entity: any): number;
  getData(id: number): any;
  getMCHour(): number;
  getMCMinute(): number;
  getMCTime(): number;
  getMetadata(tile: any): number;
  getModelName(): string;
  getModelObj(): any;
  getModelObject(): any;
  getSystemHour(): number;
  getSystemMillisecond(): number;
  getSystemMinute(): number;
  getSystemSecond(): number;
  getSystemTime(): number;
  getSystemTimeMillis(): number;
  getViewerVec(x: number, y: number, z: number): any;
  getWorld(tile: any): any;
  getX(tile: any): number;
  getY(tile: any): number;
  getZ(tile: any): number;
  postRender(t: any, smoothing: boolean, culling: boolean, par3: number): void;
  preRender(t: any, smoothing: boolean, culling: boolean, par3: number): void;
  renderLightEffect(normal: any, pos: number[], rL: number, rS: number, length: number, color: number, type: number, reverse: boolean): void;
  renderLightEffectS(normal: any, x: number, y: number, z: number, rL: number, rS: number, length: number, color: number, type: number, reverse: boolean): void;
  renderStaticParts(tile: any, x: number, y: number, z: number): void;
  rotate(angle: number, axis: string, x: number, y: number, z: number): void;
  rotateAndRender(parts: any, x: number, y: number, z: number, rotationX: number, rotationY: number, rotationZ: number): void;
  setBrightness(packedLight: number): void;
  setData(id: number, value: any): void;
  sigmoid(par1: number): number;
  sigmoid(x: number, c: number): number;
  spawnParticle(entity: any, name: string, posX: number, posY: number, posZ: number, speedX: number, speedY: number, speedZ: number): void;
  validPath(path: string): boolean;
}

/** 車両 (renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer")。 */
declare interface VehiclePartsRenderer extends PartsRenderer {
  getDoorMovementL(entity: any): number;
  getDoorMovementR(entity: any): number;
  getPantographMovementBack(entity: any): number;
  getPantographMovementFront(entity: any): number;
  getWheelRotationL(entity: any): number;
  getWheelRotationR(entity: any): number;
}

/** 機器・踏切 (renderClass = "jp.ngt.rtm.render.MachinePartsRenderer")。 */
declare interface MachinePartsRenderer extends PartsRenderer {
  getLightPos(tile: any, x: number, y: number, z: number, pitch: number, yaw: number): number[];
  getLightState(tile: any): number;
  getLodState(tile: any): number;
  getMovingCount(tile: any): number;
  getNormal(tile: any, x: number, y: number, z: number, pitch: number, yaw: number): any;
  getPitch(tile: any): number;
  getTick(tile: any): number;
  getYaw(tile: any): number;
}

/** 信号機 (renderClass = "jp.ngt.rtm.render.SignalPartsRenderer")。 */
declare interface SignalPartsRenderer extends PartsRenderer {
  getBlockDirection(tile: any): number;
  getRotation(tile: any): number;
  getSignal(tile: any): number;
  isOpaqueCube(tile: any): boolean;
}

/** スクリプトに渡される描画器。renderClass に合わせて型を絞って使う。 */
declare const renderer: PartsRenderer & VehiclePartsRenderer & MachinePartsRenderer & SignalPartsRenderer;

/** 1.7.10 の LWJGL 互換シム。行列とブレンドだけが記録される。 */
declare const GL11: {
  glPushMatrix(): void;
  glPopMatrix(): void;
  glTranslatef(x: number, y: number, z: number): void;
  glRotatef(angle: number, x: number, y: number, z: number): void;
  glScalef(x: number, y: number, z: number): void;
  glColor4f(r: number, g: number, b: number, a: number): void;
  glColor3f(r: number, g: number, b: number): void;
  glEnable(cap: number): void;
  glDisable(cap: number): void;
  glBlendFunc(src: number, dst: number): void;
  glDepthMask(flag: boolean): void;
  [key: string]: any;
};
declare const GL12: typeof GL11;

/** 車両エンティティ。スクリプトが直接触るぶんだけ。 */
declare interface VehicleEntity {
  doorMoveL: number;
  doorMoveR: number;
  seatRotation: number;
  wheelRotationR: number;
  wheelRotationL: number;
  getTrainStateData(id: number): number;
  getVehicleState(stateTypeId: number): number;
  getTrainDirection(): number;
  getSpeed(): number;
  [key: string]: any;
}

/** 設置物のブロックエンティティ。 */
declare interface InstalledObject {
  getSignal(): number;
  getRotation(): number;
  getBlockDirection(): number;
  [key: string]: any;
}

// ---- スクリプトが定義する側 ----
// var renderClass = "jp.ngt.rtm.render.VehiclePartsRenderer";
// function init(modelSet, modelObject) { ... }
// function render(entity, pass, partialTick) { ... }
