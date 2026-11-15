package com.xebolax.fonaria.motor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo

/**
 * Responde a Android qué idiomas tienen voces instaladas (CHECK_TTS_DATA): los que tienen al
 * menos una voz van como disponibles; los demás del catálogo, como pendientes de descargar.
 */
class ComprobarDatosActivity : Activity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val instaladas = Almacen.instaladas(this)
		val todas = Catalogo.voces(this).filter { !it.personaje }.map { it.idioma to it.region }.distinct()
		fun etiqueta(par: Pair<String, String>): String {
			val (i, r) = Idiomas.aIso3(par.first, par.second)
			return if (r.isEmpty()) i else "$i-$r"
		}
		val disponibles = todas.filter { (i, r) -> instaladas.any { it.idioma == i && it.region == r } }
		val faltan = todas - disponibles.toSet()
		setResult(
			TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
			Intent()
				.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ArrayList(disponibles.map(::etiqueta)))
				.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList(faltan.map(::etiqueta))),
		)
		finish()
	}
}

/** La frase de ejemplo que Android dice en «Escuchar un ejemplo» (GET_SAMPLE_TEXT). */
class TextoMuestraActivity : Activity() {
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val idioma = Idiomas.codigo(intent?.getStringExtra("language")) ?: "es"
		setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra("sampleText", Idiomas.muestra(this, idioma)))
		finish()
	}
}
