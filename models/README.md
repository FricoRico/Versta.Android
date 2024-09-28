# Models
This readme describes the models used in the application and how to convert to application compatible formats.

## Translation Models
This application uses translation models based on [Helsinki-NLP's](https://huggingface.co/Helsinki-NLP) opensource [Opus-MT](https://github.com/Helsinki-NLP/OPUS-MT) models. These models are conveniently split up in single direction language pairs (ie. English to Japanese).

To avoid having to convert the TensorFlow models to ONNX, we use pre-converted models conveniently provided by [Xenova](https://huggingface.co/Xenova). These models are also available in quantized format which is great for low-power devices such as phones. However, the models are still in ONNX format, which is not supported by Microsoft's ONNX Runtime for Android. Follow this guide to convert the ONNX models to ORT format, which is compatible.

### Converting ONNX to ORT
To convert to ORT format, we use the [onnxruntime-tools](https://pypi.org/project/onnxruntime-tools/) package. This means you need to have Python installed on your system. If you don't have Python installed, you can download it from [here](https://www.python.org/downloads/).

1. Install the required packages:
```bash
pip install -r requirements.txt
```
2. Copy the ONNX models to a directory. For example, `models/translation/en-ja/input` directory.
3. Run the conversion using the CLI:
```bash
python -m onnxruntime.tools.convert_onnx_models_to_ort $INPUT_DIR --output_dir $OUTPUT_DIR --target_platform arm --optimization_style Runtime --enable_type_reduction
```

Replace `$INPUT_DIR` with the directory containing the ONNX models and `$OUTPUT_DIR` with the directory where you want the ORT models to be saved. After conversion, you will have the ORT models in the specified output directory. Only the models **WITHOUT** the `with_runtime_opt` suffix are needed for the application.

TODO: Add instructions for packing the models into the Android app along with a `metadata.json` file.

### Optional: ONNX Runtime
Since the ONNX models contain operations that are not available in the prepackaged ONNX Runtime for Android, we need to build the runtime from source. This is an optional step only for those who want to contribute new versions of the runtime to the project.

In the previous step, we converted the ONNX models to ORT format. Along with the ORT models, the conversion script also generated a `required_operators_and_types.with_runtime_opt.config`, which contains the list of operators required by the model. We need to build the ONNX Runtime with these operators enabled.

To be able to compile the project, you need to have the Android NDK and SDK installed on your system. You can download and install the SDK and NDK through Android Studio or download them separately from the [Android Developer website](https://developer.android.com/studio). Make sure to set the `ANDROID_HOME` and `ANDROID_NDK` environment variables to the SDK and NDK paths, respectively.

1. Clone the [ONNX Runtime repository](https://github.com/microsoft/onnxruntime) to your system:
```bash
git clone git@github.com:microsoft/onnxruntime.git
```
2. Checkout the release tag for compilation:
```bash
git checkout tags/vX.XX.X
```
3. Build the ONNX Runtime with the required operators:
```bash
./build.sh --config Release \
           --build_shared_lib \
           --android \
           --android_api 28 \
           --android_sdk $ANDROID_HOME \
           --android_ndk $ANDROID_NDK \
           --android_abi $ARCHITECTURE \
           --minimal_build extended \
           --build_java \
           --use_xnnpack \
           --use_nnapi \ 
           --include_ops_by_config $REQUIRED_CONFIG_PATH \
           --parallel
```

Be sure to replace `$ARCHITECTURE` with the architecture you want to build for with any of these choices: `armeabi-v7a`, `arm64-v8a`,`x86`, `x86_64`. Also replace `$REQUIRED_CONFIG_PATH` with the path to the `required_operators.config` file generated during the conversion step.

After compilation is finished, you will have the ONNX Runtime shared library in the `build/Android/Release/java/build/android/outputs/aar` directory. Copy the `onnxruntime-release.aar` file to the `app/src/main/app/libs` library folder in the Android project. Be sure to name it appropriately to update the `build.gradle.kts` file.