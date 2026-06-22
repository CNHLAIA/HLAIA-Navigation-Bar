# ============================================================
# 同步基础镜像到局域网 Registry（Windows PowerShell 版）
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
#   2. 局域网 Registry 已部署且可访问
#
# 使用方法（在项目根目录下）：
#   .\scripts\sync-base-images.ps1                          # 从 .env 读 REGISTRY
#   .\scripts\sync-base-images.ps1 192.168.8.6:5000         # 显式指定 Registry
#
# 如遇执行策略限制，用以下方式放行（仅当前会话生效，安全）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\sync-base-images.ps1
# ============================================================

# strict mode + 友好错误停止（cmdlet 错误也抛异常，便于 try/catch）
$ErrorActionPreference = "Stop"

# ---------- 1. 解析 Registry 地址 ----------
$Registry = ""
if ($args.Count -ge 1) {
    $Registry = $args[0]
}
elseif (Test-Path .env) {
    # 从 .env 文件读 REGISTRY 变量
    # 注意：.env 是 KEY=VALUE 文本，不是 PowerShell 脚本，逐行解析
    Get-Content .env | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.StartsWith("REGISTRY=")) {
            $Registry = $line.Substring("REGISTRY=".Length).Trim()
        }
    }
}

if (-not $Registry) {
    Write-Host "错误：未指定 Registry 地址。" -ForegroundColor Red
    Write-Host "用法：.\sync-base-images.ps1 <registry>   例如 .\sync-base-images.ps1 192.168.8.6:5000"
    Write-Host "或在 .env 中设置 REGISTRY=192.168.8.6:5000 后直接运行 .\sync-base-images.ps1"
    exit 1
}

Write-Host "============================================================"
Write-Host " 同步基础镜像到局域网 Registry"
Write-Host "============================================================"
Write-Host " 目标 Registry: $Registry"
Write-Host "============================================================"
Write-Host ""

# ---------- 2. 待同步的基础镜像清单 ----------
# 与 Dockerfile / frontend/Dockerfile 中的 FROM 保持一致
$Images = @(
    "eclipse-temurin:25-jdk-alpine",
    "eclipse-temurin:25-jre-alpine",
    "node:20-alpine",
    "nginx:alpine"
)

# ---------- 3. 逐个拉取 + 打标签 + 推送 ----------
# 注意：拉取/推送本身不放进 try/catch——$ErrorActionPreference=Stop 会让它们失败时
# 直接抛错，被外层 catch 接住。每个镜像独立 try/catch，单个失败不影响其他镜像。
$Failed = 0

foreach ($image in $Images) {
    Write-Host "------------------------------------------------------------"
    Write-Host " [$image]"
    Write-Host "------------------------------------------------------------"

    try {
        # 拉取（从 Docker Hub）
        # 2>&1 把 docker 的 stderr 也并入 stdout 显示，便于排查
        docker pull $image 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "docker pull 失败（exit $LASTEXITCODE）" }

        # 打标签（指向局域网 Registry）
        docker tag $image "$Registry/$image" 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "docker tag 失败（exit $LASTEXITCODE）" }

        # 推送
        docker push "$Registry/$image" 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "docker push 失败（exit $LASTEXITCODE）" }

        Write-Host " ✅ 同步成功：$Registry/$image" -ForegroundColor Green
    }
    catch {
        Write-Host " ❌ 同步失败：$image ($_) " -ForegroundColor Red
        $Failed++
    }
    Write-Host ""
}

# ---------- 4. 汇总 ----------
Write-Host "============================================================"
if ($Failed -eq 0) {
    Write-Host " ✅ 全部同步成功（$($Images.Count) 个镜像）" -ForegroundColor Green
    Write-Host ""
    Write-Host " 下一步：在 .env 中确认 REGISTRY=$Registry，然后运行："
    Write-Host "   docker compose build"
    Write-Host "   docker compose push"
    Write-Host " 构建将完全从局域网 Registry 拉取基础镜像。"
}
else {
    Write-Host " ⚠️  $Failed / $($Images.Count) 个镜像同步失败，请检查上方日志。" -ForegroundColor Yellow
    Write-Host " 已成功的镜像不会回滚，可修复后重跑本脚本。"
}
Write-Host "============================================================"
exit $Failed
