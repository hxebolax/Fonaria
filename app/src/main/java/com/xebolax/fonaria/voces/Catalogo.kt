package com.xebolax.fonaria.voces

import android.content.Context
import android.util.Log
import com.xebolax.fonaria.R
import org.json.JSONObject
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/** Una voz del catálogo de Gradium (o importada por el usuario). */
data class Voz(
	val id: String,
	val nombre: String,
	/** es, en, fr, de, pt. */
	val idioma: String,
	/** ES, MX, GB, US, FR, DE, BR, o vacío en las voces de personaje. */
	val region: String,
	/** Voz de personaje (inglés de fantasía): sin región. */
	val personaje: Boolean,
	/** f o m. */
	val genero: String,
	/** joven, joven_adulta, adulta, mediana, mayor. */
	val edad: String,
	val descripcion: String,
	val descripcionEs: String,
	val descripcionPt: String = "",
	val archivo: String,
	val tamano: Long,
	val sha256: String,
	/** Viene dentro del APK. */
	val incluida: Boolean,
	/** La importó el usuario (archivo de Gradium Studio). */
	val importada: Boolean = false,
) {
	/** «es-ES», «en-GB», «en» (personajes). */
	val etiquetaIdioma get() = if (region.isEmpty()) idioma else "$idioma-$region"

	val locale: Locale get() = if (region.isEmpty()) Locale(idioma) else Locale(idioma, region)

	/** Nombre para el sistema Android: «es-ES-Celia». Único entre todas las voces. */
	val nombreSistema get() = if (importada) "$etiquetaIdioma-$nombre-$id" else "$etiquetaIdioma-$nombre"
}

/**
 * El catálogo de voces (assets/catalogo_voces.json, generado por herramientas/generar_catalogo.py
 * a partir del de Gradium): todas las voces que existen, instaladas o no.
 */
object Catalogo {
	private const val ETIQUETA = "Fonaria.Catalogo"

	@Volatile
	private var cache: List<Voz>? = null

	fun voces(contexto: Context): List<Voz> {
		cache?.let { return it }
		return synchronized(this) {
			cache ?: leer(contexto).also { cache = it }
		}
	}

	private fun leer(contexto: Context): List<Voz> = try {
		val json = contexto.assets.open("catalogo_voces.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
		val lista = JSONObject(json).getJSONArray("voces")
		(0 until lista.length()).map { i ->
			val o = lista.getJSONObject(i)
			Voz(
				id = o.getString("id"),
				nombre = o.getString("nombre"),
				idioma = o.getString("idioma"),
				region = o.optString("region"),
				personaje = o.optBoolean("personaje"),
				genero = o.optString("genero"),
				edad = o.optString("edad"),
				descripcion = o.optString("descripcion"),
				descripcionEs = o.optString("descripcion_es"),
				descripcionPt = o.optString("descripcion_pt"),
				archivo = o.getString("archivo"),
				tamano = o.optLong("tamano"),
				sha256 = o.optString("sha256"),
				incluida = o.optBoolean("incluida"),
			)
		}
	} catch (e: Exception) {
		Log.e(ETIQUETA, "No se pudo leer el catálogo de voces", e)
		emptyList()
	}

	fun buscar(contexto: Context, id: String) = voces(contexto).firstOrNull { it.id == id }

	/** Idiomas del catálogo, con el español primero. */
	fun idiomas(contexto: Context): List<String> =
		voces(contexto).map { it.idioma }.distinct().sortedBy { if (it == "es") "" else it }

	/** Nombre del idioma y la región en el idioma de la interfaz: «Español (España)». */
	fun nombreIdioma(contexto: Context, idioma: String, region: String = "", personaje: Boolean = false): String {
		if (personaje) return contexto.getString(R.string.idioma_personajes)
		val local = Locale(idioma, region)
		val ui = idiomaInterfaz(contexto)
		val nombre = local.getDisplayLanguage(ui).replaceFirstChar { it.titlecase(ui) }
		return if (region.isEmpty()) nombre else "$nombre (${local.getDisplayCountry(ui)})"
	}

	/** «Mujer joven» / «Hombre de mediana edad». */
	fun perfil(contexto: Context, voz: Voz): String {
		if (voz.personaje || voz.genero.isEmpty()) return contexto.getString(R.string.perfil_personaje)
		val mujer = voz.genero == "f"
		val id = when (voz.edad) {
			"joven" -> if (mujer) R.string.perfil_mujer_joven else R.string.perfil_hombre_joven
			"joven_adulta" -> if (mujer) R.string.perfil_mujer_joven_adulta else R.string.perfil_hombre_joven_adulto
			"adulta" -> if (mujer) R.string.perfil_mujer_adulta else R.string.perfil_hombre_adulto
			"mediana" -> if (mujer) R.string.perfil_mujer_mediana else R.string.perfil_hombre_mediana
			"mayor" -> if (mujer) R.string.perfil_mujer_mayor else R.string.perfil_hombre_mayor
			else -> if (mujer) R.string.perfil_mujer else R.string.perfil_hombre
		}
		return contexto.getString(id)
	}

	/** La descripción en el idioma de la interfaz si la hay; si no, la original en inglés. */
	fun descripcion(contexto: Context, voz: Voz): String {
		val ui = idiomaInterfaz(contexto).language
		return when {
			ui == "es" && voz.descripcionEs.isNotEmpty() -> voz.descripcionEs
			ui == "pt" && voz.descripcionPt.isNotEmpty() -> voz.descripcionPt
			else -> voz.descripcion
		}
	}
}

/** El idioma en que se ve la interfaz (Android 5 y posteriores). */
fun idiomaInterfaz(contexto: Context): Locale =
	ConfigurationCompat.getLocales(contexto.resources.configuration).get(0) ?: Locale.getDefault()
