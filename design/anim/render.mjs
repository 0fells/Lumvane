import { createCanvas } from '@napi-rs/canvas';
import gifenc from 'gifenc';
import fs from 'fs';
const { GIFEncoder, quantize, applyPalette } = gifenc;

const W = 800, FPS = 25, T = 4.0, N = Math.round(T * FPS);
const OUT = process.argv[2];
if (!OUT) throw new Error('usage: node render.mjs <out.gif>');

const C = { tile: '#221A4D', tail: '#5C7CFF', shaft: '#F3EEFF', head: '#B79CFF', glow: '183, 156, 255', wind: '207, 194, 255' };
const REST = -30, SCALE = W / 512 * 0.9;

const GUST = 0.3;
function angle(t) {
  if (t < GUST) return REST;
  const u = t - GUST;
  return REST + 34 * Math.exp(-1.7 * u) * Math.sin(2 * Math.PI * 1.1 * u);
}
function gust(t) {
  const u = t - GUST;
  return u < 0 ? 0 : Math.exp(-2.2 * u) * Math.min(1, u * 8);
}
function glow(t) {
  const u = (t - 2.4) / 1.3;
  return u < 0 || u > 1 ? 0 : Math.sin(Math.PI * u);
}

function poly(ctx, pts, color) {
  ctx.beginPath();
  pts.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.closePath();
  ctx.fillStyle = color;
  ctx.fill();
}

function shaft(ctx) {
  ctx.beginPath();
  ctx.moveTo(162, 244); ctx.lineTo(358, 244); ctx.arc(358, 256, 12, -Math.PI / 2, Math.PI / 2);
  ctx.lineTo(162, 268); ctx.arc(162, 256, 12, Math.PI / 2, Math.PI * 1.5);
  ctx.closePath();
  ctx.fillStyle = C.shaft;
  ctx.fill();
}

function drawFrame(ctx, i) {
  const t = i / FPS;
  ctx.fillStyle = C.tile;
  ctx.fillRect(0, 0, W, W);

  const g = gust(t);
  if (g > 0.01) {
    ctx.strokeStyle = `rgba(${C.wind}, ${0.35 * g})`;
    ctx.lineWidth = 6;
    ctx.lineCap = 'round';
    for (const [y, phase, len] of [[250, 0, 180], [430, 0.35, 240], [560, 0.7, 150]]) {
      const x = ((t * 520 + phase * 800) % 1000) - 200;
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + len, y); ctx.stroke();
    }
  }

  ctx.save();
  ctx.translate(W / 2, W / 2);
  ctx.scale(SCALE, SCALE);
  ctx.rotate(angle(t) * Math.PI / 180);
  ctx.translate(-256, -256);
  const k = glow(t);
  if (k > 0.01) {
    const r = ctx.createRadialGradient(392, 256, 10, 392, 256, 150);
    r.addColorStop(0, `rgba(${C.glow}, ${0.55 * k})`);
    r.addColorStop(1, `rgba(${C.glow}, 0)`);
    ctx.fillStyle = r;
    ctx.beginPath(); ctx.arc(392, 256, 150, 0, Math.PI * 2); ctx.fill();
  }
  poly(ctx, [[64, 214], [150, 214], [176, 256], [150, 298], [64, 298], [98, 256]], C.tail);
  shaft(ctx);
  poly(ctx, [[352, 196], [456, 256], [352, 316]], C.head);
  ctx.beginPath(); ctx.arc(256, 256, 30, 0, Math.PI * 2);
  ctx.fillStyle = C.tile; ctx.fill();
  ctx.lineWidth = 14; ctx.strokeStyle = C.shaft; ctx.stroke();
  ctx.restore();
}

const canvas = createCanvas(W, W);
const ctx = canvas.getContext('2d');
const pixels = () => new Uint8Array(ctx.getImageData(0, 0, W, W).data.buffer.slice(0));

const sample = [];
for (let i = 0; i < N; i += 5) {
  drawFrame(ctx, i);
  const px = pixels();
  for (let p = 0; p < px.length; p += 16) sample.push(px[p], px[p + 1], px[p + 2], px[p + 3]);
}
const palette = quantize(new Uint8Array(sample), 256);

const gif = GIFEncoder();
for (let i = 0; i < N; i++) {
  drawFrame(ctx, i);
  gif.writeFrame(applyPalette(pixels(), palette), W, W, { palette, delay: 1000 / FPS, repeat: 0 });
}
gif.finish();
fs.writeFileSync(OUT, gif.bytes());
console.log(`ok: ${OUT} (${N} frames)`);
