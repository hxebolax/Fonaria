package com.xebolax.fonaria.datos

import android.content.Context
import com.xebolax.fonaria.BuildConfig
import com.xebolax.fonaria.texto.Diccionario
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia de la configuración: todos los ajustes (también los de cada voz) y el diccionario, en
 * un JSON que se puede guardar donde se quiera y cargar en otro móvil. Las voces no van dentro:
 * se vuelven a descargar.
 */
object Copias {
	/** Claves que no se copian: dependen de este móvil. */
	private val PROPIAS_DEL_MOVIL = setOf("ultima_comprobacion", "version_omitida", "version_anterior", "bienvenida_vista", "aviso_idioma_visto")

	fun exportar(contexto: Context): String {
		val p = Ajustes(contexto).p
		val ajustes = JSONObject()
		for ((clave, valor) in p.all) {
			if (clave in PROPIAS_DEL_MOVIL || valor == null) continue
			val tipo = when (valor) {
				is Boolean -> "booleano"
				is Int -> "entero"
				is Long -> "largo"
				is Float -> "decimal"
				else -> "texto"
			}
			ajustes.put(clave, JSONObject().put("tipo", tipo).put("valor", valor))
		}
		return JSONObject()
			.put("fonaria", 1)
			.put("version_app", BuildConfig.VERSION_NAME)
			.put("ajustes", ajustes)
			.put("diccionario", JSONArray().apply { Diccionario.entradas(contexto).forEach { put(it.json()) } })
			.toString(1)
	}

	/** Carga una copia. Devuelve cuántos ajustes se han cargado. Lanza excepción si no es una copia de Fonaria. */
	fun importar(contexto: Context, texto: String): Int {
		val o = JSONObject(texto)
		require(o.has("fonaria")) { "No es una copia de Fonaria" }
		val ajustes = o.optJSONObject("ajustes") ?: JSONObject()
		val e = Ajustes(contexto).p.edit()
		var n = 0
		for (clave in ajustes.keys()) {
			if (clave in PROPIAS_DEL_MOVIL) continue
			val a = ajustes.getJSONObject(clave)
			when (a.optString("tipo")) {
				"booleano" -> e.putBoolean(clave, a.getBoolean("valor"))
				"entero" -> e.putInt(clave, a.getInt("valor"))
				"largo" -> e.putLong(clave, a.getLong("valor"))
				"decimal" -> e.putFloat(clave, a.getDouble("valor").toFloat())
				else -> e.putString(clave, a.getString("valor"))
			}
			n++
		}
		e.apply()
		val dic = o.optJSONArray("diccionario")
		if (dic != null) {
			Diccionario.guardar(contexto, (0 until dic.length()).map { com.xebolax.fonaria.texto.Entrada.de(dic.getJSONObject(it)) })
		}
		return n
	}

	/** Todos los ajustes a sus valores de fábrica. El diccionario y las voces se conservan. */
	fun restablecer(contexto: Context) {
		val p = Ajustes(contexto).p
		val e = p.edit()
		p.all.keys.filter { it !in PROPIAS_DEL_MOVIL }.forEach { e.remove(it) }
		e.apply()
	}
}
