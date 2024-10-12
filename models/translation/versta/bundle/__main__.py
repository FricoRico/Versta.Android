import os

from argparse import ArgumentParser
from pathlib import Path
from typing import List

from .metadata import load_metadata_for_input_dirs, generate_metadata
from .language import validate_translation_pairs, extract_unique_languages, update_metadata_file
from .bundle_tar import bundle_files
from .utils import copy_folders, remove_folder

def parse_args():
    parser = ArgumentParser(
        os.path.basename(__file__),
        description="""Bundle multiple translation models into a single tarball file.
        The tarball is directly deployable to the Versta application for translation purposes.
        The provided models should first have been converted using the 'convert' module.
        """,
    )

    parser.add_argument(
        "--input_dir",
        nargs="+",
        type=Path,
        help="Provide the directories containing the models to bundle."
        "When translation pairs is enabled, multiple directories should be provided for each language in the pair."
        "For example, if the languages are 'en' and 'nl', provide the directories for 'en-nl' and 'nl-en'.",
        required=True,
    )

    parser.add_argument(
        "--output_dir",
        type=Path,
        default=Path("output"),
        help="Provide an output directory for the converted model's and configuration file. "
        "If unspecified, the converted ORT format model's will be in the '/output' directory.",
   )

    parser.add_argument(
        "--translation_pairs",
        action="store_true",
        default=True,
        help="Whether the languages are a pair, e.g. 'en-nl' and 'nl-en'."
        "A language pair allows for the translation model to be used in both directions easily."
        "This will default to True if not specified.",
    )

    parser.add_argument(
        "--clean_intermediates",
        action="store_true",
        default=True,
        help="Whether to remove intermediate files created during the conversion process."
        "This will default to True if not specified.",
    )

    parsed_args = parser.parse_args()
    return parsed_args

def main(
    input_dirs: List[Path],
    output_dir: Path,
    translation_pairs: bool = True,
    clean_intermediates: bool = True
):
    """
    Main function to bundle multiple translation models into a single tarball file.
    The tarball is directly deployable to the Versta application for translation purposes.
    The provided models should first have been converted using the 'convert' module.

    Args:
        input_dirs (List[Path]): List of directories containing the models to bundle.
        translation_pairs (bool): Whether the languages are a pair, e.g. 'en-nl' and 'nl-en'.
    """
    # Step 1: Load metadata for the input directories
    metadata = load_metadata_for_input_dirs(input_dirs)

    # Step 2: Validate the translation pairs
    if translation_pairs:
        validate_translation_pairs(metadata)

    # Step 3: Extract the unique languages from the metadata
    languages = extract_unique_languages(metadata)

    bundle_output_dir = output_dir / f"{"-".join(languages)}-bundle"
    intermediates_dir = bundle_output_dir / "intermediates"

    bundle_output_dir.mkdir(parents=True, exist_ok=True)
    intermediates_dir.mkdir(parents=True, exist_ok=True)

    # Step 4: Copy the input directories to the intermediate directory
    copy_folders(input_dirs, intermediates_dir)

    # Step 5: Update the metadata files with the new file paths
    for data in metadata:
        update_metadata_file(intermediates_dir / data["directory"] / "metadata.json", data["directory"])

    # Step 6: Generate metadata for the model conversion process
    generate_metadata(intermediates_dir, languages, metadata, translation_pairs)

    # Step 7: Bundle the folders into a single .tar.gz file
    output_archive = bundle_output_dir / f"{"-".join(languages)}-bundle.tar.gz"
    output_files = list(intermediates_dir.iterdir())

    bundle_files(output_files, output_archive)

    # Step 8: Remove intermediate files if specified
    if clean_intermediates:
        remove_folder(intermediates_dir)
        print("Intermediates files cleaned.")

if __name__ == "__main__":
    args = parse_args()
    main(
        input_dirs=args.input_dir,
        output_dir=args.output_dir,
        translation_pairs=args.translation_pairs,
        clean_intermediates=args.clean_intermediates,
    )
