'use client';

interface TabButtonProps {
  active: boolean;
  onClick: () => void;
  highlight?: boolean;
  children: React.ReactNode;
}

export default function TabButton({ active, onClick, highlight, children }: TabButtonProps) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '0.75rem 1.5rem',
        border: 'none',
        background: active ? 'var(--primary)' : 'transparent',
        color: active ? 'white' : highlight ? 'var(--primary)' : 'var(--gray-600)',
        fontWeight: active || highlight ? 600 : 400,
        cursor: 'pointer',
        borderBottom: active ? '2px solid var(--primary)' : '2px solid transparent',
        marginBottom: '-2px',
        transition: 'all 0.2s',
      }}
    >
      {children}
    </button>
  );
}
