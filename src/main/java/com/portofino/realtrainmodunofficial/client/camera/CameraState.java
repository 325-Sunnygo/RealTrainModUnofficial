package com.portofino.realtrainmodunofficial.client.camera;

import java.util.Locale;

/** Original Camera/CameraKeySet units and steps; no photographic-unit substitutions. */
public final class CameraState {
    private float scale=1, sensitivity=1, focus=0.5F;
    private int focusMode;
    public float scale() { return scale; }
    public float sensitivity() { return sensitivity; }
    public float focus() { return focus; }
    public int focusMode() { return focusMode; }
    public void autoFocus(float value) { if(focusMode==1) focus=value; }
    public void cycleFocusMode() { focusMode=(focusMode+1)%3; }
    private static float step(float value,boolean up,boolean down,float increment,float min,float max) {
        if(up) value=Math.min(max,value+increment);
        if(down) value=Math.max(min,value-increment);
        return value;
    }
    public void update(boolean zoomIn,boolean zoomOut,boolean sensitivityUp,boolean sensitivityDown,boolean focusIn,boolean focusOut) {
        scale=step(scale,zoomIn,zoomOut,0.05F,1,30);
        sensitivity=step(sensitivity,sensitivityUp,sensitivityDown,0.01F,0,1);
        if(focusMode==2) focus=step(focus,focusIn,focusOut,0.005F,0,1);
    }
    public double fov(double base) { return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(base*0.5))/scale)*2); }
    public float delayBlend() { return (int)(255*(1-sensitivity))/255F; }
    public String status() {
        String text=focusMode==0?"NONE":focusMode==1?String.format(Locale.ROOT,"AUTO(%.3f)",focus):String.format(Locale.ROOT,"%.3f",focus);
        return String.format(Locale.ROOT,"x%.1f S:%d%% F:%s",scale,(int)(sensitivity*100),text);
    }
    /** Original Camera.fixDepth, followed by the 8-bit grayscale conversion. */
    public static int depthLevel(float depth) {
        if(depth<0.75F) return 0;
        double d=(depth-0.75F)/0.25F;
        if(d>=1) return 255;
        d*=d;d*=d;d*=d;
        return (int)((-Math.sqrt(1-d)+1)*255);
    }
    public static int blurRange(float threshold,float focus,int displayWidth) {
        double max=Math.min(24,24D*displayWidth/1500D),x=Math.abs(threshold-focus)*2;
        double sigmoid=x<=0?0:x>=1?1:((x-0.5)*7/Math.sqrt(1+Math.pow((x-0.5)*7,2))+1)*0.5;
        return (int)Math.floor(sigmoid*max);
    }
    public static float[] gaussian(int range) {
        float[] weights=new float[24];
        if(range<=0) { weights[0]=1;return weights; }
        range=Math.min(range,24);double total=0,variance=20D*range*range/576D;int end=0;
        for(int i=0;i<24;i++) {
            double r=1+2D*i,w=Math.exp(-0.5*r*r/variance);
            if(w<1D/255) { end=i-1;break; }
            weights[i]=(float)w;total+=i>0?w*2:w;
        }
        if(total==0) { weights[0]=1;return weights; }
        for(int i=0;i<24;i++) weights[i]=i>end?0:(float)(weights[i]/total);
        return weights;
    }
}
