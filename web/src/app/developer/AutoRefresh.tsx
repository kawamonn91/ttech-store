"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

/** active の間だけ、一定間隔でページのデータを読み直す(検査の結果が出るのを待つため) */
export function AutoRefresh({ active, intervalMs = 8000 }: { active: boolean; intervalMs?: number }) {
  const router = useRouter();
  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => router.refresh(), intervalMs);
    return () => clearInterval(timer);
  }, [active, intervalMs, router]);
  return null;
}
