/* Ink bloom on arrival.
 *
 * The website's echo of the app's signature word-by-word reveal: each block of
 * the sheet rests at "upcoming" ink (styles.css) and fades to full ink as it
 * scrolls into view, on the same smootherstep curve the highlight engine uses
 * (Fade.kt — letterFadeIn). Opacity only — no float or translate — matching
 * how the engine writes ink onto the page.
 *
 * The resting state is CSS, gated on the `.js` class set in the page <head>
 * before first paint, so there is never a flash of full-ink content that then
 * hides. All this script does is lift each block to full ink (`.inked`) the
 * moment it arrives, top-to-bottom, so a screenful blooms as one gentle fade
 * in the reading direction.
 */
(function () {
  'use strict';

  // Keep this list in step with the reveal selector in styles.css.
  var SELECTOR = [
    '.arabic-title', '.app-title', '.subtitle', '.rosette',
    '.sheet > .tagline', '.sheet > p', '.sheet > h1', '.sheet > h2',
    '.sheet > .actions', '.sheet > ul li', '.screenshots figure',
    '.attribution p', '.sheet > .footer-links', '.sheet > .back-link'
  ].join(',');

  var blocks = Array.prototype.slice.call(document.querySelectorAll(SELECTOR));
  if (!blocks.length) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // No observer, or the reader prefers no motion: settle everything at full ink
  // immediately. (With reduced motion the resting state isn't applied anyway,
  // but adding `.inked` keeps the DOM in one consistent, fully-revealed state.)
  if (reduceMotion || !('IntersectionObserver' in window)) {
    blocks.forEach(function (el) { el.classList.add('inked'); });
    return;
  }

  var observer = new IntersectionObserver(function (entries, obs) {
    // Reveal the blocks crossing in together top-to-bottom, each a beat after
    // the one above it, so a screenful blooms as a single downward sweep.
    entries
      .filter(function (entry) { return entry.isIntersecting; })
      .sort(function (a, b) {
        return a.boundingClientRect.top - b.boundingClientRect.top;
      })
      .forEach(function (entry, i) {
        var el = entry.target;
        el.style.transitionDelay = Math.min(i * 70, 320) + 'ms';
        el.classList.add('inked');
        obs.unobserve(el);
      });
  }, { rootMargin: '0px 0px -8% 0px', threshold: 0.15 });

  blocks.forEach(function (el) { observer.observe(el); });
})();
