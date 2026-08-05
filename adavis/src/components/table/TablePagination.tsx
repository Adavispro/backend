"use client";

interface TablePaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange?: (page: number) => void;
  maxVisiblePages?: number;
}

export default function TablePagination({
  currentPage,
  totalPages,
  onPageChange,
  maxVisiblePages = 4,
}: TablePaginationProps) {
  const safeTotalPages = Math.max(totalPages, 1);
  const safeCurrentPage = Math.min(Math.max(currentPage, 1), safeTotalPages);

  const getVisiblePages = () => {
    if (safeTotalPages <= maxVisiblePages) {
      return Array.from({ length: safeTotalPages }, (_, index) => index + 1);
    }

    const half = Math.floor(maxVisiblePages / 2);
    let start = safeCurrentPage - half;
    let end = start + maxVisiblePages - 1;

    if (start < 1) {
      start = 1;
      end = maxVisiblePages;
    }

    if (end > safeTotalPages) {
      end = safeTotalPages;
      start = end - maxVisiblePages + 1;
    }

    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  };

  const visiblePages = getVisiblePages();
  const showLeadingEllipsis = visiblePages[0] > 2;
  const showTrailingEllipsis = visiblePages[visiblePages.length - 1] < safeTotalPages - 1;

  return (
    <div className="flex items-center gap-1.5">
      <button
        type="button"
        aria-label="Previous page"
        disabled={safeCurrentPage <= 1}
        onClick={() => onPageChange?.(safeCurrentPage - 1)}
        className="type-pagination grid h-5 w-5 place-items-center rounded-md border border-[#D9E2EE] bg-white/55 text-text-tertiary disabled:cursor-not-allowed disabled:opacity-40"
      >
        ‹
      </button>

      {visiblePages[0] > 1 ? (
        <button
          type="button"
          aria-current={safeCurrentPage === 1 ? "page" : undefined}
          onClick={() => onPageChange?.(1)}
          className={`type-pagination grid h-5 w-5 place-items-center rounded-md ${
            safeCurrentPage === 1
              ? "bg-primary text-white shadow-[0_6px_14px_rgba(6,79,165,0.22)]"
              : "border border-[#D9E2EE] bg-white/55 text-text-secondary"
          }`}
        >
          1
        </button>
      ) : null}

      {showLeadingEllipsis ? (
        <span className="type-pagination grid h-5 w-5 place-items-center text-text-tertiary">
          ...
        </span>
      ) : null}

      {visiblePages.map((page) => (
          <button
            key={page}
            type="button"
            aria-current={page === safeCurrentPage ? "page" : undefined}
            onClick={() => onPageChange?.(page)}
            className={`type-pagination grid h-5 w-5 place-items-center rounded-md ${
              page === safeCurrentPage
                ? "bg-primary text-white shadow-[0_6px_14px_rgba(6,79,165,0.22)]"
                : "border border-[#D9E2EE] bg-white/55 text-text-secondary"
            }`}
          >
            {page}
          </button>
      ))}

      {showTrailingEllipsis ? (
        <span className="type-pagination grid h-5 w-5 place-items-center text-text-tertiary">
          ...
        </span>
      ) : null}

      {visiblePages[visiblePages.length - 1] < safeTotalPages ? (
        <button
          type="button"
          aria-current={safeCurrentPage === safeTotalPages ? "page" : undefined}
          onClick={() => onPageChange?.(safeTotalPages)}
          className={`type-pagination grid h-5 w-5 place-items-center rounded-md ${
            safeCurrentPage === safeTotalPages
              ? "bg-primary text-white shadow-[0_6px_14px_rgba(6,79,165,0.22)]"
              : "border border-[#D9E2EE] bg-white/55 text-text-secondary"
          }`}
        >
          {safeTotalPages}
        </button>
      ) : null}

      <button
        type="button"
        aria-label="Next page"
        disabled={safeCurrentPage >= safeTotalPages}
        onClick={() => onPageChange?.(safeCurrentPage + 1)}
        className="type-pagination grid h-5 w-5 place-items-center rounded-md border border-[#D9E2EE] bg-white/55 text-text-tertiary disabled:cursor-not-allowed disabled:opacity-40"
      >
        ›
      </button>
    </div>
  );
}
