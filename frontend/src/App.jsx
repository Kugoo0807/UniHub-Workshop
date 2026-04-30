import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import Layout from './components/layouts/Layout';
import ProtectedRoute from './routes/ProtectedRoute';
import GuestRoute from './routes/GuestRoute';

// Pages
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import HomeStudent from './pages/HomeStudent';
import Dashboard from './pages/Dashboard';
import NotFoundPage from './pages/NotFoundPage';
import WorkshopManagement from './pages/admin/WorkshopManagement';

const App = () => {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>
                    <Route element={<Layout />}>
                        {/* Always accessible public routes */}
                        <Route path="/" element={<HomePage />} />
                        <Route path="/unauthorized" element={<UnauthorizedPage />} />

                        {/* Guest-only routes (redirects to dashboard if already logged in) */}
                        <Route element={<GuestRoute />}>
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/register" element={<RegisterPage />} />
                        </Route>

                        {/* Student-only routes */}
                        <Route element={<ProtectedRoute allowedRoles={['STUDENT']} />}>
                            <Route path="/student" element={<HomeStudent />} />
                        </Route>

                        {/* Admin-only routes */}
                        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
                            <Route path="/admin" element={<Dashboard />} />
                            <Route path="/admin/workshops" element={<WorkshopManagement />} />
                        </Route>

                        {/* Catch-all route for undefined URLs (404 Not Found) */}
                        <Route path="*" element={<NotFoundPage />} />
                    </Route>
                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
};

export default App;