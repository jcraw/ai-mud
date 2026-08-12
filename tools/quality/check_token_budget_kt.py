#!/usr/bin/env python3
"""
check_token_budget_kt.py — Kotlin token/structure report-only checker (MUD-028–031).

Scans prod `*/src/main/**/*.kt` under --root (full-repo, or path-scoped via
--files / --git-diff). Estimates tokens as ceil(chars/4) via max(0, (len+3)//4).
Function spans and structure metrics are **heuristics** (not a full Kotlin parse).
Always exits 0 (report-only); verify owns hard-on-touched policy (MUD-031).

Overrides in config require burn-down ticket (MUD-\\d+); ignored for new/Added
paths (anti same-PR grandfather). Override error-tier uses metric > limit so
measured grandfather sizes hold; global thresholds stay metric >= limit.

Does not write/merge tmp/dod-summary.json (verify owns that; MUD-030/031).
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

# Default caps if config missing keys
DEFAULT_THRESHOLDS: Dict[str, Any] = {
    "tokens": {
        "file": {"warn": 2000, "error": 2500},
        "function": {"warn": 200, "error": 250},
    },
    "structure": {
        "file_loc": {"warn": 700, "error": 1100},
        "fn_loc": {"warn": 55, "error": 90},
        "cyclo": {"warn": 10, "error": 16},
        "cognitive": {"warn": 15, "error": 25},
    },
    "overrides": {},
}

# Cap noisy per-file function findings (file-level always kept).
MAX_FN_FINDINGS_PER_FILE = 15

# Directory segments excluded from prod scans (align with discover_kt_files).
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

# Default git base for --git-diff (MUD-029). Override with --git-base.
DEFAULT_GIT_BASE = "origin/master"

# Burn-down ticket id required on each overrides{} entry (MUD-031).
# Allow optional letter suffix for Wave Q3 children (e.g. MUD-034a).
TICKET_RE = re.compile(r"^MUD-\d+[a-z]?$")

# Kotlin function declaration start (heuristic — not PSI).
# Matches: fun name, suspend fun, override fun, private/internal/protected/public fun,
# operator fun, inline/crossinline/noinline fun, actual/expect fun, fun <T> name
FN_DECL_RE = re.compile(
    r"(?m)^[ \t]*"
    r"(?:(?:public|private|protected|internal|open|final|override|abstract|"
    r"inline|crossinline|noinline|tailrec|operator|infix|external|"
    r"actual|expect|suspend)\s+)*"
    r"fun\s+"
    r"(?:<[^>]*>\s*)?"  # type params (simple, non-nested)
    r"(?:`([^`]+)`|([A-Za-z_][\w]*))"  # name (backtick or plain)
)

# Keywords / operators for complexity heuristics
IF_RE = re.compile(r"\bif\b")
WHEN_RE = re.compile(r"\bwhen\b")
FOR_RE = re.compile(r"\bfor\b")
WHILE_RE = re.compile(r"\bwhile\b")
CATCH_RE = re.compile(r"\bcatch\b")
ELSE_IF_RE = re.compile(r"\belse\s+if\b")
AND_RE = re.compile(r"&&")
OR_RE = re.compile(r"\|\|")
# Branch-ish for cognitive nesting walk
BRANCH_OPEN_RE = re.compile(
    r"\b(if|when|for|while|catch|else)\b|&&|\|\|"
)


def token_estimate(text: str) -> int:
    """ceil(len/4) via integer arithmetic; jam / OpenClaw convention."""
    n = len(text)
    return max(0, (n + 3) // 4)


def load_config(path: Path) -> Dict[str, Any]:
    if not path.is_file():
        return json.loads(json.dumps(DEFAULT_THRESHOLDS))
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    # Merge shallow defaults for missing keys
    out = json.loads(json.dumps(DEFAULT_THRESHOLDS))
    if "tokens" in data:
        out["tokens"].update(
            {k: v for k, v in data["tokens"].items() if isinstance(v, dict)}
        )
    if "structure" in data:
        out["structure"].update(
            {k: v for k, v in data["structure"].items() if isinstance(v, dict)}
        )
    if "overrides" in data and isinstance(data["overrides"], dict):
        out["overrides"] = data["overrides"]
    return out


def is_prod_main_kt(rel: str) -> bool:
    """True if relative path is prod */src/main/**/*.kt (posix slashes)."""
    rel = rel.replace("\\", "/")
    if not rel.endswith(".kt"):
        return False
    parts = [p for p in rel.split("/") if p and p != "."]
    if any(seg in SKIP_DIR_SEGMENTS for seg in parts):
        return False
    if "src" not in parts:
        return False
    si = parts.index("src")
    if si + 1 >= len(parts) or parts[si + 1] != "main":
        return False
    # Exclude src/test (already handled by main check) and nested junk
    return True


def discover_kt_files(root: Path) -> List[Path]:
    """Prod Kotlin under */src/main/**/*.kt; exclude build/, buildSrc/, src/test."""
    results: List[Path] = []
    root = root.resolve()
    skip_dirs = set(SKIP_DIR_SEGMENTS)
    for dirpath, dirnames, filenames in os.walk(root):
        p = Path(dirpath)
        try:
            rel = p.relative_to(root)
        except ValueError:
            dirnames[:] = []
            continue
        parts = rel.parts
        # Prune non-prod / heavy trees
        if any(seg in skip_dirs for seg in parts):
            dirnames[:] = []
            continue
        # Only walk under src/main once we enter src/
        if "src" in parts:
            si = parts.index("src")
            if si + 1 < len(parts) and parts[si + 1] != "main":
                dirnames[:] = []
                continue
        dirnames[:] = [d for d in dirnames if d not in skip_dirs]
        # If current dir is .../src, only keep main
        if parts and parts[-1] == "src":
            dirnames[:] = [d for d in dirnames if d == "main"]

        for name in filenames:
            if not name.endswith(".kt"):
                continue
            fp = p / name
            try:
                rparts = fp.relative_to(root).parts
            except ValueError:
                continue
            rel_s = "/".join(rparts)
            if not is_prod_main_kt(rel_s):
                continue
            results.append(fp)
    results.sort(key=lambda x: str(x.relative_to(root)))
    return results


def normalize_paths(root: Path, raws: Sequence[str]) -> List[Path]:
    """
    Resolve paths under --root; reject escapes outside root; keep existing
    prod main `.kt` only. Missing/non-prod/outside paths are skipped (no fail).
    """
    root = root.resolve()
    seen: set = set()
    out: List[Path] = []
    for raw in raws:
        if not raw or not str(raw).strip():
            continue
        p = Path(raw)
        if not p.is_absolute():
            p = root / p
        try:
            rp = p.resolve()
        except OSError:
            continue
        try:
            rel = rp.relative_to(root)
        except ValueError:
            sys.stderr.write(
                f"check_token_budget_kt: skip path outside root: {raw}\n"
            )
            continue
        rel_s = str(rel).replace("\\", "/")
        if not is_prod_main_kt(rel_s):
            continue
        if not rp.is_file():
            continue
        if rel_s in seen:
            continue
        seen.add(rel_s)
        out.append(rp)
    out.sort(key=lambda x: str(x.relative_to(root)))
    return out


def _git_rev_ok(root: Path, ref: str) -> bool:
    r = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", ref],
        capture_output=True,
        text=True,
    )
    return r.returncode == 0


def resolve_git_base(root: Path, preferred: str) -> Tuple[Optional[str], Optional[str]]:
    """
    Pick a usable git base. Tries preferred, then master, then HEAD~1.
    Returns (resolved_ref, optional_one_line_warning). Missing all → (None, warn).
    """
    root = root.resolve()
    ordered: List[str] = []
    for cand in (preferred, "master", "HEAD~1"):
        if cand not in ordered:
            ordered.append(cand)
    for base in ordered:
        if _git_rev_ok(root, base):
            warn = None
            if base != preferred:
                warn = (
                    f"check_token_budget_kt: git base {preferred!r} missing; "
                    f"using {base!r}"
                )
            return base, warn
    warn = (
        f"check_token_budget_kt: no usable git base "
        f"(tried {', '.join(ordered)}); empty touch set"
    )
    return None, warn


def git_touched_paths(
    root: Path, base: str
) -> Tuple[List[str], Optional[str], Optional[str]]:
    """
    Collect touched paths via `git diff --name-only --diff-filter=ACMR <base>`.
    Untracked files are NOT included (pass --files for new untracked .kt).
    Returns (rel_paths, resolved_base, warning_or_None). On git failure: empty + warn.
    """
    root = root.resolve()
    resolved, warn = resolve_git_base(root, base)
    if warn:
        sys.stderr.write(warn + "\n")
    if resolved is None:
        return [], None, warn

    r = subprocess.run(
        [
            "git",
            "-C",
            str(root),
            "diff",
            "--name-only",
            "--diff-filter=ACMR",
            resolved,
        ],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        msg = (
            f"check_token_budget_kt: git diff failed (rc={r.returncode}); "
            "empty touch set"
        )
        sys.stderr.write(msg + "\n")
        if r.stderr and r.stderr.strip():
            sys.stderr.write(r.stderr.strip() + "\n")
        return [], resolved, msg

    paths = [
        line.strip().replace("\\", "/")
        for line in r.stdout.splitlines()
        if line.strip()
    ]
    return paths, resolved, warn


def resolve_scan_files(
    root: Path,
    files_args: Optional[Sequence[str]],
    git_diff: bool,
    git_base: str,
) -> Tuple[List[Path], Dict[str, Any]]:
    """
    Resolve the file list for analysis.

    - Neither --files nor --git-diff → full-repo discover (MUD-028 compat).
    - Either or both → union of inputs, then prod-main filter (scope=touched).
    """
    root = root.resolve()
    use_files = bool(files_args)
    use_git = bool(git_diff)

    if not use_files and not use_git:
        files = discover_kt_files(root)
        return files, {
            "scope": "full",
        }

    raw_paths: List[str] = []
    meta: Dict[str, Any] = {"scope": "touched"}

    if use_files:
        raw_paths.extend(str(p) for p in files_args or [])

    if use_git:
        gpaths, resolved_base, _warn = git_touched_paths(root, git_base)
        raw_paths.extend(gpaths)
        meta["git_base_requested"] = git_base
        if resolved_base is not None:
            meta["git_base"] = resolved_base

    # Unique inputs (preserve order) for count
    seen: set = set()
    unique_raw: List[str] = []
    for r in raw_paths:
        key = str(r).replace("\\", "/")
        if key in seen:
            continue
        seen.add(key)
        unique_raw.append(key)

    meta["touched_input_count"] = len(unique_raw)
    files = normalize_paths(root, unique_raw)
    meta["touched_prod_kt_count"] = len(files)
    return files, meta


def strip_strings_and_comments(src: str) -> str:
    """
    Replace comments and string/char literal contents with spaces (keep newlines)
    so brace matching and keyword scans ignore them. Heuristic only.
    """
    out: List[str] = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        # Line comment
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            out.append("  ")
            i += 2
            while i < n and src[i] != "\n":
                out.append(" ")
                i += 1
            continue
        # Block comment
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            out.append("  ")
            i += 2
            while i < n - 1 and not (src[i] == "*" and src[i + 1] == "/"):
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            if i < n - 1:
                out.append("  ")
                i += 2
            continue
        # Triple-quoted string
        if c == '"' and i + 2 < n and src[i : i + 3] == '"""':
            out.append("   ")
            i += 3
            while i < n - 2 and src[i : i + 3] != '"""':
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            if i < n - 2:
                out.append("   ")
                i += 3
            continue
        # Double-quoted string
        if c == '"':
            out.append(" ")
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                if src[i] == '"':
                    out.append(" ")
                    i += 1
                    break
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            continue
        # Char literal
        if c == "'":
            out.append(" ")
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                if src[i] == "'":
                    out.append(" ")
                    i += 1
                    break
                out.append(" ")
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def find_matching_brace(text: str, open_idx: int) -> int:
    """Return index of matching '}' for '{' at open_idx, or -1."""
    if open_idx < 0 or open_idx >= len(text) or text[open_idx] != "{":
        return -1
    depth = 0
    i = open_idx
    n = len(text)
    while i < n:
        c = text[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


@dataclass
class FnSpan:
    name: str
    start_line: int  # 1-based
    end_line: int
    start_off: int
    end_off: int
    body: str  # cleaned body including braces


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def extract_functions(src: str, cleaned: str) -> List[FnSpan]:
    """Heuristic function span extraction via fun-decl regex + brace match."""
    spans: List[FnSpan] = []
    for m in FN_DECL_RE.finditer(cleaned):
        name = m.group(1) or m.group(2) or "<anon>"
        # Find first '{' after the match (skip signature / where clauses lightly)
        search_from = m.end()
        # Expression-body functions (`=`) have no brace block — skip structure body
        # Look ahead for `{` or `=` before next fun-ish boundary
        window = cleaned[search_from : search_from + 800]
        brace_rel = window.find("{")
        eq_rel = -1
        for i, ch in enumerate(window):
            if ch == "=":
                # not == or =>
                prev = window[i - 1] if i > 0 else ""
                nxt = window[i + 1] if i + 1 < len(window) else ""
                if prev not in "=!<>" and nxt not in "=>":
                    eq_rel = i
                    break
            if ch == "{":
                break
        if brace_rel < 0:
            continue
        if eq_rel >= 0 and eq_rel < brace_rel:
            # expression body — measure tokens on signature..end of line only
            # Skip as no block; still could count line tokens but plan targets blocks
            continue
        open_idx = search_from + brace_rel
        close_idx = find_matching_brace(cleaned, open_idx)
        if close_idx < 0:
            continue
        start_off = m.start()
        end_off = close_idx + 1
        body = cleaned[start_off:end_off]
        spans.append(
            FnSpan(
                name=name,
                start_line=line_of(src, start_off),
                end_line=line_of(src, end_off - 1),
                start_off=start_off,
                end_off=end_off,
                body=body,
            )
        )
    return spans


def count_loc(text: str) -> int:
    """Non-empty, non-whitespace-only lines."""
    return sum(1 for ln in text.splitlines() if ln.strip())


def estimate_cyclo(body: str) -> int:
    """Base 1 + branches/loops/boolean ops (keyword heuristic)."""
    # else if counted once via if; still count else-if as extra branch
    score = 1
    score += len(IF_RE.findall(body))
    score += len(WHEN_RE.findall(body))
    score += len(FOR_RE.findall(body))
    score += len(WHILE_RE.findall(body))
    score += len(CATCH_RE.findall(body))
    score += len(AND_RE.findall(body))
    score += len(OR_RE.findall(body))
    return score


def estimate_cognitive(body: str) -> int:
    """
    Nesting-aware cognitive-ish score (heuristic).
    +1 per branch/loop/boolean at nesting depth, with +nesting bonus.
    """
    # Walk with simple brace depth; award for control keywords
    score = 0
    depth = 0  # brace nesting inside body
    control_depth = 0  # approx control nesting
    i = 0
    n = len(body)
    # Pre-tokenize control keywords for speed
    while i < n:
        c = body[i]
        if c == "{":
            depth += 1
            i += 1
            continue
        if c == "}":
            depth = max(0, depth - 1)
            control_depth = min(control_depth, depth)
            i += 1
            continue
        # Word boundary keyword scan
        if c.isalpha() or c == "|" or c == "&":
            rest = body[i:]
            matched = None
            weight = 1
            if rest.startswith("else") and (len(rest) == 4 or not rest[4].isalnum() and rest[4] != "_"):
                # else / else if
                if re.match(r"else\s+if\b", rest):
                    matched = "else_if"
                    weight = 1
                else:
                    matched = "else"
                    weight = 1
            elif rest.startswith("if") and (len(rest) == 2 or not (rest[2].isalnum() or rest[2] == "_")):
                matched = "if"
            elif rest.startswith("when") and (len(rest) == 4 or not (rest[4].isalnum() or rest[4] == "_")):
                matched = "when"
            elif rest.startswith("for") and (len(rest) == 3 or not (rest[3].isalnum() or rest[3] == "_")):
                matched = "for"
            elif rest.startswith("while") and (len(rest) == 5 or not (rest[5].isalnum() or rest[5] == "_")):
                matched = "while"
            elif rest.startswith("catch") and (len(rest) == 5 or not (rest[5].isalnum() or rest[5] == "_")):
                matched = "catch"
            elif rest.startswith("&&"):
                matched = "&&"
            elif rest.startswith("||"):
                matched = "||"
            if matched:
                # nesting bonus = current control_depth
                score += weight + control_depth
                if matched in ("if", "when", "for", "while", "catch", "else_if"):
                    control_depth += 1
                # advance past token
                if matched in ("&&", "||"):
                    i += 2
                elif matched == "else_if":
                    m2 = re.match(r"else\s+if", rest)
                    i += m2.end() if m2 else 7
                else:
                    i += len(matched) if matched != "else" else 4
                continue
        i += 1
    return score


@dataclass
class Finding:
    code: str
    path: str
    metric: float
    limit: float
    remediation: str
    name: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        d: Dict[str, Any] = {
            "code": self.code,
            "path": self.path,
            "metric": int(self.metric) if float(self.metric).is_integer() else self.metric,
            "limit": int(self.limit) if float(self.limit).is_integer() else self.limit,
            "remediation": self.remediation,
        }
        if self.name:
            d["name"] = self.name
        return d


def thr_pair(section: Dict[str, Any], key: str) -> Tuple[int, int]:
    block = section.get(key) or {}
    return int(block.get("warn", 0)), int(block.get("error", 0))


def path_exists_at_ref(root: Path, ref: str, rel: str) -> bool:
    """True if path exists in tree at ref (git cat-file -e ref:path)."""
    r = subprocess.run(
        ["git", "-C", str(root), "cat-file", "-e", f"{ref}:{rel}"],
        capture_output=True,
        text=True,
    )
    return r.returncode == 0


def git_paths_with_filter(root: Path, base: str, diff_filter: str) -> List[str]:
    """git diff --name-only --diff-filter=<filter> <base> → rel paths."""
    r = subprocess.run(
        [
            "git",
            "-C",
            str(root),
            "diff",
            "--name-only",
            f"--diff-filter={diff_filter}",
            base,
        ],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        return []
    return [
        line.strip().replace("\\", "/")
        for line in r.stdout.splitlines()
        if line.strip()
    ]


def resolve_override_entry(
    cfg: Dict[str, Any],
    rel: str,
    *,
    is_new_file: bool,
) -> Optional[Dict[str, Any]]:
    """
    Return valid override dict for rel, or None.

    - Missing entry → None
    - New/Added file → ignore override (stderr note; anti same-PR grandfather)
    - Missing/invalid ticket (not MUD-\\d+) → ignore + stderr note
    """
    overrides = cfg.get("overrides") or {}
    if not isinstance(overrides, dict):
        return None
    entry = overrides.get(rel)
    if not isinstance(entry, dict):
        return None
    if is_new_file:
        sys.stderr.write(
            f"check_token_budget_kt: override ignored for new/Added file {rel}\n"
        )
        return None
    ticket = entry.get("ticket")
    if not ticket or not TICKET_RE.match(str(ticket).strip()):
        sys.stderr.write(
            f"check_token_budget_kt: override for {rel} missing valid ticket "
            f"(MUD-\\d+); ignored\n"
        )
        return None
    return entry


def thr_pair_merged(
    global_section: Dict[str, Any],
    ov_section: Optional[Dict[str, Any]],
    key: str,
) -> Tuple[int, int]:
    """Merge override warn/error over global for one metric key (if present)."""
    g_w, g_e = thr_pair(global_section, key)
    if not ov_section or not isinstance(ov_section, dict):
        return g_w, g_e
    block = ov_section.get(key)
    if not isinstance(block, dict):
        return g_w, g_e
    w = int(block["warn"]) if "warn" in block else g_w
    e = int(block["error"]) if "error" in block else g_e
    return w, e


def add_threshold_findings(
    findings: List[Finding],
    *,
    code_prefix: str,
    path: str,
    metric: int,
    warn: int,
    error: int,
    remediation: str,
    name: Optional[str] = None,
    error_gt: bool = False,
) -> None:
    """
    Emit E then W. Global: metric >= error. Override (error_gt): metric > error
    so a grandfathered measured size holds without raising caps further.
    """
    if error > 0 and (metric > error if error_gt else metric >= error):
        findings.append(
            Finding(
                code=f"{code_prefix}_E",
                path=path,
                metric=metric,
                limit=error,
                remediation=remediation,
                name=name,
            )
        )
    elif metric >= warn > 0:
        findings.append(
            Finding(
                code=f"{code_prefix}_W",
                path=path,
                metric=metric,
                limit=warn,
                remediation=remediation,
                name=name,
            )
        )


def rel_path(root: Path, fp: Path) -> str:
    try:
        return str(fp.resolve().relative_to(root.resolve())).replace("\\", "/")
    except ValueError:
        return str(fp).replace("\\", "/")


def analyze_file(
    root: Path,
    fp: Path,
    cfg: Dict[str, Any],
    *,
    is_new_file: bool = False,
) -> Tuple[List[Finding], Optional[Dict[str, Any]], Dict[str, int]]:
    """
    Returns (findings, override_candidate_or_None, counters).

    Applies path overrides when ticket-valid and not a new/Added file (MUD-031).
    Override error-tier comparison uses metric > limit; global uses >=.
    """
    rel = rel_path(root, fp)
    try:
        raw = fp.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        raw = fp.read_text(encoding="utf-8", errors="replace")

    cleaned = strip_strings_and_comments(raw)
    file_tok = token_estimate(raw)
    file_loc = count_loc(raw)

    tok = cfg["tokens"]
    struct = cfg["structure"]
    ov = resolve_override_entry(cfg, rel, is_new_file=is_new_file)
    error_gt = ov is not None
    ov_tok = ov.get("tokens") if ov else None
    ov_struct = ov.get("structure") if ov else None
    if ov_tok is not None and not isinstance(ov_tok, dict):
        ov_tok = None
    if ov_struct is not None and not isinstance(ov_struct, dict):
        ov_struct = None

    f_w, f_e = thr_pair_merged(tok, ov_tok, "file")
    fn_w, fn_e = thr_pair_merged(tok, ov_tok, "function")
    floc_w, floc_e = thr_pair_merged(struct, ov_struct, "file_loc")
    fnloc_w, fnloc_e = thr_pair_merged(struct, ov_struct, "fn_loc")
    cy_w, cy_e = thr_pair_merged(struct, ov_struct, "cyclo")
    cog_w, cog_e = thr_pair_merged(struct, ov_struct, "cognitive")

    # Global (no override) caps for candidate detection — still use base thresholds.
    g_f_w, g_f_e = thr_pair(tok, "file")
    g_floc_w, g_floc_e = thr_pair(struct, "file_loc")

    findings: List[Finding] = []
    rem_file = (
        "split file or extract modules; temporary override requires burn-down ticket"
        if not ov
        else "god under override — burn down (lower caps / split); see ticket in overrides"
    )

    add_threshold_findings(
        findings,
        code_prefix="TOKEN_FILE",
        path=rel,
        metric=file_tok,
        warn=f_w,
        error=f_e,
        remediation=rem_file,
        error_gt=error_gt,
    )
    add_threshold_findings(
        findings,
        code_prefix="STRUCTURE_FILE_LOC",
        path=rel,
        metric=file_loc,
        warn=floc_w,
        error=floc_e,
        remediation="reduce file LOC (split types/helpers); secondary to tokens",
        error_gt=error_gt,
    )

    spans = extract_functions(raw, cleaned)
    fn_findings: List[Finding] = []
    for sp in spans:
        # tokens on raw slice for consistency with file measure
        fn_raw = raw[sp.start_off : sp.end_off]
        fn_tok = token_estimate(fn_raw)
        fn_loc = count_loc(fn_raw)
        cyclo = estimate_cyclo(sp.body)
        cog = estimate_cognitive(sp.body)
        path_ref = f"{rel}:{sp.start_line}"
        rem_base = f"extract pure helpers from {sp.name}(); prefer smaller units in :core/:reasoning"

        add_threshold_findings(
            fn_findings,
            code_prefix="TOKEN_FN",
            path=path_ref,
            metric=fn_tok,
            warn=fn_w,
            error=fn_e,
            remediation=rem_base,
            name=sp.name,
            error_gt=error_gt,
        )
        add_threshold_findings(
            fn_findings,
            code_prefix="STRUCTURE_FN_LOC",
            path=path_ref,
            metric=fn_loc,
            warn=fnloc_w,
            error=fnloc_e,
            remediation=rem_base + " (fn LOC heuristic)",
            name=sp.name,
            error_gt=error_gt,
        )
        add_threshold_findings(
            fn_findings,
            code_prefix="STRUCTURE_CYCLO",
            path=path_ref,
            metric=cyclo,
            warn=cy_w,
            error=cy_e,
            remediation=rem_base + " (cyclomatic heuristic)",
            name=sp.name,
            error_gt=error_gt,
        )
        add_threshold_findings(
            fn_findings,
            code_prefix="STRUCTURE_COGNITIVE",
            path=path_ref,
            metric=cog,
            warn=cog_w,
            error=cog_e,
            remediation=rem_base + " (cognitive heuristic)",
            name=sp.name,
            error_gt=error_gt,
        )

    # Cap noisy fn rows: prefer E over W, then higher metric
    def severity_key(f: Finding) -> Tuple[int, float]:
        sev = 0 if f.code.endswith("_E") else 1
        return (sev, -float(f.metric))

    fn_findings.sort(key=severity_key)
    findings.extend(fn_findings[:MAX_FN_FINDINGS_PER_FILE])

    candidate = None
    reasons: List[str] = []
    # Candidates vs global error (not override) so burn-down list still surfaces gods.
    if g_f_e > 0 and file_tok >= g_f_e:
        reasons.append(f"file_tokens={file_tok}>={g_f_e}")
    if g_floc_e > 0 and file_loc >= g_floc_e:
        reasons.append(f"file_loc={file_loc}>={g_floc_e}")
    if reasons:
        candidate = {
            "path": rel,
            "file_tokens": file_tok,
            "file_loc": file_loc,
            "reasons": reasons,
            "note": (
                "under override" if ov else
                "candidate for temporary overrides{} entry (requires burn-down ticket)"
            ),
        }

    counters = {
        "file_tokens": file_tok,
        "file_loc": file_loc,
        "functions": len(spans),
        "fn_findings_total": len(fn_findings),
        "fn_findings_emitted": min(len(fn_findings), MAX_FN_FINDINGS_PER_FILE),
        "override_applied": 1 if ov else 0,
    }
    return findings, candidate, counters


def modules_from_paths(rel_paths: Iterable[str]) -> List[str]:
    mods = set()
    for p in rel_paths:
        first = p.split("/", 1)[0]
        if first:
            mods.add(first)
    return sorted(mods)


def build_envelope(
    root: Path,
    cfg_path: Path,
    findings: List[Finding],
    candidates: List[Dict[str, Any]],
    files_scanned: int,
    modules: List[str],
    summary_extra: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    by_code: Dict[str, int] = {}
    for f in findings:
        by_code[f.code] = by_code.get(f.code, 0) + 1
    err_n = sum(1 for f in findings if f.code.endswith("_E"))
    warn_n = sum(1 for f in findings if f.code.endswith("_W"))
    summary: Dict[str, Any] = {
        "files_scanned": files_scanned,
        "modules": modules,
        "findings_total": len(findings),
        "findings_error": err_n,
        "findings_warn": warn_n,
        "by_code": dict(sorted(by_code.items())),
        "override_candidates": len(candidates),
        "token_formula": "max(0, (len+3)//4)  # ceil(chars/4), raw UTF-8, no comment strip",
        "structure_note": "LOC exact; cyclo/cognitive keyword+nest heuristics — not Detekt PSI",
    }
    if summary_extra:
        # scope, git_base, touched_* counts, etc. (schema-free report tool)
        for k, v in summary_extra.items():
            if k not in summary:
                summary[k] = v
    return {
        "tool": "check_token_budget_kt",
        "exit_policy": "report_only",
        "root": str(root),
        "config": str(cfg_path),
        "summary": summary,
        "findings": [f.to_dict() for f in findings],
        "override_candidates": candidates,
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description=(
            "Kotlin token/structure report-only checker (MUD-028–031). "
            "Always exits 0. Path scope via --files and/or --git-diff; "
            "neither → full-repo prod scan. Untracked new .kt: pass --files. "
            "Overrides require ticket; ignored for new/Added paths."
        )
    )
    p.add_argument(
        "--root",
        default=".",
        help="Repo root to scan (default: .)",
    )
    p.add_argument(
        "--config",
        default="config/quality/token_budget_kt.json",
        help="Thresholds JSON (default: config/quality/token_budget_kt.json)",
    )
    p.add_argument(
        "--json-out",
        default=None,
        help="Write full JSON report to this path (also prints to stdout)",
    )
    p.add_argument(
        "--quiet-stdout",
        action="store_true",
        help="Do not print full JSON to stdout (still exit 0; use with --json-out)",
    )
    p.add_argument(
        "--files",
        nargs="+",
        default=None,
        metavar="PATH",
        help=(
            "Explicit path(s) to analyze (repo-rel or under --root). "
            "Only existing prod */src/main/**/*.kt kept; others skipped. "
            "Use for untracked new .kt (not in git diff). "
            "Union with --git-diff when both set."
        ),
    )
    p.add_argument(
        "--git-diff",
        action="store_true",
        help=(
            "Analyze prod .kt touched vs git base "
            f"(default base: {DEFAULT_GIT_BASE}). "
            "Uses: git diff --name-only --diff-filter=ACMR <base>. "
            "Untracked files not included — pass --files. "
            "Union with --files when both set."
        ),
    )
    p.add_argument(
        "--git-base",
        default=DEFAULT_GIT_BASE,
        metavar="REF",
        help=(
            f"Git ref for --git-diff (default: {DEFAULT_GIT_BASE}). "
            "If missing, falls back to master then HEAD~1 (one stderr warn); "
            "still exit 0."
        ),
    )
    return p.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    root = Path(args.root).resolve()
    cfg_path = Path(args.config)
    if not cfg_path.is_absolute():
        cfg_path = (root / cfg_path).resolve() if not cfg_path.exists() else cfg_path.resolve()
        # Prefer root-relative when running from repo
        cand = (root / args.config).resolve()
        if cand.is_file():
            cfg_path = cand

    cfg = load_config(cfg_path)
    files, scope_meta = resolve_scan_files(
        root,
        files_args=args.files,
        git_diff=bool(args.git_diff),
        git_base=str(args.git_base),
    )

    # New-file ban for overrides (MUD-031): Added in git-diff or missing at base.
    git_base_resolved: Optional[str] = scope_meta.get("git_base")
    if git_base_resolved is None and (args.git_diff or args.files):
        git_base_resolved, _ = resolve_git_base(root, str(args.git_base))
    added_set: set = set()
    if git_base_resolved is not None and args.git_diff:
        added_set = set(git_paths_with_filter(root, git_base_resolved, "A"))

    def is_new_file(rel: str) -> bool:
        if rel in added_set:
            return True
        if git_base_resolved is not None and not path_exists_at_ref(
            root, git_base_resolved, rel
        ):
            return True
        return False

    all_findings: List[Finding] = []
    candidates: List[Dict[str, Any]] = []
    for fp in files:
        rel = rel_path(root, fp)
        findings, cand, _ = analyze_file(
            root, fp, cfg, is_new_file=is_new_file(rel)
        )
        all_findings.extend(findings)
        if cand:
            candidates.append(cand)

    # Sort findings: E before W, then path
    def find_sort(f: Finding) -> Tuple[int, str, str]:
        return (0 if f.code.endswith("_E") else 1, f.path, f.code)

    all_findings.sort(key=find_sort)
    candidates.sort(key=lambda c: (-c.get("file_tokens", 0), c.get("path", "")))

    rels = [rel_path(root, f) for f in files]
    envelope = build_envelope(
        root=root,
        cfg_path=cfg_path,
        findings=all_findings,
        candidates=candidates,
        files_scanned=len(files),
        modules=modules_from_paths(rels),
        summary_extra=scope_meta,
    )

    text = json.dumps(envelope, indent=2, ensure_ascii=False) + "\n"

    if args.json_out:
        out_path = Path(args.json_out)
        if not out_path.is_absolute():
            out_path = (root / out_path).resolve()
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(text, encoding="utf-8")

    if not args.quiet_stdout:
        sys.stdout.write(text)
    else:
        # Brief human line for smoke
        s = envelope["summary"]
        scope = s.get("scope", "full")
        sys.stdout.write(
            f"check_token_budget_kt: files={s['files_scanned']} modules={len(s['modules'])} "
            f"findings={s['findings_total']} (E={s['findings_error']} W={s['findings_warn']}) "
            f"override_candidates={s['override_candidates']} scope={scope} "
            f"exit_policy=report_only\n"
        )

    # Report-only: always 0 (even with E breaches). Verify owns hard (MUD-031).
    return 0


if __name__ == "__main__":
    sys.exit(main())
