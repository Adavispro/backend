"""Dataset requests using a fixed bearer token supplied in configuration."""

import base64
import json
import socket
import ssl
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class APIClient:
    def __init__(self, config):
        self.config = config
        self.timeout = int(config["request_timeout_seconds"])
        self.token = config.get("bearer_token", "").strip()
        if not self.token or self.token.startswith("PASTE_"):
            raise ValueError("Set api_fetch.bearer_token in config/fetch_config.json")
        if self.token.lower().startswith("bearer ") or any(char.isspace() for char in self.token):
            raise ValueError("Paste only the bearer token value, without the Bearer prefix or spaces")
        self._check_expiry()

    def _check_expiry(self):
        """Read JWT expiry for an actionable error; this does not verify the signature."""
        parts = self.token.split(".")
        if len(parts) != 3:
            return
        try:
            payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
            expires_at = payload.get("exp")
        except (ValueError, TypeError):
            return
        if expires_at is None:
            return
        try:
            expiry = float(expires_at)
        except (TypeError, ValueError):
            return
        if time.time() >= expiry:
            raise ValueError("Configured bearer token has expired; paste a fresh token")

    def get_dataset(self, point_name):
        base = self.config["base_url"].rstrip("/")
        path = self.config["dataset_path"]
        url = base + (path if path.startswith("/") else "/" + path) + "?" + urlencode({"pointName": point_name})
        self._check_expiry()
        request = Request(url, headers={
            "Accept": "application/json",
            "Authorization": f"Bearer {self.token}",
        })
        try:
            with urlopen(request, timeout=self.timeout) as response:
                return response.read()
        except HTTPError as error:
            if error.code in (401, 403):
                raise RuntimeError(f"Dataset API returned HTTP {error.code}; check the bearer token and its permissions") from None
            raise RuntimeError(f"Dataset API returned HTTP {error.code}") from None
        except URLError as error:
            reason = error.reason
            if isinstance(reason, socket.gaierror):
                detail = "API host name could not be resolved (DNS)"
            elif isinstance(reason, ssl.SSLCertVerificationError):
                detail = "TLS certificate verification failed"
            elif isinstance(reason, ssl.SSLError):
                detail = "TLS handshake failed"
            elif isinstance(reason, ConnectionRefusedError):
                detail = "connection refused on the API port"
            elif isinstance(reason, TimeoutError):
                detail = "connection timed out"
            elif isinstance(reason, OSError):
                detail = f"network error {reason.errno or type(reason).__name__}"
            else:
                detail = str(reason) or "unknown network error"
            raise RuntimeError(f"Dataset API connection failed: {detail}") from None
