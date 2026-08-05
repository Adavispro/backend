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

const optionalServiceUrl = (name: string, value: string | undefined, fallback: string) =>
  requiredUrl(name, value ?? fallback);

const gatewayUrl = requiredUrl(
  "API_GATEWAY_URL",
  process.env.API_GATEWAY_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL,
);

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
