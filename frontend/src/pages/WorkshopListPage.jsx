import React, { useState, useEffect } from 'react';
import workshopService from '../services/workshopService';
import workshopRegistrationService from '../services/workshopRegistrationService';
import PaymentModal from '../components/workshops/PaymentModal';
import RegistrationSuccessModal from '../components/workshops/RegistrationSuccessModal';

const formatDateTime = (dt) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' })
        + ' • ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
};

const formatPrice = (price) => {
    if (!price || Number(price) === 0) return 'Miễn phí';
    return Number(price).toLocaleString('vi-VN') + 'đ';
};

const WorkshopListPage = () => {
    const [workshops, setWorkshops] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedWorkshop, setSelectedWorkshop] = useState(null);
    const [isRegistering, setIsRegistering] = useState(false);
    const [showPaymentModal, setShowPaymentModal] = useState(false);
    const [showSuccessModal, setShowSuccessModal] = useState(false);
    const [registrationData, setRegistrationData] = useState(null);
    const [registrationError, setRegistrationError] = useState('');

    const fetchWorkshops = async () => {
        const data = await workshopService.getAll();
        setWorkshops(data);
    };

    const handleRegister = async () => {
        if (!selectedWorkshop) return;
        setIsRegistering(true);
        setRegistrationError('');

        try {
            const response = await workshopRegistrationService.register(selectedWorkshop.id);
            const dataWithTitle = {
                ...response,
                title: selectedWorkshop.title,
                workshopId: selectedWorkshop.id,
            };
            setRegistrationData(dataWithTitle);
            await fetchWorkshops();

            if (response.paidFlow) {
                // Show payment modal for paid workshops
                setShowPaymentModal(true);
            } else {
                // Show success modal for free workshops
                setShowSuccessModal(true);
            }
            setSelectedWorkshop(null);
        } catch (error) {
            const status = error.response?.status;
            if (status === 409) {
                setRegistrationError('You have already registered for this workshop!');
            } else if (status === 429) {
                setRegistrationError('Too many requests. Please try again later.');
            } else if (status === 402) {
                setRegistrationError('No seats left. Please choose another workshop.');
            } else {
                setRegistrationError(error.response?.data?.message || error.message || 'Registration failed');
            }
        } finally {
            setIsRegistering(false);
        }
    };

    const handlePaymentSuccess = async () => {
        if (!registrationData) return;
        setIsRegistering(true);
        setRegistrationError('');

        try {
            // Generate unique Idempotency-Key
            const idempotencyKey = registrationData.idempotencyKey || crypto.randomUUID();
            const paymentResponse = await workshopRegistrationService.processPayment(
                registrationData.workshopId,
                idempotencyKey
            );

            if (paymentResponse.success) {
                if (paymentResponse.qrCode) {
                    setRegistrationData((current) => ({
                        ...current,
                        qrCode: paymentResponse.qrCode,
                    }));
                }
                setShowPaymentModal(false);
                setShowSuccessModal(true);
            } else {
                setRegistrationError(`Thanh toán thất bại: ${paymentResponse.message}`);
                await fetchWorkshops();
            }
        } catch (error) {
            const status = error.response?.status;
            if (status === 409) {
                setRegistrationError('Payment is already being processed. Please wait or try again.');
            } else if (status === 429) {
                setRegistrationError('Too many payment requests. Please try again later.');
            } else {
                setRegistrationError(error.response?.data?.message || error.message || 'Payment failed');
            }
            await fetchWorkshops();
        } finally {
            setIsRegistering(false);
        }
    };

    useEffect(() => {
        const loadWorkshops = async () => {
            try {
                await fetchWorkshops();
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };
        loadWorkshops();
    }, []);

    const isRegistrationOpen = (w) => {
        try {
            const now = new Date();
            // Prefer explicit registration window if provided, otherwise fall back to start/end
            const start = w.registrationStartTime ? new Date(w.registrationStartTime) : new Date(w.startTime);
            const end = w.registrationEndTime ? new Date(w.registrationEndTime) : new Date(w.endTime);
            return now >= start && now <= end;
        } catch (e) {
            return true; // if parsing fails, default to allow registration and let backend enforce
        }
    };

    if (loading) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-gray-50">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50 p-6 mx-auto max-w-7xl">
            {/* Header */}
            <div className="mb-8 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-gray-900">Workshop Catalog</h1>
                    <p className="mt-1 text-sm text-gray-500">
                        Discover and register for exciting workshops at UniHub.
                    </p>
                </div>
            </div>

            {error && (
                <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>
            )}

            {workshops.length === 0 ? (
                <div className="rounded-2xl bg-white p-12 text-center shadow-sm border border-gray-100">
                    <p className="text-gray-400">No workshops are available yet. Please check back later.</p>
                </div>
            ) : (
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
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
                                    {w.remainingSlots === 0 ? 'Full' : `${w.remainingSlots} slots left`}
                                </span>
                                {!isRegistrationOpen(w) && (
                                    <span className="ml-2 rounded-full px-2 py-0.5 bg-gray-100 text-gray-600 font-semibold">Registration closed</span>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Workshop Detail Modal */}
            {selectedWorkshop && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => setSelectedWorkshop(null)}>
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
                                    {selectedWorkshop.remainingSlots} slots left
                                </span>
                            </div>
                        </div>

                        {selectedWorkshop.description && (
                            <div className="mt-4">
                                <h4 className="text-sm font-medium text-gray-500 mb-1">Description:</h4>
                                <p className="text-sm text-gray-700 whitespace-pre-wrap">{selectedWorkshop.description}</p>
                            </div>
                        )}

                        {registrationError && (
                            <div className="mt-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded text-sm">
                                {registrationError}
                            </div>
                        )}

                            <div className="mt-6 flex justify-end gap-3">
                            <button
                                onClick={() => setSelectedWorkshop(null)}
                                disabled={isRegistering}
                                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition disabled:opacity-50"
                            >
                                Close
                            </button>
                            <button
                                onClick={handleRegister}
                                disabled={selectedWorkshop.remainingSlots === 0 || isRegistering || !isRegistrationOpen(selectedWorkshop)}
                                className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                            >
                                {isRegistering ? 'Processing...' : !isRegistrationOpen(selectedWorkshop) ? 'Registration closed' : selectedWorkshop.remainingSlots === 0 ? 'Full' : 'Register'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Payment Modal */}
            {registrationData && (
                <PaymentModal
                    isOpen={showPaymentModal}
                    workshop={{
                        title: registrationData.title || 'Workshop',
                        price: registrationData.amount,
                    }}
                    idempotencyKey={registrationData.idempotencyKey}
                    onClose={() => {
                        setShowPaymentModal(false);
                        setRegistrationData(null);
                        setRegistrationError('');
                    }}
                    onPaymentSuccess={handlePaymentSuccess}
                    onPaymentError={(error) => {
                        setRegistrationError(error.message || 'Payment error');
                    }}
                />
            )}

            {/* Registration Success Modal */}
            {registrationData && (
                <RegistrationSuccessModal
                    isOpen={showSuccessModal}
                    qrCode={registrationData.qrCode}
                    workshopTitle={registrationData.title || 'Workshop'}
                    onClose={() => {
                        setShowSuccessModal(false);
                        setRegistrationData(null);
                        setRegistrationError('');
                    }}
                />
            )}
        </div>
    );
};

export default WorkshopListPage;
