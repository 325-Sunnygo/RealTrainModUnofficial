#!/usr/bin/env python3
"""GraalJS 互換オーバーロード生成器。

Nashorn は JS の数値 (Double) → float/int、Boolean → int を暗黙変換したが、
GraalJS はしない。そのためスクリプト公開クラスの全メソッドに対し、
float/int/long を double に、char を String に緩めたオーバーロードを機械生成する。

実行: python3 tools/generate_graaljs_overloads.py   (リポジトリ直下)
"""
import os, re, sys

TARGETS = [
    'src/main/java/jp/ngt/rtm/render/PartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/VehiclePartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/MachinePartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/RailPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/SignalPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/WirePartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/OrnamentPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/TileEntityPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/EntityPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/BasicVehiclePartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/BasicRailPartsRenderer.java',
    'src/main/java/jp/ngt/rtm/render/ModelObject.java',
    'src/main/java/jp/ngt/rtm/render/Parts.java',
    'src/main/java/jp/ngt/rtm/render/ActionParts.java',
    'src/main/java/jp/ngt/rtm/modelpack/state/DataMap.java',
    'src/main/java/jp/ngt/rtm/modelpack/modelset/DataMapCompat.java',
    'src/main/java/jp/ngt/rtm/modelpack/ModelPackManager.java',
    'src/main/java/jp/ngt/rtm/sound/SoundUpdaterTrain.java',
    'src/main/java/jp/ngt/rtm/entity/train/EntityTrainBase.java',
    'src/main/java/jp/ngt/rtm/entity/train/EntityBogie.java',
    'src/main/java/jp/ngt/rtm/entity/vehicle/EntityVehicleBase.java',
    'src/main/java/jp/ngt/rtm/entity/train/parts/EntityFloor.java',
    'src/main/java/jp/ngt/rtm/entity/train/parts/EntityVehiclePart.java',
    'src/main/java/jp/ngt/ngtlib/math/NGTMath.java',
    'src/main/java/jp/ngt/ngtlib/math/Vec3.java',
    'src/main/java/jp/ngt/ngtlib/math/Axis.java',
    'src/main/java/jp/ngt/ngtlib/util/NGTUtil.java',
    'src/main/java/jp/ngt/ngtlib/util/NGTUtilClient.java',
    'src/main/java/jp/ngt/ngtlib/util/MCWrapper.java',
    'src/main/java/jp/ngt/ngtlib/util/MCWrapperClient.java',
    'src/main/java/jp/ngt/ngtlib/util/ColorUtil.java',
    'src/main/java/jp/ngt/ngtlib/renderer/NGTTessellator.java',
    'src/main/java/jp/ngt/ngtlib/renderer/GLHelper.java',
    'src/main/java/jp/ngt/rtm/entity/train/util/Formation.java',
    'src/main/java/jp/ngt/rtm/rail/util/RailMap.java',
    'src/main/java/jp/ngt/rtm/rail/util/RailMapBasic.java',
    'src/main/java/jp/ngt/rtm/rail/util/RailPosition.java',
    'src/main/java/com/portofino/realtrainmodunofficial/entity/TrainEntity.java',
    'src/main/java/com/portofino/realtrainmodunofficial/script/SoundScriptExecutor.java',
]

LOOSEN = {'float': 'double', 'int': 'double', 'long': 'double', 'char': 'String'}
CAST = {'float': '(float) ', 'int': '(int) ', 'long': '(long) ', 'char': 'String.valueOf('}

def split_params(s):
    out, depth, cur = [], 0, ''
    for ch in s:
        if ch in '<([': depth += 1
        elif ch in '>)]:': depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur.strip()); cur = ''
        else:
            cur += ch
    if cur.strip(): out.append(cur.strip())
    return [p for p in out if p]

def parse_methods(src):
    """(start_line, end_line, header_line_index, decl_text, owner_class, is_static, name) を列挙"""
    lines = src.split('\n')
    methods = []
    owner = None
    i = 0
    while i < len(lines):
        line = lines[i]
        cm = re.search(r'(?:class|interface|record|enum)\s+(\w+)', line)
        if cm and 'extends' not in line.split('{')[0][:len(line)]:
            pass
        if cm:
            owner = cm.group(1)
        if re.match(r'\s*(public|protected)\s', line) and '(' in line:
            buf = [line]
            j = i
            while j < len(lines) and ')' not in buf[-1]:
                j += 1
                if j < len(lines): buf.append(lines[j])
            decl = '\n'.join(buf)
            # 本体が直後に開く (abstract/record は除外)
            body_open = decl.rstrip().endswith('{') or (j+1 < len(lines) and lines[j+1].strip().startswith('{'))
            if not body_open:
                i = j + 1
                continue
            mm = re.match(r'\s*(public|protected)\s+((?:static\s+)?(?:final\s+)?)?((?:synchronized\s+)?)?([\w<>\[\], .?]+?)\s+(\w+)\s*\(([^)]*)\)', decl, re.S)
            if mm:
                mods = (mm.group(2) or '') + (mm.group(3) or '')
                is_static = 'static' in mods
                ret = mm.group(4).strip()
                name = mm.group(5)
                params = split_params(mm.group(6))
                if name == owner:  # コンストラクタは除外
                    i = j + 1
                    continue
                # 本体終端を探す
                depth = 0; k = j
                started = False
                while k < len(lines):
                    for ch in lines[k]:
                        if ch == '{': depth += 1; started = True
                        elif ch == '}': depth -= 1
                    if started and depth <= 0 and k >= i:
                        break
                    k += 1
                methods.append(dict(start=i, end=k, name=name, ret=ret,
                                    params=params, is_static=is_static, owner=owner,
                                    decl_lines=lines[i:k+1]))
                i = k
        i += 1
    return methods

def loosen(params):
    out, changed = [], False
    for p in params:
        pm = re.match(r'((?:final\s+)?)((?:[\w<>\[\], .?]+?))\s+(\w+)$', p)
        if not pm:
            out.append(p); continue
        final_kw, typ, name = pm.group(1), pm.group(2).strip(), pm.group(3)
        base = typ
        array = ''
        while base.endswith('[]'):
            base = base[:-2].strip(); array += '[]'
        if base in LOOSEN and not array:
            out.append(f'{LOOSEN[base]} {name}'); changed = True
        else:
            out.append(p)
    return out, changed

def gen_body(m, loosened_params, decl_text):
    casts = []
    orig_types = [re.match(r'((?:final\s+)?)((?:[\w<>\[\], .?]+?))\s+(\w+)$', p) for p in m['params']]
    for (op, lp) in zip(orig_types, loosened_params):
        if op is None:
            casts.append(lp.split()[-1]); continue
        base = op.group(2).strip()
        name = op.group(3)
        if base in ('float',): casts.append(f'(float) {name}')
        elif base == 'int': casts.append(f'(int) {name}')
        elif base == 'long': casts.append(f'(long) {name}')
        elif base == 'char': casts.append(f'String.valueOf({name}).charAt(0)')
        else: casts.append(name)
    call = ('%s.' % m['owner'] if m['is_static'] else 'this.') + m['name'] + '(' + ', '.join(casts) + ');'
    if m['ret'] == 'void':
        return '    ' + call
    return '    return ' + call

def process(path, dry=False):
    src = open(path, encoding='utf-8').read()
    methods = parse_methods(src)
    gen = []
    existing = re.findall(r'\b(\w+)\s*\(([^)]*)\)', src)
    def exists(name, loosened):
        for (n, p) in existing:
            if n != name: continue
            ps = [re.sub(r'\s+', ' ', x) for x in split_params(p)]
            if ps == loosened:
                return True
            # Java 型の緩急のみ違う場合も重複とみなす (double と float など)
            def norm(x):
                return re.sub(r'^(final )?', '', x).strip()
            if len(ps) == len(loosened):
                ok = True
                for a, b in zip(ps, loosened):
                    an = norm(a).split(' '); bn = norm(b).split(' ')
                    at, bn_t = ' '.join(an[:-1]), ' '.join(bn[:-1])
                    if at in ('float','int','long','double') and bn_t in ('float','int','long','double'):
                        continue
                    if at != bn_t: ok = False; break
                if ok: return True
        return False
    for m in methods:
        loosened, changed = loosen(m['params'])
        if not changed: continue
        if exists(m['name'], loosened): continue
        sig_params = ', '.join(loosened)
        mods = ('static ' if m['is_static'] else '')
        header = f'    {mods}public {m["ret"]} {m["name"]}({sig_params}) {{'
        body = gen_body(m, loosened, None)
        gen.append('    /** ★GraalJS 互換オーバーロード (生成) */\n' + header + '\n' + body + '\n    }')
    if not gen: return 0
    block = '\n\n    // ==== ★GraalJS 互換オーバーロード (tools/generate_graaljs_overloads.py が生成) ====\n' + '\n\n'.join(gen) + '\n'
    # クラス最後の "}" の前に挿入
    a = src.rindex('\n}')
    new = src[:a] + '\n' + block + src[a:]
    if not dry: open(path, 'w', encoding='utf-8').write(new)
    return len(gen)

if __name__ == '__main__':
    dry = '--dry' in sys.argv
    total = 0
    for t in TARGETS:
        fab = t.replace('src/main/java', 'fabric/src/main/java', 1)
        for p in (t, fab):
            if not os.path.exists(p):
                continue
            n = process(p, dry)
            if n: print(f'{p}: {n} overloads')
            total += n
    print('TOTAL', total)
