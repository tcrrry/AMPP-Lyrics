"""Compare all 1613 vivo seams to the original 1614 host DEX."""
import argparse, json, re, zipfile
from pathlib import Path
from host_dex import dex_classes
p=argparse.ArgumentParser();p.add_argument("apk",type=Path);args=p.parse_args()
contract=json.loads(Path(__file__).with_name("vivo-1613-contracts.json").read_text(encoding="utf8"));classes={}
with zipfile.ZipFile(args.apk) as z:
 for n in z.namelist():
  if re.fullmatch(r"classes\d*\.dex",n): classes.update(dex_classes(z.read(n)))
errors=[];checks=0
for owner,methods in contract["methods"].items():
 c=classes.get("L"+owner.replace(".","/")+";")
 for method in methods:
  checks+=1
  if not c or method not in c["methods"]:errors.append(owner+"."+method)
for owner,fields in {"N6/h$a":{"c":"LN6/g$a;","f":"Z","j":"Z"},"N6/g$c":{"MediaTypeSong":"LN6/g$c;"},"N6/g$a":{"Downloaded":"LN6/g$a;","None":"LN6/g$a;"}}.items():
 c=classes.get("L"+owner+";")
 for name,desc in fields.items():
  checks+=1
  if not c or c["fields"].get(name)!=desc:errors.append(owner+"."+name)
print(json.dumps({"checks":checks,"missing":errors},ensure_ascii=False,indent=2))
if errors:raise SystemExit(1)
