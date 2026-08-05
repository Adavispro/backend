import Link from "next/link";
import { ROUTES } from "@/config/routes";

export default function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-6">
      <section className="w-full max-w-lg rounded-2xl border border-line bg-white p-8 text-center shadow-sm">
        <p className="type-overline mb-3 text-primary">404</p>
        <h1 className="type-page-title mb-3">Page not found</h1>
        <p className="type-page-subtitle mb-8">
          The requested route does not exist in this application.
        </p>
        <Link
          href={ROUTES.modules}
          className="inline-flex rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
        >
          Go to modules
        </Link>
      </section>
    </main>
  );
}
