import type { SupabaseClient } from "@supabase/supabase-js";

/**
 * テスト用の最小のSupabaseクライアント。
 * どんなメソッドチェーンも受け付け、最後に await(または maybeSingle/single)した時点で、
 * `results["テーブル.最初の操作"]`(操作: select/insert/update/delete/upsert)を返す。
 * 呼ばれた内容は `calls` に残るので、「何のテーブルに何を書いたか」を検証できる。
 */
export interface FakeCall {
  table: string;
  op: string;
  /** 操作メソッド(insert/update など)に渡された引数 */
  payload: unknown;
  /** チェーンに現れたメソッド名と引数(例: ["eq", ["id", "x"]]) */
  chain: [string, unknown[]][];
}

export interface FakeResult {
  data?: unknown;
  error?: { message: string } | null;
  count?: number | null;
}

type ResultSpec = FakeResult | ((call: FakeCall) => FakeResult);

export interface FakeSupabaseOptions {
  results?: Record<string, ResultSpec>;
  rpc?: Record<string, ResultSpec>;
  authAdmin?: Partial<Record<"updateUserById" | "getUserById" | "deleteUser", (...args: unknown[]) => unknown>>;
  storageRemove?: (bucket: string, paths: string[]) => { error: { message: string } | null };
  storageUpload?: (bucket: string, path: string) => { error: { message: string } | null };
}

const OPS = ["select", "insert", "update", "delete", "upsert"];

export function fakeSupabase(options: FakeSupabaseOptions = {}) {
  const calls: FakeCall[] = [];
  const rpcCalls: { fn: string; args: unknown }[] = [];
  const storageCalls: { bucket: string; op: string; paths: string[]; body?: unknown; options?: unknown }[] = [];
  const authCalls: { fn: string; args: unknown[] }[] = [];

  function resolve(spec: ResultSpec | undefined, call: FakeCall, single: boolean): FakeResult {
    const result = (typeof spec === "function" ? spec(call) : spec) ?? { data: null, error: null };
    let data = result.data ?? null;
    if (single && Array.isArray(data)) data = data[0] ?? null;
    return { data, error: result.error ?? null, count: result.count ?? null };
  }

  const client = {
    from(table: string) {
      const call: FakeCall = { table, op: "", payload: undefined, chain: [] };
      let single = false;
      const builder: unknown = new Proxy(function () {}, {
        get(_target, prop: string) {
          if (prop === "then") {
            return (onFulfilled: (v: unknown) => unknown, onRejected: (e: unknown) => unknown) =>
              Promise.resolve(resolve(options.results?.[`${table}.${call.op || "select"}`], call, single)).then(onFulfilled, onRejected);
          }
          return (...args: unknown[]) => {
            if (!call.op && OPS.includes(prop)) {
              call.op = prop;
              call.payload = args[0];
              calls.push(call);
            } else if (!call.op && prop !== "select") {
              // select より前に別のメソッドが来ることは無いが、念のため記録だけ残す
            }
            if (prop === "maybeSingle" || prop === "single") single = true;
            call.chain.push([prop, args]);
            return builder;
          };
        },
      });
      return builder;
    },
    rpc(fn: string, args: unknown) {
      rpcCalls.push({ fn, args });
      const call: FakeCall = { table: `rpc:${fn}`, op: "rpc", payload: args, chain: [] };
      return Promise.resolve(resolve(options.rpc?.[fn], call, false));
    },
    auth: {
      admin: new Proxy(
        {},
        {
          get(_t, fn: string) {
            return async (...args: unknown[]) => {
              authCalls.push({ fn, args });
              const handler = options.authAdmin?.[fn as keyof typeof options.authAdmin];
              return handler ? handler(...args) : { data: { user: null }, error: null };
            };
          },
        },
      ),
    },
    storage: {
      from(bucket: string) {
        return {
          async remove(paths: string[]) {
            storageCalls.push({ bucket, op: "remove", paths });
            return options.storageRemove?.(bucket, paths) ?? { data: [], error: null };
          },
          async upload(path: string, body: unknown, opts?: unknown) {
            storageCalls.push({ bucket, op: "upload", paths: [path], body, options: opts });
            return options.storageUpload?.(bucket, path) ?? { data: { path }, error: null };
          },
        };
      },
    },
  };

  return {
    client: client as unknown as SupabaseClient,
    calls,
    rpcCalls,
    authCalls,
    storageCalls,
    /** 指定したテーブル・操作の呼び出し */
    callsTo: (table: string, op: string) => calls.filter((c) => c.table === table && c.op === op),
  };
}
