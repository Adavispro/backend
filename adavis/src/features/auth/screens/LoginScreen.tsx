"use client";

import Image from "next/image";
import loginBg from "@/assets/auth/login-background.png";
import logoFull from "@/assets/logo/logo-full.svg";
import { Snackbar } from "@/components/ui";
import LoginRightPanel from "../components/LoginRightPanel";
import { useLoginFlow } from "../hooks/useLoginFlow";

export default function LoginScreen() {
  const loginFlow = useLoginFlow();

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

        <LoginRightPanel
          displayName={loginFlow.displayName}
          form={loginFlow.form}
          showPassword={loginFlow.showPassword}
          isLoading={loginFlow.isLoading}
          isVerifying={loginFlow.isVerifying}
          isIdentityVerified={loginFlow.isIdentityVerified}
          onFieldChange={loginFlow.handleChange}
          onIdentifierBlur={() => void loginFlow.verifyIdentity()}
          onSubmit={loginFlow.handleSubmit}
          onTogglePassword={loginFlow.togglePassword}
        />
      </div>

      <Snackbar
        open={Boolean(loginFlow.notification.message)}
        variant="error"
        title={loginFlow.notification.title}
        message={loginFlow.notification.message}
        onClose={loginFlow.closeNotification}
      />
    </main>
  );
}
