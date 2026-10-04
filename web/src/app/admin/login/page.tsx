"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

type Step = "loading" | "password" | "totp";

/** ログイン中かつ2段階認証(TOTP)が必要な状態かを確認する */
async function needsStepUp(): Promise<boolean> {
  const supabase = browserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return false;
  const { data: factors } = await supabase.auth.mfa.listFactors();
  const hasVerifiedTotp = (factors?.totp ?? []).some((f) => f.status === "verified");
  if (!hasVerifiedTotp) return false;
  const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  return aal?.currentLevel !== "aal2";
}

function AdminLoginForm() {
  const router = useRouter();
  const next = useSearchParams().get("next") || "/admin";
  const [step, setStep] = useState<Step>("loading");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 既にパスワード認証済みで、あとはコード入力だけでよい状態(ページ再読み込み等)ならその画面から始める
  useEffect(() => {
    needsStepUp().then((need) => setStep(need ? "totp" : "password"));
  }, []);

  async function submitPassword(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } = await browserClient().auth.signInWithPassword({ email, password });
    setBusy(false);
    if (error) {
      setError("メールアドレスまたはパスワードが正しくありません");
      return;
    }
    if (await needsStepUp()) {
      setStep("totp");
      return;
    }
    router.push(next);
    router.refresh();
  }

  async function submitCode(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const supabase = browserClient();
    const { data: factors } = await supabase.auth.mfa.listFactors();
    const factor = factors?.totp?.find((f) => f.status === "verified");
    if (!factor) {
      setError("認証アプリが設定されていません");
      setBusy(false);
      return;
    }
    const { error } = await supabase.auth.mfa.challengeAndVerify({ factorId: factor.id, code });
    setBusy(false);
    if (error) {
      setError("確認コードが正しくありません");
      return;
    }
    router.push(next);
    router.refresh();
  }

  if (step === "loading") return null;

  if (step === "totp") {
    return (
      <form onSubmit={submitCode} className="mx-auto max-w-sm space-y-4">
        <h1 className="text-xl font-bold">2段階認証</h1>
        <p className="text-sm text-muted">認証アプリに表示されている6桁のコードを入力してください。</p>
        <input
          inputMode="numeric"
          autoFocus
          required
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          placeholder="123456"
          className="w-full rounded-xl border border-border bg-surface px-4 py-2 text-center text-lg tracking-widest"
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <button disabled={busy} className="w-full rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60">
          {busy ? "確認中…" : "確認する"}
        </button>
      </form>
    );
  }

  return (
    <form onSubmit={submitPassword} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-xl font-bold">管理コンソール ログイン</h1>
      <input
        type="email"
        required
        autoComplete="username"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="メールアドレス"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      <input
        type="password"
        required
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="パスワード"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      {error && <p className="text-sm text-danger">{error}</p>}
      <button disabled={busy} className="w-full rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "ログイン中…" : "ログイン"}
      </button>
    </form>
  );
}

export default function AdminLoginPage() {
  return (
    <Suspense fallback={null}>
      <AdminLoginForm />
    </Suspense>
  );
}
