import json, re, sys

# usage: make-index.py <template.html> <flutter_bootstrap.js> <out.index.html> <out.build_config.json>
tpl_path, boot_path, out_html, out_cfg = sys.argv[1:5]

src = open(boot_path, encoding="utf-8").read()
m = re.search(r"_flutter\.buildConfig = (\{.*?\});", src, re.S)
if not m:
    print("ERROR: could not extract _flutter.buildConfig from flutter_bootstrap.js", file=sys.stderr)
    sys.exit(1)
cfg = json.dumps(json.loads(m.group(1)), separators=(",", ":"))

html = open(tpl_path, encoding="utf-8").read()
if "__FLUTTER_BUILD_CONFIG__" not in html:
    print("ERROR: template is missing __FLUTTER_BUILD_CONFIG__ placeholder", file=sys.stderr)
    sys.exit(1)
html = html.replace("__FLUTTER_BUILD_CONFIG__", cfg)
open(out_html, "w", encoding="utf-8").write(html)

open(out_cfg, "w", encoding="utf-8").write(cfg)
print("wrote %s and %s (builds: %s)" % (
    out_html, out_cfg,
    ",".join(b.get("compileTarget", "?") + "/" + b.get("renderer", "?") for b in json.loads(cfg)["builds"] if b)))
