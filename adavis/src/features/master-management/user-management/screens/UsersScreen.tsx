import UsersTable from "../components/UsersTable";
import type { UserStatusFilter } from "../components/UsersTable";

export default function UsersScreen({
  statusFilter,
}: {
  statusFilter?: UserStatusFilter;
}) {
  return <UsersTable statusFilter={statusFilter} />;
}
