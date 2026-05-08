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
      <body>{children}</body>
    </html>
  );
}
