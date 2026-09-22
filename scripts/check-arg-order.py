#!/usr/bin/env python3
"""Flags violations of the argument-order convention.

Heuristic, regex + paren-depth based -- not a real AST check. Exits non-zero if
any violation is found. Covers:
  - Composable Screens (XxxScreen): modifier -> viewModel -> nav callbacks
  - Composable Content (XxxContent): modifier must be the first parameter (ADR-0021)
  - ViewModel constructors: SavedStateHandle must be first
  - Repository / DataSource / UseCase multi-param methods and UseCase Params
    data classes: identifier-like params (name ending in Id/Ids) must precede
    non-identifier (payload/action/value) params
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def find_matching_paren(text: str, open_idx: int) -> int:
    """Given the index of an opening '(', return the index of its matching ')'.

    Treats '<' / '>' as depth-affecting too, since Kotlin generics
    (List<String>) and lambda types ((String) -> Unit) both nest inside
    parameter lists and would otherwise confuse a naive comma split.
    """
    depth = 0
    i = open_idx
    while i < len(text):
        c = text[i]
        if c == ">" and text[i - 1:i] == "-":
            pass  # '->' lambda arrow, not a generic close
        elif c in "(<":
            depth += 1
        elif c in ")>":
            depth -= 1
            if depth == 0 and c == ")":
                return i
        i += 1
    return -1


def split_params(param_text: str) -> list[str]:
    parts = []
    depth = 0
    current = []
    prev = ""
    for c in param_text:
        if c == ">" and prev == "-":
            pass  # '->' lambda arrow, not a generic close
        elif c in "(<[{":
            depth += 1
        elif c in ")>]}":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(c)
        prev = c
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def param_name(param: str) -> str:
    name = param.strip()
    for prefix in ("private val ", "val ", "var ", "vararg ", "crossinline ", "noinline "):
        if name.startswith(prefix):
            name = name[len(prefix):]
    return name.split(":")[0].strip()


def is_identifier_param(name: str) -> bool:
    return bool(re.search(r"(Id|Ids)$", name))


def iter_signatures(text: str, start_pattern: str):
    """Yield (match, params_list, sig_end_idx) for each signature matching start_pattern."""
    for m in re.finditer(start_pattern, text):
        open_idx = text.index("(", m.end() - 1)
        close_idx = find_matching_paren(text, open_idx)
        if close_idx == -1:
            continue
        params = split_params(text[open_idx + 1:close_idx])
        yield m, params, close_idx


def line_of(text: str, idx: int) -> int:
    return text.count("\n", 0, idx) + 1


def check_composables(path: Path, text: str, violations: list[str]) -> None:
    pattern = r"(?:^|\n)[ \t]*(?:private |internal )?fun ([A-Za-z0-9_]+)\("
    for m, params, _ in iter_signatures(text, pattern):
        fn_name = m.group(1)
        names = [param_name(p) for p in params]
        line = line_of(text, m.start())

        def idx_of(pred):
            for i, n in enumerate(names):
                if pred(n):
                    return i
            return -1

        modifier_idx = idx_of(lambda n: n == "modifier")
        vm_idx = idx_of(lambda n: n == "viewModel")
        navcb_idx = idx_of(lambda n: n.startswith("onNavigate"))
        navback_idx = idx_of(lambda n: n == "onNavigateBack")
        navcb_indices = [i for i, n in enumerate(names) if n.startswith("onNavigate")]

        if fn_name.endswith("Screen"):
            if modifier_idx >= 0 and vm_idx >= 0 and vm_idx < modifier_idx:
                violations.append(f"{path}:{line}: {fn_name}: viewModel must come after modifier (ADR-0020)")
            if modifier_idx >= 0 and navcb_idx >= 0 and navcb_idx < modifier_idx:
                violations.append(f"{path}:{line}: {fn_name}: nav callback must come after modifier (ADR-0020)")
            if vm_idx >= 0 and navcb_idx >= 0 and navcb_idx < vm_idx:
                violations.append(f"{path}:{line}: {fn_name}: nav callback must come after viewModel (ADR-0020)")
            if navback_idx >= 0 and any(i < navback_idx for i in navcb_indices if i != navback_idx):
                violations.append(f"{path}:{line}: {fn_name}: onNavigateBack must be the first nav callback (ADR-0020)")

        if fn_name.endswith("Content"):
            if modifier_idx > 0:
                violations.append(f"{path}:{line}: {fn_name}: modifier must be the first parameter (ADR-0021)")


def check_viewmodel_ctor(path: Path, text: str, violations: list[str]) -> None:
    pattern = r"@Inject constructor\("
    for m, params, _ in iter_signatures(text, pattern):
        line = line_of(text, m.start())
        names = [param_name(p) for p in params]
        svh_idx = next((i for i, n in enumerate(names) if "SavedStateHandle" in params[i]), -1)
        if svh_idx > 0:
            violations.append(f"{path}:{line}: SavedStateHandle must be the first constructor param (ADR-0020)")


def check_identifier_order(path: Path, text: str, violations: list[str]) -> None:
    patterns = [
        r"(?:^|\n)\s*(?:override |private )?(?:suspend )?(?:operator )?fun ([A-Za-z0-9_]+)\(",
        r"(?:^|\n)\s*data class ([A-Za-z0-9_]+)\(",
    ]
    for pattern in patterns:
        for m, params, _ in iter_signatures(text, pattern):
            fn_name = m.group(1)
            if len(params) < 2:
                continue
            names = [param_name(p) for p in params]
            id_flags = [is_identifier_param(n) for n in names]
            if not any(id_flags) or all(id_flags):
                continue
            first_payload = next(i for i, f in enumerate(id_flags) if not f)
            for i in range(first_payload + 1, len(names)):
                if id_flags[i]:
                    line = line_of(text, m.start())
                    violations.append(
                        f"{path}:{line}: {fn_name}: identifier param '{names[i]}' must come before "
                        f"payload param '{names[first_payload]}' (ADR-0020)"
                    )
                    break


def main() -> int:
    violations: list[str] = []

    for path in ROOT.rglob("*.kt"):
        rel = path.relative_to(ROOT)
        name = path.name

        # Reusable design-system components (:core:ui composables/) follow the official
        # Compose Component API guidelines (required -> modifier -> optional -> trailing
        # slot), NOT the Screen/Content order. Exempt them from the arg-order check.
        rel_posix = rel.as_posix()
        if "/core/ui/" in f"/{rel_posix}" and "/composables/" in f"/{rel_posix}":
            continue

        text = path.read_text(encoding="utf-8")

        if name.endswith("Screen.kt") or name.endswith("Content.kt"):
            check_composables(rel, text, violations)
        if name.endswith("ViewModel.kt"):
            check_viewmodel_ctor(rel, text, violations)
        if name.endswith("Repository.kt") or name.endswith("DataSource.kt") or name.endswith("UseCase.kt"):
            check_identifier_order(rel, text, violations)

    if violations:
        for v in violations:
            print(v)
        print("\nArgument-order violations found -- see ADR-0020.")
        return 1

    print("check-arg-order: no violations found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
