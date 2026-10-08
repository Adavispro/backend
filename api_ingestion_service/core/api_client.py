"""Dataset requests using standard library urllib and bearer token authentication."""

from __future__ import annotations

import base64
import json
import socket
import ssl
import time
from typing import Any, Dict
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class PlantAPIClient:
    def __init__(self, config: Dict[str, Any]):
        self.config = config
        self.timeout = int(config.get("request_timeout_seconds", 60))
        self.ssl_context = ssl.create_default_context()
        if not config.get("verify_tls", False):
            self.ssl_context.check_hostname = False
            self.ssl_context.verify_mode = ssl.CERT_NONE
        self.token = config.get("bearer_token", "").strip()
        self.use_mock = config.get("use_mock_service", False)

        if not self.use_mock:
            if not self.token or self.token.startswith("PASTE_"):
                raise ValueError("Set api_fetch.bearer_token in config/ingestion_config.json or enable use_mock_service")
            if self.token.lower().startswith("bearer ") or any(char.isspace() for char in self.token):
                raise ValueError("Paste only the bearer token value, without the Bearer prefix or spaces")
            self._check_expiry()

    def _check_expiry(self) -> None:
        """Read JWT expiry for an actionable error; this does not verify the signature."""
        if self.use_mock:
            return
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

    def get_dataset(self, point_name: str) -> bytes:
        base = self.config["base_url"].rstrip("/")
        path = self.config.get("dataset_path", "/fwxapi/rest/v1/Dataset")
        url = base + (path if path.startswith("/") else "/" + path) + "?" + urlencode({"pointName": point_name})
        
        if not self.use_mock:
            self._check_expiry()

        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {self.token}",
        }

        request = Request(url, headers=headers)
        try:
            with urlopen(request, timeout=self.timeout, context=self.ssl_context) as response:
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
