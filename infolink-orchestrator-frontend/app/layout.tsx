import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'GIMS-Link',
  description: 'GIMS-Link 운영 관리',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <head>
        {/* KRDS 디자인 시스템 — token → common → component 순서 */}
        <link rel="stylesheet" href="/krds/css/token/krds_tokens.css" />
        <link rel="stylesheet" href="/krds/css/common/common.css" />
        <link rel="stylesheet" href="/krds/css/component/component.css" />
      </head>
      <body>{children}</body>
    </html>
  );
}
