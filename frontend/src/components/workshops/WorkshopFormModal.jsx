import React, { useState, useEffect, useCallback } from 'react';
import axiosClient from '../../api/axiosClient';

const WorkshopFormModal = ({ isOpen, onClose, onSubmit, initialData, isLoading }) => {
    const [form, setForm] = useState({
        title: '',
        description: '',
        roomId: '',
        speaker: '',
        totalSlots: '',
        price: '0',
        workshopDate: '',      // Same-day constraint: single date picker
        workshopStartTime: '', // Time-only picker for start
        workshopEndTime: '',   // Time-only picker for end
        registrationStartTime: '',
        registrationEndTime: '',
    });
    const [error, setError] = useState('');
    const [rooms, setRooms] = useState([]);

    const isEditing = !!initialData;

    // Fetch available rooms for the dropdown
    useEffect(() => {
        const fetchRooms = async () => {
            try {
                const data = await axiosClient.get('/rooms');
                setRooms(Array.isArray(data) ? data : []);
            } catch (_) {
                // Silently fail — rooms dropdown will be empty
            }
        };
        if (isOpen) fetchRooms();
    }, [isOpen]);

    useEffect(() => {
        if (initialData) {
            // Parse existing datetime into date + time components
            let workshopDate = '';
            let workshopStartTime = '';
            let workshopEndTime = '';
            if (initialData.startTime) {
                const st = initialData.startTime.slice(0, 16);
                workshopDate = st.slice(0, 10);
                workshopStartTime = st.slice(11, 16);
            }
            if (initialData.endTime) {
                workshopEndTime = initialData.endTime.slice(11, 16);
            }

            setForm({
                title: initialData.title || '',
                description: initialData.description || '',
                roomId: initialData.roomId ? String(initialData.roomId) : '',
                speaker: initialData.speaker || '',
                totalSlots: String(initialData.totalSlots || ''),
                price: String(initialData.price || '0'),
                workshopDate,
                workshopStartTime,
                workshopEndTime,
                registrationStartTime: initialData.registrationStartTime ? initialData.registrationStartTime.slice(0, 16) : '',
                registrationEndTime: initialData.registrationEndTime ? initialData.registrationEndTime.slice(0, 16) : '',
            });
        } else {
            setForm({
                title: '', description: '', roomId: '', speaker: '',
                totalSlots: '', price: '0',
                workshopDate: '', workshopStartTime: '', workshopEndTime: '',
                registrationStartTime: '', registrationEndTime: '',
            });
        }
        setError('');
    }, [initialData, isOpen]);

    if (!isOpen) return null;

    const handleChange = (e) => {
        setForm({ ...form, [e.target.name]: e.target.value });
    };

    // Get selected room for capacity validation
    const selectedRoom = rooms.find(r => String(r.id) === form.roomId);

    // Calculate maximum allowed registration end time (startTime - 24h)
    let maxRegistrationEndTime = "";
    if (form.workshopDate && form.workshopStartTime) {
        const workshopStart = new Date(`${form.workshopDate}T${form.workshopStartTime}`);
        const maxEnd = new Date(workshopStart.getTime() - 24 * 60 * 60 * 1000);
        // Correctly format to local YYYY-MM-DDTHH:mm for datetime-local input
        const tzOffset = maxEnd.getTimezoneOffset() * 60000;
        maxRegistrationEndTime = new Date(maxEnd.getTime() - tzOffset).toISOString().slice(0, 16);
    }

    const handleSubmit = (e) => {
        e.preventDefault();
        setError('');

        // V1: Title required
        if (!form.title.trim()) { setError('Title is required'); return; }
        // V7: Speaker required
        if (!form.speaker.trim()) { setError('Speaker is required'); return; }
        // V8: Room required
        if (!form.roomId) { setError('Please select a room'); return; }
        // V2: Total slots > 0
        if (!form.totalSlots || Number(form.totalSlots) <= 0) { setError('Total slots must be > 0'); return; }
        // V6: Price >= 0
        if (Number(form.price) < 0) { setError('Price cannot be negative'); return; }

        // V10: Total slots <= room capacity
        if (selectedRoom && Number(form.totalSlots) > selectedRoom.capacity) {
            setError(`Total slots exceeds room capacity (${selectedRoom.capacity})`);
            return;
        }

        // New Constraint: Cannot reduce slots below existing successful registrations
        if (initialData && initialData.successfulCount && Number(form.totalSlots) < initialData.successfulCount) {
            setError(`Cannot reduce slots below the number of confirmed registrations (${initialData.successfulCount})`);
            return;
        }

        // Workshop date + time validation (same-day constraint enforced by design)
        if (!form.workshopDate) { setError('Workshop date is required'); return; }
        if (!form.workshopStartTime || !form.workshopEndTime) { setError('Start time and end time are required'); return; }

        const startTime = `${form.workshopDate}T${form.workshopStartTime}:00`;
        const endTime = `${form.workshopDate}T${form.workshopEndTime}:00`;

        // V3: End time > start time
        if (form.workshopEndTime <= form.workshopStartTime) {
            setError('End time must be after start time');
            return;
        }

        // V9: Registration time range required
        if (!form.registrationStartTime || !form.registrationEndTime) {
            setError('Registration time range is required');
            return;
        }
        // V4: registrationEndTime > registrationStartTime
        if (new Date(form.registrationEndTime) <= new Date(form.registrationStartTime)) {
            setError('Registration end time must be after registration start time');
            return;
        }
        // V5: registrationStartTime < startTime
        if (new Date(form.registrationStartTime) >= new Date(startTime)) {
            setError('Registration must start before the workshop begins');
            return;
        }
        // V5b: registrationEndTime <= startTime - 1 day
        const startDT = new Date(startTime);
        const regEndDT = new Date(form.registrationEndTime);
        const oneDayInMs = 24 * 60 * 60 * 1000;

        if (startDT.getTime() - regEndDT.getTime() < oneDayInMs) {
            setError('Registration must close at least 1 day before the workshop starts');
            return;
        }

        const payload = {
            title: form.title.trim(),
            description: form.description.trim() || null,
            roomId: Number(form.roomId),
            speaker: form.speaker.trim(),
            totalSlots: Number(form.totalSlots),
            price: Number(form.price) || 0,
            startTime,
            endTime,
            registrationStartTime: form.registrationStartTime + ':00',
            registrationEndTime: form.registrationEndTime + ':00',
        };

        onSubmit(payload);
    };

    const inputClass = "w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500";

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}>
            <div
                className="w-full max-w-lg max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-xl"
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
                            className={inputClass}
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
                            className={inputClass}
                            placeholder="Mô tả nội dung workshop (có thể để AI điền sau)"
                        />
                    </div>

                    {/* Speaker & Room */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Speaker *</label>
                            <input
                                name="speaker"
                                value={form.speaker}
                                onChange={handleChange}
                                maxLength={255}
                                className={inputClass}
                                placeholder="Nguyen Van A"
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Room *</label>
                            <select
                                name="roomId"
                                value={form.roomId}
                                onChange={handleChange}
                                className={inputClass}
                            >
                                <option value="">— Select Room —</option>
                                {rooms.map(r => (
                                    <option key={r.id} value={r.id}>
                                        {r.name} (cap: {r.capacity})
                                    </option>
                                ))}
                            </select>
                            {selectedRoom && (
                                <p className="mt-1 text-xs text-gray-500">Capacity: {selectedRoom.capacity}</p>
                            )}
                        </div>
                    </div>

                    {/* Total Slots & Price */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">
                                Total Slots *
                                {selectedRoom && Number(form.totalSlots) > selectedRoom.capacity && (
                                    <span className="ml-1 text-red-500 text-xs"> exceeds capacity</span>
                                )}
                            </label>
                            <input
                                name="totalSlots"
                                type="number"
                                min="1"
                                max={selectedRoom?.capacity}
                                value={form.totalSlots}
                                onChange={handleChange}
                                className={inputClass}
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
                                className={inputClass}
                                placeholder="0"
                            />
                        </div>
                    </div>

                    {/* Workshop Date — Same-day constraint: 1 date + 2 time pickers (G24) */}
                    <div>
                        <label className="mb-1 block text-sm font-medium text-gray-700">Workshop Date *</label>
                        <input
                            name="workshopDate"
                            type="date"
                            value={form.workshopDate}
                            onChange={handleChange}
                            className={inputClass}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Start Time *</label>
                            <input
                                name="workshopStartTime"
                                type="time"
                                value={form.workshopStartTime}
                                onChange={handleChange}
                                className={inputClass}
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">End Time *</label>
                            <input
                                name="workshopEndTime"
                                type="time"
                                value={form.workshopEndTime}
                                onChange={handleChange}
                                min={form.workshopStartTime}
                                className={inputClass}
                            />
                        </div>
                    </div>

                    {/* Registration Time Range */}
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Registration Start *</label>
                            <input
                                name="registrationStartTime"
                                type="datetime-local"
                                value={form.registrationStartTime}
                                onChange={handleChange}
                                max={form.registrationEndTime || maxRegistrationEndTime}
                                className={inputClass}
                            />
                        </div>
                        <div>
                            <label className="mb-1 block text-sm font-medium text-gray-700">Registration End *</label>
                            <input
                                name="registrationEndTime"
                                type="datetime-local"
                                value={form.registrationEndTime}
                                onChange={handleChange}
                                min={form.registrationStartTime}
                                max={maxRegistrationEndTime}
                                className={inputClass}
                            />
                            {maxRegistrationEndTime && (
                                <p className="mt-1 text-[10px] text-gray-500 italic">
                                    Must close by: {maxRegistrationEndTime.replace('T', ' ')}
                                </p>
                            )}
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
