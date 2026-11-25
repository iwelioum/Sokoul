package com.sokoul.pc.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.pc.data.L
import com.sokoul.pc.startup.onboarding.LocalMonochrome
import com.sokoul.pc.startup.onboarding.Spinner
import com.sokoul.pc.startup.onboarding.TouchField
import com.sokoul.pc.theme.Inter
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.ui.Form
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.QrCode
import com.sokoul.pc.ui.moveWithArrow
import com.sokoul.pc.ui.softShadow
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.delay

/** DESIGN.md §4 on the 1920 frame: body 29, secondary 25, titles 48; the code is the one large figure of the page. */
private object AccType {
    val Page = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-0.96).sp)
    val Title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.48).sp)
    val Strong = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 31.sp, lineHeight = 40.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 29.sp, lineHeight = 40.sp)
    val Secondary = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 32.sp)
    val Code = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 112.sp, lineHeight = 120.sp, letterSpacing = 6.sp)
}

private val Plaque = Color(0xFF1C1C1E)
private val Pill = Color(0xFF2C2C2E)
private val FocusFill = Color(0xFFF5F5F7)
private val Ink = Color(0xFF0B0B0C)
private val Secondary = Color(0x99FFFFFF)

object AccountCopy {
    val title = L("Sokoul account", "Compte Sokoul")
    val notConnected = L("Not connected", "Non connecté")
    val serverWhat = L(
        "The Sokoul server is a small program on your home PC that keeps your account, your profiles and where you stopped, so every TV, tablet and PC shares them.",
        "Le serveur Sokoul est un petit programme sur le PC de la maison qui garde votre compte, vos profils et où vous en êtes, pour que chaque TV, tablette et PC les partagent.",
    )
}

/** "Link this TV" on a TV, "this tablet" on a tablet, "this PC" on the PC. */
private fun linkThis(kind: DeviceKind): String = when (kind) {
    DeviceKind.Pc -> L("Link this PC", "Relier ce PC").text()
    DeviceKind.Tablet -> L("Link this tablet", "Relier cette tablette").text()
    DeviceKind.Tv -> L("Link this TV", "Relier cette TV").text()
}

/**
 * Settings › Sokoul account (openspec sokoul-server 5.4). Signed out, a TV shows a code and its QR to confirm from a
 * phone (never a keyboard on the remote); a PC or a tablet may also sign in with a name and a passphrase. Signed in, the
 * account's name and role and every device of the account, each one signed out with a second press. Without a server
 * address, one sentence says what the server is and the address is asked once.
 */
@Composable
fun AccountScreen(
    model: AccountPanelModel,
    onBack: () -> Unit,
    /** A round back button for touch and mouse; the remote's Back key needs none. */
    backButton: Boolean = !com.sokoul.pc.Platform.IS_TV || Form.tablet,
    /** Renders only: the state is set by the caller, nothing is asked of the server at opening. */
    openOnStart: Boolean = true,
) {
    LaunchedEffect(Unit) { if (openOnStart) model.open() }
    DisposableEffect(Unit) { onDispose { model.close() } }
    val narrow = Form.tabletPortrait || Form.isPhone
    val view = model.view

    fun back() {
        when (view) {
            is AccountView.SignIn, is AccountView.Linking -> model.backToSignedOut()
            is AccountView.NeedsServer -> if (model.server != null) model.backToSignedOut() else onBack()
            else -> onBack()
        }
    }

    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    // The page's main action takes the focus on a remote or keyboard; a touch tablet starts with nothing lit.
    LaunchedEffect(view::class) {
        if (Form.tablet) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("account-page")
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Escape || e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyDown) back()
                    true
                } else false
            },
    ) {
        val margin = if (narrow) 56.dp else 80.dp
        CompositionLocalProvider(LocalMonochrome provides true) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                    .onKeyEvent { focus.moveWithArrow(it) }
                    .padding(start = margin, end = margin, top = if (narrow) 56.dp else 64.dp, bottom = 80.dp),
            ) {
                if (backButton) {
                    RoundBack(::back)
                    Spacer(Modifier.height(24.dp))
                }
                Label(AccountCopy.title.text(), AccType.Page, Color.White, maxLines = 1)
                Spacer(Modifier.height(16.dp))
                Label(
                    L(
                        "Your profiles and where you stopped follow you on every TV, tablet and PC of the house.",
                        "Vos profils et où vous en êtes vous suivent sur chaque TV, tablette et PC de la maison.",
                    ).text(),
                    AccType.Body, Secondary, Modifier.widthIn(max = 1500.dp),
                )
                Spacer(Modifier.height(if (narrow) 48.dp else 56.dp))
                when (view) {
                    AccountView.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spinner(Secondary, Modifier.size(32.dp))
                        Label(L("Reading your account…", "Lecture de votre compte…").text(), AccType.Body, Secondary)
                    }
                    is AccountView.NeedsServer -> ServerSection(view, model, first)
                    is AccountView.SignedOut -> SignedOutSection(view, model, first)
                    is AccountView.Linking -> LinkingSection(view, model, narrow, first)
                    is AccountView.SignIn -> SignInSection(view, model, first)
                    is AccountView.SignedIn -> SignedInSection(view, model, narrow, first)
                }
            }
        }
    }
}

@Composable
private fun ServerSection(view: AccountView.NeedsServer, model: AccountPanelModel, first: FocusRequester) {
    var address by remember(view.address) { mutableStateOf(view.address) }
    Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Label(L("Connect the Sokoul server", "Connecter le serveur Sokoul").text(), AccType.Title, Color.White, maxLines = 2)
        Label(AccountCopy.serverWhat.text(), AccType.Body, Secondary)
        TouchField(
            L("Server address", "Adresse du serveur").text(), address, { address = it },
            hint = "192.168.1.20", keyboard = KeyboardType.Uri, imeAction = ImeAction.Done,
            onDone = { model.saveServer(address) }, error = view.problem != null,
        )
        Label(
            L(
                "On the PC that runs it, the address is 127.0.0.1:25570. Elsewhere, use that PC's address on your Wi-Fi, shown in the server's window.",
                "Sur le PC qui le fait tourner, l'adresse est 127.0.0.1:25570. Ailleurs, utilisez l'adresse de ce PC sur votre Wi-Fi, affichée dans la fenêtre du serveur.",
            ).text(),
            AccType.Secondary, Secondary,
        )
        view.problem?.let { Problem(it.text()) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Capsule(L("Check and save", "Vérifier et enregistrer").text(), primary = true, tag = "account-server-save", requester = first) { model.saveServer(address) }
            if (view.checking) Spinner(Secondary, Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SignedOutSection(view: AccountView.SignedOut, model: AccountPanelModel, first: FocusRequester) {
    Column(Modifier.widthIn(max = 1200.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Label(AccountCopy.notConnected.text(), AccType.Title, Color.White, maxLines = 1)
        Label(
            if (model.kind != DeviceKind.Tv) L(
                "Link this device with your phone, or sign in with your name and passphrase.",
                "Reliez cet appareil avec votre téléphone, ou connectez-vous avec votre nom et votre phrase secrète.",
            ).text() else L(
                "Link this TV with your phone: a code and a QR code, nothing to type on the remote.",
                "Reliez cette TV avec votre téléphone : un code et un code QR, rien à saisir avec la télécommande.",
            ).text(),
            AccType.Body, Secondary,
        )
        view.problem?.let { Problem(it.text()) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Capsule(linkThis(model.kind), primary = true, tag = "account-link", requester = first) { model.startLinking() }
            if (model.keyboard) Capsule(L("Sign in", "Se connecter").text(), primary = false, tag = "account-sign-in") { model.showSignIn() }
        }
        Spacer(Modifier.height(24.dp))
        ServerLine(model)
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Capsule(L("Change server", "Changer de serveur").text(), primary = false, tag = "account-change-server") { model.changeServer() }
            if (model.serverPc && (model.view as? AccountView.SignedOut)?.needsOwner != false) Capsule(L("Create the owner account", "Créer le compte propriétaire").text(), primary = false, tag = "account-create-owner") { model.showSignIn(owner = true) }
        }
    }
}

@Composable
private fun ServerLine(model: AccountPanelModel) {
    model.server?.let { url ->
        Label(L("Server: ${shortServer(url)}", "Serveur : ${shortServer(url)}").text(), AccType.Secondary, Secondary, maxLines = 1)
    }
}

/** "http://192.168.0.10:25570" → "192.168.0.10:25570". */
internal fun shortServer(url: String): String = url.substringAfter("://").trimEnd('/')

@Composable
private fun LinkingSection(view: AccountView.Linking, model: AccountPanelModel, narrow: Boolean, first: FocusRequester) {
    val code = view.code
    var now by remember { mutableLongStateOf(model.clock()) }
    LaunchedEffect(code) {
        while (true) {
            now = model.clock()
            delay(1_000)
        }
    }
    val steps: @Composable ColumnScope.() -> Unit = {
        Label(linkThis(model.kind), AccType.Title, Color.White, maxLines = 1)
        Spacer(Modifier.height(24.dp))
        val link = code?.verifyUrl?.substringBefore('?')?.let(::shortServer) ?: (model.server?.let { shortServer(it) + "/link" } ?: "")
        Step(1, L("On your phone, scan the QR code, or open $link", "Sur votre téléphone, scannez le code QR, ou ouvrez $link").text())
        Spacer(Modifier.height(14.dp))
        Step(2, L("Sign in to your Sokoul account, then check this code:", "Connectez-vous à votre compte Sokoul, puis vérifiez ce code :").text())
        Spacer(Modifier.height(28.dp))
        if (code != null) {
            Label(code.userCode, AccType.Code, Color.White, Modifier.testTag("account-code"), maxLines = 1)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Color.White, Modifier.size(30.dp))
                Label(L("Waiting for your confirmation…", "En attente de votre confirmation…").text(), AccType.Strong, Color.White, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            val left = ((code.expiresAt - now) / 1000).coerceAtLeast(0)
            val time = "${left / 60}:${(left % 60).toString().padStart(2, '0')}"
            Label(
                L("Code valid for ${time}. A new one comes by itself.", "Code valable ${time}. Un nouveau s'affiche tout seul.").text(),
                AccType.Secondary, Secondary, maxLines = 2,
            )
        } else if (view.problem == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Secondary, Modifier.size(30.dp))
                Label(L("Asking the server for a code…", "Demande d'un code au serveur…").text(), AccType.Body, Secondary, maxLines = 1)
            }
        }
        view.problem?.let {
            Spacer(Modifier.height(20.dp))
            Problem(it.text())
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (model.keyboard) Capsule(L("Sign in with my name", "Me connecter avec mon nom").text(), primary = false, tag = "account-sign-in", requester = first) { model.showSignIn() }
            Capsule(L("Cancel", "Annuler").text(), primary = false, tag = "account-cancel-link", requester = if (model.keyboard) remember { FocusRequester() } else first) { model.backToSignedOut() }
        }
    }
    val qr: @Composable () -> Unit = {
        if (code != null) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            QrCode(code.verifyUrl, L("Scan with your phone", "Scannez avec votre téléphone").text(), size = if (narrow) 400.dp else 380.dp, modifier = Modifier.testTag("account-qr"))
            Label(L("Scan with your phone", "Scannez avec votre téléphone").text(), AccType.Strong, Color.White, maxLines = 2)
        }
    }
    if (narrow) {
        Column { steps(); Spacer(Modifier.height(48.dp)); qr(); Spacer(Modifier.height(48.dp)); buttons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.weight(1f)) { steps(); Spacer(Modifier.height(40.dp)); buttons() }
            Box(Modifier.width(380.dp)) { qr() }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Pill), contentAlignment = Alignment.Center) {
            Label("$n", AccType.Secondary.copy(fontWeight = FontWeight.SemiBold), Color.White, maxLines = 1)
        }
        Label(text, AccType.Body, Color.White, Modifier.weight(1f).padding(top = 2.dp))
    }
}

@Composable
private fun SignInSection(view: AccountView.SignIn, model: AccountPanelModel, first: FocusRequester) {
    var name by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    fun submit() { if (!view.busy && name.isNotBlank() && passphrase.isNotEmpty()) model.signIn(name, passphrase) }
    Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Label(
            if (view.owner) L("Create the owner account", "Créer le compte propriétaire").text() else L("Sign in", "Se connecter").text(),
            AccType.Title, Color.White, maxLines = 1,
        )
        if (view.owner) Label(
            L(
                "The first account of the server. Choose a passphrase of at least 12 characters: a few words you will remember.",
                "Le premier compte du serveur. Choisissez une phrase secrète d'au moins 12 caractères : quelques mots dont vous vous souviendrez.",
            ).text(),
            AccType.Body, Secondary,
        )
        TouchField(L("Name", "Nom").text(), name, { name = it }, keyboard = KeyboardType.Text, error = view.problem != null)
        TouchField(
            L("Passphrase", "Phrase secrète").text(), passphrase, { passphrase = it }, secret = true,
            keyboard = KeyboardType.Password, imeAction = ImeAction.Done, onDone = ::submit, error = view.problem != null,
        )
        view.problem?.let { Problem(it.text()) }
        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Capsule(
                if (view.owner) L("Create", "Créer").text() else L("Sign in", "Se connecter").text(),
                primary = true, tag = "account-sign-in-submit", requester = first, onClick = ::submit,
            )
            Capsule(L("Cancel", "Annuler").text(), primary = false, tag = "account-sign-in-cancel") { model.backToSignedOut() }
            if (view.busy) Spinner(Secondary, Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SignedInSection(view: AccountView.SignedIn, model: AccountPanelModel, narrow: Boolean, first: FocusRequester) {
    val who: @Composable ColumnScope.() -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(Pill), contentAlignment = Alignment.Center) {
                Label(view.user.name.take(1).uppercase(), AccType.Title, Color.White, maxLines = 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(view.user.name, AccType.Title, Color.White, maxLines = 1)
                Label(roleText(view.user), AccType.Secondary, Secondary, maxLines = 1)
            }
        }
        if (view.justConnected) {
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("account-connected")) {
                Icon(Icons.Rounded.Check, null, Modifier.size(34.dp), tint = Color.White)
                Label(L("Connected as ${view.user.name}", "Connecté en tant que ${view.user.name}").text(), AccType.Strong, Color.White, maxLines = 1)
            }
        }
        Spacer(Modifier.height(32.dp))
        val here = view.devices.firstOrNull { it.current }?.deviceName ?: model.deviceName
        Label(L("This device: $here", "Cet appareil : $here").text(), AccType.Secondary, Secondary, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        ServerLine(model)
        view.problem?.let {
            Spacer(Modifier.height(20.dp))
            Problem(it.text())
        }
        Spacer(Modifier.height(40.dp))
        ConfirmCapsule(
            L("Sign out of this device", "Déconnecter cet appareil").text(),
            L("Press again to sign out", "Appuyez à nouveau pour déconnecter").text(),
            "account-sign-out", first,
        ) { model.signOut() }
    }
    val devices: @Composable ColumnScope.() -> Unit = {
        Label(L("Devices", "Appareils").text(), AccType.Title, Color.White, maxLines = 1)
        Spacer(Modifier.height(20.dp))
        val now = model.clock()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            view.devices.forEach { d -> DeviceRow(d, now) { model.revoke(d.id) } }
        }
        if (view.devices.size > 1) {
            Spacer(Modifier.height(16.dp))
            Label(
                L("Press a device twice to sign it out. It will need to be linked again.", "Appuyez deux fois sur un appareil pour le déconnecter. Il faudra le relier à nouveau.").text(),
                AccType.Secondary, Secondary,
            )
        }
    }
    if (narrow) {
        Column { who(); Spacer(Modifier.height(56.dp)); devices() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
            Column(Modifier.weight(0.9f)) { who() }
            Column(Modifier.weight(1.1f)) { devices() }
        }
    }
}

private fun roleText(user: AccountUser): String =
    if (user.owner) L("Owner of the server", "Propriétaire du serveur").text() else L("Family member", "Membre de la famille").text()

/** "Used today", "Used yesterday", "Used 3 days ago". */
internal fun lastUsedText(lastUsedAt: Long, now: Long): String {
    val days = ((now - lastUsedAt).coerceAtLeast(0) / 86_400_000L).toInt()
    return when (days) {
        0 -> L("Used today", "Utilisé aujourd'hui")
        1 -> L("Used yesterday", "Utilisé hier")
        else -> L("Used $days days ago", "Utilisé il y a $days jours")
    }.text()
}

/** A device of the account. This device only informs; another one signs out with a second press, as maintenance rows do. */
@Composable
private fun DeviceRow(device: DeviceSession, now: Long, onRevoke: () -> Unit) {
    var armed by remember(device.id) { mutableIntStateOf(0) }
    LaunchedEffect(armed) {
        if (armed == 1) {
            delay(4_000)
            armed = 0
        }
    }
    var focused by remember { mutableStateOf(false) }
    val lit = focused && !device.current
    val fill by animateColorAsState(if (lit) FocusFill else Plaque, tween(140), label = "device-fill")
    val scale by animateFloatAsState(if (lit) 1.02f else 1f, tween(140), label = "device-scale")
    val ink = if (lit) Ink else Color.White
    val soft = if (lit) Ink.copy(alpha = 0.72f) else Secondary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp)).background(fill)
            .testTag("account-device-${device.id}")
            .then(
                if (device.current) Modifier
                else Modifier.tvFocusable(onFocusChanged = { focused = it }, onClick = {
                    if (armed == 0) armed = 1 else { armed = 0; onRevoke() }
                }),
            )
            .padding(horizontal = 30.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Label(device.deviceName, AccType.Strong, ink, Modifier.weight(1f), maxLines = 1)
        val value = when {
            device.current -> L("This device", "Cet appareil").text()
            armed == 1 -> L("Press again to sign out", "Appuyez à nouveau pour déconnecter").text()
            else -> lastUsedText(device.lastUsedAt, now)
        }
        Label(value, AccType.Secondary.copy(fontWeight = if (armed == 1) FontWeight.SemiBold else FontWeight.Medium), if (armed == 1) ink else soft, maxLines = 1)
    }
}

@Composable
private fun Problem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.testTag("account-problem")) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(top = 4.dp).size(32.dp), tint = Color.White)
        Label(text, AccType.Body, Color.White)
    }
}

@Composable
private fun RoundBack(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) FocusFill else Pill, tween(140), label = "back-fill")
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(fill).testTag("account-back")
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, L("Back", "Retour").text(), Modifier.size(34.dp), tint = if (focused) Ink else Color.White)
    }
}

/** A capsule button: white with dark ink when it is the main action or focused, graphite otherwise. */
@Composable
private fun Capsule(text: String, primary: Boolean, tag: String, requester: FocusRequester = remember { FocusRequester() }, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused || primary) FocusFill else Pill, tween(140), label = "capsule-fill")
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "capsule-scale")
    Box(
        Modifier.heightIn(min = 72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.softShadow(36.dp, spread = 18.dp, strength = 0.5f) else Modifier)
            .clip(RoundedCornerShape(50)).background(fill).testTag(tag)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester)
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, AccType.Strong, if (focused || primary) Ink else Color.White, maxLines = 1)
    }
}

/** A capsule that asks twice: the first press arms it, the second does it. */
@Composable
private fun ConfirmCapsule(text: String, confirm: String, tag: String, requester: FocusRequester, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    Capsule(if (armed) confirm else text, primary = false, tag = tag, requester = requester) {
        if (armed) { armed = false; onConfirm() } else armed = true
    }
}
