package iwarok.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.AuthState
import iwarok.app.ui.components.BrandWordmark
import iwarok.app.ui.components.FlowPrimaryButton
import iwarok.app.ui.components.FlowSecondaryButton
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowError
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import kotlinx.coroutines.launch

/**
 * Editorial Obsidian × Champagne login: big serif word mark, small-caps
 * "WELCOME BACK", thin-line fields and a light champagne primary button.
 * Guest escape hatch + collapsed token entry are preserved.
 */
@Composable
fun LoginScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onGuestContinue: () -> Unit,
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val authState by graph.repository.authState.collectAsStateWithLifecycle()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var tokenSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var token by rememberSaveable { mutableStateOf("") }
    var isTokenLoggingIn by remember { mutableStateOf(false) }
    var tokenErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) onLoggedIn()
    }

    fun submitEmailLogin() {
        if (isLoggingIn) return
        focusManager.clearFocus()
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isBlank() -> {
                errorMessage = context.getString(R.string.login_error_email_empty)
            }

            password.isBlank() -> {
                errorMessage = context.getString(R.string.login_error_password_empty)
            }

            else -> {
                errorMessage = null
                isLoggingIn = true
                scope.launch {
                    graph.repository.login(trimmedEmail, password)
                        .onSuccess { onLoggedIn() }
                        .onFailure { errorMessage = iwarok.app.ui.util.localizedErrorMessage(context, it.message ?: context.getString(R.string.login_failed)) }
                    isLoggingIn = false
                }
            }
        }
    }

    fun submitTokenLogin() {
        if (isTokenLoggingIn) return
        focusManager.clearFocus()
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank()) {
            tokenErrorMessage = context.getString(R.string.login_error_token_empty)
            return
        }
        tokenErrorMessage = null
        isTokenLoggingIn = true
        scope.launch {
            graph.repository.loginWithToken(trimmedToken)
                .onSuccess { onLoggedIn() }
                .onFailure { tokenErrorMessage = iwarok.app.ui.util.localizedErrorMessage(context, it.message ?: context.getString(R.string.login_failed)) }
            isTokenLoggingIn = false
        }
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (tokenSectionExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground),
    ) {
        // Very light champagne halo behind the word mark (no large bright area).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(FlowPink.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(540f, 40f),
                        radius = 940f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = FlowTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandWordmark(fontSize = 44.sp)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = stringResource(R.string.login_welcome_back),
                    color = FlowTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF121214))
                        .border(1.dp, FlowOutline, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                ) {
                    EditorialField(
                        label = stringResource(R.string.login_email_label),
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(24.dp))
                    EditorialField(
                        label = stringResource(R.string.login_password_label),
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitEmailLogin() }),
                        trailing = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        stringResource(R.string.login_hide_password)
                                    } else {
                                        stringResource(R.string.login_show_password)
                                    },
                                    tint = FlowTextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = FlowError,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                FlowPrimaryButton(
                    text = stringResource(R.string.login_button),
                    onClick = { submitEmailLogin() },
                    loading = isLoggingIn,
                    modifier = Modifier.height(54.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.login_guest_continue),
                    color = FlowTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onGuestContinue)
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { tokenSectionExpanded = !tokenSectionExpanded }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.login_token_entry),
                        color = FlowTextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = FlowTextTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(chevronRotation),
                    )
                }
                AnimatedVisibility(
                    visible = tokenSectionExpanded,
                    enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        EditorialField(
                            label = stringResource(R.string.login_token_label),
                            value = token,
                            onValueChange = {
                                token = it
                                tokenErrorMessage = null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitTokenLogin() }),
                        )
                        AnimatedVisibility(visible = tokenErrorMessage != null) {
                            Text(
                                text = tokenErrorMessage.orEmpty(),
                                color = FlowError,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        FlowSecondaryButton(
                            text = stringResource(R.string.login_token_button),
                            onClick = { submitTokenLogin() },
                            enabled = !isTokenLoggingIn,
                            icon = {
                                if (isTokenLoggingIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        color = FlowPink,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * Thin-line editorial text field: small label above, 1dp underline that turns
 * champagne when focused.
 */
@Composable
private fun EditorialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (focused) FlowPink else FlowTextTertiary,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = FlowTextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(FlowPink),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
            )
            if (trailing != null) {
                Spacer(Modifier.width(2.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (focused) FlowPink else FlowOutline),
        )
    }
}
