# 同步基础镜像到局域网 Registry，使后续 docker compose build 完全在内网完成。
# 需在能访问 Docker Hub 的机器上运行；建议每季度重跑以拉取安全补丁。
#
# 用法（项目根目录）：
#   .\scripts\sync-base-images.ps1                # 从 .env 读 REGISTRY
#   .\scripts\sync-base-images.ps1 <registry>     # 显式指定，如 my-reg:5000
#
# 遇执行策略限制：powershell -ExecutionPolicy Bypass -File .\scripts\sync-base-images.ps1

$ErrorActionPreference = "Stop"

# Registry 来源：命令行参数 > .env 文件
$Registry = ""
if ($args.Count -ge 1) {
    $Registry = $args[0]
} elseif (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.StartsWith("REGISTRY=")) {
            $Registry = $line.Substring("REGISTRY=".Length).Trim()
        }
    }
}

if (-not $Registry) {
    Write-Host "错误：未指定 Registry。用法：.\sync-base-images.ps1 <registry>" -ForegroundColor Red
    exit 1
}

# 与 Dockerfile / frontend/Dockerfile 的 FROM 保持一致
$Images = @(
    "eclipse-temurin:25-jdk-alpine",
    "eclipse-temurin:25-jre-alpine",
    "node:20-alpine",
    "nginx:alpine"
)

Write-Host "同步基础镜像到 $Registry ..."
$Failed = 0

foreach ($image in $Images) {
    Write-Host "`n[$image]"
    try {
        # 单个失败不影响其他：try/catch 隔离每个镜像的错误
        docker pull $image 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "pull 失败" }
        docker tag $image "$Registry/$image" 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "tag 失败" }
        docker push "$Registry/$image" 2>&1 | Write-Host
        if ($LASTEXITCODE -ne 0) { throw "push 失败" }
        Write-Host "✅ $Registry/$image" -ForegroundColor Green
    } catch {
        Write-Host "❌ $image ($_)" -ForegroundColor Red
        $Failed++
    }
}

if ($Failed -eq 0) {
    Write-Host "`n✅ 全部同步成功（$($Images.Count) 个）" -ForegroundColor Green
} else {
    Write-Host "`n⚠️  $Failed / $($Images.Count) 失败，可修复后重跑（已成功的不受影响）" -ForegroundColor Yellow
}
exit $Failed
