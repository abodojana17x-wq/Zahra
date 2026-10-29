import com.example.eval.EvalStats

fun main() {
  val (lo, hi) = EvalStats.wilson(60, 100)
  println("wilson 60/100 = %.6f %.6f".format(lo, hi))
  val (lo2, hi2) = EvalStats.wilson(0, 20)
  println("wilson 0/20 = %.6f %.6f".format(lo2, hi2))
  val (lo3, hi3) = EvalStats.wilson(20, 20)
  println("wilson 20/20 = %.6f %.6f".format(lo3, hi3))
  println("mcnemar 0,5 = %.6f".format(EvalStats.mcnemarExact(0, 5)))
  println("mcnemar 1,9 = %.6f".format(EvalStats.mcnemarExact(1, 9)))
  val a = EvalStats.bootstrapMeanCi(List(30) { 1.0 } + List(70) { 0.0 }, iters = 500, seed = 7L)
  val b = EvalStats.bootstrapMeanCi(List(30) { 1.0 } + List(70) { 0.0 }, iters = 500, seed = 7L)
  println("bootstrap = %.4f %.4f deterministic=%s".format(a.first, a.second, a == b))
  println("pct = ${EvalStats.pct(0.86)} interval=${EvalStats.intervalText(180, 223)}")
  // TextNorm + scorer sanity (لا يعتمد على Android)
  println("numbers = " + com.example.eval.TextNorm.numbers("الناتج ٥٦ و ١٫٥ و 1,536"))
  println("norm = " + com.example.eval.TextNorm.normalize("أهلاً وَسَهلاً"))
}
