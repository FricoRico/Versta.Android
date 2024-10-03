from optimum.onnxruntime import ORTQuantizer, AutoQuantizationConfig
from pathlib import Path

QuantizeConfiguration = AutoQuantizationConfig.arm64(is_static=False, use_symmetric_activations=True, per_channel=False)

def quantize_model(export_dir: Path, model_filename: str, quantization_dir: Path):
    """
    Quantizes a specific ONNX model file and saves the quantized model to the given directory.

    Args:
        export_dir (Path): Path to the directory where the ONNX model is stored.
        model_filename (str): Name of the ONNX model file to quantize (e.g., "encoder_model.onnx").
        quantization_dir (Path): Path to the directory where the quantized model will be saved.
        config (AutoQuantizationConfig): Configuration for the quantization process.
    """
    quantizer = ORTQuantizer.from_pretrained(export_dir, model_filename)
    quantizer.quantize(QuantizeConfiguration, quantization_dir)