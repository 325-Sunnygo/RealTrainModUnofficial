package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.model.*;
import java.util.*;
import java.lang.reflect.*;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;

/** Real parser/baker tests without a graphics context or Minecraft world. */
public final class LegacyModelRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static final String MODEL = """
        Metasequoia Document
        Format Text Ver 1.0
        Material 1 {
          "body" col(0.2 0.3 0.4 0.2)
        }
        Object "shadow" {
          facet 59.5
          vertex 4 {
            0 -100 0
            100 -100 0
            100 0 100
            0 0 0
          }
          face 2 {
            4 V(0 1 2 3) M(0) UV(0 0 1 0 1 1 0 1)
            4 V(0 1 2 3) M(0) UV(0 0 1 0 1 1 0 1)
          }
        }
        Eof
        """;

    public static void main(String[] args) throws Exception {
        check(Arrays.equals(new int[]{0, 3, 2, 0, 2, 1}, LegacyPolygon.triangleIndices(4)), "quad diagonal/winding");
        check(Arrays.equals(new int[]{0, 4, 3, 0, 3, 2, 0, 2, 1}, LegacyPolygon.triangleIndices(5)), "polygon winding");
        PolygonModel model = ModelLoader.parse(MODEL.getBytes(java.nio.charset.StandardCharsets.UTF_8), "test.mqo");
        check(model.materials.size() == 1 && model.materials.get("body").id == 0, "material IDs");
        check(model.groupObjects.size() == 1 && model.groupObjects.get(0).faces.size() == 2, "authored duplicate faces preserved");
        Face face = model.groupObjects.get(0).faces.get(0);
        check(face.vertices.length == 6, "triangle-mode source face");
        check(face.vertices[0].y == -1 && face.vertices[1].y == 0, "source vertex order and units");
        check(face.uvs[2] == 0 && face.uvs[3] == 1, "UV follows vertex order");
        TextureSet[] textures = LegacyTextureLayout.create(model, Map.of("body", "textures/body.png|ptmeta=LightAlphaBlend,custom/front.png,custom/back.png", "default", "textures/default.png"));
        check(textures.length == 1 && textures[0].material.id == 0, "texture slots follow material IDs");
        check(textures[0].doAlphaBlend && textures[0].subTextures.length == 2, "explicit light count");
        check(textures[0].subTextures[1].toString().equals("minecraft:custom/back.png"), "light slot order");
        check(LegacyTextureLayout.resolve("unknown", Map.of("another", "wrong.png")) == null, "never choose arbitrary texture");
        check(LegacyTextureLayout.resolve("unknown", Map.of("default", "default.png")).equals("default.png"), "default texture fallback");
        check(!LegacyTextureLayout.fromDescriptor((byte) 0, "body.png|ptmeta=lightalphablend").doAlphaBlend, "case-sensitive original flags");
        TextureSet standard = LegacyTextureLayout.fromDescriptor((byte) 0, "body.png|ptmeta=Light");
        check(standard.subTextures.length == 3 && standard.subTextures[2].toString().equals("minecraft:body_light2.png"), "derived light slots");
        var commaFlags=com.google.gson.JsonParser.parseString("[\"mat1\",\"body.png\",\"AlphaBlend,Light\"]").getAsJsonArray();
        String descriptor=com.portofino.realtrainmodunofficial.modelpack.LegacyTextureDescriptor.encode(commaFlags);
        TextureSet commaTexture=LegacyTextureLayout.fromDescriptor((byte)0,descriptor);
        check(commaTexture.doAlphaBlend && commaTexture.lightOptionDeclared,"comma-separated flags remain in the third options field");
        check(commaTexture.subTextures.length==3 && commaTexture.subTextures[0].toString().equals("minecraft:body_light0.png"),
                "Light flag is not interpreted as a custom texture filename");
        var customFlags=com.google.gson.JsonParser.parseString("[\"mat1\",\"body.png\",\"AlphaBlend,Light\",\"lamp0.png\",\"lamp1.png\"]").getAsJsonArray();
        TextureSet customTexture=LegacyTextureLayout.fromDescriptor((byte)0,
                com.portofino.realtrainmodunofficial.modelpack.LegacyTextureDescriptor.encode(customFlags));
        check(customTexture.subTextures.length==2 && customTexture.subTextures[1].toString().equals("minecraft:lamp1.png"),
                "custom light image count starts at the fourth field");
        var brokenFlag=com.google.gson.JsonParser.parseString("[\"mat1\",\"body.png\",\"Al,phaBlend\"]").getAsJsonArray();
        check(!LegacyTextureLayout.fromDescriptor((byte)0,
                com.portofino.realtrainmodunofficial.modelpack.LegacyTextureDescriptor.encode(brokenFlag)).doAlphaBlend,
                "do not create options by deleting commas inside unrelated text");
        Class<?> openerType = Class.forName(MqoModelLoader.class.getName() + "$TextureOpener");
        Object opener = Proxy.newProxyInstance(openerType.getClassLoader(), new Class<?>[]{openerType},
                (proxy, method, values) -> method.getName().equals("getPackKey") ? "legacy-regression" : null);
        Method bake = MqoModelLoader.class.getDeclaredMethod("bake", String.class, openerType, Map.class, boolean.class);
        bake.setAccessible(true);
        MqoModelLoader.MqoModel baked = (MqoModelLoader.MqoModel) bake.invoke(null, MODEL, opener, Map.of(), true);
        check(baked.getOriginalGroupNames().contains("shadow"), "shadow group retained by actual baker");
        Field batchesField = baked.getClass().getDeclaredField("batches");
        batchesField.setAccessible(true);
        List<?> batches = (List<?>) batchesField.get(baked);
        Object batch = batches.get(0);
        Field vertices = batch.getClass().getDeclaredField("vertexCount");
        vertices.setAccessible(true);
        check(vertices.getInt(batch) == 8, "two source quads retain all eight original corners, without deduplication");
        Field alpha = batch.getClass().getDeclaredField("baseAlpha");
        alpha.setAccessible(true);
        check(alpha.getFloat(batch) == 1, "MQO col alpha does not override original RTM material rules");
        Field translucentField=batch.getClass().getDeclaredField("translucent");
        translucentField.setAccessible(true);translucentField.setBoolean(batch,true);
        var submission=batch.getClass().getDeclaredMethod("triangleIndices");submission.setAccessible(true);
        check(((int[])submission.invoke(batch)).length==12,"alpha pass preserves original duplicate triangle stream");

        float[] irisNormals = new float[32];
        check(!com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.hasVaryingNormals(irisNormals), "flat batch retains shader quads");
        irisNormals[8+3]=0.5F;
        check(com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.hasVaryingNormals(irisNormals), "vertex-varying normals use Iris-compatible triangles");
        check(com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(true,true), "Iris smooth normals use triangles");
        check(!com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(true,false), "Iris flat normals retain quads");
        check(com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(false,false), "original normal path keeps triangles");

        float[] flat = com.portofino.realtrainmodunofficial.client.model.LegacyModelGeometry.flatten(model.groupObjects.get(0), false);
        check(flat.length == 16 * 8, "script triangle list must not be triangulated a second time");
        float[] nonplanar = LegacyPolygon.faceNormal(new float[]{0,0,0, 0,1,0, 1,1,1, 0,0,0, 1,1,1, 1,0,0});
        double unit = 1 / Math.sqrt(6);
        check(Math.abs(nonplanar[0] - unit) < 1e-6 && Math.abs(nonplanar[1] - unit) < 1e-6
                && Math.abs(nonplanar[2] + 2 * unit) < 1e-6, "nonplanar face averages unit triangle normals");
        PolygonModel mirrored = ModelLoader.parse(MODEL.replace("facet 59.5", "facet 59.5\n mirror_axis 1")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8), "mirrored.mqo");
        check(mirrored.groupObjects.get(0).faces.size() == 4, "mirror produces source and reflected faces");
        boolean negativeX = mirrored.groupObjects.get(0).faces.stream()
                .flatMap(f -> Arrays.stream(f.vertices)).anyMatch(v -> v.x < 0);
        check(negativeX, "mirrored vertices retained in script model");
        String floor = MODEL.replace("Object \"shadow\"", "Object \"body\"")
                .replace("100 0 100", "100 -100 100").replace("0 0 0\n", "0 -100 0\n");
        MqoModelLoader.MqoModel floorBaked = (MqoModelLoader.MqoModel) bake.invoke(null, floor, opener, Map.of(), false);
        check(floorBaked.getOriginalGroupNames().contains("body"), "low flat body geometry must not be removed as shadow");
        check("default.png".equals(LegacyTextureLayout.resolve(null, Map.of("default", "default.png"))),
                "material-less MQO resolves immutable default texture map");
        check(LegacyTextureLayout.resolve(null, Map.of()) == null, "material-less MQO with empty overrides");
        String materialLess = MODEL.replace("Material 1 {\n  \"body\" col(0.2 0.3 0.4 0.2)\n}\n", "");
        check(ModelLoader.parse(materialLess.getBytes(java.nio.charset.StandardCharsets.UTF_8), "no-material.mqo").materials.isEmpty(),
                "fixture really has no material declarations");
        MqoModelLoader.MqoModel noMaterials = (MqoModelLoader.MqoModel) bake.invoke(null, materialLess, opener, Map.of(), false);
        check(noMaterials.getOriginalGroupNames().contains("shadow"), "material-less MQO loads rather than returning missing object");
        Method modernFilter = Class.forName("com.portofino.realtrainmodunofficial.client.renderer.RtmTrainRenderer")
                .getDeclaredMethod("shouldRenderGroup", String.class);
        modernFilter.setAccessible(true);
        Method trainFilter = Class.forName("com.portofino.realtrainmodunofficial.client.renderer.TrainEntityRenderer")
                .getDeclaredMethod("shouldRenderTrainGroup", String.class, boolean.class, boolean.class,
                        boolean.class, Class.forName("com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition"), boolean.class, boolean.class);
        trainFilter.setAccessible(true);
        for (String name : List.of("shadow", "body_shadow", "影", "影ms", "body_ms", "body_kage", "body_ms_0", "body_kage_0", "door_guide", "panel[obj]", "body_atari", "body atari", "body-90", "body-180(mx)", "floor", "under_detail")) {
            check((boolean) modernFilter.invoke(null, name), "RTM renderer preserves " + name);
            check((boolean) trainFilter.invoke(null, name, true, false, false, null, false, false),
                    "train renderer preserves " + name);
        }
        float[] storedTriangles=com.portofino.realtrainmodunofficial.client.model.LegacyModelGeometry.flatten(model.groupObjects.get(0),false);
        int[] submitted=com.portofino.realtrainmodunofficial.client.model.LegacyTriangleSubmission.indices(storedTriangles);
        check(submitted.length==12,"two duplicated source quads submit four triangles without extra degenerate primitives");
        check(Arrays.equals(Arrays.copyOf(submitted,6),new int[]{0,1,2,4,5,6}),"triangle vertices preserve stored winding and UV association");
        float[] actualQuad=new float[32];actualQuad[8]=1;actualQuad[16]=1;actualQuad[17]=1;actualQuad[25]=1;
        check(Arrays.equals(com.portofino.realtrainmodunofficial.client.model.LegacyTriangleSubmission.indices(actualQuad),
                new int[]{0,1,2,0,2,3}),"non-degenerate OBJ/voxel quad retains both triangles");
        var mirrorBaked=(MqoModelLoader.MqoModel)bake.invoke(null,MODEL.replace("facet 59.5","facet 59.5\n mirror_axis 1"),opener,Map.of(),false);
        Object mirrorBatch=((List<?>)batchesField.get(mirrorBaked)).get(0);
        Field mirrorDataField=mirrorBatch.getClass().getDeclaredField("data");mirrorDataField.setAccessible(true);
        float[] mirrorData=(float[])mirrorDataField.get(mirrorBatch);
        Method nativeIndices=mirrorBatch.getClass().getDeclaredMethod("triangleIndices");nativeIndices.setAccessible(true);
        int[] mirrorOrder=(int[])nativeIndices.invoke(mirrorBatch);
        boolean sourceBeforeMirror=true,mirrorAfterSource=true;
        for(int i=0;i<6;i++) sourceBeforeMirror &= mirrorData[mirrorOrder[i]*8]>=0;
        for(int i=6;i<12;i++) mirrorAfterSource &= mirrorData[mirrorOrder[i]*8]<=0;
        check(sourceBeforeMirror,"complete source polygon precedes reflected triangles");
        check(mirrorAfterSource,"complete reflected face follows the source polygon");
        Class<?> bindingType=Class.forName(MqoModelLoader.class.getName()+"$TextureBinding");
        Method parseBinding=bindingType.getDeclaredMethod("parse",String.class);parseBinding.setAccessible(true);
        Object binding=parseBinding.invoke(null,descriptor);
        Method getOptions=bindingType.getDeclaredMethod("options");getOptions.setAccessible(true);
        Method getLights=bindingType.getDeclaredMethod("lightTexturePaths");getLights.setAccessible(true);
        check(((Set<?>)getOptions.invoke(binding)).containsAll(Set.of("alphablend","light"))
                && ((List<?>)getLights.invoke(binding)).isEmpty(),"actual texture binder preserves flags without a bogus Light image");
        var childPart=new com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.DoorAnimationDefinition(
                List.of("doorChild"),net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,List.of(),List.of());
        var leftPart=new com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.DoorAnimationDefinition(
                List.of("doorFL","doorFL1"),net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,List.of(),List.of(childPart));
        var rightPart=new com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition.DoorAnimationDefinition(
                List.of("doorFR"),net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,List.of(),List.of());
        var partOrder=com.portofino.realtrainmodunofficial.client.model.LegacyVehiclePartOrder.create(List.of(leftPart),List.of(rightPart));
        check(partOrder.getOrDefault("alpha",0)==0 && partOrder.get("doorFL")>0,"body shadow belongs before moving parts within each material");
        check(partOrder.get("doorFL").equals(partOrder.get("doorFL1")),"one configured Parts call groups its source objects");
        check(partOrder.get("doorFL")<partOrder.get("doorChild") && partOrder.get("doorChild")<partOrder.get("doorFR"),
                "parent then child then right-side configured part order");
        float[] duplicate=new float[6*8];
        for(int i=0;i<3;i++) { duplicate[i*8]=i;duplicate[i*8+6]=i*0.25F; }
        System.arraycopy(duplicate,0,duplicate,24,24);
        check(com.portofino.realtrainmodunofficial.client.model.LegacyCoplanarFaces.unique(duplicate,new int[]{0,1,2,3,4,5}).length==3,
                "identical alpha triangle composites only once");
        check(com.portofino.realtrainmodunofficial.client.model.LegacyCoplanarFaces.unique(duplicate,new int[]{0,1,2,4,5,3}).length==3,
                "cyclic vertex start does not hide a duplicate");
        check(com.portofino.realtrainmodunofficial.client.model.LegacyCoplanarFaces.unique(duplicate,new int[]{0,1,2,3,5,4}).length==6,
                "opposite-facing surfaces remain independent");
        duplicate[3*8+6]+=0.125F;
        check(com.portofino.realtrainmodunofficial.client.model.LegacyCoplanarFaces.unique(duplicate,new int[]{0,1,2,3,4,5}).length==6,
                "coplanar faces with different UVs are retained");
        System.out.println("Legacy model regressions: " + checks + " passed");
    }
}
