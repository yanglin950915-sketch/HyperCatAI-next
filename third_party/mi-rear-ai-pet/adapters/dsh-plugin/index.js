import http from 'node:http';
import { createHash, randomUUID } from 'node:crypto';

export const name = 'mi-rear-pet-status';
export const inject = ['sessions'];

const PORT = Number(process.env.MI_REAR_PET_PORT || 8765);

function endState(reason) {
  const kind = reason?.kind;
  if (kind === 'completed') return { state: 'completed_unread', completion: true };
  if (kind === 'aborted') return { state: 'idle', completion: false };
  if (kind === 'blocked') return { state: 'waiting_user', completion: false };
  return { state: 'failed', completion: false };
}

function postEvent(payload) {
  try {
    const body = Buffer.from(JSON.stringify(payload), 'utf8');
    const req = http.request(
      {
        hostname: '127.0.0.1',
        port: PORT,
        path: '/api/event',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': body.length,
        },
        timeout: 400,
      },
      (res) => {
        res.resume();
      }
    );
    req.on('error', () => {});
    req.on('timeout', () => req.destroy());
    req.write(body);
    req.end();
  } catch (_) {}
}

export function apply(ctx) {
  const active = new WeakMap();
  const isMain = (session) => session && session.header?.origin !== 'subagent';
  const hashId = (raw) => createHash('sha256').update(String(raw)).digest('hex').slice(0, 24);

  function send(session, state, turn, eventName, completion = false) {
    if (!isMain(session)) return;
    postEvent({
      provider: 'dsh',
      state,
      event: eventName || state,
      session: hashId(session.id || 'dsh'),
      turn: turn || hashId(randomUUID()),
      completion,
      at: Date.now() / 1000,
    });
  }

  ctx.on(
    'session/event',
    (session, event) => {
      if (!isMain(session) || !event) return;
      if (event.type === 'turn/start') {
        const turn = hashId(randomUUID());
        active.set(session, { turn, questions: 0, approvals: new Set() });
        send(session, 'running', turn, 'turn/start', false);
      } else if (event.type === 'turn/end') {
        const turn = active.get(session)?.turn ?? hashId(randomUUID());
        active.delete(session);
        const mapped = endState(event.data?.reason);
        send(session, mapped.state, turn, 'turn/end', mapped.completion);
      } else if (event.type === 'approval/asked') {
        const current = active.get(session);
        if (current && event.data?.id !== undefined) current.approvals.add(event.data.id);
        send(session, 'waiting_user', current?.turn, 'approval/asked', false);
      } else if (event.type === 'approval/decided') {
        const current = active.get(session);
        if (current && event.data?.id !== undefined) current.approvals.delete(event.data.id);
        if (current && !current.questions && !current.approvals.size) {
          send(session, 'running', current.turn, 'approval/decided', false);
        }
      }
    },
    { global: true }
  );

  ctx.on(
    'user-questions/request',
    async (request, next) => {
      const session = request?.agent?.session;
      const current = isMain(session) && active.get(session);
      if (current) {
        current.questions++;
        send(session, 'waiting_user', current.turn, 'user-questions/request', false);
      }
      try {
        return await next();
      } finally {
        if (current && active.get(session) === current) {
          current.questions--;
          if (!current.questions && !current.approvals.size) {
            send(session, 'running', current.turn, 'user-questions/resolved', false);
          }
        }
      }
    },
    { global: true, prepend: true }
  );
}
