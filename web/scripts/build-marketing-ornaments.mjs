/**
 * Pre-renders the product page's ornaments from the ornament generator.
 *
 * `docs/` is a dependency-free static site — the Pages workflow copies it
 * verbatim, with no bundler — so it can't import the TypeScript generator the
 * app uses. This script runs the generator at authoring time and writes its
 * output as committed assets:
 *
 *   docs/ornaments.css       custom properties for the frieze bands and url()
 *                            refs to the ornaments below
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
import { borderBandBackground } from '../src/ui/theme/ornamentSvg.ts'

const DOCS = join(dirname(fileURLToPath(import.meta.url)), '../../docs')

/** Al-Fatihah (7 ayat) — the seal and frieze of the page. */
const COVER_SEED = chapterOrnamentSeed(1, 7)
/** Al-Ikhlas (4 ayat) — the footer tailpiece. */
const TAILPIECE_SEED = chapterOrnamentSeed(112, 4)
/**
 * One colour scheme's inks, matching the page tokens in `docs/styles.css`.
 * Data-URI and standalone SVGs can't resolve `var(--…)`, so each scheme is
 * rendered separately and swapped by a `prefers-color-scheme` block.
 */
const SCHEMES = {
  light: {
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
      ground: 'rgba(232,226,213,0.34)',
      mortar: 'rgba(138,107,30,0.30)',
      tileBlue: 'rgba(65,108,120,0.13)',
      tileGreen: 'rgba(38,111,82,0.16)',
      tileGold: 'rgba(184,144,28,0.20)',
      tileIvory: 'rgba(255,255,255,0.38)',
      rule: 'rgba(138,107,30,0.62)',
    },
  },
  dark: {
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
      ground: 'rgba(10,18,17,0.58)',
      mortar: 'rgba(237,209,136,0.24)',
      tileBlue: 'rgba(38,82,94,0.38)',
      tileGreen: 'rgba(27,91,65,0.42)',
      tileGold: 'rgba(154,123,42,0.42)',
      tileIvory: 'rgba(232,226,213,0.14)',
      rule: 'rgba(217,180,74,0.50)',
    },
  },
}

const ROSETTE_SCALE = 200

const radialPath = (cell, points, outer, inner = outer, turn = -Math.PI / 2) =>
  Array.from({ length: points }, (_, i) => {
    const radius = i % 2 ? inner : outer
    const angle = turn + (i * Math.PI * 2) / points
    return `${i ? 'L' : 'M'}${(cell / 2 + Math.cos(angle) * radius).toFixed(1)} ${(cell / 2 + Math.sin(angle) * radius).toFixed(1)}`
  }).join(' ') + 'Z'

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
 * An architectural surround rather than a medallion: a pointed multifoil
 * arch inside a plain double-rule alfiz, with a quiet eightfold zellige field
 * confined to the spandrels. The arch is drafted, not drawn freehand —
 * cusps sit on a compass-swung guide arc and every foil is an exact
 * circular arc, the way a zellij maallem would set it out.
 */
function heroArchSvg(ink) {
  const cell = 84
  const khatam = radialPath(cell, 16, 28, 12)
  const innerOctagon = radialPath(cell, 8, 9, 9, -Math.PI / 8)
  const lozenges = `M42 0L49 7L42 14L35 7ZM84 42L77 49L70 42L77 35ZM42 84L35 77L42 70L49 77ZM0 42L7 35L14 42L7 49Z`
  const cornerTiles = `M0 0H14L0 14ZM84 0H70L84 14ZM84 84H70L84 70ZM0 84H14L0 70Z`
  const joints = `M14 0L22.2 22.2L0 14M70 0L61.8 22.2L84 14M84 70L61.8 61.8L70 84M14 84L22.2 61.8L0 70`

  const AXIS = 300 // centreline of the 600-wide viewBox
  const BOTTOM = 724
  const SPRING_Y = 420 // springline: foils above, straight jambs below
  const JAMB_X = 95 // opening half-width a = AXIS − JAMB_X
  const GUIDE_APEX_Y = 60
  const FOILS = 7 // per side, counting the half-foil that forms the point
  const FOIL_K = 0.58 // foil radius / chord; 0.5 is a semicircle

  // Two-centre pointed arch guide through springing and apex.
  const a = AXIS - JAMB_X
  const h = SPRING_Y - GUIDE_APEX_Y
  const d = (h * h - a * a) / (2 * a)
  const R = d + a
  const cx = AXIS + d
  const span = Math.atan2(h, d)

  // Cusps divide the guide evenly; the last foil overshoots the guide apex
  // so the two sides cross in a true point rather than a cleft.
  const cusps = Array.from({ length: FOILS }, (_, j) => {
    const t = (j * span) / FOILS
    return [cx - R * Math.cos(t), SPRING_Y - R * Math.sin(t)]
  })
  const apex = [AXIS, GUIDE_APEX_Y - 14]

  const f = (v) => Number(v.toFixed(1))
  const foil = ([x0, y0], [x1, y1], sweep) => {
    const rho = f(FOIL_K * Math.hypot(x1 - x0, y1 - y0))
    return `A${rho} ${rho} 0 0 ${sweep} ${f(x1)} ${f(y1)}`
  }
  const left = cusps.slice(1).map((c, j) => foil(cusps[j], c, 0))
  const mirror = ([x, y]) => [2 * AXIS - x, y]
  const rightCusps = [...cusps].reverse().map(mirror)
  const right = rightCusps.map((c, j) => foil(j === 0 ? apex : rightCusps[j - 1], c, 0))
  const archLine =
    `M${JAMB_X} ${BOTTOM}V${SPRING_Y}` +
    left.join('') +
    foil(cusps[FOILS - 1], apex, 0) +
    right.join('') +
    `V${BOTTOM}`

  const facade = `M30 28H570V${BOTTOM}H30Z ${archLine}Z`

  return (
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 752">` +
    `<defs>` +
    `<pattern id="z" width="${cell}" height="${cell}" patternUnits="userSpaceOnUse">` +
    `<rect width="84" height="84" fill="${ink.tileBlue}"/>` +
    `<g stroke="${ink.mortar}" stroke-width="1.15" stroke-linejoin="round">` +
    `<path d="${cornerTiles}" fill="${ink.tileIvory}"/><path d="${lozenges}" fill="${ink.tileGold}"/>` +
    `<path d="${khatam}" fill="${ink.tileGreen}"/><path d="${innerOctagon}" fill="${ink.tileGold}"/>` +
    `<path d="${joints}" fill="none"/>` +
    `</g>` +
    `</pattern>` +
    `<linearGradient id="sharp-fade" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="white"/><stop offset="56%" stop-color="white"/>` +
    `<stop offset="66%" stop-color="#aaa"/><stop offset="78%" stop-color="black"/>` +
    `</linearGradient>` +
    `<linearGradient id="soft-fade" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="black"/><stop offset="54%" stop-color="black"/>` +
    `<stop offset="62%" stop-color="#3a3a3a"/><stop offset="72%" stop-color="#333"/>` +
    `<stop offset="82%" stop-color="black"/>` +
    `</linearGradient>` +
    `<linearGradient id="haze-fade" x1="0" y1="0" x2="0" y2="1">` +
    `<stop offset="0" stop-color="black"/><stop offset="64%" stop-color="black"/>` +
    `<stop offset="72%" stop-color="#222"/><stop offset="80%" stop-color="#1c1c1c"/>` +
    `<stop offset="86%" stop-color="black"/><stop offset="100%" stop-color="black"/>` +
    `</linearGradient>` +
    `<mask id="sharp"><rect width="600" height="752" fill="url(#sharp-fade)"/></mask>` +
    `<mask id="soft"><rect width="600" height="752" fill="url(#soft-fade)"/></mask>` +
    `<mask id="haze"><rect width="600" height="752" fill="url(#haze-fade)"/></mask>` +
    `<filter id="soft-blur" x="-20" y="-20" width="640" height="792" filterUnits="userSpaceOnUse">` +
    `<feGaussianBlur stdDeviation="1.8"/></filter>` +
    `<filter id="haze-blur" x="-20" y="-20" width="640" height="792" filterUnits="userSpaceOnUse">` +
    `<feGaussianBlur stdDeviation="4.2"/></filter>` +
    `<g id="arch">` +
    `<path d="${facade}" fill="${ink.ground}" fill-rule="evenodd"/>` +
    `<path d="${facade}" fill="url(#z)" fill-rule="evenodd" opacity=".9"/>` +
    `<g fill="none" stroke="${ink.rule}" stroke-linejoin="round">` +
    `<path d="M30 ${BOTTOM}V28H570V${BOTTOM}" stroke-width="1.5"/>` +
    `<path d="M36 ${BOTTOM}V34H564V${BOTTOM}" stroke-width="1"/>` +
    `<path d="${archLine}" stroke-width="2.8"/>` +
    `<path d="M36 ${SPRING_Y}H${JAMB_X}M${600 - JAMB_X} ${SPRING_Y}H564" stroke-width="1.2"/>` +
    `<path d="M36 428H88M512 428H564" stroke-width="1.2"/>` +
    `<path d="M79 428V${BOTTOM}M521 428V${BOTTOM}" stroke-width="1"/>` +
    `</g></g>` +
    `</defs>` +
    `<use href="#arch" mask="url(#sharp)"/>` +
    `<use href="#arch" mask="url(#soft)" filter="url(#soft-blur)"/>` +
    `<use href="#arch" mask="url(#haze)" filter="url(#haze-blur)"/>` +
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
  writeFileSync(join(DOCS, archFile), heroArchSvg(ink.arch) + '\n')
  vars[scheme].push(`--orn-hero-arch: url('${archFile}');`)

  for (const [suffix, vertical] of [['h', false], ['v', true]]) {
    const band = borderBandBackground(cover.border, vertical, ink.band)
    vars[scheme].push(`--orn-band-${suffix}: ${band.backgroundImage};`)
  }
}

// Geometry shared by both schemes: the tile size and the frieze period, so
// CSS can size bands with calc() at whatever height a given rule wants.
const shared = [
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
