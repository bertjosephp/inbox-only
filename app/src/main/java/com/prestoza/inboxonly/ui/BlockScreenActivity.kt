// BlockScreenActivity: the calm "this is blocked" page shown over a blocked feed, instead of yanking the
// user straight out. It's our own screen, opened with an intent: nothing in the blocked app is touched.
// The main button takes the way out the rules prefer (DMs, Messenger, back, or closing the app).
//
// Entrance: fade + zoom, passed by the service when it opens the page (see blockScreenAnimation there).
// It runs in its own task, hidden from Recents, so finishing it reveals the app underneath rather than
// Inbox Only's settings. Back (button or gesture) never returns to the blocked feed: it closes the app.
package com.prestoza.inboxonly.ui

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.prestoza.inboxonly.R
import com.prestoza.inboxonly.appContainer
import com.prestoza.inboxonly.domain.BlockAction
import com.prestoza.inboxonly.domain.BlockScreen
import com.prestoza.inboxonly.domain.BlockScreenFormatter
import com.prestoza.inboxonly.domain.Decision
import com.prestoza.inboxonly.ui.theme.InboxOnlyTheme
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Shown by the accessibility service when a surface is blocked. Not exported: only we can open it. */
class BlockScreenActivity : ComponentActivity() {
    private var request by mutableStateOf<Request?>(null)

    private data class Request(
        val packageName: String,
        val surfaceId: String,
        val screen: BlockScreen,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = readRequest(intent) ?: return finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Fade + shrink on the way out (the "Go back" path, where nothing else opens).
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.block_screen_exit)
        }
        onBackPressedDispatcher.addCallback(this) { closeBlockedApp() }
        setContent {
            InboxOnlyTheme {
                request?.let { BlockScreenContent(it.screen, onPrimary = ::onPrimary, onClose = ::closeBlockedApp) }
            }
        }
    }

    // Single-task: a second block while this is open reuses the screen with the new content.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readRequest(intent)?.let { request = it }
    }

    private fun onPrimary() {
        val current = request ?: return
        when (val action = current.screen.primaryAction) {
            is BlockAction.Redirect -> {
                val intent = Intent(Intent.ACTION_VIEW, action.uri.toUri())
                action.packageName?.let(intent::setPackage)
                leaveTo(intent)
            }

            is BlockAction.Launch -> {
                leaveTo(packageManager.getLaunchIntentForPackage(action.packageName))
            }

            BlockAction.Back -> {
                // Only the accessibility service can press Back in another app; it waits for us to close.
                if (appContainer.blockScreenBridge.requestBack(current.packageName, current.surfaceId)) {
                    finishAndRemoveTask()
                } else {
                    closeBlockedApp()
                }
            }

            BlockAction.Home -> {
                closeBlockedApp()
            }
        }
    }

    // Like pressing Home: the blocked app goes to the background.
    private fun closeBlockedApp() {
        leaveTo(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    // Opens [target] (falling back to the home screen if it can't), then removes this screen.
    private fun leaveTo(target: Intent?) {
        val opened = target != null && start(target)
        if (!opened) start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        finishAndRemoveTask()
    }

    // Whatever opens next (DMs, Messenger, the home screen) fades in while this page fades out.
    private fun start(intent: Intent): Boolean =
        try {
            val crossFade =
                ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, R.anim.block_screen_exit)
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), crossFade.toBundle())
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    private fun readRequest(intent: Intent): Request? {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return null
        val surfaceId = intent.getStringExtra(EXTRA_SURFACE_ID) ?: return null
        val actions =
            intent.getStringExtra(EXTRA_ACTIONS)?.let { json.decodeFromString(actionsSerializer, it) } ?: return null
        val screen = BlockScreenFormatter.forBlock(appId, surfaceId, actions) ?: return null
        return Request(packageName, surfaceId, screen)
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_SURFACE_ID = "surface_id"
        private const val EXTRA_ACTIONS = "actions"
        private val json = Json
        private val actionsSerializer = ListSerializer(BlockAction.serializer())

        /** The intent the service starts for [decision] in [packageName] (rules app [appId]). */
        fun intent(
            context: Context,
            packageName: String,
            appId: String,
            decision: Decision.Block,
        ): Intent =
            Intent(context, BlockScreenActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_APP_ID, appId)
                .putExtra(EXTRA_SURFACE_ID, decision.surfaceId)
                .putExtra(EXTRA_ACTIONS, json.encodeToString(actionsSerializer, decision.actions))
    }
}

/** The page itself: fades in, then the content settles upward with a soft spring. */
@Composable
private fun BlockScreenContent(
    screen: BlockScreen,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    // Starts hidden and targets visible, so the entrance plays once when the screen first appears.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.safeDrawingPadding().padding(24.dp)) {
            AnimatedVisibility(
                visibleState = visible,
                enter =
                    fadeIn(tween(durationMillis = 280)) +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), initialScale = 0.94f) +
                        slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 10 },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 420.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(80.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_tile_focus),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        screen.appLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(screen.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "Focus is on. Your chats and DMs still work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(screen.primaryLabel) }
                    if (screen.showClose) {
                        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(screen.closeLabel) }
                    }
                }
            }
        }
    }
}
