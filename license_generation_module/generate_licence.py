# generate_license.py
import jwt
import os
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KEYS_DIR = os.path.join(BASE_DIR, "keys")
LICENSE_FOLDER = os.path.join(BASE_DIR, "licenses")
os.makedirs(LICENSE_FOLDER, exist_ok=True)

def load_private_key():
    candidate_paths = [
        os.path.join(BASE_DIR, "keys", "private_key.pem"),
        os.path.join(BASE_DIR, "keys_txt", "private_key.txt"),
        os.path.join(BASE_DIR, "keys", "private_key.txt"),
        os.path.join(BASE_DIR, "keys_txt", "private_key.pem"),
        os.path.join("keys", "private_key.pem"),
        os.path.join("keys_txt", "private_key.txt"),
    ]
    for path in candidate_paths:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                return f.read()
    raise FileNotFoundError(
        f"Private key file not found. Checked: {candidate_paths}. "
        f"Please run 'python generate_keys.py' to generate keys or place private_key.pem in the keys/ directory."
    )

private_key = load_private_key()

def generate_license():
    now = datetime.now(timezone.utc)

    payload = {
        "tenantId": "TNT-0002",
        'licenceKey': 'LIC-0001',
        "plan": {
            "planId": "PLAN_ENTERPRISE",
            "planName": "Enterprise",
            "planType": "PAID"
        },

        "modules": ["MDM", "IIOT"],
        "maxUsers": 500,

        "startDate": "2026-07-01",
        "expiryDate": "2027-09-30",

        "version": 1,

        # Standard JWT claims
        "iss": "ADAVIS",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=365)).timestamp())
    }

    token = jwt.encode(payload, private_key, algorithm="RS256")

    return token


if __name__ == "__main__":
    license_key = generate_license()

    now = datetime.now(timezone.utc)
    tenant_id = "TENANT_ACME" # Assuming this is the tenant ID for the generated license
    timestamp_str = now.strftime("%Y-%m-%d_%H-%M-%S")
    license_filename = f"{tenant_id}_{timestamp_str}.txt"
    license_file_path = os.path.join(LICENSE_FOLDER, license_filename)
    with open(license_file_path, "w") as f:
        f.write(license_key)
    print(f"\n✅ License key generated and saved to: {license_file_path}")