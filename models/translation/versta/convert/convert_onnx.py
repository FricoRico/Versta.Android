from optimum.exporters.onnx import main_export
from pathlib import Path

def convert_model_to_onnx(model_name: str, export_dir: Path):
    """
    Exports the specified pre-trained model to ONNX format and saves it in the export directory.

    Args:
        model_name (str): Name of the pre-trained model.
        export_dir (Path): Path to the directory where the ONNX model will be saved.
    """
    task = "text2text-generation-with-past"

    main_export(
        model_name,
        output=export_dir,
        task=task,
        framework="pt",
        for_ort=True,
        opset=20,
    )
