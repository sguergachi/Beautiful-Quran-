(function () {
  'use strict';

  var nav = document.querySelector('.top-nav');
  var title = document.querySelector('.app-title');
  if (!nav || !title) return;

  document.documentElement.classList.add('nav-reveal');

  function update() {
    nav.classList.toggle('is-visible', title.getBoundingClientRect().bottom <= 0);
  }

  update();

  if ('IntersectionObserver' in window) {
    new IntersectionObserver(update).observe(title);
  } else {
    window.addEventListener('scroll', update, { passive: true });
  }
})();
