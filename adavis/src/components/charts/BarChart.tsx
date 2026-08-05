import ChartTooltip from "./ChartTooltip";

interface BarChartItem {
  label: string;
  value: number;
  color: string;
  gradientTo?: string;
}

const DEFAULT_TICK_COUNT = 5;

function getNiceStep(maxValue: number, desiredTickCount: number) {
  if (maxValue <= 0) return 1;

  const rawStep = maxValue / desiredTickCount;
  const magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const normalized = rawStep / magnitude;

  const niceNormalized =
    normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;

  return niceNormalized * magnitude;
}

function getChartTicks(maxValue: number, desiredTickCount = DEFAULT_TICK_COUNT) {
  const step = getNiceStep(maxValue, desiredTickCount);
  const ceiling = Math.max(step, Math.ceil(maxValue / step) * step);

  return Array.from({ length: Math.ceil(ceiling / step) }, (_, index) =>
    ceiling - index * step,
  ).filter((tick) => tick > 0);
}

export interface BarChartProps {
  items: BarChartItem[];
  maxValue?: number;
  ticks?: number[];
  height?: number;
  tooltipValueFormatter?: (value: number, item: BarChartItem) => string;
}

export default function BarChart({
  items,
  maxValue,
  ticks,
  height = 170,
  tooltipValueFormatter,
}: BarChartProps) {
  const highestItemValue = Math.max(...items.map((item) => item.value), 0);
  const chartMaxValue = Math.max(maxValue ?? 0, highestItemValue, 1);
  const chartTicks = ticks ?? getChartTicks(chartMaxValue);
  const labelGridTemplateColumns = `repeat(${Math.max(items.length, 1)}, minmax(0, 1fr))`;

  return (
    <div className="w-full">
      <div
        className="grid w-full grid-cols-[30px_1fr] grid-rows-[1fr_auto]"
        style={{ height }}
      >
        <div className="relative row-start-1">
          {chartTicks.map((tick) => (
            <span
              key={tick}
              className="type-chart-axis absolute right-2 -translate-y-1/2"
              style={{ top: `${100 - (tick / chartMaxValue) * 100}%` }}
            >
              {tick}
            </span>
          ))}
        </div>

        <div className="relative row-start-1 border-b border-l-2 border-[#D6DFEA]">
          {chartTicks.map((tick) => (
            <span
              key={tick}
              className="absolute left-0 h-px w-full bg-[#DDE6F0]"
              style={{ top: `${100 - (tick / chartMaxValue) * 100}%` }}
            />
          ))}

          <div className="absolute inset-x-0 bottom-0 z-10 flex h-full items-end justify-around px-5">
            {items.map((item, index) => (
              <div
                key={`${item.label}-${index}`}
                tabIndex={0}
                aria-label={`${item.label}: ${
                  tooltipValueFormatter
                    ? tooltipValueFormatter(item.value, item)
                    : item.value
                }`}
                className="group relative flex h-full items-end outline-none"
              >
                <div
                  className="w-5 rounded-t ring-primary/25 transition-transform group-hover:scale-x-110 group-focus-visible:ring-4"
                  style={{
                    height: `${Math.min(item.value / chartMaxValue, 1) * 100}%`,
                    background: item.gradientTo
                      ? `linear-gradient(180deg, ${item.gradientTo} 0%, ${item.color} 100%)`
                      : item.color,
                  }}
                />
                <span className="sr-only">
                  {item.label}: {item.value}
                </span>
                <ChartTooltip>
                  <span className="block text-text-secondary">
                    {item.label}
                  </span>
                  <span className="block text-primary">
                    {tooltipValueFormatter
                      ? tooltipValueFormatter(item.value, item)
                      : item.value}
                  </span>
                </ChartTooltip>
              </div>
            ))}
          </div>
        </div>

        <div
          className="col-start-2 row-start-2 grid px-5 pt-2"
          style={{ gridTemplateColumns: labelGridTemplateColumns }}
        >
          {items.map((item, index) => (
            <span
              key={`${item.label}-${index}`}
              className="type-chart-axis text-center"
            >
              {item.label}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
