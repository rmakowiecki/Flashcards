#!/usr/bin/env python3
"""Regenerates Compose compiler reports and prints their stability findings.

Usage:
  python3 scripts/compose-stability.py                       # every module
  python3 scripts/compose-stability.py :feature:browse :core:ui
  python3 scripts/compose-stability.py --uncertain           # also list uncertain params

Compiles the release variant with `--rerun -PcomposeCompilerReports=true` (see the
`android-compose` convention plugin), after deleting each target module's old
`build/compose_compiler/` so stale reports never mix in. Then parses the fresh reports
and prints one dense line per finding, grouped by module:

  - `unstable`      a composable parameter the compiler proved unstable;
  - `not skippable` a restartable composable the compiler can't skip;
  - `uncertain`     (only with --uncertain) a parameter with no stability prefix, e.g. a
                    sealed interface type.

Each unstable finding carries the unstable fields of its type, looked up in every
module's `*-classes.txt`. There is no allowlist: the script reports, the reader judges.

Exit codes: 0 after a successful build (whatever it found), 2 when the build fails.
"""
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORTS_DIR = Path("build") / "compose_compiler"
BUILD_FAILURE_TAIL_LINES = 40
MAX_CAUSE_FIELDS = 3

HEADER_PATTERN = re.compile(r"^(?P<flags>.*?)\bfun (?P<name>[^(]+)\((?P<rest>.*)$")
PARAM_PATTERN = re.compile(
    r"^\s+(?P<prefix>(?:(?:stable|unstable|runtime)\s+)?)(?P<name>[^:]+): (?P<type>.+?)(?: = .*)?$"
)
CLASS_HEADER_PATTERN = re.compile(r"^(?P<stability>\w+) class (?P<name>[\w.$]+)")
TYPE_NAME_PATTERN = re.compile(r"[A-Z][\w]*(?:\.[A-Z][\w]*)*")


def settings_modules() -> list[str]:
    settings = (ROOT / "settings.gradle.kts").read_text()
    return re.findall(r'include\("(:[^"]+)"\)', settings)


def module_dir(module_path: str) -> Path:
    return ROOT / module_path.strip(":").replace(":", "/")


def compile_reports(modules: list[str] | None) -> None:
    tasks = [f"{module}:compileReleaseKotlin" for module in modules] if modules else ["compileReleaseKotlin"]
    command = ["./gradlew", *tasks, "--rerun", "-PcomposeCompilerReports=true", "--quiet"]
    completed = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    if completed.returncode == 0:
        return
    output_lines = (completed.stdout + completed.stderr).splitlines()
    error_lines = [line for line in output_lines if line.startswith("e: ")]
    tail_lines = [line for line in output_lines[-BUILD_FAILURE_TAIL_LINES:] if line not in error_lines]
    print(f"BUILD FAILED: {' '.join(command)}")
    print("\n".join(error_lines + tail_lines))
    sys.exit(2)


def parse_composables(report: Path) -> list[dict]:
    composables = []
    current = None
    for line in report.read_text().splitlines():
        header = HEADER_PATTERN.match(line) if not line.startswith(" ") else None
        if header:
            current = {
                "flags": header["flags"].split(),
                "name": header["name"].rsplit(".", 1)[-1],
                "params": [],
            }
            composables.append(current)
        elif current is not None:
            param = PARAM_PATTERN.match(line)
            if param:
                current["params"].append(
                    {"prefix": param["prefix"].strip(), "name": param["name"].strip(), "type": param["type"].strip()}
                )
    return composables


def parse_classes(reports_roots: list[Path]) -> dict[str, list[str]]:
    """Maps each fully qualified class name to its unstable, runtime-checked and `var` field lines."""
    unstable_fields_by_class: dict[str, list[str]] = {}
    for report in (path for root in reports_roots for path in root.glob("*-classes.txt")):
        class_name = None
        for line in report.read_text().splitlines():
            header = CLASS_HEADER_PATTERN.match(line)
            if header:
                class_name = header["name"]
                unstable_fields_by_class.setdefault(class_name, [])
            # A `var` field makes its class unstable even when the field's own type is stable.
            elif class_name and re.match(r"^\s+(?:(?:unstable|runtime) (?:val|var)|\w+ var) ", line):
                unstable_fields_by_class[class_name].append(line.strip())
    return unstable_fields_by_class


def cause_for(type_text: str, unstable_fields_by_class: dict[str, list[str]]) -> str:
    fields: list[str] = []
    for type_name in TYPE_NAME_PATTERN.findall(type_text):
        for class_name, class_fields in unstable_fields_by_class.items():
            nested_name = re.sub(r"^(?:[a-z_][\w]*\.)+", "", class_name)
            if nested_name == type_name or nested_name.startswith(f"{type_name}."):
                fields.extend(field for field in class_fields if field not in fields)
    if not fields:
        return ""
    shown = fields[:MAX_CAUSE_FIELDS]
    more = f" (+{len(fields) - MAX_CAUSE_FIELDS} more)" if len(fields) > MAX_CAUSE_FIELDS else ""
    return f"  <- {'; '.join(shown)}{more}"


class SourceIndex:
    """Finds `fun Name(` declarations in one module's sources, for file:line locations."""

    def __init__(self, source_root: Path):
        self.files = [(path, path.read_text().splitlines()) for path in source_root.rglob("*.kt")]

    def locate(self, function_name: str) -> str:
        pattern = re.compile(rf"\bfun (?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?{re.escape(function_name)}\s*\(")
        matches = [
            f"{path.name}:{line_number}"
            for path, lines in self.files
            for line_number, line in enumerate(lines, start=1)
            if pattern.search(line)
        ]
        return ",".join(matches) if matches else "?"


def module_summary(reports_root: Path) -> str:
    metrics_files = list(reports_root.glob("*/*-module.json"))
    if not metrics_files:
        return "no metrics"
    metrics = json.loads(metrics_files[0].read_text())
    return (
        f"skippable {metrics['skippableComposables']}/{metrics['restartableComposables']}"
        f"  unstable args {metrics['knownUnstableArguments']}"
    )


def module_findings(
    module_path: str, reports_root: Path, unstable_fields_by_class: dict[str, list[str]], include_uncertain: bool
) -> list[str]:
    sources = SourceIndex(module_dir(module_path) / "src")
    findings = []
    for report in sorted(reports_root.glob("*-composables.txt")):
        for composable in parse_composables(report):
            name = composable["name"]
            if "restartable" in composable["flags"] and "skippable" not in composable["flags"]:
                findings.append(f"{module_path}  {sources.locate(name)}  {name}()  not skippable")
            for param in composable["params"]:
                signature = f"{name}({param['name']}: {param['type']})"
                if param["prefix"] == "unstable":
                    # A ViewModel's fields are always unstable and never the actionable cause.
                    is_view_model = param["type"].rstrip("?").endswith("ViewModel")
                    cause = "" if is_view_model else cause_for(param["type"], unstable_fields_by_class)
                    findings.append(f"{module_path}  {sources.locate(name)}  {signature}  unstable{cause}")
                elif include_uncertain and not param["prefix"]:
                    findings.append(f"{module_path}  {sources.locate(name)}  {signature}  uncertain")
    return findings


def main() -> int:
    arguments = sys.argv[1:]
    include_uncertain = "--uncertain" in arguments
    requested_modules = [argument for argument in arguments if argument.startswith(":")]
    unknown_arguments = [argument for argument in arguments if argument != "--uncertain" and not argument.startswith(":")]
    if unknown_arguments:
        print(f"Unknown arguments: {' '.join(unknown_arguments)}\n{__doc__}")
        return 2

    target_modules = requested_modules or settings_modules()
    for module_path in target_modules:
        shutil.rmtree(module_dir(module_path) / REPORTS_DIR, ignore_errors=True)

    compile_reports(requested_modules or None)

    reports_roots = {
        module_path: module_dir(module_path) / REPORTS_DIR
        for module_path in target_modules
        if (module_dir(module_path) / REPORTS_DIR).is_dir()
    }
    if not reports_roots:
        print("No Compose compiler reports produced (no Compose module among the targets).")
        return 0

    # Class stability comes from every module's reports, since a param type is often declared elsewhere;
    # modules outside the targets contribute whatever reports they last produced.
    all_reports_roots = [module_dir(module_path) / REPORTS_DIR for module_path in settings_modules()]
    unstable_fields_by_class = parse_classes(all_reports_roots)

    for module_path, reports_root in reports_roots.items():
        findings = module_findings(module_path, reports_root, unstable_fields_by_class, include_uncertain)
        print(f"{module_path}  {module_summary(reports_root)}  findings {len(findings)}")
        for finding in findings:
            print(f"  {finding}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
