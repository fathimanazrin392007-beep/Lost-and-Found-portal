$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$connector = Get-ChildItem (Join-Path $projectRoot "lib") -Filter "mysql-connector-j-*.jar" |
    Select-Object -First 1
if ($null -eq $connector) {
    Write-Error "MySQL Connector/J is missing. Place mysql-connector-j-*.jar in the lib folder."
    exit 1
}

$databaseUrl = Read-Host "MySQL JDBC URL (default: jdbc:mysql://localhost:3306/mall_lost_found?serverTimezone=UTC)"
if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
    $databaseUrl = "jdbc:mysql://localhost:3306/mall_lost_found?serverTimezone=UTC"
}

$databaseCredential = Get-Credential -Message "Enter a MySQL account with permission to create the mall_lost_found database"
if ($null -eq $databaseCredential) {
    Write-Error "MySQL credentials are required."
    exit 1
}

$adminName = Read-Host "Initial security username (used only if no staff account exists; default: security)"
if ([string]::IsNullOrWhiteSpace($adminName)) {
    $adminName = "security"
}
$adminPasswordSecure = Read-Host "Initial security password (used only if no staff account exists)" -AsSecureString
if ($adminPasswordSecure.Length -eq 0) {
    Write-Error "A non-empty initial security password is required."
    exit 1
}

$databasePasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($databaseCredential.Password)
$adminPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminPasswordSecure)
try {
    $env:MALL_DB_URL = $databaseUrl.Trim()
    $env:MALL_DB_USER = $databaseCredential.UserName
    $env:MALL_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($databasePasswordPointer)
    $env:MALL_ADMIN_USERNAME = $adminName.Trim()
    $env:MALL_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($adminPasswordPointer)

    New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot "bin") | Out-Null
    & javac --release 17 -cp "lib\*" -d bin src\*.java
    if ($LASTEXITCODE -ne 0) {
        throw "Java compilation failed with exit code $LASTEXITCODE."
    }

    & java -cp "bin;lib\*" App
    if ($LASTEXITCODE -ne 0) {
        throw "The application exited with code $LASTEXITCODE."
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($databasePasswordPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($adminPasswordPointer)
    Remove-Item Env:MALL_DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:MALL_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MALL_DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:MALL_ADMIN_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:MALL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
