package com.sokoul.pc.startup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.pc.data.L
import com.sokoul.pc.data.Profile
import com.sokoul.pc.data.Profiles
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.moveWithArrow
import com.sokoul.pc.ui.tvFocusable

private val AVATAR = 200.dp

/** The circle of a profile: its initial on its colour, the same everywhere a profile is shown. */
@Composable
fun ProfileAvatar(p: Profile, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    // The kids badge sits over the edge of the circle, so it is drawn outside its clip.
    Box(modifier.size(size)) {
        Box(Modifier.size(size).clip(CircleShape).background(Color(Profiles.COLORS[p.color % Profiles.COLORS.size])), contentAlignment = Alignment.Center) {
            Label(p.name.take(1).uppercase(), Type.RowTitle.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold), Color.White)
        }
        if (p.kids) {
            Box(Modifier.align(Alignment.BottomEnd).size(size * 0.3f).clip(CircleShape).background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChildCare, null, tint = Color.White, modifier = Modifier.size(size * 0.18f))
            }
        }
    }
}

/**
 * "Who is watching?", the profile picker: the profiles in a row, then Add and Edit. Editing turns the row
 * into a form for the chosen one (name, kids, colour, delete).
 */
@Composable
fun ProfilesScreen(profiles: Profiles, onChosen: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    /** A locked profile waiting for its code, and whether it is to be opened (true) or edited. */
    var asking by remember { mutableStateOf<Pair<Profile, Boolean>?>(null) }
    Box(Modifier.fillMaxSize().background(Palette.Background).testTag("profiles"), contentAlignment = Alignment.Center) {
        val shown = form
        val ask = asking
        when {
            ask != null -> PinPad(
                L("${ask.first.name}'s code", "Code de ${ask.first.name}").text(),
                if (ask.second) L("This profile is protected.", "Ce profil est protégé.").text() else L("Enter the code to edit this profile.", "Entrez le code pour modifier ce profil.").text(),
                check = { profiles.pinMatches(ask.first, it) },
                onDone = {
                    asking = null
                    if (ask.second) {
                        profiles.switchTo(ask.first)
                        onChosen()
                    } else {
                        form = ask.first
                    }
                },
                onCancel = { asking = null },
            )
            adding || shown != null -> ProfileForm(
                profile = shown,
                onSave = { name, kids, color, pin ->
                    val saved = if (shown == null) profiles.add(name, kids).also { profiles.update(it.copy(color = color)) } else shown.copy(name = name, kids = kids, color = color).also { profiles.update(it) }
                    if (pin != null) profiles.setPin(saved, pin.ifEmpty { null })
                    form = null
                    adding = false
                },
                onDelete = shown?.takeIf { it.id != Profiles.MAIN }?.let { p -> { profiles.remove(p); form = null } },
                onCancel = { form = null; adding = false },
            )
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(56.dp)) {
                Label(
                    (if (editing) L("Edit profiles", "Modifier les profils") else L("Who is watching?", "Qui regarde ?")).text(),
                    Type.RowTitle.copy(fontSize = 56.sp),
                )
                val first = remember { FocusRequester() }
                LaunchedEffect(editing) {
                    withFrameNanos { }
                    runCatching { first.requestFocus() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    profiles.all.forEachIndexed { i, p ->
                        ProfileTile(p, editing, if (p.id == profiles.current.id || (i == 0 && profiles.all.none { it.id == profiles.current.id })) first else remember { FocusRequester() }) {
                            when {
                                // A protected profile asks for its code before it opens and before it can be changed.
                                p.locked -> asking = p to !editing
                                editing -> form = p
                                else -> {
                                    profiles.switchTo(p)
                                    onChosen()
                                }
                            }
                        }
                    }
                    if (profiles.all.size < MAX_PROFILES) AddTile { adding = true }
                }
                PillButton(
                    (if (editing) L("Done", "Terminé") else L("Edit profiles", "Modifier les profils")).text(),
                    { editing = !editing },
                    Modifier.testTag("profiles-edit"),
                )
            }
        }
    }
}

@Composable
private fun ProfileTile(p: Profile, editing: Boolean, requester: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f)
    Column(
        Modifier.width(AVATAR + 40.dp).testTag("profile-${p.id.ifEmpty { "main" }}")
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            ProfileAvatar(p, AVATAR, Modifier.border(if (focused) 6.dp else 0.dp, Palette.FocusFill, CircleShape))
            if (editing) {
                Box(Modifier.size(AVATAR).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.locked) Icon(Icons.Filled.Lock, L("Protected", "Protégé").text(), tint = if (focused) Palette.OnBackground else Palette.Secondary, modifier = Modifier.size(26.dp).testTag("profile-lock"))
            Label(p.name, Type.Meta.copy(fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center), if (focused) Palette.OnBackground else Palette.Secondary, maxLines = 1)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.RowFill)
    Column(
        Modifier.width(AVATAR + 40.dp).testTag("profile-add").tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = remember { FocusRequester() }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(AVATAR).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = if (focused) Palette.OnFocus else Palette.OnBackground, modifier = Modifier.size(80.dp))
        }
        Spacer(Modifier.height(20.dp))
        Label(L("Add", "Ajouter").text(), Type.Meta, if (focused) Palette.OnBackground else Palette.Secondary)
    }
}

/** Name, kids profile and colour of a new or existing profile. */
@Composable
private fun ProfileForm(profile: Profile?, onSave: (String, Boolean, Int, String?) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    // The code: null keeps it as it is, "" removes it, four digits set it. Chosen twice, so that a slip does not lock.
    var pin by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(0) }
    var firstEntry by remember { mutableStateOf("") }
    val hasPin = pin?.isNotEmpty() ?: (profile?.locked == true)
    if (choosing > 0) {
        PinPad(
            if (choosing == 1) L("Choose a 4-digit code", "Choisissez un code à 4 chiffres").text() else L("Type it again", "Tapez-le une seconde fois").text(),
            L("Asked before this profile opens or is edited.", "Demandé avant d'ouvrir ou de modifier ce profil.").text(),
            check = { choosing == 1 || it == firstEntry },
            onDone = { typed ->
                if (choosing == 1) { firstEntry = typed; choosing = 2 } else { pin = typed; choosing = 0 }
            },
            onCancel = { choosing = 0 },
        )
        return
    }
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var kids by remember { mutableStateOf(profile?.kids ?: false) }
    var color by remember { mutableStateOf(profile?.color ?: 0) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { nameFocus.requestFocus() }
    }
    val focusManager = LocalFocusManager.current
    Column(Modifier.width(820.dp).testTag("profile-form"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        ProfileAvatar(Profile(profile?.id ?: "new", name.ifBlank { "?" }, color, kids), 180.dp)
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier.width(820.dp).height(72.dp).clip(RoundedCornerShape(36.dp)).background(if (focused) Palette.FocusFill else Palette.RowFill).padding(horizontal = 30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (name.isEmpty()) Label(L("Name", "Nom").text(), Type.Meta, if (focused) Palette.OnFocus.copy(alpha = 0.5f) else Palette.Secondary)
            BasicTextField(
                name, { name = it.take(Profiles.MAX_NAME) },
                singleLine = true,
                textStyle = Type.Meta.copy(color = if (focused) Palette.OnFocus else Palette.OnBackground),
                cursorBrush = SolidColor(if (focused) Palette.OnFocus else Palette.OnBackground),
                modifier = Modifier.width(760.dp).focusRequester(nameFocus).testTag("profile-name").onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && (e.key == Key.DirectionDown || e.key == Key.DirectionUp) && focusManager.moveWithArrow(e) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PillButton(
                (if (kids) L("Kids profile: on", "Profil enfant : oui") else L("Kids profile: off", "Profil enfant : non")).text(),
                { kids = !kids }, Modifier.testTag("profile-kids"),
            )
            PillButton(L("Colour", "Couleur").text(), { color = (color + 1) % Profiles.COLORS.size }, Modifier.testTag("profile-color"))
            PillButton(
                (if (hasPin) L("Code: on", "Code PIN : oui") else L("Code: off", "Code PIN : non")).text(),
                { if (hasPin) pin = "" else choosing = 1 }, Modifier.testTag("profile-pin"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PillButton(L("Save", "Enregistrer").text(), { if (name.isNotBlank()) onSave(name, kids, color, pin) }, Modifier.testTag("profile-save"))
            PillButton(L("Cancel", "Annuler").text(), onCancel, Modifier.testTag("profile-cancel"))
            if (onDelete != null) PillButton(L("Delete", "Supprimer").text(), onDelete, Modifier.testTag("profile-delete"))
        }
        if (kids) {
            Label(
                L("A kids profile cannot start titles rated 16 or 18.", "Un profil enfant ne peut pas lancer les titres déconseillés aux moins de 16 ou 18 ans.").text(),
                Type.Body.copy(textAlign = TextAlign.Center), Palette.Secondary,
            )
        }
    }
}

private const val MAX_PROFILES = 6
