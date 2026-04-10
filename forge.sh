#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -eq 0 ]]; then
  echo "Usage: ./forge.sh <forge arguments...>"
  echo "Example:"
  echo "  ./forge.sh run autopilot --project /path/to/repo --goal '实现一个俄罗斯方块' --constraints '需要网页版'"
  exit 1
fi

cd "$ROOT_DIR"

has_ollama_model_override=false
for arg in "$@"; do
  if [[ "$arg" == --devflow.ollama.model=* || "$arg" == --devflow.ollama.models.*=* ]]; then
    has_ollama_model_override=true
    break
  fi
done

default_model_args=()
if [[ "$has_ollama_model_override" == false ]]; then
  # 默认用 coder 路线跑整条链，避免文档阶段回落到 gemma4:26b 后在长输出上频繁截断。
  # 如果调用方显式传了 --devflow.ollama.model / --devflow.ollama.models.*，这里不会覆盖用户选择。
  default_model_args+=(
    --devflow.ollama.model=qwen3-coder:30b
    --devflow.ollama.models.analysis=qwen3-coder:30b
    --devflow.ollama.models.prd=qwen3-coder:30b
    --devflow.ollama.models.design=qwen3-coder:30b
    --devflow.ollama.models.implementation=qwen3-coder:30b
    --devflow.ollama.models.code-review=qwen3-coder:30b
    --devflow.ollama.models.test=qwen3-coder:30b
    --devflow.ollama.models.test-case-design=qwen3-coder:30b
    --devflow.ollama.models.validation-strategy=qwen3-coder:30b
    --devflow.ollama.models.diagnosis=qwen3-coder:30b
    --devflow.ollama.models.repair=qwen3-coder:30b
    --devflow.ollama.models.supervisor=qwen3-coder:30b
  )
fi

if [[ ${#default_model_args[@]} -gt 0 ]]; then
  exec mvn -q spring-boot:run -Dspring-boot.run.arguments="$* ${default_model_args[*]}"
fi

exec mvn -q spring-boot:run -Dspring-boot.run.arguments="$*"
