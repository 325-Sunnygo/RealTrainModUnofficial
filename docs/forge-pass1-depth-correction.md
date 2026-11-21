# Forge pass-1 inherited depth mask correction

2026-10-26 JST

## Reproduced failure

Previous production code let vehicle alpha pass fragments write depth. The new regression uses actual Minecraft ShaderInstance, RenderType.setupRenderState, RenderType.draw and BufferUploader in a hidden GL context. With source alpha=0 and pass=1, it reproduced depth changing from 1.0 to 0.5 despite no visible color. This creates invisible occluders for later faces and causes overlap/plane boundaries to become visible. The previous shader-only/color-only tests did not check this.

## Original caller verified

The earlier assumption that RenderVehicleBase inherits an enabled depth mask was wrong. Its own code does not disable depth writes because Minecraft/Forge already does so around the pass.

- The installed original Minecraft 1.7.10 client JAR was inspected with javap: blt.class (EntityRenderer), glDepthMask(false) at bytecode offset 1107, translucent terrain rendering, then glDepthMask(true) at 1284.
- Official Forge 1.7.10 inserts entity render pass 1 between translucent terrain and the depth-mask restoration:
  https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/patches/minecraft/net/minecraft/client/renderer/EntityRenderer.java.patch#L121-L137
- The installed KaizPatchX 1.10.4 RenderVehicleBase uses LESS 1.0 alpha testing and source-alpha blending for vehicle pass 1; it inherits the caller's disabled depth mask.

## Fix

- LegacyRenderTypes.vehicle now disables default depth writes for alpha passes while retaining depth testing. Explicit script depth-mask changes still override the inherited default.
- Vehicle script recording starts pass 1 with depthWrite(false); passes 0 and 2–4 retain enabled writes.
- Deferred vehicle alpha is flushed at AFTER_TRANSLUCENT_BLOCKS, following translucent terrain. Rail geometry still flushes at AFTER_BLOCK_ENTITIES.
- The legacy alpha fallback RenderType also uses color-only writes.
- Original PNGs, UVs and face streams remain untouched. No shadow group is hidden or removed.

## Verification

- Before fix: actual production-path depth check fails at alpha=0/pass=1 (expected 1.0, actual 0.5).
- After fix: 40 production-path color/depth checks passed across 10 alpha values and normal/alpha passes. Fully transparent fragments preserve depth, opaque normal fragments still write it.
- Four actual shader programs initialized; five camera framebuffer tests and four metadata sampler combinations passed; 1048576 PNG/GPU RGBA pixels identical.
- Script 44, model 76, camera 1063, vertex positions 4096 bit-identical, render/sound 28, API 26, selection 8, immediate 8, rail/alpha 16, runtime 31, inventory 42, rail properties 39 and deferred alpha 8 passed.
- Build and packaged resource paths checked. No game/world launch, no automatic installation. Live screenshot appearance still requires in-game validation.

This corrects prior reports in this task which claimed vehicle pass 1 should always write depth.

SHA-256: 6b7e2e5526aefd8604351f15144870e74a861e060f8739a71da13162cfff7ee1
