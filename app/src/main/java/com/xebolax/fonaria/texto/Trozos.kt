package com.xebolax.fonaria.texto

/** Un trozo de lo que hay que decir: texto o silencio. */
sealed class Trozo {
	/** Texto para el modelo. [subirTono] (por ciento) para una mayúscula deletreada. */
	data class Habla(val texto: String, val subirTono: Int = 0) : Trozo()

	data class Pausa(val ms: Int) : Trozo()
}

/**
 * Divide un texto en frases para que el primer audio llegue antes y para que «callar» corte
 * enseguida: el modelo empieza a sonar cuando tiene la primera frase, no el párrafo entero.
 *
 * Una frase termina en . ! ? … seguido de espacio, salvo en abreviaturas («Sr. García», «p. ej.»)
 * e iniciales («J. R. R. Tolkien»). Los puntos de las cifras (3.14) y de las direcciones web no
 * cortan porque no van seguidos de espacio.
 */
object Trozos {
	private val ABREVIATURAS = setOf(
		// Español
		"sr", "sra", "srta", "sres", "dr", "dra", "d", "dña", "ud", "uds", "vd", "vds", "lic", "ing", "arq", "prof", "pág", "págs",
		"p", "pp", "ej", "etc", "núm", "nº", "tel", "av", "avda", "c", "cap", "vol", "art", "aprox", "dpto", "admón", "cía", "sta",
		"sto", "fig", "máx", "mín", "aa", "ee", "uu", "a.c", "d.c", "vs",
		// Inglés, francés, alemán y portugués
		"mr", "mrs", "ms", "st", "jr", "sr", "inc", "ltd", "co", "no", "mme", "mlle", "m", "z", "b", "bzw", "usw", "ca", "nr",
		"sra", "exmo", "exma",
	)

	/** Signos que cierran una frase. */
	private fun finDeFrase(c: Char) = c == '.' || c == '!' || c == '?' || c == '…'

	/**
	 * Parte [texto] en frases con su pausa. [pausaFrase] va entre frases y [pausaParrafo] entre
	 * párrafos (salto de línea). Con [rapida], una primera frase larga se parte también en la
	 * primera coma, para empezar a hablar antes.
	 */
	fun dividir(texto: String, pausaFrase: Int, pausaParrafo: Int, rapida: Boolean = false): List<Trozo> {
		val salida = mutableListOf<Trozo>()
		val parrafos = texto.split(Regex("\\s*\\n\\s*")).filter { it.isNotBlank() }
		for ((ip, parrafo) in parrafos.withIndex()) {
			if (ip > 0 && pausaParrafo > 0) salida += Trozo.Pausa(pausaParrafo)
			val frases = agrupar(frases(parrafo))
			for ((i, f) in frases.withIndex()) {
				if (i > 0 && pausaFrase > 0) salida += Trozo.Pausa(pausaFrase)
				if (rapida && salida.none { it is Trozo.Habla } && f.length > 80) {
					val coma = f.indexOf(',', 25)
					if (coma in 25 until f.length - 10) {
						salida += Trozo.Habla(f.substring(0, coma + 1).trim())
						salida += Trozo.Habla(f.substring(coma + 1).trim())
						continue
					}
				}
				salida += Trozo.Habla(f)
			}
		}
		return salida
	}

	/**
	 * Junta las frases cortas seguidas hasta reunir al menos [MINIMO] caracteres. Cada trozo que se
	 * genera por separado le cuesta al motor un arranque (un cuarto de segundo sin entregar voz):
	 * con frases de dos palabras («Voces. Pestaña 2 de 5.») gastaba más en arrancar que en hablar
	 * y la voz se quedaba sin audio entre una y otra, que es lo que suena a chisporroteo.
	 */
	fun agrupar(frases: List<String>): List<String> {
		val grupos = mutableListOf<String>()
		val actual = StringBuilder()
		for (f in frases) {
			if (actual.isNotEmpty()) actual.append(' ')
			actual.append(f)
			if (actual.length >= MINIMO) {
				grupos += actual.toString()
				actual.setLength(0)
			}
		}
		if (actual.isNotEmpty()) {
			// Lo que queda al final, si es poco, se une al grupo anterior
			if (grupos.isNotEmpty() && actual.length < MINIMO / 2) grupos[grupos.size - 1] = grupos.last() + " " + actual
			else grupos += actual.toString()
		}
		return grupos
	}

	/** Caracteres que debe reunir un trozo para generarse por separado. */
	const val MINIMO = 90

	/** Las frases de un párrafo, sin espacios sobrantes. */
	fun frases(parrafo: String): List<String> {
		val resultado = mutableListOf<String>()
		var inicio = 0
		var i = 0
		val n = parrafo.length
		while (i < n) {
			val c = parrafo[i]
			if (finDeFrase(c)) {
				var fin = i + 1
				// Varios signos seguidos («?!», «...»), y comillas o paréntesis que cierran
				while (fin < n && (finDeFrase(parrafo[fin]) || parrafo[fin] in "\"'»”’)]")) fin++
				val siguienteEsEspacio = fin >= n || parrafo[fin].isWhitespace()
				if (siguienteEsEspacio && !(c == '.' && esAbreviatura(parrafo, i))) {
					val frase = parrafo.substring(inicio, fin).trim()
					if (frase.isNotEmpty()) resultado += frase
					inicio = fin
				}
				i = fin
				continue
			}
			i++
		}
		val resto = parrafo.substring(inicio).trim()
		if (resto.isNotEmpty()) resultado += resto
		return resultado
	}

	/** ¿El punto de la posición [punto] es de una abreviatura o una inicial? */
	private fun esAbreviatura(texto: String, punto: Int): Boolean {
		var j = punto - 1
		while (j >= 0 && (texto[j].isLetter() || texto[j] == '.')) j--
		val palabra = texto.substring(j + 1, punto)
		if (palabra.isEmpty()) return false
		// Una inicial: una sola letra mayúscula («J.»)
		if (palabra.length == 1 && palabra[0].isUpperCase()) return true
		if (palabra.lowercase() in ABREVIATURAS) {
			// «etc.» al final de la frase sí la termina si lo que sigue empieza en mayúscula... no se sabe: se sigue
			return true
		}
		// «EE.UU.», «p.ej.»: con puntos dentro
		return palabra.contains('.')
	}
}
