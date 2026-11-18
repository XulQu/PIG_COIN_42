package app.planetaryhours;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;
import com.nothing.ketchum.GlyphToy;

/**
 * Glyph Toy for the Nothing Phone (3) Glyph Matrix. Shows the symbol of the planet ruling the
 * current planetary hour. A long press on the Glyph Button switches to the minutes left in the
 * hour and back. Supports the always-on Glyph Toy mode.
 */
public class PlanetaryToyService extends Service {
    private GlyphMatrixManager manager;
    private boolean connected;
    private boolean showMinutes;
    // True once the system has sent an always-on event to this instance
    private boolean alwaysOn;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg);
                return;
            }
            Bundle data = msg.getData();
            String event = data == null ? null : data.getString(GlyphToy.MSG_GLYPH_TOY_DATA);
            if (GlyphToy.EVENT_CHANGE.equals(event)) {
                showMinutes = !showMinutes;
                render();
            } else if (GlyphToy.EVENT_AOD.equals(event)) {
                // Sent once a minute while this is the always-on toy
                alwaysOn = true;
                Store.log(PlanetaryToyService.this, "aod", "event received");
                render();
            }
        }
    };
    private final Messenger messenger = new Messenger(handler);
    private final Runnable refresh = this::render;

    @Override
    public IBinder onBind(Intent intent) {
        Store.log(this, "bind", "toy selected");
        manager = GlyphMatrixManager.getInstance(getApplicationContext());
        manager.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName name) {
                if (manager == null) return;
                manager.register(Glyph.DEVICE_23112);
                connected = true;
                try {
                    // Keep the matrix lit instead of letting it time out after a few minutes
                    manager.setGlyphMatrixTimeout(false);
                } catch (Exception e) {
                    Store.log(PlanetaryToyService.this, "frame", "timeout: " + e);
                }
                render();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                connected = false;
            }
        });
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(refresh);
        connected = false;
        if (manager != null) {
            try {
                // In always-on mode the last frame must stay on the matrix after we unbind
                if (!alwaysOn) manager.turnOff();
                manager.unInit();
            } catch (RuntimeException e) {
                // The Glyph service has already gone away
            }
            manager = null;
        }
        return false;
    }

    private void render() {
        handler.removeCallbacks(refresh);
        if (!connected || manager == null) return;

        long now = System.currentTimeMillis();
        PlanetaryTime.Hour hour = Store.currentHour(this);
        boolean[] dots;
        long nextRefresh;
        if (hour == null) {
            // No saved location yet (open the app once), or polar day/night
            dots = Glyphs.question();
            nextRefresh = 60000;
        } else if (showMinutes) {
            dots = Glyphs.number((int) Math.ceil((hour.end - now) / 60000.0));
            nextRefresh = Math.min(60000 - now % 60000, hour.end - now) + 500;
        } else {
            dots = Glyphs.planet(hour.planet);
            nextRefresh = hour.end - now + 500;
        }

        int[] pixels = new int[Glyphs.SIZE * Glyphs.SIZE];
        for (int i = 0; i < pixels.length; i++) pixels[i] = dots[i] ? 0xFFFFFFFF : 0xFF000000;
        Bitmap bitmap = Bitmap.createBitmap(
                pixels, Glyphs.SIZE, Glyphs.SIZE, Bitmap.Config.ARGB_8888);
        try {
            GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    .setPosition(0, 0)
                    .setScale(100)
                    .setBrightness(255)
                    .build();
            GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder().addTop(object).build(this);
            manager.setMatrixFrame(frame.render());
            Store.log(this, "frame", "sent" + (alwaysOn ? " (always-on)" : ""));
        } catch (Exception e) {
            // Not a Glyph Matrix device, or the Glyph service refused the frame
            Store.log(this, "frame", "failed: " + e);
        }
        handler.postDelayed(refresh, Math.max(1000, nextRefresh));
    }
}
