"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  ListBullets,
  MagnifyingGlass,
  PencilSimple,
  Plus,
  Trash,
} from "@phosphor-icons/react/dist/ssr";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import ActionLabelTooltip from "@/components/table/ActionLabelTooltip";
import {
  ActionTooltip,
  ConfirmDialog,
  FilterButton,
  Snackbar,
} from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { getAuditLogsByAction } from "../../audit-logs/api";
import { useDepartments } from "../../department-management/hooks/useDepartments";
import type { User } from "../api/types";
import { deleteUser } from "../api";
import { useUsers } from "../hooks/useUsers";
import EditUserDialog from "./EditUserDialog";
import EditUserStatusDialog from "./EditUserStatusDialog";
import UpdateUserPasswordDialog from "./UpdateUserPasswordDialog";
import UserFiltersPanel, { type UserTableFilters } from "./UserFiltersPanel";

export type UserStatusFilter = "active" | "idle" | "blocked" | "deactivated";

interface UserRow {
  id: string;
  name: string;
  email: string;
  department: string;
  status: "Active" | "Blocked" | "Deactivated";
  source: User;
}

const getUserStatus = (user: User): UserRow["status"] => {
  const lifecycleStatus = user.lifecycleStatus?.toUpperCase();
  if (user.isBlocked) return "Blocked";
  if (lifecycleStatus === "DEACTIVATED" || lifecycleStatus === "INACTIVE") {
    return "Deactivated";
  }
  return user.isActive ? "Active" : "Deactivated";
};

const toUserRow = (
  user: User,
  departmentNamesById: Record<string, string>,
): UserRow => ({
  id: user.userId,
  name:
    [user.firstName, user.lastName].filter(Boolean).join(" ") ||
    user.username ||
    "-",
  email: user.email,
  department: user.departmentId
    ? departmentNamesById[user.departmentId] || user.departmentId
    : "-",
  status: getUserStatus(user),
  source: user,
});

const statusFilterLabels: Record<UserStatusFilter, UserRow["status"]> = {
  active: "Active",
  idle: "Active",
  blocked: "Blocked",
  deactivated: "Deactivated",
};

const statusPillClassNames: Record<UserRow["status"], string> = {
  Active: "bg-[#DDF6DF] text-[#158047]",
  Blocked: "bg-[#FFF1C9] text-[#C08400]",
  Deactivated: "bg-[#FFD7D7] text-[#E44141]",
};

const emptyFilters: UserTableFilters = {
  departments: [],
  statuses: [],
};

const countAppliedFilters = (filters: UserTableFilters) =>
  filters.departments.length + filters.statuses.length;

const uniqueValues = (values: string[]) =>
  Array.from(new Set(values.filter((value) => value && value !== "-"))).sort(
    (first, second) => first.localeCompare(second),
  );

const apiStatusFilters: Record<UserStatusFilter, { isActive?: boolean; isBlocked?: boolean }> = {
  active: { isActive: true, isBlocked: false },
  idle: { isActive: true, isBlocked: false },
  blocked: { isBlocked: true },
  deactivated: { isActive: false, isBlocked: false },
};

const IDLE_LOOKBACK_DAYS = 30;
const LOGIN_PAGE_SIZE = 100;

function UserActions({
  user,
  onEditDetails,
  onEditStatus,
  onUpdatePassword,
  onDelete,
}: {
  user: User;
  onEditDetails: (user: User) => void;
  onEditStatus: (user: User) => void;
  onUpdatePassword: (user: User) => void;
  onDelete: (user: User) => void;
}) {
  return (
    <div className="flex items-center gap-2">
      <ActionLabelTooltip label="View">
        <Link
          href={`/master-management/users/${encodeURIComponent(user.userId)}`}
          aria-label="View user"
          className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary transition-colors hover:bg-[#D6E8FF]"
        >
          <ListBullets size={12} weight="regular" />
        </Link>
      </ActionLabelTooltip>

      <ActionTooltip
        ariaLabel="Open user edit actions"
        trigger={
          <span className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary transition-colors hover:bg-[#D6E8FF]">
            <PencilSimple size={12} weight="regular" />
          </span>
        }
        options={[
          { label: "Edit User Details", onClick: () => onEditDetails(user) },
          { label: "Edit User Status", onClick: () => onEditStatus(user) },
          {
            label: "Update Password",
            onClick: () => onUpdatePassword(user),
          },
        ]}
      />

      <ActionLabelTooltip label="Delete">
        <button
          type="button"
          aria-label="Delete user"
          onClick={() => onDelete(user)}
          className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary transition-colors hover:bg-[#D6E8FF]"
        >
          <Trash size={12} weight="regular" />
        </button>
      </ActionLabelTooltip>
    </div>
  );
}

const createColumns = (
  onEditDetails: (user: User) => void,
  onEditStatus: (user: User) => void,
  onUpdatePassword: (user: User) => void,
  onDelete: (user: User) => void,
): DataTableColumn<UserRow>[] => [
  {
    key: "serialNumber",
    header: "S No.",
    render: (_row, index) => index + 1,
  },
  { key: "name", header: "Name", render: (row) => row.name },
  { key: "email", header: "Email", render: (row) => row.email },
  { key: "id", header: "User ID", render: (row) => row.id },
  
  { key: "department", header: "Department", render: (row) => row.department },
  {
    key: "status",
    header: "Status",
    render: (row) => (
      <StatusPill
        label={row.status}
        className={statusPillClassNames[row.status]}
      />
    ),
  },
  {
    key: "actions",
    header: "Actions",
    render: (row) => (
      <UserActions
        user={row.source}
        onEditDetails={onEditDetails}
        onEditStatus={onEditStatus}
        onUpdatePassword={onUpdatePassword}
        onDelete={onDelete}
      />
    ),
    disableRowLink: true,
  },
];

function UsersToolbar({
  appliedFilterCount,
  search,
  showCreateAction,
  onFilterClick,
  onSearchChange,
}: {
  appliedFilterCount: number;
  search: string;
  showCreateAction: boolean;
  onFilterClick: () => void;
  onSearchChange: (value: string) => void;
}) {
  return (
    <div className="flex items-center gap-3">
      <label className="module-glass-control hidden h-8 w-[310px] items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex">
        <MagnifyingGlass size={14} />
        <span className="sr-only">Search users</span>
        <input
          type="search"
          placeholder="Search Users"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          className="type-filter-value min-w-0 flex-1 bg-transparent outline-none placeholder:text-text-secondary"
        />
      </label>

      <FilterButton
        appliedCount={appliedFilterCount}
        onClick={onFilterClick}
      />

      {showCreateAction ? (
        <Link
          href={ROUTES.masterCreateUser}
          className="inline-flex h-8 items-center justify-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white shadow-[0_8px_18px_rgba(7,92,175,0.18)] transition-colors hover:bg-primary-hover"
        >
          <Plus size={13} weight="bold" />
          Create User
        </Link>
      ) : null}
    </div>
  );
}

export default function UsersTable({
  statusFilter,
}: {
  statusFilter?: UserStatusFilter;
}) {
  const { departments } = useDepartments();
  const [search, setSearch] = useState("");
  const [isFilterPanelOpen, setIsFilterPanelOpen] = useState(false);
  const [appliedFilters, setAppliedFilters] =
    useState<UserTableFilters>(emptyFilters);
  const [draftFilters, setDraftFilters] =
    useState<UserTableFilters>(emptyFilters);
  const [editingDetailsUser, setEditingDetailsUser] = useState<User | null>(null);
  const [editingStatusUser, setEditingStatusUser] = useState<User | null>(null);
  const [updatingPasswordUser, setUpdatingPasswordUser] =
    useState<User | null>(null);
  const [pendingDelete, setPendingDelete] = useState<User | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [idleUserIds, setIdleUserIds] = useState<Set<string> | null>(null);
  const [operationNotification, setOperationNotification] = useState({
    message: "",
    variant: "success" as "error" | "success",
  });
  const {
    clearError,
    errorMessage,
    isLoading,
    page,
    removeUser,
    replaceUser,
    setPage,
    usersPage,
  } = useUsers({
    pageSize: statusFilter || countAppliedFilters(appliedFilters) > 0
      ? 100
      : undefined,
    filters: statusFilter ? apiStatusFilters[statusFilter] : undefined,
  });
  const columns = useMemo(
    () =>
      createColumns(
        setEditingDetailsUser,
        setEditingStatusUser,
        setUpdatingPasswordUser,
        setPendingDelete,
      ),
    [],
  );
  const departmentNamesById = useMemo(
    () =>
      Object.fromEntries(
        departments.map((department) => [
          department.departmentId,
          department.departmentName || department.departmentId,
        ]),
      ),
    [departments],
  );
  const allRows = useMemo(
    () =>
      (usersPage?.content ?? []).map((user) =>
        toUserRow(user, departmentNamesById),
      ),
    [departmentNamesById, usersPage],
  );

  useEffect(() => {
    if (statusFilter !== "idle") {
      setIdleUserIds(null);
      return;
    }

    const controller = new AbortController();
    const end = new Date();
    const start = new Date(end);
    start.setDate(start.getDate() - IDLE_LOOKBACK_DAYS);

    void (async () => {
      const firstPage = await getAuditLogsByAction(
        {
          action: "LOGIN",
          page: 0,
          size: LOGIN_PAGE_SIZE,
          from: start.toISOString(),
          to: end.toISOString(),
        },
        controller.signal,
      );

      const loginUserIds = new Set(
        firstPage.content
          .map((log) => log.userId)
          .filter((userId): userId is string => Boolean(userId)),
      );

      if (firstPage.totalPages > 1) {
        const remainingPages = await Promise.all(
          Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
            getAuditLogsByAction(
              {
                action: "LOGIN",
                page: index + 1,
                size: LOGIN_PAGE_SIZE,
                from: start.toISOString(),
                to: end.toISOString(),
              },
              controller.signal,
            ),
          ),
        );

        remainingPages.flatMap((page) => page.content).forEach((log) => {
          if (log.userId) loginUserIds.add(log.userId);
        });
      }

      if (!controller.signal.aborted) {
        setIdleUserIds(loginUserIds);
      }
    })().catch(() => {
      if (!controller.signal.aborted) {
        setIdleUserIds(new Set());
      }
    });

    return () => controller.abort();
  }, [statusFilter]);

  const filterOptions = useMemo(
    () => ({
      departments: uniqueValues(allRows.map((user) => user.department)),
      statuses: ["Active", "Blocked", "Deactivated"],
    }),
    [allRows],
  );
  const appliedFilterCount = countAppliedFilters(appliedFilters);
  const isIdleView = statusFilter === "idle";
  const isClientFiltered = Boolean(statusFilter) || appliedFilterCount > 0;
  const rows = useMemo(() => {
    const mappedUsers = allRows
      .filter((user) =>
        statusFilter
          ? statusFilter === "idle"
            ? user.status === "Active" && idleUserIds
              ? !idleUserIds.has(user.id)
              : false
            : user.status === statusFilterLabels[statusFilter]
          : true,
      )
      .filter((user) =>
        appliedFilters.departments.length > 0
          ? appliedFilters.departments.includes(user.department)
          : true,
      )
      .filter((user) =>
        appliedFilters.statuses.length > 0
          ? appliedFilters.statuses.includes(user.status)
          : true,
      );
    const normalizedSearch = search.trim().toLowerCase();

    if (!normalizedSearch) return mappedUsers;

    return mappedUsers.filter((user) =>
      [user.id, user.name, user.email, user.department].some(
        (value) => value.toLowerCase().includes(normalizedSearch),
      ),
    );
  }, [allRows, appliedFilters, search, statusFilter, idleUserIds]);
  const totalElements = isClientFiltered ? rows.length : usersPage?.totalElements ?? 0;
  const pageSize = usersPage?.pageSize ?? 20;
  const firstEntry = totalElements === 0 ? 0 : isClientFiltered ? 1 : page * pageSize + 1;
  const lastEntry = isClientFiltered
    ? rows.length
    : Math.min((page + 1) * pageSize, totalElements);
  const tableTitle = isIdleView
    ? "Idle Users List"
    : statusFilter === "active"
      ? "Active Users List"
      : statusFilter === "blocked"
        ? "Blocked Users List"
        : statusFilter === "deactivated"
          ? "Deactivated Users List"
          : "Users List";
  const isIdleLoading = isIdleView && idleUserIds === null;

  const handleDelete = async () => {
    if (!pendingDelete) return;

    setIsDeleting(true);
    setOperationNotification({ message: "", variant: "success" });

    try {
      await deleteUser(pendingDelete.userId);
      removeUser(pendingDelete.userId);
      if ((usersPage?.content.length ?? 0) === 1 && page > 0) {
        setPage(page - 1);
      }
      setPendingDelete(null);
      setOperationNotification({
        message: "User deleted successfully.",
        variant: "success",
      });
    } catch (error) {
      setOperationNotification({
        message:
          error instanceof Error
            ? error.message
            : "Unable to delete user. Please try again.",
        variant: "error",
      });
    } finally {
      setIsDeleting(false);
    }
  };

  const handleOpenFilters = () => {
    setDraftFilters(appliedFilters);
    setIsFilterPanelOpen(true);
  };

  const handleApplyFilters = () => {
    setAppliedFilters(draftFilters);
    setPage(0);
    setIsFilterPanelOpen(false);
  };

  const handleClearFilters = () => {
    setDraftFilters(emptyFilters);
    setAppliedFilters(emptyFilters);
    setPage(0);
  };

  return (
    <>
      <DataTable
        title={tableTitle}
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.id}
        getRowHref={(row) =>
          `/master-management/users/${encodeURIComponent(row.id)}`
        }
        toolbar={
          <UsersToolbar
            appliedFilterCount={appliedFilterCount}
            search={search}
            showCreateAction={!statusFilter}
            onFilterClick={handleOpenFilters}
            onSearchChange={setSearch}
          />
        }
        footerText={`Showing ${firstEntry} to ${lastEntry} of ${totalElements} entries`}
        currentPage={isClientFiltered ? 1 : page + 1}
        totalPages={isClientFiltered ? 1 : Math.max(usersPage?.totalPages ?? 1, 1)}
        emptyText={isLoading || isIdleLoading ? "Loading users..." : "No users found."}
        onPageChange={
          isClientFiltered ? undefined : (nextPage) => setPage(nextPage - 1)
        }
      />

      <UserFiltersPanel
        isOpen={isFilterPanelOpen}
        draftFilters={draftFilters}
        departmentOptions={filterOptions.departments}
        statusOptions={filterOptions.statuses}
        onDraftChange={setDraftFilters}
        onApply={handleApplyFilters}
        onClear={handleClearFilters}
        onClose={() => setIsFilterPanelOpen(false)}
      />

      <Snackbar
        open={Boolean(errorMessage)}
        variant="error"
        title="Unable to load users"
        message={errorMessage}
        onClose={clearError}
      />

      <ConfirmDialog
        isOpen={Boolean(pendingDelete)}
        title="Delete User"
        message={`Are you sure you want to delete ${
          pendingDelete
            ? [pendingDelete.firstName, pendingDelete.lastName]
                .filter(Boolean)
                .join(" ") || pendingDelete.userId
            : "this user"
        }? This action will deactivate the account.`}
        confirmLabel="Delete User"
        isConfirming={isDeleting}
        onConfirm={() => void handleDelete()}
        onCancel={() => setPendingDelete(null)}
      />

      {editingDetailsUser ? (
        <EditUserDialog
          key={editingDetailsUser.userId}
          isOpen
          user={editingDetailsUser}
          onClose={() => setEditingDetailsUser(null)}
          onUpdated={replaceUser}
        />
      ) : null}

      {editingStatusUser ? (
        <EditUserStatusDialog
          key={editingStatusUser.userId}
          isOpen
          user={editingStatusUser}
          onClose={() => setEditingStatusUser(null)}
          onUpdated={replaceUser}
        />
      ) : null}

      {updatingPasswordUser ? (
        <UpdateUserPasswordDialog
          key={updatingPasswordUser.userId}
          isOpen
          user={updatingPasswordUser}
          onClose={() => setUpdatingPasswordUser(null)}
        />
      ) : null}

      <Snackbar
        open={Boolean(operationNotification.message)}
        variant={operationNotification.variant}
        title={
          operationNotification.variant === "success"
            ? "User deleted"
            : "Unable to delete user"
        }
        message={operationNotification.message}
        onClose={() =>
          setOperationNotification({ message: "", variant: "success" })
        }
      />

    </>
  );
}
