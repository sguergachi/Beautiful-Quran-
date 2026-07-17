/* Ink bloom demo — only the basmalah showcase on the product page.
 *
 * The rest of the page is fully inked from first paint. This is the one
 * place the Fade.kt breath earns its keep: it *is* the product feature.
 *
 * Words are already wrapped as .ink-word in the HTML. We park them at
 * upcoming ink, then wash them in on view (or immediately if reduced-motion).
 */
(function () {
  'use strict';

  var Ink = window.BeautifulQuranInk;
  if (!Ink) return;

  var WORD_STAGGER_MS = 220;
  var WASH_MS = 340;

  var demos = Array.prototype.slice.call(
    document.querySelectorAll('[data-ink-demo]')
  );
  if (!demos.length) return;

  var reduceMotion =
    window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function settle(el) {
    Ink.clearWash(el);
    el.classList.remove('ink-washing');
    el.classList.add('inked');
  }

  function park(el) {
    Ink.applyWash(el, 0, false, Ink.DEFAULT_RESTING_ALPHA);
  }

  function washUnit(el, delay) {
    window.setTimeout(function () {
      if (el.classList.contains('inked') || el.classList.contains('ink-washing')) {
        return;
      }
      park(el);
      el.classList.add('ink-washing');
      Ink.animateWash(el, {
        duration: WASH_MS,
        restingAlpha: Ink.DEFAULT_RESTING_ALPHA,
        onDone: function () {
          settle(el);
        },
      });
    }, delay);
  }

  function wordsOf(demo) {
    return Array.prototype.slice.call(demo.querySelectorAll('.ink-word'));
  }

  // Park every demo word at upcoming ink immediately.
  demos.forEach(function (demo) {
    wordsOf(demo).forEach(park);
  });

  if (reduceMotion || !('IntersectionObserver' in window) || !window.requestAnimationFrame) {
    demos.forEach(function (demo) {
      wordsOf(demo).forEach(settle);
    });
    return;
  }

  var observer = new IntersectionObserver(
    function (entries, obs) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var demo = entry.target;
        obs.unobserve(demo);
        if (demo.dataset.inkStarted === '1') return;
        demo.dataset.inkStarted = '1';

        wordsOf(demo).forEach(function (unit, i) {
          washUnit(unit, i * WORD_STAGGER_MS);
        });
      });
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.35 }
  );

  demos.forEach(function (el) {
    observer.observe(el);
  });
})();
