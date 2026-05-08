'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import styles from './Sidebar.module.css';

const navItems = [
  { href: '/', label: '대시보드' },
  { href: '/datasources', label: 'DB 관리' },
  { href: '/agents', label: 'Agent 관리' },
  { href: '/api-collect', label: 'API 수집 관리' },
  { href: '/api-provide', label: 'API 제공 관리' },
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
    <aside className={styles.aside}>
      <div className={styles.brand}>GIMS-Link</div>
      <nav>
        <ul className={styles.list}>
          {navItems.map((item) => (
            <li key={item.href}>
              <Link
                href={item.href}
                className={`${styles.link} ${isActive(item.href) ? styles.active : ''}`}
              >
                {item.label}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </aside>
  );
}
