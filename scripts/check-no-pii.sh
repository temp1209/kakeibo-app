#!/usr/bin/env bash
set -euo pipefail

# scripts/check-no-pii.sh
#
# このリポジトリはポートフォリオとして公開するパブリックリポジトリのため、
# 個人情報(メールアドレス・氏名など)を追跡対象ファイルに含めないことを検査する。
#
# 重要な設計方針:
# - このスクリプト自体には実際の個人情報(メールアドレス・氏名)を一切書かない。
#   汎用的なメールアドレスの正規表現のみをハードコードし、氏名など固有の語句は
#   環境変数 PII_DENYLIST_TERMS(改行区切り)経由でのみ渡す。実行者のローカル環境変数、
#   またはCI側のGitHub Secretsに置き、リポジトリには絶対にコミットしない。
# - 検出時も「一致した内容そのもの」は出力しない。file:line の位置のみ表示する。
#   (パブリックリポジトリではGitHub Actionsのログも誰でも閲覧できるため、
#    検出ログに内容を出すこと自体が新たな漏洩経路になり得るため)
#
# Usage:
#   ./scripts/check-no-pii.sh
#   PII_DENYLIST_TERMS=$'森信太朗\nMORI SHINTARO' ./scripts/check-no-pii.sh

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

fail=0
self_path="scripts/check-no-pii.sh"

echo "[check-no-pii] scanning tracked files for email-like strings..."
email_pattern='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
locations="$(git grep -InE "$email_pattern" -- . ":!$self_path" 2>/dev/null | cut -d: -f1-2 || true)"
if [ -n "$locations" ]; then
  echo "NG: メールアドレスらしき文字列が見つかりました（内容は伏せます。該当箇所）:"
  echo "$locations"
  fail=1
fi

if [ -n "${PII_DENYLIST_TERMS:-}" ]; then
  echo "[check-no-pii] scanning tracked files for denylisted terms (from PII_DENYLIST_TERMS env)..."
  while IFS= read -r term; do
    [ -z "$term" ] && continue
    term_locations="$(git grep -InF "$term" -- . ":!$self_path" 2>/dev/null | cut -d: -f1-2 || true)"
    if [ -n "$term_locations" ]; then
      echo "NG: 登録済みの語句と一致する文字列が見つかりました（内容は伏せます。該当箇所）:"
      echo "$term_locations"
      fail=1
    fi
  done <<< "$PII_DENYLIST_TERMS"
else
  echo "[check-no-pii] PII_DENYLIST_TERMS が未設定のため、氏名など固有語句のチェックはスキップしました。"
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "個人情報の可能性がある文字列が見つかりました。コミット/push前に削除してください。"
  exit 1
fi

echo "[check-no-pii] OK: 個人情報らしき文字列は見つかりませんでした。"
