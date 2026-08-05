export default function ProgressList({
  items,
}: {
  items: Array<{ label: string; value: number }>;
}) {
  return (
    <div className="mt-5 grid gap-4">
      {items.map((item) => (
        <div
          key={item.label}
          className="grid grid-cols-[105px_1fr_36px] items-center gap-3"
        >
          <span className="type-table-compact font-medium text-text-heading">
            {item.label}
          </span>
          <span className="h-1.5 overflow-hidden rounded-full bg-[#DCE6F1]">
            <span
              className="block h-full rounded-full bg-[linear-gradient(90deg,#2FB1A6,#4DA4E8)]"
              style={{ width: `${item.value}%` }}
            />
          </span>
          <span className="type-chart-legend-value text-right">
            {item.value}%
          </span>
        </div>
      ))}
    </div>
  );
}
