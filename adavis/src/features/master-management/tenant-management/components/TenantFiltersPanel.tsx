"use client";

import { SidePanel } from "@/components/ui";
import TenantFiltersSection, {
  type TenantTableFilters,
} from "./TenantFiltersSection";

export type { TenantTableFilters } from "./TenantFiltersSection";

interface TenantFiltersPanelProps {
  isOpen: boolean;
  draftFilters: TenantTableFilters;
  companyCodeOptions: string[];
  companyNameOptions: string[];
  domainOptions: string[];
  statusOptions: string[];
  onDraftChange: (filters: TenantTableFilters) => void;
  onApply: () => void;
  onClear: () => void;
  onClose: () => void;
}

export default function TenantFiltersPanel({
  isOpen,
  draftFilters,
  companyCodeOptions,
  companyNameOptions,
  domainOptions,
  statusOptions,
  onDraftChange,
  onApply,
  onClear,
  onClose,
}: TenantFiltersPanelProps) {
  return (
    <SidePanel
      isOpen={isOpen}
      title="Filters"
      widthClassName="w-[378px]"
      onClose={onClose}
    >
      <TenantFiltersSection
        filters={draftFilters}
        companyCodeOptions={companyCodeOptions}
        companyNameOptions={companyNameOptions}
        domainOptions={domainOptions}
        statusOptions={statusOptions}
        onChange={onDraftChange}
        onApply={onApply}
        onClear={onClear}
      />
    </SidePanel>
  );
}
