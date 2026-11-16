package com.xebolax.fonaria.texto

import android.os.Build

/**
 * Escribe con letras los números sueltos antes de dárselos al motor.
 *
 * El motor deja en cifras los números de menos de mil y es la propia voz la que los lee: casi
 * siempre acierta, pero a veces los trocea («105 %» sonaba «cien y cinco por ciento»). Con letras
 * no hay duda: «ciento cinco por ciento». Los nombres salen de las reglas de Android (ICU), así
 * que valen para todos los idiomas de las voces.
 *
 * Solo se tocan los enteros de hasta seis cifras que van sueltos. Lo que tiene pinta de otra cosa
 * se deja como está, para el motor, que tiene reglas propias: horas (10:30), fechas (12/05),
 * decimales y miles con separador (1.234,5), versiones (1.0.6), teléfonos y códigos con letras.
 */
object Numeros {
	/** Un número con letras en un idioma; null si no se puede (se deja en cifras). */
	fun interface Lector {
		fun leer(numero: Long, idioma: String, masculino: Boolean): String?
	}

	private val numero = Regex("\\d+")
	private val porCiento = mapOf("es" to "por ciento", "en" to "percent", "fr" to "pour cent", "de" to "Prozent", "pt" to "por cento")
	/** Signos que, pegados a otra cifra, indican que el número es parte de algo mayor. */
	private const val UNIONES = ".,:/-"

	fun aplicar(texto: String, idioma: String, lector: Lector = Icu): String {
		if (texto.none { it in '0'..'9' }) return texto
		val sb = StringBuilder(texto.length + 32)
		var i = 0
		for (m in numero.findAll(texto)) {
			val ini = m.range.first
			val fin = m.range.last + 1
			sb.append(texto, i, ini)
			i = fin
			val cifras = m.value
			val suelto = cifras.length <= 6 && (cifras.length == 1 || cifras[0] != '0') && !pegado(texto, ini, fin)
			// ¿Le sigue un «%» (con o sin espacio)?
			var tras = fin
			while (tras < texto.length && (texto[tras] == ' ' || texto[tras] == ' ')) tras++
			val ciento = if (tras < texto.length && texto[tras] == '%') porCiento[idioma] else null
			// Delante de una palabra, en masculino apocopado: «21 mensajes» es «veintiún mensajes»
			val masculino = ciento != null || (tras > fin && tras < texto.length && texto[tras].isLetter())
			val letras = if (suelto) lector.leer(cifras.toLong(), idioma, masculino) else null
			sb.append(letras ?: cifras)
			if (ciento != null && letras != null) {
				sb.append(' ').append(ciento)
				i = tras + 1
			}
		}
		sb.append(texto, i, texto.length)
		return sb.toString()
	}

	/** Si el número de [ini] a [fin] está pegado a una letra o unido a otra cifra por un signo. */
	private fun pegado(t: String, ini: Int, fin: Int): Boolean {
		if (ini > 0) {
			val a = t[ini - 1]
			if (a.isLetter() || a == '_') return true
			if (a in UNIONES && ini > 1 && t[ini - 2].isDigit()) return true
		}
		if (fin < t.length) {
			val d = t[fin]
			if (d.isLetter() || d == '_' || d == 'º' || d == 'ª' || d == '°') return true
			if (d in UNIONES && fin + 1 < t.length && t[fin + 1].isDigit()) return true
		}
		return false
	}

	/**
	 * Las reglas de Android para decir números (ICU, desde Android 7), a través de MessageFormat:
	 * la clase que las aplica directamente no es pública.
	 */
	private object Icu : Lector {
		private val formatos = HashMap<String, android.icu.text.MessageFormat?>()

		private fun formato(idioma: String, regla: String): android.icu.text.MessageFormat? = formatos.getOrPut("$idioma$regla") {
			try {
				android.icu.text.MessageFormat("{0,spellout$regla}", java.util.Locale(idioma))
			} catch (e: Exception) {
				null
			}
		}

		@Synchronized
		override fun leer(numero: Long, idioma: String, masculino: Boolean): String? {
			if (Build.VERSION.SDK_INT < 24) return null
			return try {
				// En español, delante de un nombre: «un», «veintiún» (si el móvil trae esa regla)
				val f = (if (masculino && idioma == "es") formato(idioma, ",%spellout-cardinal-masculine") else null) ?: formato(idioma, "")
				f?.format(arrayOf<Any>(numero))?.takeIf { it.isNotBlank() && it.none { c -> c.isDigit() } }
			} catch (e: Exception) {
				null
			}
		}
	}
}
