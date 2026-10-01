import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import type { Budget, Category } from '../types'

interface BudgetsTabProps {
    token: string
    categories: Category[]
}

function currentPeriod(): string {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export default function BudgetsTab({ token, categories }: BudgetsTabProps) {
    const [period, setPeriod] = useState(currentPeriod())
    const [budgets, setBudgets] = useState<Budget[]>([])
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    const [categoryId, setCategoryId] = useState<number | string>(categories[0]?.id ?? '')
    const [limitAmount, setLimitAmount] = useState('')
    const [submitting, setSubmitting] = useState(false)

    async function loadBudgets() {
        setLoading(true)
        setError(null)
        try {
            setBudgets(await api.listBudgets(token, period))
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setLoading(false)
        }
    }

    // осознанный паттерн "загрузить данные при смене зависимости" - не бесконечный цикл
    // eslint-disable-next-line react-hooks/exhaustive-deps, react-hooks/set-state-in-effect
    useEffect(() => { loadBudgets() }, [period])

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await api.setBudget(token, {
                categoryId: Number(categoryId),
                period,
                limitAmount: Number(limitAmount),
            })
            setLimitAmount('')
            await loadBudgets()
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <>
            <div className="card">
                <h2 style={{ marginTop: 0, fontSize: 16 }}>Лимит на месяц</h2>
                <form onSubmit={handleSubmit}>
                    <label>
                        Месяц
                        <input type="month" value={period} onChange={(e) => setPeriod(e.target.value)} required />
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
                        Лимит
                        <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={limitAmount}
                            onChange={(e) => setLimitAmount(e.target.value)}
                            required
                        />
                    </label>

                    {error && <div className="error">{error}</div>}

                    <button type="submit" disabled={submitting}>
                        {submitting ? 'Сохраняю...' : 'Сохранить лимит'}
                    </button>
                </form>
            </div>

            <div className="card">
                <h2 style={{ marginTop: 0, fontSize: 16 }}>Бюджеты за {period}</h2>

                {loading && <div className="muted">Загрузка...</div>}
                {!loading && budgets.length === 0 && <div className="muted">Лимиты не заданы</div>}

                {budgets.map((b) => (
                    <div className="row" key={b.id}>
                        <div>
                            <div>{b.categoryName}</div>
                            <div className="muted">{b.spent} из {b.limitAmount}</div>
                        </div>
                        <span className={`badge ${b.exceeded ? 'exceeded' : 'ok'}`}>
              {b.exceeded ? 'превышен' : 'в норме'}
            </span>
                    </div>
                ))}
            </div>
        </>
    )
}
