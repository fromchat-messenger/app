@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.fromchat.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Topmost view controller suitable for presenting UIKit sheets. */
fun iosTopViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.connectedScenes
        .mapNotNull { it as? UIWindowScene }
        .flatMap { scene ->
            scene.windows.mapNotNull { it as? UIWindow }
        }
        .firstOrNull { it.isKeyWindow() }
        ?: application.keyWindow
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
