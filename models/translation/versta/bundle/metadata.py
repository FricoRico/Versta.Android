import json

from pathlib import Path
from typing import List, TypedDict

class Metadata(TypedDict):
    directory: Path
    source_language: str
    target_language: str

def load_metadata(model_dir: Path) -> Metadata:
    """
    Loads and parses the metadata.json file from the specified folder.

    Args:
        folder (Path): Path to the folder containing the metadata.json file.

    Returns:
        dict: A dictionary with the source and target languages.
    """
    metadata_file = model_dir / "metadata.json"

    metadata = Metadata(
        directory=None,
        source_language=None,
        target_language=None
    )

    if metadata_file.exists():
        metadata["directory"] = model_dir

        with open(metadata_file, "r", encoding="utf-8") as file:
            data = json.load(file)

            metadata["source_language"] = data.get("source_language")
            metadata["target_language"] = data.get("target_language")

        missing_entries = [key for key, value in metadata.items() if value is None]

        if missing_entries:
            raise ValueError(f"Missing required metadata entries: {', '.join(missing_entries)}")

        return metadata
    else:
        raise FileNotFoundError(f"Metadata file not found in {model_dir}")

def load_metadata_for_input_dirs(input_dirs: List[Path]) -> List[Metadata]:
    """
    Loads the metadata.json file from the specified input directories.

    Args:
        input_dirs (List[Path]): List of paths to the input directories containing the metadata.json files.

    Returns:
        List[Metadata]: List of metadata with the source and target languages.
    """
    metadata: List[Metadata] = list()

    for folder in input_dirs:
        metadata.append(load_metadata(folder))

    return metadata

def generate_metadata(output_dir: Path, languages: List[str], language_metadata: List[Metadata], language_pairs: bool) -> Path:
    """
    Generates a metadata file for the model conversion process.

    Args:
        output_dir (Path): Path to the directory where the metadata file will be saved.
        languages (List[str]): List of languages supported by the model.
        metadata (List[Metadata]): List of Metadata dictionaries containing source and target language pairs.
        language_pairs (bool): Flag to indicate if the metadata contains language pairs.
    """
    metadata = {
        "languages": languages or [],
        "language_pairs": language_pairs,
        "metadata": language_metadata or []
    }

    # Define the path for the metadata.json file
    metadata_file_path = output_dir / "metadata.json"

    # Write the metadata to a JSON file
    with open(metadata_file_path, "w") as metadata_file:
        json.dump(metadata, metadata_file, default=serialize_metadata, indent=4)

    return metadata_file_path

# Custom serialization function to handle non-serializable objects (like Path)
def serialize_metadata(obj: Metadata) -> dict:
    if isinstance(obj, Path):
        return str(obj)
    raise TypeError(f"Object of type {type(obj)} is not JSON serializable")