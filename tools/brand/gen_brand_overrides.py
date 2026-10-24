#!/usr/bin/env python3
"""Genera app/src/main/res/values*/brand_overrides.xml.

Las cadenas de la interfaz viven en la librería tv.own.owntv:core (repo OwnTV_Core). Un recurso con el
mismo nombre en el módulo :app tiene prioridad sobre el de la librería, así que basta con volver a
declarar, en :app, cada <string>/<plurals> que menciona "OwnTV" con la marca nueva.

Uso (desde la raíz del repo, con OwnTV_Core clonado en la versión que fija libs.versions.toml):
    python3 tools/brand/gen_brand_overrides.py ../OwnTV_Core "8K PRO"

Vuelve a ejecutarlo cada vez que subas la versión de core.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOKEN = re.compile(r"Own ?TV", re.IGNORECASE)
ELEMENT = re.compile(r"<(string|plurals)\b([^>]*)>(.*?)</\1>", re.DOTALL)
NAME = re.compile(r'name="([^"]+)"')
# Identificadores y nombres de archivo que no son texto de marca visible.
KEEP = re.compile(r"owntv-backup\.own")


def rebrand(body: str, brand: str) -> str:
    out = []
    pos = 0
    for m in KEEP.finditer(body):
        out.append(TOKEN.sub(brand, body[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(TOKEN.sub(brand, body[pos:]))
    return "".join(out)


def main() -> int:
    core = Path(sys.argv[1]).resolve() / "core" / "src" / "main" / "res"
    brand = sys.argv[2] if len(sys.argv) > 2 else "8K PRO"
    xml_brand = brand.replace("&", "&amp;")
    target = ROOT / "app" / "src" / "main" / "res"
    total = 0
    for values_dir in sorted(core.glob("values*")):
        found = []
        for f in sorted(values_dir.glob("*.xml")):
            text = re.sub(r"<!--.*?-->", "", f.read_text(encoding="utf-8"), flags=re.DOTALL)
            for m in ELEMENT.finditer(text):
                kind, attrs, body = m.groups()
                if not TOKEN.search(body):
                    continue
                name = NAME.search(attrs).group(1)
                if name == "app_name":
                    continue  # declarado a mano en values/strings.xml
                found.append(f"    <{kind}{attrs}>{rebrand(body, xml_brand)}</{kind}>")
        out_dir = target / values_dir.name
        out_file = out_dir / "brand_overrides.xml"
        if not found:
            if out_file.exists():
                out_file.unlink()
            continue
        out_dir.mkdir(parents=True, exist_ok=True)
        out_file.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- GENERADO por tools/brand/gen_brand_overrides.py — no editar a mano. -->\n"
            '<resources xmlns:tools="http://schemas.android.com/tools"\n'
            '    xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2"\n'
            '    tools:ignore="MissingTranslation,ExtraTranslation,UnusedResources">\n'
            + "\n".join(found)
            + "\n</resources>\n",
            encoding="utf-8",
        )
        total += len(found)
        print(f"{values_dir.name}: {len(found)}")
    print(f"total: {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
