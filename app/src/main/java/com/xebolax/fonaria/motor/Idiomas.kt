package com.xebolax.fonaria.motor

import android.content.Context
import android.speech.tts.TextToSpeech
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Voz
import java.util.Locale
import java.util.MissingResourceException

/**
 * Cómo se entienden los idiomas que pide Android con las voces instaladas.
 *
 * Android pide en ISO 639-2 y 3166 de tres letras («spa», «ESP»); el modelo habla «es», «en»,
 * «fr», «de» y «pt». La región decide la voz preferida (España o México, Reino Unido o EE. UU.).
 */
object Idiomas {
	/** Los idiomas que sabe hablar el modelo multilingüe. */
	val DEL_MODELO = listOf("es", "en", "fr", "de", "pt")

	private val tres = mapOf(
		"spa" to "es", "es" to "es", "eng" to "en", "en" to "en", "fra" to "fr", "fre" to "fr", "fr" to "fr",
		"deu" to "de", "ger" to "de", "de" to "de", "por" to "pt", "pt" to "pt",
	)

	private val paises: Map<String, String> by lazy {
		Locale.getISOCountries().associateBy { c ->
			try {
				Locale("", c).isO3Country.uppercase()
			} catch (e: MissingResourceException) {
				c
			}
		}
	}

	/** «spa» o «es» → «es»; null si el modelo no lo habla. */
	fun codigo(idioma: String?): String? = idioma?.lowercase()?.let { tres[it] }

	/** «ESP» o «ES» → «ES»; vacío si no se sabe. */
	fun region(pais: String?): String {
		val p = pais?.uppercase().orEmpty()
		return when (p.length) {
			2 -> p
			3 -> paises[p].orEmpty()
			else -> ""
		}
	}

	/** «es», «ES» → «spa», «ESP» para Android. */
	fun aIso3(idioma: String, region: String): Pair<String, String> {
		val l = Locale(idioma, region)
		val i3 = try { l.isO3Language } catch (e: MissingResourceException) { idioma }
		val r3 = if (region.isEmpty()) "" else try { l.isO3Country } catch (e: MissingResourceException) { region }
		return i3 to r3
	}

	/**
	 * La voz que habla [idioma] y [region]:
	 * 1. La que pide la app, si así se ha elegido en Ajustes.
	 * 2. La elegida en Fonaria para ese idioma y región, o para el idioma.
	 * 3. Cualquiera instalada de ese idioma (mejor de esa región).
	 * 4. Si falta, una de reserva (la española elegida o la primera), si así se ha elegido.
	 */
	fun elegirVoz(contexto: Context, idioma: String, region: String, pedida: String? = null): Voz? {
		val ajustes = Ajustes(contexto)
		val instaladas = Almacen.instaladas(contexto)
		if (instaladas.isEmpty()) return null
		if (ajustes.respetarVozPedida && !pedida.isNullOrEmpty()) {
			instaladas.firstOrNull { it.nombreSistema == pedida }?.let { return it }
		}
		ajustes.vozPara(idioma, region)?.let { id -> instaladas.firstOrNull { it.id == id }?.let { return it } }
		instaladas.firstOrNull { it.idioma == idioma && it.region.equals(region, true) && !it.personaje }?.let { return it }
		instaladas.firstOrNull { it.idioma == idioma && !it.personaje }?.let { return it }
		instaladas.firstOrNull { it.idioma == idioma }?.let { return it }
		if (!ajustes.vozDeReserva) return null
		ajustes.vozPara("es", "ES")?.let { id -> instaladas.firstOrNull { it.id == id }?.let { return it } }
		return instaladas.firstOrNull { it.idioma == "es" } ?: instaladas.first()
	}

	/** Lo que se responde a Android al preguntar si un idioma está disponible. */
	fun disponibilidad(contexto: Context, idioma: String?, pais: String?): Int {
		val codigo = codigo(idioma) ?: return TextToSpeech.LANG_NOT_SUPPORTED
		val region = region(pais)
		val instaladas = Almacen.instaladas(contexto)
		val delIdioma = instaladas.filter { it.idioma == codigo }
		return when {
			region.isNotEmpty() && delIdioma.any { it.region.equals(region, true) } -> TextToSpeech.LANG_COUNTRY_AVAILABLE
			delIdioma.isNotEmpty() -> TextToSpeech.LANG_AVAILABLE
			Ajustes(contexto).vozDeReserva && instaladas.isNotEmpty() -> TextToSpeech.LANG_AVAILABLE
			else -> TextToSpeech.LANG_MISSING_DATA
		}
	}

	/** Frase de ejemplo de cada idioma, para probar una voz. */
	fun muestra(contexto: Context, idioma: String): String {
		val id = when (idioma) {
			"en" -> com.xebolax.fonaria.R.string.muestra_en
			"fr" -> com.xebolax.fonaria.R.string.muestra_fr
			"de" -> com.xebolax.fonaria.R.string.muestra_de
			"pt" -> com.xebolax.fonaria.R.string.muestra_pt
			else -> com.xebolax.fonaria.R.string.muestra_es
		}
		return contexto.getString(id)
	}

	/** Las voces del catálogo de un idioma, para listarlas. */
	fun delCatalogo(contexto: Context, idioma: String) = Catalogo.voces(contexto).filter { it.idioma == idioma }
}
