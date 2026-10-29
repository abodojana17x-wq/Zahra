/**
 * تحقق مستقل: جدول أم القرى المدمج في `UmmAlQuraHijriCalculator` مقابل مرجع JDK
 * (`java.time.chrono.HijrahDate` نوع umalqura) يومًا بيوم.
 *
 * التشغيل:
 *   ./tools/offline-typecheck/phase02/run.sh            # يبني /tmp/verify-phase02
 *   kotlinc -cp /tmp/verify-phase02 VerifyUmmAlQura.kt -d /tmp/verify-phase02/v.jar
 *   java -cp /tmp/verify-phase02:/tmp/kc/package/lib/kotlin-stdlib.jar VerifyUmmAlQuraKt
 */
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import com.example.zahra.tools.UmmAlQuraHijriCalculator
import com.example.zahra.tools.TabularHijriCalculator
import java.util.TimeZone
fun main() {
  val calc = UmmAlQuraHijriCalculator()
  val zone = TimeZone.getTimeZone("UTC")
  var mismatch = 0; var total = 0
  var start = LocalDate.of(2018, 9, 11)
  val end = LocalDate.of(2077, 10, 16)
  var d = start
  while (!d.isAfter(end)) {
    val h = HijrahDate.from(d)
    val expected = "${h.get(ChronoField.YEAR)}-${h.get(ChronoField.MONTH_OF_YEAR)}-${h.get(ChronoField.DAY_OF_MONTH)}"
    val got = calc.fromEpochMillis(d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), zone)
    val actual = "${got.year}-${got.month}-${got.day}"
    total++
    if (expected != actual) { mismatch++; if (mismatch <= 5) println("DIFF $d expected=$expected got=$actual") }
    d = d.plusDays(1)
  }
  println("table-vs-jdk-umalqura: mismatches=$mismatch / $total")
  println("out-of-range falls back: " + calc.fromEpochMillis(0L, zone))
}
