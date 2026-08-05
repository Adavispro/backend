import Image from "next/image";
import waitingState from "@/assets/states/waiting.png";

export default function WaitingStateCard() {
  return (
    <section className="grid min-h-[410px] place-items-center rounded-xl border border-dashed border-[#AAB4C1] bg-transparent px-6 py-10">
      <div className="grid justify-items-center text-center">
        <Image
          src={waitingState}
          alt=""
          aria-hidden="true"
          className="h-auto w-[300px]"
          priority
        />
        <h2 className="mt-8 text-[16px] font-semibold leading-none text-text-heading">
          Awaiting Selection
        </h2>
        <p className="mt-6 max-w-[560px] text-[11px] leading-relaxed text-text-secondary">
          Please select Plant, Block, Area and Room to view real-time equipment
          status for this sector.
        </p>
      </div>
    </section>
  );
}
