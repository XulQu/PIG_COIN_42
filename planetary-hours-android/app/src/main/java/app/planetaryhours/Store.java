package app.planetaryhours;

import android.content.Context;
import android.content.SharedPreferences;

/** Saved location and settings, shared by the app, the notifications and the Glyph Toy. */
final class Store {
    private static final String FILE = "planetary_hours";

    private Store() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean hasLocation(Context context) {
        return prefs(context).contains("lat");
    }

    static double latitude(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong("lat", 0));
    }

    static double longitude(Context context) {
        return Double.longBitsToDouble(prefs(context).getLong("lon", 0));
    }

    static void saveLocation(Context context, double lat, double lon) {
        prefs(context).edit()
                .putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lon", Double.doubleToLongBits(lon))
                .apply();
    }

    static boolean notificationsEnabled(Context context) {
        return prefs(context).getBoolean("notify", false);
    }

    static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("notify", enabled).apply();
    }

    /** The current planetary hour for the saved location, or null if unknown. */
    static PlanetaryTime.Hour currentHour(Context context) {
        if (!hasLocation(context)) return null;
        return PlanetaryTime.current(
                System.currentTimeMillis(), latitude(context), longitude(context));
    }
}
