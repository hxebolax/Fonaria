package com.xebolax.fonaria.texto

import android.content.Context
import android.os.Build
import android.text.Spanned
import android.text.style.TtsSpan
import com.xebolax.fonaria.datos.Ajustes

/**
 * Convierte lo que piden leer en trozos listos para el modelo, en este orden:
 *
 * 1. Las marcas de lectura de Android (TtsSpan): lo marcado como literal se deletrea.
 * 2. El diccionario de pronunciación.
 * 3. El deletreo: un carácter suelto se cambia por su nombre («b» → «be»).
 * 4. Los emojis, por su nombre.
 * 5. La puntuación, según el nivel elegido.
 * 6. La división en frases con sus pausas.
 *
 * Los números, fechas, horas, monedas, correos y direcciones web los normaliza después el
 * propio motor según el idioma (si está activado «Normalizar el texto»).
 */
object Preparador {

	fun preparar(contexto: Context, entrada: CharSequence, idioma: String, region: String, voz: String, ajustes: Ajustes): List<Trozo> {
		var texto = expandirMarcas(entrada, idioma, region)
		// Fuera caracteres de control (salvo saltos de línea y tabuladores)
		texto = texto.filter { it == '\n' || it == '\t' || !Character.isISOControl(it) }
		if (texto.isBlank()) return emptyList()
		if (ajustes.diccionarios) texto = Diccionario.aplicar(contexto, texto, idioma, voz)

		val solo = texto.trim()
		if (ajustes.deletrear && solo.codePointCount(0, solo.length) == 1 && !Character.isSurrogate(solo[0])) {
			return listOf(deletrear(solo[0], idioma, region, ajustes.tonoMayusculas))
		}
		if (ajustes.emojis) texto = Emojis.aplicar(contexto, texto, idioma)
		texto = puntuacion(texto, ajustes.puntuacion, idioma)
		if (texto.isBlank()) return emptyList()
		return if (ajustes.dividirFrases) {
			Trozos.dividir(texto, ajustes.pausaFrases, ajustes.pausaParrafos, ajustes.respuestaRapida)
		} else {
			listOf(Trozo.Habla(texto.replace(Regex("\\s+"), " ").trim()))
		}
	}

	/** Un carácter dicho por su nombre; las mayúsculas, con el tono más alto o con la palabra «mayúscula». */
	fun deletrear(c: Char, idioma: String, region: String, tonoMayusculas: Int): Trozo.Habla {
		val nombre = Simbolos.nombre(c, idioma, region) ?: c.toString()
		if (c.isUpperCase() && c.lowercaseChar() != c) {
			return if (tonoMayusculas > 0) Trozo.Habla(nombre, tonoMayusculas)
			else Trozo.Habla(Simbolos.mayuscula(idioma) + " " + nombre)
		}
		return Trozo.Habla(nombre)
	}

	/** Cambia los signos del nivel elegido por su nombre. Con «toda», el signo se queda detrás para conservar la entonación. */
	fun puntuacion(texto: String, nivel: String, idioma: String): String {
		val signos = Simbolos.signosDeNivel(nivel)
		if (signos.isEmpty()) return texto
		val nombres = Simbolos.signos(idioma)
		val sb = StringBuilder(texto.length + 16)
		for (c in texto) {
			val nombre = if (c in signos) nombres[c] else null
			if (nombre == null) {
				sb.append(c)
				continue
			}
			if (sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
			sb.append(nombre)
			// Los que marcan pausa siguen ahí para que la voz haga la pausa
			if (c in ".,;:!?") sb.append(c)
			sb.append(' ')
		}
		return sb.toString()
	}

	/**
	 * Las marcas TtsSpan que ponen algunas apps (y TalkBack): el texto «literal» (VERBATIM) se
	 * deletrea carácter a carácter; las demás se leen con el texto que traen, si traen alguno.
	 */
	fun expandirMarcas(entrada: CharSequence, idioma: String, region: String): String {
		if (entrada !is Spanned) return entrada.toString()
		val marcas = entrada.getSpans(0, entrada.length, TtsSpan::class.java)
		if (marcas.isEmpty()) return entrada.toString()
		val sb = StringBuilder()
		var i = 0
		for (m in marcas.sortedBy { entrada.getSpanStart(it) }) {
			val ini = entrada.getSpanStart(m)
			val fin = entrada.getSpanEnd(m)
			if (ini < i) continue
			sb.append(entrada, i, ini)
			val trozo = entrada.subSequence(ini, fin).toString()
			val args = m.args
			when (m.type) {
				TtsSpan.TYPE_VERBATIM -> {
					val literal = args.getString(TtsSpan.ARG_VERBATIM) ?: trozo
					sb.append(' ').append(literal.mapNotNull { c ->
						if (c.isWhitespace()) null else Simbolos.nombre(c, idioma, region) ?: c.toString()
					}.joinToString(" ")).append(' ')
				}
				TtsSpan.TYPE_TEXT -> sb.append(args.getString(TtsSpan.ARG_TEXT) ?: trozo)
				else -> {
					// Cardinal, ordinal, fecha, hora... llevan el texto visible; el motor ya lo normaliza
					val numero = if (Build.VERSION.SDK_INT >= 21) args.getString(TtsSpan.ARG_NUMBER) else null
					sb.append(if (m.type == TtsSpan.TYPE_CARDINAL && numero != null) numero else trozo)
				}
			}
			i = fin
		}
		sb.append(entrada, i, entrada.length)
		return sb.toString()
	}
}
