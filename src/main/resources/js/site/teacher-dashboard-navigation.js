(() => {
  const pages = [...document.querySelectorAll('.teacher-function-page')];
  const valid = new Set(pages.map(page => page.id));
  let tutorAreaAllowed = window.tutorAreaAccess === true;
  const show = hash => {
    const requested = hash || 'overview';
    const active = valid.has(requested) && (requested !== 'tutor-area' || tutorAreaAllowed) ? requested : 'overview';
    pages.forEach(page => { page.hidden = page.id !== active; });
    document.querySelectorAll('.teacher-main-menu a').forEach(link => {
      const linkHash = new URL(link.getAttribute('href'), location.href).hash.slice(1);
      if (valid.has(linkHash)) link.setAttribute('aria-current', linkHash === active ? 'page' : 'false');
      else link.removeAttribute('aria-current');
    });
  };
  const select = () => show(location.hash.slice(1));
  window.addEventListener('tutor-area-access', event => {
    tutorAreaAllowed = event.detail === true;
    select();
  });
  window.addEventListener('hashchange', select);
  select();
})();
