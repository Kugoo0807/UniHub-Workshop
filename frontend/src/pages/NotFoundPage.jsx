import React from 'react';
import { Link } from 'react-router-dom';

const NotFoundPage = () => {
    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
            <div className="text-center">
                <h1 className="text-6xl font-bold text-gray-300 mb-4">404</h1>
                <h2 className="text-2xl font-semibold text-gray-700 mb-2">Page Not Found</h2>
                <p className="text-gray-500 mb-6">
                    It looks like you followed a broken link or the page you are looking for does not exist.
                </p>
                <Link   
                    to="/"
                    className="inline-block px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-medium 
                               rounded-md shadow-sm transition-colors"
                >
                    Back to Home
                </Link>
            </div>
        </div>
    );
};

export default NotFoundPage;
