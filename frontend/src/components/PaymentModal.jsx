import React from 'react';

const PaymentModal = ({ isOpen, price, isProcessing, onClose, onConfirm }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm transition-opacity">
            <div className="w-full max-w-md transform overflow-hidden rounded-2xl bg-white p-6 text-left align-middle shadow-xl transition-all">
                <h3 className="text-xl font-bold leading-6 text-gray-900">
                    Xác nhận thanh toán
                </h3>
                
                <div className="mt-2">
                    <p className="text-sm text-gray-500">
                        Bạn đang tiến hành thanh toán cho Workshop này. Số tiền cần thanh toán là: 
                        <span className="ml-1 text-lg font-semibold text-indigo-600">
                            {price?.toLocaleString('vi-VN')} VND
                        </span>
                    </p>
                </div>

                <div className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={isProcessing}
                        className="inline-flex justify-center rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 transition"
                    >
                        Hủy bỏ
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        disabled={isProcessing}
                        className="inline-flex justify-center rounded-lg border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 transition"
                    >
                        {isProcessing ? 'Đang xử lý...' : 'Thanh toán ngay'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default PaymentModal;