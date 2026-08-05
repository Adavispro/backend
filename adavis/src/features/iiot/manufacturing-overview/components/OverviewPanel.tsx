import type { ReactNode } from "react";

export default function OverviewPanel({
  title,
  action,
  children,
  className = "",
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <article
      className={`module-glass-panel relative rounded-lg p-4 shadow-[0_14px_26px_rgba(35,50,70,0.12)] ${className}`}
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="type-table-title">{title}</h2>
        {action}
      </div>
      {children}
    </article>
  );
}
