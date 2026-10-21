/** 改行・タブ以外の制御文字(利用者の入力に混ぜて、表示やメールを崩すのに使われうるもの) */
const CONTROL_CHAR = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/;
const CONTROL_CHARS = new RegExp(CONTROL_CHAR.source, "g");

export function hasControlChars(text: string): boolean {
  return CONTROL_CHAR.test(text);
}

export function stripControlChars(text: string): string {
  return text.replace(CONTROL_CHARS, "");
}
