# -*- coding: utf-8 -*-
# Fonaria - Publica una version: servidor propio y GitHub.
#
# 1. Copia Fonaria.apk a dist\Fonaria_v<version>.apk y crea dist\version.json con la version,
#    el tamano, el SHA-256 y las novedades (seccion "== <version>" de NOVEDADES.txt).
# 2. Los copia a la carpeta sincronizada con el servidor (Synology Drive), primero el APK y
#    despues version.json, para que nadie vea una version cuyo APK aun no esta subido.
# 3. Con -ConRecursos, copia tambien al servidor las voces de otros idiomas (voces\) y los
#    modelos opcionales (modelos\) y el modelo integrado (modelo\), que la app descarga.
# 4. Si esta la herramienta gh de GitHub con la sesion iniciada, crea (o actualiza) la release
#    v<version> en GitHub con el APK y version.json.
#
# Uso: publicar_actualizacion.ps1 [-ConRecursos] [-SinGitHub] [-SinServidor] [-Repo usuario/repositorio]
# Sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.

param(
	[string]$Apk = "",
	[string]$Dist = "",
	# Carpeta sincronizada con https://xebolax.synology.me/Fonaria
	[string]$Servidor = "$env:USERPROFILE\SynologyDrive\web\Fonaria",
	[string]$Repo = "hxebolax/Fonaria",
	[switch]$ConRecursos,
	[switch]$SinGitHub,
	[switch]$SinServidor
)

$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
if (-not $Apk) { $Apk = Join-Path $Raiz "Fonaria.apk" }
if (-not $Dist) { $Dist = Join-Path $Raiz "dist" }
$Aapt2 = Join-Path $Raiz "entorno\android-sdk\build-tools\35.0.0\aapt2.exe"
$Utf8 = New-Object System.Text.UTF8Encoding($false)

function Error($texto) { Write-Host "[ERROR] $texto" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $Apk)) { Error "No existe $Apk. Compila primero." }

$gradle = [IO.File]::ReadAllText((Join-Path $Raiz "app\build.gradle.kts"), $Utf8)
$nombre = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$codigo = [regex]::Match($gradle, 'versionCode\s*=\s*(\d+)').Groups[1].Value
if (-not $nombre -or -not $codigo) { Error "No se encuentran versionName y versionCode en app\build.gradle.kts" }

# El APK tiene que ser de esa version (evita publicar uno antiguo)
if (Test-Path $Aapt2) {
	$datos = & $Aapt2 dump badging $Apk 2>$null | Select-String "versionCode='(\d+)' versionName='([^']*)'"
	if ($datos) {
		$c = $datos.Matches[0].Groups[1].Value
		$v = $datos.Matches[0].Groups[2].Value
		if ($c -ne $codigo -or $v -ne $nombre) { Error "El APK es la version $v ($c), no la $nombre ($codigo). Compila primero." }
	}
}

# Novedades de NOVEDADES.txt (seccion "== 1.0.0")
$notas = ""
$fichero = Join-Path $Raiz "NOVEDADES.txt"
if (Test-Path $fichero) {
	$texto = [IO.File]::ReadAllText($fichero, $Utf8)
	$partes = [regex]::Split($texto, '(?m)^==\s*(\S+)\s*$')
	for ($i = 1; $i -lt $partes.Length - 1; $i += 2) {
		if ($partes[$i] -eq $nombre) { $notas = $partes[$i + 1].Trim() }
	}
	if (-not $notas) { Write-Host "[AVISO] NOVEDADES.txt no tiene la seccion '== $nombre': se publica sin novedades." }
}

New-Item -ItemType Directory -Force $Dist | Out-Null
$archivo = "Fonaria_v$nombre.apk"
$destino = Join-Path $Dist $archivo
Copy-Item -Force $Apk $destino
$sha = (Get-FileHash -Algorithm SHA256 $destino).Hash.ToLower()
$tamano = (Get-Item $destino).Length
$manifiesto = [ordered]@{
	version = $nombre
	versionCode = [int]$codigo
	file = $archivo
	sha256 = $sha
	size = $tamano
	date = (Get-Date -Format "yyyy-MM-dd")
	notas = $notas
}
[IO.File]::WriteAllText((Join-Path $Dist "version.json"), ($manifiesto | ConvertTo-Json -Depth 3) + "`n", $Utf8)
Write-Host "[OK] Version $nombre lista en $Dist ($archivo, $tamano bytes, version.json)" -ForegroundColor Green

# --- Servidor -------------------------------------------------------------------------
if (-not $SinServidor) {
	if (-not (Test-Path (Split-Path -Parent $Servidor))) {
		Write-Host "[AVISO] No existe $(Split-Path -Parent $Servidor): sube a mano dist\$archivo y dist\version.json."
	} else {
		New-Item -ItemType Directory -Force $Servidor | Out-Null
		if ($ConRecursos) {
			# Voces de otros idiomas (las que no van en el APK) y modelos
			$catalogo = [IO.File]::ReadAllText((Join-Path $Raiz "app\src\main\assets\catalogo_voces.json"), $Utf8) | ConvertFrom-Json
			$vocesSrv = Join-Path $Servidor "voces"
			New-Item -ItemType Directory -Force $vocesSrv | Out-Null
			$n = 0
			foreach ($v in $catalogo.voces) {
				$origen = Join-Path $Raiz "modelo\voces\$($v.archivo)"
				if (-not (Test-Path $origen)) { Write-Host "[AVISO] Falta $origen (preparar_modelo.ps1 -TodasLasVoces)"; continue }
				$fin = Join-Path $vocesSrv $v.archivo
				if (-not (Test-Path $fin) -or (Get-Item $fin).Length -ne (Get-Item $origen).Length) { Copy-Item -Force $origen $fin; $n++ }
			}
			Copy-Item -Force (Join-Path $Raiz "app\src\main\assets\catalogo_voces.json") (Join-Path $vocesSrv "catalogo_voces.json")
			Write-Host "[OK] Voces en el servidor: $n nuevas o cambiadas." -ForegroundColor Green
			$modelosSrv = Join-Path $Servidor "modelos"
			$modeloSrv = Join-Path $Servidor "modelo"
			New-Item -ItemType Directory -Force $modelosSrv, $modeloSrv | Out-Null
			foreach ($m in @("model.q4k.gguf", "model.safetensors")) {
				$origen = Join-Path $Raiz "modelo\$m"
				if (Test-Path $origen) { Copy-Item -Force $origen (Join-Path $modelosSrv $m) } else { Write-Host "[AVISO] Falta modelo\$m" }
			}
			foreach ($m in @("config.json", "tokenizer.json", "model.q8.gguf")) {
				Copy-Item -Force (Join-Path $Raiz "modelo\apk\modelo\$m") (Join-Path $modeloSrv $m)
			}
			Copy-Item -Force (Join-Path $Raiz "docs\LICENCIA-MODELO.txt") (Join-Path $modeloSrv "LICENCIA.txt")
			Copy-Item -Force (Join-Path $Raiz "docs\LICENCIA-MODELO.txt") (Join-Path $modelosSrv "LICENCIA.txt")
			Write-Host "[OK] Modelos copiados al servidor." -ForegroundColor Green
		}
		Copy-Item -Force $destino (Join-Path $Servidor $archivo)
		Copy-Item -Force (Join-Path $Dist "version.json") (Join-Path $Servidor "version.json")
		Write-Host "[OK] APK y version.json copiados a $Servidor (Synology Drive los sube)." -ForegroundColor Green
	}
}

# --- GitHub ---------------------------------------------------------------------------
if (-not $SinGitHub) {
	$ErrorActionPreference = "Continue"
	$gh = Get-Command gh -ErrorAction SilentlyContinue
	if (-not $gh) {
		Write-Host "[AVISO] Sin la herramienta gh no se publica en GitHub (https://cli.github.com)."
	} else {
		& gh auth status *> $null
		if ($LASTEXITCODE -ne 0) {
			Write-Host "[AVISO] gh no tiene la sesion iniciada (gh auth login): no se publica en GitHub."
		} else {
			$etiqueta = "v$nombre"
			# La release se crea sobre el commit actual: tiene que estar subido a GitHub
			Push-Location $Raiz
			$commit = (& git rev-parse HEAD 2>$null)
			& git fetch -q origin 2>$null
			$subido = (& git branch -r --contains $commit 2>$null)
			Pop-Location
			if (-not $commit -or -not $subido) {
				Write-Host "[AVISO] El commit actual no esta subido a GitHub: haz commit y git push, y vuelve a publicar. No se crea la release." -ForegroundColor Yellow
				exit 0
			}
			$fichNotas = Join-Path $Dist "notas.md"
			[IO.File]::WriteAllText($fichNotas, $notas + "`n", $Utf8)
			& gh release view $etiqueta --repo $Repo *> $null
			if ($LASTEXITCODE -eq 0) {
				& gh release upload $etiqueta $destino (Join-Path $Dist "version.json") --repo $Repo --clobber
				& gh release edit $etiqueta --repo $Repo --notes-file $fichNotas
			} else {
				& gh release create $etiqueta $destino (Join-Path $Dist "version.json") --repo $Repo --title "Fonaria $nombre" --notes-file $fichNotas --target $commit --latest
			}
			if ($LASTEXITCODE -eq 0) { Write-Host "[OK] Publicada en GitHub: https://github.com/$Repo/releases/tag/$etiqueta" -ForegroundColor Green }
			else { Write-Host "[ERROR] No se pudo publicar en GitHub." -ForegroundColor Red }
		}
	}
}
exit 0
