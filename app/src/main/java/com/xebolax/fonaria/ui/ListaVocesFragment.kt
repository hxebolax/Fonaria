package com.xebolax.fonaria.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.Accion
import com.xebolax.fonaria.accesibilidad.avisar
import com.xebolax.fonaria.accesibilidad.confirmar
import com.xebolax.fonaria.accesibilidad.conEspera
import com.xebolax.fonaria.accesibilidad.elegirUno
import com.xebolax.fonaria.accesibilidad.mensaje
import com.xebolax.fonaria.accesibilidad.pedirTexto
import com.xebolax.fonaria.accesibilidad.sinFocoPropio
import com.xebolax.fonaria.databinding.ListaBinding
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.datos.motivo
import com.xebolax.fonaria.motor.Idiomas
import com.xebolax.fonaria.motor.Motor
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Descargas
import com.xebolax.fonaria.voces.Voz
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Una pestaña de Voces:
 * - Instaladas: las que se pueden usar, con Escuchar, Usar para su idioma, Ajustes de la voz y Borrar.
 * - Descargar: las del catálogo que faltan, una a una o un idioma entero.
 * - Importadas: voces propias de Gradium Studio (.safetensors).
 */
class ListaVocesFragment : Fragment() {
	private var _b: ListaBinding? = null
	private val b get() = _b!!
	private val adaptador = AdaptadorFilas()
	private var locutor: Locutor? = null
	private var filtro = ""
	private var descarga: Job? = null
	private val modo get() = arguments?.getInt(ARG_MODO) ?: VocesFragment.PESTANA_INSTALADAS

	private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importar(it) } }

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = ListaBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		filtro = estado?.getString("filtro") ?: ""
		locutor = Locutor(requireContext())
		b.lista.layoutManager = LinearLayoutManager(requireContext())
		b.lista.adapter = adaptador
		b.lista.sinFocoPropio()
		b.accion.visibility = View.VISIBLE
		when (modo) {
			VocesFragment.PESTANA_DESCARGAR -> {
				b.accion.text = getString(R.string.descargar_idioma_completo)
				b.accion.setOnClickListener { elegirIdiomaParaDescargar() }
			}
			VocesFragment.PESTANA_IMPORTADAS -> {
				b.accion.text = getString(R.string.importar_voz)
				b.accion.setOnClickListener { elegirArchivo.launch(arrayOf("application/octet-stream", "*/*")) }
			}
			else -> {
				b.accion.text = getString(R.string.filtrar_idioma_todos)
				b.accion.setOnClickListener { elegirFiltro() }
			}
		}
	}

	override fun onSaveInstanceState(estado: Bundle) {
		super.onSaveInstanceState(estado)
		estado.putString("filtro", filtro)
	}

	override fun onResume() {
		super.onResume()
		refrescar()
	}

	private fun nombreIdioma(v: Voz) = Catalogo.nombreIdioma(requireContext(), v.idioma, v.region, v.personaje)

	private fun refrescar() {
		val c = context ?: return
		if (_b == null) return
		val ajustes = Ajustes(c)
		val voces = when (modo) {
			VocesFragment.PESTANA_DESCARGAR -> Catalogo.voces(c).filter { !Almacen.instalada(c, it) }
			VocesFragment.PESTANA_IMPORTADAS -> Almacen.importadas(c)
			else -> Almacen.instaladas(c).filter { !it.importada }
		}.filter { filtro.isEmpty() || it.idioma == filtro }
		adaptador.filas = voces.map { v -> fila(v, ajustes) }
		b.estado.text = when {
			voces.isNotEmpty() && modo == VocesFragment.PESTANA_DESCARGAR -> resources.getQuantityString(R.plurals.voces_por_descargar, voces.size, voces.size)
			voces.isNotEmpty() && modo == VocesFragment.PESTANA_IMPORTADAS -> resources.getQuantityString(R.plurals.voces_importadas, voces.size, voces.size)
			voces.isNotEmpty() -> resources.getQuantityString(R.plurals.voces_en_lista, voces.size, voces.size)
			modo == VocesFragment.PESTANA_DESCARGAR -> getString(R.string.todas_descargadas)
			modo == VocesFragment.PESTANA_IMPORTADAS -> getString(R.string.sin_importadas)
			else -> getString(R.string.sin_voces)
		}
		if (modo == VocesFragment.PESTANA_INSTALADAS) {
			b.accion.text = if (filtro.isEmpty()) getString(R.string.filtrar_idioma_todos)
			else getString(R.string.filtrar_idioma_uno, Catalogo.nombreIdioma(c, filtro))
		}
	}

	private fun fila(v: Voz, ajustes: Ajustes): Fila {
		val c = requireContext()
		val idioma = nombreIdioma(v)
		val preferida = ajustes.vozPara(v.idioma, v.region) == v.id
		val detalle = buildString {
			if (!v.importada) append(Catalogo.perfil(c, v)).append(", ")
			append(idioma)
			if (preferida && modo != VocesFragment.PESTANA_DESCARGAR) append(". ").append(getString(R.string.voz_preferida))
			if (modo == VocesFragment.PESTANA_DESCARGAR) append(". ").append(Formatter.formatShortFileSize(c, v.tamano))
			val descripcion = Catalogo.descripcion(c, v)
			if (descripcion.isNotEmpty()) append(". ").append(descripcion)
		}
		val acciones = mutableListOf<Accion>()
		if (modo == VocesFragment.PESTANA_DESCARGAR) {
			acciones += getString(R.string.descargar_x, v.nombre) to { descargar(listOf(v)) }
			acciones += getString(R.string.descargar_todas_idioma, Catalogo.nombreIdioma(c, v.idioma)) to {
				descargar(Catalogo.voces(c).filter { it.idioma == v.idioma && !Almacen.instalada(c, it) })
			}
			return Fila(v.id, v.nombre, detalle, acciones, acciones.first())
		}
		acciones += getString(R.string.escuchar_x, v.nombre) to { escuchar(v) }
		if (!preferida) acciones += getString(R.string.usar_para, idioma) to { usar(v) }
		acciones += getString(R.string.ajustes_de_x, v.nombre) to {
			startActivity(Intent(c, VozActivity::class.java).putExtra(VozActivity.EXTRA_VOZ, v.id))
		}
		if (v.importada) {
			acciones += getString(R.string.renombrar_x, v.nombre) to { renombrar(v) }
			acciones += getString(R.string.cambiar_idioma_x, v.nombre) to { cambiarIdioma(v) }
		}
		if (!v.incluida) acciones += getString(R.string.borrar_x, v.nombre) to { borrar(v) }
		return Fila(v.id, v.nombre, detalle, acciones, acciones.first())
	}

	private fun escuchar(v: Voz) {
		locutor?.decir(getString(R.string.presentacion_voz, v.nombre) + " " + Idiomas.muestra(requireContext(), v.idioma), v.locale, v.id)
	}

	private fun usar(v: Voz) {
		Ajustes(requireContext()).ponerVoz(v.idioma, v.region.ifEmpty { null }, v.id)
		// Si no hay voz elegida para el idioma en general, también lo es
		if (Ajustes(requireContext()).texto("voz_${v.idioma}").isEmpty()) Ajustes(requireContext()).ponerVoz(v.idioma, null, v.id)
		(activity as? AppCompatActivity)?.avisar(getString(R.string.voz_elegida, v.nombre, nombreIdioma(v)))
		refrescar()
	}

	private fun borrar(v: Voz) {
		requireContext().confirmar(getString(R.string.confirmar_borrar_voz, v.nombre), getString(R.string.borrar)) {
			Almacen.borrar(requireContext(), v)
			Ajustes(requireContext()).quitarPropios(v.id)
			(activity as? AppCompatActivity)?.avisar(getString(R.string.voz_borrada, v.nombre))
			refrescar()
		}
	}

	private fun elegirFiltro() {
		val c = requireContext()
		val idiomas = listOf("") + Almacen.instaladas(c).map { it.idioma }.distinct()
		val nombres = idiomas.map { if (it.isEmpty()) getString(R.string.todos_los_idiomas) else Catalogo.nombreIdioma(c, it) }
		c.elegirUno(getString(R.string.filtrar_por_idioma), nombres, idiomas.indexOf(filtro)) { i ->
			filtro = idiomas[i]
			refrescar()
		}
	}

	// --- Descargar ------------------------------------------------------------------------

	private fun elegirIdiomaParaDescargar() {
		val c = requireContext()
		val faltan = Catalogo.voces(c).filter { !Almacen.instalada(c, it) }
		val idiomas = faltan.map { it.idioma }.distinct()
		if (idiomas.isEmpty()) {
			c.mensaje(getString(R.string.descargar_voces), getString(R.string.todas_descargadas))
			return
		}
		val nombres = idiomas.map { i ->
			val n = faltan.count { it.idioma == i }
			val bytes = faltan.filter { it.idioma == i }.sumOf { it.tamano }
			getString(R.string.idioma_con_voces, Catalogo.nombreIdioma(c, i), n, Formatter.formatShortFileSize(c, bytes))
		}
		c.elegirUno(getString(R.string.descargar_idioma_completo), nombres, -1) { i -> descargar(faltan.filter { it.idioma == idiomas[i] }) }
	}

	private fun descargar(voces: List<Voz>) {
		val actividad = activity as? AppCompatActivity ?: return
		if (voces.isEmpty() || descarga?.isActive == true) return
		val margen = (20 * resources.displayMetrics.density).toInt()
		val estado = TextView(actividad).apply {
			text = getString(R.string.descargando_inicio)
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		}
		val barra = LinearProgressIndicator(actividad).apply {
			max = 100
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		val vista = LinearLayout(actividad).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(margen, margen / 2, margen, 0)
			addView(estado)
			addView(barra)
		}
		val dialogo = MaterialAlertDialogBuilder(actividad)
			.setTitle(resources.getQuantityString(R.plurals.descargando_voces, voces.size, voces.size))
			.setView(vista)
			.setCancelable(false)
			.setNegativeButton(R.string.cancelar) { _, _ -> descarga?.cancel() }
			.show()
		val total = voces.sumOf { it.tamano }.coerceAtLeast(1)
		var hechos = 0L
		var ultimo = 0L
		descarga = viewLifecycleOwner.lifecycleScope.launch {
			val fallidas = mutableListOf<Pair<Voz, Exception>>()
			try {
				for ((i, v) in voces.withIndex()) {
					try {
						Descargas.descargar(actividad, v) { leidos, _ ->
							val ahora = SystemClock.uptimeMillis()
							if (ahora - ultimo > 300) {
								ultimo = ahora
								val pct = ((hechos + leidos) * 100 / total).toInt().coerceIn(0, 100)
								actividad.runOnUiThread {
									barra.progress = pct
									estado.text = getString(R.string.descargando_voz, v.nombre, i + 1, voces.size, pct)
								}
							}
						}
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						Log.e("Fonaria.Voces", "No se pudo descargar ${v.nombre}", e)
						fallidas += v to e
					}
					hechos += v.tamano
				}
				dialogo.dismiss()
				// El motor conoce las voces nuevas en la siguiente frase
				val bien = voces.size - fallidas.size
				if (fallidas.isEmpty()) actividad.avisar(resources.getQuantityString(R.plurals.voces_descargadas, bien, bien))
				else actividad.mensaje(
					getString(R.string.descargar_voces),
					getString(R.string.descarga_con_fallos, bien, fallidas.joinToString(", ") { it.first.nombre }, actividad.motivo(fallidas.first().second)),
				)
				ofrecerUsar(voces.filter { v -> fallidas.none { it.first == v } })
			} catch (e: CancellationException) {
				dialogo.dismiss()
				actividad.avisar(getString(R.string.descarga_cancelada))
				throw e
			} finally {
				refrescar()
			}
		}
	}

	/** Tras bajar las primeras voces de un idioma, ofrece elegir cuál usar para él. */
	private fun ofrecerUsar(bajadas: List<Voz>) {
		val c = context ?: return
		val ajustes = Ajustes(c)
		val idioma = bajadas.firstOrNull()?.idioma ?: return
		if (ajustes.vozPara(idioma, "") != null) return
		val candidatas = Almacen.instaladas(c).filter { it.idioma == idioma && !it.personaje }
		if (candidatas.isEmpty()) return
		c.elegirUno(getString(R.string.elegir_voz_para, Catalogo.nombreIdioma(c, idioma)), candidatas.map { "${it.nombre}, ${nombreIdioma(it)}" }, -1) { i ->
			usar(candidatas[i])
		}
	}

	// --- Importar -------------------------------------------------------------------------

	private fun importar(uri: Uri) {
		val actividad = activity as? AppCompatActivity ?: return
		val nombreArchivo = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.take(60) ?: "Voz"
		actividad.pedirTexto(getString(R.string.nombre_de_la_voz), getString(R.string.nombre), nombreArchivo) { nombre ->
			if (nombre.isEmpty()) return@pedirTexto
			val opciones = Idiomas.DEL_MODELO.flatMap { i ->
				val regiones = Catalogo.voces(actividad).filter { it.idioma == i && !it.personaje }.map { it.region }.distinct()
				regiones.map { i to it }
			}
			actividad.elegirUno(getString(R.string.idioma_de_la_voz), opciones.map { Catalogo.nombreIdioma(actividad, it.first, it.second) }, 0) { i ->
				val (idioma, region) = opciones[i]
				actividad.lifecycleScope.launch {
					try {
						val voz = actividad.conEspera(getString(R.string.importar_voz), getString(R.string.comprobando_voz)) {
							withContext(Dispatchers.IO) {
								val datos = actividad.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("sin datos")
								if (datos.size > Almacen.MAX_VOZ) throw IllegalArgumentException(getString(R.string.voz_demasiado_grande))
								val temporal = File(actividad.cacheDir, "voz_importada.safetensors").apply { writeBytes(datos) }
								val problema = Motor.validarVoz(actividad, temporal)
								temporal.delete()
								if (problema != null) throw IllegalArgumentException(getString(R.string.voz_no_valida, problema))
								Almacen.importar(actividad, datos, nombre, idioma, region, "")
							}
						}
						actividad.avisar(getString(R.string.voz_importada, voz.nombre))
						refrescar()
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						Log.e("Fonaria.Voces", "No se pudo importar la voz", e)
						actividad.mensaje(getString(R.string.importar_voz), actividad.motivo(e))
					}
				}
			}
		}
	}

	private fun renombrar(v: Voz) {
		requireContext().pedirTexto(getString(R.string.renombrar_x, v.nombre), getString(R.string.nombre), v.nombre) { nuevo ->
			if (nuevo.isEmpty()) return@pedirTexto
			Almacen.editarImportada(requireContext(), v, nuevo, v.idioma, v.region)
			refrescar()
		}
	}

	private fun cambiarIdioma(v: Voz) {
		val c = requireContext()
		val opciones = Idiomas.DEL_MODELO.flatMap { i -> Catalogo.voces(c).filter { it.idioma == i && !it.personaje }.map { i to it.region }.distinct() }
		c.elegirUno(getString(R.string.idioma_de_la_voz), opciones.map { Catalogo.nombreIdioma(c, it.first, it.second) }, opciones.indexOf(v.idioma to v.region)) { i ->
			Almacen.editarImportada(c, v, v.nombre, opciones[i].first, opciones[i].second)
			refrescar()
		}
	}

	override fun onDestroyView() {
		locutor?.cerrar()
		locutor = null
		_b = null
		super.onDestroyView()
	}

	companion object {
		private const val ARG_MODO = "modo"
		fun nuevo(modo: Int) = ListaVocesFragment().apply { arguments = Bundle().apply { putInt(ARG_MODO, modo) } }
	}
}
