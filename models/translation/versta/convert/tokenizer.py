from transformers import AutoTokenizer
from pathlib import Path
from typing import TypedDict, Tuple

class TokenizerFiles(TypedDict):
    config: Path
    vocabulary: Path
    source: Path
    target: Path

def save_tokenizer(model_name: str, export_dir: Path) -> TokenizerFiles:
    """
    Saves the tokenizer for the specified model to the export directory.

    Args:
        model_name (str): Name of the pre-trained model.
        export_dir (Path): Path to the directory where tokenizer will be saved.
    """
    tokenizer = AutoTokenizer.from_pretrained(model_name, clean_up_tokenization_spaces=False)
    output_files = tokenizer.save_pretrained(export_dir)

    # Get the file paths for the tokenizer configuration, vocabulary, source, and target files
    return _get_files_from_output(output_files)

def _get_files_from_output(output_files: Tuple[str]) -> TokenizerFiles:
    """
    Extracts the file paths from the tokenizer output files list and returns a dictionary with the file types as keys.

    Args:
        output_files (Tuple[str]): List of output files generated during the conversion process.

    Returns:
        TokenizerFiles: Dictionary containing the file paths for the tokenizer configuration, vocabulary, source, and target files.
    """
    tokenizer_files = TokenizerFiles(
        config=None,
        vocabulary=None,
        source=None,
        target=None
    )

    for path in output_files:
        file = Path(path).name

        if "config" in file:
            tokenizer_files["config"] = file
        elif "vocab" in file:
            tokenizer_files["vocabulary"] = file
        elif "source" in file:
            tokenizer_files["source"] = file
        elif "target" in file:
            tokenizer_files["target"] = file

    # Check if any required file is still None, raise an error if it is
    missing_files = [key for key, value in tokenizer_files.items() if value is None]

    if missing_files:
        raise FileNotFoundError(f"Missing required Tokenizer output files: {', '.join(missing_files)}")

    return tokenizer_files