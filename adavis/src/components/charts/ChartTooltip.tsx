interface ChartTooltipProps {
  children: React.ReactNode;
  className?: string;
}

export default function ChartTooltip({
  children,
  className = "",
}: ChartTooltipProps) {
  return (
    <span
      className={`pointer-events-none absolute bottom-full left-1/2 z-40 mb-2 min-w-max -translate-x-1/2 rounded-[6px] border border-white/70 bg-white/95 px-2.5 py-1.5 text-[10px] font-semibold leading-tight text-text-heading opacity-0 shadow-[0_10px_22px_rgba(20,45,75,0.16)] backdrop-blur-md transition-opacity duration-150 group-hover:opacity-100 group-focus-visible:opacity-100 ${className}`}
      role="tooltip"
    >
      {children}
      <span className="absolute left-1/2 top-full h-2 w-2 -translate-x-1/2 -translate-y-1/2 rotate-45 border-b border-r border-white/70 bg-white/95" />
    </span>
  );
}
