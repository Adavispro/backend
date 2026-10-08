"""
OAuth 2.0 & Token Authentication Manager with PKCE (Proof Key for Code Exchange).
Supports Authorization Code flow with PKCE (RFC 7636), Password Grant,
Refresh Token lifecycle, and Static Bearer tokens.
"""

from __future__ import annotations

import base64
import hashlib
import logging
import re
import secrets
import time
from datetime import datetime, timedelta
from typing import Any, Dict, Optional, Tuple
from urllib.parse import parse_qs, quote, urlencode, urlparse

import requests
import urllib3

logger = logging.getLogger("api_ingestion_service.auth")


def generate_pkce_pair(verifier_length: int = 64) -> Tuple[str, str]:
    """
    Generate PKCE code_verifier and code_challenge (RFC 7636, S256).
    code_challenge = BASE64URL-ENCODE(SHA256(code_verifier)) without padding.
    """
    # 1. Generate high-entropy cryptographically random string (unreserved characters)
    code_verifier = secrets.token_urlsafe(verifier_length)[:verifier_length]

    # 2. Compute SHA-256 hash
    sha256_hash = hashlib.sha256(code_verifier.encode("ascii")).digest()

    # 3. Base64url encode without padding
    code_challenge = base64.urlsafe_b64encode(sha256_hash).decode("ascii").rstrip("=")

    return code_verifier, code_challenge


class OAuth2TokenManager:
    def __init__(
        self,
        auth_type: str = "oauth2_pkce",  # "oauth2_pkce", "oauth2_password", "static_token", "none"
        token_url: str = "https://u3-miebmr-srv-t/fwxserverweb/security/connect/token",
        auth_url: str = "https://u3-miebmr-srv-t/fwxserverweb/security/connect/authorize",
        callback_url: str = "http://u3-miebmr-srv-t",
        client_id: str = "In_house_client",
        client_secret: str = "",
        username: str = "",
        password: str = "",
        scope: str = "openid profile fwxapi offline_access",
        static_token: str = "",
        verify_tls: bool = False,
        timeout: int = 30,
    ) -> None:
        self.auth_type = auth_type.lower()
        self.token_url = token_url
        self.auth_url = auth_url
        self.callback_url = callback_url
        self.client_id = client_id
        self.client_secret = client_secret
        self.username = username
        self.password = password
        self.scope = scope
        self.static_token = static_token
        self.verify_tls = verify_tls
        self.timeout = timeout

        if not self.verify_tls:
            urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

        self._cached_access_token: Optional[str] = None
        self._cached_refresh_token: Optional[str] = None
        self._token_expiry_epoch: float = 0.0

        # PKCE session storage
        self._current_code_verifier: Optional[str] = None

    def get_token(self, force_refresh: bool = False) -> Optional[str]:
        """Return a valid Bearer token based on the configured auth_type."""
        if self.auth_type == "none":
            return None

        if self.auth_type == "static_token":
            if self.static_token and self.static_token != "PASTE_FRESH_BEARER_TOKEN_HERE":
                return self.static_token
            return None

        # Check if cached access token is still valid (with 60s buffer)
        if not force_refresh and self._cached_access_token and time.time() < (self._token_expiry_epoch - 60):
            return self._cached_access_token

        # Try refresh token first if available
        if self._cached_refresh_token:
            refreshed = self._refresh_access_token()
            if refreshed:
                return refreshed

        # Execute selected OAuth 2.0 flow
        if self.auth_type in ("oauth2_pkce", "pkce", "authorization_code"):
            return self._authenticate_pkce()
        elif self.auth_type in ("oauth2", "oauth2_password", "password"):
            return self._request_password_grant_token()

        return None

    def build_pkce_authorization_url(self) -> Tuple[str, str]:
        """
        Construct the PKCE Authorization URL for the user/browser or automated agent.
        Returns: (authorization_url, code_verifier)
        """
        code_verifier, code_challenge = generate_pkce_pair(64)
        self._current_code_verifier = code_verifier
        state = secrets.token_urlsafe(16)

        params = {
            "response_type": "code",
            "client_id": self.client_id,
            "redirect_uri": self.callback_url,
            "code_challenge": code_challenge,
            "code_challenge_method": "S256",
            "scope": self.scope,
            "state": state,
        }
        if self.client_secret:
            params["client_secret"] = self.client_secret

        auth_url = f"{self.auth_url}?{urlencode(params)}"
        logger.debug(f"Generated PKCE Auth URL: {auth_url}")
        return auth_url, code_verifier

    def exchange_pkce_code(self, authorization_code: str, code_verifier: Optional[str] = None) -> Optional[str]:
        """
        Exchange an Authorization Code and code_verifier at the token endpoint.
        """
        verifier = code_verifier or self._current_code_verifier
        if not verifier:
            logger.error("Missing code_verifier for PKCE token exchange.")
            return None

        payload = {
            "grant_type": "authorization_code",
            "client_id": self.client_id,
            "code": authorization_code.strip(),
            "redirect_uri": self.callback_url,
            "code_verifier": verifier,
        }
        if self.client_secret:
            payload["client_secret"] = self.client_secret

        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        }

        try:
            resp = requests.post(
                self.token_url,
                data=payload,
                headers=headers,
                timeout=self.timeout,
                verify=self.verify_tls,
            )
            return self._process_token_response(resp)
        except Exception as exc:
            logger.error(f"Error exchanging authorization code: {exc}")
            return None

    def _authenticate_pkce(self) -> Optional[str]:
        """
        Automated PKCE Authentication Flow:
        Initiates a session, requests the authorization endpoint with PKCE parameters,
        authenticates with username/password, captures the redirect code, and exchanges it.
        """
        logger.info(f"Initiating OAuth 2.0 PKCE flow with {self.auth_url} for client '{self.client_id}'")
        code_verifier, code_challenge = generate_pkce_pair(64)
        self._current_code_verifier = code_verifier
        state = secrets.token_urlsafe(16)

        session = requests.Session()
        session.verify = self.verify_tls

        auth_params = {
            "response_type": "code",
            "client_id": self.client_id,
            "redirect_uri": self.callback_url,
            "code_challenge": code_challenge,
            "code_challenge_method": "S256",
            "scope": self.scope,
            "state": state,
        }

        try:
            # 1. Request authorize endpoint
            auth_req_url = f"{self.auth_url}?{urlencode(auth_params)}"
            resp = session.get(auth_req_url, allow_redirects=True, timeout=self.timeout)

            # Check if immediately redirected with code in URL
            code = self._extract_code_from_url(resp.url)
            if code:
                return self.exchange_pkce_code(code, code_verifier)

            # 2. If login page rendered, attempt automated form submission
            login_form_action = resp.url
            login_payload = {
                "Username": self.username,
                "Password": self.password,
                "username": self.username,
                "password": self.password,
            }

            # Check for verification tokens in HTML if present
            rvt_match = re.search(r'name="__RequestVerificationToken"\s+value="([^"]+)"', resp.text)
            if rvt_match:
                login_payload["__RequestVerificationToken"] = rvt_match.group(1)

            post_resp = session.post(login_form_action, data=login_payload, allow_redirects=True, timeout=self.timeout)
            code = self._extract_code_from_url(post_resp.url)

            if code:
                return self.exchange_pkce_code(code, code_verifier)

            # Fallback to direct password grant if interactive redirect is blocked
            logger.info("PKCE interactive step did not return code directly. Attempting password grant fallback...")
            return self._request_password_grant_token()

        except Exception as exc:
            logger.warning(f"Automated PKCE flow error: {exc}. Trying password grant fallback.")
            return self._request_password_grant_token()

    def _extract_code_from_url(self, url: str) -> Optional[str]:
        parsed = urlparse(url)
        query = parse_qs(parsed.query)
        if "code" in query:
            return query["code"][0]
        return None

    def _refresh_access_token(self) -> Optional[str]:
        """Renew access token using the stored refresh token."""
        if not self._cached_refresh_token:
            return None

        logger.info("Renewing access token using OAuth 2.0 refresh_token...")
        payload = {
            "grant_type": "refresh_token",
            "client_id": self.client_id,
            "refresh_token": self._cached_refresh_token,
        }
        if self.client_secret:
            payload["client_secret"] = self.client_secret

        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        }

        try:
            resp = requests.post(
                self.token_url,
                data=payload,
                headers=headers,
                timeout=self.timeout,
                verify=self.verify_tls,
            )
            return self._process_token_response(resp)
        except Exception as exc:
            logger.warning(f"Failed to refresh token: {exc}")
            self._cached_refresh_token = None
            return None

    def _request_password_grant_token(self) -> Optional[str]:
        """Request token via OAuth 2.0 password grant."""
        logger.info(f"Requesting token from {self.token_url} (grant_type=password, client_id={self.client_id})")

        payload = {
            "grant_type": "password",
            "client_id": self.client_id,
            "username": self.username,
            "password": self.password,
            "scope": self.scope,
        }
        if self.client_secret:
            payload["client_secret"] = self.client_secret
        if self.callback_url:
            payload["redirect_uri"] = self.callback_url

        headers = {
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        }

        try:
            resp = requests.post(
                self.token_url,
                data=payload,
                headers=headers,
                timeout=self.timeout,
                verify=self.verify_tls,
            )
            return self._process_token_response(resp)
        except Exception as exc:
            logger.error(f"Password grant token request error: {exc}")
            return None

    def _process_token_response(self, resp: requests.Response) -> Optional[str]:
        if resp.status_code == 200:
            data = resp.json()
            access_token = data.get("access_token")
            refresh_token = data.get("refresh_token")
            expires_in = int(data.get("expires_in", 3600))

            if access_token:
                self._cached_access_token = access_token
                self._token_expiry_epoch = time.time() + expires_in
                if refresh_token:
                    self._cached_refresh_token = refresh_token
                logger.info(f"OAuth 2.0 Token acquired successfully. Valid for {expires_in}s")
                return access_token
            else:
                logger.error(f"Response missing access_token: {data}")
        else:
            logger.error(f"Token endpoint returned HTTP {resp.status_code}: {resp.text}")

        return None

    def invalidate_token(self) -> None:
        """Invalidate cached tokens to force fresh authentication."""
        self._cached_access_token = None
        self._token_expiry_epoch = 0.0
