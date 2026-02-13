import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 responses globally
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      // Only redirect if not already on login page
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// ---------- Auth API ----------
export interface AuthRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  role: string;
}

export const authApi = {
  login: (data: AuthRequest) => apiClient.post<AuthResponse>('/auth/login', data),
  register: (data: AuthRequest) => apiClient.post<AuthResponse>('/auth/register', data),
};

// ---------- Query API ----------
export interface QueryRequest {
  connectionId: number;
  question: string;
}

export interface QueryResponse {
  sql: string;
  result: Record<string, unknown>[];
  safetyCheck: string;
}

export const queryApi = {
  ask: (data: QueryRequest) => apiClient.post<QueryResponse>('/query/ask', data),
  getMyConnections: () => apiClient.get<ConnectionInfo[]>('/query/connections'),
};

// ---------- Admin Connections API ----------
export interface ConnectionInfo {
  id: number;
  name: string;
  type: 'MYSQL' | 'POSTGRESQL' | 'H2';
  url: string;
  username: string;
  encryptedPassword?: string;
  schemaDdl?: string;
}

export interface ConnectionRequest {
  name: string;
  type: 'MYSQL' | 'POSTGRESQL' | 'H2';
  url: string;
  username: string;
  password: string;
}

export const connectionsApi = {
  list: () => apiClient.get<ConnectionInfo[]>('/admin/connections'),
  get: (id: number) => apiClient.get<ConnectionInfo>(`/admin/connections/${id}`),
  create: (data: ConnectionRequest) => apiClient.post<ConnectionInfo>('/admin/connections', data),
  remove: (id: number) => apiClient.delete(`/admin/connections/${id}`),
  refreshSchema: (id: number) => apiClient.post<ConnectionInfo>(`/admin/connections/${id}/refresh-schema`),
};

// ---------- Admin Permissions API ----------
export interface PermissionInfo {
  id: number;
  userId: number;
  connectionId: number;
}

export interface PermissionRequest {
  userId: number;
  connectionId: number;
}

export const permissionsApi = {
  grant: (data: PermissionRequest) => apiClient.post<PermissionInfo>('/admin/permissions', data),
  revoke: (userId: number, connectionId: number) =>
    apiClient.delete('/admin/permissions', { params: { userId, connectionId } }),
  byUser: (userId: number) => apiClient.get<PermissionInfo[]>(`/admin/permissions/user/${userId}`),
  byConnection: (connectionId: number) =>
    apiClient.get<PermissionInfo[]>(`/admin/permissions/connection/${connectionId}`),
};
