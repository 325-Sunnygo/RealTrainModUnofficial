package com.portofino.realtrainmodunofficial.entity.legacyformation;

import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public final class Formation {
    public final long id;
    public FormationEntry[] entries;
    private byte direction; // 0 = entries[0] is front, 1 = entries[last] is front
    private float speed;

    public Formation(long id, int size) {
        this.id = id;
        this.entries = new FormationEntry[size];
        FormationManager.getInstance().register(id, this);
    }

    public int size() { return entries.length; }

    public FormationEntry get(int i) { return entries[i]; }

    public Stream<FormationEntry> stream() {
        return Arrays.stream(entries).filter(Objects::nonNull);
    }

    public Stream<TrainEntity> trainStream() {
        return stream().map(e -> e.train).filter(Objects::nonNull);
    }

    /**
     * 引数は TrainEntity でも、レガシー JS が渡す LegacyScriptExecutor ラッパーでも受け付ける。
     * ★TrainEntity 専用オーバーロードを作らないこと: Nashorn はオーバーロードがあると
     *   getEntry(TrainEntity) を選び、ラッパー引数を TrainEntity へキャストして ClassCastException に
     *   なる。Object 1 本にして内部で getTrain() で中身を取り出す(スクリプト互換)。
     */
    public FormationEntry getEntry(Object obj) {
        TrainEntity train = asTrainEntity(obj);
        return train == null ? null : stream().filter(e -> train.equals(e.train)).findFirst().orElse(null);
    }

    private static TrainEntity asTrainEntity(Object obj) {
        if (obj instanceof TrainEntity t) {
            return t;
        }
        if (obj == null) {
            return null;
        }
        try {
            Object r = obj.getClass().getMethod("getTrain").invoke(obj);
            if (r instanceof TrainEntity t) {
                return t;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Returns the front-most entry (the one driving the formation) */
    public FormationEntry getFrontEntry() {
        if (entries.length == 0) return null;
        return (direction == 0) ? entries[0] : entries[entries.length - 1];
    }

    public boolean isFrontCar(TrainEntity train) {
        FormationEntry front = getFrontEntry();
        return front != null && train.equals(front.train);
    }

    private void reverse() {
        for (int i = 0, j = entries.length - 1; i < j; i++, j--) {
            FormationEntry tmp = entries[i];
            entries[i] = entries[j];
            entries[j] = tmp;
        }
        stream().forEach(e -> e.dir ^= 1);
    }

    private void reallocation() {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != null) {
                entries[i].entryId = i;
                entries[i].train.setFormation(this);
            }
        }
    }

    private void addAll(FormationEntry[] other) {
        FormationEntry[] merged = new FormationEntry[entries.length + other.length];
        System.arraycopy(entries, 0, merged, 0, entries.length);
        System.arraycopy(other, 0, merged, entries.length, other.length);
        entries = merged;
    }

    private void trim(int start, int end) {
        if (start > end || start < 0 || end >= entries.length) return;
        int len = end - start + 1;
        FormationEntry[] arr = new FormationEntry[len];
        System.arraycopy(entries, start, arr, 0, len);
        entries = arr;
    }

    /**
     * RTM faithful: merge par5 (other formation) into this formation.
     * par1/par2: trains being connected, par3/par4: their bogieIndex sides (0 or 1).
     */
    public void connectTrain(TrainEntity par1, TrainEntity par2, int par3, int par4, Formation par5) {
        FormationEntry entry = getEntry(par1);
        if (entry == null) return;

        // If connecting side == entry.dir, reverse this formation so par1 becomes the tail
        if (par3 == entry.dir) {
            reverse();
        }

        entry = par5.getEntry(par2);
        if (entry == null) return;

        // If connecting side != entry.dir, reverse other formation so par2 becomes the head
        if (par4 != entry.dir) {
            par5.reverse();
        }

        // Store connection sides in the first entry of par5 (it now connects to our last entry)
        int lastIdx = entries.length - 1;
        FormationEntry ourTail = entries[lastIdx];
        FormationEntry theirHead = par5.entries[0];
        if (ourTail != null && theirHead != null) {
            // Determine actual sides based on dir:
            // ourTail connects via its "end" end (dir=0 → side index 1 end, dir=1 → side index 0 end)
            int ourSide = (ourTail.dir == 0) ? 1 : -1; // +1=front end, -1=rear end
            int theirSide = (theirHead.dir == 0) ? -1 : 1; // opposite side
            theirHead.leaderSide = ourSide;
            theirHead.followerSide = theirSide;
        }

        addAll(par5.entries);
        reallocation();
        setSpeed(0.0f);
        trainStream().forEach(t -> { t.setSpeed(0.0f); t.setNotch(0); });
        FormationManager.getInstance().remove(par5.id);
    }

    /**
     * Called when a train is removed (died/discarded).
     */
    public void onRemovedTrain(TrainEntity train) {
        if (entries.length <= 1) {
            FormationManager.getInstance().remove(id);
            return;
        }
        FormationEntry entry = getEntry(train);
        if (entry == null) return;

        if (entry.entryId == 0) {
            trim(1, entries.length - 1);
        } else if (entry.entryId == entries.length - 1) {
            trim(0, entries.length - 2);
        } else {
            // Split into two formations
            int idx = entry.entryId;
            int tailSize = entries.length - idx - 1;
            Formation tail = new Formation(FormationManager.getInstance().getNewId(), tailSize);
            for (int i = idx + 1; i < entries.length; i++) {
                tail.entries[i - idx - 1] = entries[i];
            }
            tail.reallocation();
            trim(0, idx - 1);
        }
        reallocation();
    }

    /**
     * Called by crowbar: disconnect at the given side of the given train.
     * RTM faithful port of Formation.onDisconnectedTrain().
     * side: bogieIndex that was interacted with (0 or 1)
     */
    public void onDisconnectedTrain(TrainEntity train, int side) {
        FormationEntry entry = getEntry(train);
        if (entry == null) return;

        boolean cutFront = (side == entry.dir);
        int splitAt = cutFront ? entry.entryId : entry.entryId + 1;

        if (splitAt <= 0 || splitAt >= entries.length) return;

        int tailSize = entries.length - splitAt;
        Formation tail = new Formation(FormationManager.getInstance().getNewId(), tailSize);
        for (int i = splitAt; i < entries.length; i++) {
            tail.entries[i - splitAt] = entries[i];
        }
        tail.reallocation();
        tail.setSpeed(0.0f);
        trim(0, splitAt - 1);
        reallocation();
        setSpeed(0.0f);
    }

    public void setSpeed(float spd) {
        this.speed = spd;
    }

    public float getSpeed() { return speed; }

    public byte getDirection() { return direction; }

    /**
     * Called every tick from the front car.
     * Moves all cars in formation order using the rail movement system.
     */
    public void updateTrainMovement() {
        TrainEntity prevTrain = null;
        float currentSpeed = speed;
        for (int i = 0; i < entries.length; i++) {
            int idx = (direction == 0) ? i : (entries.length - i - 1);
            FormationEntry entry = entries[idx];
            if (entry != null && entry.train != null && entry.train.isAlive()) {
                if (prevTrain == null) {
                    // Front car: already moved by normal tick; capture its actual speed
                    currentSpeed = entry.train.getSpeed();
                } else {
                    // Follower car: position relative to previous car
                    entry.train.moveAsFormationFollower(prevTrain, entry.leaderSide, entry.followerSide, currentSpeed);
                }
                prevTrain = entry.train;
            }
        }
    }

    /**
     * Returns true if this formation contains the given train
     */
    public boolean containsTrain(TrainEntity train) {
        return trainStream().anyMatch(t -> t == train);
    }
}
