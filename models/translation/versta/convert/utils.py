from shutil import rmtree
from pathlib import Path

def remove_folder(dir: Path):
    """
    Removes the specified directory and all its contents.
    """
    if dir.exists() and dir.is_dir():
        rmtree(dir)