import json

from pathlib import Path
from typing import List, Tuple, TypedDict

from .metadata import BundleMetadata

class LanguageTokenizerFiles(TypedDict):
    config: Path
    vocabulary: Path
    source: Path
    target: Path

class LanguageORTFiles(TypedDict):
    encoder: Path
    decoder: Path

class LanguageORTMonolithFile(TypedDict):
    model: Path

class LanguageFiles(TypedDict):
    tokenizer: LanguageTokenizerFiles
    inference: LanguageORTFiles or LanguageORTMonolithFile

class LanguageMetadata(TypedDict):
    source_language: str
    target_language: str
    architectures: List[str]
    monolith: bool
    files: LanguageFiles

def validate_translation_pairs(metadata_list: List[BundleMetadata]) -> List[Tuple[str, str]]:
    """
    Validates that for each language pair (source -> target), there is a corresponding reverse pair (target -> source).
    If any reverse pair is missing, it returns them as a list of missing translation pairs.

    Args:
        metadata_list (List[BundleMetadata]): List of BundleMetadata dictionaries containing source and target language pairs.

    Returns:
        List[Tuple[str, str]]: A list of missing translation pairs as tuples (source_language, target_language).
    """
    # Set to store the existing language pairs
    existing_pairs = set()

    # Populate the existing pairs set
    for metadata in metadata_list:
        existing_pairs.add((metadata['source_language'], metadata['target_language']))

    # List to store the missing translation pairs
    missing_pairs = []

    # Check for missing reverse translation pairs
    for metadata in metadata_list:
        reverse_pair = (metadata['target_language'], metadata['source_language'])
        if reverse_pair not in existing_pairs:
            missing_pairs.append(reverse_pair)

    if missing_pairs:
        missing_pairs_formatted = [f"{source}-{target}" for source, target in missing_pairs]
        raise ValueError(f"Missing translation pairs: {missing_pairs_formatted}")

def extract_unique_languages(metadata: List[BundleMetadata]) -> List[str]:
    """
    Extracts unique languages from a list of language pairs.

    Args:
        metadata (List[BundleMetadata]): List of BundleMetadata dictionaries containing source and target language pairs.

    Returns:
        List[str]: A sorted list of unique languages.
    """
    unique_languages = set()

    # Split each pair and add the languages to the set
    for data in metadata:
        unique_languages.update([data['source_language'], data['target_language']])

    # Return the unique languages as a sorted list
    return sorted(unique_languages)

def update_metadata_file(file: Path, path: Path) -> None:
    """
    Update all file paths in the 'files' section of the given metadata.json file by
    prepending the specified folder prefix to each path.

    Args:
        file (Path): Path to the metadata.json file.
        path (Path): The folder prefix to prepend to all file paths in the 'files' section.

    Returns:
        None: The function updates the metadata.json file with the new file paths.
    """
    # Load the metadata.json
    with open(file, "r") as f:
        metadata = json.load(f)

    # Update the paths in the 'files' section with the folder prefix
    metadata['files'] = update_metadata_file_paths(metadata['files'], path)

    # Save the updated metadata back to the original file
    with open(file, "w") as f:
        json.dump(metadata, f, indent=4)

def update_metadata_file_paths(metadata: LanguageMetadata, path: Path) -> LanguageMetadata:
    """
    Recursively update file paths in the metadata dictionary by adding a folder prefix.

    Args:
        metadata (LanguageMetadata): The metadata dictionary to update.
        path (Path): The folder prefix to prepend to each file path.

    Returns:
        LanguageMetadata: The updated metadata dictionary with the new file paths
    """
    if isinstance(metadata, dict):
        updated_metadata = {}

        for key, value in metadata.items():
            if isinstance(value, dict):
                updated_metadata[key] = update_metadata_file_paths(value, path)
            elif isinstance(value, str) and Path(value).suffix:
                updated_metadata[key] = str(path / Path(value))
            else:
                updated_metadata[key] = value
        return updated_metadata

    return metadata