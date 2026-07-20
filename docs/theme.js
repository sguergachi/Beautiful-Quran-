(function () {
  'use strict';

  var key = 'beautiful-quran-marketing-theme';
  var themes = ['light', 'dark', 'royal_green'];
  var saved;

  try {
    saved = localStorage.getItem(key);
  } catch (_) {
    saved = null;
  }

  var current = themes.indexOf(saved) >= 0
    ? saved
    : window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';

  document.documentElement.setAttribute('data-theme', current);

  function bind() {
    var buttons = document.querySelectorAll('[data-theme-choice]');

    function apply(theme, remember) {
      document.documentElement.setAttribute('data-theme', theme);
      Array.prototype.forEach.call(buttons, function (button) {
        var active = button.getAttribute('data-theme-choice') === theme;
        var label = button.getAttribute('data-theme-label');
        button.setAttribute('aria-pressed', String(active));
        button.setAttribute('aria-label', active ? label + ' theme selected' : 'Use ' + label + ' theme');
      });
      if (remember) {
        try {
          localStorage.setItem(key, theme);
        } catch (_) {}
      }
    }

    Array.prototype.forEach.call(buttons, function (button) {
      button.addEventListener('click', function () {
        apply(button.getAttribute('data-theme-choice'), true);
      });
    });
    apply(current, false);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bind);
  } else {
    bind();
  }
})();
