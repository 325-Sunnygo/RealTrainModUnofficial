package jp.ngt.rtm.block.tileentity;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import org.openjdk.nashorn.api.scripting.AbstractJSObject;

/** Legacy type predicate for scaffold tiles sharing RTMU's installed-object implementation. */
public final class TileEntityScaffold extends AbstractJSObject {
    public static final TileEntityScaffold TYPE = new TileEntityScaffold();

    @Override
    public boolean isFunction() { return true; }

    @Override
    public boolean isInstance(Object value) {
        return value instanceof InstalledObjectBlockEntity tile
                && tile.getCategory() == InstalledObjectCategory.SCAFFOLD;
    }

    @Override
    public Object getMember(String name) {
        if ("class".equals(name)) return InstalledObjectBlockEntity.class;
        return super.getMember(name);
    }
}
