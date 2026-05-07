import React, { useEffect, useState } from 'react';

const PaymentModal = ({ isOpen, workshop, idempotencyKey, onClose, onPaymentSuccess, onPaymentError }) => {
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    setError('');
    setIsProcessing(false);
  }, [isOpen, workshop?.id]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsProcessing(true);

    try {
      await onPaymentSuccess();
    } catch (err) {
      setError(err.message || 'Payment processing failed');
      onPaymentError(err);
    } finally {
      setIsProcessing(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-60 flex items-center justify-center bg-black/50">
      <div className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-5 rounded-2xl bg-linear-to-r from-slate-900 via-slate-800 to-indigo-900 p-5 text-white shadow-inner">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.25em] text-slate-300">Secure Payment</p>
              <h2 className="mt-2 text-xl font-bold">Payment</h2>
              <p className="mt-1 text-sm text-slate-300">{workshop?.title}</p>
            </div>
            <div className="rounded-full border border-white/15 bg-white/10 px-3 py-1 text-xs font-semibold text-white/90">
              {idempotencyKey ? 'Live Demo' : 'Demo'}
            </div>
          </div>

          <div className="mt-6 rounded-2xl border border-white/10 bg-white/10 p-4 backdrop-blur-sm">
            <div className="flex items-center justify-between text-xs uppercase tracking-[0.2em] text-slate-300">
              <span>UniHub Payment</span>
              <span>Auto-confirm</span>
            </div>
            <div className="mt-6 text-sm text-slate-100">
              No card details are required for this demo flow.
            </div>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded text-sm">
            {error}
          </div>
        )}

        <div className="mb-4 rounded-lg bg-gray-50 p-4">
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Amount:</span>
            <span className="font-semibold text-gray-900">
              {Number(workshop?.price || 0).toLocaleString('vi-VN')}đ
            </span>
          </div>
          <p className="mt-2 text-xs text-gray-500">
            Click payment to complete immediately. The system will confirm the transaction automatically.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isProcessing}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition disabled:opacity-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isProcessing}
              className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isProcessing ? 'Confirming...' : 'Pay Now'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PaymentModal;
