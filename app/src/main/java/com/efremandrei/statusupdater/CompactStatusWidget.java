package com.efremandrei.statusupdater;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** A separate one-cell entry in the launcher widget picker, sharing the active session. */
public final class CompactStatusWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        StatusWidget.updateWidgets(context, manager, ids, true);
    }
}
