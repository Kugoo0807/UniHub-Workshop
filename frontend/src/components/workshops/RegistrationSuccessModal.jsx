import React from 'react';

const RegistrationSuccessModal = ({ isOpen, qrCode, workshopTitle, onClose }) => {
  if (!isOpen) return null;

  const qrImageUrl = qrCode
    ? `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(qrCode)}`
    : null;

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl border border-gray-100 text-center">
        <div className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>

        <h2 className="text-2xl font-bold text-gray-900 mb-2">Registration Successful</h2>
        <p className="text-sm text-gray-600 mb-4">{workshopTitle}</p>

        <div className="mb-6 rounded-xl border border-gray-100 bg-gray-50 p-4">
          <p className="text-xs font-medium uppercase tracking-wide text-gray-500 mb-3">Your QR code</p>
          <div className="w-48 h-48 mx-auto bg-white border border-gray-200 rounded-xl flex items-center justify-center overflow-hidden shadow-sm">
            {qrImageUrl ? (
              <img
                src={qrImageUrl}
                alt={`QR code for ${workshopTitle}`}
                className="h-full w-full object-contain"
              />
            ) : (
              <p className="text-xs text-gray-400">QR code unavailable</p>
            )}
          </div>
          {qrCode && <p className="mt-3 break-all text-[11px] text-gray-400">{qrCode}</p>}
        </div>

        <p className="text-sm text-gray-600 mb-4">
          Save this QR code for check-in at the event. You will receive a confirmation email.
        </p>

        <button
          onClick={onClose}
          className="w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition"
        >
          Back
        </button>
      </div>
    </div>
  );
};

export default RegistrationSuccessModal;
