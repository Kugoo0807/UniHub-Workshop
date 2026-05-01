import axiosClient from '../api/axiosClient';

const listWorkshops = async () => {
    return await axiosClient.get('/students/workshops');
};

const registerFree = async (workshopId) => {
    return await axiosClient.post('/registrations/free', { workshopId });
};

const initiatePaid = async (workshopId) => {
    return await axiosClient.post('/registrations/paid', { workshopId });
};

const processPayment = async (payload) => {
    return await axiosClient.post('/registrations/payments', payload);
};

export default {
    listWorkshops,
    registerFree,
    initiatePaid,
    processPayment,
};
