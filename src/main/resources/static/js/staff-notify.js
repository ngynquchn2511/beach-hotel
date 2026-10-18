// Thong bao tuc thoi cho le tan: nghe luong SSE /admin/notifications/stream, hien toast goc phai duoi,
// keu "ting" (neu trinh duyet cho phep), dem so thong bao chua xem tren tieu de tab va bao ra man hinh may tinh khi tab dang an.
(function () {
    'use strict';
    var script = document.currentScript;
    if (!script || !window.EventSource || window.__staffNotify) {
        return;
    }
    window.__staffNotify = true;
    var streamUrl = script.getAttribute('data-stream');
    // Ung dung chay duoi context path -> lay tu duong dan stream
    var baseUrl = streamUrl.replace(/\/admin\/notifications\/stream.*$/, '');
    var baseTitle = document.title;
    var unread = 0;
    var MAX_TOASTS = 4;

    var box = document.createElement('div');
    box.className = 'staff-notify-box';
    box.setAttribute('aria-live', 'polite');
    box.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2000;display:flex;flex-direction:column;gap:8px;'
        + 'max-width:min(360px,calc(100vw - 32px));';
    document.addEventListener('DOMContentLoaded', function () {
        document.body.appendChild(box);
    });
    if (document.body) {
        document.body.appendChild(box);
    }

    var COLORS = {INFO: '#0d6efd', SUCCESS: '#198754', WARNING: '#dc6803'};

    function setTitle() {
        document.title = unread > 0 ? '(' + unread + ') ' + baseTitle : baseTitle;
    }

    window.addEventListener('focus', function () {
        unread = 0;
        setTitle();
    });

    function beep() {
        try {
            var Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) {
                return;
            }
            var ctx = beep.ctx || (beep.ctx = new Ctx());
            var osc = ctx.createOscillator();
            var gain = ctx.createGain();
            osc.frequency.value = 880;
            gain.gain.setValueAtTime(0.0001, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.2, ctx.currentTime + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.4);
            osc.connect(gain).connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.45);
        } catch (e) {
            // trinh duyet chan am thanh khi chua bam vao trang - bo qua
        }
    }

    function desktop(n) {
        if (!('Notification' in window) || Notification.permission !== 'granted' || !document.hidden) {
            return;
        }
        try {
            var note = new Notification(n.title, {body: n.message || '', tag: 'staff-' + n.id});
            note.onclick = function () {
                window.focus();
                if (n.url) {
                    window.location.href = baseUrl + n.url;
                }
            };
        } catch (e) {
            // mot so trinh duyet chi cho tao Notification qua service worker
        }
    }

    function permissionButton(toast) {
        if (!('Notification' in window) || Notification.permission !== 'default') {
            return;
        }
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-link btn-sm p-0 small';
        btn.textContent = 'Bật thông báo trên máy tính';
        btn.onclick = function () {
            Notification.requestPermission();
            btn.remove();
        };
        toast.appendChild(btn);
    }

    function show(n) {
        var toast = document.createElement('div');
        toast.className = 'bg-white shadow rounded p-3 small';
        toast.style.borderLeft = '4px solid ' + (COLORS[n.level] || COLORS.INFO);
        var head = document.createElement('div');
        head.className = 'd-flex justify-content-between gap-2';
        var title = document.createElement('strong');
        title.textContent = n.title;
        var close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close btn-sm';
        close.setAttribute('aria-label', 'Đóng');
        close.onclick = function () {
            toast.remove();
        };
        head.appendChild(title);
        head.appendChild(close);
        toast.appendChild(head);
        if (n.message) {
            var msg = document.createElement('div');
            msg.className = 'text-muted';
            msg.textContent = n.message;
            toast.appendChild(msg);
        }
        if (n.url) {
            var link = document.createElement('a');
            link.href = baseUrl + n.url;
            link.textContent = 'Xem ngay';
            toast.appendChild(link);
        }
        permissionButton(toast);
        box.appendChild(toast);
        while (box.children.length > MAX_TOASTS) {
            box.removeChild(box.firstChild);
        }
        // Canh bao (coc vao nhung phong da bi giu...) giu lai den khi bam dong
        if (n.level !== 'WARNING') {
            setTimeout(function () {
                toast.remove();
            }, 15000);
        }
    }

    var source = new EventSource(streamUrl);
    source.addEventListener('staff', function (e) {
        var n;
        try {
            n = JSON.parse(e.data);
        } catch (err) {
            return;
        }
        show(n);
        beep();
        desktop(n);
        if (document.hidden || !document.hasFocus()) {
            unread++;
            setTitle();
        }
    });
    // Phien dang nhap het han -> server tra trang dang nhap thay vi event-stream: dung han, khong thu lai lien tuc
    source.onerror = function () {
        if (source.readyState === EventSource.CLOSED) {
            source.close();
        }
    };
})();
