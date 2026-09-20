"use client";

import { useRouter } from "next/navigation";
import { browserClient } from "@/lib/supabase-browser";

export function LogoutButton() {
  const router = useRouter();
  return (
    <button
      className="underline"
      onClick={async () => {
        await browserClient().auth.signOut();
        router.push("/admin/login");
        router.refresh();
      }}
    >
      ログアウト
    </button>
  );
}
