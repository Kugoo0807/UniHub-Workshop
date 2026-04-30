import React, { useState, useEffect } from 'react';

const WorkshopFormModal = ({ isOpen, onClose, onSubmit, initialData, isLoading }) => {
    const [form, setForm] = useState({
        title: '',
        description: '',
        totalSlots: '',
        price: '0',
        startTime: '',
        endTime: '',
    });
    const [error, setError] = useState('');

    const isEditing = !!initialData;

    useEffect(() => {
        if (initialData) {
            setForm({
                title: initialData.title || '',
                description: initialData.description || '',
                totalSlots: String(initialData.totalSlots || ''),
                price: String(initialData.price || '0'),
                startTime: initialData.startTime ? initialData.startTime.slice(0, 16) : '',
                endTime: initialData.endTime ? initialData.endTime.slice(0, 16) : '',
            });
        } else {
            setForm({ title: '', description: '', totalSlots: '', price: '0', startTime: '', endTime: '' });
        }
        setError('');
    }, [initialData, isOpen]);

    if (!isOpen) return null;

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        setError('');

        if (!form.title.trim()) { setError('Title is required'); return; }
        if (!form.totalSlots || Number(form.totalSlots) <= 0) { setError('Total slots must be > 0'); return; }
        if (!form.startTime || !form.endTime) { setError('Start time and end time are required'); return; }
        if (new Date(form.endTime) <= new Date(form.startTime)) { setError('End time must be after start time'); return; }

        const payload = {
            title: form.title.trim(),
            description: form.description.trim() || null,
            totalSlots: Number(form.totalSlots),
            price: Number(form.price) || 0,
            startTime: form.startTime + ':00',
            endTime: form.endTime + ':00',
        };

        onSubmit(payload);
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
            <div
                className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl"
                onClick={(e) => e.stopPropagation()}
            >
                <h2 className="mb-5 text-xl font-bold text-gray-900">
                    {isEditing ? 'Edit Workshop' : 'Create Workshop'}
                </h2>

                {error && (
                    <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</div>
                )}

                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Title */}
                    <div>
                        <label className="mb-1 block text-sm font-medium text-gray-700">Title *</label>
                        <input
                            name="title"
                            value={form.title}
                            onChange={handleChange}
                            maxLength={255}
                            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            placeholder="Workshop: Clean Code với Java"
                        />
                    </div>

                    {/* Description */}
                    <div>
                        <label className="mb-1 block text-sm font-medium text-gray-700">Description</label>
                        <textarea
                            name="description"
                            value={form.description}
                            onChange={handleChange}
                            rows={3}
                            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            placeholder="Mô tả nội dung workshop (có thể để AI điền sau)"
                        />
                    </div>

                    {/* Total Slots & Price */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Total Slots *</label>
                            <input
                                name="totalSlots"
                                type="number"
                                min="1"
                                value={form.totalSlots}
                                onChange={handleChange}
                                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                                placeholder="60"
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Price (VND)</label>
                            <input
                                name="price"
                                type="number"
                                min="0"
                                value={form.price}
                                onChange={handleChange}
                                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                                placeholder="0"
                            />
                        </div>
                    </div>

                    {/* Start & End Time */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Start Time *</label>
                            <input
                                name="startTime"
                                type="datetime-local"
                                value={form.startTime}
                                onChange={handleChange}
                                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">End Time *</label>
                            <input
                                name="endTime"
                                type="datetime-local"
                                value={form.endTime}
                                onChange={handleChange}
                                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                            />
                        </div>
                    </div>

                    {/* Actions */}
                    <div className="flex justify-end gap-3 pt-2">
                        <button
                            type="button"
                            onClick={onClose}
                            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isLoading}
                            className="rounded-lg bg-indigo-600 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50 transition"
                        >
                            {isLoading ? 'Saving...' : (isEditing ? 'Update' : 'Create')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default WorkshopFormModal;
