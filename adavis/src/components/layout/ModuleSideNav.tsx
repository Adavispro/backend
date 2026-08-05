"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  Broadcast,
  Buildings,
  CaretDown,
  CaretLeft,
  CaretRight,
  ChartLineUp,
  ChartBar,
  ClipboardText,
  Cpu,
  Database,
  Detective,
  Files,
  Factory,
  House,
  Kanban,
  Key,
  MagnifyingGlass,
  NotePencil,
  Pulse,
  Scales,
  ShieldCheck,
  SquaresFour,
  User,
  UsersThree,
} from "@phosphor-icons/react";
import type { IconProps } from "@phosphor-icons/react";
import auditLogsIcon from "@/assets/icons/auditLogs.svg";
import logoExtended from "@/assets/logo/logo-extended.svg";
import logoShort from "@/assets/logo/logo-short.svg";
import { ROUTES } from "@/config/routes";

type SidebarIcon = ComponentType<IconProps>;

function AuditLogsIcon({
  size = 21,
  className,
}: IconProps) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block shrink-0 bg-current ${className ?? ""}`}
      style={{
        width: size,
        height: size,
        WebkitMaskImage: `url(${auditLogsIcon.src})`,
        maskImage: `url(${auditLogsIcon.src})`,
        WebkitMaskPosition: "center",
        maskPosition: "center",
        WebkitMaskRepeat: "no-repeat",
        maskRepeat: "no-repeat",
        WebkitMaskSize: "contain",
        maskSize: "contain",
      }}
    />
  );
}

interface NavigationItem {
  label: string;
  icon: SidebarIcon;
  href?: string;
  isActive?: (pathname: string) => boolean;
}

const iiotNavigationItems: NavigationItem[] = [
  {
    label: "Equipment Overview",
    icon: Pulse,
    href: ROUTES.iiot,
    isActive: (pathname) =>
      pathname === ROUTES.iiot ||
      pathname.startsWith(`${ROUTES.iiot}/`) ||
      pathname === ROUTES.iiotEquipment ||
      pathname.startsWith(`${ROUTES.iiotEquipment}/`),
  },
  {
    label: "Analytics",
    icon: Broadcast,
    href: ROUTES.iiotMonitoring,
    isActive: (pathname) => pathname === ROUTES.iiotMonitoring,
  },
  {
    label: "OEE",
    icon: ChartLineUp,
    href: ROUTES.iiotAnalytics,
    isActive: (pathname) => pathname === ROUTES.iiotAnalytics,
  },
];

const sidebarItemsByModuleId: Record<string, NavigationItem[]> = {
  "master-management": [
    {
      label: "Dashboard",
      icon: SquaresFour,
      href: ROUTES.masterManagement,
      isActive: (pathname) => pathname === ROUTES.masterManagement,
    },
    {
      label: "Tenants",
      icon: Buildings,
      href: ROUTES.masterTenants,
      isActive: (pathname) =>
        pathname === ROUTES.masterTenants ||
        pathname.startsWith(`${ROUTES.masterTenants}/`),
    },
    {
      label: "Plant Topology",
      icon: Factory,
      href: ROUTES.masterPlantTopology,
      isActive: (pathname) =>
        pathname === ROUTES.masterPlantTopology ||
        pathname.startsWith(`${ROUTES.masterPlantTopology}/`),
    },
    {
      label: "Departments",
      icon: Database,
      href: ROUTES.masterDepartments,
      isActive: (pathname) =>
        pathname === ROUTES.masterDepartments ||
        pathname.startsWith(`${ROUTES.masterDepartments}/`),
    },
    
    {
      label: "Roles",
      icon: ShieldCheck,
      href: ROUTES.masterRoles,
      isActive: (pathname) =>
        pathname === ROUTES.masterRoles ||
        pathname.startsWith(`${ROUTES.masterRoles}/`),
    },
    {
      label: "Users",
      icon: User,
      href: ROUTES.masterUsers,
      isActive: (pathname) =>
        pathname === ROUTES.masterUsers ||
        pathname.startsWith(`${ROUTES.masterUsers}/`),
    },
    {
      label: "User Groups",
      icon: UsersThree,
      href: ROUTES.masterUserGroups,
      isActive: (pathname) =>
        pathname === ROUTES.masterUserGroups ||
        pathname.startsWith(`${ROUTES.masterUserGroups}/`),
    },
    
    {
      label: "User & Group Context Assignments",
      icon: ClipboardText,
      href: ROUTES.masterAssignments,
      isActive: (pathname) =>
        pathname === ROUTES.masterAssignments ||
        pathname.startsWith(`${ROUTES.masterAssignments}/`),
    },
    {
      label: "IIOT Master",
      icon: Cpu,
      href: ROUTES.masterIiotEquipments,
      isActive: (pathname) =>
        pathname.startsWith("/master-management/iiot-master"),
    },
    {
      label: "License",
      icon: Key,
      href: ROUTES.masterLicenses,
      isActive: (pathname) =>
        pathname === ROUTES.masterLicenses ||
        pathname.startsWith(`${ROUTES.masterLicenses}/`),
    },
    {
      label: "Audit Logs",
      icon: AuditLogsIcon,
      href: ROUTES.masterAuditLogs,
      isActive: (pathname) => pathname === ROUTES.masterAuditLogs,
    },
  ],
  iiot: iiotNavigationItems,
  "project-engine": [
    { label: "Projects", icon: Kanban },
    { label: "Tasks", icon: ClipboardText },
    { label: "Progress", icon: ChartLineUp },
  ],
  "ai-elogbook": [
    { label: "Logbook", icon: NotePencil },
    { label: "Entries", icon: ClipboardText },
    { label: "Reports", icon: ChartBar },
  ],
  "ai-ebmr": [
    { label: "Batch Records", icon: ClipboardText },
    { label: "Workflow", icon: Kanban },
    { label: "Review", icon: ShieldCheck },
  ],
  "ai-iot": iiotNavigationItems,
  "cleaning-validations": [
    { label: "Protocols", icon: Files },
    { label: "Validation", icon: ShieldCheck },
    { label: "Trends", icon: ChartLineUp },
  ],
  "ai-qms": [
    { label: "Quality Overview", icon: MagnifyingGlass },
    { label: "CAPA", icon: ShieldCheck },
    { label: "Trends", icon: ChartBar },
  ],
  apqr: [
    { label: "Review", icon: ClipboardText },
    { label: "Products", icon: Database },
    { label: "Analytics", icon: ChartBar },
  ],
  "ai-investigation": [
    { label: "Investigations", icon: Detective },
    { label: "Evidence", icon: Files },
    { label: "Actions", icon: ShieldCheck },
  ],
  "ai-audit": [
    { label: "Audits", icon: Scales },
    { label: "Findings", icon: MagnifyingGlass },
    { label: "Compliance", icon: ShieldCheck },
  ],
  "ai-dms": [
    { label: "Documents", icon: Files },
    { label: "Library", icon: Database },
    { label: "Approvals", icon: ShieldCheck },
  ],
};

export interface ModuleSideNavProps {
  moduleId?: string;
}

export default function ModuleSideNav({ moduleId }: ModuleSideNavProps) {
  const pathname = usePathname();
  const [isExpanded, setIsExpanded] = useState(false);
  const [canScrollDown, setCanScrollDown] = useState(false);
  const [hoveredTooltip, setHoveredTooltip] = useState<{
    label: string;
    icon: SidebarIcon;
    left: number;
    top: number;
  } | null>(null);
  const scrollAreaRef = useRef<HTMLDivElement>(null);
  const navigationItems =
    moduleId !== undefined && sidebarItemsByModuleId[moduleId] !== undefined
      ? sidebarItemsByModuleId[moduleId]
      : [
      { label: "Overview", icon: Cpu },
      { label: "Records", icon: ClipboardText },
      { label: "Analytics", icon: ChartLineUp },
    ];

  const updateScrollIndicator = useCallback(() => {
    const scrollArea = scrollAreaRef.current;

    if (scrollArea === null || isExpanded) {
      setCanScrollDown(false);
      return;
    }

    setCanScrollDown(
      scrollArea.scrollTop + scrollArea.clientHeight < scrollArea.scrollHeight - 2,
    );
  }, [isExpanded]);

  useEffect(() => {
    updateScrollIndicator();
    window.addEventListener("resize", updateScrollIndicator);

    return () => window.removeEventListener("resize", updateScrollIndicator);
  }, [navigationItems.length, updateScrollIndicator]);

  return (
    <aside
      className={`relative z-50 flex h-full shrink-0 flex-col items-center overflow-visible pb-5 pt-[15px] transition-[width] duration-200 ease-out ${
        isExpanded ? "w-[220px]" : "w-[84px]"
      }`}
    >
      <Link
        href={ROUTES.modules}
        aria-label="Back to modules"
        className={`mb-5 flex h-[60px] items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${
          isExpanded ? "w-[190px] rounded-lg" : "w-[60px] rounded-full"
        }`}
      >
        <Image
          src={isExpanded ? logoExtended : logoShort}
          alt="ADAVIS"
          width={isExpanded ? 180 : 60}
          height={60}
          className={
            isExpanded
              ? "h-auto max-h-[54px] w-[180px] object-contain"
              : "h-[60px] w-[60px]"
          }
          priority
        />
      </Link>

      <nav
        aria-label="Module navigation"
        className={`flex min-h-0 flex-1 flex-col gap-4 rounded-[14px] bg-[#0958ad] px-3 py-8 drop-shadow-[0_12px_14px_rgba(38,74,112,0.18)] transition-[width] duration-200 ease-out ${
          isExpanded
            ? "w-[212px] items-stretch"
            : "relative w-[76px] items-center gap-3 py-5"
        }`}
      >
        <div
          ref={scrollAreaRef}
          onScroll={() => {
            updateScrollIndicator();
            setHoveredTooltip(null);
          }}
          className={`flex min-h-0 w-full flex-1 flex-col gap-4 overflow-y-auto overscroll-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden ${
            isExpanded ? "" : "items-center pb-7"
          }`}
        >
          {navigationItems.map(({ label, icon: Icon, href, isActive }) => {
            const active = isActive?.(pathname) ?? false;
            const itemClassName = isExpanded
              ? `relative z-10 flex h-10 w-full items-center gap-3 rounded-lg px-3 text-[0.72rem] font-semibold transition-colors ${
                  active
                    ? "bg-white text-[#0759b5]"
                    : "text-white hover:bg-white/10"
                }`
              : `relative z-10 flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                  active
                    ? "bg-white text-[#0759b5]"
                    : "text-white hover:bg-white/10"
                }`;
            const ItemContent = (
              <>
                <Icon size={21} weight="regular" className="shrink-0" />
                {isExpanded ? (
                  <span className="truncate whitespace-nowrap">{label}</span>
                ) : null}
              </>
            );

            return (
              <div
                key={label}
                className="group relative z-50 w-full"
                onMouseEnter={(event) => {
                  if (isExpanded) return;

                  const rect = event.currentTarget.getBoundingClientRect();
                  setHoveredTooltip({
                    label,
                    icon: Icon,
                    left: rect.left,
                    top: rect.top + rect.height / 2,
                  });
                }}
                onMouseLeave={() => setHoveredTooltip(null)}
              >
                {href ? (
                  <Link
                    href={href}
                    aria-label={label}
                    aria-current={active ? "page" : undefined}
                    className={itemClassName}
                  >
                    {ItemContent}
                  </Link>
                ) : (
                  <button
                    type="button"
                    aria-label={label}
                    aria-current={active ? "page" : undefined}
                    className={itemClassName}
                  >
                    {ItemContent}
                  </button>
                )}

              </div>
            );
          })}
        </div>

        {isExpanded ? (
          <div className="flex w-full flex-col items-center gap-3">
            <Link
              href={ROUTES.modules}
              aria-label="Go to modules"
              title="Go to modules"
              className="flex h-8 w-full items-center justify-start gap-2 rounded-full border border-white/35 px-3 text-white transition-colors hover:bg-white hover:text-[#0759b5] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
            >
              <House size={15} weight="regular" />
              <span className="text-[0.65rem] font-semibold">Modules</span>
            </Link>

            <button
              type="button"
              aria-label="Collapse sidebar"
              title="Collapse sidebar"
              onClick={() => setIsExpanded(false)}
              className="flex h-8 w-full items-center justify-end gap-2 rounded-full border border-white/35 px-3 text-white transition-colors hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
            >
              <span className="text-[0.65rem] font-semibold">Collapse</span>
              <CaretLeft size={14} weight="bold" />
            </button>
          </div>
        ) : null}

        {!isExpanded ? (
          <div className="flex w-full flex-col items-center gap-3">
            <span className="h-px w-10 rounded-full bg-white/25" />

            <div className="flex w-full items-center justify-center gap-2">
              <Link
                href={ROUTES.modules}
                aria-label="Go to modules"
                title="Go to modules"
                className="flex h-8 w-8 items-center justify-center rounded-full border border-white/40 text-white transition-colors hover:bg-white hover:text-[#0759b5] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
              >
                <House size={15} weight="regular" />
              </Link>

              <button
                type="button"
                aria-label="Expand sidebar"
                title="Expand sidebar"
                onClick={() => setIsExpanded(true)}
                className="flex h-8 w-8 items-center justify-center rounded-full border border-white/40 text-white transition-colors hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
              >
                <CaretRight size={15} weight="bold" />
              </button>
            </div>
          </div>
        ) : null}

        {!isExpanded && canScrollDown ? (
          <button
            type="button"
            aria-label="Scroll sidebar navigation down"
            title="More navigation items"
            onClick={() => scrollAreaRef.current?.scrollBy({ top: 56, behavior: "smooth" })}
            className="absolute bottom-[78px] left-1/2 flex h-6 w-6 -translate-x-1/2 items-center justify-center rounded-full bg-[#0958ad]/95 text-white/75 transition-colors hover:bg-white hover:text-[#0759b5] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
          >
            <CaretDown size={13} weight="bold" />
          </button>
        ) : null}
      </nav>

      {!isExpanded && hoveredTooltip ? (
        <div
          className="
            pointer-events-none fixed z-[9999] flex h-10 -translate-y-1/2 items-center
            gap-2 rounded-full bg-white py-1 pl-1 pr-4 text-[0.64rem]
            font-semibold text-[#0759b5] shadow-[0_8px_18px_rgba(20,55,90,0.16)]
          "
          style={{ left: hoveredTooltip.left + 2, top: hoveredTooltip.top }}
        >
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#0759b5] text-white">
            <hoveredTooltip.icon size={18} weight="regular" />
          </span>
          <span className="max-w-[130px] truncate whitespace-nowrap">
            {hoveredTooltip.label}
          </span>
        </div>
      ) : null}
    </aside>
  );
}
