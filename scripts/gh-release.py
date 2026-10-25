#!/usr/bin/env python3
"""为指定标签创建 GitHub Release，并上传 APK 作为附件。

用法：
    export GH_TOKEN=ghp_xxxx
    python3 scripts/gh-release.py v0.1.1 release/零控-v0.1.1-debug.apk
    python3 scripts/gh-release.py v0.1.1 <apk路径> --notes-file release-notes.md
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request

OWNER, REPO = "enjoyitxml", "LeapBuddy"
UPLOAD_HOST = "uploads.github.com"


def request(method: str, url: str, token: str, payload: dict | None = None,
            data: bytes | None = None, headers: dict | None = None) -> dict:
    hdrs = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json"}
    if payload is not None:
        hdrs["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            body = r.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        raise SystemExit(f"❌ HTTP {e.code} {method} {url}\n   {e.read().decode()[:200]}")


def ensure_release(tag: str, token: str, notes: str, draft: bool, prerelease: bool) -> dict:
    base = f"https://api.github.com/repos/{OWNER}/{REPO}/releases"
    # 已存在则复用
    existing = request("GET", f"{base}/tags/{tag}", token)
    if "id" in existing:
        print(f"Release {tag} 已存在（id={existing['id']}），复用")
        return existing
    return request("POST", base, token, {
        "tag_name": tag,
        "name": tag,
        "body": notes,
        "draft": draft,
        "prerelease": prerelease,
    })


def upload_asset(release: dict, path: str, token: str) -> None:
    name = os.path.basename(path)
    size = os.path.getsize(path)
    ctype = mimetypes.guess_type(name)[0] or "application/vnd.android.package-archive"
    url = f"https://{UPLOAD_HOST}/repos/{OWNER}/{REPO}/releases/{release['id']}/assets?name={urllib.parse.quote(name)}"
    with open(path, "rb") as f:
        data = f.read()
    print(f"上传附件 {name}（{size / 1024 / 1024:.1f} MB）…")
    request(
        "POST", url, token, data=data,
        headers={"Content-Type": ctype, "Content-Length": str(len(data))},
    )
    print(f"  ✅ {name} 上传完成")


def main() -> int:
    ap = argparse.ArgumentParser(description="创建 GitHub Release 并上传 APK")
    ap.add_argument("tag", help="标签名，如 v0.1.1")
    ap.add_argument("assets", nargs="*", help="要上传的文件路径")
    ap.add_argument("--notes", help="Release 说明文字")
    ap.add_argument("--notes-file", help="从文件读取 Release 说明")
    ap.add_argument("--draft", action="store_true", help="创建为草稿")
    ap.add_argument("--prerelease", action="store_true", help="标记为预发布")
    args = ap.parse_args()

    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("❌ 缺少令牌：请先 export GH_TOKEN=ghp_xxxx", file=sys.stderr)
        return 1

    notes = args.notes or ""
    if args.notes_file:
        notes = open(args.notes_file, encoding="utf-8").read()

    release = ensure_release(args.tag, token, notes, args.draft, args.prerelease)
    print(f"Release: {release.get('html_url')}")

    for asset in args.assets:
        if not os.path.isfile(asset):
            print(f"⚠️  跳过不存在的文件：{asset}")
            continue
        upload_asset(release, asset, token)

    print("✅ 完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
