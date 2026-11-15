package com.xebolax.fonaria.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.xebolax.fonaria.motor.ServicioVoz
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Las pantallas de Fonaria hablan a través del propio motor del sistema (el servicio de este
 * paquete), igual que lo haría cualquier app: así el modelo se carga una sola vez y lo que se
 * oye aquí es exactamente lo que oirá TalkBack.
 */
class Locutor(contexto: Context, private val alCambiar: (hablando: Boolean) -> Unit = {}) {
	private val app = contexto.applicationContext
	private var listo = false
	private var pendiente: (() -> Unit)? = null
	private val alTerminarArchivo = HashMap<String, (Boolean) -> Unit>()

	private val tts: TextToSpeech = TextToSpeech(app, { estado ->
		listo = estado == TextToSpeech.SUCCESS
		if (!listo) Log.e("Fonaria.Locutor", "No se pudo conectar con el motor ($estado)")
		pendiente?.invoke()
		pendiente = null
	}, app.packageName)

	init {
		tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(id: String?) = alCambiar(true)
			override fun onDone(id: String?) {
				alCambiar(false)
				id?.let { alTerminarArchivo.remove(it)?.invoke(true) }
			}

			@Deprecated("Obligatorio en API 21")
			override fun onError(id: String?) {
				alCambiar(false)
				id?.let { alTerminarArchivo.remove(it)?.invoke(false) }
			}

			override fun onError(id: String?, codigo: Int) {
				alCambiar(false)
				id?.let { alTerminarArchivo.remove(it)?.invoke(false) }
			}

			override fun onStop(id: String?, interrumpido: Boolean) = alCambiar(false)
		})
	}

	private fun cuandoEsteListo(accion: () -> Unit) {
		if (listo) accion() else pendiente = accion
	}

	private fun parametros(vozId: String?) = Bundle().apply { if (vozId != null) putString(ServicioVoz.PARAM_VOZ, vozId) }

	/** Dice [texto] con la voz [vozId] (o la elegida para el idioma) en [locale]. */
	fun decir(texto: String, locale: Locale, vozId: String? = null) = cuandoEsteListo {
		tts.language = locale
		tts.speak(texto, TextToSpeech.QUEUE_FLUSH, parametros(vozId), UUID.randomUUID().toString())
	}

	/** Guarda [texto] dicho en un archivo WAV. [alTerminar] recibe si salió bien. */
	fun guardar(texto: String, locale: Locale, vozId: String?, archivo: File, alTerminar: (Boolean) -> Unit) = cuandoEsteListo {
		val id = UUID.randomUUID().toString()
		alTerminarArchivo[id] = alTerminar
		tts.language = locale
		if (tts.synthesizeToFile(texto, parametros(vozId), archivo, id) != TextToSpeech.SUCCESS) {
			alTerminarArchivo.remove(id)
			alTerminar(false)
		}
	}

	fun parar() {
		if (listo) tts.stop()
	}

	fun cerrar() {
		tts.stop()
		tts.shutdown()
	}
}
