# Models
This readme describes the models used in the application and how to convert to application compatible formats.

## Translation Models
This application uses translation models based on [Helsinki-NLP's](https://huggingface.co/Helsinki-NLP) opensource [Opus-MT](https://github.com/Helsinki-NLP/OPUS-MT) models. These models are conveniently split up in single direction language pairs (ie. English to Japanese).

Follow this guide to convert the PyTorch models to ORT format, which is compatible.

### Converting ONNX to ORT
To convert to ORT format, we use the [onnxruntime-tools](https://pypi.org/project/onnxruntime-tools/) package. This means you need to have Python installed on your system. If you don't have Python installed, you can download it from [here](https://www.python.org/downloads/).

1. Install the required packages:
```bash
pip install -r requirements.txt
```
2. Run the conversion using the CLI:
```bash
python -m verta.convert --model $HUGGING_FACE_MODEL_NAME --output_dir $OUTPUT_DIR
```

Replace `$HUGGING_FACE_MODEL_NAME` with the model of choice (eg. `Helsinki-NLP/opus-mt-nl-en`) `$OUTPUT_DIR` with the directory where you want the ORT models to be saved. After conversion, you will have the ORT models in the specified output directory inside the specific language folder. If no output directory is specified, the models will be saved in the `./output` directory.

### Bundling Models
After converting the models to ORT format, we need to bundle them to be used in the Android application. The models are side-loaded by the user during runtime. To make it convenient for the user to do so, we intent to bundle the assets required for the models into a tarball. This can conveniently be done using the custom CLI tool.

1. Install the required packages:
```bash
pip install -r requirements.txt
```
2. Run the bundling using the CLI:
```bash
python -m versta.bundle --input_dir [$INPUT_DIR] --output_dir $OUTPUT_DIR
```

Replace `[$INPUT_DIR]` with each directory containing the ORT models, separated by a space (eg. `en-nl nl-en`). After bundling, you will have a tarball in the same directory as the input directory. If no output directory is specified, the tarball will be saved in the `./output` directory.

By default we expect to deliver languages in pairs, so usually two or more input directories are expected. If you only want to support a single direction translation model, you can pass the optional argument `--language_pairs False` to the CLI.

### Example workflow
This is an example workflow to convert the models and bundle them for the Android application. The models we will convert are `Helsinki-NLP/opus-mt-nl-en` and `Helsinki-NLP/opus-mt-en-nl`.

1. Convert the models to ORT format models:
```bash
python -m verta.convert --model Helsinki-NLP/opus-mt-nl-en --output_dir ./output
python -m verta.convert --model Helsinki-NLP/opus-mt-en-nl --output_dir ./output
```
2. Bundle the models:
```bash
python -m versta.bundle --input_dir ./output/en-nl ./output/nl-en --output_dir ./output
```

After running these commands, you will have a tarball in the `./output/en-nl-bundle` directory containing the models.

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