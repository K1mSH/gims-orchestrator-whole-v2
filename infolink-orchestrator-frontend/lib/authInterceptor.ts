import type { AxiosInstance } from 'axios';
import authClient from './authApi';

// 동시 갱신 race 방어 — 한 번만 fetch
let refreshing: Promise<void> | null = null;

/**
 * axios 인스턴스에 401 → /auth/refresh 자동 시도 + 503 알림 박음.
 *
 * - 401 (cookie 없음/만료/위조): refresh 1회 시도. 성공 시 원 요청 재시도.
 *   실패 시 — 무효 cookie 는 HttpOnly 라 JS 로 못 지움 → /auth/logout 으로 서버측 만료시킨 뒤 /login redirect.
 *   (logout 안 하면 무효 cookie 가 남아 middleware 가 '쿠키 존재 → 로그인됨' 으로 /login→/ 무한 루프)
 * - 503 AUTH_SERVICE_UNAVAILABLE: 인증 서버 다운 — alert 1회.
 * - 그 외: 그대로 throw (호출자 처리).
 */
export function attachAuthInterceptor(client: AxiosInstance) {
  client.defaults.withCredentials = true;

  client.interceptors.response.use(
    (res) => res,
    async (err) => {
      const original = err.config;
      const status = err.response?.status;
      const errorCode = err.response?.data?.error;

      // 503 — 인증 서버 다운
      if (status === 503 && errorCode === 'AUTH_SERVICE_UNAVAILABLE') {
        console.error('[auth] AUTH_SERVICE_UNAVAILABLE');
        if (typeof window !== 'undefined') {
          alert('인증 서버가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해주세요.');
        }
        return Promise.reject(err);
      }

      // 401 — 토큰 문제: refresh 1회 시도
      if (status === 401 && !original._retry) {
        original._retry = true;
        if (!refreshing) {
          refreshing = authClient
            .post('/refresh')
            .then(() => {
              refreshing = null;
            })
            .catch(async (refreshErr) => {
              refreshing = null;
              if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
                const next = window.location.pathname + window.location.search;
                // 무효 cookie 는 HttpOnly — 서버측 logout 으로 만료시켜야 middleware 의 /login→/ 루프 회피
                try { await authClient.post('/logout'); } catch { /* idempotent — 무시 */ }
                window.location.href = '/login?next=' + encodeURIComponent(next);
              }
              throw refreshErr;
            });
        }
        try {
          await refreshing;
          return client(original);
        } catch {
          return Promise.reject(err);
        }
      }

      return Promise.reject(err);
    },
  );
}
