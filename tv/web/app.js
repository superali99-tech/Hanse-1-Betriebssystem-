/*
 * NovaOS TV Web - TV channels and Nova Filme for the web browser of any smart TV.
 * ES5 on purpose (TV browsers are often old). Data: data/sender.json, data/filme.json
 * (built by tv/web/build.sh with the same logic as the Android app).
 * Remote: arrows + OK; Back = close; in the TV player up/down or P+/P- change the
 * channel, digits select a number. Everything can also be clicked with the pointer.
 */
(function () {
  'use strict';

  var DE = (navigator.language || 'de').toLowerCase().indexOf('de') === 0;
  var T = DE ? {
    tv: 'Fernsehen', tvCount: '{0} Sender im Internet', films: 'Nova Filme',
    filmsSub: 'Kostenlose Filme, Serien und Dokus von offiziellen YouTube-Kanälen · Stand: {0}',
    cats: { FILME: 'Spielfilme', SERIEN: 'Serien', DOKUS: 'Dokus', KINDER: 'Kinder & Familie' },
    hint: 'Pfeiltasten: auswählen · OK: öffnen · Zurück: schließen',
    loading: 'Wird geladen …', loadFailed: 'Die Filmliste konnte nicht geladen werden. Bitte später erneut öffnen.',
    badgeHint: '▲▼ Sender wechseln · OK: Senderliste · Zurück: beenden',
    list: 'Senderliste', full: 'Vollbild', close: 'Zurück', noChannel: 'kein Sender',
    geo: '{0} ist gerade nicht empfangbar. Die meisten deutschen Sender senden im Internet nur innerhalb Deutschlands.',
    offline: '{0} ist gerade nicht erreichbar. Bitte die Internetverbindung prüfen.',
    error: '{0} kann gerade nicht abgespielt werden ({1}).',
    noHls: 'Dieser Browser kann die Livestreams leider nicht abspielen.',
    tapToPlay: 'OK drücken zum Abspielen', retry: 'OK: erneut versuchen · ▲▼ anderer Sender',
    pause: 'Pause', play: 'Weiter', paused: 'Pausiert', ended: 'Zu Ende',
    filmHint: 'OK: Pause / Weiter · ◀ ▶ 10 Sekunden · Zurück: beenden',
    blocked: 'Dieses Video ist hier nicht verfügbar – oft nur in Deutschland oder nur auf YouTube (Fehler {0}).',
    ytError: 'Das Video kann nicht abgespielt werden (Fehler {0}).'
  } : {
    tv: 'TV', tvCount: '{0} channels online', films: 'Nova Filme',
    filmsSub: 'Free movies, series and documentaries from official YouTube channels · updated {0}',
    cats: { FILME: 'Movies', SERIEN: 'Series', DOKUS: 'Documentaries', KINDER: 'Kids & family' },
    hint: 'Arrow keys: select · OK: open · Back: close',
    loading: 'Loading …', loadFailed: 'The film list could not be loaded. Please try again later.',
    badgeHint: '▲▼ change channel · OK: channel list · Back: exit',
    list: 'Channel list', full: 'Full screen', close: 'Back', noChannel: 'no channel',
    geo: '{0} cannot be received right now. Most German channels stream on the internet only within Germany.',
    offline: '{0} cannot be reached right now. Please check the internet connection.',
    error: '{0} cannot be played right now ({1}).',
    noHls: 'This browser cannot play the live streams.',
    tapToPlay: 'Press OK to play', retry: 'OK: try again · ▲▼ other channel',
    pause: 'Pause', play: 'Continue', paused: 'Paused', ended: 'Finished',
    filmHint: 'OK: pause / continue · ◀ ▶ 10 seconds · Back: end',
    blocked: 'This video is not available here – often only in Germany or only on YouTube (error {0}).',
    ytError: 'The video cannot be played (error {0}).'
  };

  function fmt(s) {
    var a = arguments;
    return s.replace(/\{(\d)\}/g, function (m, i) { return a[+i + 1]; });
  }
  function $(id) { return document.getElementById(id); }
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function show(e, on) { e.classList[on ? 'remove' : 'add']('hidden'); }
  function getJSON(url, ok, fail) {
    var x = new XMLHttpRequest();
    x.open('GET', url + '?v=' + Math.floor(Date.now() / 600000), true);  // fresh every 10 min
    x.onload = function () {
      if (x.status !== 200) return fail && fail();
      try { ok(JSON.parse(x.responseText)); } catch (e) { if (fail) fail(); }
    };
    x.onerror = function () { if (fail) fail(); };
    x.send();
  }
  function time(sec) {
    sec = sec > 0 && isFinite(sec) ? Math.floor(sec) : 0;
    var h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60, s = sec % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }
  function darker(hex) {
    var n = parseInt(hex.slice(1), 16), r = (n >> 16) * 0.7, g = ((n >> 8) & 255) * 0.7, b = (n & 255) * 0.7;
    return 'rgb(' + Math.round(r) + ',' + Math.round(g) + ',' + Math.round(b) + ')';
  }

  var channels = [], view = 'home';

  // ------------------------------------------------------------ start page
  function clock() {
    var d = new Date();
    $('time').textContent = (d.getHours() < 10 ? '0' : '') + d.getHours() + ':' + (d.getMinutes() < 10 ? '0' : '') + d.getMinutes();
    try {
      $('date').textContent = d.toLocaleDateString(DE ? 'de-DE' : undefined, { weekday: 'long', day: 'numeric', month: 'long' });
    } catch (e) {
      $('date').textContent = d.toDateString();
    }
  }

  function buildChannels(list) {
    channels = list;
    $('tv-title').textContent = T.tv + '  ·  ' + fmt(T.tvCount, list.length);
    var box = $('channels');
    box.innerHTML = '';
    list.forEach(function (c, i) {
      var b = el('button', 'tile channel focusable');
      b.style.background = 'linear-gradient(135deg, ' + c.color + ', ' + darker(c.color) + ')';
      b.appendChild(el('span', 'no', String(c.number)));
      b.appendChild(el('span', 'name' + (c.name.length > 12 ? ' long' : ''), c.name));
      b.setAttribute('aria-label', c.number + ' ' + c.name);
      b.onclick = function () { openTv(i); };
      box.appendChild(b);
    });
    var first = box.querySelector('.focusable');
    if (first && !document.activeElement.classList.contains('focusable')) first.focus();
  }

  function buildFilms(data) {
    var box = $('films');
    box.innerHTML = '';
    var when = new Date(data.generated);
    var stamp = isNaN(when) ? '' : when.toLocaleDateString(DE ? 'de-DE' : undefined, { day: 'numeric', month: 'numeric' }) +
      ', ' + (when.getHours() < 10 ? '0' : '') + when.getHours() + ':' + (when.getMinutes() < 10 ? '0' : '') + when.getMinutes();
    $('films-sub').textContent = fmt(T.filmsSub, stamp);
    data.rows.forEach(function (row) {
      box.appendChild(el('h3', 'row', (T.cats[row.category] || row.category) + '  ·  ' + row.videos.length));
      var grid = el('div', 'grid');
      row.videos.forEach(function (v) {
        var b = el('button', 'tile video focusable');
        var img = el('img');
        img.alt = '';
        img.setAttribute('loading', 'lazy');
        img.src = 'https://i.ytimg.com/vi/' + v.id + '/mqdefault.jpg';
        b.appendChild(img);
        var cap = el('span', 'cap');
        cap.appendChild(el('b', null, v.title));
        cap.appendChild(el('small', null, v.source));
        b.appendChild(cap);
        b.setAttribute('aria-label', v.title + ', ' + v.source);
        b.onclick = function () { openFilm(v); };
        grid.appendChild(b);
      });
      box.appendChild(grid);
    });
  }

  // ------------------------------------------------------------ remote control: spatial navigation
  function focusables() {
    var scope = view === 'tv' && !$('tv-list').classList.contains('hidden') ? '#tv-list' : '#' + view;
    return Array.prototype.filter.call(document.querySelectorAll(scope + ' .focusable'), function (e) {
      return e.offsetParent !== null;
    });
  }

  function move(dir) {
    var list = focusables(), cur = document.activeElement;
    if (!list.length) return;
    if (list.indexOf(cur) < 0) { focus(list[0]); return; }
    var r = cur.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    var best = null, bestScore = 1e9;
    list.forEach(function (e) {
      if (e === cur) return;
      var q = e.getBoundingClientRect(), x = q.left + q.width / 2, y = q.top + q.height / 2;
      var main, cross;
      if (dir === 'left') { main = cx - x; cross = Math.abs(y - cy); }
      else if (dir === 'right') { main = x - cx; cross = Math.abs(y - cy); }
      else if (dir === 'up') { main = cy - y; cross = Math.abs(x - cx); }
      else { main = y - cy; cross = Math.abs(x - cx); }
      if (main <= 2) return;
      var score = main + cross * (dir === 'left' || dir === 'right' ? 4 : 1.5);
      if (score < bestScore) { bestScore = score; best = e; }
    });
    if (best) focus(best);
  }

  function focus(e) {
    e.focus();
    if (e.scrollIntoView) {
      try { e.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (x) { e.scrollIntoView(false); }
    }
  }

  var KEY = {
    LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13,
    BACK: [8, 27, 10009, 461, 166], PLAYPAUSE: [10252, 179, 463], PLAY: [415, 250], PAUSE: [19],
    FF: [417, 228], RW: [412, 227], CHUP: [427, 33], CHDOWN: [428, 34], INFO: [457]
  };
  function is(code, k) { return k instanceof Array ? k.indexOf(code) >= 0 : code === k; }

  document.addEventListener('keydown', function (ev) {
    var k = ev.keyCode, handled = true;
    if (view === 'home') {
      if (k === KEY.LEFT) move('left');
      else if (k === KEY.RIGHT) move('right');
      else if (k === KEY.UP) move('up');
      else if (k === KEY.DOWN) move('down');
      else handled = false;
    } else if (view === 'tv') {
      handled = tvKey(k, ev);
    } else if (view === 'film') {
      handled = filmKey(k);
    }
    if (handled) ev.preventDefault();
  });

  // ------------------------------------------------------------ views and browser history
  function setView(v) {
    view = v;
    show($('home'), v === 'home');
    show($('tv'), v === 'tv');
    show($('film'), v === 'film');
  }

  function openView(v) {
    if (history.pushState) history.pushState({ nova: v }, '', '#' + v);
    setView(v);
  }

  function closeView() {
    if (history.state && history.state.nova) history.back(); else backHome();
  }

  function backHome() {
    stopTv();
    stopFilm();
    var from = view;
    setView('home');
    var again = from === 'tv' ? $('channels').children[tvIndex] : lastFilmTile;
    if (again) focus(again);
  }

  window.addEventListener('popstate', function () {
    if (view !== 'home' && !(history.state && history.state.nova === view)) backHome();
  });

  // ------------------------------------------------------------ TV player (HLS live streams)
  var video = $('video'), hls = null, tvIndex = 0, tvError = false, digits = '', digitTimer = 0, badgeTimer = 0, barTimer = 0;

  function openTv(i) {
    openView('tv');
    buildList();
    tune(i);
  }

  function buildList() {
    var box = $('tv-list-items');
    if (box.children.length) return;
    $('list-title').textContent = T.list;
    channels.forEach(function (c, i) {
      var b = el('button', 'focusable');
      b.appendChild(el('span', 'n', String(c.number)));
      b.appendChild(document.createTextNode(c.name));
      b.onclick = function () { hideList(); if (i !== tvIndex || tvError) tune(i); };
      box.appendChild(b);
    });
  }

  function stopTv() {
    if (hls) { hls.destroy(); hls = null; }
    video.removeAttribute('src');
    try { video.load(); } catch (e) { /* ignore */ }
    hideList();
  }

  function tune(i) {
    var n = channels.length;
    i = ((i % n) + n) % n;
    tvIndex = i;
    var ch = channels[i];
    stopTv();
    tvError = false;
    show($('tv-msg'), false);
    show($('spinner'), true);
    badge(String(ch.number), ch.name);
    var items = $('tv-list-items').children;
    for (var j = 0; j < items.length; j++) items[j].classList.toggle('current', j === i);
    if (window.Hls && Hls.isSupported()) {
      hls = new Hls({ capLevelToPlayerSize: true, maxBufferLength: 20 });
      var recovered = false;
      hls.on(Hls.Events.MANIFEST_PARSED, function () { hls.subtitleDisplay = false; play(); });
      hls.on(Hls.Events.ERROR, function (e, d) {
        if (!d.fatal) return;
        if (d.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) { recovered = true; hls.recoverMediaError(); return; }
        var code = d.response && d.response.code;
        if (code === 403 || code === 451) tvFail(fmt(T.geo, ch.name));
        else if (d.type === Hls.ErrorTypes.NETWORK_ERROR && !code) tvFail(fmt(T.offline, ch.name));
        else tvFail(fmt(T.error, ch.name, code ? 'HTTP ' + code : d.details));
      });
      hls.loadSource(ch.url);
      hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = ch.url;  // native HLS (Safari, some TV browsers)
      play();
    } else {
      tvFail(T.noHls);
    }
  }

  function play() {
    var p = video.play();
    if (p && p.catch) p.catch(function () { show($('spinner'), false); msg(T.tapToPlay); });
  }

  video.addEventListener('playing', function () { show($('spinner'), false); if (!tvError) show($('tv-msg'), false); });
  video.addEventListener('waiting', function () { if (!tvError) show($('spinner'), true); });
  video.addEventListener('error', function () {
    if (!hls && view === 'tv' && video.getAttribute('src')) tvFail(fmt(T.error, channels[tvIndex].name, 'media'));
  });

  function msg(text) { $('tv-msg-text').textContent = text; show($('tv-msg'), true); }

  function tvFail(text) {
    tvError = true;
    show($('spinner'), false);
    msg(text + '\n' + T.retry);
  }

  function badge(no, name) {
    $('badge-no').textContent = no;
    $('badge-name').textContent = name;
    $('badge-hint').textContent = T.badgeHint;
    show($('badge'), true);
    clearTimeout(badgeTimer);
    badgeTimer = setTimeout(function () { show($('badge'), false); }, 4500);
  }

  function showBar() {
    $('btn-list').textContent = T.list;
    $('btn-full').textContent = T.full;
    $('btn-close-tv').textContent = T.close;
    show($('tv-bar'), true);
    clearTimeout(barTimer);
    barTimer = setTimeout(function () { show($('tv-bar'), false); }, 5000);
  }

  function showList() {
    show($('badge'), false);
    show($('tv-list'), true);
    var cur = $('tv-list-items').children[tvIndex];
    if (cur) focus(cur);
  }
  function hideList() { show($('tv-list'), false); }

  function digit(d) {
    if (digits.length < 3) digits += d;
    badge(digits, '');
    clearTimeout(digitTimer);
    digitTimer = setTimeout(function () {
      var n = parseInt(digits, 10);
      digits = '';
      for (var i = 0; i < channels.length; i++) if (channels[i].number === n) { hideList(); tune(i); return; }
      badge(String(n), T.noChannel);
    }, digits.length >= 2 ? 900 : 2500);
  }

  function tvKey(k, ev) {
    if (k >= 48 && k <= 57) { digit(String(k - 48)); return true; }
    if (!$('tv-list').classList.contains('hidden')) {
      if (is(k, KEY.BACK) || k === KEY.LEFT) { hideList(); return true; }
      if (k === KEY.UP) { move('up'); return true; }
      if (k === KEY.DOWN) { move('down'); return true; }
      return false;  // OK clicks the focused channel
    }
    if (document.activeElement && document.activeElement.parentNode === $('tv-bar') && k === KEY.ENTER) return false;
    if (k === KEY.UP || is(k, KEY.CHUP)) tune(tvIndex + 1);
    else if (k === KEY.DOWN || is(k, KEY.CHDOWN)) tune(tvIndex - 1);
    else if (k === KEY.ENTER) { if (tvError) tune(tvIndex); else if (video.paused) play(); else showList(); }
    else if (k === KEY.LEFT || k === KEY.RIGHT || is(k, KEY.INFO)) { badge(String(channels[tvIndex].number), channels[tvIndex].name); showBar(); }
    else if (is(k, KEY.BACK)) closeView();
    else return false;
    return true;
  }

  $('tv').addEventListener('mousemove', showBar);
  $('tv').addEventListener('click', function (e) { if (e.target === video) showBar(); });
  $('tv-bar').addEventListener('click', function (e) {
    var a = e.target.getAttribute('data-act');
    if (a === 'next') tune(tvIndex + 1);
    else if (a === 'prev') tune(tvIndex - 1);
    else if (a === 'list') showList();
    else if (a === 'close') closeView();
    else if (a === 'full') {
      var d = document.documentElement, f = d.requestFullscreen || d.webkitRequestFullscreen;
      if (f) f.call(d);
    }
  });

  // ------------------------------------------------------------ Nova Filme player (embedded YouTube player)
  var yt = null, ytLoading = false, ytWaiting = null, filmTimer = 0, lastFilmTile = null, film = null;

  function loadYouTubeApi(cb) {
    if (window.YT && YT.Player) return cb();
    ytWaiting = cb;
    if (ytLoading) return;
    ytLoading = true;
    window.onYouTubeIframeAPIReady = function () { if (ytWaiting) { var f = ytWaiting; ytWaiting = null; f(); } };
    var s = document.createElement('script');
    s.src = 'https://www.youtube.com/iframe_api';
    document.head.appendChild(s);
  }

  function openFilm(v) {
    lastFilmTile = document.activeElement;
    film = { v: v, state: -2, error: 0 };
    openView('film');
    $('film-title').textContent = v.title;
    $('film-meta').textContent = v.source;
    $('film-state').textContent = T.loading;
    $('film-progress').style.width = '0';
    $('btn-toggle').textContent = T.pause;
    $('btn-close-film').textContent = T.close;
    $('film-hint').textContent = T.filmHint;
    $('btn-toggle').focus();
    loadYouTubeApi(function () {
      if (view !== 'film' || !film || film.v !== v) return;
      $('yt-box').innerHTML = '<div id="yt"></div>';
      yt = new YT.Player('yt', {
        width: '100%', height: '100%', videoId: v.id,
        host: 'https://www.youtube-nocookie.com',
        playerVars: { autoplay: 1, controls: 1, rel: 0, fs: 0, playsinline: 1, iv_load_policy: 3,
          hl: DE ? 'de' : 'en', origin: location.origin },
        events: {
          onReady: function (e) { e.target.playVideo(); },
          onStateChange: function (e) { if (film) film.state = e.data; filmStatus(); },
          onError: function (e) { if (film) film.error = e.data; filmStatus(); }
        }
      });
      clearInterval(filmTimer);
      filmTimer = setInterval(filmStatus, 500);
    });
  }

  function stopFilm() {
    clearInterval(filmTimer);
    if (yt) { try { yt.destroy(); } catch (e) { /* ignore */ } yt = null; }
    $('yt-box').innerHTML = '<div id="yt"></div>';
    film = null;
  }

  function filmStatus() {
    if (!film) return;
    if (yt && yt.getCurrentTime) {
      var t = yt.getCurrentTime() || 0, d = yt.getDuration() || 0;
      if (d > 0) {
        $('film-meta').textContent = film.v.source + '  ·  ' + time(t) + ' / ' + time(d);
        $('film-progress').style.width = Math.min(100, t * 100 / d) + '%';
      }
    }
    var s = film.state, text = '';
    if (film.error) {
      text = film.error === 101 || film.error === 150 || film.error === 153 ? fmt(T.blocked, film.error) : fmt(T.ytError, film.error);
    } else if (s === 2) text = T.paused;
    else if (s === 0) text = T.ended;
    else if (s === 3 || s === -1 || s === -2) text = T.loading;
    $('film-state').textContent = text;
    $('btn-toggle').textContent = s === 1 ? T.pause : T.play;
  }

  function toggle() {
    if (!yt || !yt.getPlayerState) return;
    if (yt.getPlayerState() === 1) yt.pauseVideo(); else yt.playVideo();
  }
  var seekTarget = 0, seekAt = 0;
  function seek(delta) {
    if (!yt || !yt.getCurrentTime) return;
    // pressed again quickly: continue from the last target (the player is still seeking)
    var base = Date.now() - seekAt < 1500 ? seekTarget : yt.getCurrentTime();
    seekTarget = Math.max(0, base + delta);
    seekAt = Date.now();
    yt.seekTo(seekTarget, true);
  }

  function filmKey(k) {
    var a = document.activeElement;
    if (k === KEY.ENTER && a && a.getAttribute('data-act') && a.getAttribute('data-act') !== 'toggle') return false;
    if (k === KEY.ENTER || is(k, KEY.PLAYPAUSE)) toggle();
    else if (is(k, KEY.PLAY)) { if (yt && yt.playVideo) yt.playVideo(); }
    else if (is(k, KEY.PAUSE)) { if (yt && yt.pauseVideo) yt.pauseVideo(); }
    else if (k === KEY.LEFT || is(k, KEY.RW)) seek(-10);
    else if (k === KEY.RIGHT || is(k, KEY.FF)) seek(10);
    else if (k === KEY.UP || k === KEY.DOWN) $('btn-toggle').focus();
    else if (is(k, KEY.BACK)) closeView();
    else return false;
    return true;
  }

  document.querySelector('.film-buttons').addEventListener('click', function (e) {
    var a = e.target.getAttribute('data-act');
    if (a === 'toggle') toggle();
    else if (a === 'back10') seek(-10);
    else if (a === 'fwd10') seek(10);
    else if (a === 'close') closeView();
  });
  // keep the remote's keys on this page when the player frame took the focus (pointer click)
  window.addEventListener('blur', function () {
    if (view === 'film') setTimeout(function () { $('btn-toggle').focus(); }, 0);
  });

  // ------------------------------------------------------------ start
  $('hint').textContent = T.hint;
  $('tv-title').textContent = T.tv;
  $('films-title').textContent = T.films;
  $('films').appendChild(el('div', 'info-tile', T.loading));
  clock();
  setInterval(clock, 20000);
  if (location.hash && history.replaceState) history.replaceState(null, '', location.pathname);
  getJSON('data/sender.json', buildChannels, function () { $('channels').appendChild(el('div', 'info-tile', T.loadFailed)); });
  getJSON('data/filme.json', buildFilms, function () {
    $('films').innerHTML = '';
    $('films').appendChild(el('div', 'info-tile', T.loadFailed));
  });
})();
