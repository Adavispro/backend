# verify_license.py
import jwt
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KEYS_DIR = os.path.join(BASE_DIR, "keys")
LICENSE_FOLDER = os.path.join(BASE_DIR, "licenses")

def load_public_key():
    candidate_paths = [
        os.path.join(BASE_DIR, "keys", "public_key.pem"),
        os.path.join(BASE_DIR, "keys_txt", "public_key.txt"),
        os.path.join(BASE_DIR, "keys", "public_key.txt"),
        os.path.join(BASE_DIR, "keys_txt", "public_key.pem"),
        os.path.join("keys", "public_key.pem"),
        os.path.join("keys_txt", "public_key.txt"),
    ]
    for path in candidate_paths:
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                return f.read()
    raise FileNotFoundError(
        f"Public key file not found. Checked: {candidate_paths}. "
        f"Please run 'python generate_keys.py' to generate keys or place public_key.pem in the keys/ directory."
    )

public_key = load_public_key()

def verify_license(token):
    try:
        decoded = jwt.decode(
            token,
            public_key,
            algorithms=["RS256"],
            issuer="ADAVIS"
        )

        print("✅ License is VALID\n")
        print(decoded)

    except jwt.ExpiredSignatureError:
        print("❌ License expired")

    except jwt.InvalidTokenError as e:
        print("❌ Invalid license:", str(e))


if __name__ == "__main__":
    filename = input(f"Enter the license filename from {LICENSE_FOLDER} (e.g., TENANT_ACME_...txt):\n")
    file_path = os.path.join(LICENSE_FOLDER, filename)
    
    if os.path.exists(file_path):
        with open(file_path, "r") as f:
            token = f.read().strip()
        verify_license(token)
    else:
        print(f"❌ File not found: {file_path}")