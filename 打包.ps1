# 打包.ps1 —— 生成干净的交付目录（默认 dist\违纪统计系统_<时间戳>）
#
# 用法：
#   .\打包.ps1                    # 构建前端（若缺 dist）+ mvn package（跳过测试）+ 组装
#   .\打包.ps1 -WithTests         # 同上，但先跑完整测试
#   .\打包.ps1 -ForceFrontendBuild # 强制重新构建前端
#
# 交付目录只包含运行必需的文件，绝不会把开发机的 .env、日志、
# .smtp-settings.key、node_modules 带出去；证书每次重新生成，
# 因此每份交付的 keystore 口令都不同，且自动写进该目录的 .env。

[CmdletBinding()]
param(
    [string]$OutRoot = 'dist',
    [switch]$WithTests,
    [switch]$ForceFrontendBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repo

function Fail([string]$message) {
    Write-Host ""
    Write-Host "[打包失败] $message" -ForegroundColor Red
    exit 1
}

function Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

# --- 0. 前置检查 -------------------------------------------------------------
function Resolve-Tool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $patterns = @()
    if ($env:JAVA_HOME) { $patterns += (Join-Path $env:JAVA_HOME "bin\$name.exe") }
    $patterns += "C:\Program Files\Java\jdk*\bin\$name.exe"
    $patterns += "C:\Program Files\Eclipse Adoptium\jdk*\bin\$name.exe"
    $patterns += "C:\Program Files\Microsoft\jdk*\bin\$name.exe"

    $found = @()
    foreach ($pattern in $patterns) {
        foreach ($hit in (Resolve-Path $pattern -ErrorAction SilentlyContinue)) {
            if (Test-Path $hit.Path) { $found += $hit.Path }
        }
    }
    if ($found) {
        # 优先 jdk-21，其次版本号最大的
        $preferred = $found | Where-Object { $_ -match 'jdk-21' } | Select-Object -First 1
        if ($preferred) { return $preferred }
        return ($found | Sort-Object -Descending | Select-Object -First 1)
    }
    return $null
}

$mvnExe = Resolve-Tool 'mvn'
if (-not $mvnExe) { Fail '找不到 mvn，请先安装 Maven 并加入 PATH' }

$keytoolExe = Resolve-Tool 'keytool'
if (-not $keytoolExe) { Fail '找不到 keytool：请安装 JDK 21（而非仅 JRE），或把 JDK\bin 加入 PATH' }

$javaExe = Resolve-Tool 'java'
if (-not $javaExe) { Fail '找不到 java，请先安装 JDK 21 并加入 PATH' }

# --- 1. 前端构建 -------------------------------------------------------------
$frontendDist = Join-Path $repo 'frontend\dist'
if ($ForceFrontendBuild -or -not (Test-Path (Join-Path $frontendDist 'index.html'))) {
    Step '构建前端'
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Fail '找不到 npm：请安装 Node.js，或先手工构建 frontend\dist 再重跑本脚本'
    }
    Push-Location (Join-Path $repo 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) { npm install --no-audit --no-fund }
        if ($LASTEXITCODE -ne 0) { Fail 'npm install 失败' }
        npm run build
        if ($LASTEXITCODE -ne 0) { Fail 'npm run build 失败' }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "前端产物已存在，跳过构建：$frontendDist（要重构建加 -ForceFrontendBuild）" -ForegroundColor DarkGray
}

# --- 2. 后端打包 -------------------------------------------------------------
Step 'mvn package'
$mvnArgs = @('-B', 'package')
if (-not $WithTests) { $mvnArgs += '-DskipTests' }
& $mvnExe @mvnArgs
if ($LASTEXITCODE -ne 0) { Fail 'mvn package 失败' }

$jar = Get-ChildItem (Join-Path $repo 'target\*.jar') |
    Where-Object { $_.Name -notmatch '\.original$' -and $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { Fail '在 target 下找不到可交付的 jar' }

# --- 3. 输出目录 -------------------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$out = Join-Path (Join-Path $repo $OutRoot) "违纪统计系统_$stamp"
if (-not $out.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) {
    Fail "输出路径不在项目目录内，已中止：$out"
}
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Path $out -Force | Out-Null

# --- 4. 生成本机 HTTPS 证书（每份交付独立口令）--------------------------------
Step '生成本机 HTTPS 证书'
$sslDir = Join-Path $out '.https'
New-Item -ItemType Directory -Path $sslDir -Force | Out-Null
$storePass = -join (1..24 | ForEach-Object { '0123456789ABCDEF'[(Get-Random -Maximum 16)] })
$p12 = Join-Path $sslDir 'server.p12'

function Invoke-Native([string]$exe, [string[]]$argList, [string]$label) {
    $outFile = Join-Path $env:TEMP "pack-$label.out"
    $errFile = Join-Path $env:TEMP "pack-$label.err"
    $proc = Start-Process -FilePath $exe -ArgumentList $argList -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    if ($proc.ExitCode -ne 0) {
        Write-Host (Get-Content $errFile -Raw -ErrorAction SilentlyContinue)
        Fail "$label 失败（退出码 $($proc.ExitCode)）"
    }
}

Invoke-Native $keytoolExe @(
    '-genkeypair', '-alias', 'disciplinary-localhost', '-keyalg', 'RSA', '-keysize', '2048',
    '-validity', '3650', '-storetype', 'PKCS12', '-keystore', $p12,
    '-storepass', $storePass, '-keypass', $storePass,
    '-dname', 'CN=localhost,OU=Local,O=DisciplinaryRecords',
    '-ext', 'SAN=dns:localhost,ip:127.0.0.1'
) 'keytool-genkeypair'
if (-not (Test-Path $p12)) { Fail 'keytool 未生成 server.p12' }

Invoke-Native $keytoolExe @(
    '-exportcert', '-alias', 'disciplinary-localhost', '-keystore', $p12,
    '-storepass', $storePass, '-file', (Join-Path $sslDir 'localhost.cer'), '-rfc'
) 'keytool-exportcert'
if (-not (Test-Path (Join-Path $sslDir 'localhost.cer'))) { Fail 'keytool 未导出 localhost.cer' }

$verify = Start-Process -FilePath $keytoolExe -ArgumentList @(
    '-list', '-keystore', $p12, '-storepass', $storePass, '-alias', 'disciplinary-localhost'
) -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'pack-list.out') `
    -RedirectStandardError (Join-Path $env:TEMP 'pack-list.err')
if ($verify.ExitCode -ne 0) { Fail '证书自检失败：口令与 p12 不匹配' }

# --- 5. 由模板生成 .env -------------------------------------------------------
Step '从 .env.example 生成 .env'
$template = Get-Content (Join-Path $repo '.env.example') -Raw -Encoding UTF8
$envText = $template -replace '(?m)^server\.ssl\.key-store-password=.*$', "server.ssl.key-store-password=$storePass"
if ($envText -notmatch [regex]::Escape($storePass)) { Fail '.env 生成失败：未能写入 keystore 口令' }
$envPath = Join-Path $out '.env'
[System.IO.File]::WriteAllText($envPath, $envText, (New-Object System.Text.UTF8Encoding($false)))

# --- 6. 复制运行必需文件 ------------------------------------------------------
Step '复制运行文件'
Copy-Item $jar.FullName $out
Copy-Item (Join-Path $repo 'deploy\*') $out -Recurse
Copy-Item (Join-Path $repo '.env.example') $out

# --- 7. 汇总 ------------------------------------------------------------------
Step '完成'
$files = Get-ChildItem $out -Recurse -File | Sort-Object FullName
$totalMB = [math]::Round((($files | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Host "输出目录：$out" -ForegroundColor Green
Write-Host "文件数：$($files.Count)　　总大小：$totalMB MB"
$files | ForEach-Object { "  {0,10:N0}  {1}" -f $_.Length, $_.FullName.Substring($out.Length + 1) }

Write-Host ""
Write-Host '交付前检查清单：' -ForegroundColor Yellow
Write-Host '  1) 目标机器的 MySQL 已建库建账号，且与本目录 .env 的 DB_URL/DB_USER 一致'
Write-Host '  2) 把 .env 里的 DB_PASSWORD 填成该账号的密码'
Write-Host '  3) 把 .env 里的 APP_ADMIN_PASSWORD 填成 6-18 位、含字母和数字、只用 A-Za-z0-9_@ 的初始密码'
Write-Host '  4) 整个目录一起拷走（含 .https），双击 启动系统.cmd'
Write-Host '  5) 首次登录后立刻改密码；SMTP 配置页初始口令 a018779 也要改'
