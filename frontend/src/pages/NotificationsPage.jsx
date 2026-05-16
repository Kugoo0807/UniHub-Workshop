import React, { useCallback, useEffect, useMemo, useState } from 'react';
import notificationService from '../services/notificationService';
import PaginationControl from '../components/common/PaginationControl';
import ErrorBanner from '../components/homeStudent/ErrorBanner';
import LoadingState from '../components/homeStudent/LoadingState';

const PAGE_SIZE = 10;

const formatDateTime = (value) => {
    if (!value) return '—';
    const date = new Date(value);
    return `${date.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
    })} ${date.toLocaleTimeString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
    })}`;
};

const channelLabel = (channel) => {
    switch (channel) {
        case 'EMAIL':
            return 'Email';
        case 'TELEGRAM':
            return 'Telegram';
        case 'IN_APP':
            return 'In-app';
        default:
            return channel || '—';
    }
};

const statusStyles = (status) => {
    switch (status) {
        case 'SUCCESS':
            return 'bg-emerald-100 text-emerald-700';
        case 'FAILED':
            return 'bg-red-100 text-red-700';
        case 'PENDING':
            return 'bg-amber-100 text-amber-700';
        default:
            return 'bg-gray-100 text-gray-600';
    }
};

const NotificationsPage = () => {
    const [notifications, setNotifications] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedId, setSelectedId] = useState(null);

    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const fetchNotifications = useCallback(async (page = 0) => {
        try {
            setLoading(true);
            setError('');
            const data = await notificationService.getMyNotifications(page, PAGE_SIZE);
            const items = data.content || [];
            setNotifications(items);
            setCurrentPage(data.page ?? page);
            setTotalPages(data.totalPages ?? 1);
            setTotalElements(data.totalElements ?? items.length);
            if (items.length > 0 && !items.find((item) => item.id === selectedId)) {
                setSelectedId(items[0].id);
            }
        } catch (err) {
            setError(err.message || 'Failed to load notifications.');
        } finally {
            setLoading(false);
        }
    }, [selectedId]);

    useEffect(() => {
        fetchNotifications(0);
    }, [fetchNotifications]);

    const selectedNotification = useMemo(() => {
        return notifications.find((item) => item.id === selectedId) || null;
    }, [notifications, selectedId]);

    if (loading) {
        return <LoadingState />;
    }

    return (
        <div className="min-h-screen bg-gray-50 px-6 py-8 mx-auto max-w-7xl">
            <div className="mb-6">
                <h1 className="text-3xl font-bold text-gray-900">Notifications</h1>
                <p className="mt-1 text-sm text-gray-500">
                    Review workshop updates, reminders, and system notices.
                </p>
            </div>

            <ErrorBanner message={error} />

            <div className="grid gap-6 lg:grid-cols-[1.1fr_1.6fr]">
                <section className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
                    <div className="flex items-center justify-between mb-4">
                        <h2 className="text-sm font-semibold text-gray-700">Inbox</h2>
                        <span className="text-xs text-gray-400">{totalElements} items</span>
                    </div>

                    {notifications.length === 0 ? (
                        <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50 px-4 py-8 text-center text-sm text-gray-500">
                            No notifications yet.
                        </div>
                    ) : (
                        <div className="space-y-2">
                            {notifications.map((item) => (
                                <button
                                    key={item.id}
                                    onClick={() => setSelectedId(item.id)}
                                    className={`w-full rounded-xl border px-4 py-3 text-left transition ${
                                        item.id === selectedId
                                            ? 'border-indigo-500 bg-indigo-50'
                                            : 'border-gray-200 hover:border-indigo-200 hover:bg-indigo-50/40'
                                    }`}
                                >
                                    <div className="flex items-start justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-semibold text-gray-900">
                                                {item.title}
                                            </p>
                                            <p className="mt-1 text-xs text-gray-500">
                                                {formatDateTime(item.createdAt)}
                                            </p>
                                        </div>
                                        <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${statusStyles(item.status)}`}>
                                            Notification
                                        </span>
                                    </div>
                                </button>
                            ))}
                        </div>
                    )}

                    <PaginationControl
                        currentPage={currentPage}
                        totalPages={totalPages}
                        totalElements={totalElements}
                        pageSize={PAGE_SIZE}
                        onPageChange={(page) => {
                            setCurrentPage(page);
                            fetchNotifications(page);
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        }}
                        itemLabel="notifications"
                    />
                </section>

                <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
                    {selectedNotification ? (
                        <>
                            <div className="mb-4 flex flex-wrap items-center gap-3">
                                <h2 className="text-lg font-semibold text-gray-900">
                                    {selectedNotification.title}
                                </h2>
                                <span className="text-xs text-gray-500">
                                    {formatDateTime(selectedNotification.createdAt)}
                                </span>
                            </div>
                            <div
                                className="text-sm text-gray-700"
                                dangerouslySetInnerHTML={{ __html: selectedNotification.contentHtml }}
                            />
                        </>
                    ) : (
                        <div className="flex h-full items-center justify-center text-sm text-gray-500">
                            Select a notification to view its details.
                        </div>
                    )}
                </section>
            </div>
        </div>
    );
};

export default NotificationsPage;
