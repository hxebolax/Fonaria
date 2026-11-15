package com.xebolax.fonaria.texto

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Lee los emojis por su nombre («cara sonriente», «pulgar hacia arriba: tono de piel medio»)
 * con los nombres cortos de Unicode CLDR (assets/emojis/<idioma>.json, licencia Unicode).
 *
 * Sin esto el modelo se salta los emojis o los dice mal. Busca primero las secuencias más
 * largas (familias, banderas, tonos de piel) y después los emojis sueltos.
 */
object Emojis {
	private const val ETIQUETA = "Fonaria.Emojis"
	/** Selector de variación 16: «muéstralo como emoji». CLDR lo quita de sus claves. */
	private const val VS16 = '️'

	private class Tabla(val nombres: Map<String, String>, val maxLargo: Int)

	private val tablas = HashMap<String, Tabla>()

	private fun tabla(contexto: Context, idioma: String): Tabla? = synchronized(tablas) {
		tablas[idioma]?.let { return it }
		val leida = try {
			val json = contexto.assets.open("emojis/$idioma.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
			val o = JSONObject(json)
			val mapa = HashMap<String, String>(o.length() * 2)
			var largo = 1
			for (clave in o.keys()) {
				val limpia = clave.replace(VS16.toString(), "")
				mapa[limpia] = o.getString(clave)
				largo = maxOf(largo, limpia.length)
			}
			Tabla(mapa, largo)
		} catch (e: Exception) {
			if (idioma != "es") Log.w(ETIQUETA, "Sin nombres de emojis para $idioma", e)
			null
		}
		if (leida != null) tablas[idioma] = leida
		leida
	}

	/** ¿Puede haber un emoji en el texto? (Atajo para no recorrer lo que es solo texto normal.) */
	private fun quizas(texto: String): Boolean = texto.any { Character.isSurrogate(it) || it.code in 0x2190..0x2BFF || it == '©' || it == '®' }

	fun aplicar(contexto: Context, texto: String, idioma: String): String {
		if (!quizas(texto)) return texto
		val t = tabla(contexto, idioma) ?: tabla(contexto, "en") ?: return texto
		val limpio = texto.replace(VS16.toString(), "")
		val sb = StringBuilder(limpio.length + 32)
		var i = 0
		while (i < limpio.length) {
			var hallado: String? = null
			var largo = minOf(t.maxLargo, limpio.length - i)
			while (largo > 0) {
				val nombre = t.nombres[limpio.substring(i, i + largo)]
				if (nombre != null) {
					hallado = nombre
					break
				}
				largo--
			}
			if (hallado != null) {
				if (sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
				sb.append(hallado).append(' ')
				i += largo
			} else {
				sb.append(limpio[i])
				i++
			}
		}
		return sb.toString()
	}
}
