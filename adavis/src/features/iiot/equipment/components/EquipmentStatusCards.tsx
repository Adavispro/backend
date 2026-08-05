import Image, { type StaticImageData } from "next/image";
import Link from "next/link";
import factoryIcon from "@/assets/iiot/factory.svg";
import idleIcon from "@/assets/iiot/idle.svg";
import maintenanceIcon from "@/assets/iiot/maintenance.svg";
import offlineIcon from "@/assets/iiot/offline.svg";
import errorIcon from "@/assets/status/error.svg";
import runningIcon from "@/assets/status/running.svg";
import { PrimaryMetricCard } from "@/components/ui";
import { ROUTE_BUILDERS } from "@/config/routes";
import type { EquipmentStatusFilter } from "../data/equipment-overview";

interface StatusCardProps {
  label: string;
  value: string;
  percentage: string;
  backgroundColor: string;
  icon: StaticImageData;
  status: Exclude<EquipmentStatusFilter, "all">;
}

type StatusCounts = Record<EquipmentStatusFilter, number>;

const statusCardTemplates: Omit<StatusCardProps, "value" | "percentage">[] = [
  {
    label: "Running",
    backgroundColor: "#E7F7EE",
    icon: runningIcon,
    status: "running",
  },
  {
    label: "Idle",
    backgroundColor: "#FFF8DD",
    icon: idleIcon,
    status: "idle",
  },
  {
    label: "Comm. Error",
    backgroundColor: "#FCEAEA",
    icon: errorIcon,
    status: "communication-error",
  },
  {
    label: "Maintenance",
    backgroundColor: "#E8F2FF",
    icon: maintenanceIcon,
    status: "maintenance",
  },
  {
    label: "Offline",
    backgroundColor: "#ECECEE",
    icon: offlineIcon,
    status: "offline",
  },
];

function StatusCard({
  label,
  value,
  percentage,
  backgroundColor,
  icon,
  status,
}: StatusCardProps) {
  return (
    <Link
      href={ROUTE_BUILDERS.iiotEquipmentStatus(status)}
      aria-label={`View ${label.toLowerCase()} equipment`}
      className="relative min-h-[86px] overflow-hidden rounded-lg border border-white/65 p-3 text-text-heading shadow-[0_10px_20px_rgba(35,50,70,0.12)] transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
      style={{
        backgroundColor,
      }}
    >
      <p className="type-dashboard-card-title leading-none">
        {label}
      </p>
      <div className="mt-4 flex items-end gap-1.5">
        <strong className="type-dashboard-card-metric">
          {value}
        </strong>
        <span className="type-dashboard-percent pb-0.5">
          {percentage}
        </span>
      </div>
      <Image
        src={icon}
        alt=""
        aria-hidden="true"
        className="absolute right-3 top-1/2 h-[32px] w-[32px] -translate-y-1/2"
      />
    </Link>
  );
}

const formatPercentage = (value: number, total: number) =>
  total > 0 ? `${Math.round((value / total) * 100)}%` : "0%";

export default function EquipmentStatusCards({
  counts = {
    all: 24,
    running: 24,
    idle: 24,
    "communication-error": 24,
    maintenance: 24,
    offline: 24,
  },
}: {
  counts?: StatusCounts;
}) {
  const total = counts.all;
  const statusCards = statusCardTemplates.map((card) => ({
    ...card,
    value: String(counts[card.status]),
    percentage: formatPercentage(counts[card.status], total),
  }));

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.45fr_repeat(5,1fr)]">
      <PrimaryMetricCard
        label="Total Equipment"
        value={String(total)}
        icon={factoryIcon}
        href={ROUTE_BUILDERS.iiotEquipmentStatus("all")}
        ariaLabel="View all equipment"
      />
      {statusCards.map((card) => (
        <StatusCard key={card.label} {...card} />
      ))}
    </div>
  );
}
