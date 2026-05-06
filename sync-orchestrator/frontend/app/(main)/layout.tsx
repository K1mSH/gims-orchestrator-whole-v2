import AppShell from '@/components/AppShell';

/**
 * (main) route group layout — sidebar + header 묶음.
 *
 * URL 영향 0 (괄호 그룹). 이 layout 안의 모든 페이지가 자동으로 AppShell 적용.
 * /login 은 root layout 만 통과 → sidebar/header 없음 (구조적 분리).
 */
export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AppShell>{children}</AppShell>;
}
