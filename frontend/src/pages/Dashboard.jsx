import React, { useState, useEffect, useCallback } from 'react';
import workshopService from '../services/workshopService';
import { useAuth } from '../context/AuthContext';
import WorkshopFormModal from '../components/workshops/WorkshopFormModal';

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
    const [statsData, setStatsData] = useState(null);

    // AI Summary State
    const [aiUploadOpen, setAiUploadOpen] = useState(false);
    const [aiWorkshop, setAiWorkshop] = useState(null);
    const [aiFile, setAiFile] = useState(null);
    const [aiUploading, setAiUploading] = useState(false);
    const [aiSuccessMessage, setAiSuccessMessage] = useState('');

    const fetchWorkshops = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await workshopService.getAll();
            setWorkshops(data);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchWorkshops(); }, [fetchWorkshops]);
    
    // Update stats value when workshops load
    stats[1].value = workshops.length.toString();

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
                await workshopService.update(editingWorkshop.id, payload);
            } else {
                await workshopService.create(payload);
            }
            setModalOpen(false);
            fetchWorkshops();
        } catch (err) {
            setError(err.message);
        } finally {
            setSubmitting(false);
        }
    };

    const handleDelete = async (id) => {
        try {
            await workshopService.delete(id);
            setDeleteConfirm(null);
            fetchWorkshops();
        } catch (err) {
            setError(err.message);
            setDeleteConfirm(null);
        }
    };

    const handleViewStats = async (id) => {
        try {
            const data = await workshopService.getStats(id);
            setStatsData(data);
        } catch (err) {
            setError(err.message);
        }
    };

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
            const res = await workshopService.uploadAiSummary(aiWorkshop.id, aiFile);
            setAiSuccessMessage(res.message || 'AI summary is being processed.');
            setAiFile(null);
            setTimeout(() => {
                setAiUploadOpen(false);
                fetchWorkshops();
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
                        {workshops.length} workshop{workshops.length !== 1 ? 's' : ''} total
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
                <div className="overflow-hidden rounded-2xl bg-white shadow-sm">
                    <table className="w-full text-sm text-left">
                        <thead>
                            <tr className="border-b bg-gray-50 text-xs uppercase tracking-wider text-gray-500">
                                <th className="px-4 py-3">Title</th>
                                <th className="px-4 py-3">Time</th>
                                <th className="px-4 py-3 text-center">Slots</th>
                                <th className="px-4 py-3 text-right">Price</th>
                                <th className="px-4 py-3 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {workshops.map((w) => (
                                <tr key={w.id} className="border-b last:border-0 hover:bg-gray-50 transition">
                                    <td className="px-4 py-3">
                                        <div className="font-medium text-gray-900">{w.title}</div>
                                        {w.description && (
                                            <div className="mt-0.5 text-xs text-gray-400 line-clamp-1">{w.description}</div>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-gray-600">
                                        <div>{formatDateTime(w.startTime)}</div>
                                        <div className="text-xs text-gray-400">→ {formatDateTime(w.endTime)}</div>
                                    </td>
                                    <td className="px-4 py-3 text-center">
                                        <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                                            w.remainingSlots === 0
                                                ? 'bg-red-100 text-red-700'
                                                : w.remainingSlots <= w.totalSlots * 0.2
                                                    ? 'bg-amber-100 text-amber-700'
                                                    : 'bg-green-100 text-green-700'
                                        }`}>
                                            {w.remainingSlots}/{w.totalSlots}
                                        </span>
                                    </td>
                                    <td className="px-4 py-3 text-right text-gray-700">{formatPrice(w.price)}</td>
                                    <td className="px-4 py-3 text-right">
                                        <div className="flex justify-end gap-1">
                                            <button
                                                onClick={() => handleViewStats(w.id)}
                                                className="rounded-md px-2.5 py-1.5 text-xs font-medium text-indigo-600 hover:bg-indigo-50 transition"
                                                title="View Stats"
                                            >
                                                Stats
                                            </button>
                                            <button
                                                onClick={() => handleOpenAiUpload(w)}
                                                className="rounded-md px-2.5 py-1.5 text-xs font-medium text-fuchsia-600 hover:bg-fuchsia-50 transition"
                                                title="AI Summary"
                                            >
                                                AI
                                            </button>
                                            <button
                                                onClick={() => handleEdit(w)}
                                                className="rounded-md px-2.5 py-1.5 text-xs font-medium text-amber-600 hover:bg-amber-50 transition"
                                                title="Edit"
                                            >
                                                Edit
                                            </button>
                                            <button
                                                onClick={() => setDeleteConfirm(w.id)}
                                                className="rounded-md px-2.5 py-1.5 text-xs font-medium text-red-600 hover:bg-red-50 transition"
                                                title="Delete"
                                            >
                                                Delete
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

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
                            This action cannot be undone. If the workshop has successful registrations, deletion will be blocked.
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
                                    className={`h-full rounded-full transition-all ${
                                        statsData.fillRate >= 90 ? 'bg-red-500' :
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
        </div>
    );
};

export default Dashboard;
