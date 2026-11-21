package com.portofino.realtrainmodunofficial.script;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.ngtlib.renderer.model.*;
import java.util.*;
public final class LegacyNativePrecisionRegression {
    public static void main(String[] args) throws Exception {
        String source="Metasequoia Document\nFormat Text Ver 1.0\nObject \"body\" {\n facet 60\n vertex 3 {\n -0.026 0 0\n 100 0 0\n 0 100 20\n }\n face 1 {\n 3 V(0 1 2) UV(0.123456 0.987654 1 0 0 1)\n }\n}\nEof\n";
        var type=Class.forName(MqoModelLoader.class.getName()+"$TextureOpener");
        var opener=java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,values)->method.getName().equals("getPackKey")?"native-low-regression":null);
        var bake=MqoModelLoader.class.getDeclaredMethod("bakeLowPrecision",String.class,type,Map.class,boolean.class);bake.setAccessible(true);
        var graph=ModelLoader.parse(source.getBytes(java.nio.charset.StandardCharsets.UTF_8),"test.mqo",VecAccuracy.LOW,4);
        int checks=0;
        for(boolean smoothing:new boolean[]{false,true}) {
            var model=bake.invoke(null,source,opener,Map.of(),smoothing);var batches=model.getClass().getDeclaredField("batches");batches.setAccessible(true);
            var batch=((List<?>)batches.get(model)).get(0);var dataField=batch.getClass().getDeclaredField("data");dataField.setAccessible(true);float[] data=(float[])dataField.get(batch);
            var face=graph.groupObjects.get(0).faces.get(0);
            for(int i=0;i<4;i++) {int corner=Math.min(i,2);var v=face.vertices[corner];var n=smoothing?face.vertexNormals[corner]:face.faceNormal;var uv=face.getTextureCoordinates()[corner];
                float[] expected={v.x,v.y,v.z,n.x,n.y,n.z,uv.getU(),uv.getV()};
                for(int j=0;j<8;j++) {checks++;if(Float.floatToIntBits(expected[j])!=Float.floatToIntBits(data[i*8+j]))throw new AssertionError("native LOW mismatch at "+i+":"+j);}
            }
        }
        var objBake=MqoModelLoader.class.getDeclaredMethod("bakeObjPrecision",String.class,String.class,type,Map.class,boolean.class,VecAccuracy.class);objBake.setAccessible(true);
        var obj=objBake.invoke(null,"v -0.00026 0.0 0.0\nv 1.0 0.0 0.0\nv 0.0 1.0 0.0\nvt 0.123456 0.75\nf 1/1 2/1 3/1\n","test.obj",opener,Map.of(),false,VecAccuracy.LOW);
        var objBatches=obj.getClass().getDeclaredField("batches");objBatches.setAccessible(true);var objBatch=((List<?>)objBatches.get(obj)).get(0);
        var objData=objBatch.getClass().getDeclaredField("data");objData.setAccessible(true);float[] values=(float[])objData.get(objBatch);
        checks++;if(values[0]!=-0.0005F || values[5]!=1 || values[6]!=(short)(0.123456F*2000F)*0.0005F || values[7]!=0.25F)throw new AssertionError("native OBJ precision/UV/normal mismatch");
        System.out.println("Native/script LOW geometry regressions: "+checks+" passed");
    }
}
