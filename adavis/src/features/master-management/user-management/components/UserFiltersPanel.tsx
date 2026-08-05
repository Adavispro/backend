"use client";

import { SidePanel } from "@/components/ui";
import UserFiltersSection, {
  type UserTableFilters,
} from "./UserFiltersSection";

export type { UserTableFilters } from "./UserFiltersSection";

interface UserFiltersPanelProps {
  isOpen: boolean;
  draftFilters: UserTableFilters;
  departmentOptions: string[];
  statusOptions: string[];
  onDraftChange: (filters: UserTableFilters) => void;
  onApply: () => void;
  onClear: () => void;
  onClose: () => void;
}

export default function UserFiltersPanel({
  isOpen,
  draftFilters,
  departmentOptions,
  statusOptions,
  onDraftChange,
  onApply,
  onClear,
  onClose,
}: UserFiltersPanelProps) {
  return (
    <SidePanel
      isOpen={isOpen}
      title="Filters"
      widthClassName="w-[378px]"
      onClose={onClose}
    >
      <UserFiltersSection
        filters={draftFilters}
        departmentOptions={departmentOptions}
        statusOptions={statusOptions}
        onChange={onDraftChange}
        onApply={onApply}
        onClear={onClear}
      />
    </SidePanel>
  );
}
