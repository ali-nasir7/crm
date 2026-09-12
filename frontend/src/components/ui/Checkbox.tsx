import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export const Checkbox = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      type="checkbox"
      ref={ref}
      className={cn('h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500', className)}
      {...props}
    />
  ),
)
Checkbox.displayName = 'Checkbox'
