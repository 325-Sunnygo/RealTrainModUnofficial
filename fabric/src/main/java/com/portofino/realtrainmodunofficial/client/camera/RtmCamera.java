package com.portofino.realtrainmodunofficial.client.camera;

import net.minecraft.client.Minecraft;

/** Original camera activation and numeric control, shared by HUD/FOV/post processing. */
public final class RtmCamera {
    public static final RtmCamera INSTANCE=new RtmCamera();
    private final CameraState state=new CameraState();
    private boolean active,modeDown,debugDown;
    private RtmCamera() {}
    public CameraState state() { return state; }
    public boolean isActive() { return active; }
    public double computeFov(double base) { return state.fov(base); }
    public void setActive(boolean value) { if(!value && active) CameraPostProcessor.reset();active=value; }
    public void close() { setActive(false);modeDown=debugDown=false; }
    /** Kept for old call sites; actual activation is determined by the original held-item rule. */
    public void toggle() { setActive(!active); }
    public void tick(Minecraft mc) {
        if(!active || mc.screen!=null || mc.isPaused()) { modeDown=debugDown=false;return; }
        state.update(CameraKeyMappings.isDown(CameraKeyMappings.ZOOM_IN),CameraKeyMappings.isDown(CameraKeyMappings.ZOOM_OUT),
                CameraKeyMappings.isDown(CameraKeyMappings.SENSIT_UP),CameraKeyMappings.isDown(CameraKeyMappings.SENSIT_DOWN),
                CameraKeyMappings.isDown(CameraKeyMappings.FOCUS_IN),CameraKeyMappings.isDown(CameraKeyMappings.FOCUS_OUT));
        boolean mode=CameraKeyMappings.isDown(CameraKeyMappings.FOCUS_MODE);
        if(mode && !modeDown) state.cycleFocusMode();modeDown=mode;
        boolean debug=CameraKeyMappings.isDown(CameraKeyMappings.DEBUG);
        if(debug && !debugDown && state.focusMode()>0) CameraPostProcessor.requestDepthScreenshot();debugDown=debug;
    }
}
