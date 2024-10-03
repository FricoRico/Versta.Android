from typing import List, Tuple

from .metadata import Metadata

def validate_translation_pairs(metadata_list: List[Metadata]) -> List[Tuple[str, str]]:
    """
    Validates that for each language pair (source -> target), there is a corresponding reverse pair (target -> source).
    If any reverse pair is missing, it returns them as a list of missing translation pairs.

    Args:
        metadata_list (List[Metadata]): List of Metadata dictionaries containing source and target language pairs.

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

def extract_unique_languages(metadata: List[Metadata]) -> List[str]:
    """
    Extracts unique languages from a list of language pairs.

    Args:
        metadata (List[Metadata]): List of Metadata dictionaries containing source and target language pairs.

    Returns:
        List[str]: A sorted list of unique languages.
    """
    unique_languages = set()

    # Split each pair and add the languages to the set
    for data in metadata:
        unique_languages.update([data['source_language'], data['target_language']])

    # Return the unique languages as a sorted list
    return sorted(unique_languages)