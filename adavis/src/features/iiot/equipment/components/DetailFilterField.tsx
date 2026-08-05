import { CaretDown } from "@phosphor-icons/react/dist/ssr";

interface DetailFilterFieldProps {
  label: string;
  value: string;
  options: string[];
  onChange?: (value: string) => void;
  required?: boolean;
}

export default function DetailFilterField({
  label,
  value,
  options,
  onChange,
  required = true,
}: DetailFilterFieldProps) {
  return (
    <label className="grid gap-1.5">
      <span className="type-filter-label">
        {label}
        {required ? <span className="text-required">*</span> : null}
      </span>
      <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
        <select
          value={value}
          onChange={(event) => onChange?.(event.target.value)}
          className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 outline-none"
        >
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <CaretDown
          aria-hidden="true"
          size={12}
          weight="bold"
          className="pointer-events-none absolute right-3 text-text-secondary"
        />
      </span>
    </label>
  );
}
