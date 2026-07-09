/* Beautiful Quran — web port of ui/theme/Fade.kt letterFadeIn.
 *
 * One-to-one with the Android highlight wash:
 *   - inkSmootherstep (6t⁵−15t⁴+10t³)
 *   - InkWashFeather = 1.6 (feather wider than the glyph run)
 *   - 9-stop alpha profile across the feather
 *   - progress 0..1 advances the wash head; letters ahead rest at restingAlpha,
 *     letters behind are fully inked (Compose BlendMode.DstIn → CSS mask-image)
 *
 * On the marketing site the wash is not locked to audio word timings — progress
 * advances on its own clock, accelerated — but the spatial ink effect matches.
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

  /**
   * Build a CSS mask-image linear-gradient that matches the Compose
   * horizontalGradient + DstIn wash for the given progress (0..1).
   *
   * @param {number} progress 0..1 wash head progress
   * @param {number} widthPx element width in CSS pixels
   * @param {boolean} rtl true for Arabic (wash right→left)
   * @param {number} restingAlpha upcoming-ink floor
   * @returns {string|null} CSS gradient, or null when fully inked
   */
  function buildWashMask(progress, widthPx, rtl, restingAlpha) {
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
      stops.push('rgba(0,0,0,' + a + ') ' + x + 'px');
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

  function setMask(el, mask) {
    el.style.webkitMaskImage = mask;
    el.style.maskImage = mask;
    el.style.webkitMaskSize = '100% 100%';
    el.style.maskSize = '100% 100%';
    el.style.webkitMaskRepeat = 'no-repeat';
    el.style.maskRepeat = 'no-repeat';
  }

  /** Solid upcoming-ink floor — used when layout width is not ready yet. */
  function restingMask(restingAlpha) {
    return 'linear-gradient(to right, rgba(0,0,0,' + restingAlpha + '), rgba(0,0,0,' + restingAlpha + '))';
  }

  function applyWash(el, progress, rtl, restingAlpha) {
    var rest = restingAlpha == null ? DEFAULT_RESTING_ALPHA : restingAlpha;
    var width = el.offsetWidth || el.getBoundingClientRect().width;
    if (!(width > 0)) {
      // Avoid a full-ink flash before layout: hold the upcoming floor.
      setMask(el, restingMask(rest));
      return;
    }
    var mask = buildWashMask(progress, width, !!rtl, rest);
    if (!mask) {
      clearWash(el);
      return;
    }
    setMask(el, mask);
  }

  function clearWash(el) {
    el.style.webkitMaskImage = '';
    el.style.maskImage = '';
    el.style.webkitMaskSize = '';
    el.style.maskSize = '';
    el.style.webkitMaskRepeat = '';
    el.style.maskRepeat = '';
  }

  /**
   * Animate the ink wash from progress 0 → 1.
   * Progress advances linearly with time (like audio position / duration in the
   * app); the softerstep lives in the feather, not in the clock.
   *
   * @param {HTMLElement} el
   * @param {{ duration?: number, rtl?: boolean, restingAlpha?: number, onDone?: function }} opts
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
    buildWashMask: buildWashMask,
    applyWash: applyWash,
    clearWash: clearWash,
    animateWash: animateWash,
    INK_PROFILE_STOPS: INK_PROFILE_STOPS,
    INK_WASH_FEATHER: INK_WASH_FEATHER,
    DEFAULT_RESTING_ALPHA: DEFAULT_RESTING_ALPHA,
  };
})(typeof window !== 'undefined' ? window : this);
