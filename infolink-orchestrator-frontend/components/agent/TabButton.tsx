'use client';

/**
 * KRDS 탭 마크업: <li class="active"><button class="btn-tab"> ... </button></li>
 * 부모는 반드시 <div class="krds-tab-area"><div class="tab line"><ul>...</ul></div></div> 구조여야 함.
 */
interface TabButtonProps {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

export default function TabButton({ active, onClick, children }: TabButtonProps) {
  return (
    <li className={active ? 'active' : ''}>
      <button type="button" className="btn-tab" onClick={onClick}>
        {children}
      </button>
    </li>
  );
}
