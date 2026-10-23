package com.albaconecta.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object LocalNotificationHelper {
    private const val CHANNEL_ID = "alba_notices"
    private const val REGISTRATION_NOTIFICATION_ID = 1

    fun showRegistrationSuccess(context: Context): Boolean {
        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Avisos de Alba Conecta",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            ) {
                return false
            }

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("¡Registro completado!")
                .setContentText("Tu cuenta fue creada correctamente. Gracias por unirte a Alba Conecta.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Tu cuenta fue creada correctamente. Gracias por unirte a Alba Conecta."
                ))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(REGISTRATION_NOTIFICATION_ID, notification)
            return true
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and publication.
            return false
        }
    }
}
