package dev.byteghst.permlens

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

// Same-package function: it wins over the star import of androidx.compose.material3.*, so every
// Text("...") call in this package goes through here. Digits are drawn in Space Grotesk
// (HeadingFont); the rest of the text keeps whatever font the style says (Montserrat).
// Files with an explicit `import androidx.compose.material3.Text` bypass this wrapper.

private val NumberRegex = Regex("\\d+(?:[.,:]\\d+)*")

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    val styled = remember(text) {
        if (!NumberRegex.containsMatchIn(text)) {
            null
        } else {
            buildAnnotatedString {
                var last = 0
                for (m in NumberRegex.findAll(text)) {
                    append(text.substring(last, m.range.first))
                    withStyle(SpanStyle(fontFamily = HeadingFont)) { append(m.value) }
                    last = m.range.last + 1
                }
                append(text.substring(last))
            }
        }
    }
    if (styled == null) {
        androidx.compose.material3.Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
            style = style
        )
    } else {
        androidx.compose.material3.Text(
            text = styled,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            overflow = overflow,
            maxLines = maxLines,
            style = style
        )
    }
}
