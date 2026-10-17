#requires -RunAsAdministrator
$ErrorActionPreference = "Stop"

$mysqlRoot = "C:\Program Files\MySQL\MySQL Server 8.4"
$serverExecutable = Join-Path $mysqlRoot "bin\mysqld.exe"
$serviceName = "MySQL84"
$dataRoot = "C:\ProgramData\MySQL\MallLostFound"
$dataDirectory = Join-Path $dataRoot "Data"
$configFile = Join-Path $dataRoot "my.ini"
$initFile = Join-Path $dataRoot "initialize-root.sql"

if (-not (Test-Path $serverExecutable)) {
    throw "MySQL Server is not installed at the expected location: $serverExecutable"
}

$existingService = Get-CimInstance Win32_Service -Filter "Name='$serviceName'" -ErrorAction SilentlyContinue
if ($null -ne $existingService) {
    if ($existingService.PathName -notlike "*$serverExecutable*" -or
        $existingService.PathName -notlike "*--defaults-file=$configFile*") {
        throw "The $serviceName service does not match this project's MySQL configuration. No changes were made."
    }
    if (-not (Test-Path $configFile)) {
        throw "The MySQL service configuration is missing: $configFile"
    }

    $configLines = [IO.File]::ReadAllLines($configFile)
    $cleanConfigLines = @($configLines | Where-Object { $_ -notmatch '^\s*init_file\s*=' })
    [IO.File]::WriteAllLines($configFile, $cleanConfigLines, [Text.UTF8Encoding]::new($false))
    Remove-Item $initFile -Force -ErrorAction SilentlyContinue

    if ((Get-Service -Name $serviceName).Status -ne "Running") {
        Start-Service -Name $serviceName
        (Get-Service -Name $serviceName).WaitForStatus(
            [System.ServiceProcess.ServiceControllerStatus]::Running,
            [TimeSpan]::FromSeconds(60))
    }
    if (-not (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet `
            -WarningAction SilentlyContinue)) {
        throw "The MySQL service is running but port 3306 is not accepting connections. Check the MySQL error log."
    }
    Write-Host "MySQL Server is running on 127.0.0.1:3306."
    Write-Host "Use MySQL username root and the password you entered during the previous setup attempt."
    Read-Host "Setup completed. Press Enter to close this window"
    exit 0
}

$dataInitialized = $false
if (Test-Path $dataDirectory) {
    $existingFiles = Get-ChildItem $dataDirectory -Force
    if ($existingFiles.Count -gt 0 -and
        (Test-Path (Join-Path $dataDirectory "auto.cnf")) -and
        (Test-Path (Join-Path $dataDirectory "mysql"))) {
        $dataInitialized = $true
    } elseif ($existingFiles.Count -gt 0) {
        throw "The MySQL data directory is not empty. Refusing to overwrite existing data."
    }
}

$securePassword = Read-Host "Set a strong MySQL root password (keep it private)" -AsSecureString
if ($securePassword.Length -lt 12) {
    throw "Use a root password with at least 12 characters."
}

$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$password = $null
$serviceInstalled = $false
$setupError = $null
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $escapedPassword = $password.Replace("\", "\\").Replace("'", "''")

    New-Item -ItemType Directory -Force -Path $dataDirectory | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    & icacls.exe $dataRoot /grant:r "*S-1-5-18:(OI)(CI)F" "*S-1-5-32-544:(OI)(CI)F" "*$($identity.User.Value):(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not set secure permissions on $dataRoot."
    }

    if (-not $dataInitialized) {
        & $serverExecutable --initialize-insecure "--basedir=$($mysqlRoot.Replace('\', '/'))" `
            "--datadir=$($dataDirectory.Replace('\', '/'))"
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL data directory initialization failed."
        }
    }

    $configuration = @"
[mysqld]
basedir="$($mysqlRoot.Replace('\', '/'))"
datadir="$($dataDirectory.Replace('\', '/'))"
port=3306
bind-address=127.0.0.1
mysqlx=0
init_file="$($initFile.Replace('\', '/'))"
"@
    [IO.File]::WriteAllText($configFile, $configuration, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($initFile,
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escapedPassword';`r`n",
        [Text.UTF8Encoding]::new($false))

    & $serverExecutable --install $serviceName "--defaults-file=$configFile"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the $serviceName Windows service."
    }
    $serviceInstalled = $true

    Set-Service -Name $serviceName -StartupType Automatic
    Start-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus(
        [System.ServiceProcess.ServiceControllerStatus]::Running,
        [TimeSpan]::FromSeconds(60))

    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $ready = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet `
            -WarningAction SilentlyContinue
        if (-not $ready) {
            Start-Sleep -Seconds 1
        }
    } while (-not $ready -and [DateTime]::UtcNow -lt $deadline)
    if (-not $ready) {
        throw "MySQL service started but port 3306 did not become available."
    }

    $configuration = $configuration -replace '(?m)^init_file=.*(?:\r?\n|$)', ''
    [IO.File]::WriteAllText($configFile, $configuration, [Text.UTF8Encoding]::new($false))
    Remove-Item $initFile -Force
    Restart-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus(
        [System.ServiceProcess.ServiceControllerStatus]::Running,
        [TimeSpan]::FromSeconds(60))

    Write-Host "MySQL Server is configured and running on 127.0.0.1:3306."
    Write-Host "The root password was stored by MySQL and was not written to the project."
    Write-Host "Launch App.java and sign in to the database as root using the password you just set."
} catch {
    if ($serviceInstalled) {
        Stop-Service -Name $serviceName -ErrorAction SilentlyContinue
    }
    Remove-Item $initFile -Force -ErrorAction SilentlyContinue
    $setupError = $_.Exception.Message
    Write-Host "MySQL setup failed: $setupError" -ForegroundColor Red
} finally {
    if ($null -ne $passwordPointer) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $password = $null
    $escapedPassword = $null
}

if ($null -ne $setupError) {
    Read-Host "Press Enter to close this window"
    exit 1
}
Read-Host "Setup completed. Press Enter to close this window"
