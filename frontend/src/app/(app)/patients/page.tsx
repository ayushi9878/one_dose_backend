'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { RiskBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { patientApi } from '@/services/careflow-api';
import type { RiskLevel } from '@/types/api';

const RISK_FILTERS: Array<{ value: RiskLevel | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'All' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
  { value: 'NONE', label: 'No signal' },
];

const PAGE_SIZE = 20;

export default function PatientsPage() {
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [riskLevel, setRiskLevel] = useState<RiskLevel | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  // Debounce so each keystroke does not fire a request.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const query = useMemo(
    () => ({
      search: search || undefined,
      riskLevel: riskLevel === 'ALL' ? undefined : riskLevel,
      page,
      size: PAGE_SIZE,
    }),
    [search, riskLevel, page],
  );

  const patients = useApiResource(
    () => patientApi.search(query),
    [query.search, query.riskLevel, query.page],
  );

  const hasFilters = search !== '' || riskLevel !== 'ALL';

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-display font-semibold text-ink">Patients</h1>
          <p className="mt-1 text-sm text-ink-muted">
            Your continuity care caseload.
          </p>
        </div>
        {patients.data && (
          <p className="text-sm text-ink-muted tabular">
            {patients.data.totalElements.toLocaleString()}{' '}
            {patients.data.totalElements === 1 ? 'patient' : 'patients'}
          </p>
        )}
      </header>

      <div className="card">
        <div className="flex flex-wrap items-center gap-3 border-b border-border p-4">
          <div className="relative min-w-[240px] flex-1">
            <svg
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-subtle"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="Search by name or record number"
              aria-label="Search patients"
              className="input pl-9"
            />
          </div>

          <div className="flex flex-wrap gap-1" role="group" aria-label="Filter by risk level">
            {RISK_FILTERS.map((filter) => (
              <button
                key={filter.value}
                type="button"
                onClick={() => {
                  setRiskLevel(filter.value);
                  setPage(0);
                }}
                aria-pressed={riskLevel === filter.value}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors
                            ${
                              riskLevel === filter.value
                                ? 'bg-brand-600 text-white'
                                : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
                            }`}
              >
                {filter.label}
              </button>
            ))}
          </div>
        </div>

        {patients.loading ? (
          <div className="p-5">
            <LoadingSkeleton rows={6} />
          </div>
        ) : patients.error ? (
          <ErrorState message={patients.error} onRetry={patients.reload} />
        ) : patients.data && patients.data.content.length > 0 ? (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="border-b border-border bg-surface-muted">
                  <tr>
                    <th scope="col" className="table-header">Patient</th>
                    <th scope="col" className="table-header">Record</th>
                    <th scope="col" className="table-header">Condition</th>
                    <th scope="col" className="table-header">Discharged</th>
                    <th scope="col" className="table-header">Care manager</th>
                    <th scope="col" className="table-header">Risk</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {patients.data.content.map((patient) => (
                    <tr key={patient.id} className="transition-colors hover:bg-surface-muted">
                      <td className="table-cell">
                        <Link
                          href={`/patients/${patient.id}`}
                          className="font-medium text-ink hover:text-brand-700"
                        >
                          {patient.fullName}
                        </Link>
                        <p className="text-xs text-ink-subtle">{patient.age} years</p>
                      </td>
                      <td className="table-cell font-mono text-xs text-ink-muted">
                        {patient.medicalRecordNumber}
                      </td>
                      <td className="table-cell text-ink-muted">
                        {patient.primaryCondition ?? '—'}
                      </td>
                      <td className="table-cell text-ink-muted tabular">
                        {patient.dischargeDate
                          ? new Date(patient.dischargeDate).toLocaleDateString(undefined, {
                              month: 'short',
                              day: 'numeric',
                              year: 'numeric',
                            })
                          : 'Not discharged'}
                      </td>
                      <td className="table-cell text-ink-muted">
                        {patient.careManagerName ?? 'Unassigned'}
                      </td>
                      <td className="table-cell">
                        <RiskBadge level={patient.currentRiskLevel} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {patients.data.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-border px-4 py-3">
                <p className="text-sm text-ink-muted">
                  Page {patients.data.page + 1} of {patients.data.totalPages}
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={patients.data.first}
                    className="btn-secondary"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={patients.data.last}
                    className="btn-secondary"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        ) : (
          <EmptyState
            title={hasFilters ? 'No patients match these filters' : 'No patients yet'}
            description={
              hasFilters
                ? 'Try a different search term or risk level.'
                : 'Patients you are assigned will appear here.'
            }
            action={
              hasFilters ? (
                <button
                  type="button"
                  onClick={() => {
                    setSearchInput('');
                    setRiskLevel('ALL');
                  }}
                  className="btn-secondary"
                >
                  Clear filters
                </button>
              ) : undefined
            }
          />
        )}
      </div>
    </div>
  );
}
