/**
 * Minimal exact non-negative decimal used for scoring points and MAXSCORE.
 *
 * The reference implementation uses java.math.BigDecimal and emits values with
 * `stripTrailingZeros().toPlainString()`. This module reproduces that behavior
 * exactly (no binary floating point, no scientific notation, integer values
 * keep no fractional part) for the limited grammar produced by the scoring
 * parser: `[0-9]+(\.[0-9]+)?`.
 */
export interface Decimal {
  /** Unscaled non-negative integer value. */
  readonly unscaled: bigint;
  /** Number of fractional digits (>= 0). */
  readonly scale: number;
}

export function parseDecimal(raw: string): Decimal {
  if (!/^[0-9]+(?:\.[0-9]+)?$/.test(raw)) {
    throw new Error(`Invalid decimal value: ${raw}`);
  }
  const dotIndex = raw.indexOf(".");
  if (dotIndex === -1) {
    return { unscaled: BigInt(raw), scale: 0 };
  }
  const digits = raw.slice(0, dotIndex) + raw.slice(dotIndex + 1);
  const scale = raw.length - dotIndex - 1;
  return { unscaled: BigInt(digits), scale };
}

export const DECIMAL_ZERO: Decimal = { unscaled: 0n, scale: 0 };

export function addDecimals(a: Decimal, b: Decimal): Decimal {
  const scale = Math.max(a.scale, b.scale);
  const aUnscaled = a.unscaled * 10n ** BigInt(scale - a.scale);
  const bUnscaled = b.unscaled * 10n ** BigInt(scale - b.scale);
  return { unscaled: aUnscaled + bUnscaled, scale };
}

/**
 * Equivalent of `BigDecimal.stripTrailingZeros().toPlainString()` for
 * non-negative values: trailing fractional zeros are removed, integers are
 * rendered without a decimal point, and no scientific notation is used.
 */
export function formatDecimal(value: Decimal): string {
  let unscaled = value.unscaled;
  let scale = value.scale;
  while (scale > 0 && unscaled % 10n === 0n) {
    unscaled /= 10n;
    scale -= 1;
  }
  if (scale === 0) {
    return unscaled.toString();
  }
  const digits = unscaled.toString().padStart(scale + 1, "0");
  const integerPart = digits.slice(0, digits.length - scale);
  const fractionPart = digits.slice(digits.length - scale);
  return `${integerPart}.${fractionPart}`;
}
