package com.portofino.realtrainmodunofficial.script;
import jp.ngt.rtm.rail.util.*;
import jp.kaiz.kaizpatch.rtm.rail.util.*;
public final class LegacyRailSectionsRegression {
    private static int checks;
    private static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var a=new RailPosition(-32,64,0,(byte)2);a.setPosition(-32,64,0);a.anchorYaw=90;a.anchorLengthHorizontal=24;
        var b=new RailPosition(64,68,0,(byte)6);b.setPosition(64,68,0);b.anchorYaw=-90;b.anchorLengthHorizontal=24;
        a.cantCenter=4;a.cantRandom=0.25F;
        var source=new RailMapBasic(a,b,1);var sections=RailChunkSectioner.split(source);
        check(sections.size()>=5,"long rail splits across positive and negative chunk boundaries");
        double previous=0,length=0;java.util.Set<String> cores=new java.util.HashSet<>();
        for(var section:sections) {
            check(Math.abs(section.startRatio()-previous)<1E-7,"no gaps or overlap in parameter domain");previous=section.endRatio();
            check(cores.add(section.startRP().blockX+":"+section.startRP().blockY+":"+section.startRP().blockZ),"section cores unique");
            var map=new RailMapSection(source,section.startRP(),section.endRP(),section.startRatio(),section.endRatio());length+=map.getLength();
            for(int i=0;i<=10;i++) {double ratio=map.sourceRatio(i/10.0);double[] p=source.getRailPos(ratio),q=map.getRailPos(10,i);
                check(p[0]==q[0] && p[1]==q[1] && source.getRailHeight(ratio)==map.getRailHeight(10,i) && source.getRailRoll(ratio)==map.getRailRoll(10,i),"position height and cant delegate to logical curve");}
        }
        check(previous==1 && Math.abs(length-source.getLength())<1E-8,"full coverage and length conservation");
        check(RailTransitionResolver.findNearestPointAround(source,100,80,-30,0,0)==0 || RailTransitionResolver.findNearestPointAround(source,100,80,-30,0,0)>=0,"nearest result exists");
        var c=new RailPosition(65,68,0,(byte)2);c.setPosition(64,68,0);c.anchorYaw=90;
        var d=new RailPosition(80,68,0,(byte)6);d.setPosition(80,68,0);d.anchorYaw=-90;
        var next=new RailMapBasic(c,d,1);
        check(RailTransitionResolver.findConnectedEntryIndex(source,next,100)==0,"entry endpoint resolves without jumping along rail");
        check(RailTransitionResolver.findNearestPointAround(source,1000,700,-32,0,0)>=610,"bounded nearest search excludes a far crossing point");
        System.out.println("Legacy section/transition regressions: "+checks+" passed");
    }
}
