package net.neoforged.neoforge.client.model.data;
import java.util.*;
public final class ModelData {
 public static final ModelData EMPTY=new ModelData(Map.of());
 private final Map<ModelProperty<?>,Object> values;
 private ModelData(Map<ModelProperty<?>,Object> values){this.values=Map.copyOf(values);}
 @SuppressWarnings("unchecked") public <T> T get(ModelProperty<T> key){return (T)values.get(key);}
 public static Builder builder(){return new Builder();}
 public static final class Builder {
 private final Map<ModelProperty<?>,Object> values=new IdentityHashMap<>();
 public <T> Builder with(ModelProperty<T> key,T value){values.put(key,value);return this;}
 public ModelData build(){return new ModelData(values);}
 }
}
