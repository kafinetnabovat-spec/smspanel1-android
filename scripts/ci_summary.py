"""Expose actionable build diagnostics even when log/artifact downloads are unavailable."""
from pathlib import Path
import os
import xml.etree.ElementTree as ET


def annotation(level, message):
    escaped = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{level}::{escaped}")


reports = list(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
if reports:
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
        for case in suite.findall("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                annotation("error", f"{case.get('classname')}.{case.get('name')}: {problem.get('message', '')}")
    summary = "JUnit: " + ", ".join(f"{key}={value}" for key, value in totals.items())
    annotation("notice", summary)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
        output.write(summary + "\n")

log = Path("gradle-check.log")
if log.exists():
    lines = log.read_text(errors="replace").splitlines()
    if any("BUILD FAILED" in line for line in lines):
        relevant = [line for line in lines if line.startswith("e: ") or " Error:" in line]
        annotation("error", "\n".join(relevant[:25] or lines[-45:]))

lint = Path("app/build/reports/lint-results-debug.xml")
if lint.exists():
    issues = ET.parse(lint).getroot().findall("issue")
    errors = [issue for issue in issues if issue.get("severity") in ("Error", "Fatal")]
    annotation("notice", f"Android Lint: {len(errors)} errors, {len(issues) - len(errors)} other findings")
