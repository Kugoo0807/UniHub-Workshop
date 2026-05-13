import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import workshopService from '../services/workshopService';
import workshopRegistrationService from '../services/workshopRegistrationService';
import PaymentModal from '../components/workshops/PaymentModal';

const HOLD_EXPIRY_MINUTES = 10;

const HomeStudent = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    
    const [upcomingWorkshops, setUpcomingWorkshops] = useState([]);
    const [myRegistrations, setMyRegistrations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [paymentTarget, setPaymentTarget] = useState(null);
    const [paymentModalOpen, setPaymentModalOpen] = useState(false);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');

            const [workshops, registrations] = await Promise.all([
                workshopService.getAll(),
                workshopService.getUserWorkshops(),
            ]);

            setUpcomingWorkshops((workshops || []).slice(0, 3));
            setMyRegistrations(registrations || []);
        } catch (err) {
            setError('Failed to load workshops. Please try again.');
            console.error('Error fetching data:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const splitRegistrations = useMemo(() => {
        const now = new Date();

        const sortable = [...myRegistrations].sort((left, right) => {
            const leftTime = new Date(left.createdAt || 0).getTime();
            const rightTime = new Date(right.createdAt || 0).getTime();
            return rightTime - leftTime;
        });

        const pending = [];
        const history = [];

        sortable.forEach((registration) => {
            const endTime = registration.endTime ? new Date(registration.endTime) : null;
            const isPendingUpcoming = registration.status === 'PENDING' && (!endTime || endTime > now);

            if (isPendingUpcoming) {
                pending.push(registration);
                return;
            }

            history.push(registration);
        });

        return { pending, history };
    }, [myRegistrations]);

    const paymentModalWorkshop = paymentTarget
        ? {
            title: paymentTarget.title,
            price: paymentTarget.price,
        }
        : null;

    const openPaymentModal = (registration) => {
        if (!registration.paymentIdempotencyKey) {
            setError('No payment code was found for this registration.');
            return;
        }

        setError('');
        setPaymentTarget(registration);
        setPaymentModalOpen(true);
    };

    const handleRetryPayment = async () => {
        if (!paymentTarget) return;

        const response = await workshopRegistrationService.processPayment(
            paymentTarget.workshopId,
            paymentTarget.paymentIdempotencyKey
        );

        if (!response?.success) {
            throw new Error(response?.message || 'Payment failed');
        }

        setPaymentModalOpen(false);
        setPaymentTarget(null);
        await fetchData();
    };

    const handleCancelPending = async (workshopId) => {
        try {
            await workshopRegistrationService.cancelRegistration(workshopId);
            await fetchData();
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'Failed to cancel registration.');
        }
    };

    const handleBrowseWorkshops = () => {
        navigate('/workshops');
    };

    const handleViewProfile = () => {
        navigate('/profile');
    };

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

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

    const formatPrice = (value) => {
        if (!value || Number(value) === 0) return 'Free';
        return Number(value).toLocaleString('vi-VN') + 'đ';
    };

    const getQrImageUrl = (qrCode) => {
        if (!qrCode) return '';
        return `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(qrCode)}`;
    };

    const isHoldExpired = (registration) => {
        if (registration.status !== 'PENDING' || !registration.createdAt) return false;
        const createdAt = new Date(registration.createdAt).getTime();
        return Date.now() - createdAt >= HOLD_EXPIRY_MINUTES * 60 * 1000;
    };

    const isWorkshopEnded = (registration) => {
        if (!registration.endTime) return false;
        return new Date(registration.endTime).getTime() <= Date.now();
    };

    const getStatusMeta = (status) => {
        switch (status) {
            case 'SUCCESS':
                return { label: 'Confirmed', className: 'bg-emerald-100 text-emerald-700' };
            case 'PENDING':
                return { label: 'Payment pending', className: 'bg-amber-100 text-amber-700' };
            case 'FAILED':
                return { label: 'Payment failed', className: 'bg-red-100 text-red-700' };
            case 'CANCELLED':
                return { label: 'Cancelled', className: 'bg-gray-100 text-gray-600' };
            default:
                return { label: status || '—', className: 'bg-gray-100 text-gray-600' };
        }
    };

    const RegistrationCard = ({ registration, muted = false, showActions = false }) => {
        const statusMeta = getStatusMeta(registration.status);
        const holdExpired = isHoldExpired(registration);
        const ended = isWorkshopEnded(registration);
        const canPay = registration.status === 'PENDING' && registration.paymentIdempotencyKey && !holdExpired;
        const canCancel = registration.status === 'PENDING' && !holdExpired;

        return (
            <article
                className={`rounded-2xl border p-4 shadow-sm transition ${
                    registration.status === 'PENDING'
                        ? 'border-amber-200 bg-amber-50/60'
                        : 'border-gray-100 bg-white'
                } ${muted ? 'opacity-50' : ''}`}
            >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-start justify-between gap-2">
                            <div>
                                <h3 className="text-base font-semibold text-gray-900">{registration.title}</h3>
                                <p className="mt-1 text-xs text-gray-500">
                                    Workshop #{registration.workshopId}
                                </p>
                            </div>
                            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusMeta.className}`}>
                                {statusMeta.label}
                            </span>
                        </div>

                        <div className="mt-4 grid gap-2 text-sm text-gray-600 sm:grid-cols-2">
                            <p><span className="font-medium text-gray-500">Price:</span> {formatPrice(registration.price)}</p>
                            <p><span className="font-medium text-gray-500">Registered at:</span> {formatDateTime(registration.createdAt)}</p>
                            <p><span className="font-medium text-gray-500">Start time:</span> {formatDateTime(registration.startTime)}</p>
                            <p><span className="font-medium text-gray-500">End time:</span> {formatDateTime(registration.endTime)}</p>
                        </div>

                        {registration.status === 'SUCCESS' && registration.qrCode && (
                            <div className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
                                <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">
                                    Check-in QR code
                                </p>
                                <div className="mt-3 flex flex-col items-start gap-3 sm:flex-row sm:items-center">
                                    <img
                                        src={getQrImageUrl(registration.qrCode)}
                                        alt={`QR code for ${registration.title}`}
                                        className="h-28 w-28 rounded-xl border border-emerald-100 bg-white p-2"
                                    />
                                    <div className="space-y-1 text-sm text-emerald-900">
                                        <p className="font-medium">Scan this code to check in at the workshop.</p>
                                        <p className="break-all text-xs text-emerald-800">{registration.qrCode}</p>
                                    </div>
                                </div>
                            </div>
                        )}

                        {registration.status === 'PENDING' && holdExpired && (
                            <div className="mt-4 rounded-2xl border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600">
                                Cancellation in progress. The seat will be released automatically if the transaction is not completed.
                            </div>
                        )}

                        {registration.status === 'SUCCESS' && !registration.qrCode && (
                            <div className="mt-4 rounded-2xl border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600">
                                QR code is not ready yet. Please refresh later.
                            </div>
                        )}
                    </div>

                    {showActions && (
                        <div className="flex shrink-0 flex-col gap-2 lg:min-w-45">
                            {holdExpired ? (
                                <div className="rounded-lg bg-gray-50 border border-gray-200 px-4 py-2 text-sm text-gray-600 text-center">
                                    Cancellation in progress
                                </div>
                            ) : (
                                <>
                                    <button
                                        onClick={() => openPaymentModal(registration)}
                                        disabled={!canPay}
                                        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                                    >
                                        Pay now
                                    </button>
                                    <button
                                        onClick={() => handleCancelPending(registration.workshopId)}
                                        disabled={!canCancel}
                                        className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                                    >
                                        Cancel registration
                                    </button>
                                </>
                            )}
                        </div>
                    )}
                </div>

                {registration.status === 'PENDING' && !holdExpired && !registration.paymentIdempotencyKey && (
                    <p className="mt-3 text-xs text-amber-700">
                        No payment code is available for retry. Please wait for the system to update or register again if needed.
                    </p>
                )}

                {registration.status === 'PENDING' && ended && (
                    <p className="mt-3 text-xs text-gray-500">
                        The workshop has already ended, but this registration is still pending. Please verify the payment status.
                    </p>
                )}
            </article>
        );
    };

    const pendingCount = splitRegistrations.pending.length;
    const historyCount = splitRegistrations.history.length;
    const successCount = myRegistrations.filter((registration) => registration.status === 'SUCCESS').length;

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="flex flex-col items-center gap-4">
                    <div className="h-12 w-12 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
                    <p className="text-gray-600">Loading your dashboard...</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            {/* Header */}
            <div className="mb-8 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Student Dashboard</h1>
                    <p className="mt-1 text-sm text-gray-500">
                        Welcome back, {user?.fullName ?? 'Student'}
                    </p>
                </div>
                <button
                    onClick={handleLogout}
                    className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                >
                    Logout
                </button>
            
            </div>

            {error && (
                <div className="mb-4 rounded-lg bg-red-50 border border-red-200 p-3 sm:p-4 text-red-700 text-sm">
                    {error}
                </div>
            )}

            <div className="mb-6 grid gap-4 sm:grid-cols-3">
                <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                    <p className="text-sm font-medium text-gray-500">Pending registrations</p>
                    <p className="mt-2 text-3xl font-bold text-gray-900">{pendingCount}</p>
                </div>
                <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                    <p className="text-sm font-medium text-gray-500">History items</p>
                    <p className="mt-2 text-3xl font-bold text-gray-900">{historyCount}</p>
                </div>
                <div className="rounded-2xl bg-white p-5 shadow-sm border border-gray-100">
                    <p className="text-sm font-medium text-gray-500">Successful check-ins</p>
                    <p className="mt-2 text-3xl font-bold text-gray-900">{successCount}</p>
                </div>
            </div>

            <div className="grid gap-4 sm:gap-6 md:grid-cols-2 lg:grid-cols-3">
                {/* User Info Card */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition">
                    <h3 className="text-sm font-semibold text-gray-500 uppercase mb-3">Your Info</h3>
                    <div className="space-y-2">
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">Name:</span> {user?.fullName}
                        </p>
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">Email:</span> {user?.email}
                        </p>
                        <p className="text-sm text-gray-700">
                            <span className="font-medium">Student ID:</span> {user?.studentCode || 'N/A'}
                        </p>
                    </div>
                    <button
                        onClick={handleViewProfile}
                        className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition"
                    >
                        Edit Profile
                    </button>
                </section>

                {/* Upcoming Workshops */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-1 lg:col-span-2">
                    <h2 className="mb-4 text-base sm:text-lg font-semibold text-gray-800">
                        Upcoming Workshops
                    </h2>
                    {upcomingWorkshops.length === 0 ? (
                        <p className="text-sm text-gray-500 py-4">No workshops available yet. Check back soon!</p>
                    ) : (
                        <ul className="space-y-2">
                            {upcomingWorkshops.map((workshop) => (
                                <li
                                    key={workshop.id}
                                    className="flex items-center justify-between rounded-lg border border-gray-100 p-3 hover:bg-gray-50 transition cursor-pointer"
                                    onClick={() => navigate('/workshops')}
                                >
                                    <div className="flex items-center gap-3 flex-1 min-w-0">
                                        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 font-bold text-sm shrink-0">
                                            {workshop.title?.charAt(0).toUpperCase()}
                                        </span>
                                        <span className="text-sm text-gray-700 truncate">{workshop.title}</span>
                                    </div>
                                    <span className="text-xs text-gray-500 ml-2 shrink-0">
                                        {workshop.remainingSlots === 0 ? 'Full' : `${workshop.remainingSlots} slots`}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>

                {/* Registration History */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-2 lg:col-span-3">
                    <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                        <div>
                            <h2 className="text-base sm:text-lg font-semibold text-gray-800">
                                Registration History
                            </h2>
                            <p className="mt-1 text-sm text-gray-500">
                                Pending workshops stay on top; successful QR codes remain visible underneath.
                            </p>
                        </div>
                        <button
                            onClick={handleBrowseWorkshops}
                            className="rounded-lg border border-indigo-600 px-4 py-2 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                        >
                            Browse workshops
                        </button>
                    </div>

                    {splitRegistrations.pending.length === 0 && splitRegistrations.history.length === 0 ? (
                        <div className="rounded-2xl border border-dashed border-gray-200 bg-gray-50 px-6 py-10 text-center">
                            <p className="text-sm text-gray-500 mb-3">No registrations yet</p>
                            <button
                                onClick={handleBrowseWorkshops}
                                className="text-sm font-medium text-indigo-600 hover:text-indigo-700"
                            >
                                Register now →
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-8">
                            {splitRegistrations.pending.length > 0 && (
                                <div>
                                    <div className="mb-3 flex items-center justify-between">
                                        <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">
                                            {splitRegistrations.pending.length} item(s)
                                        </span>
                                    </div>
                                    <div className="space-y-3">
                                        {splitRegistrations.pending.map((registration) => (
                                            <RegistrationCard
                                                key={registration.registrationId}
                                                registration={registration}
                                                showActions
                                            />
                                        ))}
                                    </div>
                                </div>
                            )}

                            {splitRegistrations.history.length > 0 && (
                                <div>
                                    <div className="mb-3 flex items-center justify-between">
                                        <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600">
                                            {splitRegistrations.history.length} item(s)
                                        </span>
                                    </div>
                                    <div className="space-y-3">
                                        {splitRegistrations.history.map((registration) => (
                                            <RegistrationCard
                                                key={registration.registrationId}
                                                registration={registration}
                                                muted={
                                                    registration.status === 'FAILED' ||
                                                    registration.status === 'CANCELLED' ||
                                                    (isWorkshopEnded(registration) && registration.status !== 'SUCCESS')
                                                }
                                            />
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </section>

                {/* Quick Actions */}
                <section className="rounded-2xl bg-white p-6 shadow-sm border border-gray-100 hover:shadow-md transition md:col-span-2 lg:col-span-3">
                    <h2 className="mb-4 text-base sm:text-lg font-semibold text-gray-800">
                        Quick Actions
                    </h2>
                    <div className="flex flex-col sm:flex-row gap-3">
                        <button
                            onClick={handleBrowseWorkshops}
                            className="flex-1 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition"
                        >
                            Browse All Workshops
                        </button>
                        <button
                            onClick={handleViewProfile}
                            className="flex-1 rounded-lg border border-indigo-600 px-4 py-2.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                        >
                            View My Profile
                        </button>
                        <button
                            onClick={() => navigate('/workshops')}
                            className="flex-1 rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition"
                        >
                            Check Attendance
                        </button>
                    </div>
                </section>
            </div>

            {paymentTarget && (
                <PaymentModal
                    isOpen={paymentModalOpen}
                    workshop={paymentModalWorkshop}
                    idempotencyKey={paymentTarget.paymentIdempotencyKey}
                    onClose={() => {
                        setPaymentModalOpen(false);
                        setPaymentTarget(null);
                    }}
                    onPaymentSuccess={handleRetryPayment}
                    onPaymentError={(paymentError) => {
                        setError(paymentError?.message || 'Payment failed');
                    }}
                />
            )}
        </div>
    );
};

export default HomeStudent;
