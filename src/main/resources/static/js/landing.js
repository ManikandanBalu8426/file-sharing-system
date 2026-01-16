/*
  Landing page animation logic
  - Adds .is-loaded to trigger hero entrance
  - IntersectionObserver reveals sections once (stagger supported)
  - requestAnimationFrame updates the How-it-Works progress line smoothly
*/

(() => {
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  // Smooth anchor scrolling with motion preference respected.
  function setupSmoothAnchors() {
    document.addEventListener('click', (e) => {
      const a = e.target && e.target.closest ? e.target.closest('a[href^="#"]') : null;
      if (!a) return;

      const href = a.getAttribute('href');
      if (!href || href === '#') return;

      const target = document.querySelector(href);
      if (!target) return;

      e.preventDefault();

      const top = target.getBoundingClientRect().top + window.scrollY - 72;
      window.scrollTo({
        top,
        behavior: prefersReducedMotion ? 'auto' : 'smooth'
      });
    });
  }

  function setupRevealOnScroll() {
    const nodes = Array.from(document.querySelectorAll('[data-reveal]'));
    if (!nodes.length) return;

    // Stagger: compute a delay per element when grouped with data-stagger.
    const staggerGroups = new Map();
    nodes.forEach((el) => {
      if (!el.hasAttribute('data-stagger')) return;
      const group = el.getAttribute('data-stagger') || 'default';
      if (!staggerGroups.has(group)) staggerGroups.set(group, []);
      staggerGroups.get(group).push(el);
    });
    for (const [, els] of staggerGroups) {
      els.forEach((el, index) => {
        el.style.setProperty('--delay', `${index * 90}ms`);
      });
    }

    // Reduced motion: show immediately.
    if (prefersReducedMotion) {
      nodes.forEach((el) => el.classList.add('is-visible'));
      return;
    }

    const io = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add('is-visible');
        io.unobserve(entry.target); // trigger only once
      }
    }, { threshold: 0.18 });

    nodes.forEach((el) => io.observe(el));
  }

  function setupHowProgress() {
    const how = document.getElementById('how');
    if (!how) return;

    let rafId = 0;
    const update = () => {
      rafId = 0;
      const rect = how.getBoundingClientRect();
      const viewportH = window.innerHeight || document.documentElement.clientHeight;

      // Progress starts when top reaches 70% of viewport and ends when bottom reaches 30%.
      const start = viewportH * 0.7;
      const end = viewportH * 0.3;
      const total = (rect.height + start - end);
      const current = (start - rect.top);

      const progress = clamp(total <= 0 ? 0 : current / total, 0, 1);
      how.style.setProperty('--progress', String(progress));
    };

    const onScrollOrResize = () => {
      if (rafId) return;
      rafId = window.requestAnimationFrame(update);
    };

    window.addEventListener('scroll', onScrollOrResize, { passive: true });
    window.addEventListener('resize', onScrollOrResize);
    update();
  }

  function setupToTop() {
    const btn = document.getElementById('toTop');
    if (!btn) return;

    const toggle = () => {
      if (window.scrollY > 600) btn.classList.add('is-visible');
      else btn.classList.remove('is-visible');
    };

    window.addEventListener('scroll', toggle, { passive: true });
    toggle();

    btn.addEventListener('click', () => {
      window.scrollTo({ top: 0, behavior: prefersReducedMotion ? 'auto' : 'smooth' });
    });
  }

  // Trigger hero entrance after first paint.
  function setupLoadIn() {
    document.documentElement.classList.add('js-load');

    if (prefersReducedMotion) {
      document.documentElement.classList.add('is-loaded');
      return;
    }

    // Give the browser a frame to apply initial styles.
    requestAnimationFrame(() => {
      document.documentElement.classList.add('is-loaded');
    });
  }

  window.addEventListener('DOMContentLoaded', () => {
    setupLoadIn();
    setupSmoothAnchors();
    setupRevealOnScroll();
    setupHowProgress();
    setupToTop();
  });
})();
