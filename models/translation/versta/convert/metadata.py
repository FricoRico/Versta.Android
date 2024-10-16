import json

from pathlib import Path
from typing import List

from .tokenizer import TokenizerFiles
from .convert_ort import ORTFiles

def generate_metadata(output_dir: Path, model: str, source_language: str, target_language: str, architectures: List[str], tokenizer_files: TokenizerFiles, ort_files: ORTFiles or ORTMonolithFile, monolith: bool) -> str:
    """
    Generates a metadata file for the model conversion process.

    Args:
        output_dir (Path): Path to the directory where the metadata file will be saved.
        source_language (str): Source language for the translation model.
        target_language (str): Target language for the translation model.
        tokenizer_files (TokenizerFiles): Dictionary containing the file paths for the tokenizer files.
        ort_files (ORTFiles): Dictionary containing the file paths for the encoder and decoder ORT files.
        monolith (bool): Whether the model is in monolith mode or not.
    """
    metadata = {
        "base_model": model,
        "source_language": source_language,
        "target_language": target_language,
        "architectures": architectures,
        "monolith": monolith,
        "files": {
            "tokenizer": tokenizer_files or {},
            "inference": ort_files or {}
        }
    }

    # Define the path for the metadata.json file
    metadata_file_path = output_dir / "metadata.json"

    # Write the metadata to a JSON file
    with open(metadata_file_path, "w") as metadata_file:
        json.dump(metadata, metadata_file, indent=4)

    return metadata_file_path
