import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import workshopService from '../services/workshopService';
import workshopRegistrationService from '../services/workshopRegistrationService';
import PaymentModal from '../components/workshops/PaymentModal';
import RegistrationSuccessModal from '../components/workshops/RegistrationSuccessModal';
import PaginationControl from '../components/common/PaginationControl';

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

const getSeatMapAlt = (workshop) => {
    const roomName = workshop?.roomName ? ` for ${workshop.roomName}` : '';
    return `Seat map${roomName}`;
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
    } catch {
        return true; // if parsing fails, default to allow registration and let backend enforce
    }
};

const isRegistrationClosed = (w) => {
    try {        
        const now = new Date();
        const end = w.registrationEndTime ? new Date(w.registrationEndTime) : new Date(w.endTime);
        return now > end;
    } catch {
        return false; // if parsing fails, default to not closed and let backend enforce
    }
};

const isRegistrationStarted = (w) => {
    try {
        const now = new Date();
        const start = w.registrationStartTime ? new Date(w.registrationStartTime) : null;
        return start && now >= start;
    } catch {
        return false; // if parsing fails, default to not started and let backend enforce
    }
};

const getRegisterButtonLabel = (workshop, isRegistering) => {
    if (isRegistering) return 'Processing...';
    if (workshop?.userRegistrationStatus === 'SUCCESS') return 'Registered';
    if (workshop?.userRegistrationStatus === 'PENDING') return 'Payment pending';
    if (!isRegistrationStarted(workshop)) return 'Registration Not Started';
    if (isRegistrationClosed(workshop)) return 'Registration closed';
    if (workshop?.remainingSlots === 0) return 'Full';
    return 'Register';
};

const PAGE_SIZE = 12;

const WorkshopListPage = () => {
    const navigate = useNavigate();
    const [workshops, setWorkshops] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedWorkshop, setSelectedWorkshop] = useState(null);
    const [showSeatMap, setShowSeatMap] = useState(false);
    const [seatMapLoading, setSeatMapLoading] = useState(false);
    const [seatMapError, setSeatMapError] = useState('');

    const resetSeatMapState = () => {
        setShowSeatMap(false);
        setSeatMapLoading(false);
        setSeatMapError('');
    };

    const handleSelectWorkshop = (workshop) => {
        resetSeatMapState();
        setSelectedWorkshop(workshop);
    };

    const handleCloseWorkshop = () => {
        resetSeatMapState();
        setSelectedWorkshop(null);
    };
    const [isRegistering, setIsRegistering] = useState(false);
    const [showPaymentModal, setShowPaymentModal] = useState(false);
    const [showSuccessModal, setShowSuccessModal] = useState(false);
    const [registrationData, setRegistrationData] = useState(null);
    const [registrationError, setRegistrationError] = useState('');

    // Pagination state
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const fetchWorkshops = useCallback(async (page = 0) => {
        const data = await workshopService.getAll(page, PAGE_SIZE);
        setWorkshops(data.content || []);
        setCurrentPage(data.page);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
    }, []);

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
            await fetchWorkshops(currentPage);

            if (response.paidFlow) {
                // Show payment modal for paid workshops
                setShowPaymentModal(true);
            } else {
                // Show success modal for free workshops
                setShowSuccessModal(true);
            }
            handleCloseWorkshop();
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
                await fetchWorkshops(currentPage);
                setShowPaymentModal(false);
                setShowSuccessModal(true);
            } else {
                setRegistrationError(`Payment failed: ${paymentResponse.message}`);
                await fetchWorkshops(currentPage);
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
            await fetchWorkshops(currentPage);
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
    }, [fetchWorkshops]);

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
                                onClick={() => handleSelectWorkshop(w)}
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

                                <div className="mt-3 flex items-center justify-between gap-3 text-xs text-gray-500">
                                    <span className="font-semibold text-gray-700">
                                        {w.roomName || 'Room TBA'}
                                    </span>
                                    {w.layoutMapUrl ? (
                                        <span className="rounded-md bg-sky-50 px-2 py-1 font-semibold text-sky-700">
                                            Seat map available
                                        </span>
                                    ) : (
                                        <span className="rounded-md bg-gray-100 px-2 py-1 font-semibold text-gray-500">
                                            No seat map available
                                        </span>
                                    )}
                                </div>

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
                                        
                                        {!isRegistrationStarted(w) && (
                                            <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-md border border-gray-200 bg-gray-50 text-gray-400">
                                                UPCOMING
                                            </span>
                                        )}

                                        {isRegistrationClosed(w) && (
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

            {/* Pagination */}
            <PaginationControl
                currentPage={currentPage}
                totalPages={totalPages}
                totalElements={totalElements}
                pageSize={PAGE_SIZE}
                onPageChange={(newPage) => {
                    setCurrentPage(newPage);
                    fetchWorkshops(newPage);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                }}
                itemLabel="workshops"
            />

            {/* Workshop Detail Modal */}
            {selectedWorkshop && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={handleCloseWorkshop}>
                    <div
                        className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <h2 className="text-xl font-bold text-gray-900">{selectedWorkshop.title}</h2>

                        <div className="mt-4 space-y-3">
                            <div className="flex items-start gap-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Time:</span>
                                <span className="text-sm text-gray-700 font-semibold">{formatDateTime(selectedWorkshop.startTime)}</span>
                            </div>
                            <div className="flex items-start gap-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Room:</span>
                                <span className="text-sm text-gray-700">
                                    {selectedWorkshop.roomName || 'Room TBA'}
                                    {selectedWorkshop.roomCapacity ? ` (${selectedWorkshop.roomCapacity} seats)` : ''}
                                </span>
                            </div>
                            <div className="flex items-start gap-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Ends:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.endTime)}</span>
                            </div>
                            
                            {/* Bổ sung Registration Starts */}
                            <div className="flex items-start gap-2 border-t border-gray-50 pt-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Registration starts:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.registrationStartTime)}</span>
                            </div>
                            
                            <div className="flex items-start gap-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Registration ends:</span>
                                <span className="text-sm text-gray-700">{formatDateTime(selectedWorkshop.registrationEndTime)}</span>
                            </div>
                            
                            <div className="flex items-start gap-2 border-t border-gray-50 pt-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Price:</span>
                                <span className={`text-sm font-bold ${
                                    Number(selectedWorkshop.price) === 0 ? 'text-emerald-600' : 'text-amber-600'
                                }`}>
                                    {formatPrice(selectedWorkshop.price)}
                                </span>
                            </div>
                            <div className="flex items-start gap-2">
                                <span className="text-sm font-medium text-gray-500 w-36 shrink-0">Seats:</span>
                                <span className="text-sm text-gray-700">
                                    {selectedWorkshop.remainingSlots} seats left
                                </span>
                            </div>
                        </div>

                        <div className="mt-4">
                            <h4 className="text-sm font-medium text-gray-500 mb-1">Description:</h4>
                            <p className="text-sm text-gray-700 whitespace-pre-wrap">{formatDescription(selectedWorkshop.description)}</p>
                        </div>

                        {/* Seat map */}
                        <div className="mt-4 border-t border-gray-50 pt-3">
                            <h4 className="text-sm font-medium text-gray-500 mb-2">
                                Seat map: {!selectedWorkshop.layoutMapUrl && <span className="font-normal italic">Unavailable</span>}
                            </h4>

                            {selectedWorkshop.layoutMapUrl && (
                                <>
                                    <div className="flex items-center gap-2 mb-2">
                                        <button
                                            onClick={() => {
                                                setShowSeatMap((s) => !s);
                                                if (!showSeatMap) {
                                                    setSeatMapLoading(true);
                                                    setSeatMapError('');
                                                }
                                            }}
                                            className="rounded-md border px-3 py-1 text-sm bg-white hover:bg-gray-50"
                                        >
                                            {showSeatMap ? 'Hide seat map' : 'View seat map'}
                                        </button>
                                    </div>

                                    {showSeatMap && (
                                        <div className="w-full overflow-hidden rounded-lg border border-gray-100 bg-gray-50 p-3">
                                            {seatMapLoading && (
                                                <div className="mb-2 text-sm text-gray-500">Loading seat map...</div>
                                            )}
                                            {seatMapError && (
                                                <div className="mb-2 text-sm text-red-600">Failed to load seat map</div>
                                            )}
                                            <img
                                                src={selectedWorkshop.layoutMapUrl}
                                                alt={getSeatMapAlt(selectedWorkshop)}
                                                loading="lazy"
                                                className="max-h-[55vh] w-full rounded object-contain"
                                                onLoad={() => setSeatMapLoading(false)}
                                                onError={() => { setSeatMapLoading(false); setSeatMapError('error'); }}
                                            />
                                        </div>
                                    )}
                                </>
                            )}
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
                                onClick={handleCloseWorkshop}
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
                                await fetchWorkshops(currentPage);
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
