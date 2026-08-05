import Image from "next/image";
import Link from "next/link";
import criticalStatusIcon from "@/assets/status/critical.svg";
import warningStatusIcon from "@/assets/status/warning.svg";
import { StatusPill } from "@/components/table/DataTable";

export type CompactEventStatus = "Warning" | "Critical" | "Normal" | "Info";

export interface CompactEventItem {
  label: string;
  value: string;
  status?: CompactEventStatus;
  markerColor?: string;
}

interface CompactEventsCardProps {
  title: string;
  items: CompactEventItem[];
  actionLabel?: string;
  actionHref?: string;
  variant?: "compact" | "timeline";
}

const statusClasses: Record<CompactEventStatus, string> = {
  Critical: "bg-[#FFE2E2] text-[#D43B3B]",
  Info: "bg-[#E0F0FF] text-primary",
  Normal: "bg-[#DFF8EA] text-[#158047]",
  Warning: "bg-[#FFF4D2] text-[#B48205]",
};

const statusIconClasses: Partial<Record<CompactEventStatus, string>> = {
  Critical: "bg-[#FFE8E8]",
  Warning: "bg-[#FFF1C7]",
};

const statusIcons = {
  Critical: criticalStatusIcon,
  Warning: warningStatusIcon,
};

export default function CompactEventsCard({
  title,
  items,
  actionLabel = "View All",
  actionHref = "#",
  variant = "compact",
}: CompactEventsCardProps) {
  const isTimeline = variant === "timeline";

  return (
    <article className="module-glass-panel h-full overflow-hidden rounded-lg">
      <div className="flex items-center justify-between px-4 py-3">
        <h2 className="type-table-title">{title}</h2>
        <Link
          href={actionHref}
          className="type-chart-legend-value font-semibold text-primary"
        >
          {actionLabel} ›
        </Link>
      </div>
      <div className={isTimeline ? "relative pb-2" : "divide-y divide-[#E2E9F2]"}>
        {isTimeline ? (
          <span
            aria-hidden="true"
            className="absolute bottom-5 left-[28px] top-5 border-l border-dashed border-[#D1DAE5]"
          />
        ) : null}
        {items.map((item, index) => {
          const StatusIcon =
            item.status === "Critical" || item.status === "Warning"
              ? statusIcons[item.status]
              : undefined;

          return (
            <div
              key={`${item.label}-${item.status ?? item.markerColor ?? "event"}-${index}`}
              className={
                isTimeline
                  ? "relative grid grid-cols-[1fr_auto] items-center gap-4 px-5 py-2"
                  : "grid grid-cols-[1fr_auto_auto] items-center gap-3 px-4 py-2.5"
              }
            >
              <span
                className={
                  isTimeline
                    ? "type-table-compact inline-flex items-center gap-4"
                    : "type-table-compact inline-flex items-center gap-2.5"
                }
              >
                {item.status === undefined ? (
                  <span
                    className={
                      isTimeline
                        ? "relative z-10 h-2 w-2 rounded-full ring-[4px] ring-[#EAF1F8]"
                        : "h-1.5 w-1.5 rounded-full"
                    }
                    style={{ backgroundColor: item.markerColor ?? "#0B63C7" }}
                  />
                ) : StatusIcon !== undefined ? (
                  <span
                    className={`grid h-6 w-6 shrink-0 place-items-center rounded-full ${statusIconClasses[item.status] ?? "bg-[#EAF1F8]"}`}
                  >
                    <Image
                      src={StatusIcon}
                      alt=""
                      aria-hidden="true"
                      className="h-3.5 w-3.5 object-contain"
                    />
                  </span>
                ) : null}
                {item.label}
              </span>
              {!isTimeline ? (
                item.status !== undefined ? (
                  <StatusPill
                    label={item.status}
                    className={statusClasses[item.status]}
                  />
                ) : (
                  <span aria-hidden="true" />
                )
              ) : null}
              <span className="type-table-compact">
                {item.value}
              </span>
            </div>
          );
        })}
      </div>
    </article>
  );
}
