// BlockScreenActivity: the calm "this is blocked" page shown over a blocked feed, instead of yanking the
// user straight out. It's our own screen, opened with an intent: nothing in the blocked app is touched.
// The main button takes the way out the rules prefer (DMs, Messenger, back, or closing the app).
//
// Animation: One UI replaces window animations for a new task with its own slide, whatever the theme or
// launch options say. So the page opens with no window animation in a transparent window and draws its
// own fade + zoom. Once it's fully shown, the window turns opaque so the app underneath stops (and its
// video goes quiet); before leaving it turns transparent again and fades out the same way.
//
// It runs in its own task, hidden from Recents, so finishing it reveals the app underneath rather than
// Inbox Only's settings. Back (button or gesture) never returns to the blocked feed: it closes the app.
package com.prestoza.inboxonly.ui

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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

    // Set once a button (or Back) is pressed: the page fades out, then this runs.
    private var pendingExit by mutableStateOf<(() -> Unit)?>(null)

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
            // The page has already faded itself out by the time it finishes.
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        onBackPressedDispatcher.addCallback(this) { leave(::closeBlockedApp) }
        setContent {
            InboxOnlyTheme {
                request?.let { current ->
                    BlockScreenPage(
                        screen = current.screen,
                        exiting = pendingExit != null,
                        onShown = { setOpaque(true) },
                        onHidden = ::finishExit,
                        onPrimary = { leave(::onPrimary) },
                        onClose = { leave(::closeBlockedApp) },
                    )
                }
            }
        }
    }

    // Single-task: a second block while this is open reuses the screen with the new content.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readRequest(intent)?.let { request = it }
    }

    // Starts the fade-out; [then] runs once the page is invisible.
    private fun leave(then: () -> Unit) {
        if (pendingExit != null) return
        setOpaque(false)
        pendingExit = then
    }

    private fun finishExit() {
        pendingExit?.invoke()
        finishAndRemoveTask()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION") // The replacement, overrideActivityTransition, needs Android 14.
            overridePendingTransition(0, 0)
        }
    }

    // Opaque: Android stops the app underneath, so its video stops. Needs Android 11; on Android 10
    // the window just stays transparent behind the opaque page.
    private fun setOpaque(opaque: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setTranslucent(!opaque)
    }

    private fun onPrimary() {
        val current = request ?: return
        when (val action = current.screen.primaryAction) {
            is BlockAction.Redirect -> {
                val intent = Intent(Intent.ACTION_VIEW, action.uri.toUri())
                action.packageName?.let(intent::setPackage)
                open(intent)
            }

            is BlockAction.Launch -> {
                open(packageManager.getLaunchIntentForPackage(action.packageName))
            }

            BlockAction.Back -> {
                // Only the accessibility service can press Back in another app; it waits for us to close.
                if (!appContainer.blockScreenBridge.requestBack(current.packageName, current.surfaceId)) {
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
        start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    // Opens [target], falling back to the home screen if it can't.
    private fun open(target: Intent?) {
        if (target == null || !start(target)) closeBlockedApp()
    }

    private fun start(intent: Intent): Boolean =
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

/**
 * The page itself. The whole page fades in while growing from 92% to full size, and the content then
 * settles upward with a soft spring. [exiting] reverses the fade + zoom; [onHidden] fires at the end.
 */
@Composable
private fun BlockScreenPage(
    screen: BlockScreen,
    exiting: Boolean,
    onShown: () -> Unit,
    onHidden: () -> Unit,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 250, easing = LinearOutSlowInEasing))
        onShown()
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            progress.animateTo(0f, tween(durationMillis = 200, easing = FastOutLinearInEasing))
            onHidden()
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value
            scaleX = 0.92f + 0.08f * progress.value
            scaleY = scaleX
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            BlockScreenContent(screen, onPrimary, onClose)
        }
    }
}

@Composable
private fun BlockScreenContent(
    screen: BlockScreen,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    // Starts hidden and targets visible, so the content's spring plays once when the page first appears.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.safeDrawingPadding().padding(24.dp)) {
        AnimatedVisibility(
            visibleState = visible,
            enter =
                fadeIn(tween(durationMillis = 300)) +
                    slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 12 },
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
