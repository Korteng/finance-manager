export interface Category {
    id: number
    name: string
}

export interface Transaction {
    id: number
    amount: number
    currency: string
    categoryId?: number
    category?: Category
    description?: string
    createdAt: string
}

export interface Budget {
    id: number
    categoryId: number
    categoryName: string
    period: string
    limitAmount: number
    spent: number
    exceeded: boolean
}

export interface AuthResponse {
    token: string
}
