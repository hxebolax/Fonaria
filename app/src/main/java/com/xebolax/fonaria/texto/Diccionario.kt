package com.xebolax.fonaria.texto

import android.content.Context
import android.util.Log
import com.xebolax.fonaria.voces.Almacen
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Cómo busca una entrada su patrón en el texto. */
enum class TipoEntrada(val clave: String) {
	/** Solo palabras completas: «sol» no cambia «soldado». */
	PALABRA("palabra"),

	/** En cualquier sitio, también dentro de otras palabras. */
	TEXTO("texto"),

	/** Expresión regular de Java; el reemplazo puede usar $1, $2... */
	REGEX("regex");

	companion object {
		fun de(clave: String) = entries.firstOrNull { it.clave == clave } ?: PALABRA
	}
}

/** Una entrada del diccionario de pronunciación. */
data class Entrada(
	val id: String = UUID.randomUUID().toString(),
	val patron: String,
	val reemplazo: String,
	val tipo: TipoEntrada = TipoEntrada.PALABRA,
	val mayusculas: Boolean = false,
	/** Idioma en el que se aplica (es, en...) o vacío para todos. */
	val idioma: String = "",
	/** Solo con esta voz (id), o vacío para todas. */
	val voz: String = "",
	val activa: Boolean = true,
	val comentario: String = "",
) {
	fun json(): JSONObject = JSONObject()
		.put("id", id).put("patron", patron).put("reemplazo", reemplazo).put("tipo", tipo.clave)
		.put("mayusculas", mayusculas).put("idioma", idioma).put("voz", voz).put("activa", activa)
		.put("comentario", comentario)

	/** La expresión regular que busca esta entrada, o null si no es válida. */
	fun patronCompilado(): Pattern? = try {
		val banderas = if (mayusculas) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
		val expresion = when (tipo) {
			TipoEntrada.REGEX -> patron
			TipoEntrada.TEXTO -> Pattern.quote(patron)
			// Límites de palabra que entienden letras con tilde y la ñ
			TipoEntrada.PALABRA -> "(?<![\\p{L}\\p{N}_])" + Pattern.quote(patron) + "(?![\\p{L}\\p{N}_])"
		}
		Pattern.compile(expresion, banderas)
	} catch (e: PatternSyntaxException) {
		null
	}

	companion object {
		fun de(o: JSONObject) = Entrada(
			id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
			patron = o.getString("patron"),
			reemplazo = o.optString("reemplazo"),
			tipo = TipoEntrada.de(o.optString("tipo")),
			mayusculas = o.optBoolean("mayusculas"),
			idioma = o.optString("idioma"),
			voz = o.optString("voz"),
			activa = o.optBoolean("activa", true),
			comentario = o.optString("comentario"),
		)
	}
}

/**
 * El diccionario de pronunciación: palabras que la voz dice mal y cómo deben sonar. Se guarda
 * en `fonaria/diccionario.json` (almacenamiento protegido del dispositivo, para que funcione
 * también en la pantalla de bloqueo).
 *
 * Importa y exporta el formato de los diccionarios de voz de NVDA (.dic: patrón, reemplazo,
 * distinguir mayúsculas y tipo, separados por tabuladores), así que sirven los que ya tenga
 * quien viene de Windows.
 */
object Diccionario {
	private const val ETIQUETA = "Fonaria.Diccionario"

	private data class Compilada(val entrada: Entrada, val patron: Pattern)

	@Volatile
	private var compiladas: List<Compilada> = emptyList()
	@Volatile
	private var marcaCompiladas = -1L

	fun archivo(contexto: Context) = File(Almacen.base(contexto), "diccionario.json")

	fun entradas(contexto: Context): List<Entrada> {
		val f = archivo(contexto)
		if (!f.isFile) return emptyList()
		return try {
			val lista = JSONObject(f.readText()).getJSONArray("entradas")
			(0 until lista.length()).map { Entrada.de(lista.getJSONObject(it)) }
		} catch (e: Exception) {
			Log.e(ETIQUETA, "Diccionario ilegible; se aparta una copia", e)
			f.copyTo(File(f.parentFile, "diccionario.dañado.json"), overwrite = true)
			emptyList()
		}
	}

	@Synchronized
	fun guardar(contexto: Context, entradas: List<Entrada>) {
		val f = archivo(contexto)
		f.parentFile?.mkdirs()
		val json = JSONObject().put("version", 1).put("entradas", JSONArray().apply { entradas.forEach { put(it.json()) } })
		val temporal = File(f.parentFile, f.name + ".tmp")
		temporal.writeText(json.toString(1))
		if (!temporal.renameTo(f)) {
			f.delete()
			temporal.renameTo(f)
		}
		marcaCompiladas = -1
	}

	/** Aplica las entradas activas que valen para [idioma] y [voz]. */
	fun aplicar(contexto: Context, texto: String, idioma: String, voz: String): String {
		val f = archivo(contexto)
		val marca = if (f.isFile) f.lastModified() xor f.length() else 0L
		if (marca != marcaCompiladas) {
			compiladas = entradas(contexto).filter { it.activa && it.patron.isNotEmpty() }.mapNotNull { e ->
				e.patronCompilado()?.let { Compilada(e, it) } ?: run {
					Log.w(ETIQUETA, "Entrada con patrón no válido: ${e.patron}")
					null
				}
			}
			marcaCompiladas = marca
		}
		var resultado = texto
		for (c in compiladas) {
			if (c.entrada.idioma.isNotEmpty() && c.entrada.idioma != idioma) continue
			if (c.entrada.voz.isNotEmpty() && c.entrada.voz != voz) continue
			resultado = reemplazar(c.patron, resultado, c.entrada)
		}
		return resultado
	}

	/** Reemplaza todas las apariciones; en las que no son expresión regular, el reemplazo es literal. */
	fun reemplazar(patron: Pattern, texto: String, e: Entrada): String {
		val m = patron.matcher(texto)
		if (!m.find()) return texto
		val reemplazo = if (e.tipo == TipoEntrada.REGEX) e.reemplazo else Matcher.quoteReplacement(e.reemplazo)
		return try {
			m.reset()
			m.replaceAll(reemplazo)
		} catch (ex: Exception) {
			// $9 sin grupo 9, por ejemplo: la entrada no se aplica, pero el texto se lee
			Log.w(ETIQUETA, "Reemplazo no válido en ${e.patron}", ex)
			texto
		}
	}

	// --- Importar y exportar -------------------------------------------------------------

	/**
	 * Lee un diccionario: el formato de Fonaria (JSON) o el de NVDA (.dic). Una línea con solo
	 * «palabra<TAB>reemplazo» también vale. Las líneas que empiezan por # son comentarios.
	 */
	fun leer(contenido: String): List<Entrada> {
		val limpio = contenido.removePrefix("﻿").trim()
		if (limpio.startsWith("{")) {
			val lista = JSONObject(limpio).getJSONArray("entradas")
			return (0 until lista.length()).map { Entrada.de(lista.getJSONObject(it)).copy(id = UUID.randomUUID().toString()) }
		}
		val resultado = mutableListOf<Entrada>()
		var comentario = ""
		for (linea in limpio.lines()) {
			if (linea.isBlank()) continue
			if (linea.startsWith("#")) {
				comentario = linea.removePrefix("#").trim()
				continue
			}
			val partes = linea.split('\t')
			if (partes.size < 2 || partes[0].isEmpty()) continue
			// NVDA: 0 = en cualquier sitio, 1 = expresión regular, 2 = palabra completa
			val tipo = when (partes.getOrNull(3)?.trim()) {
				"0" -> TipoEntrada.TEXTO
				"1" -> TipoEntrada.REGEX
				else -> TipoEntrada.PALABRA
			}
			// NVDA guarda las barras invertidas del reemplazo de una regex como \1; Java usa $1
			val reemplazo = if (tipo == TipoEntrada.REGEX) partes[1].replace(Regex("\\\\(\\d)"), "\\$$1") else partes[1]
			resultado += Entrada(
				patron = partes[0],
				reemplazo = reemplazo,
				tipo = tipo,
				mayusculas = partes.getOrNull(2)?.trim() == "1",
				comentario = comentario,
			)
			comentario = ""
		}
		return resultado
	}

	/** Formato de NVDA: se puede usar tal cual en NVDA (Diccionarios de voz). */
	fun exportarNvda(entradas: List<Entrada>): String = buildString {
		for (e in entradas) {
			if (e.comentario.isNotEmpty()) append("#").append(e.comentario.replace('\n', ' ')).append("\r\n")
			val tipo = when (e.tipo) {
				TipoEntrada.TEXTO -> 0
				TipoEntrada.REGEX -> 1
				TipoEntrada.PALABRA -> 2
			}
			val reemplazo = if (e.tipo == TipoEntrada.REGEX) e.reemplazo.replace(Regex("\\$(\\d)"), "\\\\$1") else e.reemplazo
			append(e.patron).append('\t').append(reemplazo).append('\t')
				.append(if (e.mayusculas) 1 else 0).append('\t').append(tipo).append("\r\n")
		}
	}

	/** Formato de Fonaria: guarda también el idioma, la voz y si está activa. */
	fun exportarJson(entradas: List<Entrada>): String =
		JSONObject().put("version", 1).put("entradas", JSONArray().apply { entradas.forEach { put(it.json()) } }).toString(1)
}
