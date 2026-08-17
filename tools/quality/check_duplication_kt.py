#!/usr/bin/env python3
"""
check_duplication_kt.py — app/client handler block-clone report (MUD-036).

Jam-style sliding-window clone detector on prod handler Kotlin only:
  app/**/handlers/**/*.kt  and  client/**/handlers/**/*.kt
  (src/main; skip build/). Intra-app / intra-client clones are out of v1.

Always exits 0 (report_only). Verify owns warn vs hard (v1 = always warn;
DUP_BLOCK_E is reserved and not emitted). Does not write dod-summary.json.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set, Tuple

DEFAULT_WINDOW = 10
DEFAULT_MIN_BLOCK = 10
FINDINGS_CAP = 50
TICKET_RE = re.compile(r"^MUD-\d+[a-z]?$")
SKIP_DIR_SEGMENTS = frozenset(
    {
        "build",
        "buildSrc",
        ".git",
        ".gradle",
        "node_modules",
        "tmp",
        ".idea",
        ".grok",
    }
)
PACKAGE_OR_IMPORT_RE = re.compile(r"^(package|import)\b")
FILE_SUPPRESS_START_RE = re.compile(r"^@file:Suppress\b")


def load_config(path: Path) -> Dict[str, Any]:
    cfg: Dict[str, Any] = {
        "window": DEFAULT_WINDOW,
        "min_block_lines": DEFAULT_MIN_BLOCK,
        "allowlist": [],
    }
    if not path.is_file():
        return cfg
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    if not isinstance(data, dict):
        return cfg
    if "window" in data:
        try:
            w = int(data["window"])
            if w > 0:
                cfg["window"] = w
        except (TypeError, ValueError):
            pass
    if "min_block_lines" in data:
        try:
            m = int(data["min_block_lines"])
            if m > 0:
                cfg["min_block_lines"] = m
        except (TypeError, ValueError):
            pass
    if isinstance(data.get("allowlist"), list):
        cfg["allowlist"] = data["allowlist"]
    return cfg


def is_handler_main_kt(rel: str) -> bool:
    """True for app|client **/src/main/**/handlers/**/*.kt (posix slashes)."""
    rel = rel.replace("\\", "/")
    if not rel.endswith(".kt"):
        return False
    parts = [p for p in rel.split("/") if p and p != "."]
    if not parts or parts[0] not in ("app", "client"):
        return False
    if any(seg in SKIP_DIR_SEGMENTS for seg in parts):
        return False
    if "src" not in parts or "handlers" not in parts:
        return False
    si = parts.index("src")
    if si + 1 >= len(parts) or parts[si + 1] != "main":
        return False
    hi = parts.index("handlers")
    return hi > si + 1


def side_of(rel: str) -> Optional[str]:
    rel = rel.replace("\\", "/")
    if rel.startswith("app/"):
        return "app"
    if rel.startswith("client/"):
        return "client"
    return None


def discover_handler_files(root: Path) -> Tuple[List[Path], List[Path]]:
    """Return (app_files, client_files) under --root."""
    root = root.resolve()
    app_files: List[Path] = []
    client_files: List[Path] = []
    skip_dirs = set(SKIP_DIR_SEGMENTS)
    for top in ("app", "client"):
        base = root / top
        if not base.is_dir():
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            p = Path(dirpath)
            try:
                rel = p.relative_to(root)
            except ValueError:
                dirnames[:] = []
                continue
            parts = rel.parts
            if any(seg in skip_dirs for seg in parts):
                dirnames[:] = []
                continue
            if "src" in parts:
                si = parts.index("src")
                if si + 1 < len(parts) and parts[si + 1] != "main":
                    dirnames[:] = []
                    continue
            dirnames[:] = [d for d in dirnames if d not in skip_dirs]
            if parts and parts[-1] == "src":
                dirnames[:] = [d for d in dirnames if d == "main"]
            for name in filenames:
                if not name.endswith(".kt"):
                    continue
                fp = p / name
                try:
                    rel_s = "/".join(fp.relative_to(root).parts)
                except ValueError:
                    continue
                if not is_handler_main_kt(rel_s):
                    continue
                if rel_s.startswith("app/"):
                    app_files.append(fp)
                else:
                    client_files.append(fp)
    app_files.sort(key=lambda x: str(x.relative_to(root)))
    client_files.sort(key=lambda x: str(x.relative_to(root)))
    return app_files, client_files


def rel_path(root: Path, fp: Path) -> str:
    try:
        return str(fp.resolve().relative_to(root.resolve())).replace("\\", "/")
    except ValueError:
        return str(fp).replace("\\", "/")


def strip_comments(src: str) -> str:
    """Remove // and /* */ comments; keep newlines so line structure remains."""
    out: List[str] = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            i += 2
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            i += 2
            while i < n - 1 and not (src[i] == "*" and src[i + 1] == "/"):
                if src[i] == "\n":
                    out.append("\n")
                i += 1
            if i < n - 1:
                i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def strip_file_suppress(src: str) -> str:
    """Drop @file:Suppress(...) including a balanced parenthetical (may span lines)."""
    out: List[str] = []
    i = 0
    n = len(src)
    needle = "@file:Suppress"
    while i < n:
        if src.startswith(needle, i):
            j = i + len(needle)
            while j < n and src[j] in " \t":
                j += 1
            if j < n and src[j] == "(":
                depth = 0
                while j < n:
                    if src[j] == "(":
                        depth += 1
                    elif src[j] == ")":
                        depth -= 1
                        if depth == 0:
                            j += 1
                            break
                    elif src[j] == "\n":
                        out.append("\n")
                    j += 1
                i = j
                continue
        out.append(src[i])
        i += 1
    return "".join(out)


def normalize_lines(src: str) -> List[str]:
    """Comment-strip, drop package/import/@file:Suppress, drop blanks, collapse ws."""
    text = strip_file_suppress(strip_comments(src))
    lines: List[str] = []
    for raw in text.splitlines():
        collapsed = " ".join(raw.split())
        if not collapsed:
            continue
        if PACKAGE_OR_IMPORT_RE.match(collapsed):
            continue
        if FILE_SUPPRESS_START_RE.match(collapsed):
            # Unbalanced leftover — skip the annotation line itself.
            continue
        lines.append(collapsed)
    return lines


def window_digest(lines: Sequence[str]) -> str:
    payload = "\n".join(lines).encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


@dataclass
class FileNorm:
    rel: str
    side: str
    lines: List[str]


def read_norm(root: Path, fp: Path) -> FileNorm:
    rel = rel_path(root, fp)
    side = side_of(rel) or "app"
    try:
        raw = fp.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        raw = fp.read_text(encoding="utf-8", errors="replace")
    return FileNorm(rel=rel, side=side, lines=normalize_lines(raw))


def parse_allowlist(raw: Iterable[Any]) -> Set[Tuple[str, str]]:
    """Honor only entries with app+client paths and a valid burn-down ticket."""
    out: Set[Tuple[str, str]] = set()
    for entry in raw:
        if not isinstance(entry, dict):
            sys.stderr.write(
                "check_duplication_kt: allowlist entry ignored (not an object)\n"
            )
            continue
        app = str(entry.get("app") or "").replace("\\", "/").strip()
        client = str(entry.get("client") or "").replace("\\", "/").strip()
        ticket = str(entry.get("ticket") or "").strip()
        if not app or not client:
            sys.stderr.write(
                "check_duplication_kt: allowlist entry ignored (need app+client)\n"
            )
            continue
        if not TICKET_RE.match(ticket):
            sys.stderr.write(
                f"check_duplication_kt: allowlist {app} ↔ {client} missing valid "
                f"ticket (MUD-\\d+); ignored\n"
            )
            continue
        out.add((app, client))
    return out


def collect_windows(
    files: Sequence[FileNorm], window: int
) -> Dict[str, List[Tuple[str, int]]]:
    """hash -> [(rel, start_idx), ...]"""
    index: Dict[str, List[Tuple[str, int]]] = {}
    if window < 1:
        return index
    for fn in files:
        n = len(fn.lines)
        if n < window:
            continue
        for i in range(0, n - window + 1):
            digest = window_digest(fn.lines[i : i + window])
            index.setdefault(digest, []).append((fn.rel, i))
    return index


def merge_adjacent(starts: List[int], window: int) -> int:
    """
    Merge adjacent window starts into regions; return max unique line count
    covered by any single region (and, if multiple, the union size is not
    used — one finding per pair uses the largest merged block).
    """
    if not starts:
        return 0
    uniq = sorted(set(starts))
    best = 0
    run_start = uniq[0]
    prev = uniq[0]
    for s in uniq[1:]:
        if s == prev + 1:
            prev = s
            continue
        best = max(best, prev + window - run_start)
        run_start = s
        prev = s
    best = max(best, prev + window - run_start)
    return best


def find_clones(
    app_files: Sequence[FileNorm],
    client_files: Sequence[FileNorm],
    window: int,
    min_block: int,
    allow: Set[Tuple[str, str]],
) -> Tuple[List[Dict[str, Any]], int]:
    """
    Return (findings, pair_count_before_cap).
    A clone is reported only when the same window hash appears on both sides.
    """
    app_idx = collect_windows(app_files, window)
    client_idx = collect_windows(client_files, window)

    # pair -> list of app-side window starts that matched some client window
    pair_starts: Dict[Tuple[str, str], List[int]] = {}
    for digest, app_hits in app_idx.items():
        client_hits = client_idx.get(digest)
        if not client_hits:
            continue
        for app_rel, a_start in app_hits:
            for client_rel, _c_start in client_hits:
                key = (app_rel, client_rel)
                if key in allow:
                    continue
                pair_starts.setdefault(key, []).append(a_start)

    findings: List[Dict[str, Any]] = []
    for (app_rel, client_rel), starts in pair_starts.items():
        metric = merge_adjacent(starts, window)
        if metric < min_block:
            continue
        findings.append(
            {
                "code": "DUP_BLOCK_W",
                "path": app_rel,
                "metric": metric,
                "limit": min_block,
                "remediation": (
                    f"clone of {client_rel} ({metric} lines); "
                    "extract shared apply or thin one side — "
                    "do not merge in MUD-036"
                ),
            }
        )

    findings.sort(key=lambda r: (-int(r["metric"]), str(r["path"]), str(r["remediation"])))
    pair_count = len(findings)
    return findings[:FINDINGS_CAP], pair_count


def build_envelope(
    root: Path,
    cfg_path: Path,
    files_scanned: int,
    pair_count: int,
    window: int,
    min_block: int,
    findings: List[Dict[str, Any]],
) -> Dict[str, Any]:
    return {
        "tool": "check_duplication_kt",
        "exit_policy": "report_only",
        "root": str(root),
        "config": str(cfg_path),
        "summary": {
            "files_scanned": files_scanned,
            "pairs": pair_count,
            "findings_warn": pair_count,
            "window": window,
            "min_block": min_block,
        },
        "findings": findings,
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=(
            "App/client handler block-clone report (MUD-036). "
            "Always exits 0. Scans app/**/handlers and client/**/handlers only. "
            "DUP_BLOCK_W only (DUP_BLOCK_E reserved). Verify owns hard policy."
        )
    )
    p.add_argument("--root", default=".", help="Repo root (default: .)")
    p.add_argument(
        "--config",
        default="config/quality/duplication_kt.json",
        help="Config JSON (default: config/quality/duplication_kt.json)",
    )
    p.add_argument(
        "--json-out",
        default=None,
        help="Write full JSON report to this path",
    )
    p.add_argument(
        "--quiet-stdout",
        action="store_true",
        help="One-line summary on stdout (still writes --json-out)",
    )
    return p.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve()
    cfg_path = Path(args.config)
    if not cfg_path.is_absolute():
        cand = (root / args.config).resolve()
        cfg_path = cand if cand.is_file() else cfg_path.resolve()

    cfg = load_config(cfg_path)
    window = int(cfg["window"])
    min_block = int(cfg["min_block_lines"])
    allow = parse_allowlist(cfg.get("allowlist") or [])

    app_paths, client_paths = discover_handler_files(root)
    app_norms = [read_norm(root, fp) for fp in app_paths]
    client_norms = [read_norm(root, fp) for fp in client_paths]
    files_scanned = len(app_norms) + len(client_norms)

    findings, pair_count = find_clones(
        app_norms, client_norms, window, min_block, allow
    )
    envelope = build_envelope(
        root, cfg_path, files_scanned, pair_count, window, min_block, findings
    )

    text = json.dumps(envelope, indent=2, ensure_ascii=True) + "\n"
    if args.json_out:
        out = Path(args.json_out)
        if not out.is_absolute():
            out = (root / out).resolve()
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")

    if args.quiet_stdout:
        s = envelope["summary"]
        sys.stdout.write(
            f"check_duplication_kt: files={s['files_scanned']} "
            f"pairs={s['pairs']} findings_warn={s['findings_warn']} "
            f"window={s['window']} min_block={s['min_block']}\n"
        )
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
