/**
 * PaginationControl — reusable pagination bar.
 *
 * Props:
 *   currentPage   {number}   0-indexed current page
 *   totalPages    {number}   total number of pages
 *   totalElements {number}   total number of items across all pages
 *   pageSize      {number}   number of items per page
 *   onPageChange  {(page: number) => void}  called with the new 0-indexed page
 *   itemLabel     {string}   singular noun shown in the "Showing X–Y of N …" label (default: "items")
 */
const PaginationControl = ({
    currentPage,
    totalPages,
    totalElements,
    pageSize,
    onPageChange,
    itemLabel = 'items',
}) => {
    if (totalPages <= 1) return null;

    const from = currentPage * pageSize + 1;
    const to   = Math.min((currentPage + 1) * pageSize, totalElements);

    const btnBase =
        'flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 text-gray-500 ' +
        'hover:bg-gray-50 disabled:opacity-30 disabled:cursor-not-allowed transition select-none';

    return (
        <div className="mt-6 flex items-center justify-between">
            {/* Summary label */}
            <p className="text-sm text-gray-500">
                Showing {from}–{to} of {totalElements} {itemLabel}
            </p>

            {/* Controls */}
            <div className="flex items-center gap-1">
                {/* First */}
                <button
                    onClick={() => onPageChange(0)}
                    disabled={currentPage === 0}
                    className={btnBase}
                    title="First page"
                >
                    «
                </button>

                {/* Prev */}
                <button
                    onClick={() => onPageChange(currentPage - 1)}
                    disabled={currentPage === 0}
                    className={btnBase}
                    title="Previous page"
                >
                    ‹
                </button>

                {/* Page numbers — up to 5 around current */}
                {Array.from({ length: totalPages }, (_, i) => i)
                    .filter(i => Math.abs(i - currentPage) <= 2)
                    .map(i => (
                        <button
                            key={i}
                            onClick={() => onPageChange(i)}
                            className={
                                'flex h-8 w-8 items-center justify-center rounded-lg border text-sm font-medium transition ' +
                                (i === currentPage
                                    ? 'border-indigo-600 bg-indigo-600 text-white shadow-sm'
                                    : 'border-gray-200 text-gray-600 hover:bg-gray-50')
                            }
                        >
                            {i + 1}
                        </button>
                    ))
                }

                {/* Next */}
                <button
                    onClick={() => onPageChange(currentPage + 1)}
                    disabled={currentPage >= totalPages - 1}
                    className={btnBase}
                    title="Next page"
                >
                    ›
                </button>

                {/* Last */}
                <button
                    onClick={() => onPageChange(totalPages - 1)}
                    disabled={currentPage >= totalPages - 1}
                    className={btnBase}
                    title="Last page"
                >
                    »
                </button>
            </div>
        </div>
    );
};

export default PaginationControl;
