import { redirect } from "next/navigation";
import { ROUTES } from "@/config/routes";

export default function Page() {
  redirect(ROUTES.login);
}
