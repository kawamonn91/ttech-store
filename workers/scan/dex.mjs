// `dexdump -d`(DEXを逆アセンブルしたテキスト)を1行ずつ読み、審査に必要な事実を集める。
//   - どのOSのAPI(patterns.mjs)を、どのクラスのどのメソッドが呼んでいるか
//   - 定数文字列に含まれるURLのホスト名、注意したい文字列
//   - native メソッド(JNI)の有無
//
// 出力は大きくなりうる(数十MB)ので、全体をメモリに持たず、ストリームで処理する。

import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { API_PATTERNS, BENIGN_URL_HOSTS, STRING_PATTERNS, URL_RE } from "./patterns.mjs";

const CLASS_LINE = /^\s+Class descriptor\s+:\s+'(L[^']*;)'/;
const METHOD_HEADER = /^\s+#\d+\s+:\s+\(in (L[^;]*;)\)/;
const NAME_LINE = /^\s+name\s+:\s+'(.*)'$/;
const TYPE_LINE = /^\s+type\s+:\s+'(.*)'$/;
const ACCESS_LINE = /^\s+access\s+:\s+0x[0-9a-f]+ \((.*)\)$/;
const CODE_LINE = /\|[0-9a-f]{4}: (\S+)(.*)$/;
const CALLER_LINE = /\|\[[0-9a-f]+\] (\S+)\.(\S+?):\(/;
const INVOKE_REF = /, (\[*L[^;\s]+;|\[+[BCDFIJSZ])\.([^:\s]+):/;
const FIELD_REF = /, (\[*L[^;\s]+;)\.([^:\s]+):/;
const TYPE_REF = /(L[^;\s]+;)\s*(?:\/\/ type@[0-9a-f]+)?\s*$/;
const CONST_STRING = /^const-string(?:\/jumbo)? \S+, "(.*)"(?: \/\/ string@[0-9a-f]+)?\s*$/;

/** 何度も出てくる、ほぼ無関係なクラスは、パターンの照合を飛ばす(速度のため) */
const SKIP_PREFIX = ["Ljava/lang/", "Ljava/util/", "Ljava/io/", "Landroidx/", "Lkotlin/", "Lkotlinx/", "Landroid/os/Bundle", "Landroid/view/", "Landroid/widget/", "Landroid/graphics/", "Landroid/content/Context", "Landroid/content/res/", "Landroid/util/", "Landroid/text/", "Landroid/animation/", "Landroid/app/Activity", "Landroid/content/Intent"];
const SKIP_EXCEPT = /^Ljava\/lang\/(ProcessBuilder|Process|ProcessHandle|Runtime|System)(\$|;)/;

const MAX_EXAMPLES = 3;
const MAX_HOSTS = 40;

function isBenignHost(host) {
  const h = host.toLowerCase().replace(/:\d+$/, "");
  return BENIGN_URL_HOSTS.some((b) => h === b || h.endsWith(`.${b}`));
}

/** dexdump の出力(行の並び)から事実を集める。lines は同期・非同期どちらの iterable でもよい */
export async function collectDexFacts(lines) {
  const hits = new Map(); // id -> { count, examples: string[], note }
  const stringHits = new Map();
  const hosts = new Map(); // host -> { count, example }
  const nativeMethods = { count: 0, examples: [] };
  const stats = { classes: 0, methods: 0 };

  let curClass = "?";
  let curMethod = "?";
  let pendingHeader = false; // メソッド(またはフィールド)の見出しの直後
  let pendingName = "";
  let pendingIsMethod = false;

  const record = (map, id, note, example) => {
    let h = map.get(id);
    if (!h) map.set(id, (h = { count: 0, examples: [], note }));
    h.count++;
    if (h.examples.length < MAX_EXAMPLES && !h.examples.includes(example)) h.examples.push(example);
  };

  const checkRef = (cls, member, kind) => {
    if (!cls) return;
    if (SKIP_PREFIX.some((p) => cls.startsWith(p)) && !SKIP_EXCEPT.test(cls)) return;
    for (const p of API_PATTERNS) {
      if (!p.cls.test(cls)) continue;
      if (p.member && !(member && p.member.test(member))) continue;
      record(hits, p.id, p.note, `${curClass}.${curMethod} → ${cls}${member ? `.${member}` : ""} (${kind})`);
    }
  };

  const checkString = (value) => {
    for (const p of STRING_PATTERNS) {
      if (p.re.test(value)) record(stringHits, p.id, p.note, `${curClass}.${curMethod}: ${value.slice(0, 80)}`);
    }
    if (value.length < 8 || !/:\/\//.test(value)) return;
    for (const m of value.matchAll(URL_RE)) {
      const host = m[1];
      if (isBenignHost(host)) continue;
      const cur = hosts.get(host);
      if (cur) cur.count++;
      else if (hosts.size < MAX_HOSTS) hosts.set(host, { count: 1, example: `${curClass}.${curMethod}` });
    }
  };

  for await (const line of lines) {
    if (line.charCodeAt(0) === 67 /* C */ && line.startsWith("Class #")) {
      stats.classes++;
      continue;
    }
    let m;
    if (line.includes("Class descriptor") && (m = line.match(CLASS_LINE))) {
      curClass = m[1];
      continue;
    }
    if (line.includes("(in L") && (m = line.match(METHOD_HEADER))) {
      pendingHeader = true;
      pendingName = "";
      pendingIsMethod = false;
      continue;
    }
    if (pendingHeader) {
      if ((m = line.match(NAME_LINE))) {
        pendingName = m[1];
        continue;
      }
      if ((m = line.match(TYPE_LINE))) {
        pendingIsMethod = m[1].startsWith("(");
        if (pendingIsMethod) stats.methods++;
        continue;
      }
      if ((m = line.match(ACCESS_LINE))) {
        if (pendingIsMethod && / NATIVE\b/.test(` ${m[1]}`)) {
          nativeMethods.count++;
          if (nativeMethods.examples.length < MAX_EXAMPLES) nativeMethods.examples.push(`${curClass}.${pendingName}`);
        }
        pendingHeader = false;
        continue;
      }
    }
    if (!line.includes("|")) continue;

    // 呼び出し元のメソッド(コードの直前に出る「|[アドレス] クラス.メソッド:(引数)」の行)
    if (line.includes("|[") && (m = line.match(CALLER_LINE))) {
      curMethod = m[2];
      if (m[1] !== "?") curClass = `L${m[1].replace(/\./g, "/")};`;
      continue;
    }
    if (!(m = line.match(CODE_LINE))) continue;
    const op = m[1];
    const rest = m[2];
    if (op.startsWith("invoke-")) {
      const r = rest.match(INVOKE_REF);
      if (r) checkRef(r[1], r[2], "呼び出し");
    } else if (op === "new-instance" || op === "const-class" || op === "check-cast" || op === "instance-of") {
      const r = rest.match(TYPE_REF);
      if (r) checkRef(r[1], null, op === "const-class" ? "クラス参照" : op === "new-instance" ? "生成" : "型");
    } else if (op.startsWith("sget") || op.startsWith("sput") || op.startsWith("iget") || op.startsWith("iput")) {
      const r = rest.match(FIELD_REF);
      if (r) checkRef(r[1], r[2], "フィールド");
    } else if (op.startsWith("const-string")) {
      const s = `${op}${rest}`.match(CONST_STRING);
      if (s) checkString(s[1]);
    }
  }

  const toObject = (map) => Object.fromEntries([...map.entries()].map(([id, h]) => [id, { count: h.count, examples: h.examples, note: h.note }]));
  return {
    classes: stats.classes,
    methods: stats.methods,
    apiHits: toObject(hits),
    stringHits: toObject(stringHits),
    urlHosts: [...hosts.entries()].map(([host, v]) => ({ host, count: v.count, example: v.example })),
    nativeMethods,
  };
}

/** 複数のDEX(classes.dex, classes2.dex …)の結果を1つにまとめる */
export function mergeDexFacts(list) {
  const merged = { dexFiles: list.length, classes: 0, methods: 0, apiHits: {}, stringHits: {}, urlHosts: [], nativeMethods: { count: 0, examples: [] } };
  const mergeHits = (into, from) => {
    for (const [id, h] of Object.entries(from)) {
      const cur = (into[id] ??= { count: 0, examples: [], note: h.note });
      cur.count += h.count;
      for (const e of h.examples) if (cur.examples.length < MAX_EXAMPLES && !cur.examples.includes(e)) cur.examples.push(e);
    }
  };
  const hostMap = new Map();
  for (const f of list) {
    merged.classes += f.classes;
    merged.methods += f.methods;
    mergeHits(merged.apiHits, f.apiHits);
    mergeHits(merged.stringHits, f.stringHits);
    merged.nativeMethods.count += f.nativeMethods.count;
    for (const e of f.nativeMethods.examples) if (merged.nativeMethods.examples.length < MAX_EXAMPLES) merged.nativeMethods.examples.push(e);
    for (const h of f.urlHosts) {
      const cur = hostMap.get(h.host);
      if (cur) cur.count += h.count;
      else if (hostMap.size < MAX_HOSTS) hostMap.set(h.host, { ...h });
    }
  }
  merged.urlHosts = [...hostMap.values()];
  return merged;
}

/** dexdump を実行して、その出力を collectDexFacts に流す */
export async function runDexdump(dexdumpPath, dexFile, { timeoutMs = 5 * 60 * 1000 } = {}) {
  const child = spawn(dexdumpPath, ["-d", dexFile], { stdio: ["ignore", "pipe", "pipe"] });
  let stderr = "";
  child.stderr.on("data", (d) => {
    if (stderr.length < 2000) stderr += d;
  });
  const timer = setTimeout(() => child.kill(), timeoutMs);
  const exit = new Promise((resolve) => child.on("close", resolve));
  try {
    const facts = await collectDexFacts(createInterface({ input: child.stdout, crlfDelay: Infinity }));
    const code = await exit;
    if (code !== 0) throw new Error(`dexdump が失敗しました (exit ${code}): ${stderr.trim().slice(0, 200)}`);
    if (facts.classes === 0) throw new Error("DEXからクラスを読み取れませんでした");
    return facts;
  } finally {
    clearTimeout(timer);
    child.kill();
  }
}
