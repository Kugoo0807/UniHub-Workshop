import React, { useState, useEffect } from 'react';
import workshopService from '../services/workshopService';

const formatDateTime = (dt) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' })
        + ' • ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const formatPrice = (price) => {
    if (!price || Number(price) === 0) return 'Free';
    return Number(price).toLocaleString('vi-VN') + 'đ';
};

const WorkshopListPage = () => {
    const [workshops, setWorkshops] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedWorkshop, setSelectedWorkshop] = useState(null);

    useEffect(() => {
        const fetchWorkshops = async () => {
            try {
                const data = await workshopService.getAll();
                setWorkshops(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };
        fetchWorkshops();
    }, []);

    if (loading) {
        return (
            <div className="flex items-center justify-center py-20">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
            </div>
        );
    }

    return (
        <div className="mx-auto max-w-5xl px-4 py-8">
            {/* Hero Section */}
            <div className="mb-8 rounded-2xl bg-gradient-to-r from-indigo-600 to-violet-600 px-8 py-10 text-white shadow-lg">
                <h1 className="text-3xl font-bold">Skills & Careers Week</h1>
                <p className="mt-2 text-indigo-100">
                    Discover and register for exciting workshops at UniHub.
                </p>
            </div>

            {error && (
                <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</div>
            )}

            {workshops.length === 0 ? (
                <div className="rounded-2xl bg-white p-12 text-center shadow-sm">
                    <p className="text-gray-400">No workshops are open yet. Please check back later.</p>
                </div>
            ) : (
                <div className="grid gap-4 md:grid-cols-2">
                    {workshops.map((w) => (
                        <div
                            key={w.id}
                            className="group cursor-pointer rounded-2xl bg-white p-5 shadow-sm border border-gray-100 hover:shadow-md hover:border-indigo-200 transition-all"
                            onClick={() => setSelectedWorkshop(w)}
                        >
                            <div className="flex items-start justify-between">
                                <h3 className="text-base font-semibold text-gray-900 group-hover:text-indigo-600 transition">
                                    {w.title}
                                </h3>
                                <span className={`ml-2 shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                                    Number(w.price) === 0
                                        ? 'bg-emerald-100 text-emerald-700'
                                        : 'bg-amber-100 text-amber-700'
                                }`}>
                                    {formatPrice(w.price)}
                                </span>
                            </div>

                            {w.description && (
                                <p className="mt-2 text-sm text-gray-500 line-clamp-2">{w.description}</p>
                            )}

                            <div className="mt-3 flex items-center gap-4 text-xs text-gray-400">
                                <span>🕐 {formatDateTime(w.startTime)}</span>
                                <span className={`rounded-full px-2 py-0.5 font-semibold ${
                                    w.remainingSlots === 0
                                        ? 'bg-red-100 text-red-600'
                                        : 'bg-indigo-100 text-indigo-600'
                                }`}>
                                    {w.remainingSlots === 0 ? 'Sold out' : `${w.remainingSlots}/${w.totalSlots} seats left`}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Workshop Detail Modal */}
            {selectedWorkshop && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setSelectedWorkshop(null)}>
                    <div
                        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <h2 className="text-xl font-bold text-gray-900">{selectedWorkshop.title}</h2>

                        <div className="mt-4 space-y-3">
                            <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-500 w-24">Time:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.startTime)}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-500 w-24">Ends:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.endTime)}</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-500 w-24">Price:</span>
                                <span className={`text-sm font-semibold ${
                                    Number(selectedWorkshop.price) === 0 ? 'text-emerald-600' : 'text-amber-600'
                                }`}>
                                    {formatPrice(selectedWorkshop.price)}
                                </span>
                            </div>
                            <div className="flex items-center gap-2">
                                <span className="text-sm font-medium text-gray-500 w-24">Seats:</span>
                                <span className="text-sm text-gray-700">
                                    {selectedWorkshop.remainingSlots}/{selectedWorkshop.totalSlots} available
                                </span>
                            </div>
                        </div>

                        {selectedWorkshop.description && (
                            <div className="mt-4">
                                <h4 className="text-sm font-medium text-gray-500 mb-1">Description:</h4>
                                <p className="text-sm text-gray-700 whitespace-pre-wrap">{selectedWorkshop.description}</p>
                            </div>
                        )}

                        <div className="mt-6 flex justify-end gap-3">
                            <button
                                onClick={() => setSelectedWorkshop(null)}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                            >
                                Close
                            </button>
                            <button
                                disabled={selectedWorkshop.remainingSlots === 0}
                                className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                            >
                                {selectedWorkshop.remainingSlots === 0 ? 'Sold out' : 'Register'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default WorkshopListPage;
