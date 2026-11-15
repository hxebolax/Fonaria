# Compila el motor nativo de Fonaria (carpeta "nativo", Rust) para Android y deja las
# bibliotecas en app\src\main\jniLibs\<abi>\, donde Gradle las mete en el APK.
#
# Bibliotecas que salen:
#   arm64-v8a\libfonaria.so       ARMv8.0 generico: cualquier movil de 64 bits.
#   arm64-v8a\libfonaria_v82.so   ARMv8.2 con dotprod y fp16 (casi todos desde 2019): mas rapido.
#                                 La app elige al arrancar cual cargar segun el procesador.
#   armeabi-v7a\libfonaria.so     Moviles antiguos de 32 bits (lento, pero funciona).
#   x86_64\libfonaria.so          Emulador de Android en un PC.
#
# Necesita Rust (rustup) y el NDK de Android en entorno\android-sdk\ndk\<version>.
# Uso:
#   powershell -NoProfile -ExecutionPolicy Bypass -File herramientas\compilar_nativo.ps1
#   ... -Abis arm64-v8a           solo esa arquitectura
#   ... -SinOptimizada            sin la variante ARMv8.2
# Sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.

param(
	[string[]]$Abis = @("arm64-v8a", "armeabi-v7a", "x86_64"),
	[switch]$SinOptimizada,
	[int]$ApiMinima = 21
)

$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
$Nativo = Join-Path $Raiz "nativo"
$Salida = Join-Path $Raiz "app\src\main\jniLibs"

# --- Rust: el de entorno\rust si existe; si no, el del sistema ---
$RustPortatil = Join-Path $Raiz "entorno\rust"
if (Test-Path (Join-Path $RustPortatil "cargo\bin\cargo.exe")) {
	$env:CARGO_HOME = Join-Path $RustPortatil "cargo"
	$env:RUSTUP_HOME = Join-Path $RustPortatil "rustup"
	$env:PATH = (Join-Path $env:CARGO_HOME "bin") + ";" + $env:PATH
}
$cargo = Get-Command cargo -ErrorAction SilentlyContinue
if (-not $cargo) {
	Write-Host "[ERROR] No se encuentra Rust. Ejecuta herramientas\preparar_entorno.ps1 -ConRust o instala rustup."
	exit 1
}

# --- NDK ---
$CarpetaNdk = Get-ChildItem (Join-Path $Raiz "entorno\android-sdk\ndk") -Directory -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if (-not $CarpetaNdk) {
	Write-Host "[ERROR] Falta el NDK en entorno\android-sdk\ndk. Ejecuta herramientas\preparar_entorno.ps1 -ConNdk."
	exit 1
}
$Bin = Join-Path $CarpetaNdk.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin"
Write-Host "[Nativo] NDK $($CarpetaNdk.Name), $(& cargo --version)"

# Objetivo de Rust, prefijo de clang del NDK y opciones de cada arquitectura
$Objetivos = @{
	"arm64-v8a"   = @{ Rust = "aarch64-linux-android"; Clang = "aarch64-linux-android"; Cpu = "-C target-cpu=generic" }
	"armeabi-v7a" = @{ Rust = "armv7-linux-androideabi"; Clang = "armv7a-linux-androideabi"; Cpu = "-C target-cpu=generic -C target-feature=+neon" }
	"x86_64"      = @{ Rust = "x86_64-linux-android"; Clang = "x86_64-linux-android"; Cpu = "-C target-cpu=x86-64" }
}
# Android 15 exige paginas de 16 KB en Google Play; los NDK anteriores al r28 no alinean asi.
$Comun = "-C link-arg=-Wl,-z,max-page-size=16384"

function Compilar([string]$Abi, [string]$Extra, [string]$NombreFinal, [string]$Carpeta) {
	$o = $Objetivos[$Abi]
	$t = $o.Rust
	$tm = $t.ToUpper().Replace("-", "_")
	$tl = $t.Replace("-", "_")
	& rustup target add $t | Out-Null
	$clang = Join-Path $Bin ("{0}{1}-clang.cmd" -f $o.Clang, $ApiMinima)
	$clangxx = Join-Path $Bin ("{0}{1}-clang++.cmd" -f $o.Clang, $ApiMinima)
	Set-Item -Path "env:CARGO_TARGET_${tm}_LINKER" -Value $clang
	Set-Item -Path "env:CARGO_TARGET_${tm}_RUSTFLAGS" -Value "$Comun $($o.Cpu) $Extra"
	Set-Item -Path "env:CC_$tl" -Value $clang
	Set-Item -Path "env:CXX_$tl" -Value $clangxx
	Set-Item -Path "env:AR_$tl" -Value (Join-Path $Bin "llvm-ar.exe")
	Remove-Item env:RUSTFLAGS -ErrorAction SilentlyContinue
	Remove-Item env:CARGO_ENCODED_RUSTFLAGS -ErrorAction SilentlyContinue
	$dirObjetivo = Join-Path $Nativo "target\$Carpeta"
	Write-Host "[Nativo] Compilando $Abi ($NombreFinal)..."
	Push-Location $Nativo
	try {
		& cargo build --release --lib --target $t --target-dir $dirObjetivo
		if ($LASTEXITCODE -ne 0) { throw "cargo fallo para $Abi" }
	} finally {
		Pop-Location
	}
	$so = Join-Path $dirObjetivo "$t\release\libfonaria.so"
	$destino = Join-Path $Salida $Abi
	New-Item -ItemType Directory -Force $destino | Out-Null
	Copy-Item $so (Join-Path $destino $NombreFinal) -Force
	$mb = [math]::Round((Get-Item (Join-Path $destino $NombreFinal)).Length / 1MB, 1)
	Write-Host "[OK] $Abi\$NombreFinal ($mb MB)"
}

foreach ($abi in $Abis) {
	if (-not $Objetivos.ContainsKey($abi)) { Write-Host "[AVISO] Arquitectura desconocida: $abi"; continue }
	Compilar $abi "" "libfonaria.so" $abi
	if ($abi -eq "arm64-v8a" -and -not $SinOptimizada) {
		Compilar $abi "-C target-feature=+dotprod,+fp16" "libfonaria_v82.so" "arm64-v8a-v82"
	}
}
Write-Host "[OK] Bibliotecas nativas listas en $Salida"
exit 0
