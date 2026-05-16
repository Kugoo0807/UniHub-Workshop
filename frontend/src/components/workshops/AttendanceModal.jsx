import React, { useState, useEffect, useCallback } from 'react';
import adminWorkshopService from '../../services/adminWorkshopService';
import PaginationControl from '../common/PaginationControl';

/* ─── helpers ───────────────────────────────────────────────── */

const fmt = (dt) => {
    if (!dt) return '—';
    return new Date(dt).toLocaleString('vi-VN', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });
};

const PAGE_SIZE = 5;

/* ─── AttendanceModal ──────────────────────────────────────── */

const AttendanceModal = ({ workshop, onClose }) => {
    const [data, setData] = useState(null);   // PageResponse
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [page, setPage] = useState(0);
    const [search, setSearch] = useState('');

    const load = useCallback(async (p = 0) => {
        try {
            setLoading(true);
            setError('');
            const res = await adminWorkshopService.getAttendances(workshop.id, p, PAGE_SIZE);
            setData(res);
        } catch (err) {
            setError(err.message || 'Failed to load attendance data.');
        } finally {
            setLoading(false);
        }
    }, [workshop.id]);

    useEffect(() => { load(page); }, [load, page]);

    /* close on Escape */
    useEffect(() => {
        const handler = (e) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', handler);
        return () => window.removeEventListener('keydown', handler);
    }, [onClose]);

    const handlePageChange = (p) => {
        setPage(p);
        setSearch('');
    };

    /* ── derived ── */
    const rows = (data?.content ?? []).filter((a) => {
        const q = search.toLowerCase();
        return (
            !q ||
            (a.fullName ?? '').toLowerCase().includes(q) ||
            (a.studentCode ?? '').toLowerCase().includes(q) ||
            (a.email ?? '').toLowerCase().includes(q)
        );
    });

    const totalElements = data?.totalElements ?? 0;
    const totalPages = data?.totalPages ?? 0;

    /* ── render ── */
    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
            onClick={onClose}
        >
            <div
                className="relative flex w-full max-w-5xl flex-col rounded-2xl bg-white shadow-2xl overflow-hidden"
                style={{ maxHeight: '92vh' }}
                onClick={(e) => e.stopPropagation()}
            >
                {/* ── Header ── */}
                <div className="flex items-start justify-between bg-gradient-to-r from-indigo-600 to-violet-600 px-8 py-6">
                    <div className="flex items-center gap-4">
                        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/20 text-white">
                            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
                            </svg>
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-white">Attendance List</h2>
                            <p className="mt-0.5 text-sm text-indigo-200 line-clamp-1">{workshop.title}</p>
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="flex h-9 w-9 items-center justify-center rounded-xl text-white/70 transition hover:bg-white/20 hover:text-white"
                        aria-label="Close"
                    >
                        <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                {/* ── Summary cards ── */}
                {!loading && !error && (
                    <div className="grid grid-cols-3 gap-3 border-b border-gray-100 px-8 py-4">
                        <div className="rounded-xl bg-indigo-50 px-4 py-3 text-center">
                            <p className="text-[10px] font-semibold uppercase tracking-wide text-indigo-400">Total Registered</p>
                            <p className="mt-1 text-2xl font-bold text-indigo-700">{totalElements}</p>
                        </div>
                        <div className="rounded-xl bg-emerald-50 px-4 py-3 text-center">
                            <p className="text-[10px] font-semibold uppercase tracking-wide text-emerald-400">Checked In (page)</p>
                            <p className="mt-1 text-2xl font-bold text-emerald-700">
                                {(data?.content ?? []).filter(a => a.checkedIn).length}
                            </p>
                        </div>
                        <div className="rounded-xl bg-violet-50 px-4 py-3 text-center">
                            <p className="text-[10px] font-semibold uppercase tracking-wide text-violet-400">Page</p>
                            <p className="mt-1 text-2xl font-bold text-violet-700">
                                {totalPages === 0 ? '—' : `${page + 1} / ${totalPages}`}
                            </p>
                        </div>
                    </div>
                )}

                {/* ── Search bar ── */}
                {!loading && !error && totalElements > 0 && (
                    <div className="flex items-center gap-3 border-b border-gray-100 px-8 py-3">
                        <div className="relative flex-1">
                            <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                            </svg>
                            <input
                                type="text"
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                placeholder="Filter by name / student code / email on this page…"
                                className="w-full rounded-lg border border-gray-200 py-1.5 pl-9 pr-3 text-sm text-gray-700 placeholder-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400"
                            />
                        </div>
                    </div>
                )}

                {/* ── Body ── */}
                <div className="flex-1 overflow-y-auto">
                    {loading ? (
                        <div className="flex items-center justify-center py-16">
                            <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-500 border-t-transparent" />
                        </div>
                    ) : error ? (
                        <div className="flex items-center justify-center py-16 text-sm text-red-500">
                            ⚠ {error}
                        </div>
                    ) : totalElements === 0 ? (
                        <div className="flex flex-col items-center justify-center py-16 text-gray-400">
                            <svg className="mb-3 h-12 w-12 opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                            </svg>
                            <p className="text-sm">No successful registrations for this workshop yet.</p>
                        </div>
                    ) : rows.length === 0 ? (
                        <div className="flex items-center justify-center py-12 text-sm text-gray-400">
                            No records match the filter on this page.
                        </div>
                    ) : (
                        <table className="w-full text-sm">
                            <thead className="sticky top-0 z-10">
                                <tr className="border-b bg-gray-50 text-[10px] uppercase tracking-wider text-gray-400">
                                    <th className="px-6 py-3 text-left font-semibold">#</th>
                                    <th className="px-6 py-3 text-left font-semibold">Student</th>
                                    <th className="px-6 py-3 text-left font-semibold">Contact</th>
                                    <th className="px-6 py-3 text-left font-semibold">Registered At</th>
                                    <th className="px-6 py-3 text-center font-semibold">Check-in</th>
                                    <th className="px-6 py-3 text-left font-semibold">Checked-in At</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((a, idx) => (
                                    <tr
                                        key={a.registrationId}
                                        className={`border-b last:border-0 transition-colors ${a.checkedIn ? 'bg-emerald-50/50 hover:bg-emerald-50' : 'hover:bg-gray-50/60'
                                            }`}
                                    >
                                        <td className="px-6 py-3.5 text-[11px] text-gray-400">
                                            {page * PAGE_SIZE + idx + 1}
                                        </td>
                                        <td className="px-6 py-3.5">
                                            <div className="font-semibold text-gray-800 text-[12px]">{a.fullName || '—'}</div>
                                            <div className="text-[10px] text-gray-400 mt-0.5">{a.studentCode || '—'}</div>
                                        </td>
                                        <td className="px-6 py-3.5">
                                            <div className="text-[11px] text-gray-600">{a.email || '—'}</div>
                                            <div className="text-[10px] text-gray-400 mt-0.5">{a.phoneNumber || '—'}</div>
                                        </td>
                                        <td className="px-6 py-3.5 text-[11px] text-gray-500">{fmt(a.registeredAt)}</td>
                                        <td className="px-6 py-3.5 text-center">
                                            {a.checkedIn ? (
                                                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-1 text-[10px] font-bold text-emerald-700">
                                                    <svg className="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
                                                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                                                    </svg>
                                                    Attended
                                                </span>
                                            ) : (
                                                <span className="inline-block rounded-full bg-gray-100 px-2.5 py-1 text-[10px] font-medium text-gray-500">
                                                    Absent
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-3.5 text-[11px] text-gray-500">{fmt(a.checkedInAt)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* ── Footer: PaginationControl + close ── */}
                <div className="flex items-center justify-between border-t border-gray-100 px-8 py-4">
                    <PaginationControl
                        currentPage={page}
                        totalPages={totalPages}
                        totalElements={totalElements}
                        pageSize={PAGE_SIZE}
                        onPageChange={handlePageChange}
                        itemLabel="attendees"
                    />
                    <button
                        onClick={onClose}
                        className="rounded-lg border border-gray-300 px-5 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50"
                    >
                        Close
                    </button>
                </div>
            </div>
        </div>
    );
};

export default AttendanceModal;
