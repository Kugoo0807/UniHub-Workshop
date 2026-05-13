import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import workshopService from '../services/workshopService';
import workshopRegistrationService from '../services/workshopRegistrationService';
import PaymentModal from '../components/workshops/PaymentModal';
import RegistrationSuccessModal from '../components/workshops/RegistrationSuccessModal';

const formatDateTime = (dt) => {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleDateString('en-GB', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' })
        + ' • ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
};

const formatPrice = (price) => {
    if (!price || Number(price) === 0) return 'Free';
    return Number(price).toLocaleString('vi-VN') + 'đ';
};

const formatDescription = (description) => {
    const text = typeof description === 'string' ? description.trim() : '';
    return text || 'This workshop does not have a description yet.';
};

const getRegistrationState = (workshop) => {
    if (!workshop?.userRegistrationStatus) return null;

    if (workshop.userRegistrationStatus === 'SUCCESS') {
        return { label: 'Registered', tone: 'success' };
    }

    if (workshop.userRegistrationStatus === 'PENDING') {
        return { label: 'Payment pending', tone: 'pending' };
    }

    return { label: 'Registered', tone: 'info' };
};

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

const getRegisterButtonLabel = (workshop, isRegistering) => {
    if (isRegistering) return 'Processing...';
    if (workshop?.userRegistrationStatus === 'SUCCESS') return 'Registered';
    if (workshop?.userRegistrationStatus === 'PENDING') return 'Payment pending';
    if (!isRegistrationOpen(workshop)) return 'Registration closed';
    if (workshop?.remainingSlots === 0) return 'Full';
    return 'Register';
};

const WorkshopListPage = () => {
    const navigate = useNavigate();
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
                await fetchWorkshops();
                setShowPaymentModal(false);
                setShowSuccessModal(true);
            } else {
                setRegistrationError(`Payment failed: ${paymentResponse.message}`);
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
                <div className="hidden sm:flex items-center gap-2 mt-1">
                    <div className="w-8 h-1.5 bg-emerald-500"></div> 
                    
                    <span className="text-sm font-semibold text-emerald-900 uppercase tracking-wider">
                        | Registered Workshop
                    </span>
                </div>
                <button
                    onClick={() => navigate('/registration-history')}
                    className="rounded-lg border border-indigo-600 px-4 py-2 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                >
                    Registration history
                </button>
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
                    {workshops.map((w) => {
                        const registrationState = getRegistrationState(w);

                        return (
                            <div
                                key={w.id}
                                className={`group cursor-pointer rounded-2xl bg-white p-5 shadow-sm border transition-all hover:shadow-md hover:border-indigo-200 ${
                                    registrationState?.tone === 'success'
                                        ? 'border-emerald-300 ring-2 ring-emerald-200 bg-emerald-50/30 shadow-emerald-100/60'
                                        : registrationState?.tone === 'pending'
                                            ? 'border-amber-300 ring-2 ring-amber-200 bg-amber-50/30 shadow-amber-100/60'
                                            : 'border-gray-100'
                                }`}
                                onClick={() => setSelectedWorkshop(w)}
                            >
                                <div className="flex items-start justify-between">
                                    <h3 className="text-base font-semibold text-gray-900 group-hover:text-indigo-600 transition">
                                        {w.title}
                                    </h3>
                                    <div className="ml-2 flex flex-col items-end gap-1">
                                        <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                                            Number(w.price) === 0
                                                ? 'bg-emerald-100 text-emerald-700'
                                                : 'bg-amber-100 text-amber-700'
                                        }`}>
                                            {formatPrice(w.price)}
                                        </span>
                                    </div>
                                </div>

                                <p className="mt-2 text-sm text-gray-500 line-clamp-2">{formatDescription(w.description)}</p>

                                <div className="mt-2 pt-3 border-t border-gray-50 flex flex-col gap-2.5">
                                    <div className="flex flex-col gap-3">
                                        <div className="flex flex-col">
                                            <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-0.5">Workshop Date</span>
                                            <span className="text-[13px] font-semibold text-gray-800 leading-tight">
                                                {formatDateTime(w.startTime)}
                                            </span>
                                        </div>
                                        
                                        <div className="flex flex-col">
                                            <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest mb-0.5">Registration End</span>
                                            <span className="text-[13px] text-gray-500 font-medium leading-tight">
                                                {formatDateTime(w.registrationEndTime)}
                                            </span>
                                        </div>
                                    </div>

                                    <div className="flex items-center gap-2 pt-1">
                                        <span className={`text-[10px] font-extrabold px-2 py-0.5 rounded-md border ${
                                            w.remainingSlots === 0 
                                                ? 'bg-red-50 text-red-600 border-red-100' 
                                                : 'bg-indigo-50 text-indigo-600 border-indigo-100'
                                        }`}>
                                            {w.remainingSlots === 0 ? 'FULL' : `${w.remainingSlots} SEATS LEFT`}
                                        </span>
                                        
                                        {!isRegistrationOpen(w) && (
                                            <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-md border border-gray-200 bg-gray-50 text-gray-400">
                                                CLOSED
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    )}
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
                                <span className="text-sm font-medium text-gray-500 w-24">Registration ends:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.registrationEndTime)}</span>
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
                                    {selectedWorkshop.remainingSlots} seats left
                                </span>
                            </div>
                        </div>

                        <div className="mt-4">
                            <h4 className="text-sm font-medium text-gray-500 mb-1">Description:</h4>
                            <p className="text-sm text-gray-700 whitespace-pre-wrap">{formatDescription(selectedWorkshop.description)}</p>
                        </div>

                        {selectedWorkshop.userRegistrationStatus && (
                            <div className="mt-4">
                                <h4 className="text-sm font-medium text-gray-500 mb-1">Registration status:</h4>
                                <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                                    selectedWorkshop.userRegistrationStatus === 'SUCCESS'
                                        ? 'bg-emerald-100 text-emerald-700'
                                        : 'bg-amber-100 text-amber-700'
                                }`}>
                                    {selectedWorkshop.userRegistrationStatus === 'SUCCESS' ? 'Registered' : 'Payment pending'}
                                </span>
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
                                disabled={
                                    selectedWorkshop.remainingSlots === 0
                                    || isRegistering
                                    || !isRegistrationOpen(selectedWorkshop)
                                    || Boolean(selectedWorkshop.userRegistrationStatus)
                                }
                                className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition"
                            >
                                {getRegisterButtonLabel(selectedWorkshop, isRegistering)}
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
                    onClose={async () => {
                        if (registrationData?.workshopId) {
                            try {
                                await workshopRegistrationService.cancelRegistration(registrationData.workshopId);
                                await fetchWorkshops();
                            } catch (e) {
                                console.error('Failed to cancel registration:', e);
                            }
                        }
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
