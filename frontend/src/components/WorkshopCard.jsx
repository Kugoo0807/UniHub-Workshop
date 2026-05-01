import React from 'react';

const WorkshopCard = ({ workshop, onRegisterFree, onRegisterPaid }) => {
    const isFree = !workshop.price || workshop.price === 0;
    const remainingSlots = Math.max(0, Number(workshop.remainingSlots ?? 0));
    const isSoldOut = remainingSlots === 0;

    return (
        <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
            <h3 className="text-lg font-semibold text-gray-800">{workshop.title}</h3>
            <p className="mt-2 text-sm text-gray-600">{workshop.description}</p>
            <div className="mt-3 flex items-center justify-between">
                <div className={`text-sm font-medium ${isSoldOut ? 'text-red-600' : 'text-gray-700'}`}>
                    {`Slots left: ${remainingSlots}`}
                </div>
                <div className="flex gap-2">
                    {isFree ? (
                        <button
                            onClick={() => onRegisterFree(workshop.id)}
                            disabled={isSoldOut}
                            className="rounded bg-green-600 px-3 py-1 text-sm text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:bg-gray-300"
                        >
                            {isSoldOut ? 'Full' : 'Register (Free)'}
                        </button>
                    ) : (
                        <button
                            onClick={() => onRegisterPaid(workshop.id, workshop.price)}
                            disabled={isSoldOut}
                            className="rounded border border-indigo-600 px-3 py-1 text-sm text-indigo-600 hover:bg-indigo-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-transparent"
                        >
                            {isSoldOut ? 'Full' : `Register (${workshop.price.toLocaleString('vi-VN')} VND)`}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default WorkshopCard;