#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import tempfile
import urllib.request
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = REPO_ROOT / "extension" / "chrome" / "vendor" / "nsfw"

ASSETS = (
    {
        "path": "tf.min.js",
        "source_name": "tf.es2017.min.js",
        "url": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.es2017.min.js",
        "sha256": "a1f9e32fd9b373cf394905050cf0c8c66238b34925846c065db00d2a34d1c040",
        "version": "4.22.0",
        "component": "TensorFlow.js ES2017 MV3 bundle",
    },
    {
        "path": "wasm/tf-backend-wasm.min.js",
        "source_name": "tf-backend-wasm.es2017.min.js",
        "url": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tf-backend-wasm.es2017.min.js",
        "sha256": "bdd8c3c1fb0b37601bc2a122efa20b8d78e2936d104b03a44591be4188c63f65",
        "version": "4.22.0",
        "component": "TensorFlow.js WASM ES2017 backend",
    },
    {
        "path": "wasm/tfjs-backend-wasm.wasm",
        "source_name": "tfjs-backend-wasm.wasm",
        "url": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tfjs-backend-wasm.wasm",
        "sha256": "70a5d516060464e5269f01c74bac1772d6b8ab6cb612acf16b5cdaf61f78d892",
        "version": "4.22.0",
        "component": "TensorFlow.js WASM baseline binary",
    },
    {
        "path": "wasm/tfjs-backend-wasm-simd.wasm",
        "source_name": "tfjs-backend-wasm-simd.wasm",
        "url": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tfjs-backend-wasm-simd.wasm",
        "sha256": "77ebb28a6d34f371dbbf2086b7f2de8994acd8ea5a3cf1fa24d2c26c840cac7b",
        "version": "4.22.0",
        "component": "TensorFlow.js WASM SIMD binary",
    },
    {
        "path": "wasm/tfjs-backend-wasm-threaded-simd.wasm",
        "source_name": "tfjs-backend-wasm-threaded-simd.wasm",
        "url": "https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tfjs-backend-wasm-threaded-simd.wasm",
        "sha256": "c052228d4bef185c27bbe59a9e029570c78bbb9f08b3cb46b597851650373de2",
        "version": "4.22.0",
        "component": "TensorFlow.js WASM threaded SIMD compatibility binary",
    },
    {
        "path": "model/model.json",
        "source_name": "chungmaru-nsfw-model.json",
        "url": "https://raw.githubusercontent.com/infinitered/nsfwjs/v4.2.1/models/mobilenet_v2/model.json",
        "sha256": "11846416217e68bf1eb7b0e651bcfd305973566453c63275bbd16766ab089979",
        "version": "v4.2.1",
        "component": "NSFWJS MobileNetV2 model",
    },
    {
        "path": "model/group1-shard1of1",
        "source_name": "chungmaru-nsfw-weights",
        "url": "https://raw.githubusercontent.com/infinitered/nsfwjs/v4.2.1/models/mobilenet_v2/group1-shard1of1",
        "sha256": "8e7dddbb16acacc1bf1601b1b8a761e730ff934b7f2d7771312b2f000e5f5f13",
        "version": "v4.2.1",
        "component": "NSFWJS MobileNetV2 weights",
    },
    {
        "path": "licenses/TENSORFLOW_JS_LICENSE",
        "source_name": "chungmaru-tfjs-LICENSE",
        "url": "https://raw.githubusercontent.com/tensorflow/tfjs/tfjs-v4.22.0/LICENSE",
        "sha256": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
        "version": "4.22.0",
        "component": "TensorFlow.js license",
    },
    {
        "path": "licenses/NSFWJS_LICENSE",
        "source_name": "chungmaru-nsfw-LICENSE",
        "url": "https://raw.githubusercontent.com/infinitered/nsfwjs/v4.2.1/LICENSE",
        "sha256": "2f7fe40b4f0d97020f0a0dc2214d637471f6204e565eb83e9fd414f0e5287d94",
        "version": "v4.2.1",
        "component": "NSFWJS license",
    },
)


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def stage_asset(asset: dict[str, str], source_dir: Path | None, stage_dir: Path) -> Path:
    staged = stage_dir / asset["path"]
    staged.parent.mkdir(parents=True, exist_ok=True)
    source = source_dir / asset["source_name"] if source_dir else None
    if source and source.exists():
        shutil.copyfile(source, staged)
    else:
        request = urllib.request.Request(
            asset["url"],
            headers={"User-Agent": "Chungmaru-NSFW-Asset-Vendor/1.0"},
        )
        with urllib.request.urlopen(request, timeout=60) as response, staged.open("wb") as output:
            shutil.copyfileobj(response, output)
    actual = file_sha256(staged)
    if actual != asset["sha256"]:
        raise RuntimeError(
            f"checksum mismatch for {asset['path']}: expected={asset['sha256']} actual={actual}"
        )
    return staged


def vendor_assets(output_dir: Path, source_dir: Path | None) -> None:
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    stage_root = Path(tempfile.mkdtemp(prefix="chungmaru-nsfw-vendor-"))
    try:
        manifest_assets = []
        for asset in ASSETS:
            staged = stage_asset(asset, source_dir, stage_root)
            manifest_assets.append(
                {
                    "component": asset["component"],
                    "path": asset["path"],
                    "version": asset["version"],
                    "url": asset["url"],
                    "sha256": asset["sha256"],
                    "bytes": staged.stat().st_size,
                }
            )

        manifest = {
            "schemaVersion": 1,
            "runtime": "TensorFlow.js 4.22.0",
            "model": "NSFWJS MobileNetV2 v4.2.1",
            "inputSize": 224,
            "classes": ["Drawing", "Hentai", "Neutral", "Porn", "Sexy"],
            "assets": manifest_assets,
        }
        (stage_root / "asset-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        if output_dir.exists():
            shutil.rmtree(output_dir)
        shutil.copytree(stage_root, output_dir)
    finally:
        shutil.rmtree(stage_root, ignore_errors=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Vendor pinned Chungmaru NSFW browser assets.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=None,
        help="Optional directory containing the source_name files; otherwise download pinned URLs.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    vendor_assets(args.output_dir.resolve(), args.source_dir.resolve() if args.source_dir else None)
    print(json.dumps({"ok": True, "outputDir": str(args.output_dir.resolve())}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
