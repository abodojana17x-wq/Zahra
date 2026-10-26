#!/usr/bin/env bash
# تثبيت ملف CI في المكان الذي يقرأه GitHub Actions.
#
# لماذا السكربت؟ لأن دفع ملفات داخل .github/workflows يحتاج صلاحية `workflows`
# في توكن GitHub، وهي غير متاحة لوكيل التنفيذ. الملف محفوظ في ci/github-actions.yml
# بنفس المحتوى، ويُنسخ بأمر واحد من صاحب المستودع (أو أي توكن يملك الصلاحية).
#
#   ./scripts/install_ci.sh            # ينسخ الملف فقط
#   ./scripts/install_ci.sh --commit   # ينسخ ويعمل commit
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC="$ROOT/ci/github-actions.yml"
DST_DIR="$ROOT/.github/workflows"
DST="$DST_DIR/ci.yml"

mkdir -p "$DST_DIR"
cp "$SRC" "$DST"
echo "نُسخ: ci/github-actions.yml → .github/workflows/ci.yml"

if [ "${1:-}" = "--commit" ]; then
  cd "$ROOT"
  git add .github/workflows/ci.yml
  git commit -m "ci: تفعيل GitHub Actions (preflight/unit/eval/apk/guard) — المرحلة 0"
  echo "تم الـ commit. ادفع لبدء التشغيل: git push"
fi
