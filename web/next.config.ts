import type { NextConfig } from "next";

/**
 * Supabase を Web と同じオリジンで公開する構成(サーバーの中で Supabase を動かす)のときだけ、
 * /auth/v1・/rest/v1・/storage/v1 をその Supabase へ転送する。転送先は INTERNAL_SUPABASE_URL。
 * 未設定(クラウドの Supabase を直接使う構成)なら転送しない。転送先はビルド時に決まる。
 */
const supabaseProxyTarget = process.env.INTERNAL_SUPABASE_URL?.replace(/\/+$/, "");

const nextConfig: NextConfig = {
  poweredByHeader: false,
  async rewrites() {
    if (!supabaseProxyTarget) return [];
    return ["auth", "rest", "storage"].map((service) => ({
      source: `/${service}/v1/:path*`,
      destination: `${supabaseProxyTarget}/${service}/v1/:path*`,
    }));
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        ],
      },
    ];
  },
};

export default nextConfig;
