import "server-only";

const requiredUrl = (name: string, value: string | undefined) => {
  if (!value) {
    throw new Error(`${name} is required.`);
  }

  try {
    return new URL(value).origin;
  } catch {
    throw new Error(`${name} must be a valid absolute URL.`);
  }
};

const readEnv = (name: string) => {
  const value = process.env[name];
  if (!value) return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

const optionalServiceUrl = (name: string, value: string | undefined, fallback: string) =>
  requiredUrl(name, value ?? fallback);

const resolveGatewayUrl = () => {
  const configuredGatewayUrl =
    readEnv("API_GATEWAY_URL") ?? readEnv("NEXT_PUBLIC_API_BASE_URL");

  if (configuredGatewayUrl) {
    return requiredUrl("API_GATEWAY_URL", configuredGatewayUrl);
  }

  // Build-time rendering can execute API route modules before env files are provided.
  // Use host-local defaults so build succeeds, while allowing runtime env overrides.
  const fallbackUrl =
    process.env.NODE_ENV === "production"
      ? "http://127.0.0.1"
      : "http://localhost:9080";

  return requiredUrl("API_GATEWAY_URL", fallbackUrl);
};

const gatewayUrl = resolveGatewayUrl();

export const SERVER_API_CONFIG = {
  gatewayUrl,
  authServiceUrl: optionalServiceUrl(
    "AUTH_SERVICE_URL",
    process.env.AUTH_SERVICE_URL,
    gatewayUrl,
  ),
  mdmServiceUrl: optionalServiceUrl("MDM_SERVICE_URL", process.env.MDM_SERVICE_URL, gatewayUrl),
  iiotServiceUrl: optionalServiceUrl(
    "IIOT_SERVICE_URL",
    process.env.IIOT_SERVICE_URL,
    gatewayUrl,
  ),
  licenseServiceUrl: optionalServiceUrl(
    "LICENSE_SERVICE_URL",
    process.env.LICENSE_SERVICE_URL,
    gatewayUrl,
  ),
  auditServiceUrl: optionalServiceUrl(
    "AUDIT_SERVICE_URL",
    process.env.AUDIT_SERVICE_URL,
    gatewayUrl,
  ),
} as const;
