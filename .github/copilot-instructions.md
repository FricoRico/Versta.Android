# Versta.Android Copilot Starting Prompt

You are an AI coding assistant working on the Versta.Android project. Follow these standards and project requirements for all code generation, refactoring, and review tasks:

## Project Requirements
- All translation, speech-to-text, text-to-speech, and OCR features must work fully offline. Never send user data off-device.
- Target Android only. Use Kotlin as the primary language.
- Use quantized and bundled models, optimized with custom Python tooling. Load and run models with ONNX Runtime.
- Do not add telemetry, analytics, or any network calls that could compromise user privacy.
- Use Jetpack Compose for UI components, with a focus on accessibility and performance.
- Follow the newest Material Expressive Design guidelines for UI/UX.

## Coding Standards
- Prefer `val` over `var` where possible.
- Use early returns to reduce nesting and improve readability.
- Use Kotlin null safety idioms (`?.`, `?:`, avoid `!!` unless necessary).
- Use data classes for simple data containers.
- Use sealed classes for restricted class hierarchies (e.g., UI state).
- Use extension functions to add functionality to existing types.
- Use coroutines for asynchronous work; avoid callbacks.
- Use dependency injection for testability and modularity.
- Use Jetpack Compose for new UI components.
- Use ViewModels for UI logic and state management.
- Use DataStore for preferences and lightweight persistence.
- Use Timber for logging; do not use `println` or `Log` directly.
- Organize code by feature/module (e.g., adapters, repositories, UI components).
- Keep Activities/Fragments thin; delegate logic to ViewModels or use-cases.
- Write unit tests for core logic and model inference.
- Use instrumented tests for UI and integration.
- Mock dependencies in tests; avoid real network or file I/O.
- Document public classes and functions with KDoc.

## Contribution Guidelines
- All features must work offline and respect user privacy.
- Prefer clean, maintainable, and modular code.
- Include tests and documentation for all changes.

## Example Patterns
- Early return:
  ```kotlin
  fun validate(input: String?): Boolean {
      if (input.isNullOrBlank()) return false
      // ...rest of logic...
      return true
  }
  ```
- ViewModel usage:
  ```kotlin
  class MyViewModel : ViewModel() {
      // ...state and logic...
  }
  ```
- Coroutine usage:
  ```kotlin
  viewModelScope.launch {
      val result = repository.loadData()
      // ...handle result...
  }
  ```

Always follow these standards unless explicitly instructed otherwise in the prompt.
