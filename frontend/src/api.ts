import type { AuthResponse, Budget, Category, Transaction } from './types'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

interface RequestOptions {
    method?: string
    token?: string | null
    body?: unknown
}

async function request<T>(path: string, { method = 'GET', token, body }: RequestOptions = {}): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    const response = await fetch(`${BASE_URL}${path}`, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
    })

    if (!response.ok) {
        let message = `Ошибка ${response.status}`
        try {
            const data = await response.json()
            if (data?.message) message = data.message
        } catch {
            // тело ответа не JSON (или пустое) - оставляем дефолтное сообщение
        }
        throw new Error(message)
    }

    if (response.status === 204) return null as T
    return response.json()
}

export interface CreateTransactionRequest {
    amount: number
    currency: string
    categoryId: number
    description?: string
}

export interface SetBudgetRequest {
    categoryId: number
    period: string
    limitAmount: number
}

export const api = {
    login: (username: string, password: string) =>
        request<AuthResponse>('/api/auth/login', { method: 'POST', body: { username, password } }),

    register: (username: string, password: string) =>
        request<AuthResponse>('/api/auth/register', { method: 'POST', body: { username, password } }),

    logout: (token: string | null) =>
        request<null>('/api/auth/logout', { method: 'POST', token }),

    getCategories: (token: string) => request<Category[]>('/api/categories', { token }),

    listTransactions: (token: string, { categoryId }: { categoryId?: number | string } = {}) => {
        const params = new URLSearchParams()
        if (categoryId) params.set('categoryId', String(categoryId))
        const qs = params.toString()
        return request<Transaction[]>(`/api/transactions${qs ? `?${qs}` : ''}`, { token })
    },

    createTransaction: (token: string, body: CreateTransactionRequest) =>
        request<Transaction>('/api/transactions', { method: 'POST', token, body }),

    listBudgets: (token: string, period?: string) =>
        request<Budget[]>(`/api/budgets${period ? `?period=${period}` : ''}`, { token }),

    setBudget: (token: string, body: SetBudgetRequest) =>
        request<Budget>('/api/budgets', { method: 'POST', token, body }),
}
