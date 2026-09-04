import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Sparkles, TrendingUp, Users, Zap } from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { api, apiError } from '@/api/client'
import { Input, Label, FieldError } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const { login } = useAuth()
    const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const [onboarding, setOnboarding] = useState<{ email: string; tempPassword: string } | null>(null)
  const [newPassword, setNewPassword] = useState('')
  const [activating, setActivating] = useState(false)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = async (values: FormValues) => {
    setServerError(null)
    try {
      await login(values.email, values.password)
      navigate('/dashboard')
    } catch (err) {
      const e = apiError(err)
      if (e.code === 'PASSWORD_CHANGE_REQUIRED') {
        setOnboarding({ email: values.email, tempPassword: values.password })
        setServerError(null)
        return
      }
      setServerError(e.message + (e.details && Object.keys(e.details).length ? ` (${Object.values(e.details)[0]})` : ''))
    }
  }

  const activate = async () => {
    if (!onboarding) return
    setServerError(null)
    if (newPassword.length < 10 || !(/[a-zA-Z]/.test(newPassword) && /\d/.test(newPassword))) {
      setServerError('New password must be at least 10 characters and contain letters and digits')
      return
    }
    setActivating(true)
    try {
      await api.post('/auth/complete-onboarding', { email: onboarding.email, tempPassword: onboarding.tempPassword, newPassword })
      await login(onboarding.email, newPassword)
      navigate('/dashboard')
    } catch (err) {
      setServerError(apiError(err).message)
    } finally {
      setActivating(false)
    }
  }

  return (
    <div className="flex min-h-screen">
      {/* Left brand panel */}
      <div className="relative hidden flex-1 flex-col justify-between overflow-hidden bg-slate-900 p-10 lg:flex">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600 text-lg font-extrabold text-white">N</div>
          <div>
            <p className="font-bold text-white">Nexus CRM</p>
            <p className="text-xs text-slate-400">Sales Operating System</p>
          </div>
        </div>
        <div className="relative z-10 max-w-md">
          <h1 className="text-3xl font-extrabold leading-tight text-white">
            Every lead, follow-up, and deal — <span className="text-blue-400">in one system.</span>
          </h1>
          <p className="mt-4 text-slate-400">
            Pipeline management, imports, email sequences, scoring, automation, and reporting — built for teams that run on outbound.
          </p>
          <div className="mt-8 space-y-3 text-sm text-slate-300">
            <p className="flex items-center gap-2.5"><Users className="h-4 w-4 text-blue-400" /> 14-stage configurable pipelines with weighted forecasting</p>
            <p className="flex items-center gap-2.5"><Zap className="h-4 w-4 text-blue-400" /> Lead scoring, automation, and campaign sequences</p>
            <p className="flex items-center gap-2.5"><TrendingUp className="h-4 w-4 text-blue-400" /> Executive and per-rep performance dashboards</p>
            <p className="flex items-center gap-2.5"><Sparkles className="h-4 w-4 text-blue-400" /> Reviewable AI assistance for summaries and drafts</p>
          </div>
        </div>
        <div className="pointer-events-none absolute -bottom-40 -right-24 h-96 w-96 rounded-full bg-blue-600/20 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-20 -right-52 h-80 w-80 rounded-full bg-cyan-500/10 blur-3xl" />
      </div>

      {/* Form */}
      <div className="flex flex-1 items-center justify-center bg-white px-6">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600 text-lg font-extrabold text-white">N</div>
            <p className="font-bold text-slate-900">Nexus CRM</p>
          </div>
          {onboarding ? (
            <>
              <h2 className="text-2xl font-bold text-slate-900">Activate your account</h2>
              <p className="mt-1 text-sm text-slate-500">
                Your account uses a temporary password. Choose your own password to continue.
              </p>
              <div className="mt-6 space-y-4">
                <div>
                  <Label htmlFor="newPassword" required>New password</Label>
                  <Input id="newPassword" type="password" autoComplete="new-password" placeholder="At least 10 characters, letters and digits" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
                </div>
                <Button type="button" className="w-full" loading={activating} onClick={activate}>Set password and sign in</Button>
                <button type="button" className="w-full text-xs text-slate-500 hover:text-slate-700" onClick={() => { setOnboarding(null); setNewPassword(''); setServerError(null) }}>Back to sign in</button>
              </div>
            </>
          ) : (
            <>
          <h2 className="text-2xl font-bold text-slate-900">Sign in</h2>
          <p className="mt-1 text-sm text-slate-500">Welcome back. Enter your credentials to continue.</p>

          {serverError && (
            <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">{serverError}</div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
            <div>
              <Label htmlFor="email" required>Email</Label>
              <Input id="email" type="email" autoComplete="email" placeholder="you@company.com" {...register('email')} />
              <FieldError message={errors.email?.message} />
            </div>
            <div>
              <Label htmlFor="password" required>Password</Label>
              <Input id="password" type="password" autoComplete="current-password" placeholder="••••••••••" {...register('password')} />
              <FieldError message={errors.password?.message} />
            </div>
            <Button type="submit" className="w-full" loading={isSubmitting}>Sign in</Button>
          </form>
            </>
          )}

        </div>
      </div>
    </div>
  )
}
