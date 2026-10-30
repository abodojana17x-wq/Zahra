package com.example.zahra.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zahra.diagnostics.KnownLimitation
import com.example.zahra.diagnostics.ReleaseInfo

/**
 * صفحة «الاستخدام والحدود» — تعرض القيود الحقيقية من release_info.json.
 *
 * لا تُخفي القيود عند الفشل: إذا تعذّر تحميل الأصل، تظهر رسالة خطأ صريحة
 * بدل قائمة فارغة تطمئن المستخدم زورًا.
 */
@Composable
fun ReleaseInfoDialog(
  info: ReleaseInfo?,
  loadError: String?,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("الاستخدام والحدود", fontWeight = FontWeight.Bold) },
    text = {
      when {
        info == null && loadError == null -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            CircularProgressIndicator()
            Text("جارٍ قراءة معلومات الإصدار من الجهاز…", fontSize = 13.sp)
          }
        }
        info == null -> {
          Text(
            "تعذّر تحميل معلومات الإصدار: ${loadError ?: "ملف release_info.json غير موجود"}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error
          )
        }
        else -> {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 470.dp)
              .verticalScroll(rememberScrollState())
          ) {
            Text("${info.app} ${info.versionName} (${info.versionCode})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("القناة: ${info.channelAr}", fontSize = 12.sp)
            Text("القياس: ${info.measurementAr}", fontSize = 12.sp)
            Text("آخر تحديث للقائمة: ${info.updatedAt}", fontSize = 11.sp)
            Text(
              "هذه القائمة جزء من الإصدار وليست ملحقًا اختياريًا. تعرض ما هو معروف عن الحدود، ولا تعني أن التطبيق آمن أو دقيق. القيود الحاسمة: ${info.criticalCount}.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for ((category, items) in info.groupedByCategory()) {
              Text(
                category,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
              )
              for (item in items) {
                LimitationRow(item)
              }
            }
            Text(
              "المصدر: release/limitations.json · التفاصيل الكاملة في docs/LIMITATIONS.md داخل المستودع.",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 8.dp)
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("إغلاق") }
    }
  )
}

@Composable
private fun LimitationRow(item: KnownLimitation) {
  val color = when (item.severity) {
    "critical" -> MaterialTheme.colorScheme.error
    "major" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Column(modifier = Modifier.padding(bottom = 4.dp)) {
    Row {
      Text("• ", fontSize = 12.sp, color = color)
      Text(
        "${item.id} — ${item.titleAr} (${item.severityAr})",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color
      )
    }
    Text(
      item.detailAr,
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}
