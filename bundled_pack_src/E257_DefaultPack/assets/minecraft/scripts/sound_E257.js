// E257 running sound: RTM 本家同梱の 223系 走行音を使用する。
var DOMAIN = "rtm";
var Tracks = [
    // AC (空調) は常に薄く鳴る。
    {file:"223_air", pitch:[[0,1.00],[120,1.00]], volume:[[0,0.25],[120,0.25]],
     power:true, off:true, brake:true, fade:false, tunnel:false},
    // 起動・加速 (停車直後の力行)
    {file:"223_s0", pitch:[[0,0.90],[8,1.00],[25,1.15]], volume:[[0,0],[1,0.85],[15,0.85],[28,0]],
     power:true, off:false, brake:false, fade:false, tunnel:null},
    {file:"223_s1", pitch:[[8,0.80],[30,1.00],[50,1.20]], volume:[[8,0],[15,1.00],[35,1.00],[50,0]],
     power:true, off:false, brake:false, fade:false, tunnel:null},
    {file:"223_s2", pitch:[[25,0.85],[55,1.00],[75,1.20]], volume:[[25,0],[35,1.00],[60,1.00],[75,0]],
     power:true, off:false, brake:false, fade:false, tunnel:null},
    // 定常走行
    {file:"rtm_223_mid", pitch:[[0,0.70],[30,1.00],[120,1.30]], volume:[[0,0],[10,1.00],[105,1.00],[120,0.85]],
     power:true, off:true, brake:true, fade:true, tunnel:false},
    // 高速域
    {file:"rtm_223mh", pitch:[[60,0.80],[110,1.00],[130,1.20]], volume:[[60,0],[80,1.00],[115,1.00],[130,0]],
     power:true, off:true, brake:false, fade:true, tunnel:false},
    // トンネル内
    {file:"223_run_tunnel", pitch:[[0,0.70],[30,1.00],[120,1.30]], volume:[[0,0],[10,1.00],[110,1.00],[120,0.85]],
     power:true, off:true, brake:true, fade:true, tunnel:true}
];

var SoundData = {
    "223_air":["rtm","train.223_air"],
    "223_s0":["rtm","train.223_s0"],
    "223_s1":["rtm","train.223_s1"],
    "223_s2":["rtm","train.223_s2"],
    "rtm_223_mid":["rtm","train.rtm_223_mid"],
    "rtm_223mh":["rtm","train.rtm_223mh"],
    "223_run_tunnel":["rtm","train.223_run_tunnel"]
};

function onUpdate(su) {
    var entity = su.getEntity();
    if (entity == null) return;
    var dataMap = entity.getResourceState().getDataMap();
    var speed = su.getSpeed();
    var notch = su.getNotch();

    var fade = dataMap.getInt("e257PowerFade");
    if (notch > 0) fade = Math.min(fade + 1, 8);
    else if (notch < 0) fade = Math.max(fade - 1, -8);
    else fade += fade > 0 ? -1 : fade < 0 ? 1 : 0;
    dataMap.setInt("e257PowerFade", fade, 0);

    var tunnel = dataMap.getInt("e257TunnelFade");
    tunnel = su.inTunnel() ? Math.min(tunnel + 1, 10) : Math.max(tunnel - 1, 0);
    dataMap.setInt("e257TunnelFade", tunnel, 0);

    for (var i = 0; i < Tracks.length; i++) {
        new SoundState(su, Tracks[i]).play(speed, notch,
            fade / 8, tunnel / 10);
    }
}

function SoundState(su, track) {
    this.su = su;
    this.track = track;
    this.info = SoundData[track.file];
}
SoundState.prototype.play = function (speed, notch, fade, tunnel) {
    if (this.info == null) return;
    var volume = interpolate(this.track.volume, speed) * this.mix(notch, fade, tunnel);
    var pitch = interpolate(this.track.pitch, speed);
    if (volume > 0.001) {
        this.su.playSound(this.info[0], this.info[1], volume, pitch, true);
    } else {
        this.su.stopSound(this.info[0], this.info[1]);
    }
};
SoundState.prototype.mix = function (notch, fade, tunnel) {
    var t = this.track;
    var v;
    if (t.fade) {
        var off = 1 - Math.abs(fade);
        v = Math.max(t.power && fade > 0 ? 1 : 0,
                     t.off && off > 0 ? 1 : 0,
                     t.brake && fade < 0 ? 1 : 0);
        v *= t.off ? off : Math.abs(fade);
    } else {
        v = Math.max(t.power && notch > 0 ? 1 : 0,
                     t.off && notch === 0 ? 1 : 0,
                     t.brake && notch < 0 ? 1 : 0);
    }
    if (t.tunnel === true) v *= tunnel;
    else if (t.tunnel === false) v *= 1 - tunnel;
    return v;
};

function interpolate(points, speed) {
    if (points == null || points.length === 0) return 0;
    if (speed <= points[0][0]) return points[0][1];
    for (var i = 0; i < points.length - 1; i++) {
        var a = points[i], b = points[i + 1];
        if (speed <= b[0]) {
            var r = (speed - a[0]) / (b[0] - a[0]);
            return a[1] + (b[1] - a[1]) * r;
        }
    }
    return points[points.length - 1][1];
}
