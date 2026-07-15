package com.nofs.desk.media

import android.service.notification.NotificationListenerService

/**
 * Пустой слушатель уведомлений: сами уведомления не обрабатываем, сервис нужен
 * только чтобы быть включённым в системных настройках («Доступ к уведомлениям») —
 * это единственный способ для стороннего приложения получить у
 * MediaSessionManager список активных медиа-сессий устройства.
 */
class NofsNotificationListener : NotificationListenerService()
