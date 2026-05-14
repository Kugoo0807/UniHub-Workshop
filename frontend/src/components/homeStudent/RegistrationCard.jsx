import React from 'react';

const RegistrationCard = ({
    registration,
    muted = false,
    showActions = false,
    getStatusMeta,
    formatPrice,
    formatDateTime,
    getQrImageUrl,
    isHoldExpired,
    isWorkshopEnded,
    onOpenPaymentModal,
    onCancelPending,
}) => {
    const statusMeta = getStatusMeta(registration.status);
    const holdExpired = isHoldExpired(registration);
    const ended = isWorkshopEnded(registration);
    const canPay = registration.status === 'PENDING' && registration.paymentIdempotencyKey && !holdExpired;
    const canCancel = registration.status === 'PENDING' && !holdExpired;
    const isCancelledOrFailed = registration.status === 'CANCELLED' || registration.status === 'FAILED';
    const statusClassName = isCancelledOrFailed
        ? 'border-red-200 bg-red-50/70'
        : registration.status === 'SUCCESS'
            ? 'border-emerald-200 bg-emerald-50/60'
            : registration.status === 'PENDING'
                ? 'border-amber-200 bg-amber-50/60'
                : 'border-gray-200 bg-white';

    return (
        <article
            className={`rounded-2xl border p-4 shadow-sm ${statusClassName} ${
                muted || isCancelledOrFailed ? 'opacity-70' : ''
            }`}
        >
            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                            <h3 className="text-base font-semibold text-gray-900">{registration.title}</h3>
                        </div>
                        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${statusMeta.className}`}>
                            {statusMeta.label}
                        </span>
                    </div>

                    <div className="mt-4 grid gap-2 text-sm text-gray-600 sm:grid-cols-2">
                        <p><span className="font-medium text-gray-500">Price:</span> {formatPrice(registration.price)}</p>
                        <p><span className="font-medium text-gray-500">Registered at:</span> {formatDateTime(registration.createdAt)}</p>
                        <div className="sm:col-span-2 mt-2">
                            <span className="font-medium text-gray-500 block mb-2">Workshop date:</span>
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="px-3 py-1 bg-gray-100 rounded-md border border-gray-200 shadow-sm">
                                    {formatDateTime(registration.startTime)}
                                </span>
                                <span className="text-gray-400">to</span>
                                <span className="px-3 py-1 bg-gray-100 rounded-md border border-gray-200 shadow-sm">
                                    {formatDateTime(registration.endTime)}
                                </span>
                            </div>
                        </div>
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
                                    onClick={() => onOpenPaymentModal(registration)}
                                    disabled={!canPay}
                                    className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                    Pay now
                                </button>
                                <button
                                    onClick={() => onCancelPending(registration.workshopId)}
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

export default RegistrationCard;
