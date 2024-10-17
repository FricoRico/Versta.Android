from optimum.exporters.onnx import main_export
from pathlib import Path

def convert_model_to_onnx(model_name: str, export_dir: Path, monolith: bool):
    """
    Exports the specified pre-trained model to ONNX format and saves it in the export directory.

    Args:
        model_name (str): Name of the pre-trained model.
        export_dir (Path): Path to the directory where the ONNX model will be saved.
        monolith (bool): Whether to export the model in monolith mode or not.
    """
    task = "text2text-generation-with-past"

    if monolith:
        task = "text2text-generation"

    main_export(
        model_name,
        output=export_dir,
        task=task,
        monolith=monolith,
        framework="pt"
    )
