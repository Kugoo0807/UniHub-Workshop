import axiosClient from '../api/axiosClient';

const WORKSHOP_API = '/workshops';
const DEFAULT_PAGE_SIZE = 12;

const workshopService = {
  /**
   * Fetch paginated published workshops.
   * Returns { content, page, size, totalElements, totalPages, last }
   */
  getAll: async (page = 0, size = DEFAULT_PAGE_SIZE) => {
    const response = await axiosClient.get(WORKSHOP_API, {
      params: { page, size },
    });
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
   * Get paginated registrations for the current user.
   * Returns { content, page, size, totalElements, totalPages, last }
   */
  getUserWorkshops: async (page = 0, size = DEFAULT_PAGE_SIZE) => {
    const response = await axiosClient.get(`${WORKSHOP_API}/my-workshops`, {
      params: { page, size },
    });
    return response;
  },
};

export default workshopService;
