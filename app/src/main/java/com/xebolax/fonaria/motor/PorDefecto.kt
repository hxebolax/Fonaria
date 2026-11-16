package com.xebolax.fonaria.motor

import android.content.Context
import com.xebolax.fonaria.datos.Ajustes

/**
 * Los nombres de voz que Fonaria ha dado a Android como «voz por defecto» de un idioma.
 *
 * Al conectar, quien habla (TalkBack, cualquier app) pregunta cuál es la voz por defecto y desde
 * entonces manda ese nombre en cada frase, como si la hubiera elegido. Con «Respetar la voz que
 * pida cada app» activado, eso dejaba clavada la voz que había al conectar: se elegía otra en
 * Fonaria y seguía sonando la anterior hasta reiniciar. Un nombre de esta lista no cuenta como
 * elección de la app. Se guarda, porque quien habla lo recuerda aunque el motor se reinicie.
 */
object PorDefecto {
	private const val CLAVE = "voces_por_defecto"
	private var nombres: MutableSet<String>? = null

	private fun estado(contexto: Context) =
		Ajustes.contextoProtegido(contexto.applicationContext ?: contexto).getSharedPreferences("estado", Context.MODE_PRIVATE)

	private fun cargados(contexto: Context): MutableSet<String> =
		nombres ?: HashSet(estado(contexto).getStringSet(CLAVE, emptySet()) ?: emptySet()).also { nombres = it }

	@Synchronized
	fun anotar(contexto: Context, nombre: String) {
		val n = cargados(contexto)
		if (n.add(nombre)) estado(contexto).edit().putStringSet(CLAVE, HashSet(n)).apply()
	}

	@Synchronized
	fun es(contexto: Context, nombre: String): Boolean = nombre in cargados(contexto)
}
