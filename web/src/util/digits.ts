/** Map Western digits to Arabic-Indic (٠–٩). */
export function toArabicIndic(n: number): string {
  return String(n).replace(/\d/g, (d) => '٠١٢٣٤٥٦٧٨٩'[Number(d)]!)
}
