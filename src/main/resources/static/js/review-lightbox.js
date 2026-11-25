// Xem anh / video cua danh gia toan man hinh (dung chung moi trang co fragments/review-media :: gallery).
// - Bam 1 anh: mo trinh xem; bam vao anh dang xem KHONG thoat ma chuyen sang anh tiep theo.
// - Mo tu 1 video: video tu phat, het video tu chuyen sang video tiep theo (giong luot xem video).
// - Danh sach tiep noi qua cac danh gia tren cung trang; chon xem Tat ca / Anh / Video.
// - Thoat: nut X, phim Esc. Chuyen: mui ten, phim <- ->, vuot tren dien thoai.
// - Neu danh gia co tuong tac (data-social): tha tim, so binh luan, chia se ngay trong trinh xem.
(function () {
    if (window.__rvLightbox) return;
    window.__rvLightbox = true;

    var LANG = (document.documentElement.lang || 'vi').indexOf('en') === 0 ? 'en' : 'vi';
    var T = LANG === 'en'
        ? { all: 'All', photos: 'Photos', videos: 'Videos', close: 'Close', prev: 'Previous', next: 'Next', of: 'of', comments: 'Comments',
            share: 'Share', copied: 'Link copied', tapNext: 'Tap the photo for the next one', review: 'review' }
        : { all: 'Tất cả', photos: 'Ảnh', videos: 'Video', close: 'Đóng', prev: 'Trước', next: 'Tiếp', of: '/', comments: 'Bình luận',
            share: 'Chia sẻ', copied: 'Đã chép link', tapNext: 'Bấm vào ảnh để xem ảnh tiếp theo', review: 'đánh giá' };

    var css = '' +
        '.rvlb{position:fixed;inset:0;z-index:2000;background:rgba(8,18,30,.94);display:none;flex-direction:column;color:#fff;font-family:inherit}' +
        '.rvlb.open{display:flex}' +
        '.rvlb-top{display:flex;align-items:center;gap:10px;padding:10px 14px;flex-wrap:wrap}' +
        '.rvlb-count{font-weight:700;font-variant-numeric:tabular-nums;min-width:64px}' +
        '.rvlb-tabs{display:inline-flex;background:rgba(255,255,255,.1);border-radius:999px;padding:3px}' +
        '.rvlb-tabs button{border:0;background:none;color:#cfe3f3;font:inherit;font-size:.85rem;font-weight:600;padding:4px 12px;border-radius:999px;cursor:pointer}' +
        '.rvlb-tabs button.on{background:#fff;color:#13395e}.rvlb-tabs button:disabled{opacity:.35;cursor:default}' +
        '.rvlb-x{margin-left:auto;width:40px;height:40px;border-radius:50%;border:0;background:rgba(255,255,255,.14);color:#fff;font-size:22px;cursor:pointer}' +
        '.rvlb-x:hover{background:rgba(255,255,255,.28)}' +
        '.rvlb-stage{flex:1;min-height:0;position:relative;display:flex;align-items:center;justify-content:center;padding:0 56px}' +
        '.rvlb-stage img,.rvlb-stage video{max-width:100%;max-height:100%;object-fit:contain;border-radius:6px;box-shadow:0 10px 40px rgba(0,0,0,.5)}' +
        '.rvlb-stage img{cursor:pointer}' +
        '.rvlb-nav{position:absolute;top:50%;transform:translateY(-50%);width:44px;height:44px;border-radius:50%;border:0;background:rgba(255,255,255,.14);color:#fff;font-size:22px;cursor:pointer}' +
        '.rvlb-nav:hover{background:rgba(255,255,255,.3)}.rvlb-prev{left:8px}.rvlb-next{right:8px}' +
        '.rvlb-hint{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);font-size:.75rem;color:#a9c4da;pointer-events:none}' +
        '.rvlb-foot{padding:10px 16px 14px;display:flex;gap:14px;align-items:flex-start;flex-wrap:wrap;max-width:1000px;width:100%;margin:0 auto}' +
        '.rvlb-who{flex:1;min-width:200px}.rvlb-who b{font-size:.95rem}.rvlb-stars{color:#f2b705;margin-left:6px;letter-spacing:1px}' +
        '.rvlb-text{font-size:.86rem;color:#d7e6f2;margin-top:2px;max-height:3.9em;overflow:auto;white-space:pre-line}' +
        '.rvlb-acts{display:flex;gap:8px;align-items:center}' +
        '.rvlb-acts button,.rvlb-acts a{display:inline-flex;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.3);background:rgba(255,255,255,.08);color:#fff;' +
        'border-radius:999px;padding:6px 13px;font:inherit;font-size:.85rem;font-weight:600;text-decoration:none;cursor:pointer}' +
        '.rvlb-acts button:hover,.rvlb-acts a:hover{background:rgba(255,255,255,.2)}.rvlb-acts .liked{color:#ff6b8b;border-color:#ff6b8b}' +
        '.rvlb-strip{display:flex;gap:6px;overflow-x:auto;padding:0 16px 12px;justify-content:center}' +
        '.rvlb-strip button{flex:0 0 auto;width:56px;height:42px;border-radius:5px;border:2px solid transparent;padding:0;overflow:hidden;background:#000;cursor:pointer;opacity:.55;position:relative}' +
        '.rvlb-strip button.on{border-color:#fff;opacity:1}.rvlb-strip img,.rvlb-strip video{width:100%;height:100%;object-fit:cover;display:block}' +
        '.rvlb-strip .pv{position:absolute;inset:0;display:grid;place-items:center;font-size:14px;text-shadow:0 1px 3px #000}' +
        '.rvlb-toast{position:absolute;top:12px;left:50%;transform:translateX(-50%);background:#fff;color:#13395e;padding:6px 14px;border-radius:999px;font-weight:700;font-size:.85rem;display:none}' +
        '@media (max-width:576px){.rvlb-stage{padding:0 4px}.rvlb-nav{display:none}.rvlb-strip{justify-content:flex-start}}' +
        /* O nho trong trang */
        '.rv-thumb{position:relative;padding:0;border:1.5px solid #13395e;border-radius:8px;overflow:hidden;background:#000;cursor:zoom-in;display:block}' +
        '.rv-thumb img,.rv-thumb video{display:block;object-fit:cover;pointer-events:none}' +
        '.rv-thumb .rv-play{position:absolute;inset:0;display:grid;place-items:center;color:#fff;font-size:30px;text-shadow:0 2px 8px rgba(0,0,0,.6);background:rgba(0,0,0,.18)}' +
        '.rv-thumb:hover .rv-play{background:rgba(0,0,0,.05)}';
    var style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);

    var box, stage, countEl, tabs, whoEl, textEl, actsEl, strip, toast, hint;
    var all = [], list = [], idx = 0, mode = 'all', touchX = null;

    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text != null) e.textContent = text;
        return e;
    }

    function build() {
        box = el('div', 'rvlb');
        box.setAttribute('role', 'dialog');
        box.setAttribute('aria-modal', 'true');
        var top = el('div', 'rvlb-top');
        countEl = el('span', 'rvlb-count');
        tabs = el('div', 'rvlb-tabs');
        [['all', T.all], ['photo', T.photos], ['video', T.videos]].forEach(function (t) {
            var b = el('button', null, t[1]);
            b.type = 'button';
            b.dataset.mode = t[0];
            b.addEventListener('click', function () { setMode(t[0], current()); });
            tabs.appendChild(b);
        });
        var x = el('button', 'rvlb-x', '×');
        x.type = 'button';
        x.setAttribute('aria-label', T.close);
        x.addEventListener('click', close);
        top.appendChild(countEl);
        top.appendChild(tabs);
        top.appendChild(x);

        stage = el('div', 'rvlb-stage');
        var prev = el('button', 'rvlb-nav rvlb-prev', '‹');
        prev.type = 'button';
        prev.setAttribute('aria-label', T.prev);
        prev.addEventListener('click', function () { go(-1); });
        var next = el('button', 'rvlb-nav rvlb-next', '›');
        next.type = 'button';
        next.setAttribute('aria-label', T.next);
        next.addEventListener('click', function () { go(1); });
        toast = el('div', 'rvlb-toast');
        hint = el('div', 'rvlb-hint', T.tapNext);
        stage.appendChild(prev);
        stage.appendChild(next);
        stage.appendChild(toast);
        stage.appendChild(hint);
        stage.addEventListener('touchstart', function (e) { touchX = e.touches[0].clientX; }, { passive: true });
        stage.addEventListener('touchend', function (e) {
            if (touchX == null) return;
            var dx = e.changedTouches[0].clientX - touchX;
            touchX = null;
            if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1);
        });

        var foot = el('div', 'rvlb-foot');
        var who = el('div', 'rvlb-who');
        whoEl = el('div');
        textEl = el('div', 'rvlb-text');
        who.appendChild(whoEl);
        who.appendChild(textEl);
        actsEl = el('div', 'rvlb-acts');
        foot.appendChild(who);
        foot.appendChild(actsEl);
        strip = el('div', 'rvlb-strip');

        box.appendChild(top);
        box.appendChild(stage);
        box.appendChild(foot);
        box.appendChild(strip);
        document.body.appendChild(box);
        document.addEventListener('keydown', function (e) {
            if (!box.classList.contains('open')) return;
            if (e.key === 'Escape') close();
            else if (e.key === 'ArrowRight') go(1);
            else if (e.key === 'ArrowLeft') go(-1);
        });
    }

    // Moi o anh / video tren trang theo thu tu, kem thong tin danh gia chua no
    function collect() {
        return Array.prototype.map.call(document.querySelectorAll('.rv-thumb[data-rv-src]'), function (b) {
            var g = b.closest('[data-rv-gallery]');
            return { btn: b, src: b.dataset.rvSrc, video: b.dataset.rvType === 'video', g: g };
        });
    }

    function current() { return list[idx]; }

    function setMode(m, keep) {
        var filtered = all.filter(function (it) { return m === 'all' || (m === 'video') === it.video; });
        if (!filtered.length) return;
        mode = m;
        list = filtered;
        idx = Math.max(0, list.indexOf(keep));
        tabs.querySelectorAll('button').forEach(function (b) { b.classList.toggle('on', b.dataset.mode === mode); });
        renderStrip();
        show();
    }

    function stopMedia() {
        stage.querySelectorAll('video').forEach(function (v) { try { v.pause(); } catch (e) { /* bo qua */ } v.remove(); });
        stage.querySelectorAll('img.rvlb-media').forEach(function (i) { i.remove(); });
    }

    function show() {
        stopMedia();
        var it = current();
        if (!it) return;
        var media;
        if (it.video) {
            media = el('video', 'rvlb-media');
            media.src = it.src;
            media.controls = true;
            media.autoplay = true;
            media.playsInline = true;
            // Het video -> video / muc tiep theo (khong vong lai khi chi co 1)
            media.addEventListener('ended', function () { if (list.length > 1) go(1); });
            hint.style.display = 'none';
        } else {
            media = el('img', 'rvlb-media');
            media.src = it.src;
            media.alt = '';
            media.addEventListener('click', function () { go(1); });
            hint.style.display = list.length > 1 ? '' : 'none';
            // Tai truoc anh ke tiep cho muot
            var nx = list[(idx + 1) % list.length];
            if (nx && !nx.video) { var pre = new Image(); pre.src = nx.src; }
        }
        stage.insertBefore(media, stage.firstChild);
        if (it.video) { var p = media.play(); if (p && p.catch) p.catch(function () { /* trinh duyet chan tu phat */ }); }
        countEl.textContent = (idx + 1) + ' ' + T.of + ' ' + list.length;
        strip.querySelectorAll('button').forEach(function (b, i) { b.classList.toggle('on', i === idx); });
        var on = strip.children[idx];
        if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest', inline: 'center' });
        renderReview(it.g);
    }

    function renderStrip() {
        strip.replaceChildren();
        list.forEach(function (it, i) {
            var b = el('button');
            b.type = 'button';
            if (it.video) {
                var v = el('video');
                v.src = it.src + '#t=0.5';
                v.muted = true;
                v.preload = 'metadata';
                b.appendChild(v);
                b.appendChild(el('span', 'pv', '▶'));
            } else {
                var im = el('img');
                im.src = it.src;
                im.loading = 'lazy';
                im.alt = '';
                b.appendChild(im);
            }
            b.addEventListener('click', function () { idx = i; show(); });
            strip.appendChild(b);
        });
        strip.style.display = list.length > 1 ? '' : 'none';
    }

    function renderReview(g) {
        whoEl.replaceChildren();
        textEl.textContent = '';
        actsEl.replaceChildren();
        if (!g) return;
        var d = g.dataset;
        if (d.rvName) whoEl.appendChild(el('b', null, d.rvName));
        var r = parseInt(d.rvRating || '0', 10);
        if (r) whoEl.appendChild(el('span', 'rvlb-stars', '★★★★★'.slice(0, r) + '☆☆☆☆☆'.slice(0, 5 - r)));
        textEl.textContent = d.rvComment || '';
        if (!d.rvId || !window.RvSocial) return;
        var like = el('button');
        like.type = 'button';
        var paint = function () {
            like.textContent = (g.dataset.rvLiked === 'true' ? '♥ ' : '♡ ') + (g.dataset.rvLikes || '0');
            like.classList.toggle('liked', g.dataset.rvLiked === 'true');
        };
        paint();
        like.addEventListener('click', function () { window.RvSocial.like(d.rvId).then(paint); });
        var cm = el('a', null, '💬 ' + (d.rvComments || '0') + ' ' + T.comments);
        cm.href = '/reviews/' + d.rvId + '#binh-luan';
        var sh = el('button', null, '↗ ' + T.share);
        sh.type = 'button';
        sh.addEventListener('click', function () {
            var m = current();
            var i = Array.prototype.indexOf.call(g.querySelectorAll('.rv-thumb'), m.btn);
            window.RvSocial.share(d.rvId, i).then(function (copied) { if (copied) flash(T.copied); });
        });
        actsEl.appendChild(like);
        actsEl.appendChild(cm);
        actsEl.appendChild(sh);
    }

    function flash(text) {
        toast.textContent = text;
        toast.style.display = 'block';
        setTimeout(function () { toast.style.display = 'none'; }, 1600);
    }

    function go(step) {
        if (!list.length) return;
        idx = (idx + step + list.length) % list.length;
        show();
    }

    function open(btn) {
        if (!box) build();
        all = collect();
        var start = all.filter(function (it) { return it.btn === btn; })[0];
        if (!start) return;
        // Mo tu video -> luot video; mo tu anh -> luot anh (doi o tab tren cung)
        var hasPhoto = all.some(function (it) { return !it.video; });
        var hasVideo = all.some(function (it) { return it.video; });
        tabs.querySelector('[data-mode="photo"]').disabled = !hasPhoto;
        tabs.querySelector('[data-mode="video"]').disabled = !hasVideo;
        box.classList.add('open');
        document.documentElement.style.overflow = 'hidden';
        setMode(hasPhoto && hasVideo ? (start.video ? 'video' : 'photo') : 'all', start);
    }

    function close() {
        stopMedia();
        box.classList.remove('open');
        document.documentElement.style.overflow = '';
    }

    window.RvLightbox = { open: open, close: close };

    document.addEventListener('click', function (e) {
        var b = e.target.closest && e.target.closest('.rv-thumb[data-rv-src]');
        if (!b) return;
        e.preventDefault();
        open(b);
    });

    // Link chia se /reviews/{id}?m=2 -> mo san muc thu 2
    window.addEventListener('load', function () {
        var m = /[?&]m=(\d+)/.exec(location.search);
        if (!m) return;
        var g = /^\/reviews\/\d+/.test(location.pathname) ? document.querySelector('[data-rv-gallery]') : null;
        var thumbs = g ? g.querySelectorAll('.rv-thumb') : [];
        var b = thumbs[parseInt(m[1], 10)];
        if (b) open(b);
    });
})();
