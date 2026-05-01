import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import axios from 'axios'; 

const QR_API = 'https://api.qrserver.com/v1/create-qr-code/';

const HomeStudent = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [isProcessing, setIsProcessing] = useState(false);
    const [registrations, setRegistrations] = useState([]);
    const [selectedReg, setSelectedReg] = useState(null);

    useEffect(() => {
        const fetchMyRegistrations = async () => {
            try {
                const token = localStorage.getItem('access_token'); 
                if (!token) return;

                const response = await axios.get('http://localhost:8080/api/v1/registrations/my-registrations', {
                    headers: { 
                        Authorization: `Bearer ${token}` 
                    }
                });
                setRegistrations(response.data);
            } catch (error) {
                console.error("Failed to fetch registrations:", error.response);
            }
        };
        
        if (user) fetchMyRegistrations();
    }, [user]);

    const handlePayment = async (registrationId, amount) => {
        setIsProcessing(true);
        try {
            const token = localStorage.getItem('access_token');
            const payload = {
                registrationId: registrationId,
                amount: amount,
                idempotencyKey: crypto.randomUUID()
            };

            const response = await axios.post('http://localhost:8080/api/v1/registrations/payments', payload, {
                headers: { 
                    Authorization: `Bearer ${token}` 
                }
            });

            if (response.data && response.data.status === 'COMPLETED') {
                alert('🎉 Payment successful!');
                setRegistrations(prev => prev.map(reg => 
                    reg.id === registrationId ? { ...reg, status: 'SUCCESS' } : reg
                ));
            }
        } catch (error) {
            console.error(error);
            alert('Payment failed.');
        } finally {
            setIsProcessing(false);
        }
    };

    const handleBrowseWorkshops = () => navigate('/workshops');
    const handleViewProfile = () => window.alert('Profile page is coming soon.');
    const handleCheckAttendance = () => window.alert('Attendance feature is coming soon.');

    return (
        <div className="min-h-screen bg-gray-50 p-6">
            <div className="mb-8 rounded-2xl bg-indigo-600 px-8 py-10 text-white shadow-md">
                <h1 className="text-3xl font-bold">
                    Welcome back, {user?.fullName ?? 'Student'} 👋
                </h1>
                <p className="mt-2 text-indigo-100">
                    Explore and register for upcoming workshops at UniHub.
                </p>
            </div>

            <div className="flex flex-col gap-6">
                <section className="rounded-2xl bg-white p-6 shadow-sm md:col-span-2">
                    <h2 className="mb-4 text-xl font-semibold text-gray-800">
                        My Registrations
                    </h2>
                    <div className="space-y-3">
                        {registrations.length === 0 ? (
                            <p className="text-gray-500 text-sm">You have not registered for any workshops yet.</p>
                        ) : (
                            registrations.map((reg) => {
                                const price = reg.workshop?.price || 0;
                                const isFree = price === 0;
                                const hasQr = !!reg.qrCode;
                                
                                return (
                                    <div
                                        key={reg.id}
                                        className={`flex items-center justify-between rounded-lg border p-3 transition ${
                                            hasQr
                                                ? 'border-indigo-200 bg-indigo-50/30 cursor-pointer hover:border-indigo-400 hover:shadow-sm'
                                                : 'border-gray-100'
                                        }`}
                                        onClick={() => hasQr && setSelectedReg(reg)}
                                    >
                                        <div className="flex flex-col">
                                            <span className="text-gray-700 font-medium">
                                                {reg.workshop?.title || 'Workshop'}
                                            </span>
                                            {!isFree && reg.status === 'PENDING' && (
                                                <span className="text-sm text-gray-500">
                                                    Fee: {price.toLocaleString('vi-VN')} VND
                                                </span>
                                            )}
                                            {isFree && (
                                                <span className="text-sm text-green-600 font-medium">Free</span>
                                            )}
                                            {hasQr && (
                                                <span className="text-xs text-indigo-500 mt-0.5">
                                                    📱 Tap to view QR code
                                                </span>
                                            )}
                                        </div>
                                        
                                        <div className="flex items-center gap-3">
                                            <span className={`rounded-full px-3 py-0.5 text-xs font-semibold ${
                                                reg.status === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
                                            }`}>
                                                {reg.status}
                                            </span>
                                            
                                            {reg.status === 'PENDING' && !isFree && (
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); handlePayment(reg.id, price); }}
                                                    disabled={isProcessing}
                                                    className="rounded-lg bg-emerald-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-600 transition disabled:opacity-50"
                                                >
                                                    {isProcessing ? 'Processing...' : 'Pay Now'}
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                </section>
                
                <section className="rounded-2xl bg-white p-6 shadow-sm md:col-span-2">
                    <h2 className="mb-4 text-xl font-semibold text-gray-800">Quick Actions</h2>
                    <div className="flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={handleBrowseWorkshops}
                            className="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition"
                        >
                            Browse All Workshops
                        </button>
                        <button
                            type="button"
                            onClick={handleViewProfile}
                            className="rounded-lg border border-indigo-600 px-5 py-2.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition"
                        >
                            View My Profile
                        </button>
                        <button
                            type="button"
                            onClick={handleCheckAttendance}
                            className="rounded-lg border border-gray-300 px-5 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition"
                        >
                            Check Attendance
                        </button>
                    </div>
                </section>
            </div>

            {/* QR Code Modal */}
            {selectedReg && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
                    onClick={() => setSelectedReg(null)}
                >
                    <div
                        className="w-full max-w-sm transform overflow-hidden rounded-2xl bg-white p-6 text-center shadow-xl"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-green-100">
                            <svg className="h-8 w-8 text-green-600" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
                            </svg>
                        </div>

                        <h3 className="mb-1 text-xl font-bold text-gray-900">
                            {selectedReg.workshop?.title || 'Workshop'}
                        </h3>
                        <p className="text-sm text-gray-500">
                            Scan the QR code below to check in
                        </p>

                        <div className="mt-4 flex flex-col items-center gap-2">
                            <div className="rounded-xl border-2 border-dashed border-indigo-200 bg-indigo-50/50 p-3">
                                <img
                                    src={`${QR_API}?size=200x200&data=${encodeURIComponent(selectedReg.qrCode)}`}
                                    alt="Registration QR Code"
                                    width={200}
                                    height={200}
                                    className="rounded-lg"
                                />
                            </div>
                        </div>

                        <div className="mt-6">
                            <button
                                type="button"
                                onClick={() => setSelectedReg(null)}
                                className="inline-flex w-full justify-center rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-indigo-700 transition"
                            >
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default HomeStudent;