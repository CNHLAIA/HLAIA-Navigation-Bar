#!/usr/bin/env bash
# ============================================================
# 同步基础镜像到局域网 Registry
# ============================================================
# 用途：把 Docker Hub 上的基础镜像拉下来，重新打标签后推送到你的局域网 Registry，
#       这样 docker compose build 就能完全在内网环境拉取基础镜像，不再依赖外网。
#
# 使用场景：
#   - 首次部署内网构建链路
#   - 定期（建议每季度）同步基础镜像的安全补丁
#
# 前置条件：
#   1. 运行本脚本的机器能访问 Docker Hub（外网通）
#   2. 局域网 Registry 已部署且可访问（默认 http://<REGISTRY>）
#
# 使用方法：
#   cd 到项目根目录
#   ./scripts/sync-base-images.sh                    # 用默认 Registry（从 .env 读）
#   ./scripts/sync-base-images.sh 192.168.8.6:5000   # 显式指定 Registry
#
# 退出码：
#   0 = 全部成功
#   非 0 = 至少一个镜像同步失败（已同步成功的不会回滚）
# ============================================================

set -euo pipefail

# ---------- 1. 解析 Registry 地址 ----------
REGISTRY="${1:-}"

if [[ -z "$REGISTRY" ]]; then
  # 没传参则从 .env 读 REGISTRY 变量
  if [[ -f .env ]]; then
    # shellcheck disable=SC1091
    REGISTRY="$(grep -E '^REGISTRY=' .env | cut -d'=' -f2- | tr -d '\r' || true)"
  fi
fi

if [[ -z "$REGISTRY" ]]; then
  echo "错误：未指定 Registry 地址。"
  echo "用法：$0 <registry>   例如 $0 192.168.8.6:5000"
  echo "或在 .env 中设置 REGISTRY=192.168.8.6:5000 后直接运行 $0"
  exit 1
fi

echo "============================================================"
echo " 同步基础镜像到局域网 Registry"
echo "============================================================"
echo " 目标 Registry: $REGISTRY"
echo "============================================================"
echo ""

# ---------- 2. 待同步的基础镜像清单 ----------
# 与 Dockerfile / frontend/Dockerfile 中的 FROM 保持一致
# 格式："基础镜像名（不含 Registry 前缀）"
IMAGES=(
  "eclipse-temurin:25-jdk-alpine"
  "eclipse-temurin:25-jre-alpine"
  "node:20-alpine"
  "nginx:alpine"
)

# ---------- 3. 逐个拉取 + 打标签 + 推送 ----------
FAILED=0

for image in "${IMAGES[@]}"; do
  echo "------------------------------------------------------------"
  echo " [$image]"
  echo "------------------------------------------------------------"

  # 拉取（从 Docker Hub）
  if ! docker pull "$image"; then
    echo " ❌ 拉取失败：$image"
    FAILED=$((FAILED + 1))
    continue
  fi

  # 打标签（指向局域网 Registry）
  if ! docker tag "$image" "$REGISTRY/$image"; then
    echo " ❌ 打标签失败：$REGISTRY/$image"
    FAILED=$((FAILED + 1))
    continue
  fi

  # 推送
  if ! docker push "$REGISTRY/$image"; then
    echo " ❌ 推送失败：$REGISTRY/$image"
    FAILED=$((FAILED + 1))
    continue
  fi

  echo " ✅ 同步成功：$REGISTRY/$image"
  echo ""
done

# ---------- 4. 汇总 ----------
echo "============================================================"
if [[ "$FAILED" -eq 0 ]]; then
  echo " ✅ 全部同步成功（${#IMAGES[@]} 个镜像）"
  echo ""
  echo " 下一步：在 .env 中确认 REGISTRY=$REGISTRY，然后运行："
  echo "   docker compose build"
  echo "   docker compose push"
  echo " 构建将完全从局域网 Registry 拉取基础镜像。"
else
  echo " ⚠️  $FAILED / ${#IMAGES[@]} 个镜像同步失败，请检查上方日志。"
  echo " 已成功的镜像不会回滚，可修复后重跑本脚本。"
fi
echo "============================================================"
exit $FAILED
