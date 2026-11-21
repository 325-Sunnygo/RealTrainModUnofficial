# NeoForge shadow / camera / item cleanup / SRBX verification

2026-10-25 JST

## Changes

- Deleted FormationItem, FormationCrowbarItem and the already unregistered VehicleFormationItem, both dedicated screens, item-data/spawner classes, both payloads, network registration, client hooks and dedicated assets/translations. The JAR contains none of these implementations. Original RTM train coupling/formation compatibility classes remain.
- Reproduced SRBX `new net.minecraft.client.gui.Gui()` resolving to the modern HUD and throwing a constructor TypeError. Added the instantiable legacy textured-rectangle helper and class mapping.
- SRBX expands to 6–10 million characters per renderer. Removed repeated whole-prefix scans in Java.type detection and repeated whole-source scans in literal restoration. Include order, repeated includes and literal values remain unchanged. Errors now report filename and cause instead of embedding megabytes of source.
- Normal/light passes share coordinate transformation, triangle submission and the legacy shader; removed the additional light-pass depth offset.
- E131 alpha has 92 submitted triangles but 84 unique triangles. The alpha path removes only duplicate triangles with identical positions, normals, UVs and winding within a material batch. Other faces and reverse winding remain. This repairs duplicate submission; it does not assert that KaizPatchX itself deduplicates faces.
- Restored original camera first-person/main-hand activation, Z/X C/V B/N M controls, four-line HUD and block-use screen. Replaced photographic exposure/grid controls with the original eight-band depth blur and ten-frame sensitivity snapshot. Nearest filtering and clamped edges.
- Canonicalized packaged asset paths, preserving legacy stream lookup aliases. Supplied miniature and four background-panel static variants for block-entity rendering.

## Checks

- SRBX 1.0.0: seven distinct renderer entry points loaded, initialized and produced geometry for null-entity preview passes 0 and 1, using the installed pack. No world was launched.
- Bundled scripts: 82 prepared and loaded, zero syntax/preparation/runtime-skipped failures. Standalone registry binding warnings remain expected outside the game.
- Regression counts: script 44, model 75, camera 1,063, coordinate positions 4,096 bit-identical, render/sound 28, render API 26, selection 8, immediate 8, rail/alpha 16, runtime 31; inventory/registry 42, rail properties 39, deferred alpha 8.
- E131 geometry: 13 models / 642,208 triangles agree in positions, UVs, winding, multiplicity and order before targeted alpha deduplication. Smoothed alpha duplicate audit 92 -> 84.
- Four real Minecraft ShaderInstance programs initialized. Five offscreen camera framebuffer checks passed. 1,048,576 source PNG/GPU RGBA pixels identical.
- Independent OpenGL compatibility oracle: 947 checks passed on Apple M2, including point boundaries.
- JAR scan: zero invalid asset paths; deleted classes absent; miniature one variant and background panel four variants included.

## Remaining validation

No game/world was launched or modified. No SRBX crash stack was present in the supplied/current log: GUI constructor failure and pathological source scanning were reproduced directly from the installed pack. SRBX placement/rail mutation, server callbacks, networking, wheel UI, live shadows and visual camera parity are not verified by the preview/component tests. Complete 1.7.10 script/API compatibility and bug-free operation have not been established. Existing autoSplit parity remains incomplete; Fabric is deferred. Camera radius-one Gaussian underflow safely uses identity weights instead of original NaNs.

JAR SHA-256: `2d97640be1312a6d484328cf002f1d45cbcca1d1ec04ff3f4e57af9fc10ecbde`
