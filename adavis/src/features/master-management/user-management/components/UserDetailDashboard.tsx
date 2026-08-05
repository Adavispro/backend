import Image from "next/image";
import { CaretDown } from "@phosphor-icons/react/dist/ssr";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import daysIcon from "@/assets/status/activeDays.svg";
import lastLoginIcon from "@/assets/status/roles.svg";
import actionsIcon from "@/assets/status/totalActions.svg";
import loginIcon from "@/assets/status/totalLogin.svg";
import type { UserActivityRow } from "../data/user-activity";
import type { UserRow } from "../data/users";
import type { UserAuditMetrics } from "../hooks/useUserAuditActivity";

const activityColumns: DataTableColumn<UserActivityRow>[] = [
  {
    key: "date",
    header: "Date & Time",
    render: (row) => row.date,
    className: "w-1/4",
  },
  {
    key: "module",
    header: "Module",
    render: (row) => row.module,
    className: "w-1/4",
  },
  {
    key: "activity",
    header: "Activity",
    render: (row) => row.activity,
    className: "w-1/4",
  },
  {
    key: "description",
    header: "Description",
    render: (row) => row.description,
    className: "w-1/4",
  },
];

function UserSummary({ user }: { user: UserRow }) {
  return (
    <section className="module-glass-panel rounded-lg px-5 py-4 shadow-[0_10px_20px_rgba(35,50,70,0.1)]">
      <div className="flex items-center gap-3">
        <h1 className="text-[13px] font-semibold text-primary">{user.name}</h1>
        <StatusPill label="Online" className="bg-[#DDF6DF] text-[#158047]" />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-y-2 text-[9px] font-medium text-text-heading">
        {[
          "Operator",
          "Production",
          `User ID - ${user.id}`,
          `Email - ${user.email}`,
        ].map((detail, index) => (
          <span
            key={detail}
            className={`px-5 first:pl-0 last:pr-0 ${
              index > 0 ? "border-l border-[#D6DEE8]" : ""
            }`}
          >
            {detail}
          </span>
        ))}
      </div>
    </section>
  );
}

function UserMetricCards({
  isLoading,
  metrics,
}: {
  isLoading: boolean;
  metrics: UserAuditMetrics;
}) {
  const cards = [
    {
      label: "Total Logins",
      value: metrics.totalLogins,
      note: "From recorded login audit events",
      icon: loginIcon,
      className: "bg-[#E7F7EE]",
      noteClassName: "text-text-secondary",
    },
    {
      label: "Total Actions",
      value: metrics.totalActions,
      note: "From user audit activity",
      icon: actionsIcon,
      className: "bg-[#FFF8DD]",
      noteClassName: "text-success",
    },
    {
      label: "Last Login",
      value: metrics.lastLogin,
      note: "Latest login audit event",
      icon: lastLoginIcon,
      className: "bg-[#EAF2FF]",
      noteClassName: "text-success",
    },
    {
      label: "Active Days ( in last 30 days )",
      value: metrics.activeDays,
      note: "Unique activity days",
      icon: daysIcon,
      className: "bg-[#F0EAFF]",
      noteClassName: "text-text-secondary",
    },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {cards.map((card) => (
        <article
          key={card.label}
          className={`relative min-h-[96px] overflow-hidden rounded-lg border border-white/65 p-4 shadow-[0_10px_20px_rgba(35,50,70,0.12)] ${card.className}`}
        >
          <p className="text-[10px] font-medium text-text-secondary">{card.label}</p>
          <strong className="mt-3 block text-[26px] font-semibold leading-none text-text-heading">
            {isLoading ? "..." : card.value}
          </strong>
          <p className={`mt-2 text-[8px] font-semibold ${card.noteClassName}`}>
            {card.note}
          </p>
          <Image
            src={card.icon}
            alt=""
            aria-hidden="true"
            className="absolute right-5 top-1/2 h-8 w-8 -translate-y-1/2 object-contain opacity-60"
          />
        </article>
      ))}
    </div>
  );
}

function ActivityToolbar() {
  return (
    <button
      type="button"
      className="module-glass-control type-filter-button flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-heading"
    >
      Filter
      <CaretDown size={12} weight="bold" />
    </button>
  );
}

export default function UserDetailDashboard({
  activityRows,
  isActivityLoading,
  metrics,
  user,
}: {
  activityRows: UserActivityRow[];
  isActivityLoading: boolean;
  metrics: UserAuditMetrics;
  user: UserRow;
}) {
  return (
    <section aria-label={`${user.name} details`} className="grid gap-4">
      <UserSummary user={user} />
      <UserMetricCards isLoading={isActivityLoading} metrics={metrics} />
      <DataTable
        title="Activity Log"
        columns={activityColumns}
        rows={activityRows}
        getRowKey={(row, index) => row.id ?? `${row.date}-${row.module}-${index}`}
        tableClassName="table-fixed"
        toolbar={<ActivityToolbar />}
        emptyText={isActivityLoading ? "Loading activity..." : "No activity found."}
        footerText={`Showing ${activityRows.length} activity entries`}
        currentPage={1}
        totalPages={1}
      />
    </section>
  );
}
