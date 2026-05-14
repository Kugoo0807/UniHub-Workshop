import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import workshopService from '../services/workshopService';
import workshopRegistrationService from '../services/workshopRegistrationService';
import PaymentModal from '../components/workshops/PaymentModal';
import RegistrationHistory from '../components/homeStudent/RegistrationHistory';
import LoadingState from '../components/homeStudent/LoadingState';
import ErrorBanner from '../components/homeStudent/ErrorBanner';
import PaginationControl from '../components/common/PaginationControl';

const HOLD_EXPIRY_MINUTES = 10;
const PAGE_SIZE = 12;

const RegistrationHistoryPage = () => {
    const navigate = useNavigate();

    const [myRegistrations, setMyRegistrations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [paymentTarget, setPaymentTarget] = useState(null);
    const [paymentModalOpen, setPaymentModalOpen] = useState(false);

    // Pagination state
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const fetchData = useCallback(async (page = 0) => {
        try {
            setLoading(true);
            setError('');

            const data = await workshopService.getUserWorkshops(page, PAGE_SIZE);
            setMyRegistrations(data.content || []);
            setCurrentPage(data.page);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (err) {
            setError('Failed to load registration history. Please try again.');
            console.error('Error fetching registrations:', err);
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
        await fetchData(currentPage);
    };

    const handleCancelPending = async (workshopId) => {
        try {
            await workshopRegistrationService.cancelRegistration(workshopId);
            await fetchData(currentPage);
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'Failed to cancel registration.');
        }
    };

    const handleBrowseWorkshops = () => {
        navigate('/workshops');
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

    if (loading) {
        return <LoadingState />;
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Registration History</h1>
                    <p className="mt-1 text-sm text-gray-500">
                        Track your registrations and payment status in one place.
                    </p>
                </div>
            </div>

            <ErrorBanner message={error} />

            <RegistrationHistory
                splitRegistrations={splitRegistrations}
                isWorkshopEnded={isWorkshopEnded}
                onBrowseWorkshops={handleBrowseWorkshops}
                onOpenPaymentModal={openPaymentModal}
                onCancelPending={handleCancelPending}
                formatPrice={formatPrice}
                formatDateTime={formatDateTime}
                getQrImageUrl={getQrImageUrl}
                getStatusMeta={getStatusMeta}
                isHoldExpired={isHoldExpired}
            />

            {/* Pagination */}
            <PaginationControl
                currentPage={currentPage}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={(newPage) => {
                    setCurrentPage(newPage);
                    fetchData(newPage);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                itemLabel="registrations"
            />

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

export default RegistrationHistoryPage;
