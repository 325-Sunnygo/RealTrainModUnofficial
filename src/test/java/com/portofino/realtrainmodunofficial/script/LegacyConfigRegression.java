package com.portofino.realtrainmodunofficial.script;

import jp.ngt.rtm.RTMConfig;
import jp.ngt.rtm.RTMConfigOptions;
import java.nio.file.Files;

public final class LegacyConfigRegression {
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var folder=Files.createTempDirectory("rtmu-config-regression");
        var file=folder.resolve("RTM.cfg");
        try {
            check(RTMConfigOptions.ALL.size()==24,"original option inventory changed");
            RTMConfigOptions.loadLegacy(file);
            var generated=RTMConfigOptions.readLegacy(file);
            check(generated.size()==24,"generated config did not round trip every option");
            for(var option:RTMConfigOptions.ALL) check(option.validate(option.defaultValue()).equals(generated.get(option)),"default changed: "+option.key());
            Files.writeString(file,"sound {\nI:\"sound train\"=37\nD:\"audible range train running\"=128.5\n}\nrail {\nI:GeneratingDistance=1024\nI:GeneratingHeight=256\nI:MarkerDisplayDistance=32767\n}\nmodel {\nB:\"do smoothing\"=false\n}\nmarker {\nB:\"Use like 1.12\"=true\nB:markerDistancesMoreRealPosition=false\n}\n");
            RTMConfigOptions.loadLegacy(file);
            check(Math.abs(RTMConfig.trainSoundVol-.37F)<.00001F,"volume percent not converted");
            check(RTMConfig.trainRunningSoundRange==128.5F,"float range not applied");
            check(RTMConfig.railGeneratingDistance==1024 && RTMConfig.railGeneratingHeight==256 && RTMConfig.markerDisplayDistance==32767,"reference integer bounds lost");
            check(!RTMConfig.smoothing && RTMConfig.use1122Marker && !RTMConfig.markerDistanceMoreRealPosition,"boolean settings not applied");
            Files.writeString(file,"Rail {\nI:GeneratingDistance=99999\n# keep this custom setting\nI:foreignSetting=17\nI:GeneratingHeight=-1\n}\nSound {\nD:\"audible range train running\"=NaN\n}\n");
            RTMConfigOptions.loadLegacy(file);
            check(RTMConfig.railGeneratingDistance==64 && RTMConfig.railGeneratingHeight==8 && RTMConfig.trainRunningSoundRange==48,"invalid config was not corrected to defaults");
            check(Files.readString(file).contains("I:foreignSetting=17") && Files.readString(file).contains("# keep this custom setting"),"legacy correction erased unrelated settings");
            check(RTMConfigOptions.readLegacy(file).size()==24,"legacy correction did not save all 24 settings");
            var bool=RTMConfigOptions.ALL.stream().filter(o->o.field().equals("smoothing")).findFirst().orElseThrow();
            try {bool.validate("garbage");throw new AssertionError("invalid boolean accepted");}catch(IllegalArgumentException expected) {}
            System.out.println("Legacy RTM config: 24 options round-tripped, custom values and invalid bounds passed");
        } finally {
            for(var option:RTMConfigOptions.ALL)option.apply(option.defaultValue());
            Files.deleteIfExists(file);Files.deleteIfExists(folder);
        }
    }
}
