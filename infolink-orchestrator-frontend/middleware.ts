import { NextRequest, NextResponse } from 'next/server';

/**
 * 미인증 1차 가드 — accessToken cookie 존재 여부 확인.
 *
 * - 미로그인 + 보호 path → /login redirect (next 쿼리에 원래 path 보존)
 * - 로그인 + /login → / redirect
 * - 인증 path (/auth/**) / Next 정적 / proxy rewrite path 는 통과
 *
 * 실 토큰 검증은 백엔드가 수행 — 여기는 cookie 존재만 본다 (성능).
 */
export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const accessCookie = req.cookies.get('accessToken');
  const isLoginPath = pathname === '/login';

  if (!accessCookie && !isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/login';
    url.searchParams.set('next', pathname + req.nextUrl.search);
    return NextResponse.redirect(url);
  }
  if (accessCookie && isLoginPath) {
    const url = req.nextUrl.clone();
    url.pathname = '/';
    url.search = '';
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: [
    // 정적 / Next 내부 / proxy rewrite path 는 가드 제외
    '/((?!_next/static|_next/image|favicon.ico|krds/|auth/|api/|collector-api/|provider-api/).*)',
  ],
};
