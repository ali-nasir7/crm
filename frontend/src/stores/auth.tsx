import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, TOKEN_KEY, REFRESH_KEY } from '@/api/client'
import type { TokenResponse, UserInfo } from '@/types'

interface AuthState {
  user: UserInfo | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshMe: () => Promise<void>
  can: (permission: string) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) { setLoading(false); return }
    api.get<UserInfo>('/auth/me')
      .then((res) => setUser(res.data))
      .catch(() => { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(REFRESH_KEY) })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<TokenResponse>('/auth/login', { email, password })
    localStorage.setItem(TOKEN_KEY, res.data.accessToken)
    localStorage.setItem(REFRESH_KEY, res.data.refreshToken)
    setUser(res.data.user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout', { refreshToken: localStorage.getItem(REFRESH_KEY) })
    } catch { /* token already invalid */ }
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    setUser(null)
  }, [])

  const refreshMe = useCallback(async () => {
    const res = await api.get<UserInfo>('/auth/me')
    setUser(res.data)
  }, [])

  const can = useCallback(
    (permission: string) => !!user && (user.superAdmin || user.permissions.includes(permission)),
    [user],
  )

  const value = useMemo(() => ({ user, loading, login, logout, refreshMe, can }), [user, loading, login, logout, refreshMe, can])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export function useCan(permission: string): boolean {
  const { can } = useAuth()
  return can(permission)
}
