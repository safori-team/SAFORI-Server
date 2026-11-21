// node --test infra/edge/worker/  (의존성 없음, Node 18+)
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { handleFetch, handleScheduled, resetCache, PARKING_HEADER } from './parking.js';

const NOW = Date.parse('2026-11-18T12:00:00Z');
const API = 'https://api.example.com';

function kv(initial = {}) {
  const store = new Map(Object.entries(initial).map(([k, v]) => [k, JSON.stringify(v)]));
  let writes = 0;
  return {
    store,
    get writes() { return writes; },
    async get(key, type) {
      const v = store.get(key);
      if (v === undefined) return null;
      return type === 'json' ? JSON.parse(v) : v;
    },
    async put(key, value) { writes++; store.set(key, value); },
    async delete(key) { writes++; store.delete(key); },
  };
}

function env(initial, extra = {}) {
  return { PARKING: kv(initial), OPERATOR_KEY: 'secret', HEALTH_URL: `${API}/actuator/health`, ...extra };
}

let originCalls;
function mockOrigin(impl) {
  originCalls = 0;
  globalThis.fetch = async (...args) => { originCalls++; return impl(...args); };
}

beforeEach(() => {
  resetCache();
  mockOrigin(() => new Response('{"ok":true}', { status: 200 }));
});

test('파킹 아니면 원본 응답을 그대로 준다', async () => {
  const res = await handleFetch(new Request(`${API}/v1/api/users/me`), env(), NOW);
  assert.equal(res.status, 200);
  assert.equal(originCalls, 1);
});

test('수동 파킹 중이면 원본에 가지 않고 503 + 사유', async () => {
  const e = env({ manual: { reason: 'DB 점검', startAt: null, endAt: '2026-11-18T12:30:00Z' } });
  const res = await handleFetch(new Request(`${API}/v1/api/users/me`), e, NOW);
  assert.equal(res.status, 503);
  assert.equal(res.headers.get(PARKING_HEADER), 'MANUAL');
  assert.equal(res.headers.get('Retry-After'), '1800');
  const body = await res.json();
  assert.equal(body.code, 503);
  assert.equal(body.result.reason, 'DB 점검');
  assert.equal(originCalls, 0);
});

test('점검 중 서버 운영자 API에는 서버 키와 요청 본문만 전달한다', async () => {
  const e = env({ manual: { reason: '점검' } });
  const url = `${API}/v1/api/operator/organizations`;
  mockOrigin(async (req) => {
    assert.equal(req.url, url);
    assert.equal(req.method, 'POST');
    assert.equal(req.headers.get('X-Operator-Key'), 'server-key');
    assert.equal(req.headers.has('X-Parking-Operator-Key'), false);
    assert.equal(await req.text(), '{"name":"test"}');
    return new Response(null, { status: 201 });
  });
  const req = new Request(url, {
    method: 'POST',
    headers: { 'X-Parking-Operator-Key': 'secret', 'X-Operator-Key': 'server-key' },
    body: '{"name":"test"}',
  });
  assert.equal((await handleFetch(req, e, NOW)).status, 201);
});

test('기존 서버 헤더만으로는 수동 파킹을 우회할 수 없다', async () => {
  const e = env({ manual: { reason: '점검' } });
  const req = new Request(`${API}/v1/api/users/me`, { headers: { 'X-Operator-Key': 'secret' } });
  assert.equal((await handleFetch(req, e, NOW)).status, 503);
  assert.equal(originCalls, 0);
});

test('정상 서비스 중에도 파킹 헤더는 값에 관계없이 제거한다', async () => {
  mockOrigin((req) => new Response(null, { status: req.headers.has('X-Parking-Operator-Key') ? 400 : 200 }));
  const req = new Request(`${API}/v1/api/users/me`, { headers: { 'X-Parking-Operator-Key': 'wrong' } });
  assert.equal((await handleFetch(req, env(), NOW)).status, 200);
});

test('예약 점검은 시작 전에는 막지 않는다', async () => {
  const e = env({ manual: { reason: '예약', startAt: '2026-11-18T13:00:00Z' } });
  const res = await handleFetch(new Request(`${API}/v1/api/x`), e, NOW);
  assert.equal(res.status, 200);
  const status = await (await handleFetch(new Request(`${API}/v1/api/parking`), e, NOW)).json();
  assert.equal(status.result.parking, false);
  assert.equal(status.result.scheduled.reason, '예약');
});

test('종료 시각이 지나면 풀린다', async () => {
  const e = env({ manual: { reason: '끝난 점검', endAt: '2026-11-18T11:00:00Z' } });
  const res = await handleFetch(new Request(`${API}/v1/api/x`), e, NOW);
  assert.equal(res.status, 200);
});

for (const status of [502, 530]) {
  test(`원본이 ${status} 면 자동 파킹`, async () => {
    mockOrigin(() => new Response('bad gateway', { status }));
    const res = await handleFetch(new Request(`${API}/v1/api/x`), env(), NOW);
    assert.equal(res.status, 503);
    assert.equal(res.headers.get(PARKING_HEADER), 'AUTO');
  });
}

test('원본 연결 실패도 자동 파킹', async () => {
  mockOrigin(() => { throw new Error('network'); });
  const res = await handleFetch(new Request(`${API}/v1/api/x`), env(), NOW);
  assert.equal(res.headers.get(PARKING_HEADER), 'AUTO');
});

test('앱의 4xx/500 은 건드리지 않는다', async () => {
  for (const status of [400, 401, 404, 500]) {
    mockOrigin(() => new Response('{}', { status }));
    const res = await handleFetch(new Request(`${API}/v1/api/x`), env(), NOW);
    assert.equal(res.status, status);
  }
});

test('상태 조회: 정상 / 자동 / 수동', async () => {
  const get = (e) => handleFetch(new Request(`${API}/v1/api/parking`), e, NOW).then((r) => r.json());
  assert.equal((await get(env())).result.parking, false);
  assert.equal((await get(env({ auto: { down: true, since: 'x' } }))).result.type, 'AUTO');
  assert.equal((await get(env({ manual: { reason: 'r' }, auto: { down: true } }))).result.type, 'MANUAL');
  assert.equal(originCalls, 0);
});

test('등록·해제는 운영자 키가 있어야 한다', async () => {
  const e = env();
  const put = (headers, body) => handleFetch(new Request(`${API}/v1/api/parking`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body),
  }), e, NOW);

  assert.equal((await put({}, { reason: 'x' })).status, 401);
  assert.equal((await put({ 'X-Operator-Key': 'secret' }, { reason: 'x' })).status, 401);
  assert.equal((await put({ 'X-Parking-Operator-Key': 'wrong' }, { reason: 'x' })).status, 401);
  assert.equal((await put({ 'X-Parking-Operator-Key': 'secret' }, { reason: ' ' })).status, 400);
  assert.equal((await put({ 'X-Parking-Operator-Key': 'secret' }, {
    reason: 'x', startAt: '2026-11-18T13:00:00Z', endAt: '2026-11-18T12:00:00Z',
  })).status, 400);

  const ok = await put({ 'X-Parking-Operator-Key': 'secret' }, { reason: '배포 점검', endAt: '2026-11-18T13:00:00Z' });
  assert.equal(ok.status, 200);
  const blocked = await handleFetch(new Request(`${API}/v1/api/x`), e, NOW);
  assert.equal(blocked.status, 503);

  const legacyDelete = new Request(`${API}/v1/api/parking`, {
    method: 'DELETE', headers: { 'X-Operator-Key': 'secret' },
  });
  assert.equal((await handleFetch(legacyDelete, e, NOW)).status, 401);
  const del = await handleFetch(new Request(`${API}/v1/api/parking`, {
    method: 'DELETE', headers: { 'X-Parking-Operator-Key': 'secret' },
  }), e, NOW);
  assert.equal(del.status, 200);
  assert.equal((await handleFetch(new Request(`${API}/v1/api/x`), e, NOW)).status, 200);
});

test('운영자 키가 설정되지 않으면 아무도 등록 못 한다', async () => {
  const e = env({}, { OPERATOR_KEY: '' });
  const res = await handleFetch(new Request(`${API}/v1/api/parking`, {
    method: 'DELETE', headers: { 'X-Parking-Operator-Key': '' },
  }), e, NOW);
  assert.equal(res.status, 401);
});

test('헬스 경로는 수동 파킹 중에도 원본으로', async () => {
  const e = env({ manual: { reason: '점검' } });
  mockOrigin((req) => new Response(null, { status: req.headers.has('X-Parking-Operator-Key') ? 400 : 200 }));
  const req = new Request(`${API}/actuator/health`, { headers: { 'X-Parking-Operator-Key': 'secret' } });
  const res = await handleFetch(req, e, NOW);
  assert.equal(res.status, 200);
});

test('CORS: 허용 출처면 파킹 응답과 preflight 에 헤더', async () => {
  const e = env({ manual: { reason: '점검' } }, { ALLOWED_ORIGINS: 'https://web.example.com' });
  const res = await handleFetch(new Request(`${API}/v1/api/x`, { headers: { Origin: 'https://web.example.com' } }), e, NOW);
  assert.equal(res.headers.get('Access-Control-Allow-Origin'), 'https://web.example.com');
  assert.match(res.headers.get('Access-Control-Expose-Headers'), /X-Safori-Parking/);
  const pre = await handleFetch(new Request(`${API}/v1/api/x`, {
    method: 'OPTIONS', headers: { Origin: 'https://evil.example.com' },
  }), e, NOW);
  assert.equal(pre.status, 204);
  assert.equal(pre.headers.get('Access-Control-Allow-Origin'), null);
});

test('Cron: 상태가 바뀔 때만 KV 에 쓴다', async () => {
  const e = env();
  mockOrigin(() => new Response('{"status":"UP"}', { status: 200 }));
  await handleScheduled(e, NOW);
  assert.equal(e.PARKING.writes, 0);

  mockOrigin(() => new Response('', { status: 530 }));
  await handleScheduled(e, NOW);
  assert.equal(e.PARKING.writes, 1);
  assert.equal(originCalls, 3); // 3번 재시도 후 DOWN
  await handleScheduled(e, NOW);
  assert.equal(e.PARKING.writes, 1);

  mockOrigin(() => new Response('{"status":"UP"}', { status: 200 }));
  await handleScheduled(e, NOW);
  assert.equal(e.PARKING.writes, 2);
  assert.equal(JSON.parse(e.PARKING.store.get('auto')).down, false);
});

test('원본이 꺼진 콘솔에 새로 방문하면 원래 주소에서 재확인할 수 있는 HTML을 받는다', async () => {
  const e = env({}, { BACKOFFICE_HOSTNAME: 'console.example.com' });
  mockOrigin(() => new Response('', { status: 530 }));
  const req = new Request('https://console.example.com/organizations', { headers: { Accept: 'text/html' } });
  const res = await handleFetch(req, e, NOW);
  assert.equal(res.status, 503);
  assert.equal(res.headers.get(PARKING_HEADER), 'AUTO');
  assert.match(res.headers.get('content-type'), /text\/html/);
  assert.match(await res.text(), /서비스 점검 중입니다/);
  mockOrigin(() => new Response('console', { status: 200 }));
  assert.equal(await (await handleFetch(req, e, NOW)).text(), 'console');
});

test('콘솔 점검 사유는 텍스트로 표시하고 콘솔에서는 파킹을 변경할 수 없다', async () => {
  const e = env({ manual: { reason: '<b>점검</b>' } }, { BACKOFFICE_HOSTNAME: 'console.example.com' });
  const req = new Request('https://console.example.com/', { headers: { Accept: 'text/html' } });
  assert.match(await (await handleFetch(req, e, NOW)).text(), /&lt;b&gt;점검/);
  const change = new Request('https://console.example.com/v1/api/parking', {
    method: 'DELETE', headers: { 'X-Parking-Operator-Key': 'secret' },
  });
  assert.equal((await handleFetch(change, e, NOW)).status, 405);
  const api = new Request('https://console.example.com/v1/api/operator/organizations', { headers: { Accept: 'text/html' } });
  assert.match((await handleFetch(api, e, NOW)).headers.get('content-type'), /application\/json/);
});
