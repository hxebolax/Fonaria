package com.xebolax.fonaria.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.Accion
import com.xebolax.fonaria.accesibilidad.accionAlMantener
import com.xebolax.fonaria.accesibilidad.accionesAccesibles
import com.xebolax.fonaria.accesibilidad.elegir
import com.xebolax.fonaria.accesibilidad.etiquetaPulsar
import com.xebolax.fonaria.databinding.FilaBinding

/**
 * Una fila de lista de una sola parada: TalkBack lee [titulo] y [detalle] juntos, y las
 * [acciones] están en su menú de acciones. Un toque abre la lista de todas las acciones
 * (la primera es la principal); mantener pulsado, también.
 */
data class Fila(
	/** Identificador estable: con él, el foco no se pierde al refrescar la lista. */
	val id: String,
	val titulo: String,
	val detalle: String,
	val acciones: List<Accion>,
	/** Lo que dice el doble toque («Escuchar», «Descargar»...). Sin él, abre el menú de acciones. */
	val principal: Accion? = null,
)

class AdaptadorFilas : RecyclerView.Adapter<AdaptadorFilas.Hueco>() {
	var filas: List<Fila> = emptyList()
		set(v) {
			field = v
			@Suppress("NotifyDataSetChanged")
			notifyDataSetChanged()
		}

	init {
		setHasStableIds(true)
	}

	override fun getItemId(posicion: Int) = filas[posicion].id.hashCode().toLong()
	override fun getItemCount() = filas.size

	override fun onCreateViewHolder(padre: ViewGroup, tipo: Int) =
		Hueco(FilaBinding.inflate(LayoutInflater.from(padre.context), padre, false))

	override fun onBindViewHolder(h: Hueco, posicion: Int) {
		val f = filas[posicion]
		h.b.titulo.text = f.titulo
		h.b.detalle.text = f.detalle
		h.b.detalle.visibility = if (f.detalle.isEmpty()) View.GONE else View.VISIBLE
		h.b.root.contentDescription = if (f.detalle.isEmpty()) f.titulo else "${f.titulo}. ${f.detalle}"
		h.b.root.accionesAccesibles(f.acciones)
		val menu = { h.b.root.context.elegir(f.titulo, f.acciones) }
		val principal = f.principal
		if (principal != null) {
			h.b.root.setOnClickListener { principal.second() }
			h.b.root.etiquetaPulsar(principal.first)
		} else {
			h.b.root.setOnClickListener { menu() }
		}
		h.b.root.accionAlMantener(h.b.root.context.getString(R.string.todas_las_acciones)) { menu() }
	}

	class Hueco(val b: FilaBinding) : RecyclerView.ViewHolder(b.root)
}
