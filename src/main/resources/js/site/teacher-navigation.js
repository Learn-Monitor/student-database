(() => {
    const dashboardSections = new Set(['overview', 'curriculum', 'student-progress']);

    function activeDestination() {
        if (location.pathname === '/attendance') return '/attendance';
        if (location.pathname === '/student') return '/dashboard#student-progress';
        if (location.pathname !== '/dashboard') return null;

        const section = location.hash.slice(1);
        return `/dashboard#${dashboardSections.has(section) ? section : 'overview'}`;
    }

    function updateTeacherNavigation() {
        const attendanceNavigation = document.querySelector('main.attendance > nav:first-child');
        if (location.pathname === '/attendance' && attendanceNavigation) {
            attendanceNavigation.classList.add('teacher-main-menu');
            attendanceNavigation.setAttribute('aria-label', 'Lehrkraftbereiche');
        }

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

    document.addEventListener('DOMContentLoaded', updateTeacherNavigation);
    window.addEventListener('hashchange', updateTeacherNavigation);
})();
