# Original texture pipeline alignment (NeoForge)

2026-10-25 JST

Compared directly against KaizPatchX ModelObject.renderWithTexture, TextureSet, RenderVehicleBase and the MQO material parser.

Changes:
- Removed generated opaque-only/window-only images and all alpha-rewriting image helpers. Both passes bind the original PNG.
- Removed filename-based _light additive/full-bright inference from script model-group rendering. Script render state controls blending/lighting.
- Kept source duplicate triangles: the previous release's alpha deduplication was not original behavior and is no longer applied.
- Applied material-major submission order to normal/alpha/light paths. Group transforms remain applied inside each material.
- Script model groups submit the original triangle stream, with depth writes and explicit legacy pass alpha testing for vehicles.
- Base and light PNG identities use source path rather than option strings; differing material flags share the same raw image.
- Added texture .mcmeta blur/clamp parsing for model and script-bound PNGs, preserving nearest/repeat defaults. Legacy RenderTypes honor blur metadata. Native core GL uses clamp-to-edge.
- Invalidated old baked model cache (format version 4).

Verification:
- Script regression 44, model regression 76 (including duplicate alpha triangle preservation), camera 1063, vertex transforms 4096 bit-identical, render/sound 28, render API 26, selection 8, immediate 8, rail/alpha 16, runtime 31 passed.
- E131: 13 models, 642208 triangles agree in positions, UVs, winding, multiplicity and ordered submissions. Alpha stream retains all 92 triangles (84 unique), as source does.
- Source parts.png / DynamicTexture GPU readback: 1048576 identical RGBA pixels.
- Four shader programs initialized, five offscreen camera framebuffer tests passed, four blur/clamp combinations matched actual GPU sampler state.
- NeoForge JAR built; packaged resource IDs valid. No game/world launch or modification, no automatic installation.

Live in-game shadow appearance remains unverified. This report establishes the listed changes and component checks, not complete renderer or script compatibility.

SHA-256: db6ba89441a30ae1c00c890fedaf7c1c38a510d3a021f1b4c674704450c5846e
