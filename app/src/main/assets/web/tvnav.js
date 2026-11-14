// Lumen TV navigation for ordinary websites.
// The remote's arrows move a highlight between the things on the page you can click, in the
// direction pressed, the way a TV app moves focus. Injected by WebPlayerActivity into every page;
// the activity sends each key here and acts on the answer:
//   "ok"          handled here
//   "tap:fx,fy"   press there with a real touch (fractions of the viewport), so players and
//                 cross-site iframes get a genuine user gesture
//   "edit"        a text field is focused: show the on-screen keyboard
//   "pass"        give the key to the page itself (caret movement in a text field)
//   "edge"        nothing further that way
(function () {
  if (window.lumenNav) return 'already';
  var ACCENT = '__ACCENT__';
  var cur = null;
  var STRICT = 'a[href],button,input:not([type=hidden]),select,textarea,video,iframe,summary,' +
    '[tabindex]:not([tabindex="-1"]),[role=button],[role=link],[role=tab],[role=menuitem],[role=option],' +
    '[role=checkbox],[role=switch],[role=radio],[role=treeitem],[onclick],[contenteditable=""],[contenteditable=true]';
  var LOOSE = 'div,li,span,img,article,figure,section,picture,h1,h2,h3,h4,p,label,svg';

  function css() {
    if (document.getElementById('lumen-nav-css')) return;
    var s = document.createElement('style');
    s.id = 'lumen-nav-css';
    s.textContent =
      'html body .lumen-focus.lumen-focus{outline:4px solid ' + ACCENT + ' !important;outline-offset:3px !important;' +
      'box-shadow:0 0 0 7px rgba(0,0,0,.5),0 0 34px 10px ' + ACCENT + ' !important;' +
      'transition:box-shadow .16s ease-out,outline-offset .16s ease-out !important}' +
      'html body video.lumen-fill.lumen-fill{position:fixed !important;inset:0 !important;left:0 !important;top:0 !important;width:100vw !important;height:100vh !important;' +
      'max-width:none !important;max-height:none !important;margin:0 !important;z-index:2147483647 !important;background:#000 !important;object-fit:contain !important;' +
      'transform:none !important;translate:none !important;scale:none !important;rotate:none !important;clip-path:none !important}' +
      // Anything on the way up that makes "fixed" mean "fixed inside me" instead of "fixed to the screen".
      'html body .lumen-fill-up{transform:none !important;translate:none !important;scale:none !important;rotate:none !important;filter:none !important;' +
      'backdrop-filter:none !important;-webkit-backdrop-filter:none !important;perspective:none !important;contain:none !important;container-type:normal !important;' +
      'will-change:auto !important;clip-path:none !important;mask:none !important;z-index:2147483647 !important;overflow:visible !important}' +
      'html.lumen-filling,html.lumen-filling body{overflow:hidden !important}' +
      '__COSMETIC__';
    (document.head || document.documentElement).appendChild(s);
  }

  function visible(el, r) {
    if (r.width < 6 || r.height < 6) return false;
    if (el.disabled) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none' && +cs.opacity > 0.05;
  }
  function nearScreen(r) {
    var H = innerHeight, W = innerWidth;
    return r.bottom > -H * 1.5 && r.top < H * 2.5 && r.right > -W && r.left < W * 2;
  }
  function onScreen(r) { return r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth; }
  // Something drawn over it (a dialog, a sticky bar) means it cannot be pressed right now.
  function covered(el, r) {
    if (!onScreen(r)) return false;
    var x = Math.min(Math.max(r.left + r.width / 2, 1), innerWidth - 1);
    var y = Math.min(Math.max(r.top + r.height / 2, 1), innerHeight - 1);
    var hit = deepHit(x, y);
    return !!hit && hit !== el && !within(el, hit) && !within(hit, el);
  }
  function area(r) { return r.width * r.height; }

  // Web components keep their insides in shadow roots, which ordinary queries cannot see
  // (Internet Archive's film tiles, many players). Search every open one.
  function roots() {
    var out = [document];
    for (var i = 0; i < out.length; i++) {
      var all = out[i].querySelectorAll('*');
      for (var j = 0; j < all.length; j++) if (all[j].shadowRoot) out.push(all[j].shadowRoot);
    }
    return out;
  }
  function deepAll(sel, rs) {
    var res = [];
    (rs || roots()).forEach(function (r) { r.querySelectorAll(sel).forEach(function (e) { res.push(e); }); });
    return res;
  }
  // The parent, stepping out of a shadow root to its host.
  function up(el) { return el.parentElement || (el.parentNode && el.parentNode.host) || null; }
  function within(a, b) { for (var x = b; x; x = up(x)) if (x === a) return true; return false; }
  function deepHit(x, y) {
    var hit = document.elementFromPoint(x, y);
    while (hit && hit.shadowRoot) { var inner = hit.shadowRoot.elementFromPoint(x, y); if (!inner || inner === hit) break; hit = inner; }
    return hit;
  }

  function candidates() {
    var out = [], rects = new Map(), rs = roots();
    deepAll(STRICT, rs).forEach(function (el) {
      var r = el.getBoundingClientRect();
      if (nearScreen(r) && visible(el, r)) { out.push(el); rects.set(el, r); }
    });
    // Cards that only a click handler makes interactive: the outermost element with a pointer cursor.
    var loose = deepAll(LOOSE, rs);
    if (loose.length < 8000) loose.forEach(function (el) {
      if (rects.has(el)) return;
      var r = el.getBoundingClientRect();
      if (r.width < 24 || r.height < 24 || !nearScreen(r)) return;
      if (getComputedStyle(el).cursor !== 'pointer') return;
      var p = up(el);
      if (p && p.nodeType === 1 && getComputedStyle(p).cursor === 'pointer') return;
      for (var q = p, n = 0; q && n < 10; q = up(q), n++) if (q.matches && q.matches(STRICT)) return;
      if (visible(el, r)) { out.push(el); rects.set(el, r); }
    });
    // A link wrapping a poster and its caption is one stop, not three.
    var kept = out.filter(function (el) {
      var r = rects.get(el);
      for (var a = up(el), n = 0; a && n < 8; a = up(a), n++) {
        var ar = rects.get(a);
        if (ar && area(ar) <= area(r) * 2.5) return false;
      }
      return !covered(el, r);
    });
    return { list: kept, rects: rects };
  }

  function overlap(a1, a2, b1, b2) { return Math.min(a2, b2) - Math.max(a1, b1); }

  function best(dir, from, c) {
    var fx = (from.left + from.right) / 2, fy = (from.top + from.bottom) / 2;
    var bestEl = null, bestS = Infinity;
    c.list.forEach(function (el) {
      if (el === cur || (cur && (within(cur, el) || within(el, cur)))) return;
      var r = c.rects.get(el), cx = (r.left + r.right) / 2, cy = (r.top + r.bottom) / 2;
      var main, ov, cross;
      if (dir === 'down' || dir === 'up') {
        if (dir === 'down') { if (cy <= fy + 2) return; main = r.top - from.bottom; }
        else { if (cy >= fy - 2) return; main = from.top - r.bottom; }
        if (main < -Math.min(from.height, r.height) * 0.6) return;
        ov = overlap(from.left, from.right, r.left, r.right);
        cross = ov > 0 ? 0 : -ov;
        main = Math.max(main, 0);
        var s = main + cross * 3 + (cross > 0 ? 40 : 0) + Math.abs(cx - fx) * 0.05;
      } else {
        if (dir === 'right') { if (cx <= fx + 2) return; main = r.left - from.right; }
        else { if (cx >= fx - 2) return; main = from.left - r.right; }
        if (main < -Math.min(from.width, r.width) * 0.6) return;
        ov = overlap(from.top, from.bottom, r.top, r.bottom);
        cross = ov > 0 ? 0 : -ov;
        main = Math.max(main, 0);
        var s = main + cross * 4 + (cross > 0 ? 60 : 0) + Math.abs(cy - fy) * 0.05;
      }
      if (s < bestS) { bestS = s; bestEl = el; }
    });
    return bestEl;
  }

  function fire(el, type) {
    try {
      var r = el.getBoundingClientRect();
      var o = { bubbles: type.indexOf('enter') < 0 && type.indexOf('leave') < 0, cancelable: true, view: window,
        clientX: r.left + r.width / 2, clientY: r.top + r.height / 2, pointerType: 'mouse', isPrimary: true };
      el.dispatchEvent(type.indexOf('pointer') === 0 ? new PointerEvent(type, o) : new MouseEvent(type, o));
    } catch (e) {}
  }

  function reveal(el) {
    var r = el.getBoundingClientRect(), H = innerHeight, W = innerWidth, m = H * 0.14;
    if (r.top < m || r.bottom > H - m || r.left < 0 || r.right > W) {
      try { el.scrollIntoView({ behavior: 'smooth', block: r.height > H * 0.7 ? 'start' : 'center', inline: 'center' }); }
      catch (e) { el.scrollIntoView(); }
    }
  }

  var saved = new WeakMap();
  function ring(el, on) {
    var st = el.style;
    if (on) {
      if (!saved.has(el)) saved.set(el, [st.getPropertyValue('outline'), st.getPropertyPriority('outline'), st.getPropertyValue('outline-offset'), st.getPropertyValue('box-shadow'), st.getPropertyPriority('box-shadow')]);
      st.setProperty('outline', '4px solid ' + ACCENT, 'important');
      st.setProperty('outline-offset', '3px', 'important');
      st.setProperty('box-shadow', '0 0 0 7px rgba(0,0,0,.5),0 0 34px 10px ' + ACCENT, 'important');
    } else if (saved.has(el)) {
      var v = saved.get(el); saved.delete(el);
      st.setProperty('outline', v[0], v[1]); st.setProperty('outline-offset', v[2]); st.setProperty('box-shadow', v[3], v[4]);
    }
  }
  function focusEl(el) {
    css();
    if (cur && cur !== el) {
      ring(cur, false);
      cur.classList.remove('lumen-focus');
      fire(cur, 'pointerout'); fire(cur, 'mouseout'); fire(cur, 'pointerleave'); fire(cur, 'mouseleave');
    }
    cur = el;
    el.classList.add('lumen-focus');
    ring(el, true);
    try { el.focus({ preventScroll: true }); } catch (e) {}
    fire(el, 'pointerover'); fire(el, 'mouseover'); fire(el, 'pointerenter'); fire(el, 'mouseenter');
    reveal(el);
  }

  function editable(el) {
    if (!el) return false;
    if (el.tagName === 'TEXTAREA' || el.isContentEditable) return true;
    return el.tagName === 'INPUT' && !/^(button|submit|checkbox|radio|range|reset|image|file|color)$/i.test(el.type || '');
  }

  // Fill mode: the film being watched covers the whole TV, the way a TV app plays it.
  var filled = null;
  function fill(v) {
    if (filled === v) return;
    unfill(false);
    css();
    filled = v;
    mark(v);
    // Pages that re-render their player (React and the like) drop these classes: keep putting them back.
    clearInterval(fill.timer);
    fill.timer = setInterval(function () { if (filled && filled.isConnected) mark(filled); else if (filled) unfill(false); }, 700);
    document.documentElement.classList.add('lumen-filling');
    if (cur) { cur.classList.remove('lumen-focus'); ring(cur, false); }
    // You pressed play on a film: it should be heard. Pages often start it muted.
    try { v.muted = false; if (!v.volume) v.volume = 1; } catch (e) {}
    try { LumenWeb.filled(true); } catch (e) {}
  }
  // Inline !important styles, so they also hold inside web components (page CSS cannot reach there).
  var FILL_V = { position: 'fixed', inset: '0', left: '0', top: '0', width: '100vw', height: '100vh', 'max-width': 'none', 'max-height': 'none',
    margin: '0', 'z-index': '2147483647', background: '#000', 'object-fit': 'contain', transform: 'none', translate: 'none', scale: 'none', rotate: 'none', 'clip-path': 'none' };
  var FILL_UP = { transform: 'none', translate: 'none', scale: 'none', rotate: 'none', filter: 'none', 'backdrop-filter': 'none', '-webkit-backdrop-filter': 'none',
    perspective: 'none', contain: 'none', 'container-type': 'normal', 'will-change': 'auto', 'clip-path': 'none', mask: 'none', 'z-index': '2147483647', overflow: 'visible' };
  var styled = [];
  function force(el, props) {
    if (el.__lumenStyle === undefined) { el.__lumenStyle = el.getAttribute('style'); styled.push(el); }
    for (var k in props) if (el.style.getPropertyValue(k) !== props[k] || el.style.getPropertyPriority(k) !== 'important') el.style.setProperty(k, props[k], 'important');
  }
  function mark(v) {
    v.classList.add('lumen-fill'); force(v, FILL_V);
    for (var a = up(v); a && a !== document.body && a !== document.documentElement; a = up(a)) if (a.style) { a.classList.add('lumen-fill-up'); force(a, FILL_UP); }
  }
  function unmark() {
    styled.forEach(function (el) {
      if (el.__lumenStyle === null) el.removeAttribute('style'); else el.setAttribute('style', el.__lumenStyle);
      el.__lumenStyle = undefined;
      if (el.classList) { el.classList.remove('lumen-fill'); el.classList.remove('lumen-fill-up'); }
    });
    styled = [];
  }
  function unfill(pause) {
    clearInterval(fill.timer);
    if (!filled) return false;
    var v = filled; filled = null;
    if (pause) try { v.pause(); } catch (e) {}
    unmark();
    document.documentElement.classList.remove('lumen-filling');
    if (cur && cur.isConnected) { cur.classList.add('lumen-focus'); ring(cur, true); reveal(cur); }
    try { LumenWeb.filled(false); } catch (e) {}
    return true;
  }
  // A film, not a muted preview loop or a short clip in a banner.
  var pressedAt = 0;
  function worthFilling(v) {
    var r = v.getBoundingClientRect();
    if (area(r) < innerWidth * innerHeight * 0.12) return false;
    // You just pressed OK (a "Watch now", a channel): whatever starts is what you asked for.
    if (Date.now() - pressedAt < 25000) return true;
    return !isFinite(v.duration) || v.duration > 90;
  }
  function osd() {
    var v = filled || mainVideo();
    if (!v) return 'ok';
    return 'osd:' + (v.currentTime || 0).toFixed(1) + ',' + (isFinite(v.duration) ? v.duration.toFixed(1) : '-1') + ',' + (v.paused ? 1 : 0) + ',' + (v.muted ? 1 : 0);
  }

  // The video being watched: fullscreen, or playing and filling most of the screen.
  function mainVideo() {
    if (filled && filled.isConnected) return filled;
    var fs = document.fullscreenElement || document.webkitFullscreenElement;
    var vids = deepAll('video');
    if (fs) { var v = fs.tagName === 'VIDEO' ? fs : fs.querySelector('video'); if (v) return v; }
    for (var i = 0; i < vids.length; i++) {
      var r = vids[i].getBoundingClientRect();
      if ((!vids[i].paused || vids[i].currentTime > 0) && onScreen(r) && area(r) > innerWidth * innerHeight * 0.3) return vids[i];
    }
    return null;
  }

  function first(c) {
    var on = c.list.filter(function (el) { return onScreen(c.rects.get(el)); });
    on.sort(function (a, b) {
      var ra = c.rects.get(a), rb = c.rects.get(b);
      return (Math.round(ra.top / 60) - Math.round(rb.top / 60)) || (ra.left - rb.left);
    });
    return on[0] || null;
  }

  function scrollable(el, vertical) {
    for (var a = el; a && a.nodeType === 1 && a !== document.body && a !== document.documentElement; a = up(a)) {
      var cs = getComputedStyle(a), o = vertical ? cs.overflowY : cs.overflowX;
      if ((o === 'auto' || o === 'scroll') && (vertical ? a.scrollHeight > a.clientHeight + 4 : a.scrollWidth > a.clientWidth + 4)) return a;
    }
    return null;
  }

  function scrollFallback(dir) {
    var vertical = dir === 'up' || dir === 'down', sign = (dir === 'down' || dir === 'right') ? 1 : -1;
    var box = cur ? scrollable(cur, vertical) : null;
    if (vertical && !box) {
      var y0 = scrollY;
      window.scrollBy({ top: sign * innerHeight * 0.6, behavior: 'smooth' });
      return (sign < 0 && y0 <= 0) ? 'edge' : 'ok';
    }
    if (!box) return 'edge';
    if (vertical) box.scrollBy({ top: sign * box.clientHeight * 0.7, behavior: 'smooth' });
    else box.scrollBy({ left: sign * box.clientWidth * 0.8, behavior: 'smooth' });
    // A carousel that loads its next cards as it scrolls: look again once they are there.
    setTimeout(function () { var c = candidates(), n = cur && best(dir, cur.getBoundingClientRect(), c); if (n) focusEl(n); }, 450);
    return 'ok';
  }

  function activate(el) {
    if (el.tagName === 'VIDEO') { if (el.paused) el.play(); else el.pause(); return 'ok'; }
    if (editable(el)) { el.focus(); return 'edit'; }
    if (el.tagName === 'SELECT') { el.focus(); return 'pass'; }
    var r = el.getBoundingClientRect();
    if (onScreen(r) && !covered(el, r)) {
      var x = Math.min(Math.max(r.left + r.width / 2, 1), innerWidth - 1), y = Math.min(Math.max(r.top + r.height / 2, 1), innerHeight - 1);
      return 'tap:' + (x / innerWidth).toFixed(4) + ',' + (y / innerHeight).toFixed(4);
    }
    el.click();
    return 'ok';
  }

  function media(cmd) {
    var v = mainVideo() || document.querySelector('video');
    if (!v) return 'none';
    if (cmd === 'mute') { v.muted = !v.muted; return osd(); }
    if (cmd === 'toggle') { if (v.paused) v.play(); else v.pause(); }
    else if (cmd === 'play') v.play();
    else if (cmd === 'pause') v.pause();
    else if (cmd === 'ff') v.currentTime = Math.min(v.duration || 1e9, v.currentTime + 30);
    else if (cmd === 'rew') v.currentTime = Math.max(0, v.currentTime - 10);
    return osd();
  }

  // Back: out of a filled film first (it stops, like leaving a film in a TV app).
  function back() { return unfill(true) ? 'ok' : 'none'; }

  function key(k) {
    try {
      if (cur && !cur.isConnected) cur = null;
      var ae = document.activeElement;
      if (editable(ae) && (k === 'left' || k === 'right')) return 'pass';
      var v = mainVideo();
      if (v) {
        if (k === 'enter') { if (v.paused) v.play(); else v.pause(); if (!filled && worthFilling(v)) fill(v); return osd(); }
        if (k === 'left') { v.currentTime = Math.max(0, v.currentTime - 10); return osd(); }
        if (k === 'right') { v.currentTime = Math.min(v.duration || 1e9, v.currentTime + 10); return osd(); }
        if (filled && k === 'down') { v.muted = !v.muted; return osd(); }
        if (filled) return osd();
      }
      if (k === 'enter') {
        pressedAt = Date.now();
        if (cur) return activate(cur);
        var f0 = first(candidates()); if (f0) focusEl(f0); return 'ok';
      }
      var c = candidates();
      if (!cur || !onScreen(cur.getBoundingClientRect())) {
        var f = first(c);
        if (f) { focusEl(f); return 'ok'; }
        return scrollFallback(k);
      }
      var n = best(k, cur.getBoundingClientRect(), c);
      if (n) { focusEl(n); return 'ok'; }
      return scrollFallback(k);
    } catch (e) { return 'error ' + e; }
  }

  // Pointer mode: scroll whatever is under the pointer.
  function scrollAt(fx, fy, dy) {
    var el = document.elementFromPoint(fx * innerWidth, fy * innerHeight);
    var box = el && scrollable(el, true);
    if (box) box.scrollBy({ top: dy * innerHeight, behavior: 'smooth' });
    else window.scrollBy({ top: dy * innerHeight, behavior: 'smooth' });
    return 'ok';
  }

  function playing() {
    var vs = deepAll('video');
    for (var i = 0; i < vs.length; i++) if (!vs[i].paused && !vs[i].ended) return true;
    return false;
  }
  ['play', 'playing', 'pause', 'ended', 'durationchange'].forEach(function (t) {
    document.addEventListener(t, function (e) {
      try { LumenWeb.playing(playing()); } catch (x) {}
      var v = e.target;
      if (v && v.tagName === 'VIDEO') {
        if ((t === 'playing' || (t === 'durationchange' && !v.paused)) && !filled && !document.fullscreenElement && worthFilling(v)) fill(v);
        if (t === 'ended' && v === filled) unfill(false);
      }
    }, true);
  });
  // A film already playing when this script arrives (a page that started it before load finished).
  deepAll('video').forEach(function (v) { if (!v.paused && worthFilling(v)) fill(v); });
  // Players inside web components do not tell the page they started (their events stay inside):
  // for a while after OK is pressed, look for one.
  setInterval(function () {
    if (filled || Date.now() - pressedAt > 25000 || document.fullscreenElement) return;
    deepAll('video').forEach(function (v) { if (!filled && !v.paused && v.readyState > 2 && worthFilling(v)) fill(v); });
  }, 1000);

  window.lumenNav = { key: key, media: media, scrollAt: scrollAt, playing: playing, back: back, osd: osd };
  css();
  return 'ready';
})();
