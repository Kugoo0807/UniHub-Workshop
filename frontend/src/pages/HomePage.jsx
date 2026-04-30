import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const HomePage = () => {
    const { user } = useAuth();

    const dashboardLink = '/admin';

    return (
        <div className="min-h-screen bg-gray-50">
            <div className="max-w-4xl mx-auto pt-20 px-4 text-center">
                <h1 className="text-4xl font-bold text-gray-900 mb-4">
                    Welcome to UniHub Workshop
                </h1>
                <p className="text-lg text-gray-600 mb-8">
                    Your platform for workshop registration and management.
                </p>
                <div className="flex justify-center gap-4">
                    {user ? (
                        <Link
                            to={dashboardLink}
                            className="px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-medium 
                                       rounded-md shadow-sm transition-colors"
                        >
                            Go to Dashboard
                        </Link>
                    ) : (
                        <>
                            <Link
                                to="/login"
                                className="px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white font-medium 
                                           rounded-md shadow-sm transition-colors"
                            >
                                Sign In
                            </Link>
                            <Link
                                to="/register"
                                className="px-6 py-3 bg-white hover:bg-gray-50 text-indigo-600 font-medium 
                                           rounded-md border border-indigo-600 shadow-sm transition-colors"
                            >
                                Register
                            </Link>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default HomePage;
