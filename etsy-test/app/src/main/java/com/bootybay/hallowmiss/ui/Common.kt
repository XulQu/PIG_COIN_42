package com.bootybay.hallowmiss.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bone = Color(0xFFE8E2D0)
val Blood = Color(0xFFB3121F)
val Pine = Color(0xFF1E6B3A)
val Dim = Color(0xFF8A8A8A)
val Panel = Color(0xFF0E0E0E)
val Gold = Color(0xFFC9A227)
val Ok = Color(0xFF2ECC71)
val Warn = Color(0xFFE67E22)

/** Pure-black theme: OLED pixels that are off use no power. */
@Composable
fun BootyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        background = Color.Black, surface = Panel, primary = Blood, onPrimary = Color.White,
        secondary = Pine, onSecondary = Color.White, onBackground = Bone, onSurface = Bone,
        surfaceVariant = Color(0xFF161616), onSurfaceVariant = Bone, outline = Color(0xFF3A3A3A),
    ),
    content = content,
)

object Thumbs {
    private val cache = LruCache<String, ImageBitmap>(40)
    fun load(path: String, sample: Int): ImageBitmap? {
        val key = "$path#$sample"
        cache.get(key)?.let { return it }
        val b = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return b.asImageBitmap().also { cache.put(key, it) }
    }
}

@Composable
fun FileImage(path: String, modifier: Modifier, sample: Int = 1) {
    val img by produceState<ImageBitmap?>(null, path, sample) {
        value = withContext(Dispatchers.IO) { Thumbs.load(path, sample) }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text("…", color = Color.Gray)
    }
}

@Composable
fun SectionTitle(text: String) =
    Text(text, color = Gold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 16.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

@Composable
fun Empty(text: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(text, color = Dim)
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, color: Color = Bone) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Panel).padding(12.dp)) {
        Text(label, color = Dim, fontSize = 12.sp)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Bar(label: String, fraction: Float, value: String, color: Color = Blood) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Bone, fontSize = 13.sp, modifier = Modifier.width(130.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1A1A1A))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.01f, 1f)).height(10.dp).background(color))
        }
        Text(value, color = Dim, fontSize = 12.sp, modifier = Modifier.width(48.dp))
    }
}

@Composable
fun PrefField(label: String, initial: String, secret: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = v,
        onValueChange = { v = it; onChange(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { toast(ctx, "No browser found") }
}

fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts == 0L) return "never"
    val m = (now - ts) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 48 * 60 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}

fun date(ts: Long): String = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ts))
fun money(v: Double): String = "$" + "%.2f".format(v)
