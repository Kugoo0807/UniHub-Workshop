import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import axios from 'axios'; 

const HomeStudent = () => {
    const { user } = useAuth();
    const navigate = useNavigate();
    const [isProcessing, setIsProcessing] = useState(false);
    const [registrations, setRegistrations] = useState([]);

    // 1. Sửa hàm fetch dữ liệu
    useEffect(() => {
        const fetchMyRegistrations = async () => {
            try {
                // Lấy trực tiếp từ localStorage bằng key 'access_token'
                const token = localStorage.getItem('access_token'); 
                
                if (!token) return;

                const response = await axios.get('http://localhost:8080/api/v1/registrations/my-registrations', {
                    headers: { 
                        // Đảm bảo gửi đúng định dạng Bearer + token
                        Authorization: `Bearer ${token}` 
                    }
                });
                setRegistrations(response.data);
            } catch (error) {
                console.error("Lỗi fetch data:", error.response);
            }
        };
        
        if (user) fetchMyRegistrations();
    }, [user]);

    // 2. Sửa hàm thanh toán
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
                alert('🎉 Thanh toán thành công!');
                setRegistrations(prev => prev.map(reg => 
                    reg.id === registrationId ? { ...reg, status: 'SUCCESS' } : reg
                ));
            }
        } catch (error) {
            console.error(error);
            alert('Lỗi thanh toán.');
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
                            <p className="text-gray-500 text-sm">Bạn chưa đăng ký workshop nào.</p>
                        ) : (
                            registrations.map((reg) => {
                                // BƯỚC 2: SỬA LẠI LOGIC LẤY GIÁ TIỀN TỪ WORKSHOP
                                const price = reg.workshop?.price || 0;
                                const isFree = price === 0;
                                
                                return (
                                    <div key={reg.id} className="flex items-center justify-between rounded-lg border border-gray-100 p-3">
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
                                        </div>
                                        
                                        <div className="flex items-center gap-3">
                                            <span className={`rounded-full px-3 py-0.5 text-xs font-semibold ${
                                                reg.status === 'SUCCESS' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
                                            }`}>
                                                {reg.status}
                                            </span>
                                            
                                            {reg.status === 'PENDING' && !isFree && (
                                                <button
                                                    onClick={() => handlePayment(reg.id, price)}
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
        </div>
    );
};

export default HomeStudent;