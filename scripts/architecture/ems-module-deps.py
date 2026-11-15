# EMS module dependency scan (architecture review 2026-11). Assigns each EMS class to a module and counts cross-module references.
# Naming rules: ems-modules.json (shared with the EMS ArchUnit rules).
# Usage (from services/event-management-service/src/main/java/ch/batbern/events): python3 ems-module-deps.py . /tmp/x/emsdeps.json
import os,re,sys,collections,json
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from ems_modules import strip_comments, load_rules, base_module
root=sys.argv[1]
RULES=load_rules()
cls={}
files={}
for d,_,fs in os.walk(root):
    for f in fs:
        if not f.endswith('.java'): continue
        rel=os.path.relpath(os.path.join(d,f),root); pkg=rel.split('/')[0] if '/' in rel else ''
        n=f[:-5]; cls[n]=base_module(RULES,pkg,n); files[n]=os.path.join(d,f)
tok=re.compile(r'\b[A-Z][A-Za-z0-9]+\b')
edges=collections.Counter(); ex=collections.defaultdict(set)
for n,path in files.items():
    src=strip_comments(open(path).read())
    m=cls[n]
    for t in set(tok.findall(src)):
        if t in cls and t!=n and cls[t]!=m:
            edges[(m,cls[t])]+=1; ex[(m,cls[t])].add(f"{n}->{t}")
cnt=collections.Counter(cls.values())
print("MODULE SIZES",dict(cnt))
for (a,b),c in sorted(edges.items(),key=lambda x:(x[0][0],-x[1])):
    print(f"{a:13} -> {b:13} {c:4}  e.g. {sorted(ex[(a,b)])[:3]}")
json.dump({'sizes':cnt,'edges':{f'{a}|{b}':c for (a,b),c in edges.items()},'examples':{f'{a}|{b}':sorted(v) for (a,b),v in ex.items()},'classes':cls},open(sys.argv[2],'w'),indent=1)
