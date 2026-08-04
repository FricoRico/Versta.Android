[![Play Store](https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=app.versta.translate)

# Versta.Android
Welcome to Versta, your go-to offline translation app designed with privacy and convenience in mind. This project is open source and aims to provide a secure and user-friendly translation experience for travelers and language learners alike.

## Features
Versta is offline-first: after the required model files are downloaded, everything runs entirely on-device. It currently offers the following features, with more planned for future releases:

- Secure Offline Translations: Translate text without an internet connection, ensuring your data stays private.
- Support for 27+ Languages: Communicate effectively in multiple languages with our extensive language support.
- Context Menu Translation: Translate text using the context menu and view translations in chat bubbles for seamless multitasking.
- Voice Translation: Speak and get translations in real time using your device's microphone.
- Image Translation: Translate text in real time by pointing your camera at it.
- Text-to-Speech: Listen to translations in the target language to improve pronunciation.
- License-Based: Contribute to the app's development by purchasing a license, helping us add new features and improve existing ones.

## Roadmap
Voice translation, image translation, and text-to-speech from our original roadmap are now implemented. Remaining planned features include:

- Conversation Mode: Translate conversations between two languages for seamless communication.

## Why Source Available ?
We believe in transparency and privacy, which is why Versta is source available. This means that you have access to the source code and can modify it for non-commercial purposes. We also encourage contributions from the community to improve the app for everyone. These are some of the reasons why we chose to make Versta source available:

- Empower Users: Give users control over their software and the ability to understand and modify it as needed.
- Encourage Contributions: Foster a community of developers who can contribute to the project, improving it for everyone.
- Ensure Privacy: Provide a transparent solution that respects user privacy and does not rely on internet connectivity or data collection.

## License
Versta is licensed under the Source First license. This license ensures that:

- Users have full access to the source code and the right to modify and share modifications for non-commercial purposes.
- Large organizations are obligated to support the developers financially if they use or package the software.
- Attribution is preserved in all copies and modifications of the software.
- The software respects user privacy and does not include advertisements or telemetry without opt-in.

For more details, please refer to the [LICENSE.md](LICENSE.md) file and visit [Source First](https://sourcefirst.com/) for more information about the licensing principles.

## Language Models
Versta runs its models entirely on-device through native C++ inference (JNI) and ONNX Runtime, so translations are fast and accurate without an internet connection. The current stack includes:

- Translation: LeanMT (Firefox MT) models.
- Speech recognition: Whisper.cpp.
- Optical character recognition: PaddleOCR.
- Text-to-speech: espeak-ng and open-jtalk phonemization with StyleTTS2 synthesis.

These open-source models are optimized for mobile devices, keeping them lightweight and efficient. In order for the models to be compatible, our conversion tools can be used to export the models to the required format. More information on our [Versta.Models](https://github.com/FricoRico/Versta.Models) GitHub page.

## Feedback
Your feedback is crucial to the development of Versta. If you encounter any issues or have suggestions for improvement, please open an issue on our [GitHub issue](https://github.com/FricoRico/Versta.Android/issues) page.

Thank you for choosing Versta! Together, we can break down language barriers and enhance communication for everyone.