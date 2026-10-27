package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.client.camera.CameraState;

public final class LegacyCameraRegression {
    private static int checks;
    private static void check(boolean v,String why) { checks++;if(!v) throw new AssertionError(why); }
    public static void main(String[] args) {
        CameraState s=new CameraState();
        check(s.status().equals("x1.0 S:100% F:NONE"),"original initial HUD");
        s.update(true,false,false,true,true,false);
        check(Math.abs(s.scale()-1.05F)<1e-6 && s.sensitivity()==0.99F,"original zoom and sensitivity increments");
        check(s.focus()==0.5F,"NONE does not adjust focus");
        s.cycleFocusMode();s.autoFocus(64/255F);
        check(s.status().contains("AUTO(0.251)"),"autofocus uses normalized grayscale depth");
        s.cycleFocusMode();float f=s.focus();s.update(false,false,false,false,true,false);
        check(Math.abs(s.focus()-f-0.005F)<1e-6,"manual focus step");
        s.autoFocus(1);check(s.focus()<1,"autofocus cannot move manual focus");
        s.cycleFocusMode();check(s.focusMode()==0,"NONE/AUTO/MANUAL cycle");
        for(int i=0;i<2000;i++) s.update(true,false,false,true,false,false);
        check(s.scale()==30 && s.sensitivity()==0,"upper zoom and lower sensitivity clamps");
        for(int i=0;i<2000;i++) s.update(false,true,true,false,false,false);
        check(s.scale()==1 && s.sensitivity()==1,"lower zoom and upper sensitivity clamps");
        check(Math.abs(s.fov(70)-70)<1e-10,"unzoomed FOV");
        check(CameraState.depthLevel(0.75F)==0 && CameraState.depthLevel(1)==255,"original depth remap boundaries");
        int previous=-1;
        for(int i=0;i<=1024;i++) {
            int v=CameraState.depthLevel(i/1024F);check(v>=previous && v<=255,"monotonic depth grayscale");previous=v;
        }
        for(int radius=0;radius<=24;radius++) {
            float[] w=CameraState.gaussian(radius);double sum=w[0];for(int i=1;i<w.length;i++)sum+=2*w[i];
            check(Double.isFinite(sum) && Math.abs(sum-1)<1e-6,"finite normalized original Gaussian kernel");
        }
        check(CameraState.blurRange(0.5F,0.5F,1920)==0,"in-focus depth slice stays sharp");
        check(CameraState.blurRange(1,0,1920)==24,"maximum original blur radius");
        System.out.println("Legacy camera regressions: "+checks+" passed");
    }
}
