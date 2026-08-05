import Image from "next/image";
import Link from "next/link";
import type { StaticImageData } from "next/image";

export interface PrimaryMetricCardProps {
  label: string;
  value: string;
  icon: StaticImageData;
  note?: string;
  href?: string;
  ariaLabel?: string;
}

export default function PrimaryMetricCard({
  label,
  value,
  icon,
  note,
  href,
  ariaLabel,
}: PrimaryMetricCardProps) {
  const content = (
    <>
      <p className="type-dashboard-card-title !text-white">{label}</p>
      <strong
        className={`type-dashboard-metric block ${note ? "mt-3" : "mt-4"}`}
      >
        {value}
      </strong>
      {note ? (
        <p className="mt-2 text-[8px] font-semibold text-[#BFE7FF]">{note}</p>
      ) : null}
      <Image
        src={icon}
        alt=""
        aria-hidden="true"
        className="absolute bottom-0 right-0 h-[70px] w-[70px] opacity-22"
      />
    </>
  );
  const className =
    "relative min-h-[86px] overflow-hidden rounded-lg bg-primary p-3 text-white shadow-[0_14px_28px_rgba(6,79,165,0.28)] transition-transform hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2";

  if (href) {
    return (
      <Link href={href} aria-label={ariaLabel ?? label} className={className}>
        {content}
      </Link>
    );
  }

  return <article className={className}>{content}</article>;
}
