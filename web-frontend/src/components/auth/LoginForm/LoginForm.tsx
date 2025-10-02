/**
 * LoginForm Component Implementation
 * Story 1.2: AWS Cognito Authentication UI
 */

import React, { useState, useEffect } from 'react'
import {
  Box,
  TextField,
  Button,
  Typography,
  FormControlLabel,
  Checkbox,
  Alert,
  CircularProgress,
  Link,
  Paper,
  Container
} from '@mui/material'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useAuth } from '@hooks/useAuth'
import { LoginCredentials } from '@types/auth'

// Validation schema
const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required')
    .email('Please enter a valid email'),
  password: z
    .string()
    .min(1, 'Password is required'),
  rememberMe: z.boolean().default(false)
})

type LoginFormData = z.infer<typeof loginSchema>

interface LoginFormProps {
  onSuccess?: () => void
  onForgotPassword?: () => void
  onSignUp?: () => void
}

export const LoginForm: React.FC<LoginFormProps> = ({
  onSuccess,
  onForgotPassword,
  onSignUp
}) => {
  const { signIn, isLoading, error, clearError } = useAuth()
  const [submitError, setSubmitError] = useState<string | null>(null)

  const {
    control,
    handleSubmit,
    formState: { errors, isValid },
    watch,
    reset
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
      rememberMe: false
    },
    mode: 'onBlur'
  })

  // Clear errors when user starts typing
  const watchedEmail = watch('email')
  const watchedPassword = watch('password')

  useEffect(() => {
    if (error || submitError) {
      clearError()
      setSubmitError(null)
    }
  }, [watchedEmail, watchedPassword, error, submitError, clearError])

  const onSubmit = async (data: LoginFormData) => {
    try {
      setSubmitError(null)

      const credentials: LoginCredentials = {
        email: data.email,
        password: data.password,
        rememberMe: data.rememberMe
      }

      const success = await signIn(credentials)

      if (success) {
        reset()
        onSuccess?.()
      }
    } catch (err: any) {
      setSubmitError(err.message || 'An unexpected error occurred')
    }
  }

  const handleForgotPassword = () => {
    onForgotPassword?.()
  }

  const handleSignUp = () => {
    onSignUp?.()
  }

  const displayError = error?.message || submitError

  return (
    <Container maxWidth="sm">
      <Paper elevation={3} sx={{ p: 4, mt: 4 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <Typography component="h1" variant="h4" gutterBottom>
            Sign In
          </Typography>

          <Typography variant="body2" color="text.secondary" align="center" sx={{ mb: 3 }}>
            Welcome back to BATbern Platform
          </Typography>

          {displayError && (
            <Alert severity="error" sx={{ width: '100%', mb: 2 }}>
              {displayError}
            </Alert>
          )}

          <Box
            component="form"
            onSubmit={handleSubmit(onSubmit)}
            sx={{ width: '100%' }}
          >
            <Controller
              name="email"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Email"
                  type="email"
                  autoComplete="email"
                  autoFocus
                  margin="normal"
                  error={!!errors.email}
                  helperText={errors.email?.message}
                  disabled={isLoading}
                />
              )}
            />

            <Controller
              name="password"
              control={control}
              render={({ field }) => (
                <TextField
                  {...field}
                  fullWidth
                  label="Password"
                  type="password"
                  autoComplete="current-password"
                  margin="normal"
                  error={!!errors.password}
                  helperText={errors.password?.message}
                  disabled={isLoading}
                />
              )}
            />

            <Controller
              name="rememberMe"
              control={control}
              render={({ field: { value, onChange, ...field } }) => (
                <FormControlLabel
                  control={
                    <Checkbox
                      {...field}
                      checked={value}
                      onChange={(e) => onChange(e.target.checked)}
                      disabled={isLoading}
                    />
                  }
                  label="Remember me"
                  sx={{ mt: 1 }}
                />
              )}
            />

            <Button
              type="submit"
              fullWidth
              variant="contained"
              sx={{ mt: 3, mb: 2 }}
              disabled={isLoading || !isValid}
              startIcon={isLoading ? <CircularProgress size={20} /> : null}
            >
              {isLoading ? 'Signing In...' : 'Sign In'}
            </Button>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
              <Link
                component="button"
                variant="body2"
                type="button"
                onClick={handleForgotPassword}
                disabled={isLoading}
                sx={{ textDecoration: 'none' }}
              >
                Forgot password?
              </Link>

              <Link
                component="button"
                variant="body2"
                type="button"
                onClick={handleSignUp}
                disabled={isLoading}
                sx={{ textDecoration: 'none' }}
              >
                Don't have an account? Sign up
              </Link>
            </Box>
          </Box>
        </Box>
      </Paper>
    </Container>
  )
}