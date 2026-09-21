<#
.SYNOPSIS
    交互式设置 CURSEFORGE_TOKEN 用户环境变量，并调用 CurseForge API 验证。
.DESCRIPTION
    运行本脚本后粘贴从 console.curseforge.com 生成的 API Key（$2a$10$... 格式）。
    Token 会写入用户级环境变量 CURSEFORGE_TOKEN，同时对当前会话生效。
    输入内容不回显明文。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\set-curseforge-token.ps1
#>

$ErrorActionPreference = 'Stop'

Write-Host "请输入 CurseForge API Key（console.curseforge.com -> API Keys 生成）：" -ForegroundColor Cyan
$secure = Read-Host -AsSecureString "Token"
$token = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Error "Token 为空，已取消。"
    exit 1
}
$token = $token.Trim()

# 写入用户级环境变量 + 当前进程
[Environment]::SetEnvironmentVariable('CURSEFORGE_TOKEN', $token, 'User')
$env:CURSEFORGE_TOKEN = $token

$masked = $token.Substring(0, [Math]::Min(6, $token.Length)) + '...' + $token.Substring($token.Length - 4)
Write-Host "已写入用户环境变量 CURSEFORGE_TOKEN ($masked)" -ForegroundColor Green

# 验证：调用公开 games 接口（只需有效 API key，无需特定项目权限）
Write-Host "正在验证 token ..." -ForegroundColor Cyan
try {
    $headers = @{ 'x-api-key' = $token; 'Accept' = 'application/json' }
    $resp = Invoke-RestMethod -Uri 'https://api.curseforge.com/v1/games' -Headers $headers -TimeoutSec 30
    $mc = $resp.data | Where-Object { $_.id -eq 432 }
    if ($mc) {
        Write-Host "验证成功！API key 有效（已能访问 $($mc.name) 数据）。" -ForegroundColor Green
    } else {
        Write-Host "验证成功（API 可访问，返回 $($resp.data.Count) 个游戏）。" -ForegroundColor Green
    }
    exit 0
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "验证失败 (HTTP $code)：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '请确认 key 从 console.curseforge.com 复制完整（通常为 $2a$10$... 开头的长串）。'
    exit 2
}