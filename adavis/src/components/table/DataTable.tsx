import Link from "next/link";
import {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useMemo,
  useState,
  type ReactElement,
  type ReactNode,
} from "react";
import ActionLabelTooltip from "./ActionLabelTooltip";
import TablePagination from "./TablePagination";

export interface DataTableColumn<T> {
  key: string;
  header: string;
  render: (row: T, index: number) => ReactNode;
  className?: string;
  disableRowLink?: boolean;
}

export interface DataTableStatusStyle {
  label: string;
  className: string;
}

export interface DataTableProps<T> {
  title: string;
  columns: DataTableColumn<T>[];
  rows: T[];
  getRowKey: (row: T, index: number) => string;
  toolbar?: ReactNode;
  footerText?: string;
  currentPage?: number;
  totalPages?: number;
  emptyText?: string;
  onPageChange?: (page: number) => void;
  showHeaderDivider?: boolean;
  getRowHref?: (row: T, index: number) => string;
  tableClassName?: string;
  fillHeight?: boolean;
  className?: string;
  showPagination?: boolean;
  pageSize?: number;
}

export function StatusPill({ label, className }: DataTableStatusStyle) {
  return (
    <span
      className={`type-status-pill inline-flex min-w-[58px] justify-center rounded-md px-2.5 py-1 ${className}`}
    >
      {label}
    </span>
  );
}

type ElementProps = {
  children?: ReactNode;
  title?: string;
  "aria-label"?: string;
  className?: string;
  href?: unknown;
};

function actionTooltipLabel(ariaLabel: string) {
  const label = ariaLabel.trim();
  const lowerLabel = label.toLowerCase();

  if (lowerLabel.includes("reactivate")) return "Reactivate";
  if (lowerLabel.includes("deactivate")) return "Deactivate";
  if (lowerLabel.includes("activate")) return "Activate";
  if (lowerLabel.includes("delete")) return "Delete";
  if (lowerLabel.includes("edit")) return "Edit";
  if (lowerLabel.includes("view")) return "View";
  if (lowerLabel.includes("unblock")) return "Unblock";
  if (lowerLabel.includes("block")) return "Block";
  if (lowerLabel.includes("manage license")) return "Manage license";
  if (lowerLabel.includes("download")) return "Download";
  if (lowerLabel.includes("filter")) return "Filter";
  if (lowerLabel.includes("sort")) return "Sort";

  return label;
}

function withDefaultActionTitles(node: ReactNode): ReactNode {
  return Children.map(node, (child) => {
    if (!isValidElement<ElementProps>(child)) return child;

    const children = child.props.children
      ? withDefaultActionTitles(child.props.children)
      : child.props.children;

    const tooltipLabel =
      typeof child.props["aria-label"] === "string"
        ? child.props["aria-label"].trim()
        : "";
    const isTooltipCandidate =
      tooltipLabel.length > 0 &&
      (child.type === "button" ||
        child.type === "a" ||
        child.props.href !== undefined);

    const props: ElementProps = {};
    if (children !== child.props.children) {
      props.children = children;
    }

    const element =
      Object.keys(props).length > 0
        ? cloneElement(child as ReactElement<ElementProps>, props)
        : child;

    if (!isTooltipCandidate) return element;

    return (
      <ActionLabelTooltip label={actionTooltipLabel(tooltipLabel)}>
        {element}
      </ActionLabelTooltip>
    );
  });
}

export default function DataTable<T>({
  title,
  columns,
  rows,
  getRowKey,
  toolbar,
  footerText,
  currentPage = 1,
  totalPages = 3,
  emptyText = "No records found.",
  onPageChange,
  showHeaderDivider = false,
  getRowHref,
  tableClassName = "",
  fillHeight = true,
  className = "",
  showPagination = true,
  pageSize = 10,
}: DataTableProps<T>) {
  const safePageSize = Math.max(1, pageSize);
  const computedTotalPages = Math.max(1, Math.ceil(rows.length / safePageSize));
  const isExternallyPaginated = typeof onPageChange === "function";
  const [internalPage, setInternalPage] = useState(currentPage);

  useEffect(() => {
    if (isExternallyPaginated) return;
    setInternalPage((previous) => Math.min(Math.max(previous, 1), computedTotalPages));
  }, [computedTotalPages, isExternallyPaginated]);

  const effectiveCurrentPage = isExternallyPaginated
    ? Math.min(Math.max(currentPage, 1), Math.max(totalPages, 1))
    : Math.min(Math.max(internalPage, 1), computedTotalPages);
  const effectiveTotalPages = isExternallyPaginated
    ? Math.max(totalPages, 1)
    : computedTotalPages;

  const pageStartIndex = showPagination
    ? (effectiveCurrentPage - 1) * safePageSize
    : 0;
  const pageEndIndexExclusive = showPagination
    ? pageStartIndex + safePageSize
    : rows.length;

  const visibleRows = useMemo(
    () => rows.slice(pageStartIndex, pageEndIndexExclusive),
    [rows, pageStartIndex, pageEndIndexExclusive],
  );

  const handlePageChange = (page: number) => {
    const nextPage = Math.min(Math.max(page, 1), effectiveTotalPages);
    if (isExternallyPaginated) {
      onPageChange?.(nextPage);
      return;
    }
    setInternalPage(nextPage);
  };

  const computedFooterText = `Showing ${
    rows.length === 0 ? 0 : pageStartIndex + 1
  } to ${Math.min(pageEndIndexExclusive, rows.length)} of ${rows.length} entries`;
  const resolvedFooterText = isExternallyPaginated
    ? (footerText ?? computedFooterText)
    : computedFooterText;

  return (
    <section
      className={`module-glass-panel flex flex-col rounded-xl p-4 shadow-[0_14px_26px_rgba(35,50,70,0.12)] ${
        fillHeight ? "min-h-[420px] flex-1" : ""
      } ${className}`}
    >
      <div className="flex shrink-0 items-center justify-between gap-4">
        <h2 className="type-table-title">{title}</h2>
        {toolbar}
      </div>

      {showHeaderDivider ? (
        <div className="-mx-4 my-4 h-px shrink-0 bg-[#E6E6E6]/60" />
      ) : (
        <div className="mb-4 shrink-0" />
      )}

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-white/75 bg-white/42 shadow-[0_8px_18px_rgba(35,50,70,0.08)] backdrop-blur-md">
        <div className="min-h-0 flex-1 overflow-auto">
          <table
            className={`w-full border-collapse text-left ${tableClassName}`}
          >
            <thead className="sticky top-0 z-10 bg-[#E7F0FA] text-text-heading">
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.key}
                    className={`type-table-head-compact px-5 py-2.5 ${column.className ?? ""}`}
                  >
                    {column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E2E9F2] bg-white/38">
              {visibleRows.length === 0 ? (
                <tr>
                  <td
                    colSpan={columns.length}
                    className="type-table-compact px-5 py-8 text-center text-text-secondary"
                  >
                    {emptyText}
                  </td>
                </tr>
              ) : null}
              {visibleRows.map((row, index) => {
                const absoluteIndex = pageStartIndex + index;
                const rowHref = getRowHref?.(row, absoluteIndex);

                return (
                  <tr
                    key={getRowKey(row, absoluteIndex)}
                    className={`text-text-body ${rowHref ? "transition-colors hover:bg-primary/[0.035]" : ""}`}
                  >
                    {columns.map((column) => {
                      const content = withDefaultActionTitles(
                        column.render(row, absoluteIndex),
                      );

                      return (
                        <td
                          key={column.key}
                          className={`type-table-compact px-5 py-2.5 ${column.className ?? ""}`}
                        >
                          {rowHref && !column.disableRowLink ? (
                            <Link
                              href={rowHref}
                              className="-mx-5 -my-2.5 block px-5 py-2.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
                            >
                              {content}
                            </Link>
                          ) : (
                            content
                          )}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <div className="flex shrink-0 items-center justify-between border-t border-[#E2E9F2]/80 px-4 py-3">
          <span className="type-table-footer">
            {resolvedFooterText}
          </span>

          {showPagination ? (
            <TablePagination
              currentPage={effectiveCurrentPage}
              totalPages={effectiveTotalPages}
              onPageChange={handlePageChange}
            />
          ) : null}
        </div>
      </div>
    </section>
  );
}
