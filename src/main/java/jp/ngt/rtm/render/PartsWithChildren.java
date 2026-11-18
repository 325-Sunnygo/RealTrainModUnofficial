package jp.ngt.rtm.render;

import java.util.ArrayList;
import java.util.List;

/** 本家 jp.ngt.rtm.render.PartsWithChildren (KaizPatch… */
public class PartsWithChildren extends Parts {
    public final List<Parts> childParts = new ArrayList<>();

    public PartsWithChildren(String... names) {
        super(names);
    }

    public void addParts(Parts parts) {
        this.childParts.add(parts);
    }

    @Override
    public void init(PartsRenderer renderer) {
        super.init(renderer);
        this.childParts.forEach(parts -> parts.init(renderer));
    }
}
