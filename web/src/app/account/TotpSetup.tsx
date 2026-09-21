"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

interface Factor {
  id: string;
  status: string;
}

type Stage = "idle" | "enrolling" | "confirming";

/**
 * 管理者アカウント向けの2段階認証(TOTP)設定。
 * 一度確認できたら、以降の管理コンソールへのアクセスにこのコードが必要になる。
 */
export function TotpSetup({ initialFactor }: { initialFactor: Factor | null }) {
  const router = useRouter();
  const [factor, setFactor] = useState(initialFactor);
  const [stage, setStage] = useState<Stage>("idle");
  const [qrSvg, setQrSvg] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [pendingFactorId, setPendingFactorId] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function startEnroll() {
    setBusy(true);
    setError(null);
    const supabase = browserClient();
    const { data, error } = await supabase.auth.mfa.enroll({ factorType: "totp" });
    setBusy(false);
    if (error || !data) {
      setError("設定を開始できませんでした");
      return;
    }
    setPendingFactorId(data.id);
    setQrSvg(data.totp.qr_code);
    setSecret(data.totp.secret);
    setStage("confirming");
  }

  async function confirmEnrollment(e: React.FormEvent) {
    e.preventDefault();
    if (!pendingFactorId) return;
    setBusy(true);
    setError(null);
    const supabase = browserClient();
    const { data: challenge, error: challengeError } = await supabase.auth.mfa.challenge({ factorId: pendingFactorId });
    if (challengeError || !challenge) {
      setBusy(false);
      setError("確認に失敗しました");
      return;
    }
    const { error } = await supabase.auth.mfa.verify({ factorId: pendingFactorId, challengeId: challenge.id, code });
    setBusy(false);
    if (error) {
      setError("コードが正しくありません");
      return;
    }
    setFactor({ id: pendingFactorId, status: "verified" });
    setStage("idle");
    router.refresh();
  }

  async function unenroll() {
    if (!factor) return;
    if (!window.confirm("2段階認証を無効にしますか?")) return;
    setBusy(true);
    const supabase = browserClient();
    const { error } = await supabase.auth.mfa.unenroll({ factorId: factor.id });
    setBusy(false);
    if (error) {
      setError("解除に失敗しました");
      return;
    }
    setFactor(null);
    router.refresh();
  }

  if (factor) {
    return (
      <div className="flex items-center justify-between rounded-xl border border-border bg-surface px-4 py-3">
        <span className="text-sm">2段階認証: 有効</span>
        <button onClick={unenroll} disabled={busy} className="text-sm text-danger underline disabled:opacity-60">
          無効にする
        </button>
      </div>
    );
  }

  if (stage === "confirming") {
    return (
      <form onSubmit={confirmEnrollment} className="space-y-3 rounded-xl border border-border bg-surface p-4">
        <p className="text-sm text-muted">認証アプリ(Google Authenticator 等)でQRコードを読み取り、表示された6桁のコードを入力してください。</p>
        {qrSvg && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={`data:image/svg+xml;utf8,${encodeURIComponent(qrSvg)}`} alt="QRコード" className="h-40 w-40" />
        )}
        {secret && <p className="break-all text-xs text-muted">読み取れない場合の手動入力用キー: {secret}</p>}
        <input
          inputMode="numeric"
          required
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          placeholder="123456"
          className="w-full rounded-xl border border-border bg-background px-4 py-2 text-center tracking-widest"
        />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex gap-2">
          <button disabled={busy} className="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground disabled:opacity-60">
            確認して有効化
          </button>
          <button type="button" onClick={() => setStage("idle")} className="rounded-xl border border-border px-4 py-2 text-sm">
            キャンセル
          </button>
        </div>
      </form>
    );
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-muted">
        管理者権限の行使には2段階認証が必要です。認証アプリ(Google Authenticator 等)を使って設定してください。
      </p>
      {error && <p className="text-sm text-danger">{error}</p>}
      <button onClick={startEnroll} disabled={busy} className="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground disabled:opacity-60">
        2段階認証を設定する
      </button>
    </div>
  );
}
