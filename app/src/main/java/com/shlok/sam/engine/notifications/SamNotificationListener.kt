package com.shlok.sam.engine.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shlok.sam.data.db.NotificationRecordEntity
import com.shlok.sam.data.db.SamDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SamNotificationListener : NotificationListenerService() {

    @Inject lateinit var db: SamDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        scope.launch {
            db.notifications().insert(
                NotificationRecordEntity(
                    packageName = sbn.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime
                )
            )
        }
    }
}
