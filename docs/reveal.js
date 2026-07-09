/* Ink bloom on arrival — driven by the Fade.kt letterFadeIn port (ink-fade.js).
 *
 * Each block rests at upcoming ink and is written onto the page with the same
 * directional ink wash the reader uses for the active word: a wide smootherstep
 * feather sweeping in the reading direction (RTL for Arabic, LTR otherwise).
 *
 * Progress is not locked to audio word timings — it advances on an accelerated
 * clock so the marketing page feels fluid — but the spatial wash is a one-to-one
 * reimplementation of ui/theme/Fade.kt.
 *
 * The resting state is CSS, gated on the `.js` class set in the page <head>
 * before first paint. This script lifts each block with the real wash (`.inked`
 * when finished) as it scrolls into view, top-to-bottom.
 */
(function () {
  'use strict';

  var Ink = window.BeautifulQuranInk;
  if (!Ink) return;

  // Keep this list in step with the reveal selector in styles.css.
  var SELECTOR = [
    '.arabic-title', '.app-title', '.subtitle', '.rosette',
    '.sheet > .tagline', '.sheet > p', '.sheet > h1', '.sheet > h2',
    '.sheet > .actions', '.sheet > ul li', '.screenshots figure',
    '.attribution p', '.sheet > .footer-links', '.sheet > .back-link'
  ].join(',');

  // Accelerated vs word-timed audio: a fluid wash, not a recited dwell.
  var WASH_MS = 320;
  var STAGGER_MS = 55;
  var STAGGER_CAP_MS = 240;

  var blocks = Array.prototype.slice.call(document.querySelectorAll(SELECTOR));
  if (!blocks.length) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function isRtl(el) {
    if (el.classList.contains('arabic-title')) return true;
    var dir = (el.getAttribute('dir') || '').toLowerCase();
    if (dir === 'rtl') return true;
    return false;
  }

  function settle(el) {
    Ink.clearWash(el);
    el.classList.remove('ink-washing');
    el.classList.add('inked');
  }

  if (reduceMotion || !('IntersectionObserver' in window)) {
    blocks.forEach(settle);
    return;
  }

  var observer = new IntersectionObserver(function (entries, obs) {
    // Reveal blocks crossing in together top-to-bottom, each a beat after the
    // one above — same reading-direction lead as the word wash.
    entries
      .filter(function (entry) { return entry.isIntersecting; })
      .sort(function (a, b) {
        return a.boundingClientRect.top - b.boundingClientRect.top;
      })
      .forEach(function (entry, i) {
        var el = entry.target;
        obs.unobserve(el);
        if (el.classList.contains('inked') || el.classList.contains('ink-washing')) {
          return;
        }

        var delay = Math.min(i * STAGGER_MS, STAGGER_CAP_MS);
        window.setTimeout(function () {
          if (el.classList.contains('inked')) return;
          // Mask at progress 0 first (upcoming floor), then lift opacity —
          // same as letterFadeIn drawing content under a resting DstIn wash.
          Ink.applyWash(el, 0, isRtl(el), Ink.DEFAULT_RESTING_ALPHA);
          el.classList.add('ink-washing');
          Ink.animateWash(el, {
            duration: WASH_MS,
            rtl: isRtl(el),
            restingAlpha: Ink.DEFAULT_RESTING_ALPHA,
            onDone: function () { settle(el); },
          });
        }, delay);
      });
  }, { rootMargin: '0px 0px -8% 0px', threshold: 0.15 });

  blocks.forEach(function (el) { observer.observe(el); });
})();
