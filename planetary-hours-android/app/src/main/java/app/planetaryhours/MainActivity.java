package app.planetaryhours;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Shows the current planetary hour for the phone's location and the day's 24 hours. */
public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;

    private MatrixView matrix;
    private TextView title;
    private TextView subtitle;
    private TextView schedule;
    private Switch notifySwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);

        matrix = new MatrixView(this);
        root.addView(matrix, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        title = text(28, 0xFFFFFFFF);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setPadding(0, pad, 0, dp(4));
        root.addView(title);

        subtitle = text(15, 0xFFB0B0B0);
        subtitle.setPadding(0, 0, 0, pad);
        root.addView(subtitle);

        notifySwitch = new Switch(this);
        notifySwitch.setText("Notify me at each new planetary hour");
        notifySwitch.setTextSize(16);
        notifySwitch.setTextColor(0xFFFFFFFF);
        notifySwitch.setChecked(Store.notificationsEnabled(this));
        notifySwitch.setOnCheckedChangeListener((button, on) -> setNotifications(on));
        root.addView(notifySwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button toyButton = new Button(this);
        toyButton.setText("Add to Glyph Matrix");
        toyButton.setOnClickListener(v -> openGlyphToys());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = dp(12);
        root.addView(toyButton, buttonParams);

        Button locationButton = new Button(this);
        locationButton.setText("Update my location");
        locationButton.setOnClickListener(v -> requestLocation());
        root.addView(locationButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        schedule = text(14, 0xFFB0B0B0);
        schedule.setTypeface(Typeface.MONOSPACE);
        schedule.setGravity(Gravity.START);
        schedule.setLineSpacing(dp(4), 1f);
        schedule.setPadding(0, pad, 0, 0);
        root.addView(schedule);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF000000);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);

        requestLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private TextView text(int sp, int colour) {
        TextView view = new TextView(this);
        view.setTextSize(sp);
        view.setTextColor(colour);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ----- Display -----

    private void refresh() {
        handler.removeCallbacks(tick);
        if (!Store.hasLocation(this)) {
            matrix.setDots(Glyphs.question());
            title.setText("Location needed");
            subtitle.setText("Planetary hours depend on your sunrise and sunset.\n"
                    + "Allow location, then tap Update my location.");
            schedule.setText("");
            return;
        }

        long now = System.currentTimeMillis();
        double lat = Store.latitude(this);
        double lon = Store.longitude(this);
        PlanetaryTime.Hour[] hours = PlanetaryTime.day(now, lat, lon);
        if (hours == null) {
            matrix.setDots(Glyphs.question());
            title.setText("No planetary hours");
            subtitle.setText("The sun does not rise or set at your location today.");
            schedule.setText("");
            handler.postDelayed(tick, 3600000);
            return;
        }
        PlanetaryTime.Hour hour = PlanetaryTime.current(now, lat, lon);
        java.text.DateFormat time = DateFormat.getTimeFormat(this);

        matrix.setDots(Glyphs.planet(hour.planet));
        title.setText(PlanetaryTime.symbol(hour.planet) + " Hour of " + hour.planet);
        subtitle.setText("Hour " + hour.hourOfPeriod + " of the " + (hour.isDay ? "day" : "night")
                + " · until " + time.format(hour.end)
                + "\nDay of " + hour.dayRuler
                + String.format(Locale.US, " · %.2f, %.2f", lat, lon));

        StringBuilder list = new StringBuilder();
        for (PlanetaryTime.Hour h : hours) {
            if (h.hourNumber == 1) list.append("DAY\n");
            if (h.hourNumber == 13) list.append("\nNIGHT\n");
            list.append(h == hour || h.start == hour.start ? "▶ " : "  ")
                    .append(String.format(Locale.US, "%2d  ", h.hourOfPeriod))
                    .append(PlanetaryTime.symbol(h.planet)).append(' ')
                    .append(String.format(Locale.US, "%-8s", h.planet))
                    .append(time.format(h.start)).append('\n');
        }
        list.append("\nGLYPH TOY STATUS\n")
                .append("Selected:  ").append(Store.logLine(this, "bind")).append('\n')
                .append("Always-on: ").append(Store.logLine(this, "aod")).append('\n')
                .append("Frame:     ").append(Store.logLine(this, "frame")).append('\n');
        schedule.setText(list);

        handler.postDelayed(tick, Math.min(30000, hour.end - now + 500));
    }

    // ----- Location -----

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        LocationManager locations = getSystemService(LocationManager.class);
        Location best = null;
        String provider = null;
        try {
            for (String name : locations.getProviders(true)) {
                if (provider == null || LocationManager.NETWORK_PROVIDER.equals(name)) {
                    provider = name;
                }
                Location last = locations.getLastKnownLocation(name);
                if (last != null && (best == null || last.getTime() > best.getTime())) {
                    best = last;
                }
            }
            if (best != null) {
                onLocation(best);
            } else if (provider != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locations.getCurrentLocation(provider, null, getMainExecutor(), this::onLocation);
            } else if (provider != null) {
                locations.requestSingleUpdate(provider, this::onLocation, Looper.getMainLooper());
            } else {
                Toast.makeText(this, "Turn on location to find your planetary hour",
                        Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            // Permission was revoked while we were asking
        }
    }

    private void onLocation(Location location) {
        if (location == null) {
            Toast.makeText(this, "Could not get your location, try again",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Store.saveLocation(this, location.getLatitude(), location.getLongitude());
        HourNotifier.schedule(this);
        refresh();
    }

    // ----- Notifications -----

    private void setNotifications(boolean on) {
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        Store.setNotificationsEnabled(this, on);
        if (on) {
            HourNotifier.announce(this);
            HourNotifier.schedule(this);
        } else {
            HourNotifier.cancel(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (request == REQUEST_LOCATION) {
            if (granted) requestLocation();
            else refresh();
        } else if (request == REQUEST_NOTIFICATIONS) {
            if (granted) {
                setNotifications(true);
            } else {
                notifySwitch.setChecked(false);
                Toast.makeText(this, "Notifications are turned off for this app",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ----- Glyph Matrix -----

    /** Opens Nothing's Glyph Toys manager so the toy can be added to the Glyph Button carousel. */
    private void openGlyphToys() {
        try {
            startActivity(new Intent().setComponent(new ComponentName(
                    "com.nothing.thirdparty",
                    "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")));
        } catch (RuntimeException e) {
            Toast.makeText(this, "The Glyph Matrix needs a Nothing Phone (3). On one, open "
                    + "Settings > Glyph Interface > Glyph Toys.", Toast.LENGTH_LONG).show();
        }
    }
}
