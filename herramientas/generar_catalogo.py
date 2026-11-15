"""Genera el catálogo de voces de Fonaria a partir del catálogo oficial de Gradium.

Entrada:  modelo/voces/voices.jsonl y los archivos <id>.safetensors de modelo/voces
          (los descarga herramientas/preparar_modelo.ps1 desde huggingface.co/gradium/phonon-voices).
Salida:   app/src/main/assets/catalogo_voces.json  (lo que la app sabe de cada voz)
          modelo/apk/voces/<id>.safetensors        (las voces en español que van dentro del APK)

Uso:  python herramientas/generar_catalogo.py

El catálogo de Gradium es Apache-2.0. Las descripciones en español de las voces
españolas y mexicanas son una traducción libre de las originales.
"""

import hashlib
import json
import shutil
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
VOCES = RAIZ / "modelo" / "voces"
SALIDA = RAIZ / "app" / "src" / "main" / "assets" / "catalogo_voces.json"
APK = RAIZ / "modelo" / "apk" / "voces"

# Idioma que va dentro del APK; el resto se descarga.
INCLUIDO = "es"

DESCRIPCIONES_ES = {
	"Noa": "Voz joven castellana, brillante y segura, con un encanto rápido y cercano: una asistente alegre que da chispa a cada conversación.",
	"Lucía-Sol": "Voz joven castellana, saltarina y alegre, con una entonación ascendente simpática y energía juguetona.",
	"Candela": "Voz joven castellana, entusiasta y burbujeante, animada como una presentadora de tutoriales.",
	"Celia": "Chispeante y directa, ligera de aire pero firme en cada consonante; cierra las frases sin forzar.",
	"Alba": "Voz joven castellana, dulce y expresiva, suave y con un énfasis vivo; ágil y al grano.",
	"Alicia": "Redonda y suavemente resonante, de registro bajo, con frases que se posan con delicadeza y disculpas que suenan sinceras.",
	"Marcos": "Voz adulta peninsular, segura y optimista, de energía contagiosa.",
	"Borja": "Resonante y contundente, con una lectura rítmica que destaca incluso sobre música alta.",
	"Iker": "Voz adulta peninsular, tranquila y cercana, que da confianza al instante.",
	"Nuno": "Resonante y relajado, de registro medio, con un toque juguetón al final de las frases.",
	"Izan": "Ataque nítido en las consonantes, energía hacia delante y una lectura contundente.",
	"Guillermo": "Grave y resonante, de tono asentado, con frases que se posan suavemente.",
	"Nerea": "Tono redondo y ligeramente resonante, con una leve subida al final de las frases y una lectura serena.",
	"Rebeca": "Voz llena y asentada en los graves, consonantes firmes y una pegada decidida.",
	"Regina": "Voz adulta mexicana, segura y optimista, con la naturalidad de quien sabe convencer.",
	"Danae": "Chispeante y contundente, de agudos ligeros, ataque limpio en cada palabra y ritmo ágil.",
	"Azucena": "Registro grave con autoridad serena, que nace de la calma y no del volumen; su dicción aguanta velocidades altas.",
	"Jimena": "Ataque nítido y lectura chispeante, con una entonación que sube y baja dentro de la misma frase.",
	"Ximena": "Voz adulta mexicana, juguetona y expresiva, que convierte lo cotidiano en historias.",
	"Lalo": "Graves llenos y redondos, frases concisas y una caída decidida al final.",
	"Aldo": "Energía cercana, como de micrófono pegado, consonantes nítidas y entonación que sube y baja.",
	"Yael": "Tono cercano y vibrante, con una sonrisa en la voz; baja en una frase y remonta en el remate.",
	"Ulises": "Grave y resonante, de lectura asentada, con consonantes que se entienden bien incluso por teléfono.",
	"Leonel": "Aterciopelado y templado, sincero al disculparse y contento de verdad con las buenas noticias.",
	"Nico": "Chispeante y al grano, con una articulación clara y un ritmo ágil.",
}

EDADES = {
	"Young": "joven",
	"Young Adult": "joven_adulta",
	"Adult": "adulta",
	"Middle-age": "mediana",
	"Older Adult": "mayor",
}


def sha256(ruta: Path) -> str:
	return hashlib.sha256(ruta.read_bytes()).hexdigest()


def main() -> int:
	origen = VOCES / "voices.jsonl"
	if not origen.is_file():
		print(f"[ERROR] Falta {origen}. Ejecuta antes herramientas\\preparar_modelo.ps1")
		return 1
	voces = []
	APK.mkdir(parents=True, exist_ok=True)
	for linea in origen.read_text(encoding="utf-8").splitlines():
		if not linea.strip():
			continue
		v = json.loads(linea)
		archivo = Path(v["filename"]).name
		ruta = VOCES / archivo
		if not ruta.is_file():
			print(f"[ERROR] Falta el archivo de la voz {v['name']}: {ruta}")
			return 1
		locale = (v.get("locale") or "").lower()
		region = locale.split("-")[1].upper() if "-" in locale else ""
		incluida = v["language"] == INCLUIDO
		voces.append({
			"id": v["voice_id"],
			"nombre": v["name"],
			"idioma": v["language"],
			"region": region,
			"personaje": "-" not in locale,
			"genero": (v.get("gender") or "")[:1],
			"edad": EDADES.get(v.get("age") or "", ""),
			"descripcion": v.get("description") or "",
			"descripcion_es": DESCRIPCIONES_ES.get(v["name"], ""),
			"archivo": archivo,
			"tamano": ruta.stat().st_size,
			"sha256": sha256(ruta),
			"incluida": incluida,
		})
		if incluida:
			shutil.copy2(ruta, APK / archivo)
	voces.sort(key=lambda x: (x["idioma"] != INCLUIDO, x["idioma"], x["region"], x["nombre"].casefold()))
	SALIDA.parent.mkdir(parents=True, exist_ok=True)
	SALIDA.write_text(
		json.dumps({"version": 1, "origen": "https://huggingface.co/gradium/phonon-voices", "voces": voces}, ensure_ascii=False, indent="\t"),
		encoding="utf-8",
	)
	incluidas = sum(1 for x in voces if x["incluida"])
	print(f"[OK] {len(voces)} voces en el catálogo; {incluidas} van dentro del APK.")
	return 0


if __name__ == "__main__":
	sys.exit(main())
