#!/usr/bin/env sh
# Creates .env from .env.example, replacing every __GENERATE__ placeholder with a random secret.
# Never overwrites an existing .env.
set -eu

root="$(cd "$(dirname "$0")/../.." && pwd)"
target="$root/.env"

if [ -f "$target" ]; then
  echo ".env already exists; leaving it unchanged."
  exit 0
fi

umask 077
tmp="$(mktemp)"
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    *=__GENERATE__) printf '%s=%s\n' "${line%%=*}" "$(openssl rand -hex 32)" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < "$root/.env.example" > "$tmp"
mv "$tmp" "$target"
echo "Created .env with freshly generated secrets."
