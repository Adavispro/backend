# generate_keys.py
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KEYS_DIR = os.path.join(BASE_DIR, "keys")
os.makedirs(KEYS_DIR, exist_ok=True)

# Generate private key
private_key = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048
)

# Define key paths
private_key_path = os.path.join(KEYS_DIR, "private_key.pem")
public_key_path = os.path.join(KEYS_DIR, "public_key.pem")

# Save private key
with open(private_key_path, "wb") as f:
    f.write(private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption()
    ))

# Save public key
public_key = private_key.public_key()
with open(public_key_path, "wb") as f:
    f.write(public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    ))

print(f"Keys generated and saved to: {KEYS_DIR}/private_key.pem, {KEYS_DIR}/public_key.pem")
