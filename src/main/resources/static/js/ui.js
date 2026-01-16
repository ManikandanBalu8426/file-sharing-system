/*
  Shared UI helpers (no external libs)
  - Adds html.js + html.is-ready for page entrance transitions
  - Toast system (window.UI.toast)
  - Safe helpers for loading states
*/

(() => {
  const root = document.documentElement;
  root.classList.add('js');

  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function ensureToastHost(){
    let host = document.querySelector('.toast-host');
    if (!host){
      host = document.createElement('div');
      host.className = 'toast-host';
      host.setAttribute('aria-live', 'polite');
      host.setAttribute('aria-relevant', 'additions');
      document.body.appendChild(host);
    }
    return host;
  }

  function iconFor(type){
    if (type === 'ok') return '✓';
    if (type === 'warn') return '!';
    if (type === 'err') return '×';
    return 'i';
  }

  function toast({ title = 'Notice', message = '', type = 'ok', timeout = 3200 } = {}){
    const host = ensureToastHost();
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.setAttribute('role', 'status');

    el.innerHTML = `
      <div aria-hidden="true" style="font-weight:700;opacity:.95">${iconFor(type)}</div>
      <div>
        <div class="title">${escapeHtml(title)}</div>
        ${message ? `<div class="msg">${escapeHtml(message)}</div>` : ''}
      </div>
      <button class="x" type="button" aria-label="Dismiss">✕</button>
    `;

    const close = () => {
      el.classList.add('is-out');
      window.setTimeout(() => el.remove(), 260);
    };

    el.querySelector('.x').addEventListener('click', close);

    host.appendChild(el);
    // Animate in next frame for smoothness.
    requestAnimationFrame(() => el.classList.add('is-in'));

    if (timeout > 0) window.setTimeout(close, timeout);
  }

  function setButtonLoading(btn, isLoading, label){
    if (!btn) return;
    btn.disabled = !!isLoading;
    btn.setAttribute('aria-busy', String(!!isLoading));
    if (label) btn.dataset.loadingLabel = label;

    if (isLoading){
      if (!btn.dataset.originalText) btn.dataset.originalText = btn.textContent.trim();
      btn.textContent = btn.dataset.loadingLabel || 'Working…';
      btn.style.opacity = '0.9';
    } else {
      if (btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
      btn.style.opacity = '';
    }
  }

  function escapeHtml(str){
    return String(str)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  // Mark ready after first paint.
  window.addEventListener('DOMContentLoaded', () => {
    if (prefersReducedMotion) {
      root.classList.add('is-ready');
      return;
    }
    requestAnimationFrame(() => root.classList.add('is-ready'));
  });

  window.UI = { toast, setButtonLoading };
})();
