/* Beautiful Quran — web port of ui/theme/Fade.kt letterFadeIn.
 *
 * One-to-one with the Android highlight wash:
 *   - inkSmootherstep (6t⁵−15t⁴+10t³)
 *   - InkWashFeather = 1.6 (feather wider than the glyph run)
 *   - 9-stop alpha profile across the feather
 *   - progress 0..1 advances the wash head; letters ahead rest at restingAlpha,
 *     letters behind are fully inked
 *
 * Firefox (especially Android) is unreliable with mask-image alpha washes on
 * text, and a failed mask collapses to a whole-element opacity pop — which is
 * exactly the "white fade of the whole paragraph" bug. Text therefore uses the
 * same spatial profile painted as a fill gradient via background-clip:text
 * (the CSS equivalent of Compose's DstIn letter wash). Non-text atoms animate
 * opacity on the smootherstep clock instead.
 *
 * On the marketing site progress is not locked to audio word timings —
 * accelerated — but the spatial ink effect matches the reader.
 */
(function (root) {
  'use strict';

  /** Matches Fade.kt InkProfileStops. */
  var INK_PROFILE_STOPS = 9;

  /** Matches Fade.kt InkWashFeather — feather is 1.6× the run width. */
  var INK_WASH_FEATHER = 1.6;

  /** Matches Fade.kt letterFadeIn default restingAlpha. */
  var DEFAULT_RESTING_ALPHA = 0.35;

  /**
   * smootherstep (6t⁵−15t⁴+10t³): zero first and second derivative at both ends.
   * Port of Fade.kt inkSmootherstep.
   */
  function inkSmootherstep(t) {
    var c = t < 0 ? 0 : t > 1 ? 1 : t;
    return c * c * c * (c * (c * 6 - 15) + 10);
  }

  /**
   * Alpha at parameter t across the feather edge.
   * Port of the washColors sampling in letterFadeIn.
   */
  function washAlphaAt(t, rtl, restingAlpha) {
    var s = inkSmootherstep(t);
    return restingAlpha + (1 - restingAlpha) * (rtl ? s : 1 - s);
  }

  function parseCssColor(css) {
    if (!css) return { r: 28, g: 27, b: 24, a: 1 };
    css = String(css).trim();
    var m = css.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i);
    if (m) {
      return {
        r: +m[1],
        g: +m[2],
        b: +m[3],
        a: m[4] == null ? 1 : +m[4],
      };
    }
    // Hex fallback (#rgb / #rrggbb)
    m = css.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
    if (m) {
      var h = m[1];
      if (h.length === 3) {
        return {
          r: parseInt(h[0] + h[0], 16),
          g: parseInt(h[1] + h[1], 16),
          b: parseInt(h[2] + h[2], 16),
          a: 1,
        };
      }
      return {
        r: parseInt(h.slice(0, 2), 16),
        g: parseInt(h.slice(2, 4), 16),
        b: parseInt(h.slice(4, 6), 16),
        a: 1,
      };
    }
    return { r: 28, g: 27, b: 24, a: 1 };
  }

  function rgba(c, alpha) {
    return 'rgba(' + c.r + ',' + c.g + ',' + c.b + ',' + alpha + ')';
  }

  /**
   * Spatial wash gradient (same stops / geometry as Fade.kt letterFadeIn).
   * Used as a text fill via background-clip:text.
   */
  function buildWashFill(progress, widthPx, rtl, restingAlpha, inkColor) {
    var p = progress < 0 ? 0 : progress > 1 ? 1 : progress;
    if (p >= 1) return null;
    if (!(widthPx > 0)) return null;

    var edge = Math.max(widthPx * INK_WASH_FEATHER, 1);
    var head = p * (widthPx + edge);
    var startX;
    var endX;
    if (rtl) {
      startX = widthPx - head;
      endX = widthPx - head + edge;
    } else {
      startX = head - edge;
      endX = head;
    }

    var stops = [];
    function push(x, a) {
      stops.push(rgba(inkColor, a) + ' ' + x + 'px');
    }

    // Outside the feather, Compose clamps edge colors of the gradient.
    // LTR: left of startX = full ink; right of endX = resting.
    // RTL: left of startX = resting; right of endX = full ink.
    if (!rtl) {
      push(Math.min(0, startX), 1);
      for (var i = 0; i < INK_PROFILE_STOPS; i++) {
        var t = i / (INK_PROFILE_STOPS - 1);
        push(startX + t * (endX - startX), washAlphaAt(t, false, restingAlpha));
      }
      push(Math.max(widthPx, endX), restingAlpha);
    } else {
      push(Math.min(0, startX), restingAlpha);
      for (var j = 0; j < INK_PROFILE_STOPS; j++) {
        var u = j / (INK_PROFILE_STOPS - 1);
        push(startX + u * (endX - startX), washAlphaAt(u, true, restingAlpha));
      }
      push(Math.max(widthPx, endX), 1);
    }

    return 'linear-gradient(to right, ' + stops.join(', ') + ')';
  }

  /** @deprecated kept for tests / callers; builds the old alpha-mask form. */
  function buildWashMask(progress, widthPx, rtl, restingAlpha) {
    var fill = buildWashFill(progress, widthPx, rtl, restingAlpha, { r: 0, g: 0, b: 0, a: 1 });
    return fill;
  }

  function resolveInkColor(el) {
    var cs = root.getComputedStyle ? root.getComputedStyle(el) : null;
    // Prefer the authored color before we make text transparent for clipping.
    var raw = (el.dataset && el.dataset.inkColor) || (cs && cs.color) || '';
    return parseCssColor(raw);
  }

  function rememberInkColor(el) {
    if (!el.dataset.inkColor) {
      var cs = root.getComputedStyle ? root.getComputedStyle(el) : null;
      if (cs && cs.color) el.dataset.inkColor = cs.color;
    }
  }

  function applyTextWash(el, progress, rtl, restingAlpha) {
    rememberInkColor(el);
    var rest = restingAlpha == null ? DEFAULT_RESTING_ALPHA : restingAlpha;
    var ink = resolveInkColor(el);
    var width = el.offsetWidth || el.getBoundingClientRect().width;
    if (!(width > 0)) {
      // Hold upcoming ink until layout.
      el.style.color = rgba(ink, rest);
      el.style.webkitTextFillColor = '';
      el.style.backgroundImage = '';
      el.style.backgroundClip = '';
      el.style.webkitBackgroundClip = '';
      return;
    }
    var fill = buildWashFill(progress, width, !!rtl, rest, ink);
    if (!fill) {
      clearWash(el);
      return;
    }
    // background-clip:text paints the Fade.kt wash into the glyphs themselves —
    // reliable in Firefox Android, unlike mask-image on text.
    el.style.backgroundImage = fill;
    el.style.backgroundSize = '100% 100%';
    el.style.backgroundRepeat = 'no-repeat';
    el.style.webkitBackgroundClip = 'text';
    el.style.backgroundClip = 'text';
    el.style.color = 'transparent';
    el.style.webkitTextFillColor = 'transparent';
  }

  function applyAtomWash(el, progress, restingAlpha) {
    var rest = restingAlpha == null ? DEFAULT_RESTING_ALPHA : restingAlpha;
    var p = progress < 0 ? 0 : progress > 1 ? 1 : progress;
    // Whole-word breath for non-text: smootherstep from resting → full.
    var a = rest + (1 - rest) * inkSmootherstep(p);
    el.style.opacity = String(a);
  }

  function isTexty(el) {
    // Atomic media / SVG — opacity path. Everything else gets the glyph wash.
    if (!el || !el.matches) return true;
    return !el.matches('svg, img, .rosette, .screenshots figure');
  }

  function applyWash(el, progress, rtl, restingAlpha) {
    if (isTexty(el)) applyTextWash(el, progress, rtl, restingAlpha);
    else applyAtomWash(el, progress, restingAlpha);
  }

  function clearWash(el) {
    el.style.backgroundImage = '';
    el.style.backgroundSize = '';
    el.style.backgroundRepeat = '';
    el.style.backgroundClip = '';
    el.style.webkitBackgroundClip = '';
    el.style.color = '';
    el.style.webkitTextFillColor = '';
    el.style.opacity = '';
    el.style.webkitMaskImage = '';
    el.style.maskImage = '';
    el.style.maskMode = '';
  }

  /**
   * Animate the ink wash from progress 0 → 1.
   * Progress advances linearly with time (like audio position / duration in the
   * app); the smootherstep lives in the feather, not in the clock.
   */
  function animateWash(el, opts) {
    opts = opts || {};
    var duration = opts.duration != null ? opts.duration : 360;
    var rtl = !!opts.rtl;
    var resting =
      opts.restingAlpha != null ? opts.restingAlpha : DEFAULT_RESTING_ALPHA;
    var start = null;
    var raf = 0;

    function frame(now) {
      if (start == null) start = now;
      var t = Math.min(1, (now - start) / duration);
      applyWash(el, t, rtl, resting);
      if (t < 1) {
        raf = root.requestAnimationFrame(frame);
      } else {
        clearWash(el);
        if (opts.onDone) opts.onDone();
      }
    }

    applyWash(el, 0, rtl, resting);
    raf = root.requestAnimationFrame(frame);
    return function cancel() {
      if (raf) root.cancelAnimationFrame(raf);
    };
  }

  root.BeautifulQuranInk = {
    inkSmootherstep: inkSmootherstep,
    washAlphaAt: washAlphaAt,
    buildWashFill: buildWashFill,
    buildWashMask: buildWashMask,
    applyWash: applyWash,
    clearWash: clearWash,
    animateWash: animateWash,
    INK_PROFILE_STOPS: INK_PROFILE_STOPS,
    INK_WASH_FEATHER: INK_WASH_FEATHER,
    DEFAULT_RESTING_ALPHA: DEFAULT_RESTING_ALPHA,
  };
})(typeof window !== 'undefined' ? window : this);
