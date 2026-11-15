# -*- coding: utf-8 -*-
# Fonaria - Prepara el modelo y las voces que van dentro del APK (carpeta modelo\apk).
#
# El modelo multilingue de Phonon (Gradium, licencia CC BY-NC 4.0) y las voces no se guardan
# en git porque pesan unos 100 MB. Este script los deja en su sitio:
#
#   modelo\apk\modelo\config.json, tokenizer.json, model.q8.gguf   (assets/modelo del APK)
#   modelo\apk\voces\<id>.safetensors                                (las voces "incluida" del catalogo)
#
# De donde salen:
#   - El modelo: del servidor de Fonaria (carpeta modelo/), o de una carpeta local con -Desde
#     (por ejemplo una descarga de huggingface.co/gradium/phonon-40m-multilingual, que pide
#     aceptar su licencia). Se comprueba su SHA-256 con app\src\main\assets\modelos.json.
#   - Las voces: del catalogo publico de Gradium en Hugging Face (Apache 2.0), comprobadas con
#     el SHA-256 de app\src\main\assets\catalogo_voces.json.
#
# Uso:
#   preparar_modelo.ps1                        descarga lo que falte
#   preparar_modelo.ps1 -Desde C:\ruta\modelo  copia config.json, tokenizer.json y model.q8.gguf de esa carpeta
#   preparar_modelo.ps1 -TodasLasVoces         baja tambien las voces de otros idiomas a modelo\voces
#                                              (para publicarlas en el servidor)
# Sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.

param(
	[string]$Desde = "",
	[string]$Servidor = "https://xebolax.synology.me/Fonaria",
	[switch]$TodasLasVoces
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Raiz = Split-Path -Parent $PSScriptRoot
$Apk = Join-Path $Raiz "modelo\apk"
$CarpetaModelo = Join-Path $Apk "modelo"
$CarpetaVocesApk = Join-Path $Apk "voces"
$CarpetaVoces = Join-Path $Raiz "modelo\voces"
$Hf = "https://huggingface.co/gradium/phonon-voices/resolve/main/voices"
$Utf8 = New-Object System.Text.UTF8Encoding($false)
New-Item -ItemType Directory -Force $CarpetaModelo, $CarpetaVocesApk, $CarpetaVoces | Out-Null

function Sha($ruta) { (Get-FileHash -Algorithm SHA256 $ruta).Hash.ToLower() }

function Bajar($url, $destino, $sha) {
	if ((Test-Path $destino) -and (-not $sha -or (Sha $destino) -eq $sha)) { return }
	Write-Host "    Descargando $(Split-Path -Leaf $destino)..."
	$temporal = "$destino.parte"
	for ($intento = 1; $intento -le 3; $intento++) {
		try {
			Invoke-WebRequest -Uri $url -OutFile $temporal -UseBasicParsing -UserAgent "Fonaria-preparar"
			if ($sha -and (Sha $temporal) -ne $sha) { throw "el SHA-256 no coincide" }
			Move-Item -Force $temporal $destino
			return
		} catch {
			Remove-Item -Force $temporal -ErrorAction SilentlyContinue
			if ($intento -eq 3) { throw "No se pudo descargar $url : $_" }
			Start-Sleep -Seconds (3 * $intento)
		}
	}
}

# --- Modelo ---------------------------------------------------------------------
$modelos = [IO.File]::ReadAllText((Join-Path $Raiz "app\src\main\assets\modelos.json"), $Utf8) | ConvertFrom-Json
$q8 = $modelos.modelos | Where-Object { $_.id -eq "q8" }
Write-Host "==> Modelo (model.q8.gguf, config.json, tokenizer.json)" -ForegroundColor Cyan
foreach ($nombre in @("config.json", "tokenizer.json", "model.q8.gguf")) {
	$destino = Join-Path $CarpetaModelo $nombre
	$sha = if ($nombre -eq "model.q8.gguf") { $q8.sha256 } else { "" }
	if ($Desde) {
		Copy-Item -Force (Join-Path $Desde $nombre) $destino
		if ($sha -and (Sha $destino) -ne $sha) { Write-Host "[AVISO] $nombre no es el publicado (otro SHA-256); los modelos opcionales podrian no coincidir." }
	} else {
		Bajar "$Servidor/modelo/$nombre" $destino $sha
	}
}

# --- Voces ----------------------------------------------------------------------
$catalogo = [IO.File]::ReadAllText((Join-Path $Raiz "app\src\main\assets\catalogo_voces.json"), $Utf8) | ConvertFrom-Json
$incluidas = @($catalogo.voces | Where-Object { $_.incluida })
Write-Host "==> $($incluidas.Count) voces dentro del APK" -ForegroundColor Cyan
foreach ($v in $catalogo.voces) {
	if (-not $v.incluida -and -not $TodasLasVoces) { continue }
	$archivo = Join-Path $CarpetaVoces $v.archivo
	Bajar "$Hf/$($v.archivo)" $archivo $v.sha256
	if ($v.incluida) { Copy-Item -Force $archivo (Join-Path $CarpetaVocesApk $v.archivo) }
}
# Fuera del APK lo que ya no es del catalogo incluido
Get-ChildItem $CarpetaVocesApk -Filter *.safetensors | Where-Object { $incluidas.archivo -notcontains $_.Name } | Remove-Item -Force

Write-Host "[OK] Modelo y voces listos en $Apk" -ForegroundColor Green
exit 0
