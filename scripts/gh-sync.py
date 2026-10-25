#!/usr/bin/env python3
"""把本地仓库同步到 GitHub（本机 github.com git 协议被拦截时的替代通道）。

为什么需要它：
    本机代理会拦掉 github.com 的 443/22 端口，git push 报 CONNECT tunnel failed，
    但 api.github.com 是通的，所以改用 GitHub Git Data API 提交。

工作原理：
    1. blob 的内容哈希算法与本地 git 完全一致，因此可以直接比对
       `git ls-files -s` 输出的 blob sha 与远端 tree，只上传变化的文件；
    2. 用本地 HEAD 的完整文件列表构造 tree，远端多出来的路径自动删除；
    3. 新提交的 parent 指向远端 main，**历史连续累加**，不会像全量重建那样丢记录。

用法：
    export GH_TOKEN=ghp_xxxx
    python3 scripts/gh-sync.py                 # 同步 main
    python3 scripts/gh-sync.py --tag v0.1.0    # 同时把本地标签推上去
    python3 scripts/gh-sync.py --dry-run       # 只看会改什么，不真提交
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

OWNER, REPO = "enjoyitxml", "LeapBuddy"
API = f"https://api.github.com/repos/{OWNER}/{REPO}"
BRANCH = "main"


class GitError(Exception):
    pass


def sh(*args: str, cwd: str | None = None) -> str:
    out = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    if out.returncode != 0:
        raise GitError(out.stderr.strip() or " ".join(args))
    return out.stdout


def api(method: str, path: str, payload: dict | None = None, token: str = "", absolute: bool = False) -> dict:
    url = path if absolute else API + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        raise GitError(f"HTTP {e.code} on {method} {path}: {e.read().decode()[:200]}")


def local_files(root: str) -> dict[str, tuple[str, str]]:
    """返回 {path: (mode, blob_sha)}，只统计 git 已跟踪的文件。"""
    result: dict[str, tuple[str, str]] = {}
    for line in sh("git", "ls-files", "-s", cwd=root).strip().splitlines():
        meta, path = line.split("\t", 1)
        mode, sha, _stage = meta.split()
        result[path] = (mode, sha)
    return result


def remote_files(token: str) -> dict[str, tuple[str, str]]:
    tree = api("GET", f"/git/trees/{BRANCH}?recursive=1", token=token)
    return {
        e["path"]: (e["mode"], e["sha"])
        for e in tree.get("tree", [])
        if e["type"] == "blob"
    }


def collect_message(root: str, head: str) -> str:
    """把本地未推送的提交信息汇总成一条提交说明。"""
    subjects = [
        line
        for line in sh("git", "log", "--format=%s", head, cwd=root).strip().splitlines()
        if line.strip()
    ]
    if not subjects:
        return "chore: 同步项目改动"
    if len(subjects) == 1:
        return subjects[0]
    body = "\n".join(f"- {s}" for s in subjects)
    return f"{subjects[0]}\n\n本次包含 {len(subjects)} 个改动：\n{body}"


def main() -> int:
    ap = argparse.ArgumentParser(description="通过 GitHub API 增量推送本地改动")
    ap.add_argument("--root", default=os.getcwd(), help="仓库根目录")
    ap.add_argument("--tag", help="同时推送本地同名标签")
    ap.add_argument("--dry-run", action="store_true", help="只预览，不提交")
    args = ap.parse_args()

    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        print("❌ 缺少令牌：请先 export GH_TOKEN=ghp_xxxx", file=sys.stderr)
        return 1

    root = os.path.abspath(args.root)
    try:
        local = local_files(root)
        remote = remote_files(token)
        head = sh("git", "rev-parse", "HEAD", cwd=root).strip()
    except GitError as e:
        print(f"❌ {e}", file=sys.stderr)
        return 1

    added = sorted(p for p in local if p not in remote)
    changed = sorted(
        p for p in local if p in remote and remote[p] != local[p]
    )
    removed = sorted(p for p in remote if p not in local)

    print(f"本地 {len(local)} 个文件 / 远端 {len(remote)} 个文件")
    print(f"  新增 {len(added)} · 修改 {len(changed)} · 删除 {len(removed)}")
    for p in added[:20]:
        print(f"    + {p}")
    for p in changed[:20]:
        print(f"    ~ {p}")
    for p in removed[:20]:
        print(f"    - {p}")

    if not (added or changed or removed):
        print("✅ 已是最新，无需同步")
        return 0
    if args.dry_run:
        print("（dry-run，未提交）")
        return 0

    # 1. 上传变化的 blob
    print(f"上传 {len(added) + len(changed)} 个 blob…")
    tree_entries: list[dict] = []
    for i, path in enumerate(added + changed, 1):
        mode, _sha = local[path]
        with open(os.path.join(root, path), "rb") as f:
            content = base64.b64encode(f.read()).decode()
        blob = api("POST", "/git/blobs", {"content": content, "encoding": "base64"}, token=token)
        tree_entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob["sha"]})
        if i % 20 == 0 or i == len(added) + len(changed):
            print(f"  {i}/{len(added) + len(changed)}")

    # 2. 远端多出来的路径：显式置空即为删除
    for path in removed:
        tree_entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})

    # 3. 未变化的文件也要带上，否则会被当成删除
    for path, (mode, sha) in local.items():
        if path not in added and path not in changed:
            tree_entries.append({"path": path, "mode": mode, "type": "blob", "sha": sha})

    print("创建 tree…")
    new_tree = api("POST", "/git/trees", {"tree": tree_entries}, token=token)["sha"]

    parent = api("GET", f"/git/ref/heads/{BRANCH}", token=token)["object"]["sha"]
    message = collect_message(root, head)
    print("创建 commit…")
    commit = api(
        "POST",
        "/git/commits",
        {
            "message": message,
            "tree": new_tree,
            "parents": [parent],
            "author": {"name": "enjoyitxml", "email": "enjoyitxml@users.noreply.github.com"},
            "committer": {"name": "enjoyitxml", "email": "enjoyitxml@users.noreply.github.com"},
        },
        token=token,
    )
    print(f"  {commit['sha'][:8]} {message.splitlines()[0]}")

    api("PATCH", f"/git/refs/heads/{BRANCH}", {"sha": commit["sha"], "force": True}, token=token)
    print(f"✅ {BRANCH} 已更新到 {commit['sha'][:8]}")

    if args.tag:
        tag_sha = sh("git", "rev-parse", args.tag, cwd=root).strip()
        api(
            "POST",
            "/git/refs",
            {"ref": f"refs/tags/{args.tag}", "sha": commit["sha"]},
            token=token,
        )
        print(f"✅ 标签 {args.tag} 已推送（本地对象 {tag_sha[:8]} → 远端提交 {commit['sha'][:8]}）")

    return 0


if __name__ == "__main__":
    sys.exit(main())
