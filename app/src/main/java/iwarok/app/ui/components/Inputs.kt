package iwarok.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary

/** Dark, rounded text field used by the login / search screens. */
@Composable
fun FlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        label = label?.let { text -> { Text(text) } },
        placeholder = placeholder?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FlowTextPrimary,
            unfocusedTextColor = FlowTextPrimary,
            disabledTextColor = FlowTextTertiary,
            focusedContainerColor = Color.White.copy(alpha = 0.04f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContainerColor = Color.White.copy(alpha = 0.02f),
            cursorColor = FlowPink,
            focusedBorderColor = FlowPink,
            unfocusedBorderColor = FlowOutline,
            disabledBorderColor = FlowOutline.copy(alpha = 0.5f),
            focusedLabelColor = FlowPink,
            unfocusedLabelColor = FlowTextTertiary,
            disabledLabelColor = FlowTextTertiary,
            focusedPlaceholderColor = FlowTextTertiary,
            unfocusedPlaceholderColor = FlowTextTertiary,
            focusedLeadingIconColor = FlowTextSecondary,
            unfocusedLeadingIconColor = FlowTextTertiary,
            focusedTrailingIconColor = FlowTextSecondary,
            unfocusedTrailingIconColor = FlowTextTertiary,
        ),
    )
}
