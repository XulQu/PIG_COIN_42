# Mercury

A personal assistant page: dump a thought, give it a time slot, and Mercury pings you with one of three sounds.

- `mercury.html` is the source, published as a Claude artifact (Claude files your dumps and speaks back).
- `index.html`, `manifest.webmanifest` and the icons are the self-hosted copy, built with `node build.js`.

Self-hosting on your home network or a VPS (any static host works):

    python3 -m http.server 8377

Then open `http://<that-machine's-IP>:8377/` in Brave on the phone and choose Add to Home screen. The self-hosted copy has the winged-shoe icon and files dumps as written, since Claude's reasoning is only available inside the artifact.

Memory is one JSON object kept in the browser on the device (items, sound choices, and every start-up and shut-down). Save or load it as `mercury-memory.json` from the Memory panel.
