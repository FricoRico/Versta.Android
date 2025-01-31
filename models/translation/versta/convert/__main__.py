import os

from argparse import ArgumentParser
from pathlib import Path

from .config import get_source_language, get_target_language, get_architecture
from .tokenizer import save_tokenizer, optimize_vocabulary
from .quantize import quantize_model
# from .download import download_model
# from .convert_pt import convert_model_to_pt
from .convert_onnx import convert_model_to_onnx
from .convert_ort import convert_model_to_ort
from .metadata import generate_metadata
from .utils import remove_folder

with open(Path(__file__).parent / ".." / "version.txt", "r") as version_file:
    version = version_file.read().strip()

def parse_args():
    parser = ArgumentParser(
        os.path.basename(__file__),
        description="""Convert a model from HuggingFace model hub to ONNX format and then to ORT format.
        The converter is intended to be used with Helsinki-NLP's Opus-MT translation models, but might work with other models as well.
        This function manages the overall workflow from exporting the model to ONNX, saving the tokenizer, and quantizing the model components.
        After the model is quantized, it is converted to ORT format for deployment on ARM devices.
        """,
    )

    parser.add_argument(
        "--model",
        type=str,
        help="Provide the name of the pre-trained model to convert."
        "For the moment, only Helsinki-NLP's Opus-MT translation models are supported.",
        required=True,
    )

    parser.add_argument(
        "--output_dir",
        type=Path,
        default=Path("output"),
        help="Provide an output directory for the converted model's and configuration file."
        "If unspecified, the converted ORT format model's will be in the '/output' directory, in the provided language.",
    )

    parser.add_argument(
        "--temp_dir",
        type=Path,
        default=Path("tmp"),
        help="Provide an temporary directory used to download and extract raw models."
        "If unspecified, the downloaded models will go into '/tmp' directory.",
    )

    parser.add_argument(
        "--model_format",
        type=str,
        default="opus",
        help="Specify the format of the model to convert."
        "This could be either 'opus' or 'opus-tatoeba' at the moment, defaulting to 'opus'."
    )

    parser.add_argument(
        "--keep_intermediates",
        action="store_true",
        default=False,
        help="Whether to remove intermediate files created during the conversion process."
        "This will default to False if not specified.",
    )

    parsed_args = parser.parse_args()
    return parsed_args

def main(
    model: str,
    output_dir: Path,
    temp_dir: Path,
    keep_intermediates: bool = False,
    model_format: str = "opus",
):
    """
    Main function to handle the model conversion, tokenization, and quantization process.
    This function manages the overall workflow from exporting the model to ONNX, saving the tokenizer,
    and quantizing the model components. After the model is quantized, it is converted to ORT format.
    """

    download_dir = temp_dir / "downloads"
    export_dir = temp_dir / "exports"

    download_dir.mkdir(parents=True, exist_ok=True)
    export_dir.mkdir(parents=True, exist_ok=True)

    # Check if the model is a direct download, then first down
    # if model.startswith("https"):
        # Step 1: Download the model files
        # downloaded_files = download_model(model, download_dir)

        # Step 2: Convert the model to PyTorch format
        # model = convert_model_to_pt(downloaded_files, export_dir, model_format)

    source_language = get_source_language(model)
    target_language = get_target_language(model)
    architectures = get_architecture(model)

    language_output_dir = output_dir / f"{source_language}-{target_language}"

    intermediates_dir = language_output_dir / "intermediates"
    converted_dir = intermediates_dir / "converted"
    quantization_dir = intermediates_dir / "quantized"

    language_output_dir.mkdir(parents=True, exist_ok=True)
    intermediates_dir.mkdir(parents=True, exist_ok=True)
    converted_dir.mkdir(parents=True, exist_ok=True)
    quantization_dir.mkdir(parents=True, exist_ok=True)

    # Step 1: Convert the model to ONNX format
    convert_model_to_onnx(model, converted_dir)

    # Step 2: Save the tokenizer and optimize the vocabulary
    tokenizer_files = save_tokenizer(model, language_output_dir)
    tokenizer_files_optimized = optimize_vocabulary(tokenizer_files, language_output_dir)

    # Step 3: Quantize the encoder and decoder models
    quantize_model(converted_dir, "encoder_model.onnx", quantization_dir)
    quantize_model(converted_dir, "decoder_model_merged.onnx", quantization_dir)

    # Step 4: Convert the quantized models to ORT format
    ort_files = convert_model_to_ort(quantization_dir, language_output_dir)

    # Step 5: Create metadata file for the model
    generate_metadata(version, language_output_dir, model, source_language, target_language, architectures, tokenizer_files_optimized, ort_files)

    # Step 6: Remove intermediate files if specified
    if keep_intermediates == False:
        remove_folder(download_dir)
        remove_folder(intermediates_dir)
        print("Intermediates files cleaned.")

if __name__ == "__main__":
    args = parse_args()
    main(
        model=args.model,
        output_dir=args.output_dir,
        temp_dir=args.temp_dir,
        keep_intermediates=args.keep_intermediates,
        model_format=args.model_format,
    )