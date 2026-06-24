const DEFAULT_REDIRECT = '/dashboard'

const isSafeInternalPath = (value) => (
  typeof value === 'string'
  && value.startsWith('/')
  && !value.startsWith('//')
  && !value.startsWith('/login')
  && !value.startsWith('/register')
)

export const resolveLoginRedirect = (route, fallback = DEFAULT_REDIRECT) => {
  const redirect = route?.query?.redirect
  const value = Array.isArray(redirect) ? redirect[0] : redirect
  return isSafeInternalPath(value) ? value : fallback
}

export const toLoginWithRedirect = (targetPath) => {
  const redirect = isSafeInternalPath(targetPath) ? targetPath : DEFAULT_REDIRECT
  return {
    path: '/login',
    query: { redirect }
  }
}
