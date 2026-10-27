#!/usr/bin/env python3
"""Run Java/Nashorn regression checks without starting Minecraft.

Uses JDK 21 from JAVA_HOME and the Nashorn/ASM artifacts already resolved by Gradle.
Run ./gradlew compileJava first if these artifacts have not been downloaded yet.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
jars = []
for group, artifact, version in [('org.openjdk.nashorn', 'nashorn-core', '15.4')] + [
        ('org.ow2.asm', artifact, '9.5') for artifact in ('asm', 'asm-commons', 'asm-tree', 'asm-util', 'asm-analysis')]:
    candidates = sorted((cache / group / artifact / version).glob('*/*.jar'))
    if not candidates:
        raise SystemExit(f'Missing {artifact}:{version}; resolve Gradle dependencies first.')
    jars.append(str(candidates[0]))
java_bin = Path(os.environ['JAVA_HOME']) / 'bin' if 'JAVA_HOME' in os.environ else None
classpath = os.pathsep.join(jars)
with tempfile.TemporaryDirectory(prefix='rtmu-script-tests-') as directory:
    for source_root in (root, root / 'fabric'):
        sources = [source_root / 'src/main/java' / p for p in (
            'com/portofino/realtrainmodunofficial/script/LegacyScriptIncludes.java',
            'com/portofino/realtrainmodunofficial/script/LegacyClassReferences.java',
            'com/portofino/realtrainmodunofficial/script/LegacySourceMask.java',
            'com/portofino/realtrainmodunofficial/util/PackTextDecoder.java',
            'jp/ngt/ngtlib/util/ColorUtil.java',
            'jp/ngt/ngtlib/math/PerlinNoise.java',
            'jp/ngt/mccompat/ChunkCoordinates.java')]
        sources.append(root / 'src/test/java/com/portofino/realtrainmodunofficial/script/LegacyScriptRegression.java')
        subprocess.run([str(java_bin / 'javac') if java_bin else 'javac', '-encoding', 'UTF-8', '-cp', classpath, '-d', directory, *map(str, sources)], check=True)
        print(f'Testing {source_root.name}', flush=True)
        subprocess.run([str(java_bin / 'java') if java_bin else 'java', '-cp', directory + os.pathsep + classpath,
                        'com.portofino.realtrainmodunofficial.script.LegacyScriptRegression'], check=True)
