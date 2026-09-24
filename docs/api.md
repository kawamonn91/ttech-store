# API 仕様 (`/api/v1`)

ストアアプリと Web が共通で使う。レスポンスの型は `web/src/lib/dto.ts`(Kotlin 側は `store-app/.../data/api/Models.kt`)。
エラーは `{ "error": "利用者向けメッセージ" }` + HTTP ステータス。公開GETは CDN で60秒キャッシュされる。

| メソッド/パス | 内容 |
|---|---|
| `GET /home` | `{ featured, newest, popular }`(各10件) |
| `GET /categories` | `{ items: [{ slug, name }] }` |
| `GET /apps?q=&category=&sort=new\|popular\|rating&limit=&offset=` | `{ items: AppSummary[], total }` |
| `GET /apps/{slug}` | `AppDetail`(説明・スクショ・開発者・最新リリース) |
| `GET /index` | 更新チェック用の全件 `{ items: [{ slug, packageName, name, iconUrl, latest }] }`。端末側で versionCode と突き合わせる |
| `POST /releases/{id}/download` body `{ deviceId }` | `{ url, sha256, signingCertSha256, apkSize, versionCode, packageName, expiresAt }`。`url` は15分有効の署名付きURL。DL数を端末単位・24時間で重複除外して記録 |

## 内部・管理用(認証あり。ストアアプリからは使わない)

管理アプリ(`native-apps/ttech-admin`)向けの `/api/admin/*` と、報告のメール通知は [admin.md](admin.md) を参照。

| パス | 認証 | 内容 |
|---|---|---|
| `POST /api/admin/apps/{id}/releases` | 管理者セッション | リリース作成 + R2 アップロード用署名URL |
| `POST /api/admin/releases/{id}/uploaded` | 管理者セッション | アップロード確認 + APK検査(GitHub Actions)起動 |
| `POST /api/admin/releases/{id}/decision` | 管理者セッション | `{ action: publish\|reject\|unpublish }` |
| `POST /api/internal/releases/{id}/scan` | HMAC(`x-signature`) | 検査結果の受け取り。承認待ち/却下を決める |

## 検証の流れ(ストアアプリ)

`download` のレスポンスを受けてAPKを取得し、次をすべて満たさなければインストールしない(`ApkVerifier`)。

1. ファイルの SHA-256 == `sha256`
2. APK の packageName / versionCode == 期待値
3. APK の署名者が1つで、その証明書 SHA-256 == `signingCertSha256`
   (`signingCertSha256` は各アプリで最初に承認されたリリースの値にDBトリガーで固定される)
