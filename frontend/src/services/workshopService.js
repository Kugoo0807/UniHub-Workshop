import axiosClient from '../api/axiosClient';

const WORKSHOP_API = '/workshops';

const workshopService = {
  /**
   * Fetch all available workshops
   */
  getAll: async () => {
    const response = await axiosClient.get(WORKSHOP_API);
    return response;
  },

  /**
   * Fetch a single workshop by ID
   */
  getById: async (workshopId) => {
    const response = await axiosClient.get(`${WORKSHOP_API}/${workshopId}`);
    return response;
  },

  /**
   * Get workshops for current user (registered/completed)
   */
  getUserWorkshops: async () => {
    const response = await axiosClient.get(`${WORKSHOP_API}/my-workshops`);
    return response;
  },
};

export default workshopService;
