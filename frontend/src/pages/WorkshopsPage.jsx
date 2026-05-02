// src/pages/WorkshopsPage.jsx
import React, { useEffect, useState } from 'react';
import registrationService from '../services/registrationService';
import WorkshopCard from '../components/WorkshopCard';
import PaymentModal from '../components/PaymentModal';
import SuccessModal from '../components/SuccessModal';
import { handleApiError } from '../utils/errorHandler';

const WorkshopsPage = () => {
    const [workshops, setWorkshops] = useState([]);
    const [error, setError] = useState('');

    // Payment modal state
    const [paymentData, setPaymentData] = useState({
        isOpen: false,
        registrationId: null,
        price: 0
    });
    const [isProcessing, setIsProcessing] = useState(false);

    const [successData, setSuccessData] = useState({
        isOpen: false,
        title: '',
        message: '',
        qrCode: null
    });

    useEffect(() => {
        load();

        const refreshInterval = window.setInterval(() => {
            load();
        }, 15000);

        const onFocus = () => load();
        window.addEventListener('focus', onFocus);

        return () => {
            window.clearInterval(refreshInterval);
            window.removeEventListener('focus', onFocus);
        };
    }, []);

    const load = async () => {
        try {
            const data = await registrationService.listWorkshops();
            setWorkshops(data);
        } catch (err) {
            setError(handleApiError(err));
        }
    };

    const onRegisterFree = async (id) => {
        setError('');
        try {
            const resp = await registrationService.registerFree(id);

            setSuccessData({
                isOpen: true,
                title: 'Registration Successful! 🎉',
                message: 'Please save the QR code below to check in.',
                qrCode: resp.qrCode
            });
            
            load();
        } catch (err) {
            setError(handleApiError(err));
        }
    };

    const onRegisterPaid = async (id, price) => {
        setError('');
        try {
            const resp = await registrationService.initiatePaid(id);
            setPaymentData({
                isOpen: true,
                registrationId: resp.registrationId,
                price: price
            });
        } catch (err) {
            setError(handleApiError(err));
        }
    };

    const handleConfirmPayment = async () => {
        setIsProcessing(true);
        setError('');
        try {
            const payload = {
                registrationId: paymentData.registrationId,
                amount: paymentData.price,
                idempotencyKey: window.crypto.randomUUID(),
            };
            const payResp = await registrationService.processPayment(payload);

            setSuccessData({
                isOpen: true,
                title: 'Payment Successful! 🎉',
                message: `Transaction ID: ${payResp.transactionId || 'OK'}`
            });
            
            handleCloseModal();
            load(); 
        } catch (err) {
            setError(handleApiError(err));
            handleCloseModal(); 
        } finally {
            setIsProcessing(false);
        }
    };

    const handleCloseModal = () => {
        if (!isProcessing) {
            setPaymentData({ isOpen: false, registrationId: null, price: 0 });
        }
    };

    return (
        <div className="p-6 min-h-screen bg-gray-50">
            <h1 className="mb-4 text-2xl font-bold text-gray-800">Discover Workshops</h1>
            {error && <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">{error}</div>}
            
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                {workshops.map((w) => (
                    <WorkshopCard 
                        key={w.id} 
                        workshop={w} 
                        onRegisterFree={onRegisterFree} 
                        onRegisterPaid={onRegisterPaid} 
                        onRefresh={load}
                    />
                ))}
            </div>

            <PaymentModal 
                isOpen={paymentData.isOpen}
                price={paymentData.price}
                isProcessing={isProcessing}
                onClose={handleCloseModal}
                onConfirm={handleConfirmPayment}
            />

            <SuccessModal 
                isOpen={successData.isOpen}
                title={successData.title}
                message={successData.message}
                qrCode={successData.qrCode}
                onClose={() => setSuccessData({ ...successData, isOpen: false })}
            />
        </div>
    );
};

export default WorkshopsPage;