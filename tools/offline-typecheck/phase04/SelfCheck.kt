import com.example.zahra.dialogue.*
import com.example.zahra.intelligence.mood.MoodAnalyzer
fun main() {
  val state = DialogueState(); val resolver = ReferenceResolver()
  check(resolver.resolve("عاصمتها", state, null) is Resolution.Ask)
  state.record("مصر", "", "knowledge", topic = "مصر")
  check((resolver.resolve("وإيه عاصمتها؟", state, null) as Resolution.Query).text.contains("مصر"))
  state.record("الطقس في القاهرة", "", "weather", city = "القاهرة")
  check(resolver.resolve("وبكرة؟", state, null) is Resolution.Abstain)
  check(resolver.resolve("فكرني بكرة", state, null) is Resolution.Unchanged)
  val analyzer = MoodAnalyzer()
  listOf("حاسس اني وحيد" to MoodAnalyzer.Mood.LONELY, "انا زهقان" to MoodAnalyzer.Mood.BORED,
    "انا فرحان النهارده" to MoodAnalyzer.Mood.HAPPY, "اتخانقت مع صاحبي" to MoodAnalyzer.Mood.ANGRY,
    "مش حاسس اني وحيد" to MoodAnalyzer.Mood.NEUTRAL, "هل تحبني" to MoodAnalyzer.Mood.CURIOUS,
    "مش قادر اذاكر" to MoodAnalyzer.Mood.CONFUSED).forEach { (input, mood) ->
      val actual = analyzer.analyze(input).primaryMood
      println("$input: $actual")
      check(actual == mood) { "expected $mood, got $actual" }
    }
  check(EmotionalSupport.answer("هل تحبني") != null)
  check(EmotionalSupport.answer("حاسس اني وحيد وعايز انتحر") == null)
  check(ResponseComposer.compose(ResponseBlock("12 https://example.com", BlockSource.TOOL)).text == "12 https://example.com")
  println("phase4 isolated checks passed")
}
