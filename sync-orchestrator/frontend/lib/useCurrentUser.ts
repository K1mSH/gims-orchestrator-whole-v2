'use client';

import { useEffect, useState, useCallback } from 'react';
import { authApi, type AuthUser } from './authApi';

/**
 * 현재 로그인 사용자 조회 훅.
 *
 * - 마운트 시 GET /auth/me. 401 이면 user=null (로그아웃 상태).
 * - 모듈 단위 캐시 (앱 내 다중 컴포넌트가 같이 사용해도 1회만 fetch).
 * - mutate() 로 강제 갱신.
 */

let cached: AuthUser | null = null;
let fetched = false;
let inFlight: Promise<AuthUser | null> | null = null;
const subscribers = new Set<(u: AuthUser | null) => void>();

async function fetchOnce(): Promise<AuthUser | null> {
  if (inFlight) return inFlight;
  inFlight = authApi
    .me()
    .then((u) => {
      cached = u;
      fetched = true;
      subscribers.forEach((fn) => fn(u));
      return u;
    })
    .catch(() => {
      cached = null;
      fetched = true;
      subscribers.forEach((fn) => fn(null));
      return null;
    })
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

export function useCurrentUser() {
  const [user, setUser] = useState<AuthUser | null>(cached);
  const [loading, setLoading] = useState(!fetched);

  useEffect(() => {
    let alive = true;
    const handler = (u: AuthUser | null) => {
      if (alive) setUser(u);
    };
    subscribers.add(handler);

    if (!fetched) {
      fetchOnce().finally(() => {
        if (alive) setLoading(false);
      });
    } else {
      setLoading(false);
    }

    return () => {
      alive = false;
      subscribers.delete(handler);
    };
  }, []);

  const mutate = useCallback(async () => {
    fetched = false;
    cached = null;
    setLoading(true);
    const u = await fetchOnce();
    setLoading(false);
    return u;
  }, []);

  const clear = useCallback(() => {
    cached = null;
    fetched = true;
    subscribers.forEach((fn) => fn(null));
  }, []);

  return { user, loading, mutate, clear };
}
