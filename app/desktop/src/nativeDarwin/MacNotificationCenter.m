#import <AppKit/AppKit.h>
#import <CoreGraphics/CoreGraphics.h>
#import <CoreServices/CoreServices.h>
#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>
#import <jni.h>

@class FromChatNotificationDelegate;

static JavaVM *fromchatJvm = NULL;
static FromChatNotificationDelegate *fromchatDelegate = nil;
static jclass fromchatMacNotificationCenterClass = NULL;

@interface FromChatNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

static void fromchatCallJavaStaticVoid(const char *methodName, const char *utfArg);

@implementation FromChatNotificationDelegate

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    UNNotificationPresentationOptions options =
        UNNotificationPresentationOptionBanner |
        UNNotificationPresentationOptionList |
        UNNotificationPresentationOptionSound |
        UNNotificationPresentationOptionBadge;
    NSLog(
        @"FromChat UN willPresent id=%@ nsAppActive=%d options=%lu",
        notification.request.identifier,
        [NSApp isActive],
        (unsigned long)options
    );
    completionHandler(options);
    fromchatCallJavaStaticVoid("onNativeWillPresent", notification.request.identifier.UTF8String);
}

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
didReceiveNotificationResponse:(UNNotificationResponse *)response
         withCompletionHandler:(void (^)(void))completionHandler {
    NSString *action = response.actionIdentifier;
    NSString *identifier = response.notification.request.identifier;
    NSLog(@"FromChat UN response action=%@ id=%@", action, identifier);
    if ([action isEqualToString:UNNotificationDismissActionIdentifier]) {
        completionHandler();
        return;
    }
    fromchatCallJavaStaticVoid("onNativeActivated", identifier.UTF8String);
    completionHandler();
}

@end

static void fromchatCallJavaStaticVoid(const char *methodName, const char *utfArg) {
    if (fromchatJvm == NULL || fromchatMacNotificationCenterClass == NULL || methodName == NULL) {
        return;
    }
    JNIEnv *env = NULL;
    jint getEnv = (*fromchatJvm)->GetEnv(fromchatJvm, (void **)&env, JNI_VERSION_1_8);
    jboolean attachedHere = JNI_FALSE;
    if (getEnv == JNI_EDETACHED) {
        if ((*fromchatJvm)->AttachCurrentThread(fromchatJvm, (void **)&env, NULL) != JNI_OK) {
            return;
        }
        attachedHere = JNI_TRUE;
    }
    if (env == NULL) return;
    jmethodID mid = (*env)->GetStaticMethodID(
        env,
        fromchatMacNotificationCenterClass,
        methodName,
        "(Ljava/lang/String;)V"
    );
    if (mid != NULL) {
        jstring jid = utfArg != NULL ? (*env)->NewStringUTF(env, utfArg) : NULL;
        (*env)->CallStaticVoidMethod(env, fromchatMacNotificationCenterClass, mid, jid);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        if (jid != NULL) (*env)->DeleteLocalRef(env, jid);
    }
    if (attachedHere) {
        (*fromchatJvm)->DetachCurrentThread(fromchatJvm);
    }
}

static BOOL fromchatIsBundledApp(void) {
    NSString *path = [NSBundle mainBundle].bundlePath;
    return [path.pathExtension isEqualToString:@"app"];
}

static UNUserNotificationCenter *fromchatNotificationCenter(void) {
    NSBundle *bundle = [NSBundle mainBundle];
    if (!fromchatIsBundledApp()) {
        NSLog(@"FromChat UN skip: not an .app (path=%@ id=%@)", bundle.bundlePath, bundle.bundleIdentifier);
        return nil;
    }
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];
    NSLog(@"FromChat UN center=%p path=%@ id=%@", center, bundle.bundlePath, bundle.bundleIdentifier);
    return center;
}

static void fromchatRunOnMain(void (^block)(void)) {
    if ([NSThread isMainThread]) {
        block();
    } else {
        dispatch_sync(dispatch_get_main_queue(), block);
    }
}

static void fromchatEnsureDelegate(UNUserNotificationCenter *center) {
    if (center == nil) return;
    fromchatRunOnMain(^{
        if (fromchatDelegate == nil) {
            fromchatDelegate = [FromChatNotificationDelegate new];
        }
        center.delegate = fromchatDelegate;
        NSLog(@"FromChat UN delegate set on %@", center);
    });
}

static NSRunningApplication *fromchatOtherFrontApp(void) {
    NSString *ours = [NSBundle mainBundle].bundleIdentifier;
    pid_t ourPid = [NSRunningApplication currentApplication].processIdentifier;
    NSRunningApplication *wsFront = [[NSWorkspace sharedWorkspace] frontmostApplication];
    if (wsFront != nil && wsFront.processIdentifier != ourPid &&
        (ours == nil || ![wsFront.bundleIdentifier isEqualToString:ours])) {
        return wsFront;
    }
    CFArrayRef info = CGWindowListCopyWindowInfo(
        kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements,
        kCGNullWindowID
    );
    if (info == NULL) return nil;
    NSArray *windows = CFBridgingRelease(info);
    for (NSDictionary *win in windows) {
        NSNumber *layer = win[(id)kCGWindowLayer];
        if (layer == nil || layer.intValue != 0) continue;
        NSNumber *pidNum = win[(id)kCGWindowOwnerPID];
        if (pidNum == nil) continue;
        pid_t pid = pidNum.intValue;
        if (pid == ourPid) continue;
        NSRunningApplication *app = [NSRunningApplication runningApplicationWithProcessIdentifier:pid];
        if (app == nil) continue;
        if (app.activationPolicy != NSApplicationActivationPolicyRegular) continue;
        return app;
    }
    return nil;
}

static BOOL fromchatIsAppFrontmost(void) {
    __block BOOL frontmost = NO;
    fromchatRunOnMain(^{
        NSString *frontId = [[NSWorkspace sharedWorkspace] frontmostApplication].bundleIdentifier;
        NSString *ours = [NSBundle mainBundle].bundleIdentifier;
        frontmost = frontId != nil && ours != nil && [frontId isEqualToString:ours];
        NSLog(
            @"FromChat frontmost=%d front=%@ ours=%@ nsAppActive=%d",
            frontmost,
            frontId,
            ours,
            [NSApp isActive]
        );
    });
    return frontmost;
}

static void fromchatResignActive(void) {
    fromchatRunOnMain(^{
        [NSApp deactivate];
        NSRunningApplication *other = fromchatOtherFrontApp();
        if (other != nil) {
            if (@available(macOS 14.0, *)) {
                [NSApp yieldActivationToApplication:other];
            } else {
                [other activateWithOptions:NSApplicationActivateIgnoringOtherApps];
            }
        }
        NSLog(
            @"FromChat resignActive nsAppActive=%d front=%@",
            [NSApp isActive],
            [[NSWorkspace sharedWorkspace] frontmostApplication].bundleIdentifier
        );
    });
}

static void fromchatYieldIfNotFrontmost(void) {
    fromchatResignActive();
}

static BOOL fromchatDeliverNotification(
    UNUserNotificationCenter *center,
    NSString *title,
    NSString *body,
    NSString *subtitle,
    NSString *identifier,
    BOOL playSound,
    BOOL windowFocused
) {
    if (center == nil) return NO;
    fromchatEnsureDelegate(center);
    if (windowFocused != YES) {
        fromchatResignActive();
    }

    UNMutableNotificationContent *content = [UNMutableNotificationContent new];
    content.title = title;
    if (subtitle.length > 0) {
        content.subtitle = subtitle;
    }
    content.body = body;
    content.sound = playSound ? [UNNotificationSound defaultSound] : nil;
    if (@available(macOS 12.0, *)) {
        content.interruptionLevel = UNNotificationInterruptionLevelActive;
    }

    UNNotificationRequest *request =
        [UNNotificationRequest requestWithIdentifier:identifier content:content trigger:nil];
    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    __block BOOL ok = NO;
    [center addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
        ok = error == nil;
        NSLog(
            @"FromChat UN add id=%@ ok=%d focused=%d nsAppActive=%d error=%@",
            identifier,
            ok,
            windowFocused == YES,
            [NSApp isActive],
            error
        );
        dispatch_semaphore_signal(semaphore);
    }];
    dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 5 * NSEC_PER_SEC));
    return ok;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    fromchatJvm = vm;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) == JNI_OK && env != NULL) {
        jclass local = (*env)->FindClass(env, "ru/fromchat/desktop/MacNotificationCenter");
        if (local != NULL) {
            fromchatMacNotificationCenterClass = (*env)->NewGlobalRef(env, local);
            (*env)->DeleteLocalRef(env, local);
        }
    }
    return JNI_VERSION_1_8;
}

JNIEXPORT jboolean JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeRequestAuthorization(
    JNIEnv *env,
    jclass cls
) {
    UNUserNotificationCenter *center = fromchatNotificationCenter();
    if (center == nil) return JNI_FALSE;
    fromchatEnsureDelegate(center);

    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    __block BOOL grantedResult = NO;
    void (^request)(void) = ^{
        [center requestAuthorizationWithOptions:(UNAuthorizationOptionAlert |
                                                 UNAuthorizationOptionSound |
                                                 UNAuthorizationOptionBadge)
                              completionHandler:^(BOOL granted, NSError * _Nullable error) {
                                  grantedResult = granted;
                                  NSLog(@"FromChat UN auth granted=%d error=%@", granted, error);
                                  dispatch_semaphore_signal(semaphore);
                              }];
    };
    if ([NSThread isMainThread]) {
        request();
    } else {
        dispatch_async(dispatch_get_main_queue(), request);
    }
    dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 10 * NSEC_PER_SEC));
    return grantedResult ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeAuthorizationStatus(
    JNIEnv *env,
    jclass cls
) {
    UNUserNotificationCenter *center = fromchatNotificationCenter();
    if (center == nil) return 0;
    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    __block NSInteger status = 0;
    [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        status = settings.authorizationStatus;
        if (@available(macOS 12.0, *)) {
            NSLog(
                @"FromChat UN status=%ld alert=%ld sound=%ld badge=%ld preview=%ld",
                (long)settings.authorizationStatus,
                (long)settings.alertSetting,
                (long)settings.soundSetting,
                (long)settings.badgeSetting,
                (long)settings.showPreviewsSetting
            );
        } else {
            NSLog(@"FromChat UN status=%ld alert=%ld sound=%ld badge=%ld",
                  (long)settings.authorizationStatus,
                  (long)settings.alertSetting,
                  (long)settings.soundSetting,
                  (long)settings.badgeSetting);
        }
        dispatch_semaphore_signal(semaphore);
    }];
    dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 5 * NSEC_PER_SEC));
    return (jint)status;
}

JNIEXPORT jboolean JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeDeliver(
    JNIEnv *env,
    jclass cls,
    jstring jTitle,
    jstring jBody,
    jstring jSubtitle,
    jstring jIdentifier,
    jboolean playSound,
    jboolean windowFocused
) {
    UNUserNotificationCenter *center = fromchatNotificationCenter();
    if (center == nil) return JNI_FALSE;

    const char *titleChars = (*env)->GetStringUTFChars(env, jTitle, NULL);
    const char *bodyChars = (*env)->GetStringUTFChars(env, jBody, NULL);
    const char *subtitleChars = jSubtitle ? (*env)->GetStringUTFChars(env, jSubtitle, NULL) : NULL;
    const char *idChars = (*env)->GetStringUTFChars(env, jIdentifier, NULL);
    NSString *title = titleChars ? [NSString stringWithUTF8String:titleChars] : @"";
    NSString *body = bodyChars ? [NSString stringWithUTF8String:bodyChars] : @"";
    NSString *subtitle = subtitleChars ? [NSString stringWithUTF8String:subtitleChars] : @"";
    NSString *identifier = idChars ? [NSString stringWithUTF8String:idChars] : [[NSUUID UUID] UUIDString];

    __block BOOL ok = NO;
    void (^deliver)(void) = ^{
        ok = fromchatDeliverNotification(
            center,
            title,
            body,
            subtitle,
            identifier,
            playSound == JNI_TRUE,
            windowFocused
        );
    };
    if ([NSThread isMainThread]) {
        deliver();
    } else {
        dispatch_sync(dispatch_get_main_queue(), deliver);
    }

    if (titleChars) (*env)->ReleaseStringUTFChars(env, jTitle, titleChars);
    if (bodyChars) (*env)->ReleaseStringUTFChars(env, jBody, bodyChars);
    if (subtitleChars) (*env)->ReleaseStringUTFChars(env, jSubtitle, subtitleChars);
    if (idChars) (*env)->ReleaseStringUTFChars(env, jIdentifier, idChars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeRemove(
    JNIEnv *env,
    jclass cls,
    jobjectArray jIdentifiers
) {
    UNUserNotificationCenter *center = fromchatNotificationCenter();
    if (center == nil || jIdentifiers == NULL) return;
    jsize count = (*env)->GetArrayLength(env, jIdentifiers);
    NSMutableArray<NSString *> *ids = [NSMutableArray arrayWithCapacity:(NSUInteger)count];
    for (jsize i = 0; i < count; i++) {
        jstring jId = (*env)->GetObjectArrayElement(env, jIdentifiers, i);
        if (jId == NULL) continue;
        const char *chars = (*env)->GetStringUTFChars(env, jId, NULL);
        if (chars) {
            [ids addObject:[NSString stringWithUTF8String:chars]];
            (*env)->ReleaseStringUTFChars(env, jId, chars);
        }
        (*env)->DeleteLocalRef(env, jId);
    }
    NSLog(@"FromChat UN remove count=%lu", (unsigned long)ids.count);
    [center removeDeliveredNotificationsWithIdentifiers:ids];
    [center removePendingNotificationRequestsWithIdentifiers:ids];
}

JNIEXPORT void JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeRemoveAll(
    JNIEnv *env,
    jclass cls
) {
    UNUserNotificationCenter *center = fromchatNotificationCenter();
    if (center == nil) return;
    NSLog(@"FromChat UN removeAll");
    [center removeAllDeliveredNotifications];
    [center removeAllPendingNotificationRequests];
}

JNIEXPORT jboolean JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeOpenSettings(
    JNIEnv *env,
    jclass cls
) {
    NSURL *url = [NSURL URLWithString:
        @"x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=ru.fromchat.desktop"];
    if (url == nil) return JNI_FALSE;
    BOOL opened = [[NSWorkspace sharedWorkspace] openURL:url];
    if (!opened) {
        url = [NSURL URLWithString:@"x-apple.systempreferences:com.apple.preference.notifications"];
        opened = url != nil && [[NSWorkspace sharedWorkspace] openURL:url];
    }
    return opened ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeDebugInfo(
    JNIEnv *env,
    jclass cls
) {
    __block NSString *info = @"";
    fromchatRunOnMain(^{
        NSBundle *bundle = [NSBundle mainBundle];
        NSString *frontId = [[NSWorkspace sharedWorkspace] frontmostApplication].bundleIdentifier;
        NSString *ours = bundle.bundleIdentifier;
        BOOL frontmost = frontId != nil && ours != nil && [frontId isEqualToString:ours];
        info = [NSString stringWithFormat:
                @"bundlePath=%@ bundleId=%@ bundled=%d frontmost=%d front=%@ nsAppActive=%d",
                bundle.bundlePath,
                ours ?: @"(null)",
                fromchatIsBundledApp() ? 1 : 0,
                frontmost ? 1 : 0,
                frontId ?: @"(null)",
                [NSApp isActive] ? 1 : 0];
    });
    return (*env)->NewStringUTF(env, info.UTF8String);
}

JNIEXPORT jboolean JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeIsAppFrontmost(
    JNIEnv *env,
    jclass cls
) {
    return fromchatIsAppFrontmost() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeResignActive(
    JNIEnv *env,
    jclass cls
) {
    fromchatResignActive();
}

JNIEXPORT void JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeYieldActivation(
    JNIEnv *env,
    jclass cls
) {
    fromchatYieldIfNotFrontmost();
}

JNIEXPORT jboolean JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeIsBundled(
    JNIEnv *env,
    jclass cls
) {
    return fromchatIsBundledApp() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_ru_fromchat_desktop_MacNotificationCenter_nativeRegisterBundle(
    JNIEnv *env,
    jclass cls
) {
    if (!fromchatIsBundledApp()) return;
    NSURL *url = [NSBundle mainBundle].bundleURL;
    if (url == nil) return;
    LSRegisterURL((__bridge CFURLRef)url, true);
}
