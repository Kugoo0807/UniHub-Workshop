import React from 'react';
import RegistrationCard from './RegistrationCard';

const RegistrationHistory = ({
    splitRegistrations,
    isWorkshopEnded,
    onBrowseWorkshops,
    onOpenPaymentModal,
    onCancelPending,
    formatPrice,
    formatDateTime,
    getQrImageUrl,
    getStatusMeta,
    isHoldExpired,
}) => {
    return (
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
                    onClick={onBrowseWorkshops}
                    className="rounded-lg border border-indigo-600 px-4 py-2 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                >
                    Browse workshops
                </button>
            </div>

            {splitRegistrations.pending.length === 0 && splitRegistrations.history.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-gray-200 bg-gray-50 px-6 py-10 text-center">
                    <p className="text-sm text-gray-500 mb-3">No registrations yet</p>
                    <button
                        onClick={onBrowseWorkshops}
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
                                        getStatusMeta={getStatusMeta}
                                        formatPrice={formatPrice}
                                        formatDateTime={formatDateTime}
                                        getQrImageUrl={getQrImageUrl}
                                        isHoldExpired={isHoldExpired}
                                        isWorkshopEnded={isWorkshopEnded}
                                        onOpenPaymentModal={onOpenPaymentModal}
                                        onCancelPending={onCancelPending}
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
                                        getStatusMeta={getStatusMeta}
                                        formatPrice={formatPrice}
                                        formatDateTime={formatDateTime}
                                        getQrImageUrl={getQrImageUrl}
                                        isHoldExpired={isHoldExpired}
                                        isWorkshopEnded={isWorkshopEnded}
                                        onOpenPaymentModal={onOpenPaymentModal}
                                        onCancelPending={onCancelPending}
                                    />
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </section>
    );
};

export default RegistrationHistory;
