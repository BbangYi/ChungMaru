#!/usr/bin/env python3
"""Build and publish the bbangyippt presentation hub.

The source of truth is docs/presentation/html-deck. The build directory is
ignored by Git and is used only as a local staging folder for deployment.
"""

from __future__ import annotations

import argparse
import datetime as dt
import html.parser
import json
import os
import posixpath
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "docs" / "presentation" / "html-deck"
BUILD_DIR = ROOT / "build" / "presentation" / "bbangyippt"
REMOTE_DIR = "/var/www/bbangyippt"
DEFAULT_URL = "http://bbangyippt.kro.kr"
DEFAULT_CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
RUNTIME_ROOT = Path.home() / ".cache" / "codex-runtimes" / "codex-primary-runtime" / "dependencies"
DEFAULT_NODE_BIN = RUNTIME_ROOT / "node" / "bin" / "node"
DEFAULT_NODE_MODULES = RUNTIME_ROOT / "node" / "node_modules"
DEFAULT_SNAPSHOT_DIR = BUILD_DIR / "qa"
ROOT_FILES = ("index.html", "deck.html", "registry.json")


class SlideCounter(html.parser.HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.count = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "section":
            return
        values = dict(attrs)
        classes = set((values.get("class") or "").split())
        if "slide" in classes:
            self.count += 1


class LinkCollector(html.parser.HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        for attr in ("href", "src"):
            value = values.get(attr)
            if value:
                self.links.append((attr, value))


def run(cmd: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(cmd))
    return subprocess.run(cmd, cwd=ROOT, text=True, check=check)


def load_registry(base_dir: Path = SOURCE_DIR) -> dict[str, Any]:
    registry_path = base_dir / "registry.json"
    return json.loads(registry_path.read_text(encoding="utf-8"))


def deck_entries(base_dir: Path = SOURCE_DIR) -> list[dict[str, Any]]:
    registry = load_registry(base_dir)
    decks = registry.get("decks")
    if not isinstance(decks, list) or not decks:
        raise SystemExit("registry.json must contain a non-empty decks array")
    return decks


def public_path_to_relative(public_path: str, base_url: str = DEFAULT_URL) -> str:
    if public_path.startswith("http://") or public_path.startswith("https://"):
        parsed = urllib.parse.urlparse(public_path)
        path = parsed.path
    else:
        path = public_path
    path = path or "/"
    if path.endswith("/"):
        path = posixpath.join(path, "index.html")
    return path.lstrip("/")


def deck_file(entry: dict[str, Any], base_dir: Path = SOURCE_DIR) -> Path:
    public_path = str(entry.get("publicPath") or "")
    if not public_path:
        raise SystemExit(f"deck `{entry.get('id', '<missing>')}` is missing publicPath")
    return base_dir / public_path_to_relative(public_path)


def validate_file(path: Path) -> int:
    parser = SlideCounter()
    parser.feed(path.read_text(encoding="utf-8"))
    return parser.count


def count_slides(html_text: str) -> int:
    parser = SlideCounter()
    parser.feed(html_text)
    return parser.count


def ensure_source() -> list[dict[str, Any]]:
    missing = [name for name in ROOT_FILES if not (SOURCE_DIR / name).is_file()]
    if missing:
        raise SystemExit(f"missing source files: {', '.join(missing)}")

    registry = load_registry()
    base_url = registry.get("baseUrl")
    if base_url != DEFAULT_URL:
        raise SystemExit(f"registry baseUrl must be {DEFAULT_URL}, got {base_url!r}")

    entries = deck_entries()
    for entry in entries:
        if not entry.get("id"):
            raise SystemExit("registry deck entry is missing id")
        path = deck_file(entry)
        if not path.is_file():
            raise SystemExit(f"missing deck file for {entry['id']}: {path}")
        count = validate_file(path)
        if count < 1:
            raise SystemExit(f"deck {entry['id']} contains no slides: {path}")
    return entries


def command_validate(_args: argparse.Namespace) -> None:
    entries = ensure_source()
    for entry in entries:
        count = validate_file(deck_file(entry))
        print(f"valid deck: {entry['id']} ({count} slides)")
    check_links(SOURCE_DIR)
    print(f"valid presentation hub: {len(entries)} decks")


def ignore_generated(_dir: str, names: list[str]) -> set[str]:
    return {name for name in names if name in {".DS_Store", "__pycache__"}}


def build_site() -> list[dict[str, Any]]:
    entries = ensure_source()
    if BUILD_DIR.exists():
        shutil.rmtree(BUILD_DIR)
    shutil.copytree(SOURCE_DIR, BUILD_DIR, ignore=ignore_generated)
    keep_ids = {str(entry["id"]) for entry in entries}
    for group in ("decks", "qa"):
        group_dir = BUILD_DIR / group
        if not group_dir.is_dir():
            continue
        for child in group_dir.iterdir():
            if child.is_dir() and child.name not in keep_ids:
                shutil.rmtree(child)
    published_at = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    (BUILD_DIR / "health.txt").write_text(
        "\n".join(
            [
                "ok",
                "domain=bbangyippt.kro.kr",
                "source=docs/presentation/html-deck",
                f"deck_count={len(entries)}",
                f"published_at_utc={published_at}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return entries


def command_build(_args: argparse.Namespace) -> None:
    entries = build_site()
    print(f"built presentation hub: {BUILD_DIR}")
    print(f"decks: {', '.join(entry['id'] for entry in entries)}")


def deploy_built(args: argparse.Namespace) -> None:
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    remote_backup = f"{REMOTE_DIR}/.backup/{stamp}"
    archive_name = f"bbangyippt-{stamp}.tar.gz"
    with tempfile.TemporaryDirectory(prefix="bbangyippt-deploy-") as temp_dir:
        archive = Path(temp_dir) / archive_name
        run(["tar", "--no-xattrs", "-czf", str(archive), "-C", str(BUILD_DIR), "."])
        run(["ssh", args.host, "mkdir", "-p", remote_backup])
        run(
            [
                "ssh",
                args.host,
                f"find {REMOTE_DIR} -mindepth 1 -maxdepth 1 ! -name .backup -exec cp -a {{}} {remote_backup}/ \\;",
            ],
        )
        run(["ssh", args.host, "mkdir", "-p", REMOTE_DIR])
        run(["scp", str(archive), f"{args.host}:/tmp/{archive_name}"])
        run(
            [
                "ssh",
                args.host,
                (
                    f"find {REMOTE_DIR} -mindepth 1 -maxdepth 1 ! -name .backup -exec rm -rf {{}} + "
                    f"&& tar -xzf /tmp/{archive_name} -C {REMOTE_DIR} "
                    f"&& rm -f /tmp/{archive_name} "
                    f"&& find {REMOTE_DIR} -name '._*' -delete "
                    f"&& chmod -R a+rX {REMOTE_DIR}"
                ),
            ],
        )
    print(f"published backup: {remote_backup}")


def command_deploy(args: argparse.Namespace) -> None:
    build_site()
    check_links(BUILD_DIR)
    deploy_built(args)


def fetch_text(url: str) -> str:
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            return response.read().decode("utf-8", errors="replace")
    except urllib.error.URLError:
        result = subprocess.run(
            ["curl", "--max-time", "10", "-fsSL", url],
            cwd=ROOT,
            text=True,
            check=True,
            stdout=subprocess.PIPE,
        )
        return result.stdout


def remote_path_for_url(url_path: str) -> str:
    relative = public_path_to_relative(url_path)
    return f"{REMOTE_DIR}/{relative}"


def fetch_remote_text(host: str, path: str) -> str:
    result = subprocess.run(
        ["ssh", host, "cat", path],
        cwd=ROOT,
        text=True,
        check=True,
        stdout=subprocess.PIPE,
    )
    return result.stdout


def fetch_text_with_fallback(url: str, host: str | None) -> tuple[str, str]:
    try:
        return fetch_text(url), url
    except (subprocess.CalledProcessError, urllib.error.URLError) as exc:
        if not host:
            raise
        parsed = urllib.parse.urlparse(url)
        remote_path = remote_path_for_url(parsed.path or "/")
        print(f"public fetch failed; falling back to {host}:{remote_path}: {exc}")
        return fetch_remote_text(host, remote_path), f"{host}:{remote_path}"


def command_verify(args: argparse.Namespace) -> None:
    base = args.url.rstrip("/")
    root_html, source = fetch_text_with_fallback(base + "/", args.host)
    health_text, _ = fetch_text_with_fallback(base + "/health.txt", args.host)
    registry_text, _ = fetch_text_with_fallback(base + "/registry.json", args.host)
    registry = json.loads(registry_text)
    entries = registry.get("decks") or []
    if "Presentation Hub" not in root_html:
        raise SystemExit("remote root does not look like the Presentation Hub")
    print(f"verified source: {source}")
    print(health_text.strip())
    for entry in entries:
        public_path = str(entry["publicPath"])
        path = urllib.parse.urlparse(public_path).path
        deck_url = base + path
        if deck_url.endswith("/"):
            deck_url += "index.html"
        deck_html, deck_source = fetch_text_with_fallback(deck_url, args.host)
        count = count_slides(deck_html)
        print(f"remote deck {entry['id']}: {count} slides ({deck_source})")
        if count < 1:
            raise SystemExit(f"remote deck {entry['id']} contains no slides")


def audit_js() -> str:
    return r"""
const { chromium } = require("playwright");

const deckUrl = process.argv[2];
const chromePath = process.argv[3];
const viewportArg = process.argv[4] || "1280x720";

function parseViewport(value) {
  const match = String(value).match(/^(\d+)x(\d+)$/);
  if (!match) throw new Error(`invalid viewport: ${value}`);
  return { width: Number(match[1]), height: Number(match[2]) };
}

function elementName(element) {
  const classes = [...element.classList].slice(0, 3).join(".");
  const text = (element.textContent || "").replace(/\s+/g, " ").trim().slice(0, 80);
  return `${element.tagName.toLowerCase()}${classes ? "." + classes : ""}${text ? " :: " + text : ""}`;
}

(async () => {
  const launchOptions = {
    headless: true,
    args: [
      "--disable-gpu",
      "--hide-scrollbars",
      "--disable-background-networking",
      "--disable-component-update",
      "--no-first-run",
      "--no-default-browser-check",
      "--disable-sync",
      "--disable-extensions",
      "--allow-file-access-from-files",
    ],
  };
  if (chromePath && chromePath !== "default") launchOptions.executablePath = chromePath;
  const browser = await chromium.launch(launchOptions);

  const page = await browser.newPage({ viewport: parseViewport(viewportArg), deviceScaleFactor: 1 });
  await page.goto(deckUrl, { waitUntil: "domcontentloaded", timeout: 15000 });
  await page.evaluate(async () => {
    const timeout = new Promise((resolve) => setTimeout(resolve, 1200));
    if (document.fonts && document.fonts.ready) await Promise.race([document.fonts.ready, timeout]);
  });

  const audit = await page.evaluate((elementNameSource) => {
    const nameElement = new Function("element", `return (${elementNameSource})(element);`);
    const style = document.createElement("style");
    style.textContent = "* { transition: none !important; animation: none !important; }";
    document.head.appendChild(style);

    const slides = [...document.querySelectorAll(".slide")];
    return slides.map((slide, index) => {
      slides.forEach((candidate, candidateIndex) => {
        candidate.classList.toggle("is-active", candidateIndex === index);
      });

      const slideRect = slide.getBoundingClientRect();
      const overflowers = [];
      for (const element of slide.querySelectorAll("*")) {
        const computed = getComputedStyle(element);
        if (computed.display === "none" || computed.visibility === "hidden") continue;
        const rect = element.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) continue;
        const outside =
          rect.left < slideRect.left - 2 ||
          rect.top < slideRect.top - 2 ||
          rect.right > slideRect.right + 2 ||
          rect.bottom > slideRect.bottom + 2;
        if (outside) overflowers.push(nameElement(element));
      }

      return {
        index: index + 1,
        title: slide.dataset.title || "",
        scrollWidth: slide.scrollWidth,
        clientWidth: slide.clientWidth,
        scrollHeight: slide.scrollHeight,
        clientHeight: slide.clientHeight,
        scrollOverflow: slide.scrollWidth > slide.clientWidth + 2 || slide.scrollHeight > slide.clientHeight + 2,
        outsideCount: overflowers.length,
        outside: overflowers.slice(0, 8),
      };
    });
  }, elementName.toString());

  console.log(JSON.stringify(audit));
  await browser.close();
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
"""


def snapshot_js() -> str:
    return r"""
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const deckUrl = process.argv[2];
const chromePath = process.argv[3];
const outputDir = process.argv[4];
const slideSelection = process.argv[5] || "all";
const viewportArg = process.argv[6] || "1280x720";

function parseViewport(value) {
  const match = String(value).match(/^(\d+)x(\d+)$/);
  if (!match) throw new Error(`invalid viewport: ${value}`);
  return { width: Number(match[1]), height: Number(match[2]) };
}

function parseSelection(value, count) {
  if (!value || value === "all") return Array.from({ length: count }, (_, index) => index + 1);
  const selected = new Set();
  for (const part of value.split(",")) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const range = trimmed.match(/^(\d+)-(\d+)$/);
    if (range) {
      const start = Number(range[1]);
      const end = Number(range[2]);
      for (let index = Math.min(start, end); index <= Math.max(start, end); index += 1) selected.add(index);
      continue;
    }
    selected.add(Number(trimmed));
  }
  return [...selected].filter((index) => Number.isInteger(index) && index >= 1 && index <= count).sort((a, b) => a - b);
}

(async () => {
  fs.mkdirSync(outputDir, { recursive: true });
  for (const name of fs.readdirSync(outputDir)) {
    if (/^(slide-\d+.*\.png|contact-sheet\.(png|html))$/.test(name)) fs.unlinkSync(path.join(outputDir, name));
  }

  const launchOptions = {
    headless: true,
    args: [
      "--disable-gpu",
      "--hide-scrollbars",
      "--disable-background-networking",
      "--disable-component-update",
      "--no-first-run",
      "--no-default-browser-check",
      "--disable-sync",
      "--disable-extensions",
      "--allow-file-access-from-files",
    ],
  };
  if (chromePath && chromePath !== "default") launchOptions.executablePath = chromePath;
  const browser = await chromium.launch(launchOptions);

  const page = await browser.newPage({ viewport: parseViewport(viewportArg), deviceScaleFactor: 1 });
  await page.goto(deckUrl, { waitUntil: "domcontentloaded", timeout: 15000 });
  await page.evaluate(async () => {
    const style = document.createElement("style");
    style.textContent = "* { transition: none !important; animation: none !important; }";
    document.head.appendChild(style);
    const timeout = new Promise((resolve) => setTimeout(resolve, 1200));
    if (document.fonts && document.fonts.ready) await Promise.race([document.fonts.ready, timeout]);
  });

  const title = await page.title();
  const metadata = await page.evaluate(() => [...document.querySelectorAll(".slide")].map((slide, index) => ({
    index: index + 1,
    title: slide.dataset.title || `Slide ${index + 1}`,
  })));
  const selected = parseSelection(slideSelection, metadata.length);
  const captures = [];

  for (const slideNumber of selected) {
    const meta = metadata[slideNumber - 1];
    await page.evaluate((index) => {
      const slides = [...document.querySelectorAll(".slide")];
      slides.forEach((slide, slideIndex) => slide.classList.toggle("is-active", slideIndex === index));
      const progress = document.querySelector(".progress span");
      if (progress) progress.style.width = `${((index + 1) / slides.length) * 100}%`;
      history.replaceState(null, "", `#/${index + 1}`);
    }, slideNumber - 1);
    await page.waitForTimeout(80);

    const fileName = `slide-${String(slideNumber).padStart(2, "0")}.png`;
    await page.screenshot({ path: path.join(outputDir, fileName), fullPage: false });
    captures.push({ ...meta, fileName });
  }

  const cells = captures.map((item) => `
    <a class="cell" href="${item.fileName}">
      <img src="${item.fileName}" alt="${item.index}. ${item.title}">
      <span>${String(item.index).padStart(2, "0")} / ${metadata.length}</span>
      <strong>${item.title}</strong>
    </a>`).join("\n");
  const contact = `<!doctype html>
<html lang="ko">
<meta charset="utf-8">
<title>${title} contact sheet</title>
<style>
  body { margin: 0; padding: 28px; background: #f5efe3; color: #17201c; font: 14px/1.45 -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", sans-serif; }
  h1 { margin: 0 0 18px; font-size: 24px; }
  .grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
  .cell { color: inherit; text-decoration: none; background: #fffaf0; border: 1px solid rgba(23,32,28,.2); padding: 10px; }
  img { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border: 1px solid rgba(23,32,28,.26); background: #fff; }
  span { display: block; margin-top: 8px; color: #b9853f; font: 700 12px/1.2 ui-monospace, SFMono-Regular, Menlo, monospace; }
  strong { display: block; margin-top: 4px; font-size: 13px; }
</style>
<h1>${title} QA contact sheet (${viewportArg})</h1>
<div class="grid">${cells}</div>
</html>`;
  const contactPath = path.join(outputDir, "contact-sheet.html");
  fs.writeFileSync(contactPath, contact);

  const contactPage = await browser.newPage({ viewport: { width: 1440, height: 1200 }, deviceScaleFactor: 1 });
  await contactPage.goto(`file://${contactPath}`, { waitUntil: "domcontentloaded", timeout: 15000 });
  await contactPage.screenshot({ path: path.join(outputDir, "contact-sheet.png"), fullPage: true });

  await browser.close();
  console.log(JSON.stringify({ outputDir, count: captures.length, contactSheet: contactPath, viewport: viewportArg }));
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
"""


def resolve_node_bin(explicit: str | None) -> str:
    if explicit:
        return explicit
    if DEFAULT_NODE_BIN.is_file():
        return str(DEFAULT_NODE_BIN)
    return shutil.which("node") or "node"


def node_env(explicit_node_modules: str | None) -> dict[str, str]:
    env = os.environ.copy()
    node_modules = explicit_node_modules or str(DEFAULT_NODE_MODULES)
    if Path(node_modules).is_dir():
        existing = env.get("NODE_PATH")
        env["NODE_PATH"] = node_modules if not existing else f"{node_modules}:{existing}"
    return env


def parse_viewports(args: argparse.Namespace) -> list[str]:
    raw = args.viewports or args.viewport
    return [item.strip() for item in raw.split(",") if item.strip()]


def selected_entries(entries: list[dict[str, Any]], deck_id: str) -> list[dict[str, Any]]:
    if deck_id == "all":
        return entries
    selected = [entry for entry in entries if entry["id"] == deck_id]
    if not selected:
        raise SystemExit(f"unknown deck id: {deck_id}")
    return selected


def run_node_script(
    source: str,
    args: list[str],
    *,
    node_bin: str | None,
    node_modules: str | None,
    timeout: int,
    verbose: bool,
) -> subprocess.CompletedProcess[str]:
    with tempfile.TemporaryDirectory(prefix="bbangyippt-node-") as temp_dir:
        script_path = Path(temp_dir) / "script.js"
        script_path.write_text(source, encoding="utf-8")
        try:
            result = subprocess.run(
                [resolve_node_bin(node_bin), str(script_path), *args],
                cwd=ROOT,
                text=True,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=node_env(node_modules),
                timeout=timeout,
            )
        except subprocess.CalledProcessError as exc:
            if exc.stdout:
                print(exc.stdout, file=sys.stderr)
            if exc.stderr:
                print(exc.stderr, file=sys.stderr)
            raise
    if verbose and result.stderr:
        print(result.stderr, file=sys.stderr)
    return result


def audit_built(args: argparse.Namespace, entries: list[dict[str, Any]]) -> None:
    failures: list[tuple[str, dict[str, Any]]] = []
    targets = selected_entries(entries, args.deck)
    for entry in targets:
        for viewport in parse_viewports(args):
            path = deck_file(entry, BUILD_DIR)
            result = run_node_script(
                audit_js(),
                [path.resolve().as_uri(), args.chrome_path, viewport],
                node_bin=args.node_bin,
                node_modules=args.node_modules,
                timeout=args.timeout,
                verbose=args.verbose,
            )
            audit = json.loads(result.stdout)
            deck_failures = [item for item in audit if item["scrollOverflow"] or item["outsideCount"]]
            print(f"layout audit: {entry['id']} {viewport}, {len(audit)} slides, {len(deck_failures)} overflow candidates")
            for item in deck_failures:
                failures.append((entry["id"], item))
                print(
                    f"- {entry['id']} {item['index']:02d} {item['title']}: "
                    f"scroll={item['scrollWidth']}x{item['scrollHeight']} "
                    f"client={item['clientWidth']}x{item['clientHeight']} "
                    f"outside={item['outsideCount']}"
                )
                for outside in item["outside"]:
                    print(f"  · {outside}")
    if failures:
        raise SystemExit(1)


def command_audit_layout(args: argparse.Namespace) -> None:
    entries = build_site()
    audit_built(args, entries)


def snapshot_base_dir(value: str | None) -> Path:
    output_dir = Path(value) if value else DEFAULT_SNAPSHOT_DIR
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    return output_dir


def write_deck_qa_index(deck_id: str, deck_dir: Path, viewports: list[str]) -> None:
    links = "\n".join(
        f'<a class="cell" href="{viewport}/contact-sheet.html"><strong>{viewport}</strong><span>contact sheet</span></a>'
        for viewport in viewports
    )
    html = f"""<!doctype html>
<html lang="ko">
<meta charset="utf-8">
<title>{deck_id} QA</title>
<style>
  body {{ margin: 0; padding: 28px; background: #f5efe3; color: #17201c; font: 14px/1.45 -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", sans-serif; }}
  h1 {{ margin: 0 0 18px; font-size: 24px; }}
  .grid {{ display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }}
  .cell {{ min-height: 82px; display: grid; align-content: center; gap: 6px; padding: 16px; color: inherit; text-decoration: none; background: #fffaf0; border: 1px solid rgba(23,32,28,.2); }}
  strong {{ color: #153d33; font-size: 20px; }}
  span {{ color: #706a60; font-weight: 700; }}
</style>
<h1>{deck_id} QA contact sheets</h1>
<div class="grid">{links}</div>
</html>"""
    deck_dir.mkdir(parents=True, exist_ok=True)
    (deck_dir / "contact-sheet.html").write_text(html, encoding="utf-8")


def capture_snapshots(args: argparse.Namespace, entries: list[dict[str, Any]]) -> None:
    output_base = snapshot_base_dir(args.output_dir)
    viewports = parse_viewports(args)
    for entry in selected_entries(entries, args.deck):
        deck_dir = output_base / entry["id"]
        if deck_dir.exists():
            shutil.rmtree(deck_dir)
        for viewport in viewports:
            viewport_dir = deck_dir / viewport
            result = run_node_script(
                snapshot_js(),
                [
                    deck_file(entry, BUILD_DIR).resolve().as_uri(),
                    args.chrome_path,
                    str(viewport_dir),
                    args.slides,
                    viewport,
                ],
                node_bin=args.node_bin,
                node_modules=args.node_modules,
                timeout=args.timeout,
                verbose=args.verbose,
            )
            summary = json.loads(result.stdout)
            print(f"captured slides: {entry['id']} {summary['viewport']} {summary['count']}")
            print(f"contact sheet: {summary['contactSheet']}")
        write_deck_qa_index(entry["id"], deck_dir, viewports)
        print(f"deck QA index: {deck_dir / 'contact-sheet.html'}")


def command_snapshot(args: argparse.Namespace) -> None:
    entries = build_site()
    capture_snapshots(args, entries)


def command_deploy_qa(args: argparse.Namespace) -> None:
    entries = build_site()
    capture_snapshots(args, entries)
    check_links(BUILD_DIR)
    deploy_built(args)
    print(f"published QA root: {DEFAULT_URL}/qa/")


def command_check_links(_args: argparse.Namespace) -> None:
    build_site()
    check_links(BUILD_DIR)


def command_publish(args: argparse.Namespace) -> None:
    entries = build_site()
    audit_built(args, entries)
    if args.with_snapshots:
        capture_snapshots(args, entries)
    check_links(BUILD_DIR)
    deploy_built(args)
    command_verify(args)


def check_links(base_dir: Path) -> None:
    failures: list[str] = []
    for html_file in sorted(base_dir.rglob("*.html")):
        parser = LinkCollector()
        parser.feed(html_file.read_text(encoding="utf-8"))
        for attr, raw_value in parser.links:
            value = raw_value.split("#", 1)[0].strip()
            if not value:
                continue
            if value.startswith(("http://", "https://", "mailto:", "tel:", "javascript:", "data:")):
                continue
            target = (html_file.parent / urllib.parse.unquote(value)).resolve()
            if raw_value.endswith("/") or value.endswith("/"):
                ok = (target / "index.html").is_file()
            else:
                ok = target.is_file() or target.is_dir()
            if not ok:
                failures.append(f"{html_file.relative_to(base_dir)} {attr}={raw_value}")
    if failures:
        print("broken links:")
        for failure in failures:
            print(f"- {failure}")
        raise SystemExit(1)
    print(f"link check: ok ({base_dir})")


def add_audit_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--chrome-path", default=DEFAULT_CHROME)
    parser.add_argument("--node-bin", default=None)
    parser.add_argument("--node-modules", default=None)
    parser.add_argument("--viewport", default="1280x720", help="Browser viewport, e.g. 1440x900")
    parser.add_argument("--viewports", default=None, help="Comma-separated viewport list, e.g. 1280x720,1440x900")
    parser.add_argument("--deck", default="all", help="Deck id or all")
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--verbose", action="store_true")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate", help="validate registry and deck files")
    validate.set_defaults(func=command_validate)

    build = subparsers.add_parser("build", help="copy source hub to ignored build output")
    build.set_defaults(func=command_build)

    check = subparsers.add_parser("check-links", help="build and check local static links")
    check.set_defaults(func=command_check_links)

    deploy = subparsers.add_parser("deploy", help="build and upload the hub to Raspberry Pi")
    deploy.add_argument("--host", default="root@bbangi")
    deploy.set_defaults(func=command_deploy)

    verify = subparsers.add_parser("verify", help="verify the public URL")
    verify.add_argument("--url", default=DEFAULT_URL)
    verify.add_argument("--host", default=None, help="SSH fallback host, e.g. root@bbangi")
    verify.set_defaults(func=command_verify)

    audit = subparsers.add_parser("audit-layout", help="run a headless Chrome overflow audit")
    add_audit_args(audit)
    audit.set_defaults(func=command_audit_layout)

    snapshot = subparsers.add_parser("snapshot", help="capture slide screenshots and QA contact sheets")
    add_audit_args(snapshot)
    snapshot.add_argument("--slides", default="all", help='Slide list or ranges, e.g. "1,8-12"; default all')
    snapshot.add_argument("--output-dir", default=str(DEFAULT_SNAPSHOT_DIR))
    snapshot.set_defaults(func=command_snapshot)

    deploy_qa = subparsers.add_parser("deploy-qa", help="capture and publish QA screenshots")
    deploy_qa.add_argument("--host", default="root@bbangi")
    add_audit_args(deploy_qa)
    deploy_qa.add_argument("--slides", default="all", help='Slide list or ranges, e.g. "1,8-12"; default all')
    deploy_qa.add_argument("--output-dir", default=str(DEFAULT_SNAPSHOT_DIR))
    deploy_qa.set_defaults(func=command_deploy_qa)

    publish = subparsers.add_parser("publish", help="audit, optionally snapshot, deploy, and verify the public hub")
    publish.add_argument("--host", default="root@bbangi")
    publish.add_argument("--url", default=DEFAULT_URL)
    publish.add_argument("--with-snapshots", action="store_true")
    publish.add_argument("--slides", default="all", help='Snapshot list when --with-snapshots is enabled')
    publish.add_argument("--output-dir", default=str(DEFAULT_SNAPSHOT_DIR))
    add_audit_args(publish)
    publish.set_defaults(func=command_publish)

    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
