import { useState, type FormEvent } from 'react'
import { api } from '../api'

type Mode = 'login' | 'register'

interface LoginFormProps {
    onAuth: (token: string) => void
}

/**
 * Один экран на вход и регистрацию - переключаются радиокнопкой, чтобы не городить
 * роутер ради одной лишней страницы.
 */
export default function LoginForm({ onAuth }: LoginFormProps) {
    const [mode, setMode] = useState<Mode>('login')
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        setError(null)
        setLoading(true)
        try {
            const { token } = mode === 'login'
                ? await api.login(username, password)
                : await api.register(username, password)
            onAuth(token)
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="card login-card">
            <h1>Finance Manager</h1>

            <div className="tabs">
                <button
                    type="button"
                    className={`tab ${mode === 'login' ? 'active' : ''}`}
                    onClick={() => setMode('login')}
                >
                    Вход
                </button>
                <button
                    type="button"
                    className={`tab ${mode === 'register' ? 'active' : ''}`}
                    onClick={() => setMode('register')}
                >
                    Регистрация
                </button>
            </div>

            <form onSubmit={handleSubmit}>
                <label>
                    Логин
                    <input
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                        required
                        autoFocus
                    />
                </label>
                <label>
                    Пароль
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />
                </label>

                {error && <div className="error">{error}</div>}

                <button type="submit" disabled={loading}>
                    {loading ? 'Подождите...' : mode === 'login' ? 'Войти' : 'Создать аккаунт'}
                </button>
            </form>
        </div>
    )
}
