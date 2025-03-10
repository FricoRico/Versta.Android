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
    Settings,
    LanguageSettings,
    LanguageImport,
    LanguageDetails(
        listOf("sourceLanguage")
    ),
    TextTranslation,
    TranslationSettings,
    About,
    LanguageAttributions,
    ThirdParty,
    PrivacyPolicy,
    Troubleshooting,
    ApplicationLogs;

    operator fun invoke(): String {
        val argList = StringBuilder()
        args.let { nnArgs ->
            nnArgs.forEach { arg -> argList.append("/{$arg}") }
        }
        return name + argList
    }

    fun withArgs(vararg args: Any): String {
        val destination = StringBuilder()
        args.forEach { arg -> destination.append("/$arg") }
        return name + destination
    }

    companion object {
        fun byRoute(route: String): Screens {
            return valueOf(route.split("/").first())
        }
    }
}