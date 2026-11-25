// Tha tim / chia se danh gia (trang Khach noi gi, trang 1 danh gia, trang chi tiet phong, trinh xem anh).
// Nut: <button class="rv-like" data-rv-id="..">, <button class="rv-share" data-rv-id="..">. Khach chua dang nhap bam tim -> /login
(function () {
    if (window.RvSocial) return;
    var EN = (document.documentElement.lang || 'vi').indexOf('en') === 0;
    var COPIED = EN ? 'Link copied' : 'Đã chép link';

    function csrf() {
        var t = document.querySelector('meta[name="_csrf"]');
        var h = document.querySelector('meta[name="_csrf_header"]');
        if (t && h) return { header: h.content, token: t.content };
        var r = document.querySelector('input.rv-csrf');
        if (r && r.value) return { header: r.dataset.header, token: r.value };
        var ti = document.getElementById('chatbotCsrfToken');
        var hi = document.getElementById('chatbotCsrfHeader');
        if (ti && hi && ti.value) return { header: hi.value, token: ti.value };
        return null;
    }

    function post(url) {
        var headers = { 'X-Requested-With': 'XMLHttpRequest' };
        var c = csrf();
        if (c) headers[c.header] = c.token;
        return fetch(url, { method: 'POST', headers: headers, credentials: 'same-origin' });
    }

    function anonymous() {
        return !!document.querySelector('[data-rv-anon="true"]');
    }

    // Cap nhat moi noi hien tim cua danh gia nay tren trang
    function paint(id, likes, liked, comments) {
        document.querySelectorAll('[data-rv-gallery][data-rv-id="' + id + '"]').forEach(function (g) {
            g.dataset.rvLikes = likes;
            g.dataset.rvLiked = liked;
            if (comments != null) g.dataset.rvComments = comments;
        });
        document.querySelectorAll('.rv-like[data-rv-id="' + id + '"]').forEach(function (b) {
            b.classList.toggle('liked', !!liked);
            b.setAttribute('aria-pressed', liked ? 'true' : 'false');
            var n = b.querySelector('.n');
            if (n) n.textContent = likes;
            var i = b.querySelector('i');
            if (i) i.className = liked ? 'bi bi-heart-fill' : 'bi bi-heart';
        });
    }

    function like(id) {
        if (anonymous()) {
            location.href = '/login';
            return Promise.resolve();
        }
        return post('/reviews/' + id + '/like').then(function (res) {
            if (res.status === 401 || res.status === 403 || res.redirected) { location.href = '/login'; return; }
            if (!res.ok) return;
            return res.json().then(function (d) { paint(id, d.likes, d.liked, d.comments); });
        }).catch(function () { /* bo qua */ });
    }

    function link(id, i) {
        return location.origin + '/reviews/' + id + (i != null && i >= 0 ? '?m=' + i : '');
    }

    // Tra ve Promise<true> neu da chep link (de hien "Da chep link")
    function share(id, i, channel) {
        var url = link(id, i);
        if (channel === 'facebook') {
            post('/reviews/' + id + '/share?channel=facebook');
            window.open('https://www.facebook.com/sharer/sharer.php?u=' + encodeURIComponent(url), '_blank', 'noopener,width=620,height=520');
            return Promise.resolve(false);
        }
        if (navigator.share) {
            return navigator.share({ title: document.title, url: url }).then(function () {
                post('/reviews/' + id + '/share?channel=native');
                return false;
            }).catch(function () { return false; });
        }
        post('/reviews/' + id + '/share?channel=link');
        var done = function () { return true; };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            return navigator.clipboard.writeText(url).then(done).catch(function () { window.prompt(COPIED, url); return false; });
        }
        window.prompt(COPIED, url);
        return Promise.resolve(false);
    }

    function toast(btn, text) {
        var old = btn.getAttribute('data-label') || btn.innerHTML;
        btn.setAttribute('data-label', old);
        btn.textContent = '✓ ' + text;
        setTimeout(function () { btn.innerHTML = old; }, 1600);
    }

    document.addEventListener('click', function (e) {
        var b = e.target.closest && e.target.closest('.rv-like[data-rv-id]');
        if (b) { e.preventDefault(); like(b.dataset.rvId); return; }
        var s = e.target.closest && e.target.closest('.rv-share[data-rv-id]');
        if (s) {
            e.preventDefault();
            share(s.dataset.rvId, null, s.dataset.channel).then(function (copied) { if (copied) toast(s, COPIED); });
        }
    });

    window.RvSocial = { like: like, share: share };
})();
