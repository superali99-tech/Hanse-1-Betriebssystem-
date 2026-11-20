// Test partner for Nova Chat: a minimal Matrix client in headless Chromium with fake
// camera/microphone. Logs in, accepts invitations, answers messages ("Echo: ...") and
// calls (m.call.* version 1, WebRTC), and can start a call itself.
//
//   node tests/chat/peer.js BASE USER PASSWORD [call ROOM_ID [video]]
// Prints one line per event (for the test log). Only for local test servers.
const { chromium } = require('playwright');
const [base, user, password, cmd, callRoom, callVideo] = process.argv.slice(2);

(async () => {
  const browser = await chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, permissions: ['microphone', 'camera'] });
  const page = await ctx.newPage();
  page.on('console', m => console.log('[peer] ' + m.text()));
  // a page on the server itself: secure context (https) and same origin for fetch
  await page.goto(base + '/_matrix/client/versions');
  await page.evaluate(async ({ base, user, password, cmd, callRoom, callVideo }) => {
    const api = async (method, path, body, token) => {
      const r = await fetch(base + '/_matrix/client/v3' + path, { method, headers: Object.assign({ 'Content-Type': 'application/json' },
        token ? { Authorization: 'Bearer ' + token } : {}), body: body ? JSON.stringify(body) : undefined });
      return r.json();
    };
    const login = await api('POST', '/login', { type: 'm.login.password', identifier: { type: 'm.id.user', user }, password });
    const token = login.access_token, me = login.user_id;
    console.log('logged in ' + me);
    let txn = Date.now();
    const send = (room, type, content) => api('PUT', `/rooms/${encodeURIComponent(room)}/send/${type}/${txn++}`, content, token);
    let pc = null, callId = null, room = null, party = 'peer' + Date.now(), pending = [], remoteSet = false;
    const setup = async (r, video) => {
      room = r;
      pc = new RTCPeerConnection({ iceServers: [] });
      pc.onicecandidate = e => { if (e.candidate) send(room, 'm.call.candidates', { call_id: callId, party_id: party, version: '1',
        candidates: [{ candidate: e.candidate.candidate, sdpMid: e.candidate.sdpMid, sdpMLineIndex: e.candidate.sdpMLineIndex }] }); };
      pc.oniceconnectionstatechange = () => console.log('ice ' + pc.iceConnectionState);
      pc.ontrack = e => console.log('remote track ' + e.track.kind);
      const s = await navigator.mediaDevices.getUserMedia({ audio: true, video: !!video });
      s.getTracks().forEach(t => pc.addTrack(t, s));
    };
    const drain = () => { if (!remoteSet) return; while (pending.length) pc.addIceCandidate(pending.shift()).catch(e => console.log('cand ' + e)); };
    if (cmd === 'call') {
      callId = 'peer' + Date.now();
      await setup(callRoom, callVideo === 'video');
      const offer = await pc.createOffer(); await pc.setLocalDescription(offer);
      await send(callRoom, 'm.call.invite', { call_id: callId, party_id: party, version: '1', lifetime: 60000, offer: { type: 'offer', sdp: offer.sdp } });
      console.log('calling ' + callRoom);
    }
    let since = '';
    for (let i = 0; i < 100000; i++) {
      const s = await api('GET', `/sync?timeout=20000${since ? '&since=' + encodeURIComponent(since) : ''}`, null, token);
      const first = !since;
      since = s.next_batch;
      for (const r in (s.rooms && s.rooms.invite) || {}) { await api('POST', `/join/${encodeURIComponent(r)}`, {}, token); console.log('joined ' + r); }
      if (first) continue;
      for (const r in (s.rooms && s.rooms.join) || {}) {
        for (const e of (s.rooms.join[r].timeline || {}).events || []) {
          if (e.sender === me) continue;
          if (e.type === 'm.room.message') {
            console.log('message ' + e.content.body);
            await send(r, 'm.room.message', { msgtype: 'm.text', body: 'Echo: ' + e.content.body });
          } else if (e.type === 'm.call.invite') {
            console.log('incoming call ' + e.content.call_id + (e.content.offer.sdp.includes('m=video') ? ' video' : ' voice'));
            callId = e.content.call_id;
            await setup(r, e.content.offer.sdp.includes('m=video'));
            await pc.setRemoteDescription(e.content.offer); remoteSet = true; drain();
            const a = await pc.createAnswer(); await pc.setLocalDescription(a);
            await send(r, 'm.call.answer', { call_id: callId, party_id: party, version: '1', answer: { type: 'answer', sdp: a.sdp } });
            console.log('answered');
          } else if (e.type === 'm.call.answer' && e.content.call_id === callId) {
            await pc.setRemoteDescription(e.content.answer); remoteSet = true; drain();
            await send(r, 'm.call.select_answer', { call_id: callId, party_id: party, version: '1', selected_party_id: e.content.party_id });
            console.log('call answered by ' + e.sender);
          } else if (e.type === 'm.call.candidates' && e.content.call_id === callId) {
            for (const c of e.content.candidates) pending.push(c);
            drain();
          } else if ((e.type === 'm.call.hangup' || e.type === 'm.call.reject') && e.content.call_id === callId) {
            console.log('call ended (' + e.type + ')');
            if (pc) pc.close(); pc = null; remoteSet = false; pending = [];
          }
        }
      }
    }
  }, { base, user, password, cmd, callRoom, callVideo });
})();
