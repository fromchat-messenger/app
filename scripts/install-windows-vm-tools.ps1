$ErrorActionPreference = "Stop"

function Ensure-Jdk17 {
    $adoptium = "C:\Program Files\Eclipse Adoptium"
    $jdkHome = Get-ChildItem $adoptium -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-17*" } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($jdkHome) { return $jdkHome.FullName }
    Write-Host "Downloading Temurin JDK 17..."
    $msi = Join-Path $env:TEMP "temurin-jdk17.msi"
    Invoke-WebRequest `
        -Uri "https://api.adoptium.net/v3/installer/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
        -OutFile $msi `
        -UseBasicParsing
    Write-Host "Installing Temurin JDK 17..."
    $args = @("/i", $msi, "/qn", "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome")
    $p = Start-Process msiexec.exe -ArgumentList $args -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "JDK 17 installer exit code $($p.ExitCode)" }
    $jdkHome = Get-ChildItem $adoptium -Directory | Where-Object { $_.Name -like "jdk-17*" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdkHome) { throw "JDK 17 install failed" }
    return $jdkHome.FullName
}

function Ensure-Jdk21 {
    $adoptium = "C:\Program Files\Eclipse Adoptium"
    $jdkHome = Get-ChildItem $adoptium -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "jdk-21*" } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($jdkHome) { return $jdkHome.FullName }
    Write-Host "Downloading Temurin JDK 21..."
    $msi = Join-Path $env:TEMP "temurin-jdk21.msi"
    Invoke-WebRequest `
        -Uri "https://api.adoptium.net/v3/installer/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
        -OutFile $msi `
        -UseBasicParsing
    Write-Host "Installing Temurin JDK 21..."
    $args = @("/i", $msi, "/qn", "ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome")
    $p = Start-Process msiexec.exe -ArgumentList $args -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "JDK 21 installer exit code $($p.ExitCode)" }
    $jdkHome = Get-ChildItem $adoptium -Directory | Where-Object { $_.Name -like "jdk-21*" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdkHome) { throw "JDK 21 install failed" }
    return $jdkHome.FullName
}

function Ensure-Rust {
    $cargo = Join-Path $env:USERPROFILE ".cargo\bin\cargo.exe"
    if (Test-Path $cargo) { return $cargo }
    Write-Host "Downloading rustup..."
    $rustup = Join-Path $env:TEMP "rustup-init.exe"
    Invoke-WebRequest -Uri "https://win.rustup.rs/x86_64" -OutFile $rustup -UseBasicParsing
    Write-Host "Installing Rust..."
    $p = Start-Process $rustup -ArgumentList @("-y", "--default-toolchain", "stable", "--profile", "minimal") -Wait -PassThru
    if ($p.ExitCode -ne 0) { throw "rustup exit code $($p.ExitCode)" }
    if (-not (Test-Path $cargo)) { throw "cargo not found after rustup" }
    return $cargo
}

function Ensure-VsBuildTools {
    $link = Get-Command link.exe -ErrorAction SilentlyContinue
    if ($link) { return $link.Source }
    $vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path $vswhere) {
        $installPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
        if ($installPath) {
            $candidate = Join-Path $installPath "VC\Tools\MSVC"
            if (Test-Path $candidate) { return $installPath }
        }
    }
    Write-Host "Downloading VS Build Tools..."
    $bootstrapper = Join-Path $env:TEMP "vs_BuildTools.exe"
    Invoke-WebRequest -Uri "https://aka.ms/vs/17/release/vs_BuildTools.exe" -OutFile $bootstrapper -UseBasicParsing
    Write-Host "Installing VS Build Tools (this may take several minutes)..."
    $args = @(
        "--quiet", "--wait", "--norestart",
        "--add", "Microsoft.VisualStudio.Workload.VCTools",
        "--includeRecommended"
    )
    $p = Start-Process $bootstrapper -ArgumentList $args -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) {
        throw "VS Build Tools exit code $($p.ExitCode)"
    }
    $link = Get-Command link.exe -ErrorAction SilentlyContinue
    if (-not $link) {
        throw "link.exe still not found after VS Build Tools install"
    }
    return $link.Source
}

$javaHome17 = Ensure-Jdk17
Write-Host "JAVA_HOME_17=$javaHome17"
$javaHome21 = Ensure-Jdk21
Write-Host "JAVA_HOME_21=$javaHome21"
$cargo = Ensure-Rust
Write-Host "CARGO=$cargo"
$link = Ensure-VsBuildTools
Write-Host "LINK=$link"
Write-Host "OK"
