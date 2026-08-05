"use client";

import { SidePanel } from "@/components/ui";
import DepartmentFiltersSection, {
  type DepartmentTableFilters,
} from "./DepartmentFiltersSection";

export type { DepartmentTableFilters } from "./DepartmentFiltersSection";

interface DepartmentFiltersPanelProps {
  draftFilters: DepartmentTableFilters;
  isOpen: boolean;
  parentOptions: string[];
  plantOptions: string[];
  statusOptions: string[];
  tenantOptions: string[];
  onApply: () => void;
  onClear: () => void;
  onClose: () => void;
  onDraftChange: (filters: DepartmentTableFilters) => void;
}

export default function DepartmentFiltersPanel({
  draftFilters,
  isOpen,
  parentOptions,
  plantOptions,
  statusOptions,
  tenantOptions,
  onApply,
  onClear,
  onClose,
  onDraftChange,
}: DepartmentFiltersPanelProps) {
  return (
    <SidePanel
      isOpen={isOpen}
      title="Filters"
      widthClassName="w-[378px]"
      onClose={onClose}
    >
      <DepartmentFiltersSection
        filters={draftFilters}
        parentOptions={parentOptions}
        plantOptions={plantOptions}
        statusOptions={statusOptions}
        tenantOptions={tenantOptions}
        onChange={onDraftChange}
        onApply={onApply}
        onClear={onClear}
      />
    </SidePanel>
  );
}
