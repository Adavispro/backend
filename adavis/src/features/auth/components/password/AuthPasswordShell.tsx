"use client";

import Image from "next/image";
import Link from "next/link";
import loginBg from "@/assets/auth/login-background.png";
import logoFull from "@/assets/logo/logo-full.svg";
import { ROUTES } from "@/config/routes";

interface AuthPasswordShellProps {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}

export default function AuthPasswordShell({
  title,
  subtitle,
  children,
}: AuthPasswordShellProps) {
  return (
    <main className="relative min-h-screen w-full overflow-hidden">
      <div className="absolute inset-0 -z-10">
        <Image
          src={loginBg}
          alt=""
          fill
          className="object-cover object-center"
          priority
          quality={90}
        />
        <div className="absolute inset-0 bg-white/30" />
        <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(255,255,255,0.88)_0%,rgba(255,255,255,0.70)_31%,rgba(255,255,255,0.22)_66%,rgba(255,255,255,0.08)_100%)]" />
      </div>

      <div className="mx-auto grid min-h-screen w-full max-w-[1600px] grid-cols-1 items-center gap-8 px-6 py-10 lg:grid-cols-[38%_62%] lg:items-start lg:content-center lg:px-[7vw] lg:py-[7vh]">
        <section className="hidden flex-col items-start lg:flex">
          <Image
            src={logoFull}
            alt="ADAVIS"
            width={205}
            height={90}
            className="mb-10 h-auto w-[clamp(160px,14vw,205px)]"
            priority
          />

          <h1
            className="text-[clamp(2.2rem,3.15vw,3.25rem)] font-medium leading-[1.12] tracking-[0.01em] text-[#3f424a]"
            style={{ fontFamily: "var(--font-alan-sans, var(--font-sans))" }}
          >
            Intelligence that
            <br />
            drives <span className="text-accent">excellence</span>
          </h1>

          <div className="my-5 h-[4px] w-[clamp(130px,12vw,175px)] rounded-full bg-[#075cbc]" />

          <p className="max-w-[470px] text-[clamp(0.8rem,1vw,1rem)] leading-[1.8] tracking-[0.01em] text-[#666b73]">
            AI powered insights and real time monitoring
            <br />
            for smarter pharmaceutical operations
          </p>
        </section>

        <section className="flex w-full justify-center lg:justify-end">
          <div className="login-card flex w-full max-w-[520px] flex-col rounded-[14px] px-7 py-8 sm:px-11 sm:py-10">
            <div className="mb-7 flex justify-center lg:hidden">
              <Image
                src={logoFull}
                alt="ADAVIS"
                width={170}
                height={72}
                priority
              />
            </div>

            <div className="mb-7">
              <h1 className="mb-2 text-[1.45rem] font-semibold tracking-[0.01em] text-[#0759b5]">
                {title}
              </h1>
              <p className="text-[0.78rem] leading-6 text-[#626870]">
                {subtitle}
              </p>
            </div>

            {children}

            <Link
              href={ROUTES.login}
              className="mt-6 text-center text-[0.68rem] font-semibold text-primary hover:underline"
            >
              Back to login
            </Link>
          </div>
        </section>
      </div>
    </main>
  );
}
