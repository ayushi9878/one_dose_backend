'use client';

import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartChrome, chartPalette, statusPalette } from './palette';
import type { AdherencePoint } from '@/types/api';

interface Props {
  history: AdherencePoint[];
  threshold?: number;
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/**
 * Adherence over time. A single series, so no legend box is needed — the
 * heading names it. The threshold line gives the reader the reference they
 * actually judge against.
 */
export function AdherenceTrendChart({ history, threshold = 80 }: Props) {
  const data = history.map((point) => ({
    date: point.date,
    adherence: point.adherencePercentage,
    taken: point.takenDoses,
    expected: point.expectedDoses,
  }));

  return (
    <div>
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: -16 }}>
          <CartesianGrid stroke={chartChrome.grid} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="date"
            tickFormatter={formatDate}
            tick={{ fill: chartChrome.muted, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: chartChrome.axis }}
          />
          <YAxis
            domain={[0, 100]}
            ticks={[0, 25, 50, 75, 100]}
            tickFormatter={(value: number) => `${value}%`}
            tick={{ fill: chartChrome.muted, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={52}
          />
          <ReferenceLine
            y={threshold}
            stroke={statusPalette.warning}
            strokeDasharray="4 4"
            label={{
              value: `${threshold}% threshold`,
              position: 'insideTopRight',
              fill: chartChrome.muted,
              fontSize: 11,
            }}
          />
          <Tooltip
            cursor={{ stroke: chartChrome.axis, strokeWidth: 1 }}
            contentStyle={{
              borderRadius: 8,
              border: '1px solid #e4e8ed',
              boxShadow: '0 4px 12px rgba(15, 28, 46, 0.08)',
              fontSize: 12,
            }}
            labelFormatter={(label) =>
              typeof label === 'string'
                ? new Date(label).toLocaleDateString(undefined, {
                    weekday: 'short',
                    month: 'short',
                    day: 'numeric',
                  })
                : ''
            }
            formatter={(value, _name, item) => {
              const payload = item?.payload as
                | { taken: number; expected: number }
                | undefined;
              const detail = payload
                ? ` (${payload.taken} of ${payload.expected} doses)`
                : '';
              return [`${String(value)}%${detail}`, 'Adherence'];
            }}
          />
          <Line
            type="monotone"
            dataKey="adherence"
            stroke={chartPalette.series1}
            strokeWidth={2}
            dot={{ r: 4, fill: chartPalette.series1, stroke: chartChrome.surface, strokeWidth: 2 }}
            activeDot={{ r: 6, stroke: chartChrome.surface, strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>

      {/* Table view keeps the data readable without relying on colour. */}
      <details className="mt-3">
        <summary className="cursor-pointer text-xs text-ink-muted hover:text-ink">
          View as table
        </summary>
        <table className="mt-2 w-full text-xs">
          <thead>
            <tr className="border-b border-border text-left text-ink-muted">
              <th className="py-1.5 font-medium">Date</th>
              <th className="py-1.5 font-medium">Taken</th>
              <th className="py-1.5 font-medium">Expected</th>
              <th className="py-1.5 text-right font-medium">Adherence</th>
            </tr>
          </thead>
          <tbody>
            {data.map((point) => (
              <tr key={point.date} className="border-b border-border/60">
                <td className="py-1.5 text-ink-muted">{formatDate(point.date)}</td>
                <td className="py-1.5 tabular">{point.taken}</td>
                <td className="py-1.5 tabular">{point.expected}</td>
                <td className="py-1.5 text-right tabular">{point.adherence}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
