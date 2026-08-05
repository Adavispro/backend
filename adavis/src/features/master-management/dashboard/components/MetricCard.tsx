import Image from "next/image";
import Link from "next/link";
import type { StaticImageData } from "next/image";

export type MetricCardVariant =
  | "blue"
  | "green"
  | "primary"
  | "purple"
  | "red"
  | "yellow";

export interface MetricCardProps {
  label: string;
  value: string;
  note: string;
  icon: StaticImageData;
  variant: MetricCardVariant;
  href?: string;
}

export default function MetricCard({
  label,
  value,
  note,
  icon,
  variant,
  href,
}: MetricCardProps) {
  const styles = {
    primary: "bg-primary text-white shadow-[0_12px_22px_rgba(7,92,175,0.24)]",
    green: "bg-[#E7F7EE] text-text-heading",
    yellow: "bg-[#FFF8DD] text-text-heading",
    red: "bg-[#FCEAEA] text-text-heading",
    blue: "bg-[#EAF2FF] text-text-heading",
    purple: "bg-[#F0EAFF] text-text-heading",
  }[variant];
  const noteClassName = variant === "red" ? "text-[#F04444]" : "text-success";
  const iconOpacity = variant === "primary" ? "opacity-45" : "opacity-70";
  const content = (
    <>
      <p className={`relative text-[10px] font-medium ${variant === "primary" ? "text-white" : "text-text-secondary"}`}>
        {label}
      </p>
      <strong className={`relative mt-3 block text-[26px] font-semibold leading-none ${variant === "primary" ? "text-white" : "text-text-heading"}`}>
        {value}
      </strong>
      <p className={`relative mt-2 text-[8px] font-semibold ${noteClassName}`}>
        {note}
      </p>
      <Image
        src={icon}
        alt=""
        aria-hidden="true"
        className={`absolute right-4 top-1/2 h-8 w-8 -translate-y-1/2 object-contain ${iconOpacity}`}
      />
    </>
  );
  const className = `relative min-h-[92px] overflow-hidden rounded-lg border border-white/65 p-3.5 text-left shadow-[0_10px_20px_rgba(35,50,70,0.12)] transition-transform hover:-translate-y-0.5 ${styles}`;

  if (href) {
    return (
      <Link href={href} className={className}>
        {content}
      </Link>
    );
  }

  return <article className={className}>{content}</article>;
}
