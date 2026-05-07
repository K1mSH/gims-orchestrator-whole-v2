'use client';

interface Tab {
  key: string;
  label: string;
}

interface TabButtonProps {
  tabs: Tab[];
  active: string;
  onChange: (key: string) => void;
}

export default function TabButton({ tabs, active, onChange }: TabButtonProps) {
  return (
    <div style={{ display: 'flex', gap: '0', borderBottom: '2px solid var(--gray-100)', marginBottom: '1rem' }}>
      {tabs.map(tab => (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          style={{
            padding: '0.5rem 1rem',
            fontSize: '0.85rem',
            fontWeight: active === tab.key ? 600 : 400,
            color: active === tab.key ? 'var(--primary)' : 'var(--gray-400)',
            background: 'none',
            border: 'none',
            borderBottom: active === tab.key ? '2px solid var(--primary)' : '2px solid transparent',
            marginBottom: '-2px',
            cursor: 'pointer',
          }}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
