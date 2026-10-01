(() => {
  const sections = [...document.querySelectorAll('.admin-function-page')];
  const links = [...document.querySelectorAll('.admin-main-menu a')];
  if (links.length === 0) return;

  const aliases = new Map([['halbjahre', 'schuljahr'], ['module', 'system']]);
  const routeSections = new Map([
    ['/manage_students', 'schuldaten'], ['/manage_teachers', 'schuldaten'],
    ['/manage_classes', 'schuldaten'], ['/manage_subjects', 'schuldaten']
  ]);
  const sectionId = id => id === 'curriculum' ? 'curriculum-admin' : id;
  const normalize = id => aliases.get(id) || id || 'uebersicht';

  function show(requestedId) {
    if (sections.length === 0) {
      const target = routeSections.get(location.pathname);
      if (!target) return;
      links.forEach(link => {
        if (link.classList.contains('admin-main-menu-logout')) link.removeAttribute('aria-current');
        else link.setAttribute('aria-current', normalize(new URL(link.getAttribute('href'), location.href).hash.slice(1)) === target ? 'page' : 'false');
      });
      return;
    }
    const requested = normalize(requestedId);
    const target = sections.some(section => section.id === sectionId(requested)) ? requested : 'uebersicht';
    sections.forEach(section => { section.hidden = section.id !== sectionId(target); });

    links.forEach(link => {
      if (link.classList.contains('admin-main-menu-logout')) {
        link.removeAttribute('aria-current');
        return;
      }
      const hash = new URL(link.getAttribute('href'), location.href).hash.slice(1);
      link.setAttribute('aria-current', normalize(hash) === target ? 'page' : 'false');
    });

    if (aliases.has(requestedId)) history.replaceState(null, '', `#${target}`);
  }

  window.addEventListener('hashchange', () => show(location.hash.slice(1)));
  show(location.hash.slice(1));
})();
