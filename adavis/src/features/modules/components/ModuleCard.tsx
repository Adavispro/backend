import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowUpRight } from "@phosphor-icons/react";
import type { AppRoute } from "@/config/routes";

export interface ModuleCardProps {
  icon: ReactNode;
  iconBg: string;
  iconColor: string;
  title: string;
  description: string;
  href: AppRoute;
}

export default function ModuleCard({
  icon,
  iconBg,
  iconColor,
  title,
  description,
  href,
}: ModuleCardProps) {
  return (
    <Link
      href={href}
      aria-label={`Open ${title}`}
      className="
        group relative flex min-h-[158px] flex-col rounded-[14px]
        border border-[#e1e5ea]
        p-4 pb-11
        shadow-[0_8px_18px_rgba(21,40,64,0.10)]
        transition-all duration-200
        hover:-translate-y-0.5 hover:shadow-[0_12px_24px_rgba(21,40,64,0.14)]
        focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2
      "
      style={{
        background: `linear-gradient(135deg, #ffffff 0%, ${iconBg}66 100%)`,
      }}
    >
      <div
        className="mb-3 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
        style={{ backgroundColor: iconBg, color: iconColor }}
      >
        {icon}
      </div>

      <p className="mb-2 text-[0.78rem] font-semibold leading-snug text-[#0056b8]">
        {title}
      </p>

      <p className="max-w-[92%] text-[0.59rem] leading-[1.65] text-[#424850]">
        {description}
      </p>

      <span
        className="
          absolute bottom-3.5 right-3.5
          flex h-8 w-8 items-center justify-center rounded-full bg-[#0058b8]
          transition-colors group-hover:bg-primary-hover
        "
      >
        <ArrowUpRight size={14} weight="bold" color="white" />
      </span>
    </Link>
  );
}
