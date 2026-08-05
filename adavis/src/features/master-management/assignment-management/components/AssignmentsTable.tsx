"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  MagnifyingGlass,
  PencilSimple,
  Plus,
  Power,
  Trash,
} from "@phosphor-icons/react";
import DataTable, {
  StatusPill,
  type DataTableColumn,
} from "@/components/table/DataTable";
import { ConfirmDialog, Snackbar } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import { useMasterLookups } from "../../lookups/hooks";
import { usePlantTopology } from "../../plant-topology/hooks/usePlantTopology";
import { useTenants } from "../../tenant-management/hooks/useTenants";
import { deactivateAssignment, reactivateAssignment } from "../api";
import type { Assignment } from "../api";
import { useAssignments } from "../hooks/useAssignments";
import EditAssignmentDialog from "./EditAssignmentDialog";

const formatDate = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "-"
    : date.toLocaleDateString("en-GB").replaceAll("/", "-");
};

export default function AssignmentsTable() {
  const { options } = useMasterLookups();
  const { tenants } = useTenants();
  const { data: topology } = usePlantTopology();
  const {
    assignments,
    clearError,
    errorMessage,
    isLoading,
    markInactive,
    removeAssignment,
    replaceAssignment,
  } = useAssignments();

  const [search, setSearch] = useState("");
  const [statusTarget, setStatusTarget] = useState<Assignment | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Assignment | null>(null);
  const [editing, setEditing] = useState<Assignment | null>(null);
  const [changing, setChanging] = useState(false);
  const [notice, setNotice] = useState<{
    message: string;
    variant: "success" | "error";
  }>({ message: "", variant: "success" });

  const rows = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return assignments;

    return assignments.filter((item) =>
      Object.values(item).some((value) =>
        String(value ?? "").toLowerCase().includes(query),
      ),
    );
  }, [assignments, search]);

  const tenantLabelsById = useMemo(
    () =>
      Object.fromEntries(
        tenants.map((tenant) => [
          tenant.tenantId,
          tenant.companyName ? `${tenant.companyName} (${tenant.tenantId})` : tenant.tenantId,
        ]),
      ),
    [tenants],
  );

  const userLabelsById = useMemo(
    () => Object.fromEntries(options.users.map((user) => [user.value, user.label || user.value])),
    [options.users],
  );

  const groupLabelsById = useMemo(
    () => Object.fromEntries(options.groups.map((group) => [group.value, group.label || group.value])),
    [options.groups],
  );

  const plantLabelsById = useMemo(
    () =>
      Object.fromEntries(
        topology.plants.map((plant) => [
          plant.plantId,
          plant.plantName ? `${plant.plantName} (${plant.plantId})` : plant.plantId,
        ]),
      ),
    [topology.plants],
  );

  const columns = useMemo<DataTableColumn<Assignment>[]>(
    () => [
      { key: "serial", header: "S No.", render: (_row, index) => index + 1 },
    //   {
    //     key: "assignmentId",
    //     header: "Assignment ID",
    //     render: (row) => row.assignmentId,
    //   },
      {
        key: "tenantId",
        header: "Tenant",
        render: (row) => tenantLabelsById[row.tenantId] ?? row.tenantId,
      },
      {
        key: "assignmentType",
        header: "Assignment Type",
        render: (row) => row.assignmentType,
      },
      {
        key: "principal",
        header: "Group Name / User Name",
        render: (row) => {
          if (row.assignmentType === "USER_OVERRIDE") {
            const userId = row.userId ?? "";
            if (!userId) return "-";
            return userLabelsById[userId] ?? userId;
          }

          const groupId = row.groupId ?? "";
          if (!groupId) return "-";
          return groupLabelsById[groupId] ?? groupId;
        },
      },
      {
        key: "scopeType",
        header: "Scope Type",
        render: (row) => row.scopeType,
      },
      {
        key: "target",
        header: "Plant / Resource",
        render: (row) => {
          if (row.scopeType === "PLANT") {
            const plantId = row.plantId ?? "";
            if (!plantId) return "-";
            return plantLabelsById[plantId] ?? plantId;
          }

          return row.resourceId ?? "-";
        },
      },
    //   {
    //     key: "assignedBy",
    //     header: "Assigned By",
    //     render: (row) => row.assignedBy ?? "-",
    //   },
    //   {
    //     key: "reason",
    //     header: "Reason",
    //     render: (row) => (
    //       <span className="line-clamp-2 max-w-[220px]" title={row.reason ?? "-"}>
    //         {row.reason ?? "-"}
    //       </span>
    //     ),
    //   },
      {
        key: "createdAt",
        header: "Created",
        render: (row) => formatDate(row.createdAt),
      },
      {
        key: "status",
        header: "Status",
        render: (row) => (
          <StatusPill
            label={row.isActive ? "Active" : "Inactive"}
            className={
              row.isActive
                ? "bg-[#DDF6DF] text-[#158047]"
                : "bg-[#EBEEF2] text-text-secondary"
            }
          />
        ),
      },
      {
        key: "actions",
        header: "Actions",
        disableRowLink: true,
        render: (row) => (
          <div className="flex items-center gap-2">
            <button
              type="button"
              aria-label={`Edit ${row.assignmentId}`}
              onClick={() => setEditing(row)}
              className="grid h-6 w-6 place-items-center rounded bg-[#E6F1FF] text-primary hover:bg-[#D6E8FF]"
            >
              <PencilSimple size={12} weight="regular" />
            </button>
            <button
              type="button"
              aria-label={`Delete ${row.assignmentId}`}
              onClick={() => setDeleteTarget(row)}
              className="grid h-6 w-6 place-items-center rounded bg-[#FFF0F0] text-danger hover:bg-[#FFE2E2]"
            >
              <Trash size={12} weight="regular" />
            </button>
            <button
              type="button"
              aria-label={
                row.isActive ? "Deactivate assignment" : "Reactivate assignment"
              }
              onClick={() => setStatusTarget(row)}
              className={`grid h-6 w-6 place-items-center rounded ${
                row.isActive
                  ? "bg-[#FFF7E5] text-[#E09A00] hover:bg-[#FFEFCC]"
                  : "bg-[#E7F7EE] text-success hover:bg-[#D8F1E4]"
              }`}
            >
              <Power size={12} />
            </button>
          </div>
        ),
      },
    ],
    [groupLabelsById, plantLabelsById, tenantLabelsById, userLabelsById],
  );

  const changeStatus = async () => {
    if (!statusTarget) return;

    setChanging(true);
    try {
      if (statusTarget.isActive) {
        await deactivateAssignment(statusTarget.assignmentId);
        markInactive(statusTarget.assignmentId);
      } else {
        const updated = await reactivateAssignment(statusTarget.assignmentId);
        replaceAssignment(updated);
      }

      setNotice({
        message: `Assignment ${statusTarget.isActive ? "deactivated" : "reactivated"} successfully.`,
        variant: "success",
      });
      setStatusTarget(null);
    } catch (error) {
      setNotice({
        message:
          error instanceof Error
            ? error.message
            : "Unable to update assignment status.",
        variant: "error",
      });
    } finally {
      setChanging(false);
    }
  };

  const deleteAssignment = async () => {
    if (!deleteTarget) return;

    setChanging(true);
    try {
      await deactivateAssignment(deleteTarget.assignmentId);
      removeAssignment(deleteTarget.assignmentId);
      setNotice({ message: "Assignment deleted successfully.", variant: "success" });
      setDeleteTarget(null);
    } catch (error) {
      setNotice({
        message:
          error instanceof Error ? error.message : "Unable to delete assignment.",
        variant: "error",
      });
    } finally {
      setChanging(false);
    }
  };

  return (
    <>
      <DataTable
        title="Assignments"
        columns={columns}
        rows={rows}
        getRowKey={(row) => row.assignmentId}
        emptyText={isLoading ? "Loading assignments..." : "No assignments found."}
        showPagination
        footerText={`SHOWING ${rows.length} ENTRIES`}
        toolbar={
          <div className="flex items-center gap-3">
            <label className="module-glass-control hidden h-8 w-[300px] items-center gap-2 px-3 md:flex">
              <MagnifyingGlass size={13} />
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search Assignments"
                className="type-filter-value min-w-0 flex-1 bg-transparent outline-none"
              />
            </label>
            <Link
              href={ROUTES.masterCreateAssignment}
              className="inline-flex h-8 items-center gap-1.5 rounded-[4px] bg-primary px-4 text-[10px] font-semibold text-white"
            >
              <Plus size={12} />
              Create Assignment
            </Link>
          </div>
        }
      />

      <ConfirmDialog
        isOpen={Boolean(deleteTarget)}
        title="Delete Assignment"
        message={`Delete ${deleteTarget?.assignmentId ?? "this assignment"}?`}
        confirmLabel="Delete"
        isConfirming={changing}
        onConfirm={deleteAssignment}
        onCancel={() => setDeleteTarget(null)}
      />

      <ConfirmDialog
        isOpen={Boolean(statusTarget)}
        title={`${statusTarget?.isActive ? "Deactivate" : "Reactivate"} Assignment`}
        message={`${statusTarget?.isActive ? "Deactivate" : "Reactivate"} ${statusTarget?.assignmentId ?? "this assignment"}?`}
        confirmLabel={statusTarget?.isActive ? "Deactivate" : "Reactivate"}
        isConfirming={changing}
        onConfirm={changeStatus}
        onCancel={() => setStatusTarget(null)}
      />

      <EditAssignmentDialog
        key={editing?.assignmentId ?? "assignment-edit-closed"}
        assignment={editing}
        onClose={() => setEditing(null)}
        onUpdated={(updated, previousId) => {
          replaceAssignment(updated, previousId);
          setNotice({ message: "Assignment updated successfully.", variant: "success" });
        }}
      />

      <Snackbar
        open={Boolean(errorMessage || notice.message)}
        title={
          errorMessage || notice.variant === "error"
            ? "Assignment operation failed"
            : "Assignment updated"
        }
        message={errorMessage || notice.message}
        variant={errorMessage ? "error" : notice.variant}
        onClose={() => {
          clearError();
          setNotice({ message: "", variant: "success" });
        }}
      />
    </>
  );
}
