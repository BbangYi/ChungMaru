#!/usr/bin/env python3
"""Generate a Chungmaru work-session log block for Notion registration.

The script intentionally does not call Notion directly. It produces a stable
copy/paste block and can write the same block under .git so the worktree stays
clean while another registrar flow appends it to the Work Session Log Hub.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo


LANES = (
    "android",
    "backend",
    "extension",
    "shared-contract",
    "docs-evaluation",
    "cross-lane",
)

WORK_SESSION_LOG_HUB_URL = "https://www.notion.so/3650cc24fc2e81279fe0cb282dc50e0c"


@dataclass(frozen=True)
class SessionLog:
  lane: str
  source_session_id: str
  registrar_session_id: str
  validation: str
  blocker: str
  evidence_link: str
  milestone_update: str
  next_action: str
  timestamp: datetime
  branch: str
  changed_summary: str


def parse_args() -> argparse.Namespace:
  parser = argparse.ArgumentParser(
      description="Create a concise Chungmaru session-close log for Notion."
  )
  parser.add_argument("--lane", choices=LANES, required=True)
  parser.add_argument("--source-session-id", default="", help="Development session/thread id.")
  parser.add_argument("--registrar-session-id", default="", help="Session id that will register the log.")
  parser.add_argument("--validation", default="", help="Validation commands/results.")
  parser.add_argument("--blocker", default="", help="Known blocker or validation gap.")
  parser.add_argument("--evidence-link", default="", help="Artifact, PR, screenshot, trace, or Notion link.")
  parser.add_argument("--milestone-update", default="", help="Deliverable/milestone status update.")
  parser.add_argument("--next-action", default="", help="Next concrete action.")
  parser.add_argument("--handoff", action="store_true", help="Wrap output in registrar handoff sentinels.")
  parser.add_argument(
      "--write-local-outbox",
      action="store_true",
      help="Write the handoff block to .git/chungmaru-session-outbox without dirtying the worktree.",
  )
  return parser.parse_args()


def run_git(args: list[str]) -> str:
  try:
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()
  except Exception:
    return ""


def current_branch() -> str:
  return run_git(["branch", "--show-current"]) or "(unknown)"


def changed_summary() -> str:
  output = run_git(["status", "--short"])
  if not output:
    return "No worktree changes reported by git."

  counts: dict[str, int] = {}
  total = 0
  for line in output.splitlines():
    total += 1
    status = line[:2].strip() or "??"
    counts[status] = counts.get(status, 0) + 1

  parts = ", ".join(f"{status}:{count}" for status, count in sorted(counts.items()))
  return f"{total} changed paths ({parts})"


def clean(value: str, fallback: str = "-") -> str:
  text = str(value or "").strip()
  if not text:
    return fallback
  return re.sub(r"\n{3,}", "\n\n", text)


def build_markdown(log: SessionLog) -> str:
  timestamp = log.timestamp.strftime("%Y-%m-%d %H:%M:%S %Z")
  lines = [
      "## Chungmaru Work Session Log",
      "",
      f"- Time: {timestamp}",
      f"- Lane: {log.lane}",
      f"- Branch: {clean(log.branch)}",
      f"- Source session: {clean(log.source_session_id)}",
      f"- Registrar session: {clean(log.registrar_session_id)}",
      f"- Worktree: {clean(log.changed_summary)}",
      f"- Validation: {clean(log.validation)}",
      f"- Blocker / gap: {clean(log.blocker, 'None')}",
      f"- Evidence link: {clean(log.evidence_link)}",
      f"- Milestone update: {clean(log.milestone_update)}",
      f"- Next action: {clean(log.next_action)}",
      "",
      f"Work Session Log Hub: {WORK_SESSION_LOG_HUB_URL}",
  ]
  return "\n".join(lines)


def wrap_handoff(markdown: str) -> str:
  return "\n".join([
      "CHUNGMARU_NOTION_HANDOFF_BEGIN",
      markdown,
      "CHUNGMARU_NOTION_HANDOFF_END",
  ])


def write_outbox(repo_root: Path, body: str, timestamp: datetime) -> Path:
  outbox = repo_root / ".git" / "chungmaru-session-outbox"
  fallback_outbox = Path(os.environ.get("TMPDIR") or "/private/tmp") / "chungmaru-session-outbox" / repo_root.name
  try:
    outbox.mkdir(parents=True, exist_ok=True)
  except PermissionError:
    outbox = fallback_outbox

  outbox.mkdir(parents=True, exist_ok=True)
  filename = timestamp.strftime("%Y%m%d-%H%M%S-session-log.md")
  path = outbox / filename
  try:
    path.write_text(body + "\n", encoding="utf-8")
  except PermissionError:
    fallback_outbox.mkdir(parents=True, exist_ok=True)
    path = fallback_outbox / filename
    path.write_text(body + "\n", encoding="utf-8")
  return path


def main() -> int:
  args = parse_args()
  repo_root = Path.cwd()
  timestamp = datetime.now(ZoneInfo("Asia/Seoul"))
  log = SessionLog(
      lane=args.lane,
      source_session_id=args.source_session_id,
      registrar_session_id=args.registrar_session_id,
      validation=args.validation,
      blocker=args.blocker,
      evidence_link=args.evidence_link,
      milestone_update=args.milestone_update,
      next_action=args.next_action,
      timestamp=timestamp,
      branch=current_branch(),
      changed_summary=changed_summary(),
  )
  markdown = build_markdown(log)
  output = wrap_handoff(markdown) if args.handoff else markdown

  if args.write_local_outbox:
    outbox_path = write_outbox(repo_root, output, timestamp)
    print(f"Wrote local outbox: {outbox_path}", file=sys.stderr)

  print(output)
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
