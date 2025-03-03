package app.versta.translate.ui.screen

enum class StatusBarStyle {
    Light,
    Dark
}

enum class Screens (
    private val args: List<String> = emptyList(),
    val statusBarStyle: StatusBarStyle = StatusBarStyle.Light
) {
    Home,
    Camera,
    Settings,
    LanguageSettings,
    LanguageImport,
    TextTranslation,
    TranslationSettings,
    About,
    LanguageAttributions,
    ThirdParty,
    PrivacyPolicy;

    operator fun invoke(): String {
        val argList = StringBuilder()
        args.let { nnArgs ->
            nnArgs.forEach { arg -> argList.append("/{$arg}") }
        }
        return name + argList
    }
}