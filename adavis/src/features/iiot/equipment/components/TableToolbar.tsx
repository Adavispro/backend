import { CaretDown, MagnifyingGlass } from "@phosphor-icons/react/dist/ssr";

interface TableToolbarProps {
  searchLabel: string;
  searchPlaceholder: string;
  searchWidthClassName?: string;
  actions?: string[];
}

export default function TableToolbar({
  searchLabel,
  searchPlaceholder,
  searchWidthClassName = "w-[270px]",
  actions = ["Filter"],
}: TableToolbarProps) {
  return (
    <div className="flex items-center gap-3">
      <label
        className={`module-glass-control hidden h-8 items-center gap-2 rounded-[4px] px-3 text-text-secondary md:flex ${searchWidthClassName}`}
      >
        <MagnifyingGlass size={14} />
        <span className="sr-only">{searchLabel}</span>
        <input
          type="search"
          placeholder={searchPlaceholder}
          className="type-filter-value min-w-0 flex-1 bg-transparent outline-none placeholder:text-text-secondary"
        />
      </label>

      {actions.map((label) => (
        <button
          key={label}
          type="button"
          className="module-glass-control type-filter-button flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-heading"
        >
          {label}
          <CaretDown size={12} weight="bold" />
        </button>
      ))}
    </div>
  );
}
