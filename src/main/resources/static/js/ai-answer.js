// Hien cau tra loi AI: khoi ```kpi -> o chi so, ```chart -> bieu do (MiniCharts), con lai markdown don gian
// (**dam**, gach dau dong, danh so, tieu de). Dung duoc ca khi dang nhan dan (khoi chua dong -> "Dang ve bieu do...").
// Can static/js/mini-charts.js nap truoc.
(function () {
    'use strict';
    var COLORS = ['#2a78d6', '#eb6834', '#0ca30c', '#8b5cf6', '#e3a008'];
    var NEG = '#e34948';

    function esc(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function inline(s) {
        return esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/(^|[^*])\*([^*\s][^*]*?)\*(?!\*)/g, '$1<i>$2</i>');
    }

    // Markdown toi gian -> HTML
    function markdown(text) {
        var out = [], list = null;
        function close() {
            if (list) { out.push('</' + list + '>'); list = null; }
        }
        text.split('\n').forEach(function (raw) {
            var line = raw.trim();
            var m;
            if (!line) { close(); return; }
            if ((m = line.match(/^[-*•]\s+(.*)$/))) {
                if (list !== 'ul') { close(); out.push('<ul class="mb-2 ps-3">'); list = 'ul'; }
                out.push('<li>' + inline(m[1]) + '</li>');
            } else if ((m = line.match(/^\d+[.)]\s+(.*)$/))) {
                if (list !== 'ol') { close(); out.push('<ol class="mb-2 ps-3">'); list = 'ol'; }
                out.push('<li>' + inline(m[1]) + '</li>');
            } else if ((m = line.match(/^#{1,4}\s+(.*)$/))) {
                close();
                out.push('<div class="fw-bold mt-2 mb-1">' + inline(m[1]) + '</div>');
            } else {
                close();
                out.push('<p class="mb-2">' + inline(line) + '</p>');
            }
        });
        close();
        return out.join('');
    }

    function kpi(host, items) {
        var row = document.createElement('div');
        row.className = 'row g-2 mb-3';
        (Array.isArray(items) ? items : []).slice(0, 6).forEach(function (k) {
            var tone = k.tone === 'good' ? '#0ca30c' : k.tone === 'bad' ? NEG : '#1f2933';
            var col = document.createElement('div');
            col.className = 'col-6 col-md-3';
            col.innerHTML = '<div class="border rounded p-2 h-100" style="border-left:4px solid ' + tone + ' !important">'
                + '<div class="small text-muted">' + esc(String(k.label || '')) + '</div>'
                + '<div class="fw-bold fs-5" style="color:' + tone + '">' + esc(String(k.value || '')) + '</div>'
                + (k.note ? '<div class="small text-muted">' + esc(String(k.note)) + '</div>' : '') + '</div>';
            row.appendChild(col);
        });
        host.appendChild(row);
    }

    function chart(host, c) {
        var box = document.createElement('div');
        box.className = 'mb-3';
        if (c.title) {
            var t = document.createElement('div');
            t.className = 'small fw-semibold mb-1';
            t.textContent = c.title;
            box.appendChild(t);
        }
        var area = document.createElement('div');
        box.appendChild(area);
        host.appendChild(box);
        var unit = c.unit || '';
        if (c.type === 'line') {
            var vals = c.values || (c.series && c.series[0] && c.series[0].values) || [];
            MiniCharts.line(area, {labels: c.labels || [], values: vals.map(Number), name: c.name || (c.series && c.series[0] && c.series[0].name) || c.title || '',
                color: COLORS[0], unit: unit});
        } else if (c.type === 'hbar') {
            var rows = c.rows || (c.labels || []).map(function (l, i) {
                return [l, c.series && c.series[0] && c.series[0].values ? c.series[0].values[i] : 0];
            });
            rows = rows.map(function (r) { return [String(r[0]), Number(r[1]) || 0]; })
                .sort(function (a, b) { return b[1] - a[1]; });
            MiniCharts.hbars(area, {rows: rows, color: COLORS[0], unit: unit});
        } else {
            var series = (c.series || []).slice(0, 5).map(function (s, i) {
                return {name: s.name || '', color: COLORS[i % COLORS.length], negColor: NEG, values: (s.values || []).map(Number)};
            });
            if (!series.length) throw new Error('no series');
            MiniCharts.bars(area, {labels: c.labels || [], series: series, unit: unit});
        }
    }

    function placeholder(host, label) {
        var d = document.createElement('div');
        d.className = 'small text-muted mb-2';
        d.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>' + label;
        host.appendChild(d);
    }

    // Ve lai toan bo noi dung vao el
    function render(el, text, streaming) {
        el.innerHTML = '';
        el.style.whiteSpace = 'normal';
        var re = /```(kpi|chart)\s*\n?([\s\S]*?)```/g;
        var last = 0, m;
        function addText(s) {
            if (s.trim()) {
                var d = document.createElement('div');
                d.innerHTML = markdown(s);
                el.appendChild(d);
            }
        }
        while ((m = re.exec(text))) {
            addText(text.substring(last, m.index));
            try {
                var data = JSON.parse(m[2].trim());
                if (m[1] === 'kpi') kpi(el, data); else chart(el, data);
            } catch (e) {
                // JSON hong: bo qua khoi do
            }
            last = re.lastIndex;
        }
        var rest = text.substring(last);
        var open = rest.search(/```/);
        if (open >= 0) {
            // khoi dang nhan do: chi hien phan chu truoc no
            addText(rest.substring(0, open));
            if (streaming) placeholder(el, /```\s*kpi/.test(rest) ? 'Đang tính chỉ số...' : 'Đang vẽ biểu đồ...');
        } else {
            addText(rest);
        }
    }

    // Ve bieu do / o chi so tu du lieu co san (ban phan tich co cau truc). Bieu do loi thi bo qua
    function charts(host, list) {
        (list || []).forEach(function (c) {
            var cell = document.createElement('div');
            cell.className = 'col-md-6';
            host.appendChild(cell);
            try { chart(cell, c); } catch (e) { host.removeChild(cell); }
        });
    }

    window.AiAnswer = {render: render, kpi: kpi, charts: charts};

    // Cau tra loi da luu: <div class="ai-answer" data-text="...">
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.ai-answer[data-text]').forEach(function (el) {
            render(el, el.getAttribute('data-text'), false);
        });
    });
})();
