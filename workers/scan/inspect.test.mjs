// node --test workers/scan/inspect.test.mjs
import assert from "node:assert/strict";
import { test } from "node:test";
import { parseApksigner, parseBadging } from "./inspect.mjs";

test("apksigner の出力から検証結果と証明書ハッシュを取り出す", () => {
  const out = [
    "Verifies",
    "Verified using v1 scheme (JAR signing): false",
    "Verified using v2 scheme (APK Signature Scheme v2): true",
    "Number of signers: 1",
    "Signer #1 certificate DN: CN=T-tech",
    "Signer #1 certificate SHA-256 digest: " + "AB".repeat(32),
    "Signer #1 certificate SHA-1 digest: " + "cd".repeat(20),
  ].join("\n");
  const r = parseApksigner(out);
  assert.equal(r.verified, true);
  assert.deepEqual(r.certs, ["ab".repeat(32)]);
});

test("apksigner -v の実出力(V2 Signer: 形式・複数スキームで同じ証明書)を扱える", () => {
  const cert = "8eddbffad3dd49dd45fa1d17366be38f5b9098387ec1f41da00b754866369da8";
  const out = [
    "",
    "Verifies",
    "Verified using v2 scheme (APK Signature Scheme v2): true",
    "Number of signers: 1",
    "V1 Signer: certificate SHA-256 digest: " + cert,
    "V2 Signer: certificate DN: C=US, O=Android, CN=Android Debug",
    "V2 Signer: certificate SHA-256 digest: " + cert,
    "V2 Signer: public key SHA-256 digest: " + "40".repeat(32),
  ].join("\n");
  const r = parseApksigner(out);
  assert.equal(r.verified, true);
  assert.deepEqual(r.certs, [cert]); // 重複は1つにまとめる。public key の行は拾わない
});

test("署名が検証できない出力は verified=false", () => {
  assert.equal(parseApksigner("DOES NOT VERIFY\nERROR: JAR_SIG_NO_SIGNATURES").verified, false);
});

test("aapt2 badging から package・version・SDK・権限を取り出す", () => {
  const out = [
    "package: name='jp.yomumemo.app' versionCode='12' versionName='1.2.0' platformBuildVersionName='16'",
    "minSdkVersion:'26'",
    "targetSdkVersion:'36'",
    "uses-permission: name='android.permission.INTERNET'",
    "uses-permission: name='android.permission.CAMERA'",
    "uses-permission-sdk-23: name='android.permission.CAMERA'",
  ].join("\n");
  assert.deepEqual(parseBadging(out), {
    packageName: "jp.yomumemo.app",
    versionCode: 12,
    versionName: "1.2.0",
    minSdk: 26,
    targetSdk: 36,
    permissions: ["android.permission.INTERNET", "android.permission.CAMERA"],
  });
});

test("package 行が無ければ null", () => {
  assert.equal(parseBadging("nothing"), null);
});

test("署名の情報: 署名方式(v1/v2/v3)と、証明書の持ち主(デバッグ鍵かどうか)を取り出す", async () => {
  const { parseSignatureFacts } = await import("./inspect.mjs");
  const out = [
    "Verifies",
    "Verified using v1 scheme (JAR signing): false",
    "Verified using v2 scheme (APK Signature Scheme v2): true",
    "Verified using v3 scheme (APK Signature Scheme v3): true",
    "Verified using v3.1 scheme (APK Signature Scheme v3.1): false",
    "Number of signers: 1",
    "Signer #1 certificate DN: CN=T-tech Run Tracker, OU=T-tech, O=T-tech, C=JP",
  ].join("\n");
  assert.deepEqual(parseSignatureFacts(out), { v1: false, v2: true, v3: true, subject: "CN=T-tech Run Tracker, OU=T-tech, O=T-tech, C=JP" });

  const debug = parseSignatureFacts("Verifies\nVerified using v1 scheme (JAR signing): true\nV1 Signer: certificate DN: C=US, O=Android, CN=Android Debug\n");
  assert.equal(debug.v1, true);
  assert.equal(debug.v2, false);
  assert.match(debug.subject, /CN=Android Debug/);
});
