// Bieu do SVG thuan (khong thu vien ngoai): cot nhom, duong, thanh ngang. Rê chuột hiện số liệu.
// Dung: MiniCharts.bars(el, {labels, series:[{name, color, values}], unit}),
//       MiniCharts.line(el, {labels, values, color, unit}), MiniCharts.hbars(el, {rows:[[ten, gia tri]], color, unit})
(function () {
    'use strict';
    var NS = 'http://www.w3.org/2000/svg';
    var NEG = '#e34948';

    function svg(w, h) {
        var s = document.createElementNS(NS, 'svg');
        s.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
        s.setAttribute('width', '100%');
        s.setAttribute('role', 'img');
        s.style.overflow = 'visible';
        return s;
    }

    function el(name, attrs, parent) {
        var e = document.createElementNS(NS, name);
        for (var k in attrs) e.setAttribute(k, attrs[k]);
        if (parent) parent.appendChild(e);
        return e;
    }

    function fmt(v) {
        return (Math.round(v * 10) / 10).toLocaleString('vi-VN');
    }

    function tip(host) {
        host.style.position = 'relative';
        var t = document.createElement('div');
        t.style.cssText = 'position:absolute;pointer-events:none;background:#1f2933;color:#fff;font-size:12px;padding:4px 8px;'
            + 'border-radius:4px;white-space:nowrap;display:none;z-index:5;transform:translate(-50%,-110%)';
        host.appendChild(t);
        return {
            show: function (html, evt) {
                var r = host.getBoundingClientRect();
                t.innerHTML = html;
                t.style.left = (evt.clientX - r.left) + 'px';
                t.style.top = (evt.clientY - r.top) + 'px';
                t.style.display = 'block';
            },
            hide: function () { t.style.display = 'none'; }
        };
    }

    function legend(host, series) {
        var d = document.createElement('div');
        d.className = 'small d-flex gap-3 flex-wrap mb-1';
        series.forEach(function (s) {
            var i = document.createElement('span');
            i.innerHTML = '<span style="display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:4px;background:'
                + s.color + '"></span>' + s.name;
            d.appendChild(i);
        });
        host.appendChild(d);
    }

    function niceMax(v) {
        if (v <= 0) return 1;
        var p = Math.pow(10, Math.floor(Math.log10(v)));
        var n = v / p;
        return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
    }

    function axes(s, W, H, L, B, min, max, unit) {
        var g = el('g', {'font-size': 11, fill: '#6b7280'}, s);
        for (var i = 0; i <= 4; i++) {
            var v = min + (max - min) * i / 4;
            var y = H - B - (H - B - 10) * i / 4;
            el('line', {x1: L, x2: W, y1: y, y2: y, stroke: v === 0 ? '#9ca3af' : '#e5e7eb'}, g);
            var t = el('text', {x: L - 6, y: y + 4, 'text-anchor': 'end'}, g);
            t.textContent = fmt(v);
        }
        if (unit) {
            var u = el('text', {x: L - 6, y: 0, 'text-anchor': 'end'}, g);
            u.textContent = unit;
        }
    }

    function bars(host, o) {
        legend(host, o.series);
        var W = 640, H = 240, L = 48, B = 24;
        var all = [];
        o.series.forEach(function (s) { all = all.concat(s.values); });
        var max = niceMax(Math.max.apply(null, all.concat([0])));
        var min = Math.min.apply(null, all.concat([0])) < 0 ? -niceMax(-Math.min.apply(null, all)) : 0;
        var s = svg(W, H);
        host.appendChild(s);
        var t = tip(host);
        axes(s, W, H, L, B, min, max, o.unit);
        var y = function (v) { return H - B - (H - B - 10) * (v - min) / (max - min); };
        var n = o.labels.length, slot = (W - L) / n, bw = Math.min(18, slot * 0.8 / o.series.length);
        o.labels.forEach(function (lb, i) {
            var x0 = L + slot * i + (slot - bw * o.series.length) / 2;
            o.series.forEach(function (sr, k) {
                var v = sr.values[i] || 0;
                var r = el('rect', {x: x0 + k * bw, y: Math.min(y(v), y(0)), width: bw - 2, height: Math.max(1, Math.abs(y(v) - y(0))),
                    fill: v < 0 && sr.negColor ? sr.negColor : sr.color, rx: 2}, s);
                r.addEventListener('mousemove', function (e) { t.show(lb + ' · ' + sr.name + ': <b>' + fmt(v) + '</b> ' + (o.unit || ''), e); });
                r.addEventListener('mouseleave', t.hide);
            });
            if (n <= 12 || i % 2 === 0) {
                var tx = el('text', {x: L + slot * i + slot / 2, y: H - 6, 'text-anchor': 'middle', 'font-size': 11, fill: '#6b7280'}, s);
                tx.textContent = lb;
            }
        });
    }

    function line(host, o) {
        legend(host, [{name: o.name, color: o.color}]);
        var W = 640, H = 200, L = 48, B = 24;
        var max = o.max || niceMax(Math.max.apply(null, o.values.concat([0])));
        var s = svg(W, H);
        host.appendChild(s);
        var t = tip(host);
        axes(s, W, H, L, B, 0, max, o.unit);
        var n = o.values.length, slot = (W - L) / n;
        var px = function (i) { return L + slot * i + slot / 2; };
        var py = function (v) { return H - B - (H - B - 10) * v / max; };
        var d = o.values.map(function (v, i) { return (i ? 'L' : 'M') + px(i) + ' ' + py(v); }).join(' ');
        el('path', {d: d, fill: 'none', stroke: o.color, 'stroke-width': 2.5}, s);
        o.values.forEach(function (v, i) {
            var c = el('circle', {cx: px(i), cy: py(v), r: 4, fill: '#fff', stroke: o.color, 'stroke-width': 2}, s);
            c.addEventListener('mousemove', function (e) { t.show(o.labels[i] + ': <b>' + fmt(v) + '</b> ' + (o.unit || ''), e); });
            c.addEventListener('mouseleave', t.hide);
            var tx = el('text', {x: px(i), y: H - 6, 'text-anchor': 'middle', 'font-size': 11, fill: '#6b7280'}, s);
            tx.textContent = o.labels[i];
        });
    }

    function hbars(host, o) {
        var rows = o.rows || [];
        if (!rows.length) {
            host.innerHTML = '<div class="small text-muted">Chưa có số liệu</div>';
            return;
        }
        var total = rows.reduce(function (a, r) { return a + r[1]; }, 0);
        var max = Math.max.apply(null, rows.map(function (r) { return r[1]; }));
        rows.forEach(function (r) {
            var d = document.createElement('div');
            d.className = 'small mb-1';
            var pct = total ? Math.round(r[1] * 100 / total) : 0;
            d.innerHTML = '<div class="d-flex justify-content-between"><span></span><span class="text-muted">'
                + fmt(r[1]) + ' ' + (o.unit || '') + ' · ' + pct + '%</span></div>'
                + '<div style="background:#f1f3f5;border-radius:3px;height:8px"><div style="height:8px;border-radius:3px;width:'
                + (max ? r[1] * 100 / max : 0) + '%;background:' + o.color + '"></div></div>';
            d.querySelector('span').textContent = r[0];
            host.appendChild(d);
        });
    }

    window.MiniCharts = {bars: bars, line: line, hbars: hbars};
})();
