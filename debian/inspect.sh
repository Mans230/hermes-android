#!/usr/bin/env bash
# Read-only diagnostics. Does not read .env, tokens, config, process arguments or logs.
set -u
printf 'Hermes executable:\n'
command -v hermes || true
printf '\nHermes version:\n'
if command -v hermes >/dev/null 2>&1; then hermes --version 2>&1 || true; fi
printf '\nSystem Hermes services:\n'
systemctl list-units --all --type=service --no-pager --plain 'hermes*' 2>/dev/null || true
printf '\nUser Hermes services:\n'
systemctl --user list-units --all --type=service --no-pager --plain 'hermes*' 2>/dev/null || true
printf '\nLocal API reachability (no authentication, no configuration changes):\n'
if command -v curl >/dev/null 2>&1; then
  curl --silent --show-error --max-time 4 --output /dev/null --write-out 'HTTP %{http_code}\n' http://127.0.0.1:8642/health || true
fi
