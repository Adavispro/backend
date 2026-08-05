import type { IiotMasterSection } from "../api";
import IiotMasterCreateForm from "../components/IiotMasterCreateForm";

export default function CreateIiotMasterScreen({
  section,
}: {
  section: IiotMasterSection;
}) {
  return <IiotMasterCreateForm section={section} />;
}
