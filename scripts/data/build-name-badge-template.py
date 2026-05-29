#!/usr/bin/env python3
"""Build the name-badge runtime template by stripping mail-merge machinery
from the human-authored Word source and adding a `«Rolle»` placeholder line.

Source : docs/Avery-Zweckform_L4784_BATLogo.docx
         (Word mail-merge template — committed; no PII.)
Target : services/event-management-service/src/main/resources/templates/
         name-badges-l4784.docx

The Java runtime (ParticipantsDocxExportService, Apache POI XWPF) expects each
badge cell to have THREE plain text-only paragraphs with the visible placeholders
«Name», «Firma», «Rolle» so it can do a straightforward XWPFRun.setText(...)
substitution per attendee. Mail-merge fields (fldChar / instrText / NEXT-record)
need to go; the BAT logo drawing must stay; «Rolle» is new.

Run from the repo root:

    python3 scripts/data/build-name-badge-template.py

Idempotent — re-running the script produces the same output bytes.

Why a committed script instead of "open in Word, Ctrl+Shift+F9, save"?
Reproducibility + PR-reviewability. See memory:feedback-docx-template-prep.
"""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

from lxml import etree

WORDML = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W = "{%s}" % WORDML
NS = {"w": WORDML}

PLACEHOLDER_NAME = "«Name»"            # «Name»
PLACEHOLDER_FIRMA = "«Firma»"          # «Firma»
PLACEHOLDER_ROLLE = "«Rolle»"          # «Rolle»
NEXT_RECORD_TEXT = "«Nächster Datensatz»"  # «Nächster Datensatz»

DEFAULT_SOURCE = Path("docs/Avery-Zweckform_L4784_BATLogo.docx")
DEFAULT_TARGET = Path(
    "services/event-management-service/src/main/resources/templates/"
    "name-badges-l4784.docx"
)


def is_badge_cell(tc: etree._Element) -> bool:
    """True iff this `<w:tc>` carries a `MERGEFIELD "Name"` instruction.

    Gutter cells (the narrow spacer cells between badges) have no fields.
    """
    for instr in tc.iter(W + "instrText"):
        txt = instr.text or ""
        if 'MERGEFIELD "Name"' in txt:
            return True
    return False


def strip_field_machinery(tc: etree._Element) -> None:
    """Drop runs that are pure field markers / instructions, plus the visible
    NEXT-record marker. Visible «Name» / «Firma» text runs survive untouched
    because they carry an actual `<w:t>` child (which isn't in the strip set).

    Field-marker runs to drop (kinds is a subset of `{w:fldChar, w:instrText}`):
      - `<w:r><w:fldChar w:fldCharType="begin|separate|end"/></w:r>`
      - `<w:r><w:instrText> MERGEFIELD "..." </w:instrText></w:r>`

    The drawing run (logo) carries `<w:drawing>` → preserved.
    """
    strip_kinds = {W + "fldChar", W + "instrText"}
    for r in list(tc.iter(W + "r")):
        non_pr_children = [c for c in r if c.tag != W + "rPr"]
        if not non_pr_children:
            continue
        kinds = {c.tag for c in non_pr_children}
        if kinds <= strip_kinds:
            r.getparent().remove(r)
            continue
        # The NEXT-record placeholder renders as «Nächster Datensatz» — strip it.
        for t in r.findall(W + "t"):
            if (t.text or "") == NEXT_RECORD_TEXT:
                r.getparent().remove(r)
                break


def append_rolle_paragraph(tc: etree._Element) -> None:
    """Add a 3rd paragraph below the existing «Firma» line, carrying `«Rolle»`
    as italic 10 pt text — visually distinct from the bold 12 pt name and the
    regular-weight 12 pt company, so it reads as a sub-line on the badge.

    We clone the cell's last paragraph (the «Firma» one) so the new paragraph
    inherits the same `AveryStyle1` paragraph style and the same paragraph-
    level spacing the badge designer chose, then replace its runs with a single
    italic/10 pt run carrying the placeholder text.
    """
    paragraphs = tc.findall(W + "p")
    if not paragraphs:
        return
    last = paragraphs[-1]

    # Build a fresh paragraph element that mirrors `last`'s style but holds
    # only our italic/10pt placeholder run.
    new_p = etree.SubElement(tc, W + "p")
    # Carry over the pStyle (AveryStyle1) so badge formatting stays consistent.
    last_pPr = last.find(W + "pPr")
    if last_pPr is not None:
        new_pPr = etree.SubElement(new_p, W + "pPr")
        for child in last_pPr:
            if child.tag == W + "rPr":
                # Drop the inherited run-property override (bold/24pt) — the
                # «Rolle» run carries its own italic/10pt rPr.
                continue
            new_pPr.append(etree.fromstring(etree.tostring(child)))

    r = etree.SubElement(new_p, W + "r")
    r_pr = etree.SubElement(r, W + "rPr")
    etree.SubElement(r_pr, W + "i")                  # italic
    sz = etree.SubElement(r_pr, W + "sz")
    sz.set(W + "val", "20")                          # 10 pt = 20 half-points
    sz_cs = etree.SubElement(r_pr, W + "szCs")
    sz_cs.set(W + "val", "20")
    t = etree.SubElement(r, W + "t")
    t.text = PLACEHOLDER_ROLLE


def sanitize_document(xml_bytes: bytes) -> tuple[bytes, int]:
    """Return (transformed_xml, badge_count)."""
    # Parse without altering the namespace map — output keeps the original
    # `xmlns:w="..."` prefixes Word expects.
    root = etree.fromstring(xml_bytes)
    badge_count = 0
    for tc in root.iter(W + "tc"):
        if not is_badge_cell(tc):
            continue
        strip_field_machinery(tc)
        append_rolle_paragraph(tc)
        badge_count += 1
    out = etree.tostring(
        root,
        xml_declaration=True,
        encoding="UTF-8",
        standalone=True,
    )
    return out, badge_count


def build_template(source: Path, target: Path) -> int:
    target.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(source, "r") as src:
        with src.open("word/document.xml") as f:
            transformed_xml, badge_count = sanitize_document(f.read())

        # Copy every other ZIP entry verbatim (media, styles, theme, rels, …).
        # ZIP_DEFLATED matches Word's default compression so file size stays
        # in the same ballpark as the source.
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as dst:
            for info in src.infolist():
                if info.filename == "word/document.xml":
                    dst.writestr(info, transformed_xml)
                else:
                    dst.writestr(info, src.read(info.filename))

    return badge_count


def verify_template(target: Path) -> tuple[int, int, int, int]:
    """Sanity-check the generated template. Returns
    (badge_cells, name_placeholders, firma_placeholders, rolle_placeholders).

    Post-sanitization a "badge cell" is one carrying the «Name» placeholder
    text — the original MERGEFIELD instrText has been removed by the strip.
    """
    with zipfile.ZipFile(target, "r") as z:
        doc = etree.fromstring(z.read("word/document.xml"))
    badge_cells = name_count = firma_count = rolle_count = 0
    leftover_next = 0
    for tc in doc.iter(W + "tc"):
        cell_has_name = False
        for t in tc.iter(W + "t"):
            txt = t.text or ""
            if PLACEHOLDER_NAME in txt:
                name_count += 1
                cell_has_name = True
            if PLACEHOLDER_FIRMA in txt:
                firma_count += 1
            if PLACEHOLDER_ROLLE in txt:
                rolle_count += 1
            if NEXT_RECORD_TEXT in txt:
                leftover_next += 1
        if cell_has_name:
            badge_cells += 1
    if leftover_next:
        print(
            f"WARN  {leftover_next} stray «Nächster Datensatz» run(s) survived "
            "the strip pass",
            file=sys.stderr,
        )
    return badge_cells, name_count, firma_count, rolle_count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--target", type=Path, default=DEFAULT_TARGET)
    args = parser.parse_args()

    if not args.source.exists():
        print(f"Source not found: {args.source}", file=sys.stderr)
        return 1

    transformed_count = build_template(args.source, args.target)

    # Post-build verification — fail loudly if the strip/insert pass produced
    # an inconsistent number of placeholders.
    cells, names, firmas, rolles = verify_template(args.target)
    if cells == names == firmas == rolles == transformed_count:
        print(
            f"OK  {args.target}: {cells} badge cells, "
            f"{names}×«Name», {firmas}×«Firma», {rolles}×«Rolle»."
        )
        return 0

    print(
        f"FAIL  Placeholder counts diverge: "
        f"transformed={transformed_count} cells={cells} "
        f"name={names} firma={firmas} rolle={rolles}",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
