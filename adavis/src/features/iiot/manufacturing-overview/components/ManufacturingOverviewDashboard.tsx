import {
  Buildings,
  CalendarCheck,
  Drop,
  Gauge,
  GraduationCap,
  Lightning,
  Package,
  ShieldCheck,
  ShieldWarning,
} from "@phosphor-icons/react/dist/ssr";
import factoryIcon from "@/assets/iiot/factory.svg";
import DoughnutChart from "@/components/charts/DoughnutChart";
import LineChart from "@/components/charts/LineChart";
import { PrimaryMetricCard } from "@/components/ui";
import {
  plantScheduleAdherence,
  productionTrend,
} from "../data/manufacturing-overview-data";
import OverviewMetricCard from "./OverviewMetricCard";
import OverviewPanel from "./OverviewPanel";
import PlantPerformanceTable from "./PlantPerformanceTable";
import ProgressList from "./ProgressList";

const primaryMetrics = [
  {
    label: "Total Sites",
    value: "18",
    note: "All sites reporting",
    icon: Buildings,
    variant: "blue" as const,
  },
  {
    label: "Total Production (MT)",
    value: "12,405",
    note: "↑ 8.4% from last month",
    icon: Gauge,
    variant: "green" as const,
  },
  {
    label: "Schedule Adherence",
    value: "82.1%",
    note: "↑ 2.3% from last month",
    icon: CalendarCheck,
    variant: "yellow" as const,
  },
  {
    label: "Quality Success Rate",
    value: "94.5%",
    note: "↑ 1.2% from last month",
    icon: ShieldCheck,
    variant: "green" as const,
  },
];

const operationalMetrics = [
  {
    label: "Inventory Coverage",
    value: "34 Days",
    note: "Balanced supply",
    icon: Package,
    variant: "blue" as const,
  },
  {
    label: "Energy Consumption",
    value: "4.8 Cr kW",
    note: "↓ 3.4% from last year",
    icon: Lightning,
    variant: "yellow" as const,
  },
  {
    label: "Water Consumption",
    value: "1.24 L Litres",
    note: "↓ 2.8% from last year",
    icon: Drop,
    variant: "blue" as const,
  },
  {
    label: "Safety Incidents (YTD)",
    value: "12",
    note: "↓ 20% from last year",
    icon: ShieldWarning,
    variant: "green" as const,
  },
  {
    label: "Training Compliance",
    value: "96.3%",
    note: "↑ 4.1% from last year",
    icon: GraduationCap,
    variant: "purple" as const,
  },
];

export default function ManufacturingOverviewDashboard() {
  return (
    <section aria-label="Manufacturing Overview" className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[1.25fr_repeat(4,1fr)]">
        <PrimaryMetricCard
          label="Manufacturing Health Index"
          value="78.6%"
          note="↑ 5.8% from last month"
          icon={factoryIcon}
        />
        {primaryMetrics.map((metric) => (
          <OverviewMetricCard key={metric.label} {...metric} />
        ))}
      </div>

      <div className="grid gap-4 lg:grid-cols-[0.9fr_2.1fr]">
        <OverviewPanel title="Capacity Utilization">
          <div className="mt-5 flex justify-center">
            <DoughnutChart
              segments={[
                {
                  label: "Used Capacity",
                  value: 72,
                  color: "#2FB1A6",
                  gradientTo: "#79D0C7",
                },
                {
                  label: "Available Capacity",
                  value: 28,
                  color: "#D9E3EC",
                },
              ]}
              centerValue="72%"
              centerLabel="Utilized"
              size={112}
              strokeWidth={15}
              legendLabelWidth={92}
            />
          </div>
        </OverviewPanel>

        <OverviewPanel
          title="Monthly Production Trend (MT)"
          action={
            <span className="rounded border border-[#D9E3EE] bg-white/55 px-3 py-1 text-[9px] font-medium text-text-secondary">
              Monthly
            </span>
          }
        >
          <div className="mt-5">
            <LineChart
              points={productionTrend}
              minValue={40}
              maxValue={90}
              ticks={[90, 80, 70, 60, 50]}
              height={170}
              lineColor="#FF7C6B"
              fillColor="rgba(255,124,107,0.22)"
              fillColorTo="rgba(255,124,107,0.03)"
              labelInterval={1}
              tooltipValueFormatter={(value) => `${value} MT`}
            />
          </div>
        </OverviewPanel>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.9fr_0.9fr]">
        <OverviewPanel title="Top 5 Plants by Schedule Adherence">
          <ProgressList items={plantScheduleAdherence} />
        </OverviewPanel>

        <OverviewPanel title="Compliance and CAPA Overview">
          <div className="mt-5 flex justify-center">
            <DoughnutChart
              segments={[
                { label: "On track", value: 82, color: "#2FB1A6" },
                { label: "Completed", value: 18, color: "#D8C66A" },
              ]}
              centerValue="82"
              centerLabel="Score"
              size={104}
              strokeWidth={14}
              legendValueSuffix="%"
              legendLabelWidth={62}
            />
          </div>
        </OverviewPanel>

        <OverviewPanel title="Batch Status">
          <div className="mt-5 flex justify-center">
            <DoughnutChart
              segments={[
                { label: "Completed", value: 72, color: "#2FB1A6" },
                { label: "In progress", value: 18, color: "#3F7ED4" },
                { label: "Delayed", value: 7, color: "#F0C65A" },
                { label: "Cancelled", value: 3, color: "#EF6A70" },
              ]}
              centerValue="245"
              centerLabel="Batches"
              size={104}
              strokeWidth={14}
              legendLabelWidth={64}
            />
          </div>
        </OverviewPanel>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {operationalMetrics.map((metric) => (
          <OverviewMetricCard key={metric.label} {...metric} />
        ))}
      </div>

      <PlantPerformanceTable />
    </section>
  );
}
