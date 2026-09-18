const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

async function request(path, { method = 'GET', token, body } = {}) {
    const headers = { 'Content-Type': 'application/json' }
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

    if (response.status === 204) return null
    return response.json()
}

export const api = {
    login: (username, password) =>
        request('/api/auth/login', { method: 'POST', body: { username, password } }),

    register: (username, password) =>
        request('/api/auth/register', { method: 'POST', body: { username, password } }),

    logout: (token) =>
        request('/api/auth/logout', { method: 'POST', token }),

    getCategories: (token) => request('/api/categories', { token }),

    listTransactions: (token, { categoryId } = {}) => {
        const params = new URLSearchParams()
        if (categoryId) params.set('categoryId', categoryId)
        const qs = params.toString()
        return request(`/api/transactions${qs ? `?${qs}` : ''}`, { token })
    },

    createTransaction: (token, body) =>
        request('/api/transactions', { method: 'POST', token, body }),

    listBudgets: (token, period) =>
        request(`/api/budgets${period ? `?period=${period}` : ''}`, { token }),

    setBudget: (token, body) =>
        request('/api/budgets', { method: 'POST', token, body }),
}