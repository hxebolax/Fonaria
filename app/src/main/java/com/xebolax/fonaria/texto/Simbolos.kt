package com.xebolax.fonaria.texto

/**
 * Nombres de letras, signos de puntuación y símbolos en los cinco idiomas del modelo.
 *
 * Phonon es un modelo neuronal: lee muy bien las frases, pero una letra o un signo sueltos
 * («b», «#») no siempre los dice bien. Para el deletreo y para leer la puntuación se le pasa
 * su nombre escrito («be», «almohadilla»).
 */
object Simbolos {

	/** Niveles de lectura de la puntuación, de menos a más. */
	const val NINGUNA = "ninguna"
	const val ALGUNA = "alguna"
	const val MAYORIA = "mayoria"
	const val TODA = "toda"

	/** Signos que se leen en cada nivel (cada nivel incluye los anteriores). */
	private const val NIVEL_ALGUNA = "#$%&*+/<=>@\\^_|~€£¥§©®°•…"
	private const val NIVEL_MAYORIA = "()[]{}\"'«»“”‘’¡¿-–—"
	private const val NIVEL_TODA = ".,;:!?"

	fun signosDeNivel(nivel: String): String = when (nivel) {
		ALGUNA -> NIVEL_ALGUNA
		MAYORIA -> NIVEL_ALGUNA + NIVEL_MAYORIA
		TODA -> NIVEL_ALGUNA + NIVEL_MAYORIA + NIVEL_TODA
		else -> ""
	}

	private val signosEs = mapOf(
		'.' to "punto", ',' to "coma", ';' to "punto y coma", ':' to "dos puntos", '!' to "cierra exclamación",
		'?' to "cierra interrogación", '¡' to "abre exclamación", '¿' to "abre interrogación", '(' to "abre paréntesis",
		')' to "cierra paréntesis", '[' to "abre corchete", ']' to "cierra corchete", '{' to "abre llave", '}' to "cierra llave",
		'"' to "comillas", '\'' to "apóstrofo", '«' to "abre comillas", '»' to "cierra comillas", '“' to "abre comillas",
		'”' to "cierra comillas", '‘' to "abre comilla", '’' to "cierra comilla", '-' to "guion", '–' to "raya", '—' to "raya",
		'#' to "almohadilla", '$' to "dólar", '%' to "por ciento", '&' to "et", '*' to "asterisco", '+' to "más",
		'/' to "barra", '<' to "menor que", '=' to "igual", '>' to "mayor que", '@' to "arroba", '\\' to "barra invertida",
		'^' to "circunflejo", '_' to "guion bajo", '|' to "barra vertical", '~' to "tilde", '€' to "euro", '£' to "libra",
		'¥' to "yen", '§' to "sección", '©' to "copyright", '®' to "marca registrada", '°' to "grado", '•' to "viñeta",
		'…' to "puntos suspensivos", ' ' to "espacio", '\n' to "salto de línea", '\t' to "tabulador",
	)

	private val signosEn = mapOf(
		'.' to "dot", ',' to "comma", ';' to "semicolon", ':' to "colon", '!' to "exclamation mark", '?' to "question mark",
		'¡' to "inverted exclamation mark", '¿' to "inverted question mark", '(' to "left paren", ')' to "right paren",
		'[' to "left bracket", ']' to "right bracket", '{' to "left brace", '}' to "right brace", '"' to "quote",
		'\'' to "apostrophe", '«' to "left angle quote", '»' to "right angle quote", '“' to "left quote", '”' to "right quote",
		'‘' to "left single quote", '’' to "right single quote", '-' to "dash", '–' to "en dash", '—' to "em dash",
		'#' to "number sign", '$' to "dollar", '%' to "percent", '&' to "and", '*' to "star", '+' to "plus", '/' to "slash",
		'<' to "less than", '=' to "equals", '>' to "greater than", '@' to "at", '\\' to "backslash", '^' to "caret",
		'_' to "underscore", '|' to "bar", '~' to "tilde", '€' to "euro", '£' to "pound", '¥' to "yen", '§' to "section",
		'©' to "copyright", '®' to "registered", '°' to "degrees", '•' to "bullet", '…' to "ellipsis", ' ' to "space",
		'\n' to "new line", '\t' to "tab",
	)

	private val signosFr = mapOf(
		'.' to "point", ',' to "virgule", ';' to "point-virgule", ':' to "deux-points", '!' to "point d'exclamation",
		'?' to "point d'interrogation", '(' to "parenthèse ouvrante", ')' to "parenthèse fermante", '[' to "crochet ouvrant",
		']' to "crochet fermant", '{' to "accolade ouvrante", '}' to "accolade fermante", '"' to "guillemet",
		'\'' to "apostrophe", '«' to "guillemet ouvrant", '»' to "guillemet fermant", '-' to "tiret", '–' to "tiret moyen",
		'—' to "tiret long", '#' to "dièse", '$' to "dollar", '%' to "pour cent", '&' to "et commercial", '*' to "étoile",
		'+' to "plus", '/' to "barre oblique", '<' to "inférieur à", '=' to "égal", '>' to "supérieur à", '@' to "arobase",
		'\\' to "barre oblique inversée", '^' to "accent circonflexe", '_' to "souligné", '|' to "barre verticale",
		'~' to "tilde", '€' to "euro", '£' to "livre", '°' to "degré", '•' to "puce", '…' to "points de suspension",
		' ' to "espace", '\n' to "retour à la ligne", '\t' to "tabulation",
	)

	private val signosDe = mapOf(
		'.' to "Punkt", ',' to "Komma", ';' to "Semikolon", ':' to "Doppelpunkt", '!' to "Ausrufezeichen",
		'?' to "Fragezeichen", '(' to "Klammer auf", ')' to "Klammer zu", '[' to "eckige Klammer auf",
		']' to "eckige Klammer zu", '{' to "geschweifte Klammer auf", '}' to "geschweifte Klammer zu",
		'"' to "Anführungszeichen", '\'' to "Apostroph", '«' to "Anführungszeichen auf", '»' to "Anführungszeichen zu",
		'„' to "Anführungszeichen unten", '“' to "Anführungszeichen oben", '-' to "Bindestrich", '–' to "Gedankenstrich",
		'—' to "langer Strich", '#' to "Raute", '$' to "Dollar", '%' to "Prozent", '&' to "und", '*' to "Stern",
		'+' to "plus", '/' to "Schrägstrich", '<' to "kleiner als", '=' to "gleich", '>' to "größer als", '@' to "at",
		'\\' to "umgekehrter Schrägstrich", '^' to "Zirkumflex", '_' to "Unterstrich", '|' to "senkrechter Strich",
		'~' to "Tilde", '€' to "Euro", '£' to "Pfund", '°' to "Grad", '•' to "Aufzählungszeichen", '…' to "Auslassungspunkte",
		' ' to "Leerzeichen", '\n' to "neue Zeile", '\t' to "Tabulator",
	)

	private val signosPt = mapOf(
		'.' to "ponto", ',' to "vírgula", ';' to "ponto e vírgula", ':' to "dois pontos", '!' to "exclamação",
		'?' to "interrogação", '(' to "abre parênteses", ')' to "fecha parênteses", '[' to "abre colchetes",
		']' to "fecha colchetes", '{' to "abre chaves", '}' to "fecha chaves", '"' to "aspas", '\'' to "apóstrofo",
		'«' to "abre aspas", '»' to "fecha aspas", '“' to "abre aspas", '”' to "fecha aspas", '-' to "hífen",
		'–' to "travessão", '—' to "travessão", '#' to "cerquilha", '$' to "cifrão", '%' to "por cento", '&' to "e comercial",
		'*' to "asterisco", '+' to "mais", '/' to "barra", '<' to "menor que", '=' to "igual", '>' to "maior que",
		'@' to "arroba", '\\' to "barra invertida", '^' to "circunflexo", '_' to "sublinhado", '|' to "barra vertical",
		'~' to "til", '€' to "euro", '£' to "libra", '°' to "grau", '•' to "marcador", '…' to "reticências",
		' ' to "espaço", '\n' to "nova linha", '\t' to "tabulação",
	)

	private val letrasEs = mapOf(
		'a' to "a", 'b' to "be", 'c' to "ce", 'd' to "de", 'e' to "e", 'f' to "efe", 'g' to "ge", 'h' to "hache", 'i' to "i",
		'j' to "jota", 'k' to "ka", 'l' to "ele", 'm' to "eme", 'n' to "ene", 'ñ' to "eñe", 'o' to "o", 'p' to "pe", 'q' to "cu",
		'r' to "erre", 's' to "ese", 't' to "te", 'u' to "u", 'v' to "uve", 'w' to "uve doble", 'x' to "equis", 'y' to "i griega",
		'z' to "zeta", 'á' to "a con tilde", 'é' to "e con tilde", 'í' to "i con tilde", 'ó' to "o con tilde", 'ú' to "u con tilde",
		'ü' to "u con diéresis", 'ç' to "ce cedilla", 'à' to "a con acento grave", 'è' to "e con acento grave",
	)

	private val letrasEn = mapOf(
		'a' to "ay", 'b' to "bee", 'c' to "see", 'd' to "dee", 'e' to "ee", 'f' to "ef", 'g' to "gee", 'h' to "aitch", 'i' to "eye",
		'j' to "jay", 'k' to "kay", 'l' to "el", 'm' to "em", 'n' to "en", 'o' to "oh", 'p' to "pee", 'q' to "cue", 'r' to "ar",
		's' to "ess", 't' to "tee", 'u' to "you", 'v' to "vee", 'w' to "double you", 'x' to "ex", 'y' to "why", 'z' to "zed",
	)

	private val letrasFr = mapOf(
		'a' to "a", 'b' to "bé", 'c' to "cé", 'd' to "dé", 'e' to "eu", 'f' to "effe", 'g' to "gé", 'h' to "ache", 'i' to "i",
		'j' to "ji", 'k' to "ka", 'l' to "elle", 'm' to "emme", 'n' to "enne", 'o' to "o", 'p' to "pé", 'q' to "ku", 'r' to "erre",
		's' to "esse", 't' to "té", 'u' to "u", 'v' to "vé", 'w' to "double vé", 'x' to "ixe", 'y' to "i grec", 'z' to "zède",
		'é' to "e accent aigu", 'è' to "e accent grave", 'ê' to "e accent circonflexe", 'à' to "a accent grave",
		'ç' to "cé cédille", 'ù' to "u accent grave", 'ô' to "o accent circonflexe", 'î' to "i accent circonflexe",
	)

	private val letrasDe = mapOf(
		'a' to "a", 'b' to "be", 'c' to "ze", 'd' to "de", 'e' to "e", 'f' to "eff", 'g' to "ge", 'h' to "ha", 'i' to "i",
		'j' to "jott", 'k' to "ka", 'l' to "ell", 'm' to "emm", 'n' to "enn", 'o' to "o", 'p' to "pe", 'q' to "ku", 'r' to "err",
		's' to "ess", 't' to "te", 'u' to "u", 'v' to "fau", 'w' to "we", 'x' to "iks", 'y' to "üpsilon", 'z' to "zett",
		'ä' to "a Umlaut", 'ö' to "o Umlaut", 'ü' to "u Umlaut", 'ß' to "Eszett",
	)

	private val letrasPt = mapOf(
		'a' to "á", 'b' to "bê", 'c' to "cê", 'd' to "dê", 'e' to "é", 'f' to "éfe", 'g' to "gê", 'h' to "agá", 'i' to "i",
		'j' to "jota", 'k' to "cá", 'l' to "éle", 'm' to "ême", 'n' to "ene", 'o' to "ó", 'p' to "pê", 'q' to "quê", 'r' to "erre",
		's' to "ésse", 't' to "tê", 'u' to "u", 'v' to "vê", 'w' to "dáblio", 'x' to "xis", 'y' to "ípsilon", 'z' to "zê",
		'ç' to "cê cedilha", 'á' to "a com acento agudo", 'â' to "a com circunflexo", 'ã' to "a com til", 'é' to "e com acento agudo",
		'ê' to "e com circunflexo", 'í' to "i com acento agudo", 'ó' to "o com acento agudo", 'ô' to "o com circunflexo",
		'õ' to "o com til", 'ú' to "u com acento agudo",
	)

	private val digitos = mapOf(
		"es" to listOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"),
		"en" to listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"),
		"fr" to listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf"),
		"de" to listOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"),
		"pt" to listOf("zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove"),
	)

	fun signos(idioma: String) = when (idioma) {
		"en" -> signosEn
		"fr" -> signosFr
		"de" -> signosDe
		"pt" -> signosPt
		else -> signosEs
	}

	private fun letras(idioma: String) = when (idioma) {
		"en" -> letrasEn
		"fr" -> letrasFr
		"de" -> letrasDe
		"pt" -> letrasPt
		else -> letrasEs
	}

	/** El nombre de un carácter suelto (letra, cifra o signo), o null si no se conoce. */
	fun nombre(c: Char, idioma: String, region: String = ""): String? {
		val minuscula = c.lowercaseChar()
		if (idioma == "en" && minuscula == 'z' && region.equals("US", true)) return "zee"
		letras(idioma)[minuscula]?.let { return it }
		if (c in '0'..'9') return digitos[idioma]?.get(c - '0') ?: digitos.getValue("es")[c - '0']
		return signos(idioma)[c]
	}

	/** Palabra para «mayúscula» en cada idioma. */
	fun mayuscula(idioma: String) = when (idioma) {
		"en" -> "capital"
		"fr" -> "majuscule"
		"de" -> "groß"
		"pt" -> "maiúscula"
		else -> "mayúscula"
	}
}
