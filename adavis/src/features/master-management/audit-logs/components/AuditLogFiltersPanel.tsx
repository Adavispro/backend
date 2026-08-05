"use client";

import { SidePanel } from "@/components/ui";
import AuditLogFiltersSection, {
  type AuditLogTableFilters,
} from "./AuditLogFiltersSection";

export type { AuditLogTableFilters } from "./AuditLogFiltersSection";

interface AuditLogFiltersPanelProps {
  draftFilters: AuditLogTableFilters;
  isOpen: boolean;
  moduleOptions: string[];
  onApply: () => void;
  onClear: () => void;
  onClose: () => void;
  onDraftChange: (filters: AuditLogTableFilters) => void;
}

export default function AuditLogFiltersPanel({
  draftFilters,
  isOpen,
  moduleOptions,
  onApply,
  onClear,
  onClose,
  onDraftChange,
}: AuditLogFiltersPanelProps) {
  return (
    <SidePanel
      isOpen={isOpen}
      title="Filters"
      widthClassName="w-[378px]"
      onClose={onClose}
    >
      <AuditLogFiltersSection
        filters={draftFilters}
        moduleOptions={moduleOptions}
        onApply={onApply}
        onChange={onDraftChange}
        onClear={onClear}
      />
    </SidePanel>
  );
}
