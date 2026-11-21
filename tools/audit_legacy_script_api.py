#!/usr/bin/env python3
"""Inventory reference Java APIs and potential gaps. Static findings are not behavior proofs."""
from pathlib import Path
import argparse
import json
import re
import os
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--reference', type=Path, default=Path('../reference/KaizPatchX-master'))
parser.add_argument('--output', type=Path, default=Path('docs/legacy-script-api-audit.json'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
reference = args.reference.resolve()
comment = re.compile(r'/\*.*?\*/|//[^\n]*', re.S)
java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'

def inventory(directory):
    output = subprocess.check_output([java, str(root/'tools/LegacyApiInventory.java'), str(directory)], text=True)
    classes = {}
    for line in output.splitlines():
        fields = line.split('\t')
        kind, name = fields[:2]
        entry = classes.setdefault(name, {'parent': '', 'methods': set(), 'fields': set()})
        if kind == 'CLASS': entry['parent'] = fields[2] if len(fields) > 2 else ''
        elif kind == 'METHOD': entry['methods'].add((fields[2], int(fields[3])))
        elif kind == 'FIELD': entry['fields'].add(fields[2])
    return classes

reference_api = inventory(reference/'src/main/java')
target_api = {target: inventory((root if target == 'neoforge' else root/'fabric')/'src/main/java') for target in ('neoforge', 'fabric')}

def inherited_members(api, name, seen=None):
    seen = set() if seen is None else seen
    if name not in api or name in seen: return set(), set()
    seen.add(name)
    entry = api[name]
    methods, fields = set(entry['methods']), set(entry['fields'])
    parent = entry['parent'].split('<')[0]
    candidates = [parent, name.rsplit('.', 1)[0]+'.'+parent]
    candidates += [key for key in api if key.rsplit('.', 1)[-1] == parent]
    for candidate in candidates:
        if candidate in api:
            parent_methods, parent_fields = inherited_members(api, candidate, seen)
            methods |= {member for member in parent_methods if member[0] != "<init>"}
            fields |= parent_fields
            break
    return methods, fields

scripts = list((reference/'src/main/resources').rglob('*.js')) + list((root/'src/main/resources').rglob('*.js')) + list((root/'bundled_pack_src').rglob('*.js'))
script_text = comment.sub('', '\n'.join(p.read_text(errors='replace') for p in scripts))
results = []
for classname, original in sorted(reference_api.items()):
    if not classname.startswith(('jp.ngt.', 'jp.kaiz.')): continue
    referenced_name = classname.replace('$', '.') if '$' in classname else classname.rsplit('.', 1)[-1]
    referenced = bool(re.search(r'\b'+re.escape(referenced_name)+r'\b', script_text))
    entry = {'class': classname, 'referenced_by_bundled_scripts': referenced}
    original_methods, original_fields = inherited_members(reference_api, classname)
    for target, api in target_api.items():
        actual_methods, actual_fields = inherited_members(api, classname)
        entry[target] = {'class_present': classname in api,
                         'method_name_and_arity_gaps': [f'{name}/{arity}' for name, arity in sorted(original_methods-actual_methods) if name != '<init>'],
                         'constructor_arity_gaps': sorted(arity for name, arity in original_methods-actual_methods if name == '<init>'),
                         'field_name_gaps': sorted(original_fields-actual_fields)}
    results.append(entry)

summary = {target: {'classes_missing': sum(not item[target]['class_present'] for item in results),
                    'referenced_classes_missing': sum(item['referenced_by_bundled_scripts'] and not item[target]['class_present'] for item in results)} for target in ('neoforge','fabric')}
report = {'scope': 'All jp.ngt and jp.kaiz Java declarations parsed with javac. Public/protected names, arities, constructors, fields, and locally resolvable superclass members. Type signatures, interfaces, remaps, and behavior require separate review; gaps are candidates, not proven incompatibilities.',
          'reference_classes': len(results), 'reference_scripts': len(scripts), 'summary': summary, 'classes': results}
output = args.output if args.output.is_absolute() else root/args.output
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({k: report[k] for k in ('reference_classes','reference_scripts','summary')}, indent=2))
print('Missing classes named in reference scripts:')
for item in results:
    if item['referenced_by_bundled_scripts'] and not item['neoforge']['class_present']:
        print(item['class'])
