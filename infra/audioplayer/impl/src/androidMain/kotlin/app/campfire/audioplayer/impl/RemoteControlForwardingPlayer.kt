package app.campfire.audioplayer.impl

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import app.campfire.settings.api.PlaybackSettings

/**
 * A [ForwardingPlayer] that intercepts next/previous media item commands from remote controllers
 * and redirects them based on the [PlaybackSettings.remoteNextPrevSkipsChapters] setting.
 *
 * When [remoteNextPrevSkipsChapters] is true (default), remote next/prev commands skip chapters.
 * When false, they seek forward/backward by the configured time instead.
 *
 * Commands from the local in-app UI always use the default chapter-skipping behavior,
 * regardless of the setting. This is determined by checking [MediaSession.controllerForCurrentRequest]
 * to identify whether the command originated from a remote controller (Bluetooth, car stereo,
 * media notification) or the local app UI.
 */
@OptIn(UnstableApi::class)
class RemoteControlForwardingPlayer(
  player: Player,
  private val settings: PlaybackSettings,
  private val appPackageName: String,
) : ForwardingPlayer(player) {

  /**
   * Reference to the MediaSession, set after session creation.
   * Used to identify the source of commands via [MediaSession.controllerForCurrentRequest].
   */
  var session: MediaSession? = null

  /**
   * Determines if the current command is from a remote controller.
   *
   * A command is considered "remote" if:
   * - It comes from a different package (Bluetooth, car stereo, etc.)
   * - It comes from the system media notification controller
   *
   * Commands from the app's own UI (same package, not notification) are considered "local".
   */
  private fun isRemoteController(): Boolean {
    val currentSession = session ?: return false
    val controller = currentSession.controllerForCurrentRequest ?: return false

    // If it's from a different package, it's definitely remote
    if (controller.packageName != appPackageName) {
      return true
    }

    // If it's the media notification controller (same package but system-managed), treat as remote
    if (currentSession.isMediaNotificationController(controller)) {
      return true
    }

    // Same package and not notification = local app UI
    return false
  }

  /**
   * Applies the setting-based behavior for remote controllers.
   * Returns true if we handled the command (seek forward/back), false if default behavior should be used.
   */
  private inline fun handleRemoteNextPrevCommand(seekAction: () -> Unit): Boolean {
    if (isRemoteController() && !settings.remoteNextPrevSkipsChapters) {
      seekAction()
      return true
    }
    return false
  }

  override fun seekToNextMediaItem() {
    if (!handleRemoteNextPrevCommand { seekForward() }) {
      super.seekToNextMediaItem()
    }
  }

  override fun seekToPreviousMediaItem() {
    if (!handleRemoteNextPrevCommand { seekBack() }) {
      super.seekToPreviousMediaItem()
    }
  }

  override fun seekToNext() {
    if (!handleRemoteNextPrevCommand { seekForward() }) {
      super.seekToNext()
    }
  }

  override fun seekToPrevious() {
    if (!handleRemoteNextPrevCommand { seekBack() }) {
      super.seekToPrevious()
    }
  }

  /**
   * Always report seek to next/previous commands as available.
   *
   * This is necessary because when the setting redirects next/prev to seek forward/backward,
   * we want Bluetooth/remote commands to work even at playlist boundaries (first/last chapter).
   * Without this, the Android system drops remote commands before they reach the player
   * when Media3 reports the command as unavailable.
   *
   * See: https://github.com/androidx/media/issues/249
   */
  override fun getAvailableCommands(): Player.Commands {
    return super.getAvailableCommands().buildUpon()
      .add(Player.COMMAND_SEEK_TO_NEXT)
      .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
      .add(Player.COMMAND_SEEK_TO_PREVIOUS)
      .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
      .build()
  }
}
