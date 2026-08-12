#!/usr/bin/env python3
"""
check_builder_preflight.py — plan/brief token preflight (MUD-033 · Wave Q2 D1/D2).

Estimates tokens as ceil(chars/4) via max(0, (len+3)//4). Raw file text (no fence strip).

Budgets (DESIGN D1/D2):
  plan  — warn 2000 / fail 3500
  brief — warn 1200 / fail 2000

Exit codes:
  0 — all clear (or warn-only with --allow-warn)
  1 — warnings only (no hard fail)
  2 — any hard fail (or missing explicit path)

Default inventory globs (under --root):
  plans/*.md
  tmp/workers/*/PLAN*.md  (excluding *BRIEF*)
  tmp/workers/*/*BRIEF*.md

PATH mode: positional paths check only those files (primary for plan approve).

D7 mandatory-read pack graph is out of scope for v1 (help pointer only).
See docs/BUILDER_PREFLIGHT.md.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

# DESIGN D1/D2 thresholds (tok ≈ chars/4)
PLAN_WARN = 2000
PLAN_FAIL = 3500
BRIEF_WARN = 1200
BRIEF_FAIL = 2000

ROLE_PLAN = "plan"
ROLE_BRIEF = "brief"


@dataclass
class Finding:
    level: str  # clear | warn | fail
    role: str  # plan | brief
    path: str
    tokens: int
    warn: int
    fail: int
    message: str
    code: str = "size"


def token_estimate(text: str) -> int:
    """ceil(len/4) via integer arithmetic; jam / kt convention."""
    n = len(text)
    return max(0, (n + 3) // 4)


def level_for(tok: int, warn: int, fail: int) -> str:
    if tok >= fail:
        return "fail"
    if tok >= warn:
        return "warn"
    return "clear"


def classify_path(path: Path, *, root: Path) -> Optional[str]:
    """Return ROLE_PLAN, ROLE_BRIEF, or None if not a preflight target.

    Priority:
      1. Under plans/ → always plan (titles may contain the word "brief")
      2. Basename *BRIEF* → brief (PLAN_BRIEF.md, IMPL_BRIEF.md, …)
      3. Basename PLAN*.md → plan (worker mirrors)
    """
    name = path.name
    name_upper = name.upper()

    try:
        rel = path.resolve().relative_to(root.resolve())
        parts = rel.parts
    except ValueError:
        parts = path.parts

    # plans/*.md — exclusive plan corpus (not reclassified by "brief" in title)
    if name_upper.endswith(".MD"):
        if len(parts) >= 2 and parts[0] == "plans":
            return ROLE_PLAN
        if path.parent.name == "plans":
            return ROLE_PLAN

    # Worker briefs: *BRIEF* wins before PLAN*
    if "BRIEF" in name_upper and name_upper.endswith(".MD"):
        return ROLE_BRIEF

    # basename PLAN*.md (worker mirrors) not BRIEF
    if name_upper.startswith("PLAN") and name_upper.endswith(".MD"):
        return ROLE_PLAN

    return None


def thresholds_for(role: str) -> Tuple[int, int]:
    if role == ROLE_BRIEF:
        return BRIEF_WARN, BRIEF_FAIL
    return PLAN_WARN, PLAN_FAIL


def check_file(path: Path, *, root: Path, explicit: bool) -> Finding:
    role = classify_path(path, root=root)
    display = str(path)
    try:
        display = str(path.resolve().relative_to(root.resolve()))
    except ValueError:
        pass

    if role is None:
        if explicit:
            return Finding(
                level="fail",
                role="unknown",
                path=display,
                tokens=0,
                warn=0,
                fail=0,
                message="unclassified path (not plan/brief); skipped role",
                code="unclassified",
            )
        # non-explicit skip — should not be called
        return Finding(
            level="clear",
            role="unknown",
            path=display,
            tokens=0,
            warn=0,
            fail=0,
            message="skipped",
            code="skip",
        )

    if not path.is_file():
        return Finding(
            level="fail",
            role=role,
            path=display,
            tokens=0,
            warn=0,
            fail=0,
            message="path missing",
            code="path_missing",
        )

    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return Finding(
            level="fail",
            role=role,
            path=display,
            tokens=0,
            warn=0,
            fail=0,
            message=f"read error: {exc}",
            code="path_missing",
        )

    tok = token_estimate(text)
    warn, fail = thresholds_for(role)
    lvl = level_for(tok, warn, fail)
    return Finding(
        level=lvl,
        role=role,
        path=display,
        tokens=tok,
        warn=warn,
        fail=fail,
        message=f"{role} ≈{tok} tok (warn {warn} / fail {fail})",
        code=f"{role}_size",
    )


def discover_default(root: Path) -> List[Path]:
    """Default inventory: plans + worker PLAN* + worker *BRIEF*."""
    found: List[Path] = []
    seen: set[Path] = set()

    def add(p: Path) -> None:
        try:
            rp = p.resolve()
        except OSError:
            return
        if rp in seen:
            return
        if not p.is_file():
            return
        seen.add(rp)
        found.append(p)

    plans_dir = root / "plans"
    if plans_dir.is_dir():
        for p in sorted(plans_dir.glob("*.md")):
            add(p)

    workers = root / "tmp" / "workers"
    if workers.is_dir():
        for worker_dir in sorted(workers.iterdir()):
            if not worker_dir.is_dir():
                continue
            for p in sorted(worker_dir.glob("PLAN*.md")):
                # *BRIEF* wins: PLAN_BRIEF.md is brief, still included as brief
                add(p)
            for p in sorted(worker_dir.glob("*BRIEF*.md")):
                add(p)

    return found


def format_line(f: Finding) -> str:
    role = f.role.upper() if f.role else "?"
    return f"{f.path}: {role} tok={f.tokens} (W={f.warn}/F={f.fail}) → {f.level}"


def exit_code_for(findings: Sequence[Finding], *, allow_warn: bool) -> int:
    has_fail = any(f.level == "fail" for f in findings)
    has_warn = any(f.level == "warn" for f in findings)
    if has_fail:
        return 2
    if has_warn and not allow_warn:
        return 1
    return 0


def build_json_report(
    findings: Sequence[Finding],
    *,
    exit_code: int,
    root: str,
) -> Dict[str, Any]:
    return {
        "schema_version": 1,
        "tool": "check_builder_preflight",
        "ticket": "MUD-033",
        "root": root,
        "exit_code": exit_code,
        "thresholds": {
            "plan": {"warn": PLAN_WARN, "fail": PLAN_FAIL},
            "brief": {"warn": BRIEF_WARN, "fail": BRIEF_FAIL},
        },
        "summary": {
            "total": len(findings),
            "clear": sum(1 for f in findings if f.level == "clear"),
            "warn": sum(1 for f in findings if f.level == "warn"),
            "fail": sum(1 for f in findings if f.level == "fail"),
        },
        "findings": [asdict(f) for f in findings],
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="check_builder_preflight.py",
        description=(
            "Builder plan/brief token preflight (MUD-033). "
            "Exit 0 clear, 1 warn-only, 2 hard fail. "
            "D7 pack scanner not in v1 — see docs/BUILDER_PREFLIGHT.md."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
thresholds (tok ≈ ceil(chars/4)):
  plan  warn 2000 / fail 3500
  brief warn 1200 / fail 2000

exit codes:
  0  all clear (or warn-only with --allow-warn)
  1  warnings only (no hard fail)
  2  any hard fail (or missing explicit path)

examples:
  python3 tools/quality/check_builder_preflight.py plans/2026-08-12-…-MUD-033-….md
  python3 tools/quality/check_builder_preflight.py --allow-warn
  python3 tools/quality/check_builder_preflight.py --json-out tmp/preflight.json
  ./tools/verify_mud.sh --preflight plans/….md
""".strip(),
    )
    p.add_argument(
        "--root",
        default=".",
        help="repo root (default: .)",
    )
    p.add_argument(
        "paths",
        nargs="*",
        help="optional paths to check (PATH mode); omit for default inventory",
    )
    p.add_argument(
        "--allow-warn",
        action="store_true",
        help="exit 0 on warn-only (still exit 2 on any fail)",
    )
    p.add_argument(
        "--json-out",
        default=None,
        metavar="PATH",
        help="write JSON report to PATH",
    )
    p.add_argument(
        "--quiet",
        action="store_true",
        help="suppress per-file lines (summary + exit still apply)",
    )
    return p.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve()
    if not root.is_dir():
        print(f"error: root not a directory: {root}", file=sys.stderr)
        return 2

    findings: List[Finding] = []
    if args.paths:
        for raw in args.paths:
            p = Path(raw)
            if not p.is_absolute():
                p = root / p
            # Explicit path: always check; missing → fail
            if not p.exists():
                display = raw
                try:
                    display = str(p.resolve().relative_to(root))
                except ValueError:
                    display = str(p)
                findings.append(
                    Finding(
                        level="fail",
                        role="unknown",
                        path=display,
                        tokens=0,
                        warn=0,
                        fail=0,
                        message="path missing",
                        code="path_missing",
                    )
                )
                continue
            role = classify_path(p, root=root)
            if role is None:
                # Explicit but unclassified: fail with note (approve path should be plan/brief)
                display = raw
                try:
                    display = str(p.resolve().relative_to(root))
                except ValueError:
                    display = str(p)
                findings.append(
                    Finding(
                        level="fail",
                        role="unknown",
                        path=display,
                        tokens=0,
                        warn=0,
                        fail=0,
                        message="unclassified path (expected plan or *BRIEF*)",
                        code="unclassified",
                    )
                )
                continue
            findings.append(check_file(p, root=root, explicit=True))
    else:
        for p in discover_default(root):
            role = classify_path(p, root=root)
            if role is None:
                continue
            findings.append(check_file(p, root=root, explicit=False))

    # Sort for stable output
    findings.sort(key=lambda f: (0 if f.level == "fail" else 1 if f.level == "warn" else 2, f.path))

    code = exit_code_for(findings, allow_warn=args.allow_warn)

    if not args.quiet:
        for f in findings:
            if f.level == "clear" and not args.paths:
                # inventory: only print non-clear by default for noise control?
                # Plan says: "Lines: path: ROLE tok=N (W/F) → level". Print all.
                pass
            print(format_line(f))

    n_clear = sum(1 for f in findings if f.level == "clear")
    n_warn = sum(1 for f in findings if f.level == "warn")
    n_fail = sum(1 for f in findings if f.level == "fail")
    print(
        f"summary: total={len(findings)} clear={n_clear} warn={n_warn} fail={n_fail} "
        f"exit={code}"
        + (" (--allow-warn)" if args.allow_warn and code == 0 and n_warn else "")
    )

    if args.json_out:
        out = Path(args.json_out)
        if not out.is_absolute():
            out = root / out
        out.parent.mkdir(parents=True, exist_ok=True)
        report = build_json_report(findings, exit_code=code, root=str(root))
        out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    return code


if __name__ == "__main__":
    sys.exit(main())
