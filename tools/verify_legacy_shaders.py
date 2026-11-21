#!/usr/bin/env python3
"""Resolve vanilla GLSL imports and compile/link the actual compatibility shader."""
from pathlib import Path
import os,re,shutil,subprocess,zipfile
root=Path(__file__).resolve().parents[1]
cache=Path.home()/'.gradle/caches'
client=cache/'fabric-loom/1.21.1/minecraft-client.jar'
if not client.is_file():
    candidates=list(cache.glob('**/client-extra.jar'))
    if not candidates: raise SystemExit('Minecraft 1.21.1 asset jar is required to resolve GLSL imports.')
    client=candidates[0]
with zipfile.ZipFile(client) as archive:
    def expand(text):
        return re.sub(r'#moj_import\s+<([^>]+)>',lambda match:expand(re.sub(r'^\s*#version[^\n]*', '', archive.read('assets/minecraft/shaders/include/'+match[1]).decode(), flags=re.MULTILINE)),text)
    out=root/'build/generated/legacy_shader_test';out.mkdir(parents=True,exist_ok=True)
    source=root/'src/main/resources/assets/realtrainmodunofficial/shaders/core'
    for extension,target in [('vsh','vert'),('fsh','frag')]:
        (out/('legacy_entity.'+target)).write_text(expand((source/('legacy_entity.'+extension)).read_text()))
compiler=shutil.which('glslangValidator')
if not compiler: raise SystemExit('glslangValidator is required for standalone shader verification.')
subprocess.run([compiler,'-l',str(out/'legacy_entity.vert'),str(out/'legacy_entity.frag')],check=True)
