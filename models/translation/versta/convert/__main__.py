import os

from argparse import ArgumentParser
from pathlib import Path

from .config import get_source_language, get_target_language
from .tokenizer import save_tokenizer
from .quantize import quantize_model
from .convert_onnx import convert_model_to_onnx
from .convert_ort import convert_model_to_ort
from .metadata import generate_metadata
from .utils import remove_folder

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
        help="Provide an output directory for the converted model's and configuration file. "
        "If unspecified, the converted ORT format model's will be in the '/output' directory, in the provided language.",
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
    model: str,
    output_dir: Path,
    clean_intermediates: bool = True,
):
    """
    Main function to handle the model conversion, tokenization, and quantization process.
    This function manages the overall workflow from exporting the model to ONNX, saving the tokenizer,
    and quantizing the model components. After the model is quantized, it is converted to ORT format.
    """
    source_language = get_source_language(model)
    target_language = get_target_language(model)

    language_output_dir = output_dir / f"{source_language}-{target_language}"

    intermediates_dir = language_output_dir / "intermediates"
    converted_dir = intermediates_dir / "converted"
    quantization_dir = intermediates_dir / "quantized"

    language_output_dir.mkdir(parents=True, exist_ok=True)
    intermediates_dir.mkdir(parents=True, exist_ok=True)
    converted_dir.mkdir(parents=True, exist_ok=True)
    quantization_dir.mkdir(parents=True, exist_ok=True)

    # Step 1: Save the tokenizer
    tokenizer_files = save_tokenizer(model, language_output_dir)

    # Step 2: Export the model to ONNX format
    convert_model_to_onnx(model, converted_dir)

    # Step 3: Quantize the encoder and decoder models
    quantize_model(converted_dir, "encoder_model.onnx", quantization_dir)
    quantize_model(converted_dir, "decoder_model.onnx", quantization_dir)

    # Step 4: Convert the quantized models to ORT format
    ort_files = convert_model_to_ort(quantization_dir, language_output_dir)

    # Step 5: Create metadata file for the model
    generate_metadata(language_output_dir, source_language, target_language, tokenizer_files, ort_files)

    # Step 6: Remove intermediate files if specified
    if clean_intermediates:
        remove_folder(intermediates_dir)
        print("Intermediates files cleaned.")

if __name__ == "__main__":
    args = parse_args()
    main(
        model=args.model,
        output_dir=args.output_dir,
        clean_intermediates=args.clean_intermediates,
    )