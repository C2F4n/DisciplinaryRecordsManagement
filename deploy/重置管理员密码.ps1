# 重置管理员密码.ps1
#
# 用途：数据库里已经有管理员账号、但密码不知道（或忘了）时，
#       用程序自带的 BCrypt 编码器生成新的密码哈希，并给出可直接执行的 SQL。
#
# 前提：本脚本与程序 JAR 放在同一目录，且已安装 JDK 21（需要 javac）。
# 用法：
#   .\重置管理员密码.ps1                                  # 交互式输入登录名和新密码
#   .\重置管理员密码.ps1 -Username admin -Password Adm1n_2026Xyz
#
# 为什么要在本机算哈希：网页端登录不进去就没法改密码，而数据库里只存 bcrypt 哈希，
# 手工写一个普通字符串是登不进去的，必须用与程序相同的 BCrypt 算法生成。

[CmdletBinding()]
param(
    [string]$Username,
    [string]$Password
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $here

$Rule = '密码须为 6–18 位，至少含一个英文字母和一个数字，只允许英文字母、数字、_ 和 @'

function Fail([string]$message) {
    Write-Host ""
    Write-Host "[失败] $message" -ForegroundColor Red
    exit 1
}

function Resolve-JdkTool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source -like '*.exe') { return $cmd.Source }
    $patterns = @()
    if ($env:JAVA_HOME) { $patterns += (Join-Path $env:JAVA_HOME "bin\$name.exe") }
    $patterns += "C:\Program Files\Java\jdk*\bin\$name.exe"
    $patterns += "C:\Program Files\Eclipse Adoptium\jdk*\bin\$name.exe"
    $found = @()
    foreach ($pattern in $patterns) {
        foreach ($hit in (Resolve-Path $pattern -ErrorAction SilentlyContinue)) {
            if (Test-Path $hit.Path) { $found += $hit.Path }
        }
    }
    if ($found) {
        $preferred = $found | Where-Object { $_ -match 'jdk-21' } | Select-Object -First 1
        if ($preferred) { return $preferred }
        return ($found | Sort-Object -Descending | Select-Object -First 1)
    }
    return $null
}

# --- 0. 找 JAR 与 JDK --------------------------------------------------------
$jar = Get-ChildItem $here -Filter 'DisciplinaryRecordsManagement-*.jar' | Select-Object -First 1
if (-not $jar) { Fail "本目录找不到 DisciplinaryRecordsManagement-*.jar，请把本脚本放到部署目录" }

$javac = Resolve-JdkTool 'javac'
$java = Resolve-JdkTool 'java'
if (-not $javac -or -not $java) {
    Fail "找不到 javac。重置密码需要 JDK（不只是 JRE），请安装 JDK 21 后重试，或把 JDK\bin 加入 PATH。"
}

# --- 1. 交互输入 -------------------------------------------------------------
if (-not $Username) {
    $Username = Read-Host '管理员登录名（小写字母开头，4-48 位）'
}
if (-not $Password) {
    $secure = Read-Host ('新密码（' + $Rule + '）') -AsSecureString
    $Password = [System.Net.NetworkCredential]::new('', $secure).Password
}

$Username = $Username.Trim()
if ($Username -notmatch '^[a-z][a-z0-9_.-]{3,47}$') {
    Fail "登录名不合法：须以小写字母开头，允许 a-z 0-9 _ . -，长度 4-48。"
}
if ($Password -notmatch '^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_@]{6,18}$') {
    Fail "密码不合规：$Rule"
}

# --- 2. 从 JAR 中取出 BCrypt 依赖 --------------------------------------------
$work = Join-Path $env:TEMP ("drm-reset-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $work -Force | Out-Null
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach ($pattern in 'BOOT-INF/lib/spring-security-crypto-*.jar', 'BOOT-INF/lib/commons-logging-*.jar') {
            $entry = $zip.Entries | Where-Object { $_.FullName -like $pattern } | Select-Object -First 1
            if (-not $entry) { Fail "JAR 内缺少依赖 $pattern，无法生成哈希" }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $work $entry.Name), $true)
        }
    } finally {
        $zip.Dispose()
    }

    $source = @'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGen {
    public static void main(String[] args) {
        var encoder = new BCryptPasswordEncoder();
        String raw = args[0];
        String hash = encoder.encode(raw);
        if (!encoder.matches(raw, hash)) {
            System.out.println("ERROR");
            System.exit(2);
        }
        System.out.println(hash);
    }
}
'@
    $srcPath = Join-Path $work 'HashGen.java'
    [System.IO.File]::WriteAllText($srcPath, $source, (New-Object System.Text.UTF8Encoding($false)))

    $classpath = ((Get-ChildItem $work -Filter *.jar | ForEach-Object { $_.FullName }) -join ';')

    $compileLog = Join-Path $work 'javac.log'
    $compileErr = Join-Path $work 'javac.err.log'
    # 注意：本机路径可能含空格（如 C:\Users\R5 5600\...），Start-Process 不会自动加引号
    $quotedCp = '"' + $classpath + '"'
    $proc = Start-Process -FilePath $javac `
        -ArgumentList @('-encoding', 'UTF-8', '-cp', $quotedCp, '-d', ('"' + $work + '"'), ('"' + $srcPath + '"')) `
        -NoNewWindow -Wait -PassThru -RedirectStandardOutput $compileLog -RedirectStandardError $compileErr
    if ($proc.ExitCode -ne 0) {
        Fail ("javac 编译失败：" + (Get-Content $compileErr -Raw -ErrorAction SilentlyContinue) + (Get-Content $compileLog -Raw -ErrorAction SilentlyContinue))
    }

    $outFile = Join-Path $work 'hash.txt'
    $errFile = Join-Path $work 'hash.err.log'
    $proc = Start-Process -FilePath $java `
        -ArgumentList @('-cp', ('"' + "$classpath;$work" + '"'), 'HashGen', ('"' + $Password + '"')) `
        -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
    if ($proc.ExitCode -ne 0) {
        Fail ("生成哈希失败：" + (Get-Content $errFile -Raw -ErrorAction SilentlyContinue))
    }

    $hash = (Get-Content $outFile -Raw).Trim()
    if ($hash -notmatch '^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$') {
        Fail "生成的哈希格式不正确：$hash"
    }
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
}

# --- 3. 输出结果 -------------------------------------------------------------
Write-Host ""
Write-Host "登录名：$Username" -ForegroundColor Green
Write-Host "密码哈希（60 字符）：" -ForegroundColor Green
Write-Host "  $hash"
Write-Host ""
Write-Host "接着在 MySQL 里执行下面这条 SQL（把 disciplinary_records 换成你的库名）：" -ForegroundColor Yellow
Write-Host ""
Write-Host "USE disciplinary_records;"
Write-Host "UPDATE app_user"
Write-Host "   SET password_hash = '$hash',"
Write-Host "       temporary_password_until = NULL"
Write-Host " WHERE username = '$Username';"
Write-Host ""
Write-Host "执行后不需要重启程序，直接用新密码登录即可。"
Write-Host "如果显示 0 rows affected，说明登录名不对，先查一下现有管理员："
Write-Host "  SELECT id, username, core_admin_slot FROM app_user WHERE core_admin_slot = 1;"
