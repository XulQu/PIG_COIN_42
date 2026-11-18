'use strict';

// Planetary Time API
// ------------------
// Classical planetary hours: the time from sunrise to sunset is split into 12
// equal "day hours" and sunset to the next sunrise into 12 "night hours". Each
// hour is ruled by a planet in the Chaldean order, starting at sunrise with the
// ruler of the weekday (Sunday = Sun, Monday = Moon, ...).
//
// Usage (browser):  PlanetaryTime.getPlanetaryHour(new Date(), lat, lon)
// Usage (Node):     const PlanetaryTime = require('./planetaryTime.js')
//
// Latitude is degrees north, longitude is degrees east (west is negative).

(function (root) {
  const CHALDEAN_ORDER = [
    'Saturn',
    'Jupiter',
    'Mars',
    'Sun',
    'Venus',
    'Mercury',
    'Moon',
  ];
  // Index 0 = Sunday, matching Date#getUTCDay()
  const DAY_RULERS = [
    'Sun',
    'Moon',
    'Mars',
    'Mercury',
    'Jupiter',
    'Venus',
    'Saturn',
  ];
  const SYMBOLS = {
    Sun: '☉',
    Moon: '☽',
    Mercury: '☿',
    Venus: '♀',
    Mars: '♂',
    Jupiter: '♃',
    Saturn: '♄',
  };

  const DAY_MS = 86400000;
  const J1970 = 2440587.5; // Julian date of the Unix epoch
  const J2000 = 2451545.0;
  const RAD = Math.PI / 180;

  const toJulian = date => date.getTime() / DAY_MS + J1970;
  const fromJulian = jd => new Date((jd - J1970) * DAY_MS);
  // Index of the solar day (days since J2000) whose noon is nearest to `date`
  const solarDayIndex = (date, lon) =>
    Math.round(toJulian(date) - J2000 - 0.0008 + lon / 360);

  // Sunrise, solar noon and sunset for solar day `n` (days since J2000).
  // Returns { polar: 'day' | 'night' } when the sun does not rise or set.
  const sunTimesForDay = (n, lat, lon) => {
    const jStar = n + 0.0008 - lon / 360;
    const M = (357.5291 + 0.98560028 * jStar) % 360;
    const C =
      1.9148 * Math.sin(M * RAD) +
      0.02 * Math.sin(2 * M * RAD) +
      0.0003 * Math.sin(3 * M * RAD);
    const lambda = (M + C + 180 + 102.9372) % 360;
    const jTransit =
      J2000 +
      jStar +
      0.0053 * Math.sin(M * RAD) -
      0.0069 * Math.sin(2 * lambda * RAD);
    const sinDec = Math.sin(lambda * RAD) * Math.sin(23.4397 * RAD);
    const cosDec = Math.cos(Math.asin(sinDec));
    // -0.833 deg: refraction plus the radius of the solar disc
    const cosOmega =
      (Math.sin(-0.833 * RAD) - Math.sin(lat * RAD) * sinDec) /
      (Math.cos(lat * RAD) * cosDec);

    const noon = fromJulian(jTransit);
    if (cosOmega < -1) return { noon, polar: 'day' };
    if (cosOmega > 1) return { noon, polar: 'night' };

    const omega = Math.acos(cosOmega) / RAD;
    return {
      noon,
      sunrise: fromJulian(jTransit - omega / 360),
      sunset: fromJulian(jTransit + omega / 360),
    };
  };

  const validate = (date, lat, lon) => {
    if (!(date instanceof Date) || isNaN(date.getTime()))
      throw new TypeError('date must be a valid Date');
    if (typeof lat !== 'number' || !(lat >= -90 && lat <= 90))
      throw new RangeError('latitude must be between -90 and 90');
    if (typeof lon !== 'number' || !(lon >= -180 && lon <= 180))
      throw new RangeError('longitude must be between -180 and 180');
  };

  // Sunrise / solar noon / sunset for the solar day containing `date`.
  const getSunTimes = (date, lat, lon) => {
    validate(date, lat, lon);
    return sunTimesForDay(solarDayIndex(date, lon), lat, lon);
  };

  // All 24 planetary hours of the planetary day (sunrise to next sunrise)
  // containing `date`. Returns null where the sun does not rise or set.
  const getPlanetaryDay = (date, lat, lon) => {
    validate(date, lat, lon);
    let n = solarDayIndex(date, lon);
    let today = sunTimesForDay(n, lat, lon);
    if (today.polar) return null;
    // Before today's sunrise we are still in yesterday's planetary day
    if (date < today.sunrise) {
      n -= 1;
      today = sunTimesForDay(n, lat, lon);
      if (today.polar) return null;
    }
    const tomorrow = sunTimesForDay(n + 1, lat, lon);
    if (tomorrow.polar) return null;

    // Weekday at the location, taken from local solar time at sunrise
    const localSunrise = new Date(
      today.sunrise.getTime() + (lon / 360) * DAY_MS,
    );
    const dayRuler = DAY_RULERS[localSunrise.getUTCDay()];
    const first = CHALDEAN_ORDER.indexOf(dayRuler);

    const sunrise = today.sunrise.getTime();
    const sunset = today.sunset.getTime();
    const dayLen = (sunset - sunrise) / 12;
    const nightLen = (tomorrow.sunrise.getTime() - sunset) / 12;

    const hours = [];
    for (let i = 0; i < 24; i++) {
      const isDay = i < 12;
      const start = isDay ? sunrise + i * dayLen : sunset + (i - 12) * nightLen;
      const planet = CHALDEAN_ORDER[(first + i) % 7];
      hours.push({
        hourNumber: i + 1, // 1-24, counted from sunrise
        hourOfPeriod: isDay ? i + 1 : i - 11, // 1-12 within day or night
        isDay,
        planet,
        symbol: SYMBOLS[planet],
        start: new Date(start),
        end: new Date(start + (isDay ? dayLen : nightLen)),
      });
    }

    return {
      dayRuler,
      dayRulerSymbol: SYMBOLS[dayRuler],
      sunrise: today.sunrise,
      sunset: today.sunset,
      nextSunrise: tomorrow.sunrise,
      hours,
    };
  };

  // The planetary hour in effect at `date`, plus the one that follows it.
  // Returns null where the sun does not rise or set (polar day / night).
  const getPlanetaryHour = (date, lat, lon) => {
    const day = getPlanetaryDay(date, lat, lon);
    if (!day) return null;
    const t = date.getTime();
    const idx = day.hours.findIndex(h => t >= h.start && t < h.end);
    const current = day.hours[idx === -1 ? 23 : idx];

    let next = day.hours[current.hourNumber]; // undefined after hour 24
    if (!next) {
      const following = getPlanetaryDay(
        new Date(day.nextSunrise.getTime() + 1000),
        lat,
        lon,
      );
      next = following ? following.hours[0] : null;
    }

    return Object.assign({}, current, {
      dayRuler: day.dayRuler,
      dayRulerSymbol: day.dayRulerSymbol,
      sunrise: day.sunrise,
      sunset: day.sunset,
      next: next
        ? { planet: next.planet, symbol: next.symbol, start: next.start }
        : null,
    });
  };

  const api = {
    CHALDEAN_ORDER,
    DAY_RULERS,
    SYMBOLS,
    getSunTimes,
    getPlanetaryDay,
    getPlanetaryHour,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PlanetaryTime = api;
})(typeof self !== 'undefined' ? self : this);
