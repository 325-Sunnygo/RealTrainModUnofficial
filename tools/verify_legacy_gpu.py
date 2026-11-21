#!/usr/bin/env python3
"""Run the actual legacy shaders against a hidden native OpenGL context (macOS arm64)."""
from pathlib import Path
import os, subprocess
root=Path(__file__).resolve().parents[1]
subprocess.run(['python3',str(root/'tools/verify_legacy_shaders.py')],check=True)
cache=Path.home()/'.gradle/caches/modules-2/files-2.1/org.lwjgl'
jars=[]
for module in ['lwjgl','lwjgl-glfw','lwjgl-opengl']:
    for filename in [module+'-3.3.3.jar',module+'-3.3.3-natives-macos-arm64.jar']:
        matches=list((cache/module/'3.3.3').glob('*/'+filename))
        if not matches: raise SystemExit('Missing cached runtime: '+filename)
        jars.append(str(matches[0]))
java=Path(os.environ.get('JAVA_HOME','/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home'))/'bin'
out=root/'build/generated/legacy_shader_test'
classpath=os.pathsep.join(jars)
subprocess.run([str(java/'javac'),'-cp',classpath,'-d',str(out),str(root/'tools/LegacyShaderGpuSmoke.java'),str(root/'src/main/java/jp/ngt/ngtlib/renderer/LegacyShaderProgram.java'),str(root/'src/main/java/jp/ngt/ngtlib/renderer/LegacyPointDraw.java'),str(root/'src/main/java/jp/ngt/ngtlib/renderer/LegacyPointVertices.java')],check=True)
subprocess.run([str(java/'java'),'-XstartOnFirstThread','-cp',str(out)+os.pathsep+classpath,'LegacyShaderGpuSmoke',str(out)],check=True)
