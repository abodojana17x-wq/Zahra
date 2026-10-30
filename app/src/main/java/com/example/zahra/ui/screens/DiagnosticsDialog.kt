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
import com.example.zahra.diagnostics.DiagnosticsReport

/**
 * نافذة «تشخيص» — تُعرض نتائج فحوصات حقيقية أُجريت على الجهاز الآن.
 * لا رقم هنا مكتوب يدويًا؛ كل قيمة تأتي من DiagnosticsReport.
 */
@Composable
fun DiagnosticsDialog(
  report: DiagnosticsReport?,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تشخيص زهرة", fontWeight = FontWeight.Bold) },
    text = {
      if (report == null) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          CircularProgressIndicator()
          Text("جاري تشغيل الفحوصات على الجهاز…", fontSize = 13.sp)
        }
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
        ) {
          Text("الإصدار: ${report.appVersion}", fontSize = 12.sp)
          Text("وقت التشغيل: ${report.ranAt}", fontSize = 12.sp)
          Text("الشبكة: ${report.networkMode}", fontSize = 12.sp)
          Text(
            "النتيجة: ${report.passedCount}/${report.totalCount} فحصًا ناجحًا",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (report.allPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
          )
          Text("آخر قياس في CI: ${report.embeddedEval}", fontSize = 12.sp)
          Text("حجم قواعد المعرفة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          for ((name, count) in report.assetCounts) {
            Text("• $name: $count وحدة", fontSize = 12.sp)
          }
          Text("الفحوصات:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          for (check in report.checks) {
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
              Text(
                text = if (check.passed) "✅" else "❌",
                fontSize = 12.sp
              )
              Text(
                text = " ${check.labelAr} — ${check.detail}",
                fontSize = 12.sp,
                color = if (check.passed) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error
              )
            }
          }
          Text(
            "لماذا هذه الأرقام؟ كل فحص أعلاه نُفِّذ الآن على الجهاز بلا شبكة. " +
              "لم يُشغَّل القياس الكامل (223 حالة × 8 تكرارات) في هذا البناء، " +
              "ولا توجد نتائج منشورة؛ راجع «الاستخدام والحدود» وdocs/RESULTS.md.",
            fontSize = 11.sp
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("إغلاق") }
    }
  )
}
