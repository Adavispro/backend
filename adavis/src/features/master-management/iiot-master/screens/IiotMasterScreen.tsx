import type { IiotMasterSection } from "../api";
import IiotMasterWorkspace from "../components/IiotMasterWorkspace";

interface IiotMasterScreenProps {
  section: IiotMasterSection;
}

export default function IiotMasterScreen({ section }: IiotMasterScreenProps) {
  return <IiotMasterWorkspace section={section} />;
}
