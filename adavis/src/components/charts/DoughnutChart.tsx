import type { CSSProperties } from "react";
import { useId } from "react";
import ChartTooltip from "./ChartTooltip";

interface DoughnutSegment {
  label: string;
  value: number;
  color: string;
  gradientTo?: string;
  displayValue?: string | number;
  legendOrder?: number;
}

export interface DoughnutChartProps {
  segments: DoughnutSegment[];
  centerValue: string;
  centerLabel: string;
  size?: number;
  strokeWidth?: number;
  gapDegrees?: number;
  legendValueSuffix?: string;
  legendLabelWidth?: number;
  centerValueClassName?: string;
  centerLabelClassName?: string;
  tooltipValueSuffix?: string;
}

function polarToCartesian(
  centerX: number,
  centerY: number,
  radius: number,
  angleInDegrees: number,
) {
  const angleInRadians = ((angleInDegrees - 90) * Math.PI) / 180;

  return {
    x: centerX + radius * Math.cos(angleInRadians),
    y: centerY + radius * Math.sin(angleInRadians),
  };
}

function describeArc(
  centerX: number,
  centerY: number,
  radius: number,
  startAngle: number,
  endAngle: number,
) {
  const start = polarToCartesian(centerX, centerY, radius, endAngle);
  const end = polarToCartesian(centerX, centerY, radius, startAngle);
  const largeArcFlag = endAngle - startAngle <= 180 ? "0" : "1";

  return [
    "M",
    start.x,
    start.y,
    "A",
    radius,
    radius,
    0,
    largeArcFlag,
    0,
    end.x,
    end.y,
  ].join(" ");
}

const toPercent = (value: number, total: number) =>
  `${((value / total) * 100).toFixed(4)}%`;

export default function DoughnutChart({
  segments,
  centerValue,
  centerLabel,
  size = 180,
  strokeWidth = 30,
  gapDegrees = 3,
  legendValueSuffix = "%",
  legendLabelWidth = 66,
  centerValueClassName = "type-chart-center-value",
  centerLabelClassName = "type-chart-center-label mt-0.5",
  tooltipValueSuffix,
}: DoughnutChartProps) {
  const gradientId = useId().replaceAll(":", "");
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  const center = size / 2;
  const radius = (size - strokeWidth) / 2;
  const legendSegments = [...segments].sort(
    (first, second) => (first.legendOrder ?? 0) - (second.legendOrder ?? 0),
  );
  const renderedSegments = segments.reduce<
    Array<DoughnutSegment & { startAngle: number; endAngle: number }>
  >((items, segment) => {
    const previousEndAngle =
      items.length > 0 ? items[items.length - 1].endAngle + gapDegrees / 2 : 0;
    const angle = total === 0 ? 0 : (segment.value / total) * 360;

    return [
      ...items,
      {
        ...segment,
        startAngle: previousEndAngle + gapDegrees / 2,
        endAngle: previousEndAngle + angle - gapDegrees / 2,
      },
    ];
  }, []);

  return (
    <div className="flex min-w-0 flex-wrap items-center justify-center gap-6 xl:flex-nowrap xl:gap-10">
      <div
        className="relative h-[var(--chart-size)] w-[var(--chart-size)] shrink-0"
        style={{ "--chart-size": `${size}px` } as CSSProperties}
      >
        <svg
          aria-hidden="true"
          viewBox={`0 0 ${size} ${size}`}
          className="h-full w-full"
        >
          <circle
            cx={center}
            cy={center}
            r={radius}
            fill="none"
            stroke="rgba(255,255,255,0.7)"
            strokeWidth={strokeWidth}
          />
          <defs>
            {renderedSegments.map((segment, index) =>
              segment.gradientTo ? (
                <linearGradient
                  key={segment.label}
                  id={`${gradientId}-doughnut-gradient-${index}`}
                  x1="0%"
                  y1="100%"
                  x2="100%"
                  y2="0%"
                >
                  <stop offset="0%" stopColor={segment.color} />
                  <stop offset="100%" stopColor={segment.gradientTo} />
                </linearGradient>
              ) : null,
            )}
          </defs>
          {renderedSegments.map((segment, index) => {
            if (segment.endAngle <= segment.startAngle) {
              return null;
            }

            return (
              <path
                key={segment.label}
                d={describeArc(
                  center,
                  center,
                  radius,
                  segment.startAngle,
                  segment.endAngle,
                )}
                fill="none"
                stroke={
                  segment.gradientTo
                    ? `url(#${gradientId}-doughnut-gradient-${index})`
                    : segment.color
                }
                strokeWidth={strokeWidth}
                strokeLinecap="butt"
              />
            );
          })}
        </svg>
        {renderedSegments.map((segment) => {
          if (segment.endAngle <= segment.startAngle) {
            return null;
          }

          const tooltipAngle = (segment.startAngle + segment.endAngle) / 2;
          const tooltipRadius = radius;
          const tooltipPosition = polarToCartesian(
            center,
            center,
            tooltipRadius,
            tooltipAngle,
          );

          return (
            <span
              key={`${segment.label}-tooltip`}
              tabIndex={0}
              aria-label={`${segment.label}: ${
                segment.displayValue ?? segment.value
              }${tooltipValueSuffix ?? legendValueSuffix}`}
              className="group absolute z-20 h-4 w-4 -translate-x-1/2 -translate-y-1/2 rounded-full outline-none ring-primary/25 focus-visible:ring-4"
              style={{
                left: toPercent(tooltipPosition.x, size),
                top: toPercent(tooltipPosition.y, size),
              }}
            >
              <span
                className="absolute inset-1 rounded-full opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100"
                style={{ backgroundColor: segment.color }}
              />
              <ChartTooltip>
                <span className="block text-text-secondary">
                  {segment.label}
                </span>
                <span className="block text-primary">
                  {segment.displayValue ?? segment.value}
                  {tooltipValueSuffix ?? legendValueSuffix}
                </span>
              </ChartTooltip>
            </span>
          );
        })}
        <div className="absolute inset-0 grid place-items-center text-center">
          <div>
            <strong className={`${centerValueClassName} block`}>
              {centerValue}
            </strong>
            <span className={`${centerLabelClassName} block`}>
              {centerLabel}
            </span>
          </div>
        </div>
      </div>

      <ul className="grid min-w-[98px] flex-none gap-2.5">
        {legendSegments.map((segment) => (
          <li
            key={segment.label}
            className="grid grid-cols-[auto_var(--legend-label-width)_auto] items-center gap-2"
            style={
              {
                "--legend-label-width": `${legendLabelWidth}px`,
              } as CSSProperties
            }
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: segment.color }}
            />
            <span className="type-chart-legend">
              {segment.label}
            </span>
            <span className="type-chart-legend-value">
              {segment.displayValue ?? segment.value}
              {legendValueSuffix}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
