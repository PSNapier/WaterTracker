package com.example.watertracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaterWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        ensureDailyResetIfNeeded(context)
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_DROP_TAP) {
            val index = intent.getIntExtra(EXTRA_DROP_INDEX, -1)
            if (index in 0 until DROP_COUNT) {
                toggleDropState(context, index)
                refreshAllWidgets(context)
            }
        }
    }

    private fun refreshAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, WaterWidget::class.java)
        val ids = manager.getAppWidgetIds(component)
        onUpdate(context, manager, ids)
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.water_widget)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val imageIds = intArrayOf(
            R.id.drop0, R.id.drop1, R.id.drop2, R.id.drop3,
            R.id.drop4, R.id.drop5, R.id.drop6, R.id.drop7
        )

        imageIds.forEachIndexed { index, imageViewId ->
            val isFilled = prefs.getBoolean(dropKey(index), false)
            views.setImageViewResource(
                imageViewId,
                if (isFilled) R.drawable.ic_drop_filled else R.drawable.ic_drop_empty
            )
            views.setOnClickPendingIntent(imageViewId, createTapPendingIntent(context, index))
        }

        return views
    }

    private fun createTapPendingIntent(context: Context, index: Int): PendingIntent {
        val tapIntent = Intent(context, WaterWidget::class.java).apply {
            action = ACTION_DROP_TAP
            putExtra(EXTRA_DROP_INDEX, index)
            // Distinct data URI ensures unique PendingIntents across launchers.
            data = Uri.parse("watertracker://drop/$index")
        }

        return PendingIntent.getBroadcast(
            context,
            index,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun toggleDropState(context: Context, index: Int) {
        ensureDailyResetIfNeeded(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = dropKey(index)
        val current = prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, !current).apply()
    }

    private fun ensureDailyResetIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dateFormatter.format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, null)

        if (today != lastDate) {
            val editor = prefs.edit()
            for (i in 0 until DROP_COUNT) {
                editor.putBoolean(dropKey(i), false)
            }
            editor.putString(KEY_LAST_DATE, today)
            editor.apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "water_widget_prefs"
        private const val KEY_LAST_DATE = "last_date"
        private const val DROP_COUNT = 8
        private const val EXTRA_DROP_INDEX = "index"
        private const val ACTION_DROP_TAP = "com.example.watertracker.ACTION_DROP_TAP"
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        private fun dropKey(index: Int): String = "drop_$index"
    }
}
