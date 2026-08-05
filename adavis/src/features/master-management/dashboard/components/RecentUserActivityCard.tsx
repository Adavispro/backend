import Link from "next/link";
import { UserCircleGear } from "@phosphor-icons/react/dist/ssr";
import { ROUTES } from "@/config/routes";
import type { AuditLog } from "../../audit-logs/api";
import DashboardPanel from "./DashboardPanel";

const formatAction = (action: string) => action.toLowerCase().split("_").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
const formatTime = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-US", { hour: "2-digit", minute: "2-digit" }).format(date);
};
const description = (log: AuditLog) => typeof log.metadata?.description === "string" ? log.metadata.description : `${formatAction(log.action)} ${log.entity ?? "record"}${log.entityId ? ` ${log.entityId}` : ""}`;

export default function RecentUserActivityCard({ auditLogs }: { auditLogs: AuditLog[] }) {
  return <DashboardPanel title="Recent User Activity" className="min-h-[212px]"><Link href={ROUTES.masterAuditLogs} className="absolute right-4 top-4 text-[9px] font-semibold text-primary">View all ›</Link><div className="mt-5 grid gap-3">{auditLogs.length ? auditLogs.slice(0, 5).map((log, index) => <div key={log.id ?? log._id ?? log.eventId ?? `${log.action}-${index}`} className="grid grid-cols-[1fr_auto] items-center gap-4"><div className="flex min-w-0 items-center gap-2"><span className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-[#E7F7EE] text-[#2FB1A6]"><UserCircleGear size={12} /></span><p className="truncate text-[9px] font-medium text-text-heading"><span className="font-semibold text-primary">{log.username ?? log.userId ?? "System"}</span>{" "}{description(log)}</p></div><span className="text-[9px] font-medium text-text-heading">{formatTime(log.timestamp ?? log.createdAt)}</span></div>) : <p className="py-10 text-center text-[10px] font-medium text-text-secondary">No recent audit activity found.</p>}</div></DashboardPanel>;
}
