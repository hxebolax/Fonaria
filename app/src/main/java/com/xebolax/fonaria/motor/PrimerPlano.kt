package com.xebolax.fonaria.motor

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xebolax.fonaria.R

/**
 * Mantiene el motor como servicio en primer plano mientras se usa.
 *
 * Por qué: si no, Android trata el motor como un proceso en segundo plano y, sobre todo con la
 * pantalla apagada o dentro de otra app, lo deja en los núcleos lentos del procesador. Entonces
 * genera la voz más despacio de lo que suena y se oyen cortes (en otros proyectos con estas
 * voces se midió 0,9 veces el tiempo real así, frente a 3 veces en primer plano).
 *
 * Se para solo tras dos minutos sin hablar, para no gastar nada cuando no se usa. Si Android no
 * deja ponerlo (puede pasar mientras la app no está en pantalla), se vuelve a pedir en el
 * siguiente uso y la voz sigue funcionando, solo que más despacio.
 */
class PrimerPlano : Service() {
	private val manejador = Handler(Looper.getMainLooper())
	private val revisar = object : Runnable {
		override fun run() {
			if (SystemClock.elapsedRealtime() - ultimoUso > INACTIVO_MS) stopSelf()
			else manejador.postDelayed(this, REVISAR_MS)
		}
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		// En cada arranque, no solo el primero: Android lo espera tras cada startForegroundService
		try {
			val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
			ServiceCompat.startForeground(this, ID, aviso(), tipo)
			activo = true
			manejador.removeCallbacks(revisar)
			manejador.postDelayed(revisar, REVISAR_MS)
		} catch (e: Exception) {
			Log.w(ETIQUETA, "No se pudo pasar a primer plano", e)
			activo = false
			stopSelf()
		}
		// Nunca lo rearranca el sistema por su cuenta: desde segundo plano se lo negaría
		return START_NOT_STICKY
	}

	override fun onDestroy() {
		manejador.removeCallbacks(revisar)
		activo = false
		super.onDestroy()
	}

	/**
	 * Aviso discreto (sin sonido ni vibración). Fonaria no pide permiso de notificaciones, así que
	 * desde Android 13 no se ve; en versiones anteriores aparece mientras el motor está en uso.
	 */
	private fun aviso(): Notification {
		NotificationManagerCompat.from(this).createNotificationChannel(
			NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_MIN)
				.setName(getString(R.string.canal_motor))
				.setShowBadge(false)
				.build(),
		)
		return NotificationCompat.Builder(this, CANAL)
			.setSmallIcon(R.drawable.ic_sonido)
			.setContentTitle(getString(R.string.app_name))
			.setContentText(getString(R.string.aviso_motor))
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setOngoing(true)
			.setSilent(true)
			.build()
	}

	companion object {
		private const val ETIQUETA = "Fonaria.PrimerPlano"
		private const val CANAL = "motor"
		private const val ID = 7
		private const val REVISAR_MS = 5_000L
		private const val INACTIVO_MS = 120_000L
		private const val REINTENTO_MS = 2_000L
		private const val REINTENTO_NEGADO_MS = 60_000L
		@Volatile
		private var espera = REINTENTO_MS

		@Volatile
		private var activo = false
		@Volatile
		private var ultimoUso = 0L
		@Volatile
		private var ultimaPeticion = 0L

		/** Si el motor está ahora mismo en primer plano. */
		val enPrimerPlano: Boolean get() = activo

		/** Se llama cada vez que se usa el motor. */
		fun mantener(contexto: Context) {
			val ahora = SystemClock.elapsedRealtime()
			ultimoUso = ahora
			if (activo || ahora - ultimaPeticion < espera) return
			ultimaPeticion = ahora
			try {
				ContextCompat.startForegroundService(contexto, Intent(contexto, PrimerPlano::class.java))
				espera = REINTENTO_MS
			} catch (e: Exception) {
				// Android 12 y posteriores lo niegan desde segundo plano salvo que la app esté libre
				// de la optimización de batería (Ajustes, Motor): se reintenta más adelante
				if (espera == REINTENTO_MS) Log.w(ETIQUETA, "Android no deja pasar a primer plano ahora: ${e.message}")
				espera = REINTENTO_NEGADO_MS
			}
		}
	}
}
