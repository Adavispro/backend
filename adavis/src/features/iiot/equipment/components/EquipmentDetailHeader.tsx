import { ArrowsClockwise, DownloadSimple } from "@phosphor-icons/react/dist/ssr";
import type { StaticImageData } from "next/image";
import alarmsIcon from "@/assets/iiot/alarms.svg";
import eventsIcon from "@/assets/iiot/events.svg";
import parametersIcon from "@/assets/iiot/parameters.svg";
import trendsIcon from "@/assets/iiot/trends.svg";
import { Button } from "@/components/ui";
import { StatusPill } from "@/components/table/DataTable";
import type { DetailTab } from "../data/types";

interface EquipmentDetailHeaderProps {
  equipmentId: string;
  activeTab: DetailTab;
  isSolid: boolean;
  statusLabel?: string;
  subtitle?: string;
  metadataItems?: Array<{ label: string; value: string }>;
  autoRefreshEnabled?: boolean;
  onToggleAutoRefresh?: () => void;
  onTabSelect: (tab: DetailTab) => void;
  onDownloadReport?: () => void;
}

export default function EquipmentDetailHeader({
  equipmentId,
  activeTab,
  isSolid,
  statusLabel = "Running",
  subtitle = "Fluid Bed Dryer | Plant 2 / Block C / Room 204",
  metadataItems = [],
  autoRefreshEnabled = false,
  onToggleAutoRefresh,
  onTabSelect,
  onDownloadReport,
}: EquipmentDetailHeaderProps) {
  const tabs: Array<{
    label: string;
    icon: StaticImageData;
    id: DetailTab;
  }> = [
    { label: "Parameters", icon: parametersIcon, id: "parameters" },
    { label: "Trends", icon: trendsIcon, id: "trends" },
    { label: "Alarms", icon: alarmsIcon, id: "alarms" },
    { label: "Events", icon: eventsIcon, id: "events" },
  ];

  return (
    <section
      className={`rounded-lg px-6 pb-3 pt-5 transition-[background-color,border-color,box-shadow] duration-200 ${
        isSolid
          ? "border border-[#E2E9F2] bg-white shadow-[0_10px_24px_rgba(35,50,70,0.14)]"
          : "module-glass-panel"
      }`}
    >
      <div className="flex items-start justify-between gap-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-[15px] font-semibold leading-none text-text-heading">
              {equipmentId}
            </h1>
            <StatusPill label={statusLabel} className="bg-[#DFF8EA] text-[#158047]" />
          </div>
          {metadataItems.length > 0 ? (
            <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
              {metadataItems.map((item) => (
                <div
                  key={item.label}
                  className="rounded-md border border-[#D9E2EE] bg-white/45 px-3 py-2"
                >
                  <p className="text-[10px] font-medium uppercase tracking-[0.08em] text-text-secondary">
                    {item.label}
                  </p>
                  <p className="mt-1 break-words text-[11px] font-semibold text-text-heading">
                    {item.value}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <p className="mt-4 text-xs font-normal text-text-secondary">
              {subtitle}
            </p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant={autoRefreshEnabled ? "primary" : "ghost"}
            onClick={onToggleAutoRefresh}
            size="sm"
            prefixIcon={<ArrowsClockwise size={12} weight="bold" className={autoRefreshEnabled ? "animate-spin" : ""} />}
            textSize="text-[11px]"
            paddingX="px-4"
            paddingY="py-0"
            rounded="rounded-[4px]"
            className={`h-9 ${autoRefreshEnabled ? "" : "border border-primary/35 bg-white/35 !text-primary hover:bg-white/65"}`}
          >
            {autoRefreshEnabled ? "Auto Refresh On" : "Auto Refresh Off"}
          </Button>

          <Button
            onClick={onDownloadReport}
            size="sm"
            prefixIcon={<DownloadSimple size={12} weight="bold" />}
            textSize="text-[11px]"
            paddingX="px-4"
            paddingY="py-0"
            rounded="rounded-[4px]"
            className="h-9"
          >
            Download Report
          </Button>
        </div>
      </div>

      <nav className="mt-7 flex items-center gap-8 text-text-secondary">
        {tabs.map(({ label, icon, id }) => {
          const active = id === activeTab;

          return (
            <button
              type="button"
              key={label}
              onClick={() => onTabSelect(id)}
              aria-current={active ? "page" : undefined}
              className={`flex items-center gap-1.5 border-b-2 pb-1 text-[11px] font-medium ${active
                  ? "border-primary text-primary"
                  : "border-transparent text-text-secondary"
                }`}
            >
              <span
                aria-hidden="true"
                className="h-3 w-3 bg-current"
                style={{
                  maskImage: `url(${icon.src})`,
                  WebkitMaskImage: `url(${icon.src})`,
                  maskRepeat: "no-repeat",
                  WebkitMaskRepeat: "no-repeat",
                  maskPosition: "center",
                  WebkitMaskPosition: "center",
                  maskSize: "contain",
                  WebkitMaskSize: "contain",
                }}
              />
              {label}
            </button>
          );
        })}
      </nav>
    </section>
  );
}
