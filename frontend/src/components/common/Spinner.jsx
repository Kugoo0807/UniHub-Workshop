import React from 'react';

const Spinner = ({ isFullScreen = true, label = "Loading..." }) => {
  // Define layout styles based on usage context
  const containerStyle = isFullScreen
    ? "fixed inset-0 z-50 flex flex-col items-center justify-center bg-white/80 backdrop-blur-sm"
    : "flex flex-col items-center justify-center p-6";

  return (
    <div className={containerStyle} role="status" aria-live="polite">
      <div className="relative inline-flex">
        {/* Track circle */}
        <div className="w-12 h-12 border-4 border-slate-200 rounded-full" />
        
        {/* Progress arc */}
        <div className="absolute top-0 left-0 w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin" />
      </div>

      {label && (
        <p className="mt-4 text-sm font-semibold text-slate-600 tracking-wide">
          {label}
        </p>
      )}

      {/* Screen reader only text */}
      <span className="sr-only">Loading content...</span>
    </div>
  );
};

export default Spinner;