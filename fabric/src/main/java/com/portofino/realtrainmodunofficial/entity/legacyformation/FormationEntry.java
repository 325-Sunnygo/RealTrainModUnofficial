package com.portofino.realtrainmodunofficial.entity.legacyformation;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;

public class FormationEntry {
    public final TrainEntity train;
    public int entryId;      // position in Formation.entries[]
    public int dir;          // 0 or 1 — which bogie faces "beginning" of formation (RTM faithful)
    public int leaderSide;   // side on the previous car that connects here (for movement)
    public int followerSide; // side on THIS car that connects to leader (for movement)

    public FormationEntry(TrainEntity train, int entryId, int dir) {
        this.train = train;
        this.entryId = entryId;
        this.dir = dir;
        this.leaderSide = -1;
        this.followerSide = 1;
    }

    public FormationEntry(TrainEntity train, int entryId, int dir, int leaderSide, int followerSide) {
        this.train = train;
        this.entryId = entryId;
        this.dir = dir;
        this.leaderSide = leaderSide;
        this.followerSide = followerSide;
    }
}
