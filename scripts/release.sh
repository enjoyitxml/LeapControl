#!/usr/bin/env bash
#
# 一键发版：改版本号 → 构建 APK → 写 CHANGELOG → 提交打标签 → 推送 → 发 Release
#
# 用法：
#   ./scripts/release.sh 0.1.1            # 正式发版（含构建）
#   ./scripts/release.sh 0.1.1 --no-build # 跳过构建（只更新版本号与变更记录）
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="${1:-}"
NO_BUILD=0
for arg in "$@"; do
  [[ "$arg" == "--no-build" ]] && NO_BUILD=1
done

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "❌ 版本号格式应为 X.Y.Z，例如：./scripts/release.sh 0.1.1"
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "❌ 工作区有未提交的改动，请先处理："
  git status --short
  exit 1
fi

# versionCode 由版本号推导，保证单调递增（0.1.0 -> 100，1.2.3 -> 10203）
IFS='.' read -r MA MI PA <<< "$VERSION"
VERSION_CODE=$(( MA * 10000 + MI * 100 + PA ))
GRADLE="app/build.gradle.kts"
CURRENT_NAME="$(grep -oE 'versionName = "[^"]+"' "$GRADLE" | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
CURRENT_CODE="$(grep -oE 'versionCode = [0-9]+' "$GRADLE" | head -1 | grep -oE '[0-9]+')"

if [[ -n "${CURRENT_NAME:-}" ]]; then
  OLD_MA=0; OLD_MI=0; OLD_PA=0
  IFS='.' read -r OLD_MA OLD_MI OLD_PA <<< "$CURRENT_NAME"
  OLD_CODE=$(( OLD_MA * 10000 + OLD_MI * 100 + OLD_PA ))
  if [[ "$VERSION_CODE" -le "$OLD_CODE" ]]; then
    echo "❌ 新版本 ${VERSION}（code ${VERSION_CODE}）必须大于当前 ${CURRENT_NAME}（code ${OLD_CODE}）"
    exit 1
  fi
fi

echo "==> 版本 $CURRENT_NAME (code ${CURRENT_CODE})  →  $VERSION (code $VERSION_CODE)"

# ---- 1. 更新构建文件里的版本号 ----
python3 - "$GRADLE" "$VERSION" "$VERSION_CODE" <<'PY'
import re, sys
path, version, code = sys.argv[1], sys.argv[2], sys.argv[3]
src = open(path, encoding="utf-8").read()
src = re.sub(r"versionCode = \d+", f"versionCode = {code}", src, count=1)
src = re.sub(r'versionName = "[^"]*"', f'versionName = "{version}"', src, count=1)
open(path, "w", encoding="utf-8").write(src)
PY

# ---- 2. 构建 ----
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
if [[ "$NO_BUILD" == "1" ]]; then
  echo "==> 跳过构建（--no-build）"
else
  echo "==> 构建中（约 1 分钟）…"
  JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug --no-daemon -q
fi

mkdir -p release
# 附件名用英文名：GitHub 会过滤附件名里的中文，中文名上传后会变成一串残缺字符
APK_DST="release/LeapControl-v${VERSION}-debug.apk"
if [[ -f "$APK_SRC" ]]; then
  cp -f "$APK_SRC" "$APK_DST"
  cp -f "$APK_SRC" "../零控-debug.apk"
  echo "==> APK：${APK_DST}（$(du -h "$APK_DST" | cut -f1)）"
else
  echo "⚠️  没有找到 APK：$APK_SRC"
fi

# ---- 3. 生成变更记录条目 ----
LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
if [[ -z "$LAST_TAG" ]]; then
  RANGE="HEAD"
else
  RANGE="${LAST_TAG}..HEAD"
fi

python3 - "$VERSION" "$RANGE" <<'PY'
import re, subprocess, sys, os, datetime

version, ref_range = sys.argv[1], sys.argv[2]
log = subprocess.run(
    ["git", "log", ref_range, "--pretty=format:%s"],
    capture_output=True, text=True,
).stdout.strip().splitlines()

GROUPS = [
    ("新增", ("feat",)),
    ("修复", ("fix",)),
    ("UI 调整", ("ui",)),
    ("优化", ("perf", "refactor")),
    ("其他", ("chore", "docs", "test", "build", "ci", "style")),
]
buckets = {name: [] for name, _ in GROUPS}
buckets["其他"] = []
for line in log:
    if not line.strip():
        continue
    m = re.match(r"^(\w+)(?:\(([^)]+)\))?:\s*(.+)$", line)
    if not m:
        buckets["其他"].append(line)
        continue
    kind, scope, text = m.groups()
    label = f"{text}"
    for name, kinds in GROUPS:
        if kind in kinds:
            buckets[name].append(label)
            break
    else:
        buckets["其他"].append(line)

parts = []
for name, _ in GROUPS:
    items = buckets.get(name) or []
    if items:
        parts.append(f"### {name}\n" + "\n".join(f"- {i}" for i in items))
body = "\n\n".join(parts) if parts else "- 细节调整"

date = datetime.date.today().isoformat()
entry = f"## [{version}] - {date}\n\n{body}\n"

path = "CHANGELOG.md"
content = open(path, encoding="utf-8").read()
anchor = "## [未发布]"
if anchor in content:
    content = content.replace(anchor, anchor + "\n\n" + entry.rstrip() + "\n", 1)
else:
    # 没有未发布小节就直接插到第一条版本记录前
    first = re.search(r"^## \[", content, re.M)
    pos = first.start() if first else len(content)
    content = content[:pos] + entry + "\n" + content[pos:]
open(path, "w", encoding="utf-8").write(content)
print(f"==> CHANGELOG 已更新（{len(log)} 条提交）")
PY

# 提取本次版本的说明，供 Release 使用
NOTES_FILE="$(mktemp)"
python3 - "$VERSION" "$NOTES_FILE" <<'PY'
import re, sys
version, out = sys.argv[1], sys.argv[2]
text = open("CHANGELOG.md", encoding="utf-8").read()
m = re.search(rf"## \[{re.escape(version)}\][^\n]*\n(.*?)(?=\n## \[|\Z)", text, re.S)
open(out, "w", encoding="utf-8").write((m.group(1).strip() if m else "")[:4000])
PY

# ---- 4. 提交 + 打标签 ----
git add CHANGELOG.md app/build.gradle.kts README.md 2>/dev/null || true
git add -A CHANGELOG.md app/build.gradle.kts
git commit -q -m "chore(release): v${VERSION}"
git tag -a "v${VERSION}" -m "版本 v${VERSION}"
echo "==> 已提交并打标签 v${VERSION}"

# ---- 5. 推送（优先 git，失败则走 API 通道）----
if git push origin main --follow-tags 2>/dev/null; then
  echo "==> 已用 git push 推送"
else
  echo "==> git push 不可用（本机网络拦截 github.com git 协议），改用 API 通道"
  if [[ -n "${GH_TOKEN:-}" || -n "${GITHUB_TOKEN:-}" ]]; then
    python3 scripts/gh-sync.py --tag "v${VERSION}"
  else
    echo "⚠️  需要令牌才能同步，稍后执行："
    echo "    export GH_TOKEN=ghp_xxxx && python3 scripts/gh-sync.py --tag v${VERSION}"
  fi
fi

# ---- 6. 创建 GitHub Release 并上传 APK ----
if [[ -f "${APK_DST:-}" ]] && [[ -n "${GH_TOKEN:-}" || -n "${GITHUB_TOKEN:-}" ]]; then
  python3 scripts/gh-release.py "v${VERSION}" "$APK_DST" --notes-file "$NOTES_FILE"
else
  echo "ℹ️  如需发布到 GitHub Release："
  echo "    export GH_TOKEN=ghp_xxxx"
  echo "    python3 scripts/gh-release.py v${VERSION} ${APK_DST:-<apk路径>}"
fi

rm -f "$NOTES_FILE"
echo "✅ 发版完成 v${VERSION}"
