import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import telegramService from '../services/telegramService';

const ProfilePage = () => {
    const { user, refreshProfile } = useAuth();
    const navigate = useNavigate();
    const [message, setMessage] = useState('');
    const [errorMessage, setErrorMessage] = useState('');
    const [isRefreshing, setIsRefreshing] = useState(false);

    const isTelegramLinked = Boolean(user?.chatId);

    const handleConnectTelegram = () => {
        setErrorMessage('');
        setMessage('');
        try {
            const link = telegramService.openTelegramDeepLink(user?.id);
            setMessage(`Opened Telegram. If it did not open, use this link: ${link}`);
        } catch (error) {
            setErrorMessage(error.message || 'Unable to open Telegram.');
        }
    };

    const handleRefreshStatus = async () => {
        setErrorMessage('');
        setMessage('');
        setIsRefreshing(true);
        try {
            await refreshProfile();
            setMessage('Profile refreshed.');
        } catch (error) {
            setErrorMessage(error.message || 'Failed to refresh profile.');
        } finally {
            setIsRefreshing(false);
        }
    };

    return (
        <div className="min-h-screen bg-gray-50 p-4 sm:p-6">
            <div className="max-w-2xl mx-auto">
                {/* Header */}
                <div className="flex items-center justify-between mb-8">
                    <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">My Profile</h1>
                    <button
                        onClick={() => navigate(-1)}
                        className="rounded-lg px-4 py-2 text-gray-600 hover:bg-gray-100 transition"
                    >
                        ← Back
                    </button>
                </div>

                {/* Profile Card */}
                <div className="rounded-2xl bg-white p-6 sm:p-8 shadow-sm border border-gray-100">
                    {/* Profile Header */}
                    <div className="flex items-center gap-6 mb-8 pb-8 border-b border-gray-200">
                        <div className="h-20 w-20 rounded-full bg-linear-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white text-2xl font-bold">
                            {user?.fullName?.charAt(0).toUpperCase()}
                        </div>
                        <div className="flex-1">
                            <h2 className="text-xl sm:text-2xl font-bold text-gray-900">{user?.fullName}</h2>
                            <p className="text-gray-500 text-sm sm:text-base">{user?.email}</p>
                            <span className="inline-block mt-2 px-3 py-1 bg-indigo-100 text-indigo-700 rounded-full text-xs font-semibold">
                                {user?.role || 'Student'}
                            </span>
                        </div>
                    </div>

                    {/* Info Fields */}
                    <div className="space-y-5">
                        {/* Full Name */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Full Name
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.fullName}</p>
                        </div>

                        {/* Email */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Email Address
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.email}</p>
                        </div>

                        {/* Student Code */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Student Code (MSSV)
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.studentCode || 'N/A'}</p>
                        </div>

                        {/* Phone Number */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Phone Number
                            </label>
                            <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{user?.phoneNumber || 'Not provided'}</p>
                        </div>

                        {/* Account Status */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Account Status
                            </label>
                            <p className="px-4 py-2 bg-green-50 rounded-lg text-green-700 font-medium">
                                Active
                            </p>
                        </div>
                    </div>
                </div>

                <div className="mt-6 rounded-2xl bg-white p-6 sm:p-8 shadow-sm border border-gray-100">
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <h3 className="text-lg font-semibold text-gray-900">Telegram Connection</h3>
                            <p className="text-sm text-gray-600 mt-1">
                                Link your Telegram account to receive instant notifications.
                            </p>
                            <p className={`mt-3 inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${
                                isTelegramLinked
                                    ? 'bg-emerald-100 text-emerald-700'
                                    : 'bg-amber-100 text-amber-700'
                            }`}>
                                {isTelegramLinked ? 'Connected' : 'Not connected'}
                            </p>
                        </div>
                        <div className="flex flex-col gap-2 sm:items-end">
                            <button
                                type="button"
                                onClick={handleConnectTelegram}
                                className="rounded-lg bg-blue-600 px-4 py-2 text-white font-semibold hover:bg-blue-700 transition"
                            >
                                Connect Telegram
                            </button>
                            <button
                                type="button"
                                onClick={handleRefreshStatus}
                                className="rounded-lg px-4 py-2 text-gray-600 hover:bg-gray-100 transition"
                                disabled={isRefreshing}
                            >
                                {isRefreshing ? 'Refreshing...' : 'Refresh Status'}
                            </button>
                        </div>
                    </div>

                    {(message || errorMessage) && (
                        <div className={`mt-4 rounded-lg px-4 py-3 text-sm ${
                            errorMessage ? 'bg-red-50 text-red-700' : 'bg-blue-50 text-blue-700'
                        }`}>
                            {errorMessage || message}
                        </div>
                    )}
                </div>

                {/* Additional Info */}
                <div className="mt-6 rounded-2xl bg-blue-50 border border-blue-200 p-4 sm:p-6">
                    <h3 className="font-semibold text-blue-900 mb-2">Tip</h3>
                    <p className="text-sm text-blue-800">
                        Your account is fully linked with your HCMUS student profile. Some information like Email and Student Code cannot be changed for security reasons.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;
