import os
import argparse
import getpass
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

def derive_key(password: str, salt: bytes) -> bytes:
    kdf = Scrypt(
        salt=salt,
        length=32,      # AES-256
        n=2**14,
        r=8,
        p=1,
    )
    return kdf.derive(password.encode("utf-8"))

def encrypt_file(input_path, output_path, password):
    salt = os.urandom(16)
    nonce = os.urandom(12)

    key = derive_key(password, salt)
    aes = AESGCM(key)

    with open(input_path, "rb") as f:
        plaintext = f.read()

    ciphertext = aes.encrypt(nonce, plaintext, None)

    # Store salt + nonce alongside ciphertext.
    with open(output_path, "wb") as f:
        f.write(salt + nonce + ciphertext)

def decrypt_file(input_path, output_path, password):
    with open(input_path, "rb") as f:
        data = f.read()

    salt = data[:16]
    nonce = data[16:28]
    ciphertext = data[28:]

    key = derive_key(password, salt)
    aes = AESGCM(key)

    plaintext = aes.decrypt(nonce, ciphertext, None)

    with open(output_path, "wb") as f:
        f.write(plaintext)

def main():
    parser = argparse.ArgumentParser(
        description="Encrypt or decrypt a file using Scrypt-derived AES-256-GCM."
    )
    parser.add_argument("mode", choices=("encrypt", "decrypt"))
    parser.add_argument("input_path")
    parser.add_argument("output_path")
    parser.add_argument(
        "--password",
        help="Password to use. If omitted, you will be prompted securely.",
    )
    args = parser.parse_args()

    password = args.password or getpass.getpass("Password: ")

    if args.mode == "encrypt":
        encrypt_file(args.input_path, args.output_path, password)
    else:
        decrypt_file(args.input_path, args.output_path, password)

if __name__ == "__main__":
    main()
