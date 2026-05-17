import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import adminRoomService from '../services/adminRoomService';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const MapPreviewIcon = () => (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
    </svg>
);

// ─── Room Form Modal ──────────────────────────────────────────────────────────

const RoomFormModal = ({ isOpen, onClose, onSubmit, initialData, isLoading }) => {
    const [form, setForm] = useState({ name: '', capacity: '' });
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (initialData) {
            setForm({ name: initialData.name ?? '', capacity: initialData.capacity ?? '' });
        } else {
            setForm({ name: '', capacity: '' });
        }
        setErrors({});
    }, [initialData, isOpen]);

    const validate = () => {
        const errs = {};
        if (!form.name.trim()) errs.name = 'Name is required';
        else if (form.name.length > 100) errs.name = 'Max 100 characters';
        const cap = Number(form.capacity);
        if (!form.capacity) errs.capacity = 'Capacity is required';
        else if (!Number.isInteger(cap) || cap <= 0) errs.capacity = 'Must be a positive integer';
        return errs;
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        const errs = validate();
        if (Object.keys(errs).length > 0) { setErrors(errs); return; }
        onSubmit({ name: form.name.trim(), capacity: Number(form.capacity) });
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={e => e.stopPropagation()}>
                <h3 className="text-lg font-bold text-gray-900 mb-5">
                    {initialData ? 'Edit Room' : 'Create New Room'}
                </h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                    {/* Name */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Room Name</label>
                        <input
                            type="text"
                            value={form.name}
                            onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                            placeholder="e.g. Hall A"
                            className={`w-full rounded-lg border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500 transition
                                ${errors.name ? 'border-red-400 bg-red-50' : 'border-gray-300'}`}
                        />
                        {errors.name && <p className="mt-1 text-xs text-red-600">{errors.name}</p>}
                    </div>

                    {/* Capacity */}
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Capacity</label>
                        <input
                            type="number"
                            min="1"
                            value={form.capacity}
                            onChange={e => setForm(f => ({ ...f, capacity: e.target.value }))}
                            placeholder="e.g. 120"
                            className={`w-full rounded-lg border px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-500 transition
                                ${errors.capacity ? 'border-red-400 bg-red-50' : 'border-gray-300'}`}
                        />
                        {errors.capacity && <p className="mt-1 text-xs text-red-600">{errors.capacity}</p>}
                    </div>

                    <div className="flex justify-end gap-3 pt-2">
                        <button type="button" onClick={onClose} disabled={isLoading}
                            className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition disabled:opacity-50">
                            Cancel
                        </button>
                        <button type="submit" disabled={isLoading}
                            className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition disabled:opacity-50">
                            {isLoading && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />}
                            {initialData ? 'Save Changes' : 'Create Room'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

// ─── Upload Map Image Modal ───────────────────────────────────────────────────

const UploadMapModal = ({ room, onClose, onSuccess }) => {
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState('');

    const handleFileChange = (e) => {
        const f = e.target.files?.[0];
        if (!f) return;
        setFile(f);
        setPreview(URL.createObjectURL(f));
        setError('');
    };

    const handleSubmit = async () => {
        if (!file) { setError('Please select an image first.'); return; }
        try {
            setUploading(true);
            setError('');
            const updated = await adminRoomService.uploadMapImage(room.id, file);
            onSuccess(updated);
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'Upload failed');
        } finally {
            setUploading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={e => e.stopPropagation()}>
                <h3 className="text-lg font-bold text-gray-900 mb-1">Upload Floor Map</h3>
                <p className="text-sm text-gray-500 mb-5">{room.name}</p>

                {/* Current map */}
                {room.layoutMapUrl && !preview && (
                    <div className="mb-4 rounded-xl overflow-hidden border border-gray-200">
                        <p className="px-3 py-1.5 text-xs text-gray-500 bg-gray-50 border-b">Current map</p>
                        <img src={room.layoutMapUrl} alt="Current floor map"
                            className="w-full max-h-40 object-contain bg-gray-100 p-2" />
                    </div>
                )}

                {/* Preview */}
                {preview && (
                    <div className="mb-4 rounded-xl overflow-hidden border border-indigo-200">
                        <p className="px-3 py-1.5 text-xs text-indigo-600 bg-indigo-50 border-b">New image preview</p>
                        <img src={preview} alt="Preview"
                            className="w-full max-h-40 object-contain bg-gray-100 p-2" />
                    </div>
                )}

                {/* File input */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Select Image <span className="text-gray-400 font-normal">(JPEG, JPG, PNG, WEBP · max 5MB)</span>
                    </label>
                    <input type="file" accept="image/jpeg,image/jpg,image/png,image/webp"
                        onChange={handleFileChange}
                        className="block w-full text-sm text-gray-500
                            file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0
                            file:text-sm file:font-semibold file:bg-indigo-50 file:text-indigo-700
                            hover:file:bg-indigo-100" />
                </div>

                {error && <p className="mb-3 text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{error}</p>}

                <div className="flex justify-end gap-3">
                    <button onClick={onClose} disabled={uploading}
                        className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition disabled:opacity-50">
                        Cancel
                    </button>
                    <button onClick={handleSubmit} disabled={uploading || !file}
                        className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 transition disabled:opacity-50">
                        {uploading && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />}
                        Upload Map
                    </button>
                </div>
            </div>
        </div>
    );
};

// ─── Map Preview Modal ────────────────────────────────────────────────────────

const MapViewModal = ({ room, onClose }) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm" onClick={onClose}>
        <div className="relative w-full max-w-2xl rounded-2xl overflow-hidden shadow-2xl" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-4 py-3 bg-gray-900 text-white">
                <div>
                    <p className="font-semibold">{room.name} — Floor Map</p>
                    <p className="text-xs text-gray-400">Capacity: {room.capacity}</p>
                </div>
                <button onClick={onClose} className="text-gray-400 hover:text-white text-2xl leading-none">×</button>
            </div>
            <img src={room.layoutMapUrl} alt={`${room.name} floor map`}
                className="w-full max-h-[70vh] object-contain bg-gray-100" />
        </div>
    </div>
);

// ─── Delete Confirm Modal ─────────────────────────────────────────────────────

const DeleteConfirmModal = ({ room, onConfirm, onClose, isDeleting }) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-bold text-gray-900">Delete Room?</h3>
            <p className="mt-2 text-sm text-gray-500">
                Are you sure you want to delete <span className="font-semibold text-gray-800">{room.name}</span>?
                This action cannot be undone.
            </p>
            <div className="mt-5 flex justify-end gap-3">
                <button onClick={onClose} disabled={isDeleting}
                    className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition">
                    Cancel
                </button>
                <button onClick={() => onConfirm(room.id)} disabled={isDeleting}
                    className="flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition disabled:opacity-50">
                    {isDeleting && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />}
                    Delete
                </button>
            </div>
        </div>
    </div>
);

// ─── Main Page ────────────────────────────────────────────────────────────────

const RoomsPage = () => {
    const navigate = useNavigate();
    const [rooms, setRooms] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    // Modals
    const [formOpen, setFormOpen] = useState(false);
    const [editingRoom, setEditingRoom] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [uploadRoom, setUploadRoom] = useState(null);
    const [deleteRoom, setDeleteRoom] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [viewMapRoom, setViewMapRoom] = useState(null);
    const [openActionId, setOpenActionId] = useState(null);

    // Close dropdown on outside click
    useEffect(() => {
        const handler = () => setOpenActionId(null);
        window.addEventListener('click', handler);
        return () => window.removeEventListener('click', handler);
    }, []);

    // Auto-clear success message
    useEffect(() => {
        if (!success) return;
        const t = setTimeout(() => setSuccess(''), 3000);
        return () => clearTimeout(t);
    }, [success]);

    const fetchRooms = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await adminRoomService.getAll();
            setRooms(Array.isArray(data) ? data : []);
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'Failed to load rooms');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchRooms(); }, [fetchRooms]);

    // ── Handlers ────────────────────────────────────────────────────────────

    const handleCreate = () => { setEditingRoom(null); setFormOpen(true); };
    const handleEdit = (room) => { setEditingRoom(room); setFormOpen(true); };

    const handleFormSubmit = async (payload) => {
        try {
            setSubmitting(true);
            setError('');
            if (editingRoom) {
                await adminRoomService.update(editingRoom.id, payload);
                setSuccess('Room updated successfully.');
            } else {
                await adminRoomService.create(payload);
                setSuccess('Room created successfully.');
            }
            setFormOpen(false);
            fetchRooms();
        } catch (err) {
            setError(err.response?.data?.message || err.message || 'Operation failed');
        } finally {
            setSubmitting(false);
        }
    };

    const handleUploadSuccess = (updatedRoom) => {
        setRooms(prev => prev.map(r => r.id === updatedRoom.id ? updatedRoom : r));
        setUploadRoom(null);
        setSuccess('Floor map updated successfully.');
    };

    const handleDelete = async (id) => {
        try {
            setDeleting(true);
            setError('');
            await adminRoomService.delete(id);
            setDeleteRoom(null);
            setSuccess('Room deleted successfully.');
            fetchRooms();
        } catch (err) {
            setDeleteRoom(null);
            setError(err.response?.data?.message || err.message || 'Delete failed');
        } finally {
            setDeleting(false);
        }
    };

    // ── Render ───────────────────────────────────────────────────────────────

    return (
        <div className="min-h-screen bg-[#fafafa] pb-12 font-sans antialiased">
            {/* ── Navbar ── */}
            <nav className="sticky top-0 z-40 border-b border-gray-200/50 bg-white/80 backdrop-blur-xl">
                <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
                    <button onClick={() => navigate('/admin')}
                        className="group flex items-center gap-2 rounded-xl bg-gray-50 px-4 py-2 text-sm font-bold text-gray-600 transition-all hover:bg-gray-100 hover:text-indigo-600">
                        <svg className="h-4 w-4 transition-transform group-hover:-translate-x-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M15 19l-7-7 7-7" />
                        </svg>
                        Exit to Dashboard
                    </button>
                    <div className="flex items-center gap-3">
                        <span className="text-[13px] font-black uppercase tracking-widest text-gray-400">Admin Panel</span>
                    </div>
                </div>
            </nav>

            <main className="mx-auto max-w-7xl px-6 pt-10 space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
                {/* Header Section */}
                <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                    <div className="space-y-1">
                        <h1 className="text-4xl font-black tracking-tight text-gray-900">Room Management</h1>
                        <p className="text-[15px] font-medium text-gray-400">
                            {rooms.length} room{rooms.length !== 1 ? 's' : ''} configured
                        </p>
                    </div>
                    <button onClick={handleCreate}
                        className="flex items-center gap-2 rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_4px_14px_0_rgb(79,70,229,0.39)] hover:shadow-[0_6px_20px_rgba(79,70,229,0.23)] hover:bg-indigo-700 transition">
                        <span className="text-lg leading-none">+</span>
                        Create Room
                    </button>
                </div>

            {/* Alerts */}
            {success && (
                <div className="mb-4 flex items-center justify-between rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-700 border border-emerald-200">
                    <span>✓ {success}</span>
                    <button onClick={() => setSuccess('')} className="text-emerald-500 hover:text-emerald-700">✕</button>
                </div>
            )}
            {error && (
                <div className="mb-4 flex items-center justify-between rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 border border-red-200">
                    <span>{error}</span>
                    <button onClick={() => setError('')} className="text-red-500 hover:text-red-700">✕</button>
                </div>
            )}

            {/* Content */}
            {loading ? (
                <div className="flex items-center justify-center py-20">
                    <div className="h-8 w-8 animate-spin rounded-full border-4 border-indigo-600 border-t-transparent" />
                </div>
            ) : rooms.length === 0 ? (
                <div className="rounded-2xl bg-white p-12 text-center shadow-sm border border-gray-100">
                    <div className="text-5xl mb-3">🏛️</div>
                    <p className="text-gray-500">No rooms yet. Click "Create Room" to add the first one.</p>
                </div>
            ) : (
                <div className="overflow-hidden rounded-2xl bg-white shadow-sm border border-gray-100">
                    <table className="w-full text-sm text-left">
                        <thead>
                            <tr className="border-b bg-gray-50/50 text-[11px] uppercase tracking-wider text-gray-400">
                                <th className="px-6 py-4 font-bold">Room</th>
                                <th className="px-6 py-4 font-bold text-center">Capacity</th>
                                <th className="px-6 py-4 font-bold text-center">Floor Map</th>
                                <th className="px-6 py-4 font-bold text-center">Active Workshops</th>
                                <th className="px-6 py-4 font-bold text-center">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rooms.map((room, idx) => (
                                <tr key={room.id} className="border-b last:border-0 hover:bg-gray-50/30 transition">
                                    {/* Room Name */}
                                    <td className="px-6 py-4">
                                        <div className="font-semibold text-gray-900">{room.name}</div>
                                    </td>

                                    {/* Capacity */}
                                    <td className="px-6 py-4 text-center">
                                        <span className="inline-flex items-center gap-1 rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                                            👥 {room.capacity}
                                        </span>
                                    </td>

                                    {/* Floor map */}
                                    <td className="px-6 py-4 text-center">
                                        {room.layoutMapUrl ? (
                                            <button
                                                onClick={() => setViewMapRoom(room)}
                                                className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-600 hover:bg-indigo-100 transition">
                                                <MapPreviewIcon />
                                                View Map
                                            </button>
                                        ) : (
                                            <span className="text-xs text-gray-400 italic">No map</span>
                                        )}
                                    </td>

                                    {/* Active workshop count */}
                                    <td className="px-6 py-4 text-center">
                                        <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold
                                            ${room.activeWorkshopCount > 0
                                                ? 'bg-amber-100 text-amber-700'
                                                : 'bg-gray-100 text-gray-500'}`}>
                                            {room.activeWorkshopCount}
                                        </span>
                                    </td>

                                    {/* Actions dropdown */}
                                    <td className="px-6 py-4 text-center">
                                        <div className="relative inline-block text-left" onClick={e => e.stopPropagation()}>
                                            <button
                                                onClick={() => setOpenActionId(openActionId === room.id ? null : room.id)}
                                                className={`flex items-center justify-center w-8 h-8 rounded-full transition-all duration-200
                                                    ${openActionId === room.id
                                                        ? 'bg-gray-100 text-gray-900 shadow-inner'
                                                        : 'text-gray-400 hover:bg-gray-50 hover:text-gray-600'}`}>
                                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"
                                                        d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                                                </svg>
                                            </button>

                                            {openActionId === room.id && (
                                                <div className={`absolute right-0 z-50 w-28 rounded-md border border-gray-200 bg-white py-1 shadow-lg
                                                    ${idx >= rooms.length - 2 ? 'bottom-full mb-1' : 'mt-1'}`}>
                                                    <button
                                                        onClick={() => { handleEdit(room); setOpenActionId(null); }}
                                                        className="w-full px-3 py-1.5 text-left text-[11px] text-gray-700 hover:bg-gray-50 transition">
                                                        Edit Details
                                                    </button>
                                                    <button
                                                        onClick={() => { setUploadRoom(room); setOpenActionId(null); }}
                                                        className="w-full px-3 py-1.5 text-left text-[11px] text-gray-700 hover:bg-gray-50 transition">
                                                        {room.layoutMapUrl ? 'Update Map' : 'Upload Map'}
                                                    </button>
                                                    <div className="my-1 border-t border-gray-100" />
                                                    <button
                                                        onClick={() => { setDeleteRoom(room); setOpenActionId(null); }}
                                                        disabled={room.activeWorkshopCount > 0}
                                                        title={room.activeWorkshopCount > 0 ? 'Cannot delete: room has active workshops' : 'Delete room'}
                                                        className="w-full px-3 py-1.5 text-left text-[11px] text-red-600 hover:bg-red-50 transition disabled:opacity-40 disabled:cursor-not-allowed">
                                                        Delete
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Modals */}
            <RoomFormModal
                isOpen={formOpen}
                onClose={() => setFormOpen(false)}
                onSubmit={handleFormSubmit}
                initialData={editingRoom}
                isLoading={submitting}
            />

            {uploadRoom && (
                <UploadMapModal
                    room={uploadRoom}
                    onClose={() => setUploadRoom(null)}
                    onSuccess={handleUploadSuccess}
                />
            )}

            {viewMapRoom && (
                <MapViewModal
                    room={viewMapRoom}
                    onClose={() => setViewMapRoom(null)}
                />
            )}

            {deleteRoom && (
                <DeleteConfirmModal
                    room={deleteRoom}
                    onConfirm={handleDelete}
                    onClose={() => setDeleteRoom(null)}
                    isDeleting={deleting}
                />
            )}
            </main>
        </div>
    );
};

export default RoomsPage;
