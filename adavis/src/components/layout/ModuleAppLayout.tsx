import type { ReactNode } from "react";
import ModuleSideNav from "./ModuleSideNav";
import ModuleTopBar from "./ModuleTopBar";

export interface ModuleAppLayoutProps {
  children: ReactNode;
  moduleId?: string;
  title?: string;
  breadcrumbs?: Array<{
    label: string;
    href?: string;
    active?: boolean;
  }>;
}

export default function ModuleAppLayout({
  children,
  moduleId,
  title,
  breadcrumbs,
}: ModuleAppLayoutProps) {
  return (
    <div className="h-dvh overflow-hidden bg-[linear-gradient(180deg,#E6F1FC_0%,#F5F7FF_100%)]">
      <div className="flex h-full w-full pl-5 pr-3 sm:pr-4">
        <ModuleSideNav moduleId={moduleId} />

        <div className="relative z-0 flex min-h-0 min-w-0 flex-1 flex-col">
          <ModuleTopBar title={title} breadcrumbs={breadcrumbs} />
          <main className="flex min-h-0 flex-1 flex-col overflow-y-auto px-5 pb-6 pt-[15px] sm:px-6">
            {children}
          </main>
        </div>
      </div>
    </div>
  );
}
