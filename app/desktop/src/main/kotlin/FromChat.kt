/**
 * Default-package entry point.
 *
 * The Java AWT toolkit derives the X11 WM_CLASS / Wayland xdg app-id from the class of the
 * thread's initial frame at toolkit construction — the `main` entry-point class — replacing
 * dots with dashes. Running the original `ru.fromchat.desktop.MainKt` registered the app as
 * `ru-fromchat-desktop-MainKt`, which is why GNOME showed the raw class name instead of the
 * app name and icon. A top-level default-package class named exactly `FromChat` makes the
 * identity "FromChat", which matches the packaged `.desktop` (and the app's self-installed
 * user entry from [ru.fromchat.desktop.MainKt]) for the deb, AppImage, and dev runs alike.
 */
class FromChat {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ru.fromchat.desktop.main(args)
        }
    }
}
