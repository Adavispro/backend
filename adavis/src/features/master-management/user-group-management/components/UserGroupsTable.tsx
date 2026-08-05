"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  MagnifyingGlass,
  PencilSimple,
  Plus,
  Power,
} from "@phosphor-icons/react/dist/ssr";
import ActionLabelTooltip from "@/components/table/ActionLabelTooltip";
import DataTable, { StatusPill, type DataTableColumn } from "@/components/table/DataTable";
import { ConfirmDialog, FilterButton, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useMasterLookups } from "../../lookups/hooks";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import type { Group } from "../api/types";
import { setGroupActive } from "../api";
import { useGroups } from "../hooks/useGroups";
import EditUserGroupDialog from "./EditUserGroupDialog";
import UserGroupFiltersPanel, {
  type UserGroupTableFilters,
} from "./UserGroupFiltersPanel";

interface UserGroupRow {
  id: string;
  code: string;
  tenant: string;
  name: string;
  roles: string[];
  usersCount: number;
  created: string;
  status: "Active" | "Inactive";
  source: Group;
}

const PAGE_SIZE = 20;
const emptyFilters: UserGroupTableFilters = {
  groupNames: [],
  roles: [],
};

const countAppliedFilters = (filters: UserGroupTableFilters) =>
  filters.groupNames.length + filters.roles.length;

const uniqueValues = (values: string[]) =>
  Array.from(new Set(values.filter((value) => value && value !== "-"))).sort(
    (first, second) => first.localeCompare(second),
  );

const formatDate = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "-"
    : date.toLocaleDateString("en-GB").replaceAll("/", "-");
};

const toGroupRow = (
  group: Group,
  assignment: { roleIds: string[]; userIds: string[] } | undefined,
  roleLabelsById: Record<string, string>,
  tenantNamesById: Record<string, string>,
): UserGroupRow => ({
  id: group.groupId,
  code: group.groupCode ?? "-",
  tenant: group.tenantId ? (tenantNamesById[group.tenantId] || group.tenantId) : "-",
  name: group.groupName || group.name,
  roles: assignment?.roleIds.length
    ? assignment.roleIds.map((roleId) => roleLabelsById[roleId] || roleId)
    : [],
  usersCount: assignment?.userIds.length ?? 0,
  created: formatDate(group.createdAt),
  status: group.isActive ? "Active" : "Inactive",
  source: group,
});

function RoleChips({ roles }: { roles: string[] }) {
  if (roles.length === 0) {
    return <span className="text-[9px] text-text-secondary">-</span>;
  }

  return (
    <div className="flex max-w-[260px] flex-wrap gap-1.5">
      {roles.map((role) => (
        <span
          key={role}
          className="inline-flex h-6 items-center rounded-md bg-[#DDEBFF] px-2.5 text-[8px] font-semibold text-[#1260BD]"
          title={role}
        >
          {role}
        </span>
      ))}
    </div>
  );
}

function UserCountBadge({ count }: { count: number }) {
  const label = `${count} ${count === 1 ? "User" : "Users"}`;

  return (
    <span className="inline-flex h-6 items-center rounded-full border border-[#DCE3EA] bg-[#F0F2F4] px-3 text-[8px] font-semibold text-text-secondary">
      {label}
    </span>
  );
}

function GroupActions({
  group,
  onEdit,
  onStatusChange,
}: {
  group: Group;
  onEdit: (group: Group) => void;
  onStatusChange: (group: Group) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <ActionLabelTooltip label="Edit">
        <button
          type="button"
          aria-label="Edit user group"
          disabled={!group.isActive}
          onClick={() => onEdit(group)}
          className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary transition-colors hover:bg-[#D6E8FF] disabled:cursor-not-allowed disabled:opacity-35"
        >
          <PencilSimple size={12} weight="regular" />
        </button>
      </ActionLabelTooltip>
      <ActionLabelTooltip label={group.isActive ? "Deactivate" : "Activate"}>
        <button
          type="button"
          aria-label={group.isActive ? "Deactivate user group" : "Activate user group"}
          onClick={() => onStatusChange(group)}
          className={`grid h-6 w-6 place-items-center rounded ${group.isActive ? "bg-[#FFF0F0] text-danger" : "bg-[#E7F7EE] text-success"}`}
        >
          <Power size={12} weight="regular" />
        </button>
      </ActionLabelTooltip>
    </div>
  );
}

const createColumns = (
  onEdit: (group: Group) => void,
  onStatusChange: (group: Group) => void,
): DataTableColumn<UserGroupRow>[] => [
  {
    key: "serialNumber",
    header: "S No.",
    render: (_row, index) => index + 1,
  },
  { key: "name", header: "Group Name", render: (row) => row.name },
  
  //{ key: "id", header: "Group ID", render: (row) => row.id },
  { key: "code", header: "Group Code", render: (row) => row.code },
  { key: "roles", header: "Assigned Roles", render: (row) => <RoleChips roles={row.roles} /> },
  {
    key: "usersCount",
    header: "Assigned Users",
    render: (row) => <UserCountBadge count={row.usersCount} />,
    
  },
  { key: "tenant", header: "Tenant", render: (row) => row.tenant },
  
  { key: "created", header: "Created", render: (row) => row.created },
  { key: "status", header: "Status", render: (row) => <StatusPill label={row.status} className={row.status === "Active" ? "bg-[#DDF6DF] text-[#158047]" : "bg-[#EBEEF2] text-text-secondary"} /> },
  {
    key: "actions",
    header: "Actions",
    render: (row) => (
      <GroupActions
        group={row.source}
        onEdit={onEdit}
        onStatusChange={onStatusChange}
      />
    ),
    disableRowLink: true,
  },
];

function UserGroupsToolbar({
  appliedFilterCount,
  onFilterClick,
  search,
  onSearchChange,
}: {
  appliedFilterCount: number;
  onFilterClick: () => void;
  search: string;
  onSearchChange: (value: string) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <label className="module-glass-control hidden h-8 w-[310px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex">
        <MagnifyingGlass size={14} />
        <span className="sr-only">Search groups</span>
        <input
          type="search"
          placeholder="Search Groups"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          className="type-filter-value min-w-0 flex-1 bg-transparent outline-none placeholder:text-text-secondary"
        />
      </label>

      <FilterButton
        appliedCount={appliedFilterCount}
        onClick={onFilterClick}
      />

      <Link
        href={ROUTES.masterCreateUserGroup}
        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white shadow-[0_8px_18px_rgba(7,92,175,0.18)] transition-colors hover:bg-primary-hover"
      >
        <Plus size={13} weight="bold" />
        Create User Group
      </Link>
    </div>
  );
}

export default function UserGroupsTable() {
  const [search, setSearch] = useState("");
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);
  const [appliedFilters, setAppliedFilters] =
    useState<UserGroupTableFilters>(emptyFilters);
  const [draftFilters, setDraftFilters] =
    useState<UserGroupTableFilters>(emptyFilters);
  const [page, setPage] = useState(1);
  const [editingGroup, setEditingGroup] = useState<Group | null>(null);
  const [statusTarget, setStatusTarget] = useState<Group | null>(null);
  const [isChangingStatus, setIsChangingStatus] = useState(false);
  const [operationNotification, setOperationNotification] = useState({
    message: "",
    variant: "success" as "error" | "success",
  });
  const {
    addGroupAssignment,
    assignments,
    clearError,
    errorMessage,
    groups,
    isLoading,
    replaceGroup,
  } = useGroups();
  const { tenants } = useTenants();
  const { options } = useMasterLookups();
  const roleLabelsById = useMemo(
    () => Object.fromEntries(options.roles.map((role) => [role.value, role.label || role.value])),
    [options.roles],
  );
  const tenantNamesById = useMemo(
    () => Object.fromEntries(tenants.map((tenant) => [tenant.tenantId, tenant.companyName || tenant.tenantId])),
    [tenants],
  );
  const columns = useMemo(
    () => createColumns(setEditingGroup, setStatusTarget),
    [],
  );
  const allRows = useMemo(
    () =>
      groups.map((group) =>
        toGroupRow(
          group,
          assignments[group.groupId],
          roleLabelsById,
          tenantNamesById,
        ),
      ),
    [assignments, groups, roleLabelsById, tenantNamesById],
  );
  const filterOptions = useMemo(
    () => ({
      groupNames: uniqueValues(allRows.map((group) => group.name)),
      roles: uniqueValues(allRows.flatMap((group) => group.roles)),
    }),
    [allRows],
  );
  const appliedFilterCount = countAppliedFilters(appliedFilters);
  const filteredRows = useMemo(() => {
    const rows = allRows
      .filter((group) =>
        appliedFilters.groupNames.length > 0
          ? appliedFilters.groupNames.includes(group.name)
          : true,
      )
      .filter((group) =>
        appliedFilters.roles.length > 0
          ? group.roles.some((role) => appliedFilters.roles.includes(role))
          : true,
      );
    const normalizedSearch = search.trim().toLowerCase();

    if (!normalizedSearch) return rows;

    return rows.filter((group) =>
      [group.id, group.name, group.tenant, ...group.roles].some((value) =>
        value.toLowerCase().includes(normalizedSearch),
      ),
    );
  }, [allRows, appliedFilters, search]);
  const totalPages = Math.max(Math.ceil(filteredRows.length / PAGE_SIZE), 1);
  const currentPage = Math.min(page, totalPages);
  const firstIndex = (currentPage - 1) * PAGE_SIZE;
  const visibleRows = filteredRows.slice(firstIndex, firstIndex + PAGE_SIZE);
  const firstEntry = filteredRows.length === 0 ? 0 : firstIndex + 1;
  const lastEntry = Math.min(firstIndex + PAGE_SIZE, filteredRows.length);

  const handleSearchChange = (value: string) => {
    setSearch(value);
    setPage(1);
  };

  const handleOpenFilters = () => {
    setDraftFilters(appliedFilters);
    setIsFilterPanelOpen(true);
  };

  const handleApplyFilters = () => {
    setAppliedFilters(draftFilters);
    setPage(1);
    setIsFilterPanelOpen(false);
  };

  const handleClearFilters = () => {
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPage(1);
  };

  const handleStatusChange = async () => {
    if (!statusTarget) return;

    setIsChangingStatus(true);
    setOperationNotification({ message: "", variant: "success" });

    try {
      const updated = await setGroupActive(statusTarget, !statusTarget.isActive);
      replaceGroup(updated);
      setStatusTarget(null);
      setOperationNotification({
        message: `User group ${statusTarget.isActive ? "deactivated" : "activated"} successfully.`,
        variant: "success",
      });
    } catch (error) {
      setOperationNotification({
        message:
          error instanceof Error
            ? error.message
            : "Unable to update user group status. Please try again.",
        variant: "error",
      });
    } finally {
      setIsChangingStatus(false);
    }
  };

  return (
    <>
      <DataTable
        title="User Groups"
        columns={columns}
        rows={visibleRows}
        getRowKey={(row) => row.id}
        toolbar={
          <UserGroupsToolbar
            appliedFilterCount={appliedFilterCount}
            search={search}
            onFilterClick={handleOpenFilters}
            onSearchChange={handleSearchChange}
          />
        }
        footerText={`Showing ${firstEntry} to ${lastEntry} of ${filteredRows.length} entries`}
        currentPage={currentPage}
        totalPages={totalPages}
        emptyText={isLoading ? "Loading user groups..." : "No user groups found."}
        onPageChange={setPage}
      />

      <UserGroupFiltersPanel
        isOpen={isFilterPanelOpen}
        draftFilters={draftFilters}
        groupNameOptions={filterOptions.groupNames}
        roleOptions={filterOptions.roles}
        onDraftChange={setDraftFilters}
        onApply={handleApplyFilters}
        onClear={handleClearFilters}
        onClose={() => setIsFilterPanelOpen(false)}
      />

      <Snackbar
        open={Boolean(errorMessage)}
        variant="error"
        title="Unable to load user groups"
        message={errorMessage}
        onClose={clearError}
      />

      <ConfirmDialog
        isOpen={Boolean(statusTarget)}
        title={`${statusTarget?.isActive ? "Deactivate" : "Activate"} User Group`}
        message={`${statusTarget?.isActive ? "Deactivate" : "Activate"} ${statusTarget?.groupName || statusTarget?.name || statusTarget?.groupId || "this user group"}?`}
        confirmLabel={statusTarget?.isActive ? "Deactivate" : "Activate"}
        isConfirming={isChangingStatus}
        onConfirm={() => void handleStatusChange()}
        onCancel={() => setStatusTarget(null)}
      />

      {editingGroup ? (
        <EditUserGroupDialog
          key={editingGroup.groupId}
          assignments={assignments[editingGroup.groupId]}
          group={editingGroup}
          onClose={() => setEditingGroup(null)}
          onUpdated={(updatedGroup, assignmentUpdates) => {
            replaceGroup(updatedGroup);
            if (assignmentUpdates) {
              addGroupAssignment(updatedGroup.groupId, assignmentUpdates);
            }
          }}
        />
      ) : null}

      <Snackbar
        open={Boolean(operationNotification.message)}
        variant={operationNotification.variant}
        title={
          operationNotification.variant === "success"
            ? "User group updated"
            : "Unable to update user group"
        }
        message={operationNotification.message}
        onClose={() =>
          setOperationNotification({ message: "", variant: "success" })
        }
      />
    </>
  );
}
