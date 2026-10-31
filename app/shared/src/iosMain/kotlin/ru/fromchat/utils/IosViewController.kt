
package ru.fromchat.utils

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Topmost view controller suitable for presenting UIKit sheets. */
fun iosTopViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.let {
        it.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene ->
                scene.windows.filterIsInstance<UIWindow>()
            }
            .firstOrNull { it.isKeyWindow() }
            ?: it.keyWindow
    }?.rootViewController

    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }

    return controller
}
