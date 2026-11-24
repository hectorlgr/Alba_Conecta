package com.albaconecta.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de red compartido por toda la app: una sola suscripción al callback del sistema.
 *
 * Se usa NET_CAPABILITY_INTERNET y no VALIDATED: con `adb reverse` el backend está en
 * 127.0.0.1, así que exigir internet "validado" marcaría offline una app que sí funciona.
 */
object Conectividad {
    private val _enLinea = MutableStateFlow(true) // optimista hasta la primera lectura real
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    @Volatile
    private var iniciado = false

    fun iniciar(context: Context) {
        if (iniciado) return
        synchronized(this) {
            if (iniciado) return@synchronized
            iniciado = true

            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            _enLinea.value = manager.activoConInternet()
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _enLinea.value = manager.tieneInternet(network)
                }

                override fun onLost(network: Network) {
                    _enLinea.value = false
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _enLinea.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        }
    }

    private fun ConnectivityManager.activoConInternet(): Boolean =
        activeNetwork?.let { tieneInternet(it) } ?: false

    private fun ConnectivityManager.tieneInternet(network: Network): Boolean =
        getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

/** Texto del aviso global; null si no hay nada que avisar. */
fun avisoRed(enLinea: Boolean, pendientes: Int): String? = when {
    !enLinea && pendientes == 1 -> "Sin conexión · 1 solicitud por enviar"
    !enLinea && pendientes > 1 -> "Sin conexión · $pendientes solicitudes por enviar"
    !enLinea -> "Sin conexión · mostrando datos guardados"
    pendientes == 1 -> "1 solicitud pendiente de envío"
    pendientes > 1 -> "$pendientes solicitudes pendientes de envío"
    else -> null
}
