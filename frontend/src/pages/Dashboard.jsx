import React, { useState, useEffect, useCallback, useRef } from 'react';
import adminWorkshopService from '../services/adminWorkshopService';
import { useAuth } from '../context/AuthContext';
import WorkshopFormModal from '../components/workshops/WorkshopFormModal';
import PaginationControl from '../components/common/PaginationControl';

const formatDateTime = (dt) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
        + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const formatPrice = (price) => {
    if (!price || Number(price) === 0) return 'Free';
    return Number(price).toLocaleString('vi-VN') + 'đ';
};

const statusBadge = (status) => {
    const map = {
        DRAFT: 'bg-gray-100 text-gray-700',
        PUBLISHED: 'bg-green-100 text-green-700',
        CANCELLED: 'bg-red-100 text-red-700',
        COMPLETED: 'bg-blue-100 text-blue-700',
    };
    return (
        <span className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${map[status] || 'bg-gray-100 text-gray-600'}`}>
            {status || '—'}
        </span>
    );
};

const StatCard = ({ label, value, color }) => (
    <div className={`rounded-2xl p-6 shadow-sm text-white ${color}`}>
        <p className="text-sm font-medium opacity-80">{label}</p>
        <p className="mt-2 text-4xl font-bold">{value}</p>
    </div>
);

const Dashboard = () => {
    const { user } = useAuth();

    const stats = [
        { label: 'Total Users', value: '—', color: 'bg-indigo-600' },
        { label: 'Active Workshops', value: '—', color: 'bg-emerald-500' },
        { label: 'Total Registrations', value: '—', color: 'bg-violet-500' },
        { label: 'Pending Payments', value: '—', color: 'bg-amber-500' },
    ];
    const [workshops, setWorkshops] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingWorkshop, setEditingWorkshop] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [deleteConfirm, setDeleteConfirm] = useState(null);
    const [cancelConfirm, setCancelConfirm] = useState(null);
    const [statsData, setStatsData] = useState(null);

    // Pagination state
    const PAGE_SIZE = 12;
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    // AI Summary State
    const [aiUploadOpen, setAiUploadOpen] = useState(false);
    const [aiWorkshop, setAiWorkshop] = useState(null);
    const [aiFile, setAiFile] = useState(null);
    const [aiUploading, setAiUploading] = useState(false);
    const [aiSuccessMessage, setAiSuccessMessage] = useState('');
    const [openActionId, setOpenActionId] = useState(null);

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = () => setOpenActionId(null);
        window.addEventListener('click', handleClickOutside);
        return () => window.removeEventListener('click', handleClickOutside);
    }, []);

    const aiPollRef = useRef({ cancelled: false, timeoutId: null });

    const fetchWorkshops = useCallback(async ({ silent = false, page = currentPage } = {}) => {
        try {
            if (!silent) setLoading(true);
            setError('');
            const data = await adminWorkshopService.getAll(page, PAGE_SIZE);
            // data is the PageResponse envelope: { content, page, size, totalElements, totalPages, last }
            setWorkshops(data.content ?? data);
            setCurrentPage(data.page ?? page);
            setTotalPages(data.totalPages ?? 1);
            setTotalElements(data.totalElements ?? (data.content ?? data).length);
        } catch (err) {
            setError(err.message);
        } finally {
            if (!silent) setLoading(false);
        }
    }, [currentPage]);

    useEffect(() => { fetchWorkshops({ page: 0 }); }, []);  // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        return () => {
            aiPollRef.current.cancelled = true;
            if (aiPollRef.current.timeoutId) {
                clearTimeout(aiPollRef.current.timeoutId);
            }
        };
    }, []);

    // Update stats value when workshops load
    stats[1].value = totalElements.toString();

    const handleCreate = () => {
        setEditingWorkshop(null);
        setModalOpen(true);
    };

    const handleEdit = (workshop) => {
        setEditingWorkshop(workshop);
        setModalOpen(true);
    };

    const handleSubmit = async (payload) => {
        try {
            setSubmitting(true);
            if (editingWorkshop) {
                await adminWorkshopService.update(editingWorkshop.id, payload);
            } else {
                await adminWorkshopService.create(payload);
            }
            setModalOpen(false);
            fetchWorkshops({ page: currentPage });
        } catch (err) {
            setError(err.message);
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (id) => {
        try {
            await adminWorkshopService.delete(id);
            setDeleteConfirm(null);
            // If we deleted the last item on this page, go back one page
            const nextPage = workshops.length === 1 && currentPage > 0 ? currentPage - 1 : currentPage;
            fetchWorkshops({ page: nextPage });
        } catch (err) {
            setError(err.message);
            setDeleteConfirm(null);
        }
    };

    const handleCancel = async (id) => {
        try {
            await adminWorkshopService.cancel(id);
            setCancelConfirm(null);
            fetchWorkshops({ page: currentPage });
        } catch (err) {
            setError(err.message);
            setCancelConfirm(null);
        }
    };

    const handlePublish = async (id) => {
        try {
            await adminWorkshopService.publish(id);
            fetchWorkshops({ page: currentPage });
        } catch (err) {
            setError(err.message);
        }
    };

    const handleViewStats = async (id) => {
        try {
            const data = await adminWorkshopService.getStats(id);
            setStatsData(data);
        } catch (err) {
            setError(err.message);
        }
    };

    const pollAiDescriptionUpdate = useCallback(async (workshopId, previousDescription) => {
        const startTime = Date.now();
        const maxWaitMs = 120_000; // Tăng lên 2 phút
        const intervalMs = 3_000; // Kiểm tra mỗi 3 giây

        const pollOnce = async () => {
            if (aiPollRef.current.cancelled) return;
            if (Date.now() - startTime > maxWaitMs) {
                console.log("Polling timed out");
                return;
            }

            try {
                const latest = await adminWorkshopService.getById(workshopId);
                const latestDescription = (latest?.description || '').trim();
                const prev = (previousDescription || '').trim();

                if (latestDescription && latestDescription !== prev) {
                    console.log("AI summary detected! Updating UI...");
                    await fetchWorkshops({ silent: true });
                    setAiSuccessMessage("AI summary has been updated successfully!");
                    return;
                }
            } catch (_) {
                // Ignore transient errors; keep polling until timeout.
            }

            aiPollRef.current.timeoutId = setTimeout(pollOnce, intervalMs);
        };

        pollOnce();
    }, [fetchWorkshops]);

    const handleOpenAiUpload = (workshop) => {
        setAiWorkshop(workshop);
        setAiFile(null);
        setAiSuccessMessage('');
        setError('');
        setAiUploadOpen(true);
    };

    const handleAiUploadSubmit = async () => {
        if (!aiFile) {
            setError('Please select a PDF file first.');
            return;
        }
        try {
            setAiUploading(true);
            setError('');
            setAiSuccessMessage('');

            // Reset any previous poll.
            if (aiPollRef.current.timeoutId) clearTimeout(aiPollRef.current.timeoutId);
            aiPollRef.current.cancelled = false;

            const previousDescription = aiWorkshop?.description || '';
            const res = await adminWorkshopService.uploadAiSummary(aiWorkshop.id, aiFile);
            setAiSuccessMessage(res.message || 'AI summary is being processed.');
            setAiFile(null);

            // The AI endpoint is async (202). Poll until the description is actually updated in DB.
            pollAiDescriptionUpdate(aiWorkshop.id, previousDescription);

            setTimeout(() => {
                setAiUploadOpen(false);
                fetchWorkshops({ silent: true });
            }, 3000);
        } catch (err) {
            setError(err.message || 'Upload failed');
        } finally {
            setAiUploading(false);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center py-20">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            {/* Dashboard Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-gray-900">Admin Dashboard</h1>
                <p className="mt-1 text-gray-500">
                    Logged in as <span className="font-medium text-indigo-600">{user?.fullName ?? 'Admin'}</span>
                </p>
            </div>

            {/* Overview Stats */}
            <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {stats.map((s) => (
                    <StatCard key={s.label} {...s} />
                ))}
            </div>

            {/* Workshop Management Section Header */}
            <div className="mb-6 flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-gray-900">Workshop Management</h2>
                    <p className="mt-1 text-sm text-gray-500">
                        {totalElements} workshop{totalElements !== 1 ? 's' : ''} total
                        {totalPages > 1 && ` · Page ${currentPage + 1} of ${totalPages}`}
                    </p>
                </div>
                <button
                    onClick={handleCreate}
                    className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 transition"
                >
                    <span className="text-lg leading-none">+</span>
                    Create Workshop
                </button>
            </div>

            {/* Error Banner */}
            {error && (
                <div className="mb-4 flex items-center justify-between rounded-lg bg-red-50 p-3 text-sm text-red-700">
                    <span>{error}</span>
                    <button onClick={() => setError('')} className="font-medium hover:text-red-900">✕</button>
                </div>
            )}

            {/* Workshop Table */}
            {workshops.length === 0 ? (
                <div className="rounded-2xl bg-white p-12 text-center shadow-sm">
                    <p className="text-gray-400">No workshops yet. Click "Create Workshop" to get started.</p>
                </div>
            ) : (
                <div className="overflow-hidden rounded-2xl bg-white shadow-sm border border-gray-100">
                    <table className="w-full text-sm text-left">
                        <thead>
                            <tr className="border-b bg-gray-50/50 text-[11px] uppercase tracking-wider text-gray-400">
                                <th className="px-6 py-4 font-bold text-left">Workshop</th>
                                <th className="px-6 py-4 font-bold text-center">Status</th>
                                <th className="px-6 py-4 font-bold text-left">Event Time</th>
                                <th className="px-6 py-4 font-bold text-left">Registration</th>
                                <th className="px-6 py-4 font-bold text-center">Slots</th>
                                <th className="px-6 py-4 font-bold text-right">Price</th>
                                <th className="px-6 py-4 font-bold text-center">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {workshops.map((w) => (
                                <tr key={w.id} className="border-b last:border-0 hover:bg-gray-50/30 transition">
                                    <td className="px-6 py-3 text-left">
                                        <div className="font-bold text-gray-900 text-[13px]">{w.title}</div>
                                        <div className="mt-1 text-[11px] text-gray-500">
                                            {w.speaker || '—'} · {w.roomName || '—'}
                                        </div>
                                        {w.description && (
                                            <div className="mt-1 text-[11px] text-gray-400 line-clamp-1 max-w-[500px]" title={w.description}>
                                                {w.description}
                                            </div>
                                        )}
                                    </td>
                                    <td className="px-6 py-3 text-center align-middle">{statusBadge(w.status)}</td>
                                    <td className="px-6 py-3 text-left align-middle text-[11px] text-gray-600">
                                        <div>{formatDateTime(w.startTime)}</div>
                                        <div className="text-gray-400">→ {formatDateTime(w.endTime)}</div>
                                    </td>
                                    <td className="px-6 py-3 text-left align-middle text-[11px] text-gray-600">
                                        <div>{formatDateTime(w.registrationStartTime)}</div>
                                        <div className="text-gray-400">→ {formatDateTime(w.registrationEndTime)}</div>
                                    </td>
                                    <td className="px-6 py-3 text-center align-middle">
                                        <span className={`inline-block rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${w.remainingSlots === 0
                                            ? 'bg-red-100 text-red-700'
                                            : w.remainingSlots <= w.totalSlots * 0.2
                                                ? 'bg-amber-100 text-amber-700'
                                                : 'bg-green-100 text-green-700'
                                            }`}>
                                            {w.remainingSlots}/{w.totalSlots}
                                        </span>
                                    </td>
                                    <td className="px-6 py-3 text-right align-middle text-[13px] font-medium text-gray-700">{formatPrice(w.price)}</td>
                                    <td className="px-6 py-3 text-center align-middle">
                                        <div className="relative inline-block text-left" onClick={(e) => e.stopPropagation()}>
                                            <button
                                                onClick={() => setOpenActionId(openActionId === w.id ? null : w.id)}
                                                className={`flex items-center justify-center w-8 h-8 rounded-full transition-all duration-200 ${openActionId === w.id ? 'bg-gray-100 text-gray-900 shadow-inner' : 'text-gray-400 hover:bg-gray-50 hover:text-gray-600'}`}
                                            >
                                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                                                </svg>
                                            </button>

                                            {openActionId === w.id && (
                                                <div className={`absolute right-0 z-50 w-28 rounded-md border border-gray-200 bg-white py-0.5 shadow-lg ${workshops.indexOf(w) >= workshops.length - 2 && workshops.length > 0 ? 'bottom-full mb-1' : 'mt-1'}`}>
                                                    <button onClick={() => { handleViewStats(w.id); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] text-gray-700 hover:bg-gray-50 transition">
                                                        View Statistics
                                                    </button>
                                                    <button onClick={() => { handleOpenAiUpload(w); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] text-gray-700 hover:bg-gray-50 transition">
                                                        AI Summarize
                                                    </button>
                                                    {w.status !== 'CANCELLED' && w.status !== 'COMPLETED' && (
                                                        <button onClick={() => { handleEdit(w); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] text-gray-700 hover:bg-gray-50 transition">
                                                            Edit Details
                                                        </button>
                                                    )}
                                                    {w.status === 'DRAFT' && (
                                                        <button onClick={() => { handlePublish(w.id); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] font-semibold text-gray-900 hover:bg-gray-50 transition">
                                                            Publish Now
                                                        </button>
                                                    )}
                                                    {w.status === 'PUBLISHED' && (
                                                        <button onClick={() => { setCancelConfirm(w.id); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] text-red-500 hover:bg-red-50 transition">
                                                            Cancel Workshop
                                                        </button>
                                                    )}
                                                    {w.status === 'DRAFT' && (
                                                        <button onClick={() => { setDeleteConfirm(w.id); setOpenActionId(null); }} className="w-full px-3 py-1.5 text-left text-[11px] text-red-600 hover:bg-red-50 transition">
                                                            Delete
                                                        </button>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Pagination Controls */}
            <PaginationControl
                currentPage={currentPage}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={(page) => fetchWorkshops({ page })}
                itemLabel="workshops"
            />

            {/* Create/Edit Modal */}
            <WorkshopFormModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                onSubmit={handleSubmit}
                initialData={editingWorkshop}
                isLoading={submitting}
            />

            {/* Delete Confirmation */}
            {deleteConfirm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                    <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
                        <h3 className="text-lg font-bold text-gray-900">Delete Workshop?</h3>
                        <p className="mt-2 text-sm text-gray-500">
                            This action cannot be undone. Only DRAFT workshops can be deleted.
                        </p>
                        <div className="mt-5 flex justify-end gap-3">
                            <button
                                onClick={() => setDeleteConfirm(null)}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleDelete(deleteConfirm)}
                                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition"
                            >
                                Delete
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Stats Modal */}
            {statsData && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setStatsData(null)}>
                    <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
                        <h3 className="text-lg font-bold text-gray-900 mb-4">Workshop Statistics</h3>
                        <p className="text-sm text-gray-500 mb-4">{statsData.title}</p>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="rounded-xl bg-indigo-50 p-4 text-center">
                                <p className="text-xs font-medium text-indigo-500 uppercase">Total Slots</p>
                                <p className="mt-1 text-2xl font-bold text-indigo-700">{statsData.totalSlots}</p>
                            </div>
                            <div className="rounded-xl bg-emerald-50 p-4 text-center">
                                <p className="text-xs font-medium text-emerald-500 uppercase">Remaining</p>
                                <p className="mt-1 text-2xl font-bold text-emerald-700">{statsData.remainingSlots}</p>
                            </div>
                            <div className="rounded-xl bg-violet-50 p-4 text-center">
                                <p className="text-xs font-medium text-violet-500 uppercase">Registered</p>
                                <p className="mt-1 text-2xl font-bold text-violet-700">{statsData.registeredCount}</p>
                            </div>
                            <div className="rounded-xl bg-amber-50 p-4 text-center">
                                <p className="text-xs font-medium text-amber-500 uppercase">Fill Rate</p>
                                <p className="mt-1 text-2xl font-bold text-amber-700">{statsData.fillRate}%</p>
                            </div>
                        </div>
                        <div className="mt-3">
                            <div className="h-2.5 w-full rounded-full bg-gray-200 overflow-hidden">
                                <div
                                    className={`h-full rounded-full transition-all ${statsData.fillRate >= 90 ? 'bg-red-500' :
                                        statsData.fillRate >= 60 ? 'bg-amber-500' : 'bg-emerald-500'
                                        }`}
                                    style={{ width: `${Math.min(statsData.fillRate, 100)}%` }}
                                />
                            </div>
                        </div>
                        <div className="mt-5 flex justify-end">
                            <button
                                onClick={() => setStatsData(null)}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                            >
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* AI Summary Upload Modal */}
            {aiUploadOpen && aiWorkshop && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                    <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                        <h3 className="text-lg font-bold text-gray-900 mb-2">Upload PDF for AI Summary</h3>
                        <p className="text-sm text-gray-500 mb-4">{aiWorkshop.title}</p>

                        {aiSuccessMessage ? (
                            <div className="mb-4 rounded-lg bg-green-50 p-4 text-sm text-green-700">
                                {aiSuccessMessage}
                            </div>
                        ) : (
                            <>
                                <div className="mb-4">
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Select PDF File (Max 10MB)</label>
                                    <input
                                        type="file"
                                        accept="application/pdf"
                                        onChange={(e) => {
                                            if (e.target.files && e.target.files.length > 0) {
                                                setAiFile(e.target.files[0]);
                                            }
                                        }}
                                        className="block w-full text-sm text-gray-500
                                          file:mr-4 file:py-2 file:px-4
                                          file:rounded-full file:border-0
                                          file:text-sm file:font-semibold
                                          file:bg-fuchsia-50 file:text-fuchsia-700
                                          hover:file:bg-fuchsia-100"
                                    />
                                </div>
                                <p className="text-xs text-gray-500 mb-4">
                                    The AI service will run in the background to summarize the PDF and update the description.
                                </p>
                            </>
                        )}

                        <div className="mt-5 flex justify-end gap-3">
                            <button
                                onClick={() => {
                                    setAiUploadOpen(false);
                                    setAiFile(null);
                                    setAiSuccessMessage('');
                                }}
                                disabled={aiUploading}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition disabled:opacity-50"
                            >
                                Close
                            </button>
                            {!aiSuccessMessage && (
                                <button
                                    onClick={handleAiUploadSubmit}
                                    disabled={aiUploading || !aiFile}
                                    className="flex items-center gap-2 rounded-lg bg-fuchsia-600 px-4 py-2 text-sm font-medium text-white hover:bg-fuchsia-700 transition disabled:opacity-50"
                                >
                                    {aiUploading ? (
                                        <>
                                            <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
                                            Uploading...
                                        </>
                                    ) : 'Upload & Summarize'}
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Cancel Confirmation Modal */}
            {cancelConfirm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                    <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
                        <h3 className="text-lg font-bold text-gray-900">Cancel Workshop?</h3>
                        <p className="mt-2 text-sm text-gray-500">
                            This will <strong>permanently cancel</strong> the workshop. All <strong>SUCCESS</strong> and <strong>PENDING</strong> registrations will be cancelled automatically. Completed payments will be preserved for refund reconciliation.
                        </p>
                        <div className="mt-5 flex justify-end gap-3">
                            <button
                                onClick={() => setCancelConfirm(null)}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                            >
                                No, Keep
                            </button>
                            <button
                                onClick={() => handleCancel(cancelConfirm)}
                                className="rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 transition"
                            >
                                Yes, Cancel Workshop
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Dashboard;