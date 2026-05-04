import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/layouts/Layout';
import ProtectedRoute from './routes/ProtectedRoute';
import GuestRoute from './routes/GuestRoute';

// Pages
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import HomeStudent from './pages/HomeStudent';
import Dashboard from './pages/Dashboard';
import NotFoundPage from './pages/NotFoundPage';

const App = () => {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>
                    <Route element={<Layout />}>
                        {/* Always accessible public routes */}
                        <Route path="/unauthorized" element={<UnauthorizedPage />} />

                        {/* Guest-only routes (redirects to dashboard if already logged in) */}
                        <Route element={<GuestRoute />}>
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/register" element={<RegisterPage />} />
                        </Route>

                        {/* Authenticated routes */}
                        <Route element={<ProtectedRoute />}>

                            {/* Student-only routes */}
                            <Route element={<ProtectedRoute allowedRoles={['STUDENT', 'ADMIN']} />}>
                                <Route path="/" element={<HomeStudent />} />
                            </Route>

                            {/* Admin-only routes */}
                            <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                                <Route path="/admin" element={<Dashboard />} />
                            </Route>

                            {/* Catch-all route for undefined URLs (404 Not Found) */}
                            <Route path="*" element={<NotFoundPage />} />
                        </Route>
                    </Route>
                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
};

export default App;