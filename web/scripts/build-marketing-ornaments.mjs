/**
 * Pre-renders the product page's ornaments from the ornament generator.
 *
 * `docs/` is a dependency-free static site — the Pages workflow copies it
 * verbatim, with no bundler — so it can't import the TypeScript generator the
 * app uses. This script runs the generator at authoring time and writes its
 * output as committed assets:
 *
 *   docs/ornaments.css       custom properties: the tiling field, the frieze
 *                            bands, and url() refs to the rosettes below
 *   docs/ornaments/*.svg     the rosettes and hero arch, one per colour scheme
 *
 * Both are committed. Re-run `npm run build:ornaments` (from `web/`) only when
 * deliberately changing the page's ornaments — a different seed grows an
 * entirely different composition.
 *
 * The seeds are chapter fingerprints, same function the reader uses: the page
 * wears Al-Fatihah's cover, and the footer closes on Al-Ikhlas.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  chapterOrnamentSeed,
  generateChapterOrnament,
  generateCoverOrnament,
} from '../src/ui/theme/ornamentGenerator.ts'
import { borderBandBackground, fieldWeaveBackground } from '../src/ui/theme/ornamentSvg.ts'

const DOCS = join(dirname(fileURLToPath(import.meta.url)), '../../docs')

/** Al-Fatihah (7 ayat) — the seal, frieze and field of the page. */
const COVER_SEED = chapterOrnamentSeed(1, 7)
/** Al-Ikhlas (4 ayat) — the footer tailpiece. */
const TAILPIECE_SEED = chapterOrnamentSeed(112, 4)

/**
 * Field cell width in CSS pixels. The generator's suggested width is tuned
 * for a phone; the page wash reads better tiled larger, so the suggestion is
 * scaled rather than replaced (it still varies with the seed).
 */
const FIELD_SCALE = 0.85

/**
 * One colour scheme's inks, matching the page tokens in `docs/styles.css`.
 * Data-URI and standalone SVGs can't resolve `var(--…)`, so each scheme is
 * rendered separately and swapped by a `prefers-color-scheme` block.
 */
const SCHEMES = {
  light: {
    fieldInk: 'rgba(184,144,28,0.12)',
    fieldEmboss: 'rgba(255,255,255,0.55)',
    band: {
      gold: 'rgba(184,144,28,0.78)',
      embossDark: 'rgba(28,27,24,0.10)',
      embossLight: 'rgba(255,255,255,0.60)',
    },
    leaf: {
      deep: '#8a6b1e',
      bright: '#c8a63c',
      embossDark: 'rgba(28,27,24,0.14)',
      embossLight: 'rgba(255,255,255,0.55)',
    },
    arch: {
      ground: 'rgba(184,144,28,0.045)',
      pattern: 'rgba(138,107,30,0.24)',
      rule: 'rgba(138,107,30,0.62)',
    },
  },
  dark: {
    fieldInk: 'rgba(217,180,74,0.10)',
    fieldEmboss: 'rgba(255,255,255,0.05)',
    band: {
      gold: 'rgba(217,180,74,0.72)',
      embossDark: 'rgba(0,0,0,0.40)',
      embossLight: 'rgba(255,255,255,0.07)',
    },
    leaf: {
      deep: '#9a7b2a',
      bright: '#edd188',
      embossDark: 'rgba(0,0,0,0.40)',
      embossLight: 'rgba(255,255,255,0.12)',
    },
    arch: {
      ground: 'rgba(217,180,74,0.035)',
      pattern: 'rgba(237,209,136,0.19)',
      rule: 'rgba(217,180,74,0.50)',
    },
  },
}

const ROSETTE_SCALE = 200

function pathAt(stroke, precision) {
  const parts = stroke.points.map(
    (p, i) =>
      `${i === 0 ? 'M' : 'L'}${(p.x * ROSETTE_SCALE).toFixed(precision)} ${(p.y * ROSETTE_SCALE).toFixed(precision)}`,
  )
  if (stroke.closed) parts.push('Z')
  return parts.join(' ')
}

/**
 * The drawn extent of a rosette in viewBox units, padded for stroke width and
 * relief offset. Corner seals carry a four-petal bezel that reaches past the
 * unit box, so the box can't be assumed to be 0…200.
 */
function extent(spec, strokeWidth) {
  let lo = Infinity
  let hi = -Infinity
  for (const s of spec.strokes) {
    for (const p of s.points) {
      lo = Math.min(lo, p.x, p.y)
      hi = Math.max(hi, p.x, p.y)
    }
  }
  for (const d of spec.dots) {
    lo = Math.min(lo, d.x - d.radius, d.y - d.radius)
    hi = Math.max(hi, d.x + d.radius, d.y + d.radius)
  }
  const pad = strokeWidth / 2 + 2
  return { min: lo * ROSETTE_SCALE - pad, size: (hi - lo) * ROSETTE_SCALE + 2 * pad }
}

/**
 * A rosette as a standalone SVG file: embossed relief copies under a
 * gold-leaf face, the same material language as `GeneratedRosette` — but
 * fully formed, with the geometry defined once and re-`use`d per ink layer
 * (three inline copies of a corolla's sampled curves is a lot of bytes).
 */
function rosetteSvg(spec, leaf, { ruleWidth = 2.2, hairWidth = 1, precision = 1 } = {}) {
  const box = extent(spec, Math.max(ruleWidth, hairWidth) + 2)
  const body = spec.strokes
    .map(
      (s) =>
        `<path d="${pathAt(s, precision)}" stroke-width="${s.weight === 'rule' ? ruleWidth : hairWidth}"/>`,
    )
    .join('')
  const dots = spec.dots
    .map(
      (d) =>
        `<circle cx="${(d.x * ROSETTE_SCALE).toFixed(precision)}" cy="${(d.y * ROSETTE_SCALE).toFixed(precision)}" r="${(d.radius * ROSETTE_SCALE).toFixed(precision)}"/>`,
    )
    .join('')
  const view = `${box.min.toFixed(2)} ${box.min.toFixed(2)} ${box.size.toFixed(2)} ${box.size.toFixed(2)}`
  // Relief first, face last — pressed into the page.
  const layers = [
    `<use href="#o" x="0.9" y="0.9" stroke="${leaf.embossDark}" fill="${leaf.embossDark}"/>`,
    `<use href="#o" x="-0.9" y="-0.9" stroke="${leaf.embossLight}" fill="${leaf.embossLight}"/>`,
    `<use href="#o" stroke="url(#leaf)" fill="url(#leaf)"/>`,
  ].join('')
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${view}">` +
    `<defs>` +
    `<linearGradient id="leaf" x1="0%" y1="22%" x2="100%" y2="78%">` +
    `<stop offset="0%" stop-color="${leaf.deep}"/>` +
    `<stop offset="50%" stop-color="${leaf.bright}"/>` +
    `<stop offset="100%" stop-color="${leaf.deep}"/>` +
    `</linearGradient>` +
    `<g id="o" fill="none" stroke-linecap="round" stroke-linejoin="round">${body}<g stroke="none">${dots}</g></g>` +
    `</defs>${layers}</svg>`
  )
}

/**
 * An architectural surround rather than a medallion: the transparent centre
 * is a Moroccan lambrequin opening, while Al-Fatihah's generated weave is
 * confined to the spandrels, cornice and tiled side dados.
 */
function heroArchSvg(field, ink) {
  const cell = 54
  const weave = field.strokes
    .map((stroke) => {
      const points = stroke.points
        .map((p, i) => `${i === 0 ? 'M' : 'L'}${(p.x * cell).toFixed(1)} ${(p.y * cell).toFixed(1)}`)
        .join(' ')
      return `<path d="${points}${stroke.closed ? ' Z' : ''}"/>`
    })
    .join('')
  const archLine =
    'M126 724V416C114 392 104 367 93 332C110 321 110 304 95 294' +
    'C80 284 81 264 100 258C119 252 122 237 108 226' +
    'C120 207 141 191 174 183C158 168 164 145 186 143' +
    'C210 141 225 125 228 105C232 82 253 80 268 92' +
    'C285 72 295 34 300 14C305 34 315 72 332 92' +
    'C347 80 368 82 372 105C375 125 390 141 414 143' +
    'C436 145 442 168 426 183C459 191 480 207 492 226' +
    'C478 237 481 252 500 258C519 264 520 284 505 294' +
    'C490 304 490 321 507 332C496 367 486 392 474 416V724'
  const opening = `${archLine}Z`
  const facade = `M30 28H570V724H30Z ${opening}`
  const diamonds = Array.from({ length: 12 }, (_, i) => {
    const x = 52 + i * 45
    return `<path d="M${x} 51l13 -13 13 13-13 13Z"/>`
  }).join('')

  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 752">` +
    `<defs>` +
    `<pattern id="z" width="${cell}" height="${cell}" patternUnits="userSpaceOnUse">` +
    `<g fill="none" stroke="${ink.pattern}" stroke-width="1.15" stroke-linejoin="round">${weave}</g>` +
    `</pattern>` +
    `<linearGradient id="fade" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="white"/><stop offset="76%" stop-color="white"/>` +
    `<stop offset="100%" stop-color="black"/>` +
    `</linearGradient>` +
    `<mask id="m"><path d="${facade}" fill="url(#fade)" fill-rule="evenodd"/></mask>` +
    `</defs>` +
    `<g mask="url(#m)">` +
    `<path d="${facade}" fill="${ink.ground}" fill-rule="evenodd"/>` +
    `<path d="${facade}" fill="url(#z)" fill-rule="evenodd" opacity=".78"/>` +
    `</g>` +
    `<g fill="none" stroke-linejoin="round" vector-effect="non-scaling-stroke">` +
    `<path d="M30 724V28H570V724M42 724V74H558V724" stroke="${ink.rule}" stroke-width="1.4"/>` +
    `<path d="M30 75H570M42 82H558" stroke="${ink.rule}" stroke-width="1.1"/>` +
    `<g stroke="${ink.rule}" stroke-width="1">${diamonds}</g>` +
    `<path d="${archLine}" stroke="${ink.rule}" stroke-width="3.2"/>` +
    `<path d="M51 706V424M92 706V435M508 706V435M549 706V424" stroke="${ink.rule}" stroke-width="1.2"/>` +
    `<path d="M42 424H110M490 424H558M42 706H126M474 706H558" stroke="${ink.rule}" stroke-width="1.2"/>` +
    `</g>` +
    `</svg>`
  )
}

const cover = generateCoverOrnament(COVER_SEED)
const tailpiece = generateChapterOrnament(TAILPIECE_SEED)

/** Rosettes, by file stem. Small renders need heavier strokes to read. */
const ROSETTES = {
  seal: { spec: cover.cornerSeal, opts: { ruleWidth: 4.6, hairWidth: 4.6 } },
  tailpiece: { spec: tailpiece.rosette, opts: { ruleWidth: 3.4, hairWidth: 2 } },
  // The kicker pip: the seal again, but at ~14px it is a mark, not a rosette.
  pip: { spec: cover.cornerSeal, opts: { ruleWidth: 9, hairWidth: 9 } },
}

mkdirSync(join(DOCS, 'ornaments'), { recursive: true })

const vars = { light: [], dark: [] }

for (const [scheme, ink] of Object.entries(SCHEMES)) {
  for (const [name, { spec, opts }] of Object.entries(ROSETTES)) {
    const file = `ornaments/${name}-${scheme}.svg`
    writeFileSync(join(DOCS, file), rosetteSvg(spec, ink.leaf, opts) + '\n')
    vars[scheme].push(`--orn-${name}: url('${file}');`)
  }

  const archFile = `ornaments/hero-arch-${scheme}.svg`
  writeFileSync(join(DOCS, archFile), heroArchSvg(cover.field, ink.arch) + '\n')
  vars[scheme].push(`--orn-hero-arch: url('${archFile}');`)

  const field = fieldWeaveBackground(cover.field, ink.fieldInk, ink.fieldEmboss)
  vars[scheme].push(`--orn-field: ${field.backgroundImage};`)

  for (const [suffix, vertical] of [['h', false], ['v', true]]) {
    const band = borderBandBackground(cover.border, vertical, ink.band)
    vars[scheme].push(`--orn-band-${suffix}: ${band.backgroundImage};`)
  }
}

// Geometry shared by both schemes: the tile size and the frieze period, so
// CSS can size bands with calc() at whatever height a given rule wants.
const cell = cover.field.cellWidthDp * FIELD_SCALE
const shared = [
  `--orn-field-size: ${cell.toFixed(2)}px ${((cell * cover.field.cellH) / cover.field.cellW).toFixed(2)}px;`,
  `--orn-band-period: ${cover.border.period.toFixed(4)};`,
]

const block = (lines, indent) => lines.map((l) => indent + l).join('\n')

const css = `/* Generated by web/scripts/build-marketing-ornaments.mjs — do not edit.
 *
 * The product page's ornaments, grown from the same generator the app uses.
 * Cover (corner seal, frieze, field) seed ${COVER_SEED} — Al-Fatihah's
 * fingerprint; footer tailpiece seed ${TAILPIECE_SEED} — Al-Ikhlas.
 *
 * Regenerate with \`npm run build:ornaments\` from \`web/\`. Consumed by
 * styles.css; see the "Ornament" section there for how each piece is used.
 */

:root {
${block(shared, '  ')}
${block(vars.light, '  ')}
}

@media (prefers-color-scheme: dark) {
  :root {
${block(vars.dark, '    ')}
  }
}
`

writeFileSync(join(DOCS, 'ornaments.css'), css)
console.log(`ornaments.css + ${(Object.keys(ROSETTES).length + 1) * 2} svg (cover seed ${COVER_SEED})`)
