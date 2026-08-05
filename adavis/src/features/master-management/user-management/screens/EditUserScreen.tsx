import EditUserForm from "../components/EditUserForm";

export default function EditUserScreen({ userId }: { userId: string }) {
  return <EditUserForm userId={userId} />;
}
