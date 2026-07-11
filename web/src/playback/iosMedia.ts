export interface NavigatorMediaIdentity {
  userAgent: string
  platform: string
  maxTouchPoints: number
}

/**
 * True for iPhone/iPad browsers and home-screen apps. Modern iPads identify
 * as MacIntel, so touch capability is required for that branch.
 */
export function isIOSMediaEnvironment(
  identity: NavigatorMediaIdentity = navigator,
): boolean {
  return (
    /iPad|iPhone|iPod/.test(identity.userAgent) ||
    (identity.platform === 'MacIntel' && identity.maxTouchPoints > 1)
  )
}
