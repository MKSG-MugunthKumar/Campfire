package app.campfire.audioplayer.impl

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import app.campfire.settings.api.PlaybackSettings

/**
 * A [ForwardingPlayer] that intercepts next/previous media item commands from external controllers
 * (e.g., car stereos with their own package) and redirects them based on the
 * [PlaybackSettings.remoteNextPrevSkipsChapters] setting.
 *
 * When [remoteNextPrevSkipsChapters] is true (default), external next/prev commands skip chapters.
 * When false, they seek forward/backward by the configured time instead.
 *
 * Note: Bluetooth is handled separately via onMediaButtonEvent in MediaSessionCallback since Media3
 * routes Bluetooth key events through the app's own package. Media notification and Android Auto
 * always use chapter skip because they have dedicated custom seek buttons in their layout.
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
   * A command is considered "remote" if it comes from a different package (e.g., car stereo).
   *
   * Note: Bluetooth controllers are handled separately via onMediaButtonEvent in
   * MediaSessionCallback, since Media3 routes their events through the app's own package.
   * Media notification and Android Auto are NOT remote because they have custom seek buttons.
   */
  private fun isRemoteController(): Boolean {
    val currentSession = session ?: return false
    val controller = currentSession.controllerForCurrentRequest ?: return false

    // Notification and Android Auto have custom seek buttons - skip should always be chapter skip
    if (currentSession.isMediaNotificationController(controller) ||
      currentSession.isAutoCompanionController(controller)
    ) {
      return false
    }

    // External controllers from different packages are remote
    if (controller.packageName != appPackageName) {
      return true
    }

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
