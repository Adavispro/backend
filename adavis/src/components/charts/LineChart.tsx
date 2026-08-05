import { useId } from "react";
import ChartTooltip from "./ChartTooltip";

interface LineChartPoint {
  label: string;
  value: number;
}

export interface LineChartProps {
  points: LineChartPoint[];
  minValue?: number;
  maxValue?: number;
  ticks?: number[];
  height?: number;
  lineColor?: string;
  fillColor?: string;
  fillColorTo?: string;
  labelInterval?: number;
  tickFormatter?: (value: number) => string;
  chartWidth?: number;
  chartHeight?: number;
  markerSize?: string;
  lineStrokeWidth?: string;
  tooltipValueFormatter?: (value: number, point: LineChartPoint) => string;
}

function getPointPosition(
  index: number,
  value: number,
  totalPoints: number,
  minValue: number,
  maxValue: number,
  width: number,
  height: number,
  paddingX: number,
) {
  const drawableWidth = Math.max(width - paddingX * 2, 0);
  const x =
    totalPoints <= 1
      ? width / 2
      : paddingX + (index / (totalPoints - 1)) * drawableWidth;
  const valueRange = maxValue - minValue;
  const y =
    valueRange <= 0 ? height : height - ((value - minValue) / valueRange) * height;

  return { x, y };
}

export default function LineChart({
  points,
  minValue = 0,
  maxValue = 25,
  ticks = [25, 20, 15, 10, 5],
  height = 170,
  lineColor = "#8175FF",
  fillColor = "rgba(129, 117, 255, 0.18)",
  fillColorTo = "rgba(129, 117, 255, 0.04)",
  labelInterval = 2,
  tickFormatter = (value) => String(value),
  chartWidth = 320,
  chartHeight = 100,
  markerSize = "h-1.5 w-1.5",
  lineStrokeWidth = "1.1",
  tooltipValueFormatter,
}: LineChartProps) {
  const gradientId = useId();
  const horizontalPadding = 12;
  const positions = points.map((point, index) =>
    getPointPosition(
      index,
      point.value,
      points.length,
      minValue,
      maxValue,
      chartWidth,
      chartHeight,
      horizontalPadding,
    ),
  );
  const linePoints = positions
    .map((position) => `${position.x},${position.y}`)
    .join(" ");
  const areaPoints = `${horizontalPadding},${chartHeight} ${linePoints} ${chartWidth - horizontalPadding},${chartHeight}`;

  return (
    <div className="w-full">
      <div
        className="grid w-full grid-cols-[30px_1fr] grid-rows-[minmax(0,1fr)_20px]"
        style={{ height }}
      >
        <div className="relative row-start-1">
          {ticks.map((tick) => (
            <span
              key={tick}
              className="type-chart-axis absolute right-2 -translate-y-1/2"
              style={{
                top: `${
                  100 - ((tick - minValue) / (maxValue - minValue)) * 100
                }%`,
              }}
            >
              {tickFormatter(tick)}
            </span>
          ))}
        </div>

        <div className="relative row-start-1 border-b border-l border-[#D6DFEA]">
          {ticks.map((tick) => (
            <span
              key={tick}
              className="absolute left-0 h-px w-full bg-[#DDE6F0]"
              style={{
                top: `${
                  100 - ((tick - minValue) / (maxValue - minValue)) * 100
                }%`,
              }}
            />
          ))}

          <svg
            aria-hidden="true"
            viewBox={`0 0 ${chartWidth} ${chartHeight}`}
            preserveAspectRatio="none"
            className="absolute inset-0 z-10 h-full w-full overflow-visible"
          >
            <defs>
              <linearGradient
                id={gradientId}
                x1="0"
                y1="0"
                x2="0"
                y2="1"
              >
                <stop offset="0%" stopColor={fillColorTo} />
                <stop offset="100%" stopColor={fillColor} />
              </linearGradient>
            </defs>
            <polygon points={areaPoints} fill={`url(#${gradientId})`} />
            <polyline
              points={linePoints}
              fill="none"
              stroke={lineColor}
              strokeWidth={lineStrokeWidth}
              vectorEffect="non-scaling-stroke"
            />
          </svg>
          <div aria-label="Performance trend line chart" className="absolute inset-0 z-20">
            {positions.map((position, index) => (
              <span
                key={`${points[index].label}-${index}`}
                tabIndex={0}
                aria-label={`${points[index].label}: ${
                  tooltipValueFormatter
                    ? tooltipValueFormatter(points[index].value, points[index])
                    : tickFormatter(points[index].value)
                }`}
                className={`group absolute ${markerSize} -translate-x-1/2 -translate-y-1/2 rounded-full border bg-[#F4F7FF] outline-none ring-primary/25 focus-visible:ring-4`}
                style={{
                  borderColor: lineColor,
                  left: `${(position.x / chartWidth) * 100}%`,
                  top: `${(position.y / chartHeight) * 100}%`,
                }}
              >
                <ChartTooltip>
                  <span className="block text-text-secondary">
                    {points[index].label}
                  </span>
                  <span className="block text-primary">
                    {tooltipValueFormatter
                      ? tooltipValueFormatter(points[index].value, points[index])
                      : tickFormatter(points[index].value)}
                  </span>
                </ChartTooltip>
              </span>
            ))}
          </div>
        </div>

        <div className="relative col-start-2 row-start-2 h-5 pt-1.5">
          {points.map((point, index) => (
            index % labelInterval === 0 ? (
              <span
                key={`${point.label}-${index}`}
                className="type-chart-axis absolute -translate-x-1/2 whitespace-nowrap"
                style={{
                  left: `${
                    points.length <= 1 ? 0 : (index / (points.length - 1)) * 100
                  }%`,
                }}
              >
                {point.label}
              </span>
            ) : null
          ))}
        </div>
      </div>
    </div>
  );
}
