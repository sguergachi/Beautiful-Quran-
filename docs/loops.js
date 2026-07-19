/* Decorative video loops (the theme row).
 *
 * The hero clip swaps to a still via CSS, because it has a paired <img>. The
 * theme tiles carry their own poster frame instead, so reduced motion is
 * handled here: drop autoplay and pause, and the poster stays on screen —
 * same picture, no movement, and nothing beyond the poster gets fetched.
 */
(function () {
  'use strict';

  if (
    !window.matchMedia ||
    !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  ) {
    return;
  }

  var loops = document.querySelectorAll('.screenshots--themes video');
  Array.prototype.forEach.call(loops, function (video) {
    video.removeAttribute('autoplay');
    video.removeAttribute('loop');
    video.pause();
  });
})();
