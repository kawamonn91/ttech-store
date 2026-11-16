// 「1 台」「30 秒」のように、数字と単位の間に半角スペースを入れて書いた所で行が分かれないよう、
// そのスペースを改行しないスペース（U+00A0）に替える（スペースは改行してよい位置なので CSS では止められない）。
// 対象は単独の数字（30・1.5・1,000 など）。IPv4 や /24 のように英字や記号に続く数字は替えない。
const NUMBER_THEN_JAPANESE = /(?<![\p{L}\d.,/:])(\d+(?:[.,]\d+)*) (?=[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}ー々])/gu;

export function keepNumbers(text: string): string {
  return text.replace(NUMBER_THEN_JAPANESE, "$1\u00a0");
}
