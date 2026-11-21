#!/usr/bin/env python3
"""LegacyPackageIndex.java を再生成する (スクリプト importPackage 用のクラス索引)。
rtmu リポジトリ直下で実行: python3 tools/generate_legacy_package_index.py"""
import os, collections
ROOT='src/main/java'
pkgs=collections.defaultdict(list)
for base in ['src/main/java/jp/ngt','src/main/java/jp/ngt/mccompat','src/main/java/jp/ngt/mcte']:
    for dirpath,_,files in os.walk(base):
        for f in files:
            if f.endswith('.java'):
                rel=os.path.relpath(os.path.join(dirpath,f), ROOT).replace('/','.')
                pkg, cls = rel.rsplit('.',1)
                pkgs.setdefault(pkg, []).append(cls)
lines=['package com.portofino.realtrainmodunofficial.script;\n',
 '/** 自動生成 (tools/generate_legacy_package_index.py)。編集しないこと。 */',
 'public final class LegacyPackageIndex {',
 '    private LegacyPackageIndex() {',
 '    }',
 '    public static final java.util.Map<String, String[]> PACKAGES = java.util.Map.ofEntries(']
for pkg in sorted(pkgs):
    cls=', '.join('"%s"'%c for c in sorted(pkgs[pkg]))
    lines.append('        java.util.Map.entry("%s", new String[]{%s}),'%(pkg, cls))
lines.append('    );')
lines.append('}')
os.makedirs('src/main/java/com/portofino/realtrainmodunofficial/script', exist_ok=True)
open('src/main/java/com/portofino/realtrainmodunofficial/script/LegacyPackageIndex.java','w').write('\n'.join(lines)+'\n')
print('generated', len(pkgs), 'packages')
