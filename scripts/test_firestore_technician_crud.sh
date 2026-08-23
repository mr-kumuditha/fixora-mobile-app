#!/usr/bin/env bash
set -euo pipefail

# Compatibility entry point for existing developer workflows. The previous
# script permanently deleted technician documents, which is no longer allowed.
# The roster verifier uses disposable records, restores every changed field,
# and checks exact Auth -> user -> technician linkages.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$script_dir/verify_technician_roster_live.sh" "$@"
