/* Ink bloom on arrival — word-by-word Fade.kt breath (ink-fade.js).
 *
 * Each line is split into .ink-word spans. As blocks enter the viewport,
 * words ink from upcoming → full on inkSmootherstep, staggered 50 ms —
 * karaoke cadence matching the reader, accelerated (not audio-timed).
 *
 * Per-word opacity only. No paragraph-level fade, no mask-image, no Canvas.
 * That is the path that works on Chrome + Firefox for Android.
 */
(function () {
  'use strict';

  var Ink = window.BeautifulQuranInk;
  if (!Ink) return;

  // Blocks that become word streams (or atomic units). Keep in step with CSS.
  var BLOCK_SELECTOR = [
    '.arabic-title', '.app-title', '.subtitle', '.rosette',
    '.sheet > .tagline', '.sheet > p', '.sheet > h1', '.sheet > h2',
    '.sheet > .actions', '.sheet > ul li', '.screenshots figure',
    '.attribution p', '.sheet > .footer-links', '.sheet > .back-link'
  ].join(',');

  /** Stagger between successive words — karaoke beat. */
  var WORD_STAGGER_MS = 50;
  /** Accelerated wash duration per word (visible breath, not audio dwell). */
  var WASH_MS = 280;

  var blocks = Array.prototype.slice.call(document.querySelectorAll(BLOCK_SELECTOR));
  if (!blocks.length) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function isAtomic(el) {
    return el.matches('.rosette, .screenshots figure');
  }

  /**
   * Split text nodes under [root] into <span class="ink-word">word</span>
   * with whitespace kept as sibling text nodes between them.
   *
   * Spaces must stay outside the spans: browsers collapse trailing whitespace
   * inside inline-block (and even some inline) boxes, which is what removed
   * all spacing on Android.
   */
  function wrapWords(root) {
    if (root.dataset.inkWrapped === '1') return;
    root.dataset.inkWrapped = '1';

    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    var textNodes = [];
    var node;
    while ((node = walker.nextNode())) {
      if (!node.nodeValue || !/\S/.test(node.nodeValue)) continue;
      if (node.parentElement && node.parentElement.closest('.ink-word')) continue;
      textNodes.push(node);
    }

    textNodes.forEach(function (textNode) {
      var parts = textNode.nodeValue.split(/(\s+)/);
      var frag = document.createDocumentFragment();
      parts.forEach(function (part) {
        if (!part) return;
        if (/^\s+$/.test(part)) {
          frag.appendChild(document.createTextNode(part));
          return;
        }
        var span = document.createElement('span');
        span.className = 'ink-word';
        span.textContent = part;
        frag.appendChild(span);
      });
      textNode.parentNode.replaceChild(frag, textNode);
    });
  }

  function unitsFor(block) {
    if (isAtomic(block)) return [block];
    wrapWords(block);
    var words = Array.prototype.slice.call(block.querySelectorAll('.ink-word'));
    return words.length ? words : [block];
  }

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
        onDone: function () { settle(el); },
      });
    }, delay);
  }

  // Wrap immediately and park every word at upcoming ink — ink is owned per
  // word from the first paint after JS, never by paragraph opacity.
  blocks.forEach(function (block) {
    if (isAtomic(block)) {
      park(block);
      return;
    }
    wrapWords(block);
    block.classList.add('ink-prepared');
    Array.prototype.forEach.call(block.querySelectorAll('.ink-word'), park);
  });

  if (reduceMotion || !('IntersectionObserver' in window) || !window.requestAnimationFrame) {
    blocks.forEach(function (block) {
      unitsFor(block).forEach(settle);
      block.classList.add('ink-prepared');
      block.classList.add('inked');
    });
    return;
  }

  // Global word clock so cascading lines keep a steady 50 ms karaoke beat.
  var nextWordAt = 0;

  var observer = new IntersectionObserver(function (entries, obs) {
    var batch = entries
      .filter(function (entry) { return entry.isIntersecting; })
      .sort(function (a, b) {
        return a.boundingClientRect.top - b.boundingClientRect.top;
      });

    var now = performance.now();
    if (nextWordAt < now) nextWordAt = now;

    batch.forEach(function (entry) {
      var block = entry.target;
      obs.unobserve(block);
      if (block.dataset.inkStarted === '1') return;
      block.dataset.inkStarted = '1';

      unitsFor(block).forEach(function (unit) {
        var delay = Math.max(0, nextWordAt - now);
        washUnit(unit, delay);
        nextWordAt += WORD_STAGGER_MS;
      });
    });
  }, { rootMargin: '0px 0px -6% 0px', threshold: 0.05 });

  blocks.forEach(function (el) { observer.observe(el); });
})();
