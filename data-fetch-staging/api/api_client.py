"""OAuth 2.0 token handling and authenticated dataset requests."""

import base64
import json
import os
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class APIClient:
    def __init__(self, config):
        self.config = config
        self.oauth = config["oauth"]
        self.timeout = int(config["request_timeout_seconds"])
        self.access_token = None
        self.refresh_token = None
        self.expires_at = 0.0

    def _env(self, key, required=False):
        name = self.oauth.get(key)
        value = os.environ.get(name, "") if name else ""
        if required and (not value or value == "Xxx"):
            raise ValueError(f"Set the environment variable named by oauth.{key} before fetching")
        return value

    def _token_request(self, grant_type):
        token_url = self.oauth.get("token_url", "")
        if not token_url.startswith("https://"):
            raise ValueError("Configure oauth.token_url with the site's HTTPS OAuth token endpoint")
        form = {"grant_type": grant_type}
        if grant_type == "password":
            form["username"] = self._env("username_env", required=True)
            form["password"] = self._env("password_env", required=True)
        elif grant_type == "refresh_token":
            form["refresh_token"] = self.refresh_token
        elif grant_type != "client_credentials":
            raise ValueError("Configure oauth.grant_type as password or client_credentials")

        client_id = self._env("client_id_env", required=grant_type == "client_credentials")
        client_secret = self._env("client_secret_env", required=grant_type == "client_credentials")
        headers = {"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"}
        if client_id:
            method = self.oauth.get("client_auth_method", "basic")
            if method == "basic":
                pair = f"{client_id}:{client_secret}".encode("utf-8")
                headers["Authorization"] = "Basic " + base64.b64encode(pair).decode("ascii")
            elif method == "body":
                form["client_id"] = client_id
                form["client_secret"] = client_secret
            else:
                raise ValueError("oauth.client_auth_method must be basic or body")
        request = Request(token_url, data=urlencode(form).encode("utf-8"), headers=headers, method="POST")
        try:
            with urlopen(request, timeout=self.timeout) as response:
                body = response.read()
        except HTTPError as error:
            raise RuntimeError(f"OAuth token endpoint returned HTTP {error.code}") from None
        except URLError:
            raise RuntimeError("OAuth token endpoint connection failed") from None
        try:
            payload = json.loads(body)
            token = payload["access_token"]
        except (ValueError, KeyError, TypeError):
            raise RuntimeError("OAuth token endpoint returned an invalid token response") from None
        if not isinstance(token, str) or not token:
            raise RuntimeError("OAuth token endpoint returned an empty access token")
        self.access_token = token
        self.refresh_token = payload.get("refresh_token", self.refresh_token)
        try:
            lifetime = int(payload.get("expires_in", 300))
        except (ValueError, TypeError):
            lifetime = 300
        self.expires_at = time.monotonic() + max(0, lifetime - 30)

    def _ensure_token(self, force=False):
        if not force and self.access_token and time.monotonic() < self.expires_at:
            return
        if self.refresh_token:
            try:
                self._token_request("refresh_token")
                return
            except RuntimeError:
                self.refresh_token = None
        self._token_request(self.oauth.get("grant_type"))

    def get_dataset(self, point_name):
        base = self.config["base_url"].rstrip("/")
        path = self.config["dataset_path"]
        url = base + (path if path.startswith("/") else "/" + path) + "?" + urlencode({"pointName": point_name})
        for attempt in range(2):
            self._ensure_token(force=attempt == 1)
            request = Request(url, headers={
                "Accept": "application/json",
                "Authorization": f"Bearer {self.access_token}",
            })
            try:
                with urlopen(request, timeout=self.timeout) as response:
                    return response.read()
            except HTTPError as error:
                if error.code == 401 and attempt == 0:
                    self.access_token = None
                    continue
                raise RuntimeError(f"Dataset API returned HTTP {error.code}") from None
            except URLError:
                raise RuntimeError("Dataset API connection failed") from None
        raise RuntimeError("Dataset API authentication failed")
