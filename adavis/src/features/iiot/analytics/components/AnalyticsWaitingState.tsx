import Image from "next/image";
import waitingState from "@/assets/states/waiting.png";

export default function AnalyticsWaitingState() {
  return (
    <section className="grid min-h-[310px] place-items-center rounded-xl border border-dashed border-[#AAB4C1] bg-transparent px-6 py-8">
      <div className="grid justify-items-center text-center">
        <Image src={waitingState} alt="" aria-hidden="true" className="h-auto w-[235px]" priority />
        <h2 className="mt-6 text-[14px] font-semibold leading-none text-text-heading">
          Awaiting Selection
        </h2>
        <p className="mt-4 max-w-[560px] text-[10px] leading-relaxed text-text-secondary">
          Please select Plant, Block, Area, Room, Equipment ID, and Date Range
          to view OEE Analytics for this sector.
        </p>
      </div>
    </section>
  );
}
