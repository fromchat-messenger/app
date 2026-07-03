@file:Suppress("NOTHING_TO_INLINE", "unused")

package com.pr0gramm3r101.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.net.Uri
import android.os.BaseBundle
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.service.quicksettings.Tile
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import android.widget.ImageView.ScaleType
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.annotation.AttrRes
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.window.core.layout.WindowSizeClass
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.lang.ref.WeakReference
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

@OptIn(ExperimentalLayoutApi::class)
actual fun Modifier.clearFocusOnKeyboardDismiss(): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    var keyboardAppearedSinceLastFocused by remember { mutableStateOf(false) }
    if (isFocused) {
        val imeIsVisible = WindowInsets.isImeVisible
        val focusManager = LocalFocusManager.current
        LaunchedEffect(imeIsVisible) {
            if (imeIsVisible) {
                @Suppress("AssignedValueIsNeverRead")
                keyboardAppearedSinceLastFocused = true
            } else if (keyboardAppearedSinceLastFocused) {
                focusManager.clearFocus()
            }
        }
    }
    onFocusEvent {
        if (isFocused != it.isFocused) {
            isFocused = it.isFocused
            if (isFocused) {
                keyboardAppearedSinceLastFocused = false
            }
        }
    }
}

@SuppressLint("ComposableNaming")
@Composable
actual fun ToggleNavScrimEffect(enabled: Boolean) {
    val context = LocalContext.current as Activity
    DisposableEffect(enabled) {
        val window = context.window
        val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = enabled
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = previousContrastEnforced
            }
        }
    }
}

@Composable
actual fun DialogEdgeToEdgeEffect() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }
}

val screenWidth: Int inline get() = Resources.getSystem().displayMetrics.widthPixels
@Suppress("unused")
val screenHeight: Int inline get() = Resources.getSystem().displayMetrics.heightPixels

fun appName(context: Context, packageName: String): String? {
    try {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfo(
            packageName, PackageManager.GET_META_DATA
        )
        val appName = packageManager.getApplicationLabel(applicationInfo) as String
        return appName
    } catch (_: PackageManager.NameNotFoundException) {
        return null
    }
}

fun Context.homeScreen() {
    val intentHome = Intent(ACTION_MAIN)
    intentHome.addCategory(Intent.CATEGORY_HOME)
    intentHome.flags = FLAG_ACTIVITY_NEW_TASK
    startActivity(intentHome)
}


inline fun async(noinline exec: () -> Unit) = Thread(exec).apply { start() }

inline fun Tile.configure(apply: Tile.() -> Unit) {
    apply.invoke(this)
    updateTile()
}

inline fun View.addOneTimeOnPreDrawListener(crossinline listener: () -> Boolean) {
    viewTreeObserver.addOnPreDrawListener(object: OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            return listener()
        }
    })
}

fun Context.launchFiles(): Boolean {
    val primaryStorageUri = "content://com.android.externalstorage.documents/root/primary".toUri()

    fun launchIntentWithComponent(action: String, componentName: ComponentName? = null): Boolean {
        val intent = Intent(action, primaryStorageUri)
        if (componentName != null) {
            intent.setComponent(componentName)
        }
        try {
            startActivity(intent)
            return true
        } catch (_: Throwable) {
            return false
        }
    }
    fun intent1() = launchIntentWithComponent(
        Intent.ACTION_VIEW,
        ComponentName(
            "com.google.android.documentsui",
            "com.android.documentsui.files.FilesActivity"
        )
    )
    fun intent2() = launchIntentWithComponent(
        Intent.ACTION_VIEW,
        ComponentName(
            "com.android.documentsui",
            "com.android.documentsui.files.FilesActivity"
        )
    )
    fun intent3() = launchIntentWithComponent(
        Intent.ACTION_VIEW,
        ComponentName(
            "com.android.documentsui",
            "com.android.documentsui.FilesActivity"
        )
    )
    fun intent4() = launchIntentWithComponent(Intent.ACTION_VIEW)
    fun intent5() = launchIntentWithComponent("android.provider.action.BROWSE")
    fun intent6() = launchIntentWithComponent("android.provider.action.BROWSE_DOCUMENT_ROOT")

    return intent1() || intent2() || intent3() || intent4() || intent5() || intent6()
}

inline fun Fragment.getSystemService(name: String): Any? = requireActivity().getSystemService(name)
inline fun <T> Fragment.getSystemService(cls: Class<T>): T? = requireActivity().getSystemService(cls)

inline fun <T: Any> Context.getSystemService(cls: KClass<T>): T? = getSystemService(cls.java)
inline fun <T: Any> Fragment.getSystemService(cls: KClass<T>): T? = requireActivity().getSystemService(cls)

typealias ActivityLauncher = BetterActivityResult<Intent, ActivityResult>

val ActivityResultCaller.activityResultLauncher get() = BetterActivityResult.registerActivityForResult(this)

@Suppress("unused")
fun getOpenDocumentIntent(vararg fileTypes: String) =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        if (fileTypes.size == 1) {
            type = fileTypes[0]
        } else {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, fileTypes)
        }
    }

fun Context.getFileName(uri: Uri): String {
    if (uri.scheme == "content") {
        contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )!!.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
    } else {
        throw UnsupportedOperationException("Cannot process non-\"content://\" URIs")
    }
    return uri.lastPathSegment.let { name ->
        name!!
        val matcher = Pattern.compile("\\w+:(.*/)*(.*)").matcher(name)
        if (matcher.find()) {
            matcher.group(2)
        } else {
            name
        }
    }
}

@Suppress("unused")
inline fun Fragment.getFileName(uri: Uri) = requireActivity().getFileName(uri)

@Suppress("unused")
val UsbInterface.endpointList: List<UsbEndpoint> get() {
    val list = mutableListOf<UsbEndpoint>()
    for (i in 0 until endpointCount) {
        list.add(getEndpoint(i))
    }
    return list.toList()
}

fun Activity.resolveAttr(@AttrRes id: Int): Int? {
    val typedValue = TypedValue()
    if (theme.resolveAttribute(
            id,
            typedValue,
            true
        )) {
        val resId = typedValue.resourceId
        return if (resId != 0) resId else null
    } else {
        return null
    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class QuickAlertDialogBuilder(context: Context): MaterialAlertDialogBuilder(context) {
    fun title(title: CharSequence): QuickAlertDialogBuilder {
        setTitle(title)
        return this
    }

    fun title(@StringRes titleRes: Int): QuickAlertDialogBuilder {
        setTitle(titleRes)
        return this
    }

    fun message(message: CharSequence): QuickAlertDialogBuilder {
        setMessage(message)
        return this
    }
    fun message(@StringRes messageRes: Int): QuickAlertDialogBuilder {
        setMessage(messageRes)
        return this
    }

    fun body(body: CharSequence) = message(body)
    fun body(@StringRes bodyRes: Int) = message(bodyRes)

    inline fun positiveButton(
        text: CharSequence,
        crossinline listener: () -> Unit
    ) : QuickAlertDialogBuilder {
        setPositiveButton(text) { _, _ -> listener() }
        return this
    }
    inline fun positiveButton(@StringRes textRes: Int, crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setPositiveButton(textRes) { _, _ -> listener() }
        return this
    }
    inline fun positiveButton(text: CharSequence, listener: Nothing? = null): QuickAlertDialogBuilder {
        setPositiveButton(text, null)
        return this
    }
    inline fun positiveButton(@StringRes text: Int, listener: Nothing? = null): QuickAlertDialogBuilder {
        setPositiveButton(text, null)
        return this
    }

    inline fun negativeButton(text: CharSequence, crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setNegativeButton(text) { _, _ -> listener() }
        return this
    }
    inline fun negativeButton(@StringRes textRes: Int, crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setNegativeButton(textRes) { _, _ -> listener() }
        return this
    }
    inline fun negativeButton(text: CharSequence, listener: Nothing? = null): QuickAlertDialogBuilder {
        setNegativeButton(text, null)
        return this
    }
    inline fun negativeButton(@StringRes text: Int, listener: Nothing? = null): QuickAlertDialogBuilder {
        setNegativeButton(text, null)
        return this
    }

    inline fun neutralButton(text: CharSequence, crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setNeutralButton(text) { _, _ -> listener() }
        return this
    }
    inline fun neutralButton(@StringRes textRes: Int, crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setNeutralButton(textRes) { _, _ -> listener() }
        return this
    }
    inline fun neutralButton(text: CharSequence, listener: Nothing? = null): QuickAlertDialogBuilder {
        setNeutralButton(text, null)
        return this
    }
    inline fun neutralButton(@StringRes text: Int, listener: Nothing? = null): QuickAlertDialogBuilder {
        setNeutralButton(text, null)
        return this
    }

    inline fun onCancel(crossinline listener: () -> Unit): QuickAlertDialogBuilder {
        setOnCancelListener { listener() }
        return this
    }

    inline fun cancelable(value: Boolean): QuickAlertDialogBuilder {
        setCancelable(value)
        return this
    }
}

inline fun Activity.alertDialog(crossinline config: QuickAlertDialogBuilder.() -> Unit): AlertDialog {
    val builder = QuickAlertDialogBuilder(this)
    config(builder)
    return builder.show()
}

@Suppress("unused")
inline fun Fragment.alertDialog(crossinline config: QuickAlertDialogBuilder.() -> Unit)
        = requireActivity().alertDialog(config)

@Suppress("unused")
class AuthenticationConfig {
    var success: ((BiometricPrompt.AuthenticationResult) -> Unit)? = null
    var fail: (() -> Unit)? = null
    var error: ((Int, String) -> Unit)? = null
    var always: (() -> Unit)? = null

    fun success(block: (BiometricPrompt.AuthenticationResult) -> Unit) {
        success = block
    }

    inline fun ail(noinline block: () -> Unit) {
        fail = block
    }

    inline fun error(noinline block: (Int, String) -> Unit) {
        error = block
    }

    inline fun always(noinline block: () -> Unit) {
        always = block
    }

    lateinit var title: String
    var subtitle: String? = null
    lateinit var negativeButtonText: String
}

inline fun FragmentActivity.requestAuthentication(crossinline callback: AuthenticationConfig.() -> Unit): BiometricPrompt {
    val config = AuthenticationConfig()
    callback(config)
    try {
        config.title
        config.negativeButtonText
    } catch (_: UninitializedPropertyAccessException) {
        throw IllegalStateException("Required fields haven't been set")
    }
    val executor = ContextCompat.getMainExecutor(this)
    val biometricPrompt = BiometricPrompt(
        this,
        executor,
        object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                config.fail?.invoke()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                config.success?.invoke(result)
                config.always?.invoke()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                config.error?.invoke(errorCode, "$errString")
                config.always?.invoke()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
        setTitle(config.title)
        setSubtitle(config.subtitle)
        setNegativeButtonText(config.negativeButtonText)
        setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        setConfirmationRequired(false)
    }.build()
    biometricPrompt.authenticate(promptInfo)
    return biometricPrompt
}

fun ContentScale.asAndroidScaleType(): ScaleType? {
    return when (this) {
        ContentScale.Fit -> ScaleType.FIT_CENTER
        ContentScale.Crop -> ScaleType.CENTER_CROP
        ContentScale.FillWidth,
        ContentScale.FillHeight,
        ContentScale.FillBounds -> ScaleType.FIT_XY
        ContentScale.Inside -> ScaleType.CENTER_INSIDE
        else -> null
    }
}

inline fun Context.openUrl(url: Uri) {
    startActivity(Intent(Intent.ACTION_VIEW, url))
}

inline fun Context.openUrl(url: String) {
    openUrl(
        url.let {
            val regex = "^[\\w-_]://".toRegex()
            var th = it
            if (!regex.containsMatchIn(th)) {
                th = "https://$th"
            }
            th
        }.toUri()
    )
}

inline fun ComponentActivity.ComposeView(crossinline init: @Composable () -> Unit) =
    ComposeView(this).apply {
        setContent {
            init()
        }
    }

inline fun backCallback(enabled: Boolean = true, crossinline callback: OnBackPressedCallback.() -> Unit)
        = object: OnBackPressedCallback(enabled) {
    override fun handleOnBackPressed() {
        callback(this)
    }
}

fun String.encrypt(password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val secretKey = factory.generateSecret(spec)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val iv = ByteArray(cipher.blockSize).also { SecureRandom().nextBytes(it) }
    val params = IvParameterSpec(iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, params)

    val encryptedBytes = cipher.doFinal(this.toByteArray())
    return "${Base64.encodeToString(salt, Base64.DEFAULT)}:${Base64.encodeToString(iv, Base64.DEFAULT)}:${Base64.encodeToString(encryptedBytes, Base64.DEFAULT)}"
}

fun String.decrypt(password: String): String {
    val parts = this.split(":")
    val salt = Base64.decode(parts[0], Base64.DEFAULT)
    val iv = Base64.decode(parts[1], Base64.DEFAULT)
    val encryptedBytes = Base64.decode(parts[2], Base64.DEFAULT)

    val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val secretKey = factory.generateSecret(spec)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val params = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, params)

    val decryptedBytes = cipher.doFinal(encryptedBytes)
    return String(decryptedBytes)
}

@Suppress("unused")
inline fun waitUntil(timeout: Long = 0, condition: () -> Boolean): Boolean {
    val time = System.currentTimeMillis()
    while (!condition()) {
        if (timeout > 0 && System.currentTimeMillis() - time > timeout) {
            return false
        }
    }
    return true
}

inline fun waitWhile(timeout: Long = 0, condition: () -> Boolean): Boolean {
    val time = System.currentTimeMillis()
    while (condition()) {
        if (timeout > 0 && System.currentTimeMillis() - time > timeout) {
            return false
        }
    }
    return true
}

inline fun orientationSensorEventListener(
    crossinline callback: (Float, Float, Float) -> Unit
) = object: SensorEventListener {
    private var accelerometerData: FloatArray? = null
    private var geomagneticData: FloatArray? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) accelerometerData = event.values
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) geomagneticData = event.values
        if (accelerometerData != null && geomagneticData != null) {
            val R = FloatArray(9)
            val success = SensorManager.getRotationMatrix(
                R,
                FloatArray(9),
                accelerometerData,
                geomagneticData
            )
            if (success) {
                val orientationData = FloatArray(3)
                SensorManager.getOrientation(R, orientationData)
                callback(
                    orientationData[0],
                    orientationData[1],
                    orientationData[2]
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

inline fun SensorEventListener(crossinline callback: (SensorEvent) -> Unit) = object: SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        callback(event)
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

inline val Context.isScreenLocked get() = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked

@Suppress("MemberVisibilityCanBePrivate", "unused")
class NotificationIdManager(vararg reservedIds: Int) {
    private val reserved = reservedIds.toMutableList()

    fun reserve(id: Int) {
        reserved.add(id)
    }

    fun release(id: Int) {
        reserved.remove(id)
    }

    fun get(): Int {
        while (true) {
            val random = Random.nextInt(0..Int.MAX_VALUE)
            if (!reserved.contains(random)) {
                return random
            }
        }
    }

    fun getAndReserve(): Int {
        val id = get()
        reserve(id)
        return id
    }
}

@Composable
fun PaddingValues.copy(
    start: Dp? = null,
    end: Dp? = null,
    top: Dp? = null,
    bottom: Dp? = null
) = PaddingValues(
    start = start ?: calculateStartPadding(LocalLayoutDirection.current),
    end = end ?: calculateEndPadding(LocalLayoutDirection.current),
    top = top ?: calculateTopPadding(),
    bottom = bottom ?: calculateBottomPadding()
)

@Suppress("unused")
@Composable
fun PaddingValues.copy(
    horizontal: Dp? = null,
    vertical: Dp? = null
) = copy(
    start = horizontal,
    end = horizontal,
    top = vertical,
    bottom = vertical
)

inline fun InputStream.test() =
    try {
        read()
        close()
        true
    } catch (_: IOException) {
        false
    }

inline fun ContentResolver.test(item: Uri) =
    try {
        openInputStream(item)!!.test()
    } catch (_: Exception) {
        false
    }

inline fun <reified T: Parcelable> Intent.getParcelableExtraAs(key: String): T {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }!!
}

inline fun <reified T: Serializable> Intent.getSerializableExtraAs(key: String): T {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as T
    }!!
}

fun ActivityInfo.contentEquals(other: ActivityInfo): Boolean {
    return (
            (
                    if (Build.VERSION.SDK_INT >= 26)
                        colorMode == other.colorMode
                    else true
                    ) &&
                    (
                            if (Build.VERSION.SDK_INT >= 34)
                                requiredDisplayCategory == other.requiredDisplayCategory
                            else true
                            ) &&
                    theme == other.theme &&
                    launchMode == other.launchMode &&
                    documentLaunchMode == other.documentLaunchMode &&
                    permission == other.permission &&
                    taskAffinity == other.taskAffinity &&
                    targetActivity == other.targetActivity &&
                    flags == other.flags &&
                    screenOrientation == other.screenOrientation &&
                    configChanges == other.configChanges &&
                    softInputMode == other.softInputMode &&
                    uiOptions == other.uiOptions &&
                    parentActivityName == other.parentActivityName &&
                    maxRecents == other.maxRecents &&
                    windowLayout == other.windowLayout
            )
}

val ActivityInfo.launchIntent get() = Intent().apply {
    component = ComponentName(packageName!!, name)
    flags = FLAG_ACTIVITY_NEW_TASK
}

@SuppressLint("QueryPermissionsNeeded")
@RequiresPermission(Manifest.permission.QUERY_ALL_PACKAGES)
fun ActivityInfo.isLauncher(context: Context): Boolean {
    with (context) {
        @Suppress("ExplicitThis")
        val intent = launchIntent.apply {
            action = ACTION_MAIN
            addCategory(CATEGORY_LAUNCHER)
            component = ComponentName(this@isLauncher.packageName!!, name)
            flags = FLAG_ACTIVITY_NEW_TASK
        }
        val infos = packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
        for (info in infos) {
            val filter = info.filter
            if (
                info.activityInfo.contentEquals(this@isLauncher) &&
                filter != null &&
                filter.hasAction(ACTION_MAIN) &&
                filter.hasCategory(CATEGORY_LAUNCHER)
            ) {
                return true
            }
        }
        return false
    }
}



@Suppress("unused", "RedundantSuppression")
operator fun <T> WeakReference<T>.getValue(thisRef: Any?, property: KProperty<*>) = get()!!

@Suppress("unused")
inline fun runOrLog(
    tag: String,
    message: String = "An error occurred:",
    crossinline block: () -> Unit
) {
    try {
        block()
    } catch (e: Exception) {
        Log.e(tag, message, e)
    }
}

@Suppress("unused")
typealias WidthSizeClass = WindowWidthSizeClass
@Suppress("unused")
typealias HeightSizeClass = WindowHeightSizeClass
@Suppress("unused")
typealias SizeClass = WindowSizeClass

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
inline fun Context.notifyIfAllowed(
    id: Int,
    notification: Notification
) {
    with (NotificationManagerCompat.from(this))  {
        if (
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@with
        }
        notify(id, notification)
    }
}

@Suppress("DEPRECATION")
fun BaseBundle.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (key in keySet()) {
        map[key] = get(key)!!
    }
    return map
}

fun Bundle.toPersistableBundle(): PersistableBundle {
    val map = toMap()
    val bundle = PersistableBundle()
    for ((key, value) in map) {
        when (value) {
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is String -> bundle.putString(key, value)
            is IntArray -> bundle.putIntArray(key, value)
            is LongArray -> bundle.putLongArray(key, value)
            is DoubleArray -> bundle.putDoubleArray(key, value)
            is PersistableBundle -> bundle.putPersistableBundle(key, value)
            is Boolean -> bundle.putBoolean(key, value)
            is BooleanArray -> bundle.putBooleanArray(key, value)
            is Array<*> -> {
                if (value.isArrayOf<String>()) {
                    @Suppress("UNCHECKED_CAST")
                    bundle.putStringArray(key, value as Array<String>)
                }
            }
        }
    }
    return bundle
}

inline fun String.decodeWindows1251() = String(
    toByteArray(Charsets.ISO_8859_1),
    Charsets.UTF_8
)

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
actual val materialYouAvailable get() = Build.VERSION.SDK_INT >= 31

private fun String?.trimIfNotBlank(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

actual fun currentDeviceInfo(): CurrentDeviceInfo {
    val osName = "Android"
    val osVersion = Build.VERSION.RELEASE.trimIfNotBlank() ?: Build.VERSION.SDK_INT.toString()
    val brand = Build.BRAND.trimIfNotBlank() ?: Build.MANUFACTURER.trimIfNotBlank()
    val model = Build.MODEL.trimIfNotBlank()

    return CurrentDeviceInfo(
        osName = osName,
        osVersion = osVersion,
        deviceType = "mobile",
        deviceName = model?.let {
            if (it.contains(
                    brand.orEmpty(),
                    ignoreCase = true
                )
            ) it else listOfNotNull(brand, it).joinToString(" ")
        },
        brand = brand,
        model = model
    )
}
