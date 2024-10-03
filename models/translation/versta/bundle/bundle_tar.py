import tarfile

from pathlib import Path
from typing import List

def bundle_files(files: List[str], output_file: Path):
    """
    Bundles the specified folders into a single .tar.gz file.

    Args:
        folders (list[Path]): List of folder paths to be bundled.
        output_file (Path): Path for the output .tar.gz file.
    """
    with tarfile.open(output_file, "w:gz") as tar:
        for file in files:
            tar.add(file, arcname=file.name)

    return output_file