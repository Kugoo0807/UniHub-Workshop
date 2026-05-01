// src/components/SuccessModal.jsx
import React from 'react';

const SuccessModal = ({ isOpen, title, message, onClose }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm transition-opacity">
            <div className="w-full max-w-sm transform overflow-hidden rounded-2xl bg-white p-6 text-center align-middle shadow-xl transition-all">
                {/* Icon Checkmark Success */}
                <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-green-100">
                    <svg className="h-8 w-8 text-green-600" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                    </svg>
                </div>
                
                <h3 className="mb-2 text-xl font-bold leading-6 text-gray-900">
                    {title}
                </h3>
                
                <div className="mt-2">
                    <p className="text-sm text-gray-500 break-all">
                        {message}
                    </p>
                </div>

                <div className="mt-6">
                    <button
                        type="button"
                        onClick={onClose}
                        className="inline-flex w-full justify-center rounded-lg border border-transparent bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition"
                    >
                        Đóng
                    </button>
                </div>
            </div>
        </div>
    );
};

export default SuccessModal;