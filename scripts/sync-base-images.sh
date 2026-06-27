#!/usr/bin/env bash
# 同步基础镜像到局域网 Registry，使后续 docker compose build 完全在内网完成。
# 需在能访问 Docker Hub 的机器上运行；建议每季度重跑以拉取安全补丁。
#
# 用法（项目根目录）：
#   ./scripts/sync-base-images.sh                # 从 .env 读 REGISTRY
#   ./scripts/sync-base-images.sh <registry>     # 显式指定，如 my-reg:5000

set -euo pipefail

# Registry 来源：命令行参数 > .env 文件
REGISTRY="${1:-}"
if [[ -z "$REGISTRY" && -f .env ]]; then
    REGISTRY="$(grep -E '^REGISTRY=' .env | cut -d'=' -f2- | tr -d '\r' || true)"
fi

if [[ -z "$REGISTRY" ]]; then
    echo "错误：未指定 Registry。用法：$0 <registry>" >&2
    exit 1
fi

# 与 Dockerfile / frontend/Dockerfile 的 FROM 保持一致
IMAGES=(
    "eclipse-temurin:25-jdk-alpine"
    "eclipse-temurin:25-jre-alpine"
    "node:20-alpine"
    "nginx:alpine"
)

echo "同步基础镜像到 $REGISTRY ..."
FAILED=0

for image in "${IMAGES[@]}"; do
    echo ""
    echo "[$image]"
    # 单个失败不影响其他：用 || 而非 set -e 直接退出
    if docker pull "$image" \
        && docker tag "$image" "$REGISTRY/$image" \
        && docker push "$REGISTRY/$image"; then
        echo "✅ $REGISTRY/$image"
    else
        echo "❌ $image"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
if [[ "$FAILED" -eq 0 ]]; then
    echo "✅ 全部同步成功（${#IMAGES[@]} 个）"
else
    echo "⚠️  $FAILED / ${#IMAGES[@]} 失败，可修复后重跑（已成功的不受影响）"
fi
exit $FAILED
