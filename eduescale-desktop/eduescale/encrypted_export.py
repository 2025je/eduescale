import hashlib
import os
import struct
import zlib
from pathlib import Path

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

MAGIC = b"EDUSCENC"
ENVELOPE_VERSION = 1
PBKDF2_ITERATIONS = 600_000
SALT_SIZE = 16
NONCE_SIZE = 12
HEADER = struct.Struct(">8sBI16s12s")
PASSWORD_MIN_LENGTH = 8


def encrypt_file(input_path: Path, output_path: Path, password: str) -> None:
    if len(password) < PASSWORD_MIN_LENGTH:
        raise ValueError(
            f"La contraseña debe tener al menos {PASSWORD_MIN_LENGTH} caracteres."
        )

    salt = os.urandom(SALT_SIZE)
    nonce = os.urandom(NONCE_SIZE)
    header = HEADER.pack(
        MAGIC,
        ENVELOPE_VERSION,
        PBKDF2_ITERATIONS,
        salt,
        nonce,
    )
    key = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        PBKDF2_ITERATIONS,
        dklen=32,
    )
    encryptor = Cipher(algorithms.AES(key), modes.GCM(nonce)).encryptor()
    encryptor.authenticate_additional_data(header)
    compressor = zlib.compressobj(level=6)

    with input_path.open("rb") as source, output_path.open("wb") as target:
        target.write(header)
        while chunk := source.read(1024 * 1024):
            compressed = compressor.compress(chunk)
            if compressed:
                target.write(encryptor.update(compressed))
        compressed = compressor.flush()
        if compressed:
            target.write(encryptor.update(compressed))
        target.write(encryptor.finalize())
        target.write(encryptor.tag)
