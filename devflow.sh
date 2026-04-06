#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -eq 0 ]]; then
  echo "Usage: ./devflow.sh <devflow arguments...>"
  echo "Example:"
  echo "  ./devflow.sh run autopilot --project /path/to/repo --goal '实现一个俄罗斯方块' --constraints '需要网页版'"
  exit 1
fi

cd "$ROOT_DIR"
exec mvn -q spring-boot:run -Dspring-boot.run.arguments="$*"
