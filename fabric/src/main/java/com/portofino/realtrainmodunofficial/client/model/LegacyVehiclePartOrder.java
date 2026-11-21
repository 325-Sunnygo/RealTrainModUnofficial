package com.portofino.realtrainmodunofficial.client.model;

import java.util.*;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.DoorAnimationDefinition;

/** Body first, then configured left/right parts, with each parent's children immediately after it. */
public final class LegacyVehiclePartOrder {
    private LegacyVehiclePartOrder() {}
    public static Map<String,Integer> create(List<DoorAnimationDefinition> left,List<DoorAnimationDefinition> right) {
        Map<String,Integer> order=new HashMap<>();int[] next={0};
        append(left,order,next);append(right,order,next);return Map.copyOf(order);
    }
    private static void append(List<DoorAnimationDefinition> parts,Map<String,Integer> order,int[] next) {
        for(var part:parts) {
            int rank=++next[0];
            for(String name:part.objects()) order.putIfAbsent(name,rank);
            append(part.childParts(),order,next);
        }
    }
}
