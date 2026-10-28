// Menu quan tri tu cuon: giu vi tri cuon khi chuyen trang, muc dang mo luon nam trong khung nhin
(function () {
    const nav = document.querySelector('.admin-sidebar');
    if (!nav) return;
    const KEY = 'adminSidebarScroll';
    const saved = sessionStorage.getItem(KEY);
    if (saved !== null) nav.scrollTop = +saved;
    const active = nav.querySelector('.list-group-item.active');
    if (active) {
        const a = active.getBoundingClientRect(), n = nav.getBoundingClientRect();
        if (a.top < n.top || a.bottom > n.bottom) nav.scrollTop += a.top - n.top - nav.clientHeight / 3;
    }
    window.addEventListener('pagehide', () => sessionStorage.setItem(KEY, nav.scrollTop));
})();
