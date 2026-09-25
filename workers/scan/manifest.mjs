// `aapt2 dump xmltree --file AndroidManifest.xml <apk>` の出力(テキスト)を読み取って、
// 審査に必要な情報(権限・コンポーネント・アプリの属性)を取り出す。
//
// 出力の形式(例):
//   N: android=http://schemas.android.com/apk/res/android (line=2)
//     E: manifest (line=2)
//       A: package="com.example" (Raw: "com.example")
//       A: http://schemas.android.com/apk/res/android:versionCode(0x0101021b)=1
//         E: uses-permission (line=12)
//           A: http://schemas.android.com/apk/res/android:name(0x01010003)="android.permission.INTERNET" (Raw: "...")
// 要素(E)の入れ子はインデントの深さで表される。属性(A)は直前の要素のもの。

const ELEMENT = /^(\s*)E: (\S+) \(line=\d+\)/;
const ATTRIBUTE = /^\s*A: (.+)$/;
const ATTR_NAME = /^([^=]*?)(?:\(0x[0-9a-fA-F]+\))?=(.*)$/;

function parseValue(raw) {
  const s = raw.trim();
  if (s.startsWith('"')) {
    const m = s.match(/^"((?:[^"\\]|\\.)*)"/);
    return m ? m[1] : s;
  }
  if (s === "true") return true;
  if (s === "false") return false;
  // 例: (type 0x12)0xffffffff(真偽値の一種)は、そのまま数値として返す
  const typed = s.match(/^\(type 0x[0-9a-fA-F]+\)(0x[0-9a-fA-F]+)$/);
  const num = typed ? typed[1] : s;
  if (/^-?\d+$/.test(num)) return Number(num);
  if (/^0x[0-9a-fA-F]+$/.test(num)) return parseInt(num, 16);
  return s;
}

/** テキストを、{ tag, attrs, children } の木にする(名前空間は除いた属性名で持つ) */
export function parseXmlTree(text) {
  const root = { tag: "#root", attrs: {}, children: [], indent: -1 };
  const stack = [root];
  for (const line of text.split(/\r?\n/)) {
    const e = line.match(ELEMENT);
    if (e) {
      const indent = e[1].length;
      while (stack.length > 1 && stack[stack.length - 1].indent >= indent) stack.pop();
      const node = { tag: e[2], attrs: {}, children: [], indent };
      stack[stack.length - 1].children.push(node);
      stack.push(node);
      continue;
    }
    const a = line.match(ATTRIBUTE);
    if (!a || stack.length < 2) continue;
    const m = a[1].match(ATTR_NAME);
    if (!m) continue;
    const local = m[1].includes(":") ? m[1].slice(m[1].lastIndexOf(":") + 1) : m[1];
    stack[stack.length - 1].attrs[local.trim()] = parseValue(m[2]);
  }
  return root.children.find((n) => n.tag === "manifest") ?? null;
}

const COMPONENT_TAGS = new Set(["activity", "activity-alias", "service", "receiver", "provider"]);

function findAll(node, tag) {
  return node.children.filter((c) => c.tag === tag);
}

/** manifest の木から、審査に使う情報を取り出す */
export function extractManifestFacts(manifest) {
  if (!manifest) return null;
  const sdk = findAll(manifest, "uses-sdk")[0]?.attrs ?? {};
  const application = findAll(manifest, "application")[0] ?? { attrs: {}, children: [] };

  const usesPermissions = [];
  for (const tag of ["uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"]) {
    for (const p of findAll(manifest, tag)) {
      if (typeof p.attrs.name === "string") usesPermissions.push({ name: p.attrs.name, maxSdk: p.attrs.maxSdkVersion ?? null });
    }
  }
  const declaredPermissions = findAll(manifest, "permission").map((p) => ({
    name: String(p.attrs.name ?? ""),
    // protectionLevel の下位4ビット: 0=normal 1=dangerous 2=signature 3=signatureOrSystem
    protectionLevel: typeof p.attrs.protectionLevel === "number" ? p.attrs.protectionLevel & 0xf : 0,
  }));

  const components = [];
  for (const c of application.children.filter((n) => COMPONENT_TAGS.has(n.tag))) {
    const filters = findAll(c, "intent-filter");
    const actions = filters.flatMap((f) => findAll(f, "action").map((a) => String(a.attrs.name ?? "")));
    const categories = filters.flatMap((f) => findAll(f, "category").map((a) => String(a.attrs.name ?? "")));
    const hasIntentFilter = filters.length > 0;
    const explicit = typeof c.attrs.exported === "boolean" ? c.attrs.exported : c.attrs.exported === 0 ? false : c.attrs.exported === undefined ? null : true;
    components.push({
      kind: c.tag,
      name: String(c.attrs.name ?? ""),
      // exported を書いていないときの既定は「インテントフィルターがあれば公開」
      exported: explicit ?? hasIntentFilter,
      hasIntentFilter,
      actions,
      categories,
      permission: typeof c.attrs.permission === "string" ? c.attrs.permission : null,
      readPermission: typeof c.attrs.readPermission === "string" ? c.attrs.readPermission : null,
      writePermission: typeof c.attrs.writePermission === "string" ? c.attrs.writePermission : null,
      grantUriPermissions: c.attrs.grantUriPermissions === true,
      authorities: typeof c.attrs.authorities === "string" ? c.attrs.authorities : null,
    });
  }

  return {
    package: String(manifest.attrs.package ?? ""),
    versionCode: typeof manifest.attrs.versionCode === "number" ? manifest.attrs.versionCode : null,
    versionName: typeof manifest.attrs.versionName === "string" ? manifest.attrs.versionName : null,
    sharedUserId: typeof manifest.attrs.sharedUserId === "string" ? manifest.attrs.sharedUserId : null,
    minSdk: typeof sdk.minSdkVersion === "number" ? sdk.minSdkVersion : null,
    targetSdk: typeof sdk.targetSdkVersion === "number" ? sdk.targetSdkVersion : null,
    debuggable: application.attrs.debuggable === true,
    testOnly: application.attrs.testOnly === true,
    hasCode: application.attrs.hasCode !== false,
    usesPermissions,
    declaredPermissions,
    components,
    usesLibraries: findAll(application, "uses-library").map((l) => String(l.attrs.name ?? "")),
  };
}
