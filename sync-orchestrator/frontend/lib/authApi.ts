import axios from 'axios';

export interface AuthUser {
  id: number;
  username: string;
  name: string;
  createdAt: string;
}

const authClient = axios.create({
  baseURL: '/auth',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

export const authApi = {
  login: (username: string, password: string) =>
    authClient.post<AuthUser>('/login', { username, password }).then((r) => r.data),

  logout: () => authClient.post('/logout'),

  refresh: () => authClient.post('/refresh'),

  me: () => authClient.get<AuthUser>('/me').then((r) => r.data),

  // 사용자 관리 (peer multiplication)
  listUsers: () => authClient.get<AuthUser[]>('/users').then((r) => r.data),

  addUser: (username: string, password: string, name: string) =>
    authClient.post<AuthUser>('/users', { username, password, name }).then((r) => r.data),

  changeMyPassword: (currentPassword: string, newPassword: string) =>
    authClient.patch('/users/me/password', { currentPassword, newPassword }),

  deleteMe: () => authClient.delete('/users/me'),
};

export default authClient;
