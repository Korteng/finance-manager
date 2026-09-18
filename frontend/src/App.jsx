import { useEffect, useState } from 'react'
import { api } from './api.js'
import LoginForm from './components/LoginForm.jsx'
import TransactionsTab from './components/TransactionsTab.jsx'
import BudgetsTab from './components/BudgetsTab.jsx'

export default function App() {
  // Токен живёт только в памяти вкладки (не localStorage) - осознанное решение:
  // для демо-приложения это безопаснее и проще, чем городить хранение и его инвалидацию.
  // Цена - разлогин при обновлении страницы, для пет-проекта это ок.
  const [token, setToken] = useState(null)
  const [categories, setCategories] = useState([])
  const [tab, setTab] = useState('transactions')

  useEffect(() => {
    if (!token) return
    api.getCategories(token).then(setCategories).catch(() => setCategories([]))
  }, [token])

  async function handleLogout() {
    try {
      await api.logout(token)
    } catch {
      // токен мог уже протухнуть - на выход это не влияет
    }
    setToken(null)
    setTab('transactions')
  }

  if (!token) {
    return <LoginForm onAuth={setToken} />
  }

  return (
      <div>
        <div className="topbar">
          <h1 style={{ margin: 0 }}>Finance Manager</h1>
          <button className="secondary" onClick={handleLogout}>Выйти</button>
        </div>

        <div className="tabs">
          <button
              className={`tab ${tab === 'transactions' ? 'active' : ''}`}
              onClick={() => setTab('transactions')}
          >
            Транзакции
          </button>
          <button
              className={`tab ${tab === 'budgets' ? 'active' : ''}`}
              onClick={() => setTab('budgets')}
          >
            Бюджеты
          </button>
        </div>

        {categories.length === 0 && (
            <div className="card muted">
              Категорий пока нет - без них нельзя создать транзакцию или бюджет.
            </div>
        )}

        {categories.length > 0 && tab === 'transactions' && (
            <TransactionsTab token={token} categories={categories} />
        )}
        {categories.length > 0 && tab === 'budgets' && (
            <BudgetsTab token={token} categories={categories} />
        )}
      </div>
  )
}
