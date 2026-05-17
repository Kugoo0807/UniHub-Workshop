import React, { useState } from 'react';
import PasswordField from './PasswordField';
import authService from '../../services/authService';

const ROLES = [
    { value: 'ADMIN', label: 'Admin', color: 'text-violet-700 bg-violet-50 border-violet-200' },
    { value: 'STAFF', label: 'Staff', color: 'text-indigo-700 bg-indigo-50 border-indigo-200' },
];

const initialForm = {
    fullName: '',
    email: '',
    phoneNumber: '',
    password: '',
    confirmPassword: '',
    role: 'STAFF',
};

const CreateAccountModal = ({ isOpen, onClose }) => {
    const [form, setForm] = useState(initialForm);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    if (!isOpen) return null;

    const handleChange = (e) => {
        setForm((prev) => ({ ...prev, [e.target.id]: e.target.value }));
    };

    const handleRoleSelect = (role) => {
        setForm((prev) => ({ ...prev, role }));
    };

    const handleClose = () => {
        setForm(initialForm);
        setError('');
        setSuccess('');
        onClose();
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');

        if (form.password !== form.confirmPassword) {
            setError('Passwords do not match.');
            return;
        }

        try {
            setSubmitting(true);
            const payload = {
                fullName: form.fullName,
                email: form.email,
                phoneNumber: form.phoneNumber || undefined,
                password: form.password,
                role: form.role,
            };
            await authService.adminStaffRegister(payload);
            setSuccess(`Account created successfully for ${form.fullName} (${form.role}).`);
            setForm(initialForm);
        } catch (err) {
            setError(err.message || 'Failed to create account.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
            onClick={handleClose}
        >
            <div
                className="relative w-full max-w-md rounded-2xl bg-white shadow-2xl ring-1 ring-gray-200 overflow-hidden"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="bg-gradient-to-r from-violet-600 to-indigo-600 px-6 py-5">
                    <div className="flex items-center justify-between">
                        <div>
                            <h2 className="text-lg font-bold text-white">Create Account</h2>
                            <p className="mt-0.5 text-sm text-indigo-200">
                                Add a new Admin or Staff member
                            </p>
                        </div>
                        <button
                            type="button"
                            onClick={handleClose}
                            className="flex items-center justify-center w-8 h-8 rounded-full bg-white/20 text-white hover:bg-white/30 transition"
                            aria-label="Close modal"
                        >
                            ✕
                        </button>
                    </div>
                </div>

                {/* Body */}
                <div className="px-6 py-5">
                    {/* Success banner */}
                    {success && (
                        <div className="mb-4 flex items-start gap-3 rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-sm text-emerald-700">
                            <span className="text-base leading-none mt-0.5">✅</span>
                            <span>{success}</span>
                        </div>
                    )}

                    {/* Error banner */}
                    {error && (
                        <div className="mb-4 flex items-start gap-3 rounded-xl bg-red-50 border border-red-200 p-3 text-sm text-red-700">
                            <span className="text-base leading-none mt-0.5">⚠️</span>
                            <span>{error}</span>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="space-y-4">
                        {/* Role selector */}
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-2">
                                Role
                            </label>
                            <div className="flex gap-3">
                                {ROLES.map((r) => (
                                    <button
                                        key={r.value}
                                        type="button"
                                        onClick={() => handleRoleSelect(r.value)}
                                        className={`flex-1 rounded-lg border-2 py-2.5 text-sm font-semibold transition-all duration-150 ${
                                            form.role === r.value
                                                ? r.color + ' border-current scale-[1.02] shadow-sm'
                                                : 'border-gray-200 text-gray-400 hover:border-gray-300 hover:text-gray-600'
                                        }`}
                                    >
                                        {form.role === r.value && (
                                            <span className="mr-1.5">✓</span>
                                        )}
                                        {r.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Full Name */}
                        <div>
                            <label
                                htmlFor="fullName"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Full Name <span className="text-red-500">*</span>
                            </label>
                            <input
                                id="fullName"
                                type="text"
                                value={form.fullName}
                                onChange={handleChange}
                                required
                                placeholder="e.g. Nguyen Van A"
                                className="block w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm shadow-sm placeholder-gray-400
                                           focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition"
                            />
                        </div>

                        {/* Email */}
                        <div>
                            <label
                                htmlFor="email"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Email <span className="text-red-500">*</span>
                            </label>
                            <input
                                id="email"
                                type="email"
                                value={form.email}
                                onChange={handleChange}
                                required
                                placeholder="e.g. staff@unihub.edu"
                                className="block w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm shadow-sm placeholder-gray-400
                                           focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition"
                            />
                        </div>

                        {/* Phone Number */}
                        <div>
                            <label
                                htmlFor="phoneNumber"
                                className="block text-sm font-medium text-gray-700 mb-1"
                            >
                                Phone Number{' '}
                                <span className="text-gray-400 font-normal">(optional)</span>
                            </label>
                            <input
                                id="phoneNumber"
                                type="text"
                                value={form.phoneNumber}
                                onChange={handleChange}
                                placeholder="+84 90 000 0000"
                                className="block w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm shadow-sm placeholder-gray-400
                                           focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition"
                            />
                        </div>

                        {/* Password */}
                        <PasswordField
                            id="password"
                            label="Password *"
                            value={form.password}
                            onChange={handleChange}
                            required
                            minLength={6}
                            placeholder="Min. 6 characters"
                            autoComplete="new-password"
                        />

                        {/* Confirm Password */}
                        <PasswordField
                            id="confirmPassword"
                            label="Confirm Password *"
                            value={form.confirmPassword}
                            onChange={handleChange}
                            required
                            minLength={6}
                            placeholder="Re-enter password"
                            autoComplete="new-password"
                        />

                        {/* Actions */}
                        <div className="flex justify-end gap-3 pt-2">
                            <button
                                type="button"
                                onClick={handleClose}
                                disabled={submitting}
                                className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700
                                           hover:bg-gray-50 transition disabled:opacity-50"
                            >
                                Cancel
                            </button>
                            <button
                                type="submit"
                                disabled={submitting}
                                className="flex items-center gap-2 rounded-lg bg-gradient-to-r from-violet-600 to-indigo-600
                                           px-5 py-2.5 text-sm font-semibold text-white shadow-sm
                                           hover:from-violet-700 hover:to-indigo-700 transition-all disabled:opacity-50"
                            >
                                {submitting ? (
                                    <>
                                        <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                                        Creating...
                                    </>
                                ) : (
                                    <>
                                        <span>＋</span>
                                        Create Account
                                    </>
                                )}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};

export default CreateAccountModal;
