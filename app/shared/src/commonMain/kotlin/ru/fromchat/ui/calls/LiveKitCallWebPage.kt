package ru.fromchat.ui.calls

/**
 * Shared LiveKit JS page for iOS [WKWebView] and desktop JavaFX [WebView].
 * Native LiveKit SDKs are used on Android; desktop has no JVM SDK.
 */
internal object LiveKitCallWebPage {
    private const val LIVEKIT_CDN =
        "https://cdn.jsdelivr.net/npm/livekit-client@2.15.4/dist/livekit-client.umd.min.js"

    val html: String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: #121212; overflow: hidden; font-family: system-ui, sans-serif;
  }
  #stage { position: relative; width: 100%; height: 100%; background: #000; }
  #remoteVideo, #remoteAudioHost video {
    position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; background: #000;
  }
  #localVideo {
    position: absolute; right: 12px; top: 12px; width: 28%; max-width: 160px;
    aspect-ratio: 3/4; object-fit: cover; border-radius: 12px; background: #333;
    z-index: 2; transform: scaleX(-1);
  }
  #status {
    position: absolute; left: 16px; right: 16px; top: 42%;
    text-align: center; color: #e8e8e8; font-size: 15px; z-index: 3;
    pointer-events: none;
  }
  #remoteAudioHost { position: absolute; width: 0; height: 0; overflow: hidden; }
</style>
</head>
<body>
<div id="stage">
  <video id="remoteVideo" autoplay playsinline></video>
  <video id="localVideo" autoplay playsinline muted></video>
  <div id="remoteAudioHost"></div>
  <div id="status">Connecting…</div>
</div>
<script src="$LIVEKIT_CDN"></script>
<script>
(function () {
  var LK = window.LivekitClient;
  var room = null;
  var statusEl = document.getElementById("status");
  var remoteVideo = document.getElementById("remoteVideo");
  var localVideo = document.getElementById("localVideo");
  var remoteAudioHost = document.getElementById("remoteAudioHost");

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text || "";
  }

  function notifyNative(method, message) {
    try {
      if (window.FromChatNative && typeof window.FromChatNative[method] === "function") {
        window.FromChatNative[method](message || "");
        return;
      }
    } catch (e) {}
    try {
      if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.FromChatCall) {
        window.webkit.messageHandlers.FromChatCall.postMessage({
          method: method,
          message: message || ""
        });
      }
    } catch (e2) {}
  }

  function attachRemoteTrack(track) {
    if (!track) return;
    if (track.kind === "video") {
      track.attach(remoteVideo);
      setStatus("");
    } else if (track.kind === "audio") {
      var el = track.attach();
      remoteAudioHost.appendChild(el);
    }
  }

  function detachAll() {
    try {
      if (room) {
        room.remoteParticipants.forEach(function (p) {
          p.trackPublications.forEach(function (pub) {
            if (pub.track) pub.track.detach();
          });
        });
      }
    } catch (e) {}
    remoteAudioHost.innerHTML = "";
  }

  async function connect(url, token) {
    if (!LK) {
      setStatus("LiveKit failed to load");
      notifyNative("onConnectFailed", "LiveKit client failed to load");
      return;
    }
    try {
      if (room) {
        await room.disconnect();
        room = null;
      }
      detachAll();
      setStatus("Connecting…");
      room = new LK.Room({ adaptiveStream: true, dynacast: true });
      room.on(LK.RoomEvent.TrackSubscribed, function (track) {
        attachRemoteTrack(track);
      });
      room.on(LK.RoomEvent.TrackUnsubscribed, function (track) {
        try { track.detach(); } catch (e) {}
      });
      room.on(LK.RoomEvent.Disconnected, function () {
        setStatus("");
      });
      room.on(LK.RoomEvent.MediaDevicesError, function (e) {
        notifyNative("onConnectFailed", (e && e.message) ? e.message : "Media device error");
      });
      await room.connect(url, token);
      await room.localParticipant.setMicrophoneEnabled(true);
      try {
        await room.localParticipant.setCameraEnabled(true);
      } catch (camErr) {
        // Audio-only is fine if camera is denied / unavailable.
      }
      var camPub = room.localParticipant.getTrackPublication(LK.Track.Source.Camera);
      if (camPub && camPub.track) {
        camPub.track.attach(localVideo);
      }
      room.remoteParticipants.forEach(function (p) {
        p.trackPublications.forEach(function (pub) {
          if (pub.isSubscribed && pub.track) attachRemoteTrack(pub.track);
        });
      });
      setStatus("");
      notifyNative("onConnected", "");
    } catch (err) {
      var msg = (err && err.message) ? err.message : String(err);
      setStatus(msg);
      notifyNative("onConnectFailed", msg);
    }
  }

  window.FromChatCallCtl = {
    setMicEnabled: function (on) {
      if (!room) return Promise.resolve();
      return room.localParticipant.setMicrophoneEnabled(!!on);
    },
    setCamEnabled: function (on) {
      if (!room) return Promise.resolve();
      return room.localParticipant.setCameraEnabled(!!on).then(function () {
        var camPub = room.localParticipant.getTrackPublication(LK.Track.Source.Camera);
        if (on && camPub && camPub.track) {
          camPub.track.attach(localVideo);
        } else if (!on) {
          try { localVideo.srcObject = null; } catch (e) {}
        }
      });
    },
    disconnect: function () {
      if (!room) return Promise.resolve();
      var r = room;
      room = null;
      detachAll();
      return r.disconnect();
    },
    connect: connect
  };

  window.fromChatConnect = connect;
})();
</script>
</body>
</html>
""".trimIndent()

    fun connectScript(serverUrl: String, token: String): String {
        val url = jsonStringLiteral(serverUrl)
        val tok = jsonStringLiteral(token)
        return "window.fromChatConnect($url, $tok);"
    }

    fun setMicScript(enabled: Boolean): String =
        "window.FromChatCallCtl && window.FromChatCallCtl.setMicEnabled(${if (enabled) "true" else "false"});"

    fun setCamScript(enabled: Boolean): String =
        "window.FromChatCallCtl && window.FromChatCallCtl.setCamEnabled(${if (enabled) "true" else "false"});"

    fun disconnectScript(): String =
        "window.FromChatCallCtl && window.FromChatCallCtl.disconnect();"

    private fun jsonStringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }
}
