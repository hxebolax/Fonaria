package com.xebolax.fonaria.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.elegirUno
import com.xebolax.fonaria.accesibilidad.encabezado
import com.xebolax.fonaria.databinding.SeccionInicioBinding
import com.xebolax.fonaria.motor.Idiomas
import com.xebolax.fonaria.motor.Motor
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Voz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Inicio: si Fonaria es el motor del sistema, qué voces hay y una prueba rápida. */
class InicioFragment : Fragment() {
	private var _b: SeccionInicioBinding? = null
	private val b get() = _b!!
	private var locutor: Locutor? = null
	private var voz: Voz? = null

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = SeccionInicioBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		b.tituloEstado.encabezado()
		b.tituloProbar.encabezado()
		b.tituloAccesos.encabezado()
		locutor = Locutor(requireContext())
		b.motorSistema.setOnClickListener { PrincipalActivity.abrirAjustesVoz(requireContext()) }
		b.vozPrueba.setOnClickListener { elegirVoz() }
		b.hablar.setOnClickListener { probar() }
		b.detener.setOnClickListener { locutor?.parar() }
		b.irVoces.setOnClickListener { (activity as? PrincipalActivity)?.mostrar(R.id.destino_voces, VocesFragment.PESTANA_DESCARGAR) }
		b.irManual.setOnClickListener { startActivity(Intent(requireContext(), ManualActivity::class.java)) }
	}

	override fun onResume() {
		super.onResume()
		refrescar()
	}

	private fun refrescar() {
		val c = requireContext()
		val instaladas = Almacen.instaladas(c)
		if (voz == null || instaladas.none { it.id == voz?.id }) voz = Idiomas.elegirVoz(c, "es", "ES") ?: instaladas.firstOrNull()
		b.vozPrueba.text = voz?.let { getString(R.string.voz_de_prueba, it.nombre, Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje)) }
			?: getString(R.string.sin_voces)
		if (b.textoPrueba.text.isNullOrEmpty()) b.textoPrueba.setText(Idiomas.muestra(c, voz?.idioma ?: "es"))
		viewLifecycleOwner.lifecycleScope.launch {
			val predeterminado = Settings.Secure.getString(c.contentResolver, "tts_default_synth")
			val esMotor = predeterminado == c.packageName
			val idiomas = instaladas.map { Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje) }.distinct()
			val listo = withContext(Dispatchers.IO) { Almacen.preparar(c) }
			_b ?: return@launch
			b.estado.text = buildString {
				append(getString(if (esMotor) R.string.estado_es_motor else R.string.estado_no_es_motor))
				append("\n")
				append(resources.getQuantityString(R.plurals.voces_instaladas, instaladas.size, instaladas.size, idiomas.joinToString(", ")))
				if (!listo) append("\n").append(getString(R.string.estado_sin_modelo))
				Motor.error?.let { append("\n").append(getString(R.string.estado_error_motor, it)) }
			}
			b.motorSistema.text = getString(if (esMotor) R.string.ajustes_voz_sistema else R.string.elegir_motor_sistema)
		}
	}

	private fun elegirVoz() {
		val c = requireContext()
		val voces = Almacen.instaladas(c)
		if (voces.isEmpty()) return
		val nombres = voces.map { "${it.nombre}, ${Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje)}" }
		c.elegirUno(getString(R.string.elegir_voz), nombres, voces.indexOfFirst { it.id == voz?.id }) { i ->
			val anterior = voz
			voz = voces[i]
			// Si el texto era la muestra de otro idioma, se cambia por la del idioma nuevo
			if (b.textoPrueba.text.toString() == Idiomas.muestra(c, anterior?.idioma ?: "es")) {
				b.textoPrueba.setText(Idiomas.muestra(c, voces[i].idioma))
			}
			refrescar()
		}
	}

	private fun probar() {
		val v = voz ?: return
		val texto = b.textoPrueba.text?.toString()?.trim().orEmpty().ifEmpty { Idiomas.muestra(requireContext(), v.idioma) }
		locutor?.decir(texto, v.locale, v.id)
	}

	override fun onDestroyView() {
		locutor?.cerrar()
		locutor = null
		_b = null
		super.onDestroyView()
	}
}
