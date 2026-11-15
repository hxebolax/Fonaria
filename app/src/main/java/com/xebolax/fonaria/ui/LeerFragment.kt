package com.xebolax.fonaria.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.elegirUno
import com.xebolax.fonaria.databinding.SeccionLeerBinding
import com.xebolax.fonaria.motor.Idiomas
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Voz
import java.io.File

/** Leer texto: escribir o pegar un texto, oírlo con cualquier voz y guardarlo como audio WAV. */
class LeerFragment : Fragment() {
	private var _b: SeccionLeerBinding? = null
	private val b get() = _b!!
	private var locutor: Locutor? = null
	private var voz: Voz? = null
	private var temporal: File? = null

	private val guardarComo = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri -> uri?.let { copiar(it) } }

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = SeccionLeerBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		val c = requireContext()
		locutor = Locutor(c) { hablando ->
			activity?.runOnUiThread { _b?.estado?.text = getString(if (hablando) R.string.leyendo else R.string.lectura_terminada) }
		}
		val id = estado?.getString("voz")
		voz = Almacen.instaladas(c).firstOrNull { it.id == id } ?: Idiomas.elegirVoz(c, "es", "ES")
		mostrarVoz()
		b.voz.setOnClickListener { elegirVoz() }
		b.leer.setOnClickListener {
			val v = voz ?: return@setOnClickListener
			val texto = b.texto.text?.toString().orEmpty()
			if (texto.isBlank()) {
				b.contenedorTexto.error = getString(R.string.escribe_algo)
				return@setOnClickListener
			}
			b.contenedorTexto.error = null
			locutor?.decir(texto, v.locale, v.id)
		}
		b.detener.setOnClickListener { locutor?.parar() }
		b.pegar.setOnClickListener {
			val portapapeles = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
			val texto = portapapeles.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString()
			if (texto.isNullOrEmpty()) b.estado.text = getString(R.string.portapapeles_vacio)
			else {
				b.texto.setText(texto)
				b.estado.text = getString(R.string.texto_pegado, texto.length)
			}
		}
		b.guardar.setOnClickListener { generar() }
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		voz?.let { estado.putString("voz", it.id) }
	}

	private fun mostrarVoz() {
		val c = requireContext()
		b.voz.text = voz?.let { getString(R.string.voz_x, it.nombre, Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje)) }
			?: getString(R.string.sin_voces)
	}

	private fun elegirVoz() {
		val c = requireContext()
		val voces = Almacen.instaladas(c)
		c.elegirUno(getString(R.string.elegir_voz), voces.map { "${it.nombre}, ${Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje)}" }, voces.indexOfFirst { it.id == voz?.id }) { i ->
			voz = voces[i]
			mostrarVoz()
		}
	}

	/** Genera el WAV en la caché y después pregunta dónde guardarlo. */
	private fun generar() {
		val v = voz ?: return
		val texto = b.texto.text?.toString().orEmpty()
		if (texto.isBlank()) {
			b.contenedorTexto.error = getString(R.string.escribe_algo)
			return
		}
		b.contenedorTexto.error = null
		val archivo = File(requireContext().cacheDir, "fonaria_audio.wav").apply { delete() }
		b.estado.text = getString(R.string.generando_audio)
		locutor?.guardar(texto, v.locale, v.id, archivo) { bien ->
			activity?.runOnUiThread {
				if (_b == null) return@runOnUiThread
				if (bien && archivo.length() > 44) {
					temporal = archivo
					b.estado.text = getString(R.string.audio_listo)
					guardarComo.launch(getString(R.string.nombre_audio, v.nombre))
				} else {
					b.estado.text = getString(R.string.audio_fallo)
				}
			}
		}
	}

	private fun copiar(uri: Uri) {
		val origen = temporal ?: return
		try {
			requireContext().contentResolver.openOutputStream(uri)?.use { salida -> origen.inputStream().use { it.copyTo(salida) } }
			b.estado.text = getString(R.string.audio_guardado)
		} catch (e: Exception) {
			Log.e("Fonaria.Leer", "No se pudo guardar el audio", e)
			b.estado.text = getString(R.string.audio_no_guardado)
		} finally {
			origen.delete()
			temporal = null
		}
	}

	override fun onDestroyView() {
		locutor?.cerrar()
		locutor = null
		_b = null
		super.onDestroyView()
	}
}
