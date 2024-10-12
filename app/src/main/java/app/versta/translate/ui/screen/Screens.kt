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
    Settings(statusBarStyle = StatusBarStyle.Dark),
    LanguageSettings(statusBarStyle = StatusBarStyle.Dark),
    LanguageImport(statusBarStyle = StatusBarStyle.Dark);

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
}