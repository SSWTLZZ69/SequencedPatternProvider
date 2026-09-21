<#
.SYNOPSIS
    上传 mod jar 到 CurseForge（使用用户环境变量 CURSEFORGE_TOKEN）。
.PARAMETER JarPath
    要上传的 jar 路径，默认 release/sequenced_pattern_provider-1.21.1-0.3.2.jar
.PARAMETER Proxy
    代理地址，默认 http://127.0.0.1:7890；传 "direct" 表示直连
#>
param(
    [string]$JarPath = "$PSScriptRoot\..\release\sequenced_pattern_provider-1.21.1-0.3.2.jar",
    [string]$Proxy = 'http://127.0.0.1:7890',
    [int]$ProjectId = 1607551
)

$ErrorActionPreference = 'Stop'
$token = [Environment]::GetEnvironmentVariable('CURSEFORGE_TOKEN', 'User')
if (-not $token) { Write-Error "CURSEFORGE_TOKEN 未设置"; exit 1 }

$jarName = Split-Path $JarPath -Leaf

$metadata = @{
    changelog     = "- Ported to NeoForge 21.1.228 / Minecraft 1.21.1`n- Data paths migrated to 1.21 format (loot_table/, recipe/, data component tags)`n- New repainted provider block textures`n- Terminal UI updated for new AE2 screen schema`n- Jade integration added`n`n- 迁移至 NeoForge 21.1.228 / Minecraft 1.21.1`n- 数据路径迁移至 1.21 格式`n- 更新主控/子供应器新贴图`n- 终端界面适配新版 AE2 屏幕格式`n- 新增 Jade 集成"
    changelogType = 'markdown'
    displayName   = 'Sequenced Pattern Provider 0.3.2 (NeoForge 1.21.1)'
    gameVersions  = @('1.21.1', 'NeoForge', 'Client', 'Server')
    releaseType   = 'release'
} | ConvertTo-Json -Compress

$boundary = [Guid]::NewGuid().ToString()
$LF = "`r`n"
$fileBytes = [IO.File]::ReadAllBytes($JarPath)
$body = New-Object IO.MemoryStream
$w = New-Object IO.StreamWriter($body)
$w.Write("--$boundary$LF")
$w.Write("Content-Disposition: form-data; name=`"metadata`"$LF$LF")
$w.Write("$metadata$LF")
$w.Write("--$boundary$LF")
$w.Write("Content-Disposition: form-data; name=`"file`"; filename=`"$jarName`"$LF")
$w.Write("Content-Type: application/java-archive$LF$LF")
$w.Flush()
$body.Write($fileBytes, 0, $fileBytes.Length)
$w.Write("$LF--$boundary--$LF")
$w.Flush()

$uri = "https://api.curseforge.com/v1/mods/$ProjectId/files"
$params = @{
    Uri             = $uri
    Method          = 'Post'
    Headers         = @{ 'x-api-key' = $token }
    ContentType     = "multipart/form-data; boundary=$boundary"
    Body            = $body.ToArray()
    UseBasicParsing = $true
    TimeoutSec      = 180
}
if ($Proxy -ne 'direct') { $params.Proxy = $Proxy }

try {
    $resp = Invoke-WebRequest @params
    $file = $resp.Content | ConvertFrom-Json
    Write-Host "上传成功！fileId=$($file.id) fileName=$($file.fileName)" -ForegroundColor Green
    Write-Host "URL: https://www.curseforge.com/minecraft/mc-mods/sequenced-pattern-provider/files/$($file.id)"
    exit 0
} catch {
    $r = $_.Exception.Response
    $body2 = ''
    if ($r) { try { $body2 = (New-Object IO.StreamReader($r.GetResponseStream())).ReadToEnd() } catch {} }
    Write-Host "上传失败：$([int]$r.StatusCode) $body2" -ForegroundColor Red
    exit 2
}
