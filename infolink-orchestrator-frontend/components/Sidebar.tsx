'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const navItems = [
  { href: '/', label: '대시보드', icon: '📊' },
  { href: '/datasources', label: 'DB 관리', icon: '🗄️' },
  { href: '/agents', label: 'Agent 관리', icon: '🖥️' },
  { href: '/api-collect', label: 'API 수집 관리', icon: '🌐' },
  { href: '/api-provide', label: 'API 제공 관리', icon: '📡' },
];

export default function Sidebar() {
  const pathname = usePathname();

  const isActive = (href: string) => {
    if (href === '/') {
      return pathname === '/';
    }
    return pathname.startsWith(href);
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">GIMS-Link</div>
      <nav>
        <ul className="sidebar-nav">
          {navItems.map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className={isActive(item.href) ? 'active' : ''}
              >
                <span style={{ marginRight: '0.5rem' }}>{item.icon}</span>
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  );
}
