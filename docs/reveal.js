/* Ink bloom on arrival — word-by-word letterFadeIn (ink-fade.js / Fade.kt).
 *
 * Text is split into per-word spans. As each line scrolls into view, words are
 * written with the highlight-engine wash in reading order, staggered 50 ms
 * apart — the same karaoke cadence as the reader, accelerated (not audio-timed).
 *
 * Non-text atoms (rosette, screenshots) wash as a single unit. Arabic runs RTL.
 *
 * Critical for Firefox Android: each word is its own paint target. A broken
 * whole-block opacity fade is what the user reported ("white fade of the whole
 * paragraph"); word wrapping + per-word fill washes is the fix.
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
  var WASH_MS = 280;

  var blocks = Array.prototype.slice.call(document.querySelectorAll(BLOCK_SELECTOR));
  if (!blocks.length) return;

  var reduceMotion = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function isRtl(el) {
    if (el.classList && el.classList.contains('arabic-title')) return true;
    if (el.closest && el.closest('.arabic-title')) return true;
    var dir = (el.getAttribute && el.getAttribute('dir') || '').toLowerCase();
    if (dir === 'rtl') return true;
    // Heuristic: Arabic script in the word itself.
    if (/[\u0600-\u06FF]/.test(el.textContent || '')) return true;
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
      var frag = document.createDocumentFragment();
      var i = 0;
      while (i < parts.length) {
        var part = parts[i];
        if (!part) { i += 1; continue; }
        if (/^\s+$/.test(part)) {
          // Leading / orphan whitespace stays plain (invisible between dim words
          // once trailing spaces ride with each word below).
          frag.appendChild(document.createTextNode(part));
          i += 1;
          continue;
        }
        var span = document.createElement('span');
        span.className = 'ink-word';
        var wordText = part;
        // Attach the following whitespace to this word so gaps aren't full-ink.
        if (i + 1 < parts.length && /^\s+$/.test(parts[i + 1] || '')) {
          wordText += parts[i + 1];
          i += 2;
        } else {
          i += 1;
        }
        span.textContent = wordText;
        frag.appendChild(span);
      }
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
      if (el.classList.contains('inked') || el.classList.contains('ink-washing')) {
        return;
      }
      var rtl = isRtl(el);
      // Capture authored ink before the fill wash makes color transparent.
      if (el.dataset && !el.dataset.inkColor) {
        var cs = window.getComputedStyle(el);
        if (cs && cs.color) el.dataset.inkColor = cs.color;
      }
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

  // Wrap words immediately and park each at upcoming ink (progress 0 wash).
  // Owning ink per-word from the start avoids any whole-paragraph opacity fade.
  blocks.forEach(function (block) {
    if (isAtomic(block)) return;
    wrapWords(block);
    block.classList.add('ink-prepared');
    Array.prototype.forEach.call(block.querySelectorAll('.ink-word'), function (word) {
      var cs = window.getComputedStyle(word);
      if (cs && cs.color) word.dataset.inkColor = cs.color;
      Ink.applyWash(word, 0, isRtl(word), Ink.DEFAULT_RESTING_ALPHA);
    });
  });

  if (reduceMotion || !('IntersectionObserver' in window) || !window.requestAnimationFrame) {
    blocks.forEach(function (block) {
      unitsFor(block).forEach(settle);
      block.classList.add('ink-prepared');
      block.classList.add('inked');
    });
    return;
  }

  // Global word clock across the viewport so cascading lines keep a steady
  // 50 ms karaoke beat instead of each block restarting at 0.
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
