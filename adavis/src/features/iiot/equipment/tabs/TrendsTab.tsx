import LineChart from "@/components/charts/LineChart";
import CompactEventsCard, {
  type CompactEventItem,
} from "../../../../components/ui/CompactEventsCard";
import DetailFilterField from "../components/DetailFilterField";
import { trendPoints } from "../data/data";

export interface TrendInsightItem {
  color: string;
  title: string;
  detail: string;
}

const outOfRangeEvents: CompactEventItem[] = [
  { label: "Outlet Air Temperature", status: "Warning", value: "10:00 AM" },
  { label: "Bed Differential Pressure", status: "Critical", value: "10:00 AM" },
  { label: "Outlet Air Temperature", status: "Warning", value: "10:00 AM" },
  { label: "Outlet Air Temperature", status: "Critical", value: "10:00 AM" },
];

function TrendsFilterSection({
  selectedParameter,
  parameterOptions,
  onParameterChange,
}: {
  selectedParameter: string;
  parameterOptions: string[];
  onParameterChange: (value: string) => void;
}) {
  return (
    <section className="module-glass-panel rounded-lg px-6 py-4">
      <div className="grid items-end gap-5 md:grid-cols-[220px_1fr]">
        <DetailFilterField
          label="Parameter Selected"
          value={selectedParameter}
          options={parameterOptions}
          onChange={onParameterChange}
        />
        <span aria-hidden="true" />
      </div>
    </section>
  );
}

function TrendInsights({ insights }: { insights: TrendInsightItem[] }) {
  return (
    <article className="module-glass-panel overflow-hidden rounded-lg">
      <h2 className="type-table-title px-4 py-4">Trend Insights</h2>
      <div className="divide-y divide-[#E2E9F2]">
        {insights.map((insight) => (
          <div
            key={insight.title}
            className="grid grid-cols-[auto_1fr] gap-3 px-4 py-2.5"
          >
            <span
              className="mt-1.5 h-2 w-2 rounded-full"
              style={{ backgroundColor: insight.color }}
            />
            <div>
              <p className="type-table-head-compact normal-case tracking-normal">
                {insight.title} 
              </p>
              <p className="type-table-footer mt-1 normal-case">
                {insight.detail}
              </p>
            </div>
          </div>
        ))}
      </div>
    </article>
  );
}

export default function TrendsTab({
  points = trendPoints,
  events = outOfRangeEvents,
  insights = [],
  selectedParameter = "",
  selectedParameterKey = "",
  parameterOptions = [],
  onParameterChange = () => undefined,
}: {
  points?: typeof trendPoints;
  events?: CompactEventItem[];
  insights?: TrendInsightItem[];
  selectedParameter?: string;
  selectedParameterKey?: string;
  parameterOptions?: string[];
  onParameterChange?: (value: string) => void;
}) {
  void selectedParameterKey;

  const values = points.map((point) => point.value);
  const minValue = values.length ? Math.floor(Math.min(...values)) : 0;
  const maxValue = values.length ? Math.ceil(Math.max(...values)) : 10;
  const valueRange = Math.max(1, maxValue - minValue);
  const step = Math.max(1, Math.ceil(valueRange / 4));
  const chartTicks = Array.from({ length: 5 }, (_, index) => minValue + step * (4 - index));

  return (
    <div className="grid gap-4">
      <TrendsFilterSection
        selectedParameter={selectedParameter}
        parameterOptions={parameterOptions}
        onParameterChange={onParameterChange}
      />
      <div className="grid gap-4 lg:grid-cols-[1.35fr_1fr]">
        <article className="module-glass-panel rounded-lg px-6 pb-3 pt-6">
          <LineChart
            points={points}
            minValue={minValue}
            maxValue={maxValue}
            ticks={chartTicks}
            height={410}
            chartWidth={420}
            chartHeight={165}
            lineColor="#E39A05"
            fillColor="rgba(227,154,5,0.18)"
            fillColorTo="rgba(227,154,5,0.02)"
            labelInterval={4}
            tickFormatter={(value) => String(value)}
            markerSize="h-1.5 w-1.5"
            lineStrokeWidth="1.25"
          />
        </article>
        <div className="grid gap-4">
          <TrendInsights insights={insights} />
          <CompactEventsCard
            title="Out of Range Events"
            items={events}
          />
        </div>
      </div>
    </div>
  );
}
