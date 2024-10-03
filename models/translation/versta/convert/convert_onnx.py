from optimum.onnxruntime import ORTModelForSeq2SeqLM
from pathlib import Path

def convert_model_to_onnx(model_name: str, export_dir: Path):
    """
    Exports the specified pre-trained model to ONNX format and saves it in the export directory.

    Args:
        model_name (str): Name of the pre-trained model.
        export_dir (Path): Path to the directory where the ONNX model will be saved.
    """
    model = ORTModelForSeq2SeqLM.from_pretrained(model_name, export=True)
    model.save_pretrained(export_dir)