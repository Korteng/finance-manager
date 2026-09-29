import { useEffect, useState } from 'react'
import { api } from '../api.js'

export default function TransactionsTab({ token, categories }) {
    const [transactions, setTransactions] = useState([])
    const [filterCategoryId, setFilterCategoryId] = useState('')
    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)

    const [amount, setAmount] = useState('')
    const [currency, setCurrency] = useState('RUB')
    const [categoryId, setCategoryId] = useState(categories[0]?.id ?? '')
    const [description, setDescription] = useState('')
    const [submitting, setSubmitting] = useState(false)

    async function loadTransactions() {
        setLoading(true)
        setError(null)
        try {
            const data = await api.listTransactions(token, {
                categoryId: filterCategoryId || undefined,
            })
            setTransactions(data)
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
    useEffect(() => { loadTransactions() }, [filterCategoryId])

    async function handleSubmit(e) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await api.createTransaction(token, {
                amount: Number(amount),
                currency,
                categoryId: Number(categoryId),
                description: description || undefined,
            })
            setAmount('')
            setDescription('')
            await loadTransactions()
        } catch (err) {
            setError(err.message)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <>
            <div className="card">
                <h2 style={{ marginTop: 0, fontSize: 16 }}>Новая транзакция</h2>
                <form onSubmit={handleSubmit}>
                    <label>
                        Сумма
                        <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value)}
                            required
                        />
                    </label>
                    <label>
                        Валюта
                        <input
                            value={currency}
                            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                            maxLength={3}
                            required
                        />
                    </label>
                    <label>
                        Категория
                        <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
                            {categories.map((c) => (
                                <option key={c.id} value={c.id}>{c.name}</option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Комментарий
                        <input value={description} onChange={(e) => setDescription(e.target.value)} />
                    </label>

                    {error && <div className="error">{error}</div>}

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Сохраняю...' : 'Добавить'}
                    </button>
                </form>
            </div>

            <div className="card">
                <div className="topbar" style={{ marginBottom: 12 }}>
                    <h2 style={{ margin: 0, fontSize: 16 }}>История</h2>
                    <select value={filterCategoryId} onChange={(e) => setFilterCategoryId(e.target.value)}>
                        <option value="">Все категории</option>
                        {categories.map((c) => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                    </select>
                </div>

                {loading && <div className="muted">Загрузка...</div>}
                {!loading && transactions.length === 0 && <div className="muted">Пока пусто</div>}

                {transactions.map((t) => (
                    <div className="row" key={t.id}>
                        <div>
                            <div>{t.category?.name ?? '—'}{t.description ? ` · ${t.description}` : ''}</div>
                            <div className="muted">{new Date(t.createdAt).toLocaleString('ru-RU')}</div>
                        </div>
                        <div>{t.amount} {t.currency}</div>
                    </div>
                ))}
            </div>
        </>
    )
}