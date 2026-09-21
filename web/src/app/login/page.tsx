"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

type Mode = "signin" | "signup";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next") ?? "/account";

  const [mode, setMode] = useState<Mode>("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    const supabase = browserClient();

    if (mode === "signin") {
      const { error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) {
        setError("メールアドレスまたはパスワードが正しくありません");
        setBusy(false);
        return;
      }
      router.push(next);
      router.refresh();
      return;
    }

    // signup
    if (password.length < 8) {
      setError("パスワードは8文字以上にしてください");
      setBusy(false);
      return;
    }
    const { error } = await supabase.auth.signUp({
      email,
      password,
      options: { emailRedirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(next)}` },
    });
    if (error) {
      setError(error.message.includes("already registered") ? "このメールアドレスは既に登録されています" : "登録に失敗しました");
      setBusy(false);
      return;
    }
    setNotice("確認メールを送信しました。メール内のリンクを開いて登録を完了してください。");
    setBusy(false);
  }

  async function withGoogle() {
    setError(null);
    const supabase = browserClient();
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(next)}` },
    });
    if (error) setError("Googleログインを開始できませんでした");
  }

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="mb-6 text-xl font-bold">{mode === "signin" ? "ログイン" : "新規登録"}</h1>

      <button
        onClick={withGoogle}
        className="mb-4 flex w-full items-center justify-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 font-semibold"
      >
        <GoogleIcon />
        Googleで{mode === "signin" ? "ログイン" : "登録"}
      </button>

      <div className="mb-4 flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-border" />
        または
        <span className="h-px flex-1 bg-border" />
      </div>

      <form onSubmit={submit} className="space-y-3">
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
          autoComplete={mode === "signin" ? "current-password" : "new-password"}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="パスワード"
          className="w-full rounded-xl border border-border bg-surface px-4 py-2"
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        {notice && <p className="text-sm text-brand">{notice}</p>}
        <button
          disabled={busy}
          className="w-full rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60"
        >
          {busy ? "処理中…" : mode === "signin" ? "ログイン" : "登録する"}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted">
        {mode === "signin" ? (
          <>
            アカウントをお持ちでない場合は{" "}
            <button className="text-brand underline" onClick={() => setMode("signup")}>
              新規登録
            </button>
          </>
        ) : (
          <>
            既にアカウントをお持ちの場合は{" "}
            <button className="text-brand underline" onClick={() => setMode("signin")}>
              ログイン
            </button>
          </>
        )}
      </p>
      <p className="mt-6 text-center text-xs text-muted">
        <Link href="/admin/login" className="underline">
          運営者はこちら
        </Link>
      </p>
    </div>
  );
}

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden>
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.95v2.33A9 9 0 0 0 9 18z" />
      <path fill="#FBBC05" d="M3.97 10.72A5.4 5.4 0 0 1 3.68 9c0-.6.1-1.18.29-1.72V4.95H.95A9 9 0 0 0 0 9c0 1.45.35 2.83.95 4.05z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .95 4.95L3.97 7.28C4.68 5.16 6.66 3.58 9 3.58z" />
    </svg>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}
