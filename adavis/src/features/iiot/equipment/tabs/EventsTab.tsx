import EventsTable from "../components/EventsTable";
import type { EventRow } from "../data/types";

export default function EventsTab({ rows }: { rows?: EventRow[] }) {
  return (
    <EventsTable rows={rows} />
  );
}
