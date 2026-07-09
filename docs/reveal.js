/* Ink bloom on arrival — word-by-word letterFadeIn (ink-fade.js / Fade.kt).
 *
 * Text is split into per-word spans. As each line scrolls into view, words are
 * written with the highlight-engine wash in reading order, staggered 50 ms
 * apart — the same karaoke cadence as the reader, accelerated (not audio-timed).
 *
 * Non-text atoms (rosette, screenshots) wash as a single unit. Arabic runs RTL.
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

  /** Stagger between successive words — user-requested karaoke beat. */
  var WORD_STAGGER_MS = 50;
  /** Accelerated wash duration per word (visible feather, not audio dwell). */
  var WASH_MS = 240;

  var blocks = Array.prototype.slice.call(document.querySelectorAll(BLOCK_SELECTOR));
  if (!blocks.length) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function isRtl(el) {
    if (el.classList && el.classList.contains('arabic-title')) return true;
    if (el.closest && el.closest('.arabic-title')) return true;
    var dir = (el.getAttribute && el.getAttribute('dir') || '').toLowerCase();
    if (dir === 'rtl') return true;
    return false;
  }

  function isAtomic(el) {
    return el.matches('.rosette, .screenshots figure');
  }

  /**
   * Split text nodes under [root] into <span class="ink-word">…</span>,
   * preserving whitespace and nested markup (links, strong, etc.).
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
      if (parts.length === 1 && !/\s/.test(parts[0])) {
        var alone = document.createElement('span');
        alone.className = 'ink-word';
        alone.textContent = parts[0];
        textNode.parentNode.replaceChild(alone, textNode);
        return;
      }
      var frag = document.createDocumentFragment();
      parts.forEach(function (part) {
        if (!part) return;
        if (/^\s+$/.test(part)) {
          frag.appendChild(document.createTextNode(part));
        } else {
          var span = document.createElement('span');
          span.className = 'ink-word';
          span.textContent = part;
          frag.appendChild(span);
        }
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

  function washUnit(el, delay) {
    window.setTimeout(function () {
      if (el.classList.contains('inked')) return;
      var rtl = isRtl(el);
      Ink.applyWash(el, 0, rtl, Ink.DEFAULT_RESTING_ALPHA);
      el.classList.add('ink-washing');
      Ink.animateWash(el, {
        duration: WASH_MS,
        rtl: rtl,
        restingAlpha: Ink.DEFAULT_RESTING_ALPHA,
        onDone: function () { settle(el); },
      });
    }, delay);
  }

  // Wrap words immediately so CSS can rest each word at upcoming ink.
  blocks.forEach(function (block) {
    if (isAtomic(block)) return;
    wrapWords(block);
    block.classList.add('ink-prepared');
  });

  if (reduceMotion || !('IntersectionObserver' in window)) {
    blocks.forEach(function (block) {
      unitsFor(block).forEach(settle);
      block.classList.add('ink-prepared');
      block.classList.add('inked');
    });
    return;
  }

  var observer = new IntersectionObserver(function (entries, obs) {
    // Lines top-to-bottom; within each line, words in DOM/reading order at 50 ms.
    var batch = entries
      .filter(function (entry) { return entry.isIntersecting; })
      .sort(function (a, b) {
        return a.boundingClientRect.top - b.boundingClientRect.top;
      });

    var wordIndex = 0;
    batch.forEach(function (entry) {
      var block = entry.target;
      obs.unobserve(block);
      if (block.dataset.inkStarted === '1') return;
      block.dataset.inkStarted = '1';

      unitsFor(block).forEach(function (unit) {
        washUnit(unit, wordIndex * WORD_STAGGER_MS);
        wordIndex += 1;
      });
    });
  }, { rootMargin: '0px 0px -8% 0px', threshold: 0.12 });

  blocks.forEach(function (el) { observer.observe(el); });
})();
