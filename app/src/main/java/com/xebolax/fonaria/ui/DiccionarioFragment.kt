package com.xebolax.fonaria.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.Accion
import com.xebolax.fonaria.accesibilidad.avisar
import com.xebolax.fonaria.accesibilidad.campoTexto
import com.xebolax.fonaria.accesibilidad.confirmar
import com.xebolax.fonaria.accesibilidad.conDeshacer
import com.xebolax.fonaria.accesibilidad.dp
import com.xebolax.fonaria.accesibilidad.elegirUno
import com.xebolax.fonaria.accesibilidad.mensaje
import com.xebolax.fonaria.accesibilidad.sinFocoPropio
import com.xebolax.fonaria.databinding.ListaBinding
import com.xebolax.fonaria.datos.motivo
import com.xebolax.fonaria.motor.Idiomas
import com.xebolax.fonaria.texto.Diccionario
import com.xebolax.fonaria.texto.Entrada
import com.xebolax.fonaria.texto.TipoEntrada
import com.xebolax.fonaria.voces.Catalogo
import java.util.Locale

/**
 * El diccionario de pronunciación: palabras que se dicen mal y cómo deben sonar. Importa y
 * exporta el formato de NVDA (.dic) y una copia completa de Fonaria (.json).
 */
class DiccionarioFragment : Fragment(), MenuProvider {
	private var _b: ListaBinding? = null
	private val b get() = _b!!
	private val adaptador = AdaptadorFilas()
	private var locutor: Locutor? = null

	private val importar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { leer(it) } }
	private val exportarNvda = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { escribir(it, nvda = true) } }
	private val exportarJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { escribir(it, nvda = false) } }

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = ListaBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
		locutor = Locutor(requireContext())
		b.lista.layoutManager = LinearLayoutManager(requireContext())
		b.lista.adapter = adaptador
		b.lista.sinFocoPropio()
		b.accion.visibility = View.VISIBLE
		b.accion.text = getString(R.string.anadir_entrada)
		b.accion.setOnClickListener { editar(null) }
		refrescar()
	}

	override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
		menu.add(0, 1, 0, R.string.importar_diccionario)
		menu.add(0, 2, 0, R.string.exportar_nvda)
		menu.add(0, 3, 0, R.string.exportar_fonaria)
		menu.add(0, 4, 0, R.string.borrar_todo_diccionario)
	}

	override fun onMenuItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			1 -> importar.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*"))
			2 -> exportarNvda.launch("fonaria.dic")
			3 -> exportarJson.launch("diccionario-fonaria.json")
			4 -> borrarTodo()
			else -> return false
		}
		return true
	}

	private fun refrescar() {
		val c = context ?: return
		if (_b == null) return
		val entradas = Diccionario.entradas(c)
		adaptador.filas = entradas.map { e -> fila(e, entradas) }
		b.estado.text = if (entradas.isEmpty()) getString(R.string.diccionario_vacio)
		else resources.getQuantityString(R.plurals.entradas, entradas.size, entradas.size)
	}

	private fun fila(e: Entrada, todas: List<Entrada>): Fila {
		val c = requireContext()
		val detalle = buildList {
			if (!e.activa) add(getString(R.string.desactivada))
			add(getString(R.string.se_dice, e.reemplazo.ifEmpty { getString(R.string.nada) }))
			add(getString(nombreTipo(e.tipo)))
			if (e.mayusculas) add(getString(R.string.distingue_mayusculas))
			add(if (e.idioma.isEmpty()) getString(R.string.todos_los_idiomas) else Catalogo.nombreIdioma(c, e.idioma))
			if (e.comentario.isNotEmpty()) add(e.comentario)
		}.joinToString(". ")
		val acciones = listOf<Accion>(
			getString(R.string.editar_x, e.patron) to { editar(e) },
			getString(R.string.escuchar) to { escuchar(e.reemplazo, e.idioma) },
			getString(if (e.activa) R.string.desactivar else R.string.activar) to {
				guardar(todas.map { if (it.id == e.id) it.copy(activa = !it.activa) else it })
			},
			getString(R.string.borrar_x, e.patron) to {
				guardar(todas.filter { it.id != e.id })
				(activity as? AppCompatActivity)?.conDeshacer(getString(R.string.entrada_borrada, e.patron)) { guardar(todas) }
			},
		)
		return Fila(e.id, e.patron, detalle, acciones, acciones.first())
	}

	private fun nombreTipo(t: TipoEntrada) = when (t) {
		TipoEntrada.PALABRA -> R.string.tipo_palabra
		TipoEntrada.TEXTO -> R.string.tipo_texto
		TipoEntrada.REGEX -> R.string.tipo_regex
	}

	private fun guardar(entradas: List<Entrada>) {
		Diccionario.guardar(requireContext(), entradas)
		refrescar()
	}

	private fun escuchar(texto: String, idioma: String) {
		val i = idioma.ifEmpty { "es" }
		val voz = Idiomas.elegirVoz(requireContext(), i, "") ?: return
		locutor?.decir(texto.ifEmpty { " " }, Locale(i), voz.id)
	}

	/** Diálogo para crear o cambiar una entrada. */
	private fun editar(original: Entrada?) {
		val c = requireContext()
		val (cajaPatron, patron) = c.campoTexto(getString(R.string.patron), original?.patron.orEmpty())
		val (cajaReemplazo, reemplazo) = c.campoTexto(getString(R.string.reemplazo), original?.reemplazo.orEmpty())
		val (cajaComentario, comentario) = c.campoTexto(getString(R.string.comentario), original?.comentario.orEmpty())
		val tipos = RadioGroup(c).apply { orientation = RadioGroup.VERTICAL }
		val botonesTipo = TipoEntrada.entries.map { t ->
			RadioButton(c).apply {
				id = View.generateViewId()
				text = getString(nombreTipo(t))
				minHeight = dp(c, 48)
				isChecked = (original?.tipo ?: TipoEntrada.PALABRA) == t
			}.also { tipos.addView(it) }
		}
		val mayusculas = CheckBox(c).apply {
			text = getString(R.string.distinguir_mayusculas)
			minHeight = dp(c, 48)
			isChecked = original?.mayusculas ?: false
		}
		val activa = CheckBox(c).apply {
			text = getString(R.string.entrada_activa)
			minHeight = dp(c, 48)
			isChecked = original?.activa ?: true
		}
		val idiomas = listOf("") + Idiomas.DEL_MODELO
		var idioma = original?.idioma.orEmpty()
		fun nombreIdioma(i: String) = if (i.isEmpty()) getString(R.string.todos_los_idiomas) else Catalogo.nombreIdioma(c, i)
		val botonIdioma = MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			text = getString(R.string.idioma_x, nombreIdioma(idioma))
			minHeight = dp(c, 48)
			setOnClickListener {
				c.elegirUno(getString(R.string.idioma), idiomas.map(::nombreIdioma), idiomas.indexOf(idioma)) { i ->
					idioma = idiomas[i]
					text = getString(R.string.idioma_x, nombreIdioma(idioma))
				}
			}
		}
		val probar = MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			text = getString(R.string.escuchar_reemplazo)
			minHeight = dp(c, 48)
			setOnClickListener { escuchar(reemplazo.text.toString(), idioma) }
		}
		val m = dp(c, 20)
		val contenido = LinearLayout(c).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(m, m / 2, m, 0)
			addView(cajaPatron)
			addView(cajaReemplazo)
			addView(probar)
			addView(tipos)
			addView(mayusculas)
			addView(botonIdioma)
			addView(activa)
			addView(cajaComentario)
		}
		val dialogo = MaterialAlertDialogBuilder(c)
			.setTitle(if (original == null) R.string.anadir_entrada else R.string.editar_entrada)
			.setView(ScrollView(c).apply { addView(contenido) })
			.setPositiveButton(R.string.guardar, null)
			.setNegativeButton(R.string.cancelar, null)
			.show()
		dialogo.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
			val tipo = TipoEntrada.entries[botonesTipo.indexOfFirst { it.isChecked }.coerceAtLeast(0)]
			val nueva = (original ?: Entrada(patron = "", reemplazo = "")).copy(
				patron = patron.text.toString(),
				reemplazo = reemplazo.text.toString(),
				tipo = tipo,
				mayusculas = mayusculas.isChecked,
				idioma = idioma,
				activa = activa.isChecked,
				comentario = comentario.text.toString().trim(),
			)
			cajaPatron.error = when {
				nueva.patron.isEmpty() -> getString(R.string.error_patron_vacio)
				nueva.patronCompilado() == null -> getString(R.string.error_regex)
				else -> null
			}
			if (cajaPatron.error != null) {
				patron.requestFocus()
				return@setOnClickListener
			}
			val todas = Diccionario.entradas(c)
			guardar(if (original == null) todas + nueva else todas.map { if (it.id == nueva.id) nueva else it })
			dialogo.dismiss()
			(activity as? AppCompatActivity)?.avisar(getString(R.string.entrada_guardada, nueva.patron))
		}
	}

	private fun leer(uri: Uri) {
		val c = requireContext()
		try {
			val texto = c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
			val nuevas = Diccionario.leer(texto)
			if (nuevas.isEmpty()) {
				c.mensaje(getString(R.string.importar_diccionario), getString(R.string.diccionario_sin_entradas))
				return
			}
			val actuales = Diccionario.entradas(c)
			val sinRepetir = nuevas.filter { n -> actuales.none { it.patron == n.patron && it.tipo == n.tipo && it.idioma == n.idioma } }
			guardar(actuales + sinRepetir)
			c.mensaje(getString(R.string.importar_diccionario), getString(R.string.diccionario_importado, sinRepetir.size, nuevas.size - sinRepetir.size))
		} catch (e: Exception) {
			Log.e("Fonaria.Diccionario", "No se pudo importar", e)
			c.mensaje(getString(R.string.importar_diccionario), c.motivo(e))
		}
	}

	private fun escribir(uri: Uri, nvda: Boolean) {
		val c = requireContext()
		try {
			val entradas = Diccionario.entradas(c)
			val texto = if (nvda) Diccionario.exportarNvda(entradas) else Diccionario.exportarJson(entradas)
			c.contentResolver.openOutputStream(uri)?.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
			(activity as? AppCompatActivity)?.avisar(resources.getQuantityString(R.plurals.entradas_exportadas, entradas.size, entradas.size))
		} catch (e: Exception) {
			Log.e("Fonaria.Diccionario", "No se pudo exportar", e)
			c.mensaje(getString(R.string.exportar_fonaria), c.motivo(e))
		}
	}

	private fun borrarTodo() {
		val c = requireContext()
		val todas = Diccionario.entradas(c)
		if (todas.isEmpty()) return
		c.confirmar(getString(R.string.confirmar_borrar_diccionario, todas.size), getString(R.string.borrar_todo)) {
			guardar(emptyList())
			(activity as? AppCompatActivity)?.conDeshacer(getString(R.string.diccionario_borrado)) { guardar(todas) }
		}
	}

	override fun onDestroyView() {
		locutor?.cerrar()
		locutor = null
		_b = null
		super.onDestroyView()
	}
}
