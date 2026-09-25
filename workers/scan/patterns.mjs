// APKのコード(DEX)の中で「外部との通信」「端末のデータの破壊」「隠れたコードの実行」につながりうるAPIを探すための一覧。
//
// 難読化(R8)されたアプリでも、Androidが提供するクラス(java.net.* など)の名前は変えられないので、
// アプリ内のクラス名ではなく「OSのAPIへの参照」で見つける。各項目の id は Web 側の審査ルール
// (web/src/lib/policy.ts)が参照するので、名前を変えるときは両方を直すこと。
//
//   cls:    参照されたクラス(DEXの型記述子。例: Ljava/net/Socket;)に一致する正規表現
//   member: (任意) メソッド/フィールド名に一致する正規表現。指定したときは、クラスと両方に一致したときだけ当たる
//   note:   運営の画面に出す短い説明

export const API_PATTERNS = [
  // ---- 外部との通信 ----
  {
    id: "net.java-net",
    cls: /^Ljava\/net\/(Socket|ServerSocket|DatagramSocket|MulticastSocket|DatagramPacket|URL|URLConnection|HttpURLConnection|InetAddress|Inet4Address|Inet6Address|InetSocketAddress|NetworkInterface|Proxy|ProxySelector|CookieHandler|CookieManager|ResponseCache|SocketAddress|SocketImpl|SocketOptions|StandardSocketOptions|JarURLConnection)(\$[A-Za-z0-9_$]+)?;$/,
    note: "java.net の通信クラス(Socket / URL / HttpURLConnection など)",
  },
  {
    id: "net.nio-channels",
    cls: /^Ljava\/nio\/channels\/(SocketChannel|ServerSocketChannel|DatagramChannel|AsynchronousSocketChannel|AsynchronousServerSocketChannel|AsynchronousChannelGroup)(\$[A-Za-z0-9_$]+)?;$/,
    note: "java.nio.channels の通信チャネル",
  },
  { id: "net.javax-net", cls: /^Ljavax\/net\//, note: "javax.net(SSL/TLS ソケット)" },
  {
    id: "net.android-http",
    cls: /^Landroid\/net\/(http\/|wifi\/|nsd\/|rtp\/|sip\/|ssl\/|LocalSocket|LocalServerSocket|VpnService|Proxy|ProxyInfo|SSLCertificateSocketFactory|SSLSessionCache)/,
    note: "android.net の通信・Wi-Fi・VPN・ローカルソケット関連",
  },
  // MimeTypeMap / URLUtil は文字列を扱うだけの補助クラス(FileProvider などが使う)なので除く
  { id: "net.webview", cls: /^Landroid\/webkit\/(?!MimeTypeMap|URLUtil)/, note: "WebView / android.webkit(ウェブ表示・外部ページへの遷移)" },
  { id: "net.download-manager", cls: /^Landroid\/app\/DownloadManager(\$[A-Za-z0-9_$]+)?;$/, note: "DownloadManager(ファイルのダウンロード)" },
  {
    id: "net.bluetooth-nfc-usb",
    cls: /^Landroid\/(bluetooth\/|nfc\/|hardware\/usb\/|hardware\/ConsumerIrManager|net\/wifi\/p2p\/|net\/wifi\/aware\/|net\/wifi\/rtt\/)/,
    note: "Bluetooth / NFC / USB / 赤外線による近くの機器との通信",
  },
  { id: "net.sms-telephony", cls: /^Landroid\/telephony\/(SmsManager|SmsMessage|TelephonyManager)(\$[A-Za-z0-9_$]+)?;$/, note: "SMS・電話回線" },
  {
    id: "net.http-library",
    cls: /^L(okhttp3|okhttp|retrofit2|com\/android\/volley|io\/ktor|com\/loopj|org\/apache\/http|com\/squareup\/(okhttp|retrofit)|coil\/network|coil3\/network|com\/bumptech\/glide\/load\/model|com\/google\/firebase|com\/google\/android\/gms|com\/facebook|com\/appsflyer|com\/adjust|io\/sentry|com\/crashlytics|com\/onesignal|com\/unity3d\/ads|com\/applovin|com\/ironsource|com\/mbridge|com\/vungle|com\/inmobi|com\/chartboost)\//,
    note: "HTTP通信・広告・解析などのライブラリ(名前が残っている場合)",
  },

  // ---- 隠れたコードの実行・外部プログラムの起動 ----
  {
    id: "code.dex-loader",
    cls: /^Ldalvik\/system\/(DexClassLoader|PathClassLoader|InMemoryDexClassLoader|BaseDexClassLoader|DexFile|DelegateLastClassLoader)(\$[A-Za-z0-9_$]+)?;$/,
    note: "DEXの動的読み込み(後から別のコードを読み込める)",
  },
  { id: "code.exec", cls: /^Ljava\/lang\/(ProcessBuilder|Process|ProcessHandle)(\$[A-Za-z0-9_$]+)?;$/, note: "外部プロセスの起動(ProcessBuilder / Process)" },
  { id: "code.exec", cls: /^Ljava\/lang\/Runtime;$/, member: /^exec$/, note: "Runtime.exec(外部プロセスの起動)" },
  {
    id: "code.native-load",
    cls: /^Ljava\/lang\/(System|Runtime);$/,
    member: /^(load|loadLibrary|mapLibraryName)$/,
    note: "ネイティブライブラリの読み込み",
  },
  { id: "code.jni-bridge", cls: /^Landroid\/system\/(Os|ErrnoException|StructStat)/, note: "android.system.Os(低レベルのシステム呼び出し)" },
  { id: "code.internal-api", cls: /^Ljdk\/internal\//, note: "JDKの内部API" },

  // ---- 端末のデータ・設定・他のアプリへの操作 ----
  { id: "destroy.device-admin", cls: /^Landroid\/app\/admin\//, note: "端末管理者API(端末の初期化・ロックなどができる)" },
  { id: "destroy.recovery", cls: /^Landroid\/os\/(RecoverySystem|PowerManager)(\$[A-Za-z0-9_$]+)?;$/, member: /^(rebootWipeUserData|installPackage|reboot|rebootWipeCache|uncrypt)$/, note: "端末の初期化・再起動" },
  { id: "destroy.package-manager", cls: /^Landroid\/content\/pm\/(PackageInstaller|IPackageDeleteObserver)(\$[A-Za-z0-9_$]+)?;$/, note: "アプリのインストール・削除(PackageInstaller)" },
  {
    id: "destroy.package-manager",
    cls: /^Landroid\/content\/pm\/PackageManager;$/,
    member: /^(deletePackage|installPackage|setApplicationEnabledSetting|setComponentEnabledSetting|clearApplicationUserData|freeStorage)$/,
    note: "他のアプリの削除・無効化・データ消去",
  },
  {
    id: "destroy.settings-write",
    cls: /^Landroid\/provider\/Settings\$(System|Secure|Global);$/,
    member: /^put/,
    note: "端末の設定の書き換え",
  },
  {
    id: "destroy.saf-delete",
    cls: /^Landroid\/provider\/DocumentsContract;$/,
    member: /^(deleteDocument|renameDocument|moveDocument)$/,
    note: "フォルダ内のファイルの削除・移動(Storage Access Framework)",
  },
  {
    id: "destroy.media-delete",
    cls: /^Landroid\/provider\/MediaStore(\$[A-Za-z0-9_$]+)?;$/,
    member: /^(createDeleteRequest|createTrashRequest|createWriteRequest)$/,
    note: "他のアプリの写真・動画の削除要求(利用者に確認は出る)",
  },
  { id: "destroy.overlay", cls: /^Landroid\/view\/WindowManager\$LayoutParams;$/, member: /^TYPE_APPLICATION_OVERLAY$/, note: "他のアプリの上に重ねて表示" },
];

/**
 * 定数文字列(const-string)に含まれていたら注意したいもの。
 *   id: 審査ルールから参照する名前 / re: 一致する正規表現
 */
export const STRING_PATTERNS = [
  { id: "str.saf-tree", re: /android\.intent\.action\.OPEN_DOCUMENT_TREE/, note: "フォルダ全体へのアクセスを求める(その中のファイルを削除できる)" },
  { id: "str.manage-storage", re: /android\.settings\.MANAGE_(ALL_FILES_ACCESS_PERMISSION|APP_ALL_FILES_ACCESS_PERMISSION)/, note: "すべてのファイルへのアクセス設定を開く" },
  { id: "str.install-apk", re: /application\/vnd\.android\.package-archive/, note: "APKのインストール要求" },
  { id: "str.view-url", re: /^android\.intent\.action\.VIEW$/, note: "他のアプリ(ブラウザなど)を開く操作", info: true },
  { id: "str.send", re: /^android\.intent\.action\.(SEND|SEND_MULTIPLE|SENDTO)$/, note: "共有・送信の操作(利用者が送信先を選ぶ)", info: true },
];

/** URLとして扱う文字列(この形式に一致したホスト名を集める) */
export const URL_RE = /\b(?:https?|ftp|wss?):\/\/([A-Za-z0-9._~-]+(?::\d+)?)(?:[/?#][^\s"'<>\\]*)?/g;

/**
 * 通信先ではなく、ライブラリの名前空間・ライセンス・エラーメッセージの案内として、
 * 多くのアプリのコードに残っているだけのホスト(前方一致ではなく完全一致か、サブドメインまでを許す)
 */
export const BENIGN_URL_HOSTS = [
  "schemas.android.com",
  "www.w3.org",
  "xmlpull.org",
  "www.xmlpull.org",
  "www.apache.org",
  "apache.org",
  "ns.adobe.com",
  "developer.android.com",
  "issuetracker.google.com",
  "g.co",
  "kotlinlang.org",
  "kotl.in",
  "developer.mozilla.org",
  "en.wikipedia.org",
  "support.google.com",
  "source.android.com",
  "cs.android.com",
  "androidx.de",
  "xml.org",
  "youtrack.jetbrains.com",
  "goo.gle",
  "localhost",
];
