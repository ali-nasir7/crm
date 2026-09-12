import { Component, type ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { error: Error | null }

/**
 * Catches any runtime rendering error and shows the message on screen instead of
 * an unexplained white page. In production this should feed a monitoring system.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: unknown) {
    // eslint-disable-next-line no-console
    console.error('UI crashed:', error, info)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-slate-100 p-6">
          <div className="max-w-xl rounded-2xl border border-red-200 bg-white p-8 shadow-lg">
            <h1 className="text-xl font-bold text-red-700">The screen failed to render</h1>
            <p className="mt-2 text-sm text-slate-600">
              This is a bug, not your fault. Show the text below to support / development:
            </p>
            <pre className="mt-4 max-h-64 overflow-auto rounded-lg bg-red-50 p-3 text-xs whitespace-pre-wrap text-red-800">
              {this.state.error.message}
              {'\n\n'}
              {this.state.error.stack}
            </pre>
            <button
              onClick={() => { localStorage.removeItem('crm.accessToken'); localStorage.removeItem('crm.refreshToken'); window.location.href = '/login' }}
              className="mt-4 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
            >
              Clear session and go to login
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
