import type { ComponentType } from "react";
import type { IconProps } from "@phosphor-icons/react";

export interface OverviewMetricCardProps {
  label: string;
  value: string;
  note: string;
  icon: ComponentType<IconProps>;
  variant?: "green" | "yellow" | "blue" | "purple";
}

export default function OverviewMetricCard({
  label,
  value,
  note,
  icon: Icon,
  variant = "blue",
}: OverviewMetricCardProps) {
  const styles = {
    green: "bg-[#E7F7EE] text-text-heading",
    yellow: "bg-[#FFF8DD] text-text-heading",
    blue: "bg-[#EAF2FF] text-text-heading",
    purple: "bg-[#F0EAFF] text-text-heading",
  }[variant];
  const iconColor = {
    green: "text-[#8BCDB5]",
    yellow: "text-[#DFC66F]",
    blue: "text-[#91B9E8]",
    purple: "text-[#BEA7E5]",
  }[variant];

  return (
    <article
      className={`relative min-h-[90px] overflow-hidden rounded-lg border border-white/65 p-3.5 shadow-[0_10px_20px_rgba(35,50,70,0.1)] ${styles}`}
    >
      <p
        className="type-dashboard-card-title"
      >
        {label}
      </p>
      <strong className="mt-3 block text-[23px] font-semibold leading-none">
        {value}
      </strong>
      <p
        className="mt-2 text-[8px] font-semibold text-success"
      >
        {note}
      </p>
      <Icon
        size={34}
        weight="regular"
        className={`absolute right-3 top-1/2 -translate-y-1/2 ${iconColor}`}
      />
    </article>
  );
}
