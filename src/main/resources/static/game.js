// src/main/resources/static/game.js
(function () {
  // ================== 기본 상태/DOM 참조 ==================
  const myName = window.MY_NAME || document.body.getAttribute('data-my-name') || 'guest';

  const inputMsg  = document.getElementById('msg');
  const canvas    = document.getElementById('board');
  const ctx       = canvas.getContext('2d', { willReadFrequently: false });

  const roleLabel = document.getElementById('drawRoleHint');
  const adminWord = document.getElementById('adminWord');       // 관리자 전용 배너
  const adminBox  = document.getElementById('adminControls');   // 관리자 컨트롤 영역
  const rankingSelect = document.getElementById('rankingPeriod');
  const rankingList   = document.getElementById('ranking');

  const btnEraser = document.getElementById('btnEraser');       // 지우개 토글
  const btnUndo   = document.getElementById('btnUndo');         // 실행취소
  const btnClear  = document.getElementById('btnClear');        // 전체 지우기

  const btnRerollWord = document.getElementById('btnRerollWord'); // (출제자 전용) 제시어 다시받기
  const btnMeDraw     = document.getElementById('btnMeDraw');      // (참여자 전용) 내가 그리기

  const actions = [];           // 로컬 실행취소 히스토리 (서버가 /topic/undo를 보낼 때 사용)
  let currentAction = null;
  const MAX_ACTIONS = 1200;     // 최대 유지 액션 수(필요 시 조절)
  const MAX_SEGMENTS = 40000;   // 전체 세그먼트 상한
  let totalLocalSegments = 0;
  // ================== 상태 ==================
  let isDrawer = false;
  let isAdmin  = false;
  let drawing  = false;
  let prev     = null;

  let drawerName   = null;   // 현재 출제자 이름
  let mySecretWord = null;   // 내가 받은 제시어(출제자/관리자만)
  let wordLen      = null;   // 참가자에게 안내할 글자수

  let drawMode = 'pen';      // 'pen' | 'eraser'
  let currentActionId = null; // 드래그(스트로크) 식별자
  let isNewStroke     = false;// 첫 세그먼트 여부

  let rankingMode = 'LIVE';  // LIVE | DAILY | WEEKLY | MONTHLY
  let liveRankingCache = []; // 실시간 랭킹 데이터 캐시

  let lastLiveDrawAt = 0; // 최근 실시간 드로잉 수신 시각(ms) — /topic/draw 수신 때만 갱신
  // ================== 유틸 함수 ==================
  // 채팅 메세지 이름은 굵게, 메시지는 일반 텍스트
  function addChat(from, text, system = false) {
    const div = document.getElementById('chatlog');
    const line = document.createElement('div');
    if (system) line.classList.add('sys');

    const nameEl = document.createElement('strong'); // ← 굵게
    nameEl.textContent = from;

    line.appendChild(nameEl);
    line.appendChild(document.createTextNode(' : '));
    line.appendChild(document.createTextNode(text || '')); // 안전하게 기본값 처리

    div.appendChild(line);
    while (div.childElementCount > 200) {
      div.removeChild(div.firstChild);
    }
    div.scrollTop = div.scrollHeight;
  }

  function getPos(e) {
    // 마우스 이벤트면 offsetX/Y가 가장 정확
    if (typeof e.offsetX === 'number' && typeof e.offsetY === 'number') {
      return { x: e.offsetX, y: e.offsetY };
    }
    // 터치/기타는 rect 기준 계산
    const r = canvas.getBoundingClientRect();
    if (e.touches && e.touches[0]) {
      const t = e.touches[0];
      return { x: t.clientX - r.left, y: t.clientY - r.top };
    }
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  // 공용: 세그먼트 1개 그리기(지우개/펜 공통)
  function drawSegment(e){
    ctx.save();
    if (e.mode === 'eraser') {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.strokeStyle = 'rgba(0,0,0,1)';   // 색상 무시
    } else {
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = e.color;
    }
    ctx.lineWidth = e.width;
    ctx.lineCap   = 'round';
    ctx.beginPath(); ctx.moveTo(e.x1, e.y1); ctx.lineTo(e.x2, e.y2); ctx.stroke();
    ctx.restore();
  }

  function refreshMeDrawBtn(){
    if (!btnMeDraw) return;
    // PARTICIPANT에게만 보이도록 기존 style.display는 유지됨
    const elapsed = Date.now() - (lastLiveDrawAt || 0);
    const allow   = (lastLiveDrawAt === 0) || (elapsed > 30_000);
    btnMeDraw.disabled = !allow;
    // 툴팁으로 남은 시간 안내
    const remain = Math.max(0, 30000 - (elapsed|0));
    btnMeDraw.title = allow ? '지금 내가 그리기 가능'
        : `최근 드로잉이 있어 ${Math.ceil(remain/1000)}초 후 가능`;
  }
// 0.5초마다 UI 상태 갱신
  setInterval(refreshMeDrawBtn, 250);

  function resetLocalHistory() {
    actions.length = 0;
    currentAction = null;
    totalLocalSegments = 0;
  }

  function trimLocalHistory() {
    while (actions.length > MAX_ACTIONS || totalLocalSegments > MAX_SEGMENTS) {
      const dropped = actions.shift();
      if (dropped) {
        totalLocalSegments -= dropped.segs.length;
        if (totalLocalSegments < 0) totalLocalSegments = 0;
      }
    }
    if (actions.length === 0) currentAction = null;
  }

  // ================== 라벨/역할 UI ==================
  function updateRoleLabel() {
    const dName = drawerName || '미정';

    if (isDrawer) {
      const word = mySecretWord || '(제시어 수신 대기)';
      roleLabel.textContent = `이번 라운드의 Artist🎨는 당신입니다. 제시어 : ${word}`;
      if (isAdmin && mySecretWord) adminWord.textContent = '제시어 : ' + mySecretWord;
      return;
    }
    if (isAdmin) {
      const word = mySecretWord || '(제시어 수신 대기)';
      roleLabel.textContent = `출제자 : ${dName} , 제시어 : ${word}`;
      if (mySecretWord) adminWord.textContent = '제시어 : ' + mySecretWord;
      return;
    }
    const nVal = (typeof wordLen === 'number') ? wordLen : '?';
    roleLabel.textContent = `출제자는 ${dName}입니다. 제시어는 ${nVal}글자입니다.`;
  }
  function parseUserEntry(s){
    const m = (s || '').match(/^(.+?)\s+\((ADMIN|DRAWER|PARTICIPANT)\)$/);
    return m ? { name: m[1], role: m[2] } : { name: s, role: null };
  }
  function renderUsersAndRoles(list) {
    // 1) 파싱 + 인덱스 부여(안정 정렬용)
    const parsed = (list || []).map(parseUserEntry).map((u, i) => ({ ...u, _idx: i }));

    // 2) 정렬: DRAWER 최상단, 나머지는 기존 순서 유지
    const rolePriority = (r) => (r === 'DRAWER' ? 0 : 1);
    const sorted = parsed.slice().sort((a, b) => {
      const pa = rolePriority(a.role), pb = rolePriority(b.role);
      if (pa !== pb) return pa - pb;
      return a._idx - b._idx; // 원래 순서 유지
    });

    // 3) 참여자 목록 렌더 (한글 역할명 꼬리표)
    const ul = document.getElementById('users');
    ul.innerHTML = '';
    sorted.forEach(u => {
      const li = document.createElement('li');
      const roleKo =
          u.role === 'ADMIN' ? ' 🛠️' :
              u.role === 'DRAWER' ? ' 🎨' : '';
      li.textContent = u.name + roleKo;
      ul.appendChild(li);
    });

    // 4) 현재 출제자/내 역할 판별 (정렬과 무관)
    const drawer = parsed.find(u => u.role === 'DRAWER');
    drawerName = drawer ? drawer.name : null;

    const me = parsed.find(u => u.name === myName);
    isDrawer = !!(me && me.role === 'DRAWER');
    isAdmin  = !!(me && me.role === 'ADMIN');

    // 5) 버튼/배너 토글
    if (btnMeDraw)     btnMeDraw.style.display     = (!isDrawer && !isAdmin) ? '' : 'none';
    if (btnRerollWord) btnRerollWord.style.display = isDrawer ? '' : 'none';
    if (adminBox)      adminBox.style.display      = isAdmin ? '' : 'none';
    if (adminWord)     adminWord.style.display     = isAdmin ? '' : 'none';

    updateRoleLabel();
  }

  function renderRanking(list, emptyText = '데이터 없음') {
    if (!rankingList) return;
    rankingList.innerHTML = '';
    const arr = Array.isArray(list) ? list : [];
    if (arr.length === 0) {
      const li = document.createElement('li');
      li.textContent = emptyText;
      li.style.color = 'var(--muted)';
      rankingList.appendChild(li);
      return;
    }
    arr.forEach(e => {
      const li = document.createElement('li');
      li.textContent = `${e.name}/${e.team} : ${e.score}점`;
      rankingList.appendChild(li);
    });
  }

  async function loadRanking(period) {
    rankingMode = period;
    if (period === 'LIVE') {
      renderRanking(liveRankingCache, liveRankingCache.length ? '데이터 없음' : '실시간 집계 중');
      return;
    }
    renderRanking([], '불러오는 중...');
    try {
      const res = await fetch(`/api/rankings?period=${period}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      renderRanking(data);
    } catch (err) {
      renderRanking([], '조회 실패');
      addChat('SYSTEM', `랭킹 조회 실패: ${err.message}`, true);
    }
  }

  // ================== 채팅 ==================
  function sendChat() {
    const text = inputMsg.value.trim();
    if (!text) return;
    stomp.send('/app/chat.send', {}, JSON.stringify({ from: myName, text }));
    inputMsg.value = '';
  }

  // ================== WebSocket/STOMP ==================
  const socket = new SockJS('/ws');
  const stomp  = Stomp.over(socket);
  stomp.debug  = null;

  btnRerollWord && (btnRerollWord.disabled = true);

  stomp.connect({}, () => {
    // ---- 브로드캐스트 구독 ----
    stomp.subscribe('/topic/users', msg => renderUsersAndRoles(JSON.parse(msg.body)));

    stomp.subscribe('/topic/scoreboard', msg => {
      const arr = JSON.parse(msg.body);
      liveRankingCache = arr;
      if (rankingMode === 'LIVE') renderRanking(arr);
    });

    stomp.subscribe('/topic/chat', msg => {
      const data = JSON.parse(msg.body);
      addChat(data.from, data.text, !!data.system);
    });

    // ✅ draw: 단 1회 구독 (세그먼트 바로 그리기)
    stomp.subscribe('/topic/draw', msg => {
      const e = JSON.parse(msg.body);

      // 액션 경계(newStroke/actionId) 기준으로 그룹핑
      if (e.newStroke || !currentAction || currentAction.id !== e.actionId) {
        currentAction = { id: e.actionId, segs: [] };
        actions.push(currentAction);
      }
      currentAction.segs.push(e);
      totalLocalSegments++;
      trimLocalHistory();

      // 공용 그리기
      drawSegment(e);
      lastLiveDrawAt = Date.now();
      refreshMeDrawBtn();
    });

    // live micro-batch from server
    stomp.subscribe('/topic/draw-batch', msg => {
      const segs = JSON.parse(msg.body);
      if (!Array.isArray(segs)) return;
      for (const e of segs) {
        if (e.newStroke || !currentAction || currentAction.id !== e.actionId) {
          currentAction = { id: e.actionId, segs: [] };
          actions.push(currentAction);
        }
        currentAction.segs.push(e);
        totalLocalSegments++;
        trimLocalHistory();
        drawSegment(e);
      }
      lastLiveDrawAt = Date.now();
      refreshMeDrawBtn();
    });

    // 글자수(브로드캐스트)
    stomp.subscribe('/topic/wordlen', msg => {
      wordLen = parseInt(msg.body, 10);
      updateRoleLabel();
    });

    // 개인 큐: 스냅샷용 캔버스 클리어 (내 화면만)
    stomp.subscribe('/user/queue/canvas/clear', () => {
      ctx.clearRect(0,0,canvas.width,canvas.height);
      resetLocalHistory();
    });

    // 개인 큐: 스냅샷용 드로잉 (과거 선들 순차 재생, legacy 단일 세그먼트)
    stomp.subscribe('/user/queue/draw', msg => {
      const e = JSON.parse(msg.body);

      // 히스토리에도 쌓아둬야 이후 'undo' 재생이 정상 동작
      if (e.newStroke || !currentAction || currentAction.id !== e.actionId) {
        currentAction = { id: e.actionId, segs: [] };
        actions.push(currentAction);
      }
      currentAction.segs.push(e);
      totalLocalSegments++;
      trimLocalHistory();

      drawSegment(e); // 즉시 화면에 그리기
    });

    // 개인 큐: 스냅샷용 드로잉 배치 (서버가 ~500개씩 전송)
    stomp.subscribe('/user/queue/draw-batch', msg => {
      const segs = JSON.parse(msg.body);
      if (!Array.isArray(segs)) return;
      for (const e of segs) {
        if (e.newStroke || !currentAction || currentAction.id !== e.actionId) {
          currentAction = { id: e.actionId, segs: [] };
          actions.push(currentAction);
        }
        currentAction.segs.push(e);
        totalLocalSegments++;
        trimLocalHistory();
        drawSegment(e);
      }
    });

    // (선택) 강제 로그아웃 신호 처리
    stomp.subscribe('/user/queue/force-logout', msg => {
      let allowSid = '';
      try { allowSid = JSON.parse(msg.body).allowSid; } catch (e) {}
      if (window.SESSION_ID && window.SESSION_ID !== allowSid) location.href = '/logout';
    });

    // ---- 개인 큐(스냅샷/제시어) ----
    stomp.subscribe('/user/queue/users',      msg => renderUsersAndRoles(JSON.parse(msg.body)));
    stomp.subscribe('/user/queue/scoreboard', msg => {
      const arr = JSON.parse(msg.body);
      liveRankingCache = arr;
      if (rankingMode === 'LIVE') renderRanking(arr);
    });


    stomp.subscribe('/user/queue/word',    msg => { mySecretWord = msg.body || null; updateRoleLabel(); });
    stomp.subscribe('/user/queue/wordlen', msg => {
      const n = parseInt(msg.body, 10);
      if (!Number.isNaN(n)) wordLen = n;
      updateRoleLabel();
    });

    stomp.subscribe('/user/queue/errors', msg => {
      const err = msg.body || '요청이 거절되었습니다.';
      // 원하는 UX로 처리 (알림창 or 채팅 로그)
      addChat('SYSTEM', err, true);
      // 버튼 상태 재확인
      refreshMeDrawBtn();
    });

    // ✅ 서버가 undo "명령"만 보낼 때를 지원 (/topic/undo)
    stomp.subscribe('/topic/undo', msg => {
      const { actionId } = JSON.parse(msg.body) || {};
      let removed = false;
      let removedCount = 0;
      for (let i = actions.length - 1; i >= 0; i--) {
        if (actions[i].id === actionId) {
          const [removedAction] = actions.splice(i, 1);
          removedCount = removedAction ? removedAction.segs.length : 0;
          removed = true;
          break;
        }
      }
      if (removed) {
        totalLocalSegments = Math.max(0, totalLocalSegments - removedCount);
        currentAction = actions.length ? actions[actions.length - 1] : null;
        ctx.clearRect(0,0,canvas.width,canvas.height);
        for (const a of actions) for (const s of a.segs) drawSegment(s);
      }
    });

// ✅ clear는 단 1회 구독 (서버가 clear→재생 방식일 때도 OK)
    stomp.subscribe('/topic/canvas/clear', () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      // 로컬 히스토리도 초기화 (서버가 곧 재생을 보내면 자연스럽게 다시 채워짐)
      resetLocalHistory();

      // 도구/라벨 상태 초기화(기존 로직 유지)
      drawMode = 'pen';
      btnEraser && btnEraser.classList.remove('active');
      if (!isDrawer && !isAdmin) mySecretWord = null;
      updateRoleLabel();
    });
    // 연결 직후 현재 상태 요청
    stomp.send('/app/state.sync', {}, '{}');

    // 연결 이후 버튼 활성화
    btnRerollWord && (btnRerollWord.disabled = false);

    addChat('SYSTEM', '피카솔브 서비스에 연결되었습니다.', true);
  });

  // ================== UI 이벤트 바인딩 ==================
  document.getElementById('send').onclick = sendChat;
  inputMsg.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); sendChat(); }
  });

  rankingSelect && rankingSelect.addEventListener('change', (e) => {
    const val = e.target.value;
    loadRanking(val);
  });

  // 관리자: 출제자 지정
  document.getElementById('btnSetDrawer').onclick = () => {
    const name = document.getElementById('drawerName').value.trim();
    if (name) stomp.send('/app/admin.setDrawer', {}, JSON.stringify({ name }));
  };

  // (출제자) 제시어 다시받기
  btnRerollWord && (btnRerollWord.onclick = () => {
    if (!isDrawer) return;
    stomp.send('/app/word.reroll', {}, '{}');
  });

  // (참여자) 내가 그리기
  btnMeDraw && (btnMeDraw.onclick = () => {
    stomp.send('/app/drawer.me', {}, '{}');
  });

  // 지우개/실행취소/전체지우기
  btnEraser && (btnEraser.onclick = () => {
    drawMode = (drawMode === 'eraser') ? 'pen' : 'eraser';
    btnEraser.classList.toggle('active', drawMode === 'eraser');
  });
  btnUndo  && (btnUndo.onclick  = () => { if (isDrawer) stomp.send('/app/draw.undo',  {}, '{}'); });
  btnClear && (btnClear.onclick = () => { if (isDrawer) stomp.send('/app/canvas.clear', {}, '{}'); });

  // 단축키: Ctrl+Z, T
  document.addEventListener('keydown', (e) => {
    if (e.ctrlKey && (e.key === 'z' || e.key === 'Z')) { e.preventDefault(); btnUndo && btnUndo.click(); }
    if (e.key && e.key.toLowerCase() === 't') { btnEraser && btnEraser.click(); }
  });

  // ================== 드로잉 송신(마우스/터치) ==================
  canvas.addEventListener('mousedown', e => {
    if (!isDrawer) return;
    drawing = true;
    prev = getPos(e);
    currentActionId = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2,8);
    isNewStroke = true;
  });
  canvas.addEventListener('mouseup',   () => { drawing = false; prev = null; currentActionId = null; isNewStroke = false; });
  canvas.addEventListener('mouseleave',() => { drawing = false; prev = null; currentActionId = null; isNewStroke = false; });

  canvas.addEventListener('mousemove', e => {
    if (!drawing || !isDrawer) return;
    const cur = getPos(e);
    const payload = {
      x1: prev.x, y1: prev.y, x2: cur.x, y2: cur.y,
      width: +document.getElementById('width').value,
      color: document.getElementById('color').value,
      mode: drawMode,
      actionId: currentActionId,
      newStroke: isNewStroke
    };
    stomp.send('/app/draw.stroke', {}, JSON.stringify(payload));
    prev = cur;
    isNewStroke = false;
  });

  canvas.addEventListener('touchstart', e => {
    if (!isDrawer) return;
    drawing = true;
    prev = getPos(e);
    currentActionId = Date.now().toString(36) + '-' + Math.random().toString(36).slice(2,8);
    isNewStroke = true;
  });
  canvas.addEventListener('touchend',   () => { drawing = false; prev = null; currentActionId = null; isNewStroke = false; });
  canvas.addEventListener('touchmove',  e => {
    if (!drawing || !isDrawer) return;
    const cur = getPos(e);
    const payload = {
      x1: prev.x, y1: prev.y, x2: cur.x, y2: cur.y,
      width: +document.getElementById('width').value,
      color: document.getElementById('color').value,
      mode: drawMode,
      actionId: currentActionId,
      newStroke: isNewStroke
    };
    stomp.send('/app/draw.stroke', {}, JSON.stringify(payload));
    prev = cur;
    isNewStroke = false;
    e.preventDefault();
  }, { passive:false });

})();
// === 헤더 시계 (game.js 맨 아래) ===
(function startClock(){
  const el = document.getElementById('clock');
  if (!el) return; // 요소 없으면 종료

  const pad = n => String(n).padStart(2, '0');
  const tick = () => {
    const d = new Date();
    el.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  };

  tick(); // 즉시 1회 반영
  if (window.__clockTimer) clearInterval(window.__clockTimer); // 중복 방지
  window.__clockTimer = setInterval(tick, 1000);
})();
