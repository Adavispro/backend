"""Unauthenticated dataset requests for a plant API that permits direct access."""

from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class APIClient:
    def __init__(self, config):
        self.config = config
        self.timeout = int(config["request_timeout_seconds"])

    def get_dataset(self, point_name):
        base = self.config["base_url"].rstrip("/")
        path = self.config["dataset_path"]
        url = base + (path if path.startswith("/") else "/" + path) + "?" + urlencode({"pointName": point_name})
        request = Request(url, headers={"Accept": "application/json"})
        try:
            with urlopen(request, timeout=self.timeout) as response:
                return response.read()
        except HTTPError as error:
            if error.code in (401, 403):
                raise RuntimeError(f"Dataset API returned HTTP {error.code}; this endpoint requires authentication") from None
            raise RuntimeError(f"Dataset API returned HTTP {error.code}") from None
        except URLError:
            raise RuntimeError("Dataset API connection failed") from None
