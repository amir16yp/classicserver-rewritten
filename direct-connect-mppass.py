import hashlib
import os

SALT_FILE = "server.salt"

def load_salt():
    if os.path.exists(SALT_FILE):
        with open(SALT_FILE, "r", encoding="utf-8") as file:
            salt = file.read().strip()
            if len(salt) == 16:  # Ensure the salt is valid
                return salt
            else:
                raise ValueError("Invalid salt file. Expected 16 characters.")
    else:
        raise FileNotFoundError(f"{SALT_FILE} not found. Please ensure it exists.")

def generate_mppass(salt, username):
    combined = salt + username
    md5_hash = hashlib.md5(combined.encode("utf-8")).hexdigest()
    return md5_hash

if __name__ == "__main__":
    try:
        salt = load_salt()
        username = input("Enter username: ").strip()
        mppass = generate_mppass(salt, username)
        print(f"Generated mppass: {mppass}")
    except Exception as e:
        print(f"Error: {e}")
