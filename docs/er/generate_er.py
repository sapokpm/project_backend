#!/usr/bin/env python3
"""
Draws the ER diagram from the real schema (src/main/resources/db/migration/V1__schema.sql), so the picture
cannot drift from the database. Writes docs/er-diagram.png and docs/er-diagram.puml (PlantUML source).

Run from the repository root:   python3 docs/er/generate_er.py        (needs Pillow: pip install pillow)
"""
import re
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SQL = (ROOT / "src/main/resources/db/migration/V1__schema.sql").read_text()

# ---------------------------------------------------------------- parse the schema
tables = {}  # name -> list of dict(name, type, pk, fk(table), unique, notnull)
for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\);", SQL, re.S):
    name, body = m.group(1), m.group(2)
    cols, table_pk = [], []
    for raw in body.split("\n"):
        line = raw.strip().rstrip(",")
        if not line or line.startswith("--"):
            continue
        if line.upper().startswith("PRIMARY KEY"):
            table_pk = [c.strip() for c in re.search(r"\((.*)\)", line).group(1).split(",")]
            continue
        if re.match(r"(CONSTRAINT|CHECK|UNIQUE)\b", line, re.I):
            continue
        cm = re.match(r"(\w+)\s+(\w+(?:\s*\([^)]*\))?)(.*)", line)
        if not cm:
            continue
        col, typ, rest = cm.group(1), re.sub(r"\s+", "", cm.group(2)).upper(), cm.group(3)
        fk = re.search(r"REFERENCES\s+(\w+)", rest)
        cols.append(dict(name=col, type=typ.replace("BIGSERIAL", "BIGINT"), pk="PRIMARY KEY" in rest.upper(),
                         fk=fk.group(1) if fk else None, unique="UNIQUE" in rest.upper(),
                         notnull="NOT NULL" in rest.upper()))
    for c in cols:
        if c["name"] in table_pk:
            c["pk"] = True
    tables[name] = cols

relations = []  # (child table, child column, parent table)
for t, cols in tables.items():
    for c in cols:
        if c["fk"]:
            relations.append((t, c["name"], c["fk"]))

# ---------------------------------------------------------------- PlantUML source
puml = ["@startuml", "hide circle", "skinparam linetype ortho", "skinparam shadowing false", ""]
for t, cols in tables.items():
    puml.append(f"entity {t} {{")
    for c in cols:
        if c["pk"]:
            puml.append(f"  * {c['name']} : {c['type']} <<PK>>")
    puml.append("  --")
    for c in cols:
        if not c["pk"]:
            tag = " <<FK>>" if c["fk"] else ""
            puml.append(f"  {'*' if c['notnull'] else ''} {c['name']} : {c['type']}{tag}".replace("  *  ", "  * "))
    puml.append("}\n")
for child, col, parent in relations:
    puml.append(f"{parent} ||--o{{ {child} : {col}")
puml.append("@enduml")
(ROOT / "docs/er-diagram.puml").write_text("\n".join(puml) + "\n")

# ---------------------------------------------------------------- picture
FONT_PATHS = ["/System/Library/Fonts/Menlo.ttc", "/System/Library/Fonts/Monaco.ttf",
              "/Library/Fonts/Arial.ttf", "/System/Library/Fonts/Supplemental/Courier New.ttf"]


def font(size, bold=False):
    for p in FONT_PATHS:
        try:
            return ImageFont.truetype(p, size, index=1 if bold and p.endswith(".ttc") else 0)
        except Exception:
            continue
    return ImageFont.load_default()


F, FB, FT = font(15), font(15, True), font(24, True)
ROW, HEAD, PAD, COLW, GAP = 21, 30, 8, 410, 170

LAYOUT = [  # columns, left to right
    ["users", "addresses", "refresh_tokens", "delivery_options"],
    ["carts", "cart_items", "wishlists", "promo_codes"],
    ["orders", "order_items", "payments", "promo_code_usages"],
    ["products", "product_variants", "product_images", "product_specifications"],
    ["categories", "brands", "reviews", "promo_code_categories", "promo_code_products"],
]
placed = {t for col in LAYOUT for t in col}
missing = set(tables) - placed
assert not missing, f"tables missing from the layout: {missing}"

pos = {}  # table -> (x, y, h)
margin_top, margin_x, vgap = 110, 80, 60
width = margin_x * 2 + len(LAYOUT) * COLW + (len(LAYOUT) - 1) * GAP
col_heights = []
for ci, col in enumerate(LAYOUT):
    x, y = margin_x + ci * (COLW + GAP), margin_top
    for t in col:
        h = HEAD + len(tables[t]) * ROW + PAD
        pos[t] = (x, y, h)
        y += h + vgap
    col_heights.append(y)
height = max(col_heights) + 120

img = Image.new("RGB", (width, height), "white")
d = ImageDraw.Draw(img)
d.text((margin_x, 30), "Electronics Store - ER diagram", font=FT, fill=(20, 20, 20))
d.text((margin_x, 66), "Generated from V1__schema.sql.  PK = primary key (gold)   FK = foreign key (blue, line to the parent's id)   "
       "1 -- N: the parent is the end with the dot-less side",
       font=F, fill=(90, 90, 90))

PALETTE = [(31, 119, 180), (214, 39, 40), (44, 160, 44), (148, 103, 189), (255, 127, 14), (23, 190, 207),
           (140, 86, 75), (227, 119, 194), (127, 127, 127), (188, 189, 34)]
col_of = {t: ci for ci, col in enumerate(LAYOUT) for t in col}
row_y = lambda t, name: pos[t][1] + HEAD + [c["name"] for c in tables[t]].index(name) * ROW + ROW // 2

# relation lines first, so boxes are drawn on top of them
lane = {}
for i, (child, col, parent) in enumerate(relations):
    color = PALETTE[i % len(PALETTE)]
    cx, px = pos[child][0], pos[parent][0]
    y1, y2 = row_y(child, col), row_y(parent, "id" if "id" in [c["name"] for c in tables[parent]] else tables[parent][0]["name"])
    cc, pc = col_of[child], col_of[parent]
    if cc == pc:  # same column: loop around the right-hand side
        k = lane.setdefault(("same", cc), 0); lane[("same", cc)] += 1
        lx = cx + COLW + 14 + k * 9
        pts = [(cx + COLW, y1), (lx, y1), (lx, y2), (px + COLW, y2)]
        end_dir = -1
    elif cc < pc:
        k = lane.setdefault(("gap", cc), 0); lane[("gap", cc)] += 1
        lx = cx + COLW + 18 + (k % 12) * 9
        pts = [(cx + COLW, y1), (lx, y1), (lx, y2), (px, y2)]
        end_dir = 1
    else:
        k = lane.setdefault(("gap", pc), 0); lane[("gap", pc)] += 1
        lx = px + COLW + 18 + (k % 12) * 9
        pts = [(cx, y1), (lx, y1), (lx, y2), (px + COLW, y2)]
        end_dir = -1
    d.line(pts, fill=color, width=2)
    ex, ey = pts[-1]
    # parent end: a bar (exactly one); child end: a dot (many)
    d.line([(ex - 0 if end_dir else ex, ey - 7), (ex, ey + 7)], fill=color, width=3)
    sx, sy = pts[0]
    d.ellipse([sx - 5, sy - 5, sx + 5, sy + 5], fill=color)

for t, (x, y, h) in pos.items():
    d.rectangle([x, y, x + COLW, y + h], fill=(250, 250, 252), outline=(60, 60, 70), width=2)
    d.rectangle([x, y, x + COLW, y + HEAD], fill=(52, 73, 94))
    d.text((x + 10, y + 6), t, font=FB, fill="white")
    for i, c in enumerate(tables[t]):
        ry = y + HEAD + i * ROW
        if c["pk"]:
            d.rectangle([x + 1, ry, x + COLW - 1, ry + ROW], fill=(255, 245, 205))
        elif c["fk"]:
            d.rectangle([x + 1, ry, x + COLW - 1, ry + ROW], fill=(220, 235, 250))
        tag = "PK" if c["pk"] else ("FK" if c["fk"] else "  ")
        d.text((x + 8, ry + 2), tag, font=FB if tag.strip() else F, fill=(120, 80, 0) if c["pk"] else (20, 80, 160))
        d.text((x + 38, ry + 2), c["name"] + ("*" if c["unique"] and not c["pk"] else ""), font=FB if c["pk"] else F, fill=(20, 20, 20))
        tw = d.textlength(c["type"], font=F)
        d.text((x + COLW - 8 - tw, ry + 2), c["type"], font=F, fill=(110, 110, 110))

d.text((margin_x, height - 70), "* = unique column.  Many-to-many links: promo_code_categories, promo_code_products.  "
       f"{len(tables)} tables, {len(relations)} relations.", font=F, fill=(90, 90, 90))
img.save(ROOT / "docs/er-diagram.png", optimize=True)
print(f"wrote docs/er-diagram.png ({width}x{height}) and docs/er-diagram.puml: {len(tables)} tables, {len(relations)} relations")
