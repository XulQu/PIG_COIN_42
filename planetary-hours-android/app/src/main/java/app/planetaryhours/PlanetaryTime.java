package app.planetaryhours;

/**
 * Classical planetary hours. Sunrise to sunset is split into 12 equal day hours and sunset to the
 * next sunrise into 12 night hours. Each hour is ruled by a planet in the Chaldean order, starting
 * at sunrise with the ruler of the weekday (Sunday = Sun, Monday = Moon, ...).
 *
 * <p>Latitude is degrees north, longitude is degrees east. Times are epoch milliseconds.
 */
final class PlanetaryTime {
    static final String[] CHALDEAN_ORDER = {
        "Saturn", "Jupiter", "Mars", "Sun", "Venus", "Mercury", "Moon"
    };
    // Index 0 = Sunday
    static final String[] DAY_RULERS = {
        "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn"
    };

    private static final double DAY_MS = 86400000.0;
    private static final double J1970 = 2440587.5;
    private static final double J2000 = 2451545.0;
    private static final double RAD = Math.PI / 180;

    /** One planetary hour. */
    static final class Hour {
        /** 1-24, counted from sunrise. */
        int hourNumber;
        /** 1-12 within the day or the night. */
        int hourOfPeriod;
        boolean isDay;
        String planet;
        String dayRuler;
        long start;
        long end;
        /** Ruler of the hour that follows; null if it cannot be computed. */
        String nextPlanet;
    }

    private PlanetaryTime() {}

    static String symbol(String planet) {
        switch (planet) {
            case "Sun": return "☉";
            case "Moon": return "☽";
            case "Mercury": return "☿";
            case "Venus": return "♀";
            case "Mars": return "♂";
            case "Jupiter": return "♃";
            default: return "♄";
        }
    }

    private static long solarDayIndex(long time, double lon) {
        return Math.round(time / DAY_MS + J1970 - J2000 - 0.0008 + lon / 360);
    }

    /** Returns {sunrise, sunset} for solar day n, or null in polar day or night. */
    private static long[] sunTimes(long n, double lat, double lon) {
        double jStar = n + 0.0008 - lon / 360;
        double m = (357.5291 + 0.98560028 * jStar) % 360;
        double c = 1.9148 * Math.sin(m * RAD)
                + 0.02 * Math.sin(2 * m * RAD)
                + 0.0003 * Math.sin(3 * m * RAD);
        double lambda = (m + c + 180 + 102.9372) % 360;
        double transit = J2000 + jStar
                + 0.0053 * Math.sin(m * RAD)
                - 0.0069 * Math.sin(2 * lambda * RAD);
        double sinDec = Math.sin(lambda * RAD) * Math.sin(23.4397 * RAD);
        double cosDec = Math.cos(Math.asin(sinDec));
        // -0.833 degrees: refraction plus the radius of the solar disc
        double cosOmega = (Math.sin(-0.833 * RAD) - Math.sin(lat * RAD) * sinDec)
                / (Math.cos(lat * RAD) * cosDec);
        if (cosOmega < -1 || cosOmega > 1) return null;
        double omega = Math.acos(cosOmega) / RAD;
        return new long[] {
            Math.round((transit - omega / 360 - J1970) * DAY_MS),
            Math.round((transit + omega / 360 - J1970) * DAY_MS),
        };
    }

    /**
     * All 24 hours of the planetary day (sunrise to next sunrise) containing {@code time}, or null
     * where the sun does not rise or set.
     */
    static Hour[] day(long time, double lat, double lon) {
        long n = solarDayIndex(time, lon);
        long[] today = sunTimes(n, lat, lon);
        if (today == null) return null;
        // Before today's sunrise we are still in yesterday's planetary day
        if (time < today[0]) {
            n -= 1;
            today = sunTimes(n, lat, lon);
            if (today == null) return null;
        }
        long[] tomorrow = sunTimes(n + 1, lat, lon);
        if (tomorrow == null) return null;

        // Weekday at the location, from local solar time at sunrise (1970-01-01 was a Thursday)
        long localDays = (long) Math.floor((today[0] + lon / 360 * DAY_MS) / DAY_MS);
        int weekday = (int) (((localDays + 4) % 7 + 7) % 7);
        String dayRuler = DAY_RULERS[weekday];
        int first = 0;
        while (!CHALDEAN_ORDER[first].equals(dayRuler)) first++;

        double dayLen = (today[1] - today[0]) / 12.0;
        double nightLen = (tomorrow[0] - today[1]) / 12.0;
        Hour[] hours = new Hour[24];
        for (int i = 0; i < 24; i++) {
            Hour h = new Hour();
            h.isDay = i < 12;
            h.hourNumber = i + 1;
            h.hourOfPeriod = h.isDay ? i + 1 : i - 11;
            h.planet = CHALDEAN_ORDER[(first + i) % 7];
            h.dayRuler = dayRuler;
            h.start = h.isDay
                    ? Math.round(today[0] + i * dayLen)
                    : Math.round(today[1] + (i - 12) * nightLen);
            h.end = h.isDay
                    ? Math.round(today[0] + (i + 1) * dayLen)
                    : Math.round(today[1] + (i - 11) * nightLen);
            h.nextPlanet = CHALDEAN_ORDER[(first + i + 1) % 7];
            hours[i] = h;
        }
        // The hour after the last one belongs to the next day, which starts with its own ruler
        hours[23].nextPlanet = DAY_RULERS[(weekday + 1) % 7];
        return hours;
    }

    /** The planetary hour in effect at {@code time}, or null in polar day or night. */
    static Hour current(long time, double lat, double lon) {
        Hour[] hours = day(time, lat, lon);
        if (hours == null) return null;
        for (Hour h : hours) if (time >= h.start && time < h.end) return h;
        return hours[23];
    }
}
