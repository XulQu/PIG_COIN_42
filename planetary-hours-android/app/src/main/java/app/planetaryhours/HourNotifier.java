package app.planetaryhours;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateFormat;

/**
 * Posts a notification when a new planetary hour begins. An alarm is set for the end of the
 * current hour; when it fires the new hour is announced and the next alarm is set.
 */
public class HourNotifier extends BroadcastReceiver {
    static final String ACTION_HOUR = "app.planetaryhours.HOUR_CHANGED";
    private static final String CHANNEL = "planetary_hours";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        Store.enableGlyphDebug(context);
        if (!Store.notificationsEnabled(context)) return;
        if (ACTION_HOUR.equals(intent.getAction())) announce(context);
        schedule(context);
    }

    private static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(context, HourNotifier.class).setAction(ACTION_HOUR);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets the alarm for the start of the next planetary hour. */
    static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = alarmIntent(context);
        alarms.cancel(pending);
        if (!Store.notificationsEnabled(context)) return;

        PlanetaryTime.Hour hour = Store.currentHour(context);
        // No location yet, or polar day/night: look again in an hour
        long at = hour != null ? hour.end + 1000 : System.currentTimeMillis() + 3600000;
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || alarms.canScheduleExactAlarms();
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
    }

    static void cancel(Context context) {
        context.getSystemService(AlarmManager.class).cancel(alarmIntent(context));
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    /** Shows the current planetary hour as a notification. */
    static void announce(Context context) {
        PlanetaryTime.Hour hour = Store.currentHour(context);
        if (hour == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Planetary hours", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("A notification each time a new planetary hour begins");
        manager.createNotificationChannel(channel);

        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String until = DateFormat.getTimeFormat(context).format(hour.end);
        String text = "Hour " + hour.hourOfPeriod + " of the " + (hour.isDay ? "day" : "night")
                + ", until " + until + ". Day of " + hour.dayRuler
                + ". Next: " + hour.nextPlanet + ".";
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(
                        PlanetaryTime.symbol(hour.planet) + " Hour of " + hour.planet)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setShowWhen(true)
                .setWhen(hour.start)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
