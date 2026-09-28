(() => {
    const dashboardSections = new Set(['overview', 'curriculum', 'student-progress', 'tutor-area']);
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
        const link = document.createElement('a');
        link.href = tutorAreaPath;
        link.textContent = 'Tutorenbereich';
        item.append(link);
        const attendanceLink = [...navigation.querySelectorAll('a[href]')]
            .find(link => link.getAttribute('href') === '/attendance');
        if (attendanceLink?.closest('li')) attendanceLink.closest('li').before(item);
        else studentProgressLink.closest('li')?.after(item);
        return item;
    }

    function removeTutorAreaItem() {
        document.querySelectorAll(`a[href="${tutorAreaPath}"]`).forEach(link => {
            const item = link.closest('li');
            if (item) item.remove();
            else link.remove();
        });
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
            setTutorAreaAccess(allowed);
            markActive();
        } catch {
            removeTutorAreaItem();
            setTutorAreaAccess(false);
            markActive();
        }
    }

    function activeDestination() {
        if (location.pathname === '/attendance') return '/attendance';
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
                const target = new URL(link.getAttribute('href'), location.origin);
                const linkDestination = `${target.pathname}${target.hash}`;
                if (linkDestination === destination) link.setAttribute('aria-current', 'page');
                else link.removeAttribute('aria-current');
            });
        });
    }

    function updateTeacherNavigation() {
        const attendanceNavigation = document.querySelector('main.attendance > nav:first-child');
        if (location.pathname === '/attendance' && attendanceNavigation) {
            attendanceNavigation.classList.add('teacher-main-menu');
            attendanceNavigation.setAttribute('aria-label', 'Lehrkraftbereiche');
        }

        markActive();

        const navigation = document.querySelector('.teacher-main-menu');
        if (navigation && location.pathname !== '/login' && !tutorLookupStarted) {
            tutorLookupStarted = true;
            updateTutorAreaVisibility(navigation);
        }
    }

    document.addEventListener('DOMContentLoaded', updateTeacherNavigation);
    window.addEventListener('hashchange', updateTeacherNavigation);
})();
