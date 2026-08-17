'use client';

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartChrome, chartPalette, statusPalette } from './palette';
import type { AdherenceBand } from '@/types/api';

interface Props {
  distribution: AdherenceBand[];
  lowThreshold: number;
}

/**
 * Patient counts by adherence band. Bands are ordered low→high, so the fill
 * encodes position on that ordered scale rather than identity: the two bands
 * below the operational threshold take the reserved warning/critical colours,
 * and each bar is directly labelled so the reading never depends on hue.
 */
export function AdherenceDistributionChart({ distribution, lowThreshold }: Props) {
  const bandFill = (label: string): string => {
    if (label.startsWith('Below 60')) return statusPalette.critical;
    if (label.startsWith('60')) return statusPalette.warning;
    if (label.startsWith('80')) return chartPalette.series1;
    return chartPalette.series3;
  };

  const total = distribution.reduce((sum, band) => sum + band.patientCount, 0);

  return (
    <div>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={distribution} margin={{ top: 20, right: 12, bottom: 4, left: -20 }}>
          <CartesianGrid stroke={chartChrome.grid} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fill: chartChrome.muted, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: chartChrome.axis }}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fill: chartChrome.muted, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={48}
          />
          <Tooltip
            cursor={{ fill: 'rgba(15, 28, 46, 0.04)' }}
            contentStyle={{
              borderRadius: 8,
              border: '1px solid #e4e8ed',
              boxShadow: '0 4px 12px rgba(15, 28, 46, 0.08)',
              fontSize: 12,
            }}
            formatter={(value) => {
              const count = Number(value);
              return [`${count} ${count === 1 ? 'patient' : 'patients'}`, 'Count'];
            }}
          />
          <Bar dataKey="patientCount" radius={[4, 4, 0, 0]} maxBarSize={64}>
            {distribution.map((band) => (
              <Cell key={band.label} fill={bandFill(band.label)} />
            ))}
            <LabelList
              dataKey="patientCount"
              position="top"
              fill={chartChrome.muted}
              fontSize={12}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>

      <p className="mt-2 text-xs text-ink-subtle">
        {total} {total === 1 ? 'patient' : 'patients'} with recorded doses. Bands below{' '}
        {lowThreshold}% are treated as an operational signal.
      </p>
    </div>
  );
}
