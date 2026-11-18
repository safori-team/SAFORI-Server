// SAFORI 파킹(점검) Worker. API 호스트 앞에서 모든 요청을 받는다.
//
//   수동 파킹  운영자가 PUT /v1/api/parking 으로 사유를 등록하면 서버로 보내지 않고 503 을 준다.
//              X-Operator-Key 를 붙인 요청은 통과시켜 점검 중에도 운영자가 확인할 수 있다.
//   자동 파킹  서버가 꺼졌거나(Tunnel 끊김 530) 앱이 죽어(502/504/52x) 원본이 실패하면 같은 형식으로 바꿔 준다.
//   헬스 체크  Cron 이 1분마다 HEALTH_URL 을 확인해 상태가 바뀔 때만 KV 에 쓴다(KV 무료 쓰기 하루 1,000건).
//   상태 조회  GET /v1/api/parking 은 서버와 무관하게 Worker 가 답한다. 웹·앱이 시작할 때 호출한다.
//
// 파킹 응답은 서버의 ApiResponseDto 형식 {isSuccess, code, message, result} 를 따르고,
// 클라이언트가 본문을 보지 않고도 알 수 있게 X-Safori-Parking: MANUAL|AUTO 헤더를 붙인다.

export const PARKING_PATH = '/v1/api/parking';
export const OPERATOR_HEADER = 'X-Operator-Key';
export const PARKING_HEADER = 'X-Safori-Parking';
export const HEALTH_PATH = '/actuator/health';

const MANUAL_KEY = 'manual';
const AUTO_KEY = 'auto';
const CACHE_TTL_MS = 15_000;
const DEFAULT_RETRY_AFTER_SEC = 60;
const DEFAULT_AUTO_REASON = '서비스 점검 중입니다. 잠시 후 다시 시도해 주세요.';
const REASON_MAX = 500;
const HEALTH_ATTEMPTS = 3;
const HEALTH_TIMEOUT_MS = 5_000;

// 원본 장애로 보는 상태 코드. 503 은 앱이 직접 줄 수도 있지만 지금 앱 코드에는 없다.
// 502/504: 앱 응답 없음, 520~527: Cloudflare ↔ 원본 오류, 530: Tunnel 끊김(서버 꺼짐)
const ORIGIN_DOWN_STATUSES = new Set([502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 527, 530]);

// isolate 메모리 캐시 — KV 무료 읽기(하루 10만 건)를 요청 수만큼 쓰지 않도록
let cache = { at: 0, manual: null, auto: null };

export function resetCache() {
  cache = { at: 0, manual: null, auto: null };
}

async function readState(env, now) {
  if (now - cache.at < CACHE_TTL_MS) {
    return cache;
  }
  const [manual, auto] = await Promise.all([
    env.PARKING.get(MANUAL_KEY, 'json'),
    env.PARKING.get(AUTO_KEY, 'json'),
  ]);
  cache = { at: now, manual, auto };
  return cache;
}

export function isManualActive(manual, now) {
  if (!manual) return false;
  const start = manual.startAt ? Date.parse(manual.startAt) : -Infinity;
  const end = manual.endAt ? Date.parse(manual.endAt) : Infinity;
  return now >= start && now < end;
}

function timingSafeEqual(a, b) {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  }
  return diff === 0;
}

// 키가 설정되지 않았으면 아무도 통과시키지 않는다(빈 헤더 == 빈 키 방지)
export function isOperator(request, env) {
  const key = env.OPERATOR_KEY;
  const provided = request.headers.get(OPERATOR_HEADER);
  return Boolean(key) && provided !== null && timingSafeEqual(key, provided);
}

function corsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const allowed = (env.ALLOWED_ORIGINS || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!origin || !allowed.includes(origin)) return {};
  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': request.headers.get('Access-Control-Request-Headers') || '*',
    'Access-Control-Expose-Headers': `${PARKING_HEADER}, Retry-After`,
    'Access-Control-Max-Age': '600',
    Vary: 'Origin',
  };
}

function json(request, env, status, body, extra = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store',
      ...corsHeaders(request, env),
      ...extra,
    },
  });
}

function retryAfterSec(manual, now) {
  if (manual?.endAt) {
    const sec = Math.ceil((Date.parse(manual.endAt) - now) / 1000);
    if (sec > 0) return sec;
  }
  return DEFAULT_RETRY_AFTER_SEC;
}

export function parkingInfo(type, manual, auto) {
  if (type === 'MANUAL') {
    return {
      parking: true,
      type,
      reason: manual.reason,
      startAt: manual.startAt ?? null,
      endAt: manual.endAt ?? null,
      updatedAt: manual.updatedAt ?? null,
    };
  }
  return {
    parking: true,
    type,
    // 자동 파킹이어도 운영자가 미리 적어 둔(아직 시작 전이거나 끝난) 사유는 쓰지 않는다 — 기본 문구
    reason: DEFAULT_AUTO_REASON,
    startAt: auto?.since ?? null,
    endAt: null,
    updatedAt: auto?.checkedAt ?? null,
  };
}

function parkingResponse(request, env, info, now, manual) {
  if (request.method === 'OPTIONS') {
    // 서버가 꺼져 있으면 preflight 도 원본에 못 가므로 여기서 답한다
    return new Response(null, { status: 204, headers: corsHeaders(request, env) });
  }
  return json(
    request,
    env,
    503,
    { isSuccess: false, code: 503, message: info.reason, result: info },
    { [PARKING_HEADER]: info.type, 'Retry-After': String(retryAfterSec(manual, now)) },
  );
}

function validateManual(body) {
  if (!body || typeof body !== 'object') return '본문이 JSON 객체가 아닙니다.';
  const { reason, startAt, endAt } = body;
  if (typeof reason !== 'string' || !reason.trim()) return 'reason 은 필수입니다.';
  if (reason.length > REASON_MAX) return `reason 은 ${REASON_MAX}자 이하입니다.`;
  for (const [name, v] of [['startAt', startAt], ['endAt', endAt]]) {
    if (v != null && (typeof v !== 'string' || Number.isNaN(Date.parse(v)))) {
      return `${name} 은 ISO-8601 시각이어야 합니다.`;
    }
  }
  if (startAt && endAt && Date.parse(startAt) >= Date.parse(endAt)) return 'endAt 은 startAt 보다 뒤여야 합니다.';
  return null;
}

async function handleParkingApi(request, env, now) {
  if (request.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders(request, env) });
  }

  if (request.method === 'GET') {
    // 등록 직후에도 바로 보이도록 캐시를 거치지 않는다
    resetCache();
    const { manual, auto } = await readState(env, now);
    let result;
    if (isManualActive(manual, now)) result = parkingInfo('MANUAL', manual, auto);
    else if (auto?.down) result = parkingInfo('AUTO', manual, auto);
    else result = { parking: false, type: null, reason: null, startAt: null, endAt: null, updatedAt: null };
    // 예약된(아직 시작 전) 점검을 미리 안내할 수 있게 같이 준다
    const scheduled = manual && !isManualActive(manual, now) && manual.startAt && Date.parse(manual.startAt) > now
      ? { reason: manual.reason, startAt: manual.startAt, endAt: manual.endAt ?? null }
      : null;
    return json(request, env, 200, { isSuccess: true, code: 200, message: 'OK', result: { ...result, scheduled } });
  }

  if (!isOperator(request, env)) {
    return json(request, env, 401, { isSuccess: false, code: 401, message: '운영자 키가 필요합니다.', result: null });
  }

  if (request.method === 'PUT') {
    let body;
    try {
      body = await request.json();
    } catch {
      body = null;
    }
    const error = validateManual(body);
    if (error) return json(request, env, 400, { isSuccess: false, code: 400, message: error, result: null });
    const manual = {
      reason: body.reason.trim(),
      startAt: body.startAt ?? null,
      endAt: body.endAt ?? null,
      updatedAt: new Date(now).toISOString(),
    };
    await env.PARKING.put(MANUAL_KEY, JSON.stringify(manual));
    resetCache();
    return json(request, env, 200, { isSuccess: true, code: 200, message: 'OK', result: manual });
  }

  if (request.method === 'DELETE') {
    await env.PARKING.delete(MANUAL_KEY);
    resetCache();
    return json(request, env, 200, { isSuccess: true, code: 200, message: 'OK', result: null });
  }

  return json(request, env, 405, { isSuccess: false, code: 405, message: 'Method Not Allowed', result: null });
}

export async function handleFetch(request, env, now = Date.now()) {
  const url = new URL(request.url);

  if (url.pathname === PARKING_PATH) {
    return handleParkingApi(request, env, now);
  }
  if (url.pathname === HEALTH_PATH) {
    // Cron 헬스 체크가 수동 파킹에 막히지 않게 그대로 원본으로 보낸다(show-details: never 라 {"status":"UP"} 만 나옴)
    return fetch(request);
  }

  const { manual, auto } = await readState(env, now);
  if (isManualActive(manual, now) && !isOperator(request, env)) {
    return parkingResponse(request, env, parkingInfo('MANUAL', manual, auto), now, manual);
  }

  let response;
  try {
    response = await fetch(request);
  } catch {
    response = null;
  }
  if (response && !ORIGIN_DOWN_STATUSES.has(response.status)) {
    return response;
  }
  return parkingResponse(request, env, parkingInfo('AUTO', manual, auto), now, null);
}

async function probe(url) {
  for (let i = 0; i < HEALTH_ATTEMPTS; i++) {
    try {
      const res = await fetch(url, {
        signal: AbortSignal.timeout(HEALTH_TIMEOUT_MS),
        headers: { 'User-Agent': 'safori-parking-health' },
        cf: { cacheTtl: 0 },
      });
      if (res.ok) return true;
    } catch {
      // 다음 시도
    }
    if (i < HEALTH_ATTEMPTS - 1) await new Promise((r) => setTimeout(r, 1_000));
  }
  return false;
}

export async function handleScheduled(env, now = Date.now()) {
  if (!env.HEALTH_URL) return;
  const up = await probe(env.HEALTH_URL);
  const prev = await env.PARKING.get(AUTO_KEY, 'json');
  if (prev && prev.down === !up) return; // 바뀐 게 없으면 쓰지 않는다
  if (!prev && up) return; // 처음부터 정상이면 쓸 것이 없다
  const nowIso = new Date(now).toISOString();
  const next = { down: !up, since: nowIso, checkedAt: nowIso };
  await env.PARKING.put(AUTO_KEY, JSON.stringify(next));
  console.log(`health changed: ${up ? 'UP' : 'DOWN'}`);
}

export default {
  fetch(request, env) {
    return handleFetch(request, env);
  },
  scheduled(_event, env, ctx) {
    ctx.waitUntil(handleScheduled(env));
  },
};
