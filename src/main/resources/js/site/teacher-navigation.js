(() => {
    const dashboardSections = new Set(['overview', 'curriculum', 'student-progress', 'schoolwide-stage-overview', 'tutor-area']);
    const tutorAreaPath = '/dashboard#tutor-area';
    let tutorLookupStarted = false;

    function setTutorAreaAccess(allowed) {
        window.tutorAreaAccess = allowed;
        window.dispatchEvent(new CustomEvent('tutor-area-access', {detail: allowed}));
    }

    function tutorAreaItem(navigation) {
        const existingLink = [...navigation.querySelectorAll('a[href]')]
            .find(link => link.getAttribute('href') === tutorAreaPath);
        if (existingLink) return existingLink.closest('li') || existingLink;

        const studentProgressLink = [...navigation.querySelectorAll('a[href]')]
            .find(link => link.getAttribute('href') === '/dashboard#student-progress');
        if (!studentProgressLink) return null;

        const item = document.createElement('li');
        item.dataset.teacherNavItem = 'tutor';
        const link = document.createElement('a');
        link.href = tutorAreaPath;
        link.textContent = 'Tutorenbereich';
        item.append(link);
        studentProgressLink.closest('li')?.after(item);
        return item;
    }

    function schoolwideOverviewItem(navigation) {
        if ([...navigation.querySelectorAll('a[href]')].some(link => link.getAttribute('href') === '/dashboard#schoolwide-stage-overview')) return;
        const progress = [...navigation.querySelectorAll('a[href]')]
            .find(link => link.getAttribute('href') === '/dashboard#student-progress');
        if (!progress) return;
        const item = document.createElement('li');
        item.dataset.teacherNavItem = 'schoolwide';
        const link = document.createElement('a'); link.href = '/dashboard#schoolwide-stage-overview'; link.textContent = 'Gelingensnachweise';
        item.append(link); progress.closest('li')?.after(item);
    }

    function removeFreistellungItems() {
        document.querySelectorAll('[data-freistellung-nav]').forEach(element => {
            if (element.matches('li')) element.remove();
        });
    }

    function stabilizeTeacherNavigation(navigation) {
        const list = navigation?.querySelector('.teacher-main-menu-sections > ul, .teacher-main-menu > ul');
        if (!list) return;
        const byHref = href => [...list.querySelectorAll(`a[href="${href}"]`)].map(link => link.closest('li') || link).filter(Boolean);
        const dynamic = [
            ...byHref(tutorAreaPath),
            ...byHref('/dashboard#schoolwide-stage-overview'),
            ...[...list.querySelectorAll('li[data-freistellung-nav]')]
                .sort((a, b) => ({apply: 0, orga: 1, sl: 2}[a.dataset.freistellungNav] ?? 99) - ({apply: 0, orga: 1, sl: 2}[b.dataset.freistellungNav] ?? 99))
        ];
        const progress = [...list.querySelectorAll('a[href]')]
            .find(link => link.getAttribute('href') === '/dashboard#student-progress')?.closest('li');
        if (!progress) return;
        let anchor = progress;
        for (const item of dynamic) {
            if (item === anchor || item.parentElement !== list) continue;
            anchor.after(item);
            anchor = item;
        }
        const order = {
            '/dashboard#overview': 10,
            '/dashboard#curriculum': 20,
            '/dashboard#student-progress': 30,
            tutor: 40,
            schoolwide: 50,
            apply: 60,
            orga: 70,
            sl: 80,
            '/attendance': 90
        };
        [...list.children].forEach((item, index) => {
            const link = item.querySelector('a[href]');
            const key = item.dataset.teacherNavItem || item.dataset.freistellungNav || link?.getAttribute('href');
            item.style.order = String(item.classList.contains('teacher-main-menu-logout-item') ? 100 : (order[key] ?? 200 + index));
        });
    }

    async function updateFreistellungVisibility(navigation) {
        if (!navigation) return;
        const updateToken = String(Number(navigation.dataset.freistellungUpdateToken || 0) + 1);
        navigation.dataset.freistellungUpdateToken = updateToken;
        removeFreistellungItems();
        try {
            const response = await fetch('/freistellung-role-info', {
                method: 'POST', headers: {'Content-Type': 'application/json'}, body: '{}',
                credentials: 'same-origin', cache: 'no-store'
            });
            if (!response.ok) return;
            const rights = await response.json();
            if (navigation.dataset.freistellungUpdateToken !== updateToken) return;
            const items = [
                rights.apply && ['/freistellung', 'Freistellungen', 'apply'],
                rights.orga && ['/freistellung-bearbeitung', 'Freistellungen – Orga-Leitung', 'orga'],
                rights.sl && ['/freistellung-bearbeitung', 'Freistellungen – Schulleitung', 'sl']
            ].filter(Boolean);
            const list = navigation.querySelector('.teacher-main-menu-sections > ul, .teacher-main-menu > ul');
            if (!list) return;
            const logout = list.querySelector('.teacher-main-menu-logout-item');
            for (const [href, label, kind] of items) {
                const item = document.createElement('li');
                item.dataset.freistellungNav = kind;
                item.dataset.teacherNavItem = kind;
                const link = document.createElement('a'); link.href = href; link.textContent = label; link.dataset.freistellungNav = kind;
                item.append(link);
                if (logout) logout.before(item); else list.append(item);
            }
            stabilizeTeacherNavigation(navigation);
            markActive();
        } catch { /* fehlendes Freistellungsmodul darf das Dashboard nicht brechen */ }
    }

    function removeTutorAreaItem() {
        document.querySelectorAll(`a[href="${tutorAreaPath}"]`).forEach(link => {
            const item = link.closest('li');
            if (item) item.remove();
            else link.remove();
        });
    }

    function integrateTeacherLogout(navigation) {
        if (navigation.querySelector('.teacher-main-menu-logout-item')) return;

        const form = document.querySelector(
            'body > header form[action="/logout"][method="post"]'
        );
        const list = navigation.querySelector(
            '.teacher-main-menu-sections > ul, .teacher-main-menu > ul'
        );
        if (!form || !list) return;

        const header = form.closest('header');
        const item = document.createElement('li');
        item.className = 'teacher-main-menu-logout-item';
        item.append(form);
        list.append(item);

        if (header && header !== navigation && header.children.length === 0) {
            header.remove();
        }
    }

    async function updateTutorAreaVisibility(navigation) {
        try {
            const response = await fetch('/my-tutor-classes', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: '{}'
            });
            if (!response.ok) throw new Error(`Tutor-Kontext konnte nicht geladen werden (${response.status}).`);
            const classes = await response.json();
            const allowed = Array.isArray(classes) && classes.length > 0;
            if (allowed) tutorAreaItem(navigation);
            else removeTutorAreaItem();
            stabilizeTeacherNavigation(navigation);
            setTutorAreaAccess(allowed);
            markActive();
        } catch {
            removeTutorAreaItem();
            setTutorAreaAccess(false);
            markActive();
        }
    }

    async function adminAttendanceNavigation() {
        if (window.location.pathname !== '/attendance') return false;
        try {
            const response = await fetch('/dashboard', {credentials: 'same-origin', cache: 'no-store'});
            if (!response.ok) return false;
            const html = await response.text();
            const documentFragment = new DOMParser().parseFromString(html, 'text/html');
            const navigation = documentFragment.querySelector('.admin-main-menu');
            return navigation ? navigation.cloneNode(true) : false;
        } catch {
            return false;
        }
    }

    function activeDestination() {
        if (location.pathname === '/attendance') return '/attendance';
        if (location.pathname === '/freistellung') return '/freistellung';
        if (location.pathname === '/freistellung-bearbeitung') return '/freistellung-bearbeitung';
        if (location.pathname === '/student') return '/dashboard#student-progress';
        if (location.pathname !== '/dashboard') return null;

        const section = location.hash.slice(1);
        return `/dashboard#${dashboardSections.has(section) ? section : 'overview'}`;
    }

    function markActive() {
        const destination = activeDestination();
        if (!destination) return;

        document.querySelectorAll('.teacher-main-menu').forEach(navigation => {
            navigation.querySelectorAll('a[href]').forEach(link => {
                const target = new URL(link.getAttribute('href'), window.location.origin);
                const linkDestination = `${target.pathname}${target.hash}`;
                if (linkDestination === destination) link.setAttribute('aria-current', 'page');
                else link.removeAttribute('aria-current');
            });
        });
    }

    function applyTeacherNavigation(attendanceNavigation) {
        if (window.location.pathname === '/attendance' && attendanceNavigation && !attendanceNavigation.classList.contains('admin-main-menu')) {
            attendanceNavigation.classList.add('teacher-main-menu');
            attendanceNavigation.setAttribute('aria-label', 'Lehrkraftbereiche');
        }

        const navigation = document.querySelector('.teacher-main-menu');
        if (navigation) {
            if (navigation.dataset.teacherNavigationStarted === 'true') {
                markActive();
                return;
            }
            navigation.dataset.teacherNavigationStarted = 'true';
            integrateTeacherLogout(navigation);
            schoolwideOverviewItem(navigation);
            stabilizeTeacherNavigation(navigation);
            const tutorPromise = !tutorLookupStarted
                ? (tutorLookupStarted = true, Promise.race([
                    updateTutorAreaVisibility(navigation),
                    new Promise(resolve => window.setTimeout(resolve, 3000))
                ]))
                : Promise.resolve();
            Promise.all([updateFreistellungVisibility(navigation), tutorPromise]).finally(() => {
                stabilizeTeacherNavigation(navigation);
                navigation.dataset.teacherNavigationReady = 'true';
                markActive();
            });
        }

        markActive();
    }

    // Die Attendance-Seite liefert die Klassenwahl als offene Mehrfachliste (<select multiple>).
    // Sie wird durch eine aufklappbare Liste mit Häkchen ersetzt; das Original-Select bleibt
    // versteckt im Formular und wird synchron gehalten, damit attendance.js unverändert funktioniert.
    function enhanceAttendanceClassFilter() {
        const select = document.querySelector('main.attendance #filters select#classes[multiple]');
        if (!select || select.dataset.enhanced) return;
        select.dataset.enhanced = 'true';

        const label = select.closest('label');
        const wrapper = document.createElement('div');
        wrapper.className = 'attendance-class-filter';
        const caption = document.createElement('span');
        caption.className = 'attendance-class-filter-caption';
        caption.id = 'attendance-class-filter-caption';
        caption.textContent = 'Klassen';

        const picker = document.createElement('details');
        picker.className = 'attendance-class-picker';
        const summary = document.createElement('summary');
        summary.setAttribute('aria-labelledby', `${caption.id} attendance-class-picker-value`);
        const value = document.createElement('span');
        value.id = 'attendance-class-picker-value';
        summary.append(value);
        const panel = document.createElement('div');
        panel.className = 'attendance-class-picker-panel';
        panel.setAttribute('role', 'group');
        panel.setAttribute('aria-labelledby', caption.id);
        picker.append(summary, panel);

        wrapper.append(caption, picker);
        (label || select).before(wrapper);
        wrapper.append(select);
        if (label) label.remove();
        select.hidden = true;
        select.tabIndex = -1;

        function updateSummary() {
            const chosen = [...select.selectedOptions].map(option => option.textContent.trim());
            if (chosen.length === 0) value.textContent = 'Alle Klassen';
            else if (chosen.length <= 3) value.textContent = chosen.join(', ');
            else value.textContent = `${chosen.slice(0, 3).join(', ')} +${chosen.length - 3}`;
        }

        function syncPanel() {
            const options = [...select.options];
            const boxes = [...panel.querySelectorAll('input[type="checkbox"]')];
            const sameOptions = boxes.length === options.length
                && boxes.every((box, index) => box.value === options[index].value);
            if (!sameOptions) {
                panel.replaceChildren(...options.map((option, index) => {
                    const item = document.createElement('label');
                    const box = document.createElement('input');
                    box.type = 'checkbox';
                    box.value = option.value;
                    box.dataset.index = String(index);
                    item.append(box, document.createTextNode(option.textContent.trim()));
                    return item;
                }));
            }
            panel.querySelectorAll('input[type="checkbox"]').forEach((box, index) => {
                box.checked = select.options[index].selected;
            });
            updateSummary();
        }

        panel.addEventListener('change', event => {
            const box = event.target;
            if (!(box instanceof HTMLInputElement)) return;
            const option = select.options[Number(box.dataset.index)];
            if (!option) return;
            option.selected = box.checked;
            updateSummary();
            select.dispatchEvent(new Event('input', {bubbles: true}));
            select.dispatchEvent(new Event('change', {bubbles: true}));
        });

        // attendance.js befüllt die Optionen und setzt gespeicherte Filter per Skript,
        // ohne Ereignisse auszulösen; deshalb wird bei jeder Seitenaktualisierung nachgezogen.
        const resync = () => setTimeout(syncPanel, 0);
        new MutationObserver(resync).observe(select, {childList: true, subtree: true, attributes: true});
        const main = select.closest('main');
        if (main) new MutationObserver(resync).observe(main.querySelector('#rows') || main, {childList: true});
        select.addEventListener('change', syncPanel);
        select.form?.addEventListener('reset', resync);
        select.form?.addEventListener('click', event => {
            if (event.target.closest?.('button')) resync();
        });
        picker.addEventListener('toggle', syncPanel);

        document.addEventListener('click', event => {
            if (picker.open && !picker.contains(event.target)) picker.open = false;
        });
        picker.addEventListener('keydown', event => {
            if (event.key === 'Escape' && picker.open) {
                picker.open = false;
                summary.focus();
            }
        });

        syncPanel();
    }

    function updateTeacherNavigation() {
        const attendanceNavigation = document.querySelector('main.attendance > nav');
        if (window.location.pathname !== '/attendance') {
            applyTeacherNavigation(attendanceNavigation);
            return;
        }
        enhanceAttendanceClassFilter();
        adminAttendanceNavigation().then(adminNavigation => {
            if (adminNavigation) {
                const attendance = document.querySelector('main.attendance');
                const currentNavigation = attendance?.querySelector(':scope > nav');
                if (attendance && currentNavigation) {
                    currentNavigation.replaceWith(adminNavigation);
                    attendance.classList.remove('teacher-dashboard');
                    attendance.classList.add('admin-dashboard-page');
                }
                setTutorAreaAccess(false);
                return;
            }
            applyTeacherNavigation(attendanceNavigation);
        });
    }

    document.addEventListener('DOMContentLoaded', updateTeacherNavigation);
    window.addEventListener('hashchange', updateTeacherNavigation);
})();
