import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "/api",
  timeout: 120_000,
});

// The frontend and backend are commonly started in separate terminals. If Vite becomes ready a
// moment before Spring Boot, retry connection failures instead of leaving the draft permanently
// stuck until the user manually refreshes the page.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;
    const isConnectionFailure = !error.response && config;
    if (!isConnectionFailure || (config.__retryCount || 0) >= 4) {
      return Promise.reject(error);
    }

    config.__retryCount = (config.__retryCount || 0) + 1;
    await new Promise((resolve) => setTimeout(resolve, config.__retryCount * 750));
    return api(config);
  }
);

export default api;
