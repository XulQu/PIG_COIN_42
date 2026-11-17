// Builds the standalone (self-hosted) copy of Mercury from mercury.html, plus the winged-shoe icon.
const fs = require('fs'), path = require('path');
const src = fs.readFileSync(path.join(__dirname, 'mercury.html'), 'utf8');
const rows = src.match(/var rows=\[([\s\S]*?)\]/)[1].match(/"[^"]+"/g).map(s => s.slice(1, -1));
let dots = '';
rows.forEach((r, y) => [...r].forEach((ch, x) => { if (ch !== '.') dots += `<circle cx="${x + 2.5}" cy="${y + 6.5}" r=".4" fill="${ch === 'r' ? '#e0202a' : '#f3f3f0'}"/>`; }));
fs.writeFileSync(path.join(__dirname, 'icon.svg'), `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 26 26"><rect width="26" height="26" fill="#000"/>${dots}</svg>\n`);
fs.writeFileSync(path.join(__dirname, 'manifest.webmanifest'), JSON.stringify({
  name: 'Mercury', short_name: 'Mercury', start_url: './index.html', display: 'standalone',
  background_color: '#000000', theme_color: '#000000',
  icons: [{ src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' }, { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' }]
}, null, 2) + '\n');
fs.writeFileSync(path.join(__dirname, 'index.html'), `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#000000">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="icon-192.png">
<style>body{margin:0}[hidden]{display:none!important}</style>
</head><body>
${src}</body></html>
`);
