"use client";

import { SidePanel } from "@/components/ui";
import UserGroupFiltersSection, {
  type UserGroupTableFilters,
} from "./UserGroupFiltersSection";

export type { UserGroupTableFilters } from "./UserGroupFiltersSection";

interface UserGroupFiltersPanelProps {
  isOpen: boolean;
  draftFilters: UserGroupTableFilters;
  groupNameOptions: string[];
  roleOptions: string[];
  onDraftChange: (filters: UserGroupTableFilters) => void;
  onApply: () => void;
  onClear: () => void;
  onClose: () => void;
}

export default function UserGroupFiltersPanel({
  isOpen,
  draftFilters,
  groupNameOptions,
  roleOptions,
  onDraftChange,
  onApply,
  onClear,
  onClose,
}: UserGroupFiltersPanelProps) {
  return (
    <SidePanel
      isOpen={isOpen}
      title="Filters"
      widthClassName="w-[378px]"
      onClose={onClose}
    >
      <UserGroupFiltersSection
        filters={draftFilters}
        groupNameOptions={groupNameOptions}
        roleOptions={roleOptions}
        onChange={onDraftChange}
        onApply={onApply}
        onClear={onClear}
      />
    </SidePanel>
  );
}
