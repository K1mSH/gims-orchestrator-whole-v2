'use client';

/**
 * KRDS 탭 마크업: <li class="active"><button class="btn-tab"> ... </button></li>
 * 부모는 반드시 <div class="krds-tab-area"><div class="tab line"><ul>...</ul></div></div> 구조여야 함.
 */
interface TabButtonProps {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
  disabled?: boolean;
  disabledReason?: string;
}

export default function TabButton({ active, onClick, children, disabled, disabledReason }: TabButtonProps) {
  return (
    <li className={active ? 'active' : ''}>
      <button
        type="button"
        className="btn-tab"
        onClick={onClick}
        disabled={disabled}
        title={disabled ? disabledReason : undefined}
      >
        {children}
      </button>
    </li>
  );
}
