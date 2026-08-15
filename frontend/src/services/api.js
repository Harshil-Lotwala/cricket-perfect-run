const baseUrl = (import.meta.env.VITE_API_URL || "/api").replace(/\/$/, "");
const timeoutMs = 120_000;
const connectionRetries = 4;

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(path, options = {}) {
  let attempt = 0;
  while (true) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(`${baseUrl}${path}`, {
        ...options,
        headers: {
          ...(options.body ? { "Content-Type": "application/json" } : {}),
          ...options.headers,
        },
        signal: controller.signal,
      });
      const contentType = response.headers.get("content-type") || "";
      const data = contentType.includes("application/json")
        ? await response.json()
        : await response.text();
      if (!response.ok) {
        const error = new Error(typeof data === "string" ? data : `HTTP ${response.status}`);
        error.status = response.status;
        error.response = { status: response.status, data };
        throw error;
      }
      return { data, status: response.status };
    } catch (error) {
      // Retry only genuine connection failures. HTTP errors are valid backend responses.
      if (error.response || error.name === "AbortError" || !(error instanceof TypeError) || attempt >= connectionRetries) throw error;
      attempt += 1;
      await delay(attempt * 750);
    } finally {
      clearTimeout(timeout);
    }
  }
}

const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: "POST", body: JSON.stringify(body) }),
};

export default api;
