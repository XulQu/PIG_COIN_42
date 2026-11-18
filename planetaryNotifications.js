'use strict';

// Planetary hour notifications
// ----------------------------
// Uses the browser's location with the PlanetaryTime API (planetaryTime.js) to
// show the current planetary hour on the page and to send a browser
// notification each time a new planetary hour begins.
//
// Notifications are sent while this page is open in a tab. Location and
// notifications need a secure page (https or localhost).

(function () {
  const STORAGE_KEY = 'planetaryNotificationsEnabled';
  const DEFAULT_LABEL = '🪐 Planetary time';
  const planetBtn = document.querySelector('.btn--planet');
  if (!planetBtn || typeof PlanetaryTime === 'undefined') return;

  let enabled = false;
  let coords = null;
  let timer = null;
  let lastNotified = null;

  const formatTime = date =>
    date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

  const storage = {
    get: () => {
      try {
        return localStorage.getItem(STORAGE_KEY) === 'true';
      } catch (err) {
        return false;
      }
    },
    set: value => {
      try {
        localStorage.setItem(STORAGE_KEY, String(value));
      } catch (err) {
        // Storage unavailable (private mode): the setting lasts for this visit
      }
    },
  };

  const getPosition = () =>
    new Promise((resolve, reject) => {
      if (!('geolocation' in navigator))
        return reject(new Error('Location is not supported'));
      navigator.geolocation.getCurrentPosition(
        pos => resolve(pos.coords),
        reject,
        { maximumAge: 3600000, timeout: 15000 },
      );
    });

  const requestNotificationPermission = async () => {
    if (!('Notification' in window)) return 'unsupported';
    if (Notification.permission !== 'default') return Notification.permission;
    try {
      return await Notification.requestPermission();
    } catch (err) {
      return 'denied';
    }
  };

  const notify = hour => {
    if (!('Notification' in window) || Notification.permission !== 'granted')
      return;
    const period = hour.isDay ? 'day' : 'night';
    const next = hour.next ? ` Next: ${hour.next.planet}.` : '';
    try {
      new Notification(`${hour.symbol} Hour of ${hour.planet}`, {
        body:
          `Hour ${hour.hourOfPeriod} of the ${period}, ` +
          `until ${formatTime(hour.end)}. ` +
          `Day of ${hour.dayRuler}.${next}`,
        tag: 'planetary-hour',
        icon: 'dice-6.png',
      });
    } catch (err) {
      // Some mobile browsers only allow notifications from a service worker
    }
  };

  const stop = message => {
    enabled = false;
    clearTimeout(timer);
    lastNotified = null;
    storage.set(false);
    planetBtn.textContent = message || DEFAULT_LABEL;
    planetBtn.title = 'Show the planetary hour for your location';
  };

  // Show the current hour, notify if it changed, and wake up for the next one
  const refresh = () => {
    if (!enabled || !coords) return;
    clearTimeout(timer);
    const hour = PlanetaryTime.getPlanetaryHour(
      new Date(),
      coords.latitude,
      coords.longitude,
    );
    if (!hour) {
      // Polar day or night: no sunrise or sunset to divide into hours
      planetBtn.textContent = '🪐 No planetary hours here today';
      timer = setTimeout(refresh, 3600000);
      return;
    }

    planetBtn.textContent = `${hour.symbol} ${hour.planet} hour · until ${formatTime(
      hour.end,
    )}`;
    planetBtn.title =
      `Day of ${hour.dayRuler}, hour ${hour.hourOfPeriod} of the ` +
      `${hour.isDay ? 'day' : 'night'}. Click to turn off.`;

    const key = hour.start.getTime();
    if (key !== lastNotified) {
      lastNotified = key;
      notify(hour);
    }
    timer = setTimeout(refresh, hour.end.getTime() - Date.now() + 500);
  };

  const start = async () => {
    planetBtn.textContent = '🪐 Finding your location…';
    try {
      coords = await getPosition();
    } catch (err) {
      stop('🪐 Location needed – try again');
      return;
    }
    await requestNotificationPermission();
    enabled = true;
    storage.set(true);
    refresh();
  };

  planetBtn.addEventListener('click', function () {
    if (enabled) stop();
    else start();
  });

  // Timers are paused while a tab sleeps, so catch up when it is shown again
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) refresh();
  });

  if (storage.get()) start();
})();
