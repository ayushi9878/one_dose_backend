/**
 * Chart palette for CareFlow.
 *
 * Validated against the app's white chart surface with the data-viz six checks:
 * lightness band, chroma floor, adjacent CVD separation (worst ΔE 9.2 deutan),
 * normal-vision floor (worst ΔE 27.6) and contrast. Aqua sits below 3:1 on
 * white, so every chart using it also ships visible axis labels and a table
 * view — identity is never carried by colour alone.
 *
 * Slots are assigned in fixed order and never cycled.
 */
export const chartPalette = {
  series1: '#2a78d6',
  series2: '#eb6834',
  series3: '#1baf7a',
} as const;

/** Reserved state colours. Never reused as a series hue. */
export const statusPalette = {
  good: '#0ca30c',
  warning: '#fab219',
  serious: '#ec835a',
  critical: '#d03b3b',
} as const;

export const chartChrome = {
  grid: '#e1e0d9',
  axis: '#c3c2b7',
  muted: '#898781',
  surface: '#ffffff',
} as const;
