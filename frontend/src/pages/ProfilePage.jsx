import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProfilePage = () => {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    
    const [isEditing, setIsEditing] = useState(false);
    const [formData, setFormData] = useState({
        fullName: user?.fullName || '',
        email: user?.email || '',
        studentCode: user?.studentCode || '',
        phoneNumber: user?.phoneNumber || '',
    });
    const [isSaving, setIsSaving] = useState(false);
    const [successMessage, setSuccessMessage] = useState('');
    const [errorMessage, setErrorMessage] = useState('');

    useEffect(() => {
        if (!user) {
            return;
        }

        setFormData({
            fullName: user.fullName || '',
            email: user.email || '',
            studentCode: user.studentCode || '',
            phoneNumber: user.phoneNumber || '',
        });
    }, [user]);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleSave = async () => {
        setIsSaving(true);
        setSuccessMessage('');
        setErrorMessage('');
        
        try {
            // In production, call API to update profile
            // const response = await authService.updateProfile(formData);
            
            // For now, just simulate success
            await new Promise(resolve => setTimeout(resolve, 1000));
            setSuccessMessage('Profile updated successfully! ✓');
            setIsEditing(false);
            
            setTimeout(() => setSuccessMessage(''), 3000);
        } catch (err) {
            setErrorMessage('Failed to update profile. Please try again.');
            console.error('Error updating profile:', err);
        } finally {
            setIsSaving(false);
        }
    };

    const handleCancel = () => {
        setFormData({
            fullName: user?.fullName || '',
            email: user?.email || '',
            studentCode: user?.studentCode || '',
            phoneNumber: user?.phoneNumber || '',
        });
        setIsEditing(false);
        setErrorMessage('');
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

                {/* Success Message */}
                {successMessage && (
                    <div className="mb-6 rounded-lg bg-green-50 border border-green-200 p-4 text-green-700">
                        {successMessage}
                    </div>
                )}

                {/* Error Message */}
                {errorMessage && (
                    <div className="mb-6 rounded-lg bg-red-50 border border-red-200 p-4 text-red-700">
                        {errorMessage}
                    </div>
                )}

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

                    {/* Form */}
                    <div className="space-y-5">
                        {/* Full Name */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Full Name
                            </label>
                            {isEditing ? (
                                <input
                                    type="text"
                                    name="fullName"
                                    value={formData.fullName}
                                    onChange={handleInputChange}
                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                                />
                            ) : (
                                <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{formData.fullName}</p>
                            )}
                        </div>

                        {/* Email */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Email Address
                            </label>
                            {isEditing ? (
                                <input
                                    type="email"
                                    name="email"
                                    value={formData.email}
                                    disabled
                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500 cursor-not-allowed"
                                />
                            ) : (
                                <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{formData.email}</p>
                            )}
                            <p className="text-xs text-gray-500 mt-1">📌 Email cannot be changed</p>
                        </div>

                        {/* Student Code */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Student Code (MSSV)
                            </label>
                            {isEditing ? (
                                <input
                                    type="text"
                                    name="studentCode"
                                    value={formData.studentCode}
                                    disabled
                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500 cursor-not-allowed"
                                />
                            ) : (
                                <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{formData.studentCode || 'N/A'}</p>
                            )}
                            <p className="text-xs text-gray-500 mt-1">📌 Student code cannot be changed</p>
                        </div>

                        {/* Phone Number */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Phone Number
                            </label>
                            {isEditing ? (
                                <input
                                    type="tel"
                                    name="phoneNumber"
                                    value={formData.phoneNumber}
                                    onChange={handleInputChange}
                                    placeholder="Enter your phone number"
                                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                                />
                            ) : (
                                <p className="px-4 py-2 bg-gray-50 rounded-lg text-gray-700">{formData.phoneNumber || 'Not provided'}</p>
                            )}
                        </div>

                        {/* Account Status */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Account Status
                            </label>
                            <p className="px-4 py-2 bg-green-50 rounded-lg text-green-700 font-medium">
                                ✓ Active
                            </p>
                        </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="mt-8 flex gap-3 pt-6 border-t border-gray-200">
                        {isEditing ? (
                            <>
                                <button
                                    onClick={handleCancel}
                                    className="flex-1 rounded-lg px-4 py-2.5 border border-gray-300 text-gray-700 font-medium hover:bg-gray-50 transition"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleSave}
                                    disabled={isSaving}
                                    className="flex-1 rounded-lg px-4 py-2.5 bg-indigo-600 text-white font-medium hover:bg-indigo-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    {isSaving ? 'Saving...' : 'Save Changes'}
                                </button>
                            </>
                        ) : (
                            <>
                                <button
                                    onClick={() => setIsEditing(true)}
                                    className="flex-1 rounded-lg px-4 py-2.5 bg-indigo-600 text-white font-medium hover:bg-indigo-700 transition"
                                >
                                    Edit Profile
                                </button>
                                <button
                                    onClick={() => {
                                        logout();
                                        navigate('/login');
                                    }}
                                    className="flex-1 rounded-lg px-4 py-2.5 bg-red-50 text-red-600 font-medium hover:bg-red-100 transition"
                                >
                                    Logout
                                </button>
                            </>
                        )}
                    </div>
                </div>

                {/* Additional Info */}
                <div className="mt-6 rounded-2xl bg-blue-50 border border-blue-200 p-4 sm:p-6">
                    <h3 className="font-semibold text-blue-900 mb-2">💡 Tip</h3>
                    <p className="text-sm text-blue-800">
                        Your account is fully linked with your HCMUS student profile. Some information like Email and Student Code cannot be changed for security reasons.
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;
