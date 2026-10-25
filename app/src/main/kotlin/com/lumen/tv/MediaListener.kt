package com.lumen.tv

import android.service.notification.NotificationListenerService

/** Leerer Listener: nur nötig, damit Lumen laufende Medien-Sitzungen lesen darf (Reihe "Läuft gerade"). */
class MediaListener : NotificationListenerService()
