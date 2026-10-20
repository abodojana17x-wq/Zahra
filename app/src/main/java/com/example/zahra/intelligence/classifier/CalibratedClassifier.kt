package com.example.zahra.intelligence.classifier

import com.example.zahra.intelligence.features.NlpFeatureExtractor
import kotlin.math.exp
import kotlin.math.ln

data class CalibratedClassification(
  val topIntent: String,
  val topProbability: Double,
  val secondIntent: String?,
  val secondProbability: Double,
  val confidenceMargin: Double,
  val shannonEntropy: Double,
  val isAbstentionRecommended: Boolean,
  val abstentionReason: String? = null,
  val rankedProbabilities: List<Pair<String, Double>>
)

class CalibratedClassifier(
  private val temperature: Double = 1.0,
  private val minProbabilityThreshold: Double = 0.35,
  private val minMarginThreshold: Double = 0.08,
  private val maxEntropyThreshold: Double = 0.85
) {
  private val classDocCounts = mutableMapOf<String, Int>()
  private val classFeatureCounts = mutableMapOf<String, MutableMap<String, Double>>()
  private val classTotalFeatureWeights = mutableMapOf<String, Double>()
  private val vocabulary = mutableSetOf<String>()
  private var totalDocs: Int = 0

  fun train(corpus: Map<String, List<String>>) {
    for ((intent, samples) in corpus) {
      val featMap = classFeatureCounts.getOrPut(intent) { mutableMapOf() }
      var totalWeight = classTotalFeatureWeights.getOrDefault(intent, 0.0)
      var docCount = classDocCounts.getOrDefault(intent, 0)

      for (sample in samples) {
        totalDocs++
        docCount++
        val features = NlpFeatureExtractor.extract(sample)
        for ((featureKey, weight) in features.featureVector) {
          vocabulary.add(featureKey)
          featMap[featureKey] = (featMap[featureKey] ?: 0.0) + weight
          totalWeight += weight
        }
      }
      classDocCounts[intent] = docCount
      classTotalFeatureWeights[intent] = totalWeight
    }
  }

  fun classify(rawText: String): CalibratedClassification {
    val features = NlpFeatureExtractor.extract(rawText)
    val vocabSize = vocabulary.size.coerceAtLeast(1)

    if (features.tokens.isEmpty() || totalDocs == 0) {
      return CalibratedClassification(
        topIntent = "unknown",
        topProbability = 0.10,
        secondIntent = null,
        secondProbability = 0.0,
        confidenceMargin = 0.0,
        shannonEntropy = 1.0,
        isAbstentionRecommended = true,
        abstentionReason = "النص المدخل فارغ أو لا يحتوي على كلمات دلالية صالحة",
        rankedProbabilities = listOf("unknown" to 0.10)
      )
    }

    // Check if any extracted feature exists in vocabulary
    val knownFeaturesCount = features.featureVector.keys.count { vocabulary.contains(it) }
    if (knownFeaturesCount == 0) {
      return CalibratedClassification(
        topIntent = "unknown",
        topProbability = 0.15,
        secondIntent = null,
        secondProbability = 0.0,
        confidenceMargin = 0.0,
        shannonEntropy = 1.0,
        isAbstentionRecommended = true,
        abstentionReason = "لا توجد سمات لغوية معروفة مطابقة في قواعد التصنيف المدمجة",
        rankedProbabilities = listOf("unknown" to 0.15)
      )
    }

    val logScores = mutableListOf<Pair<String, Double>>()

    for ((intent, docCount) in classDocCounts) {
      // Prior in log-space
      var score = ln(docCount.toDouble() / totalDocs)
      val featMap = classFeatureCounts[intent] ?: emptyMap()
      val totalWeight = classTotalFeatureWeights[intent] ?: 0.0

      for ((fKey, fWeight) in features.featureVector) {
        if (!vocabulary.contains(fKey)) continue
        val count = featMap[fKey] ?: 0.0
        // Smoothed probability
        val prob = (count + 0.1) / (totalWeight + (0.1 * vocabSize))
        score += fWeight * ln(prob)
      }
      logScores.add(intent to score)
    }

    if (logScores.isEmpty()) {
      return CalibratedClassification(
        topIntent = "unknown",
        topProbability = 0.10,
        secondIntent = null,
        secondProbability = 0.0,
        confidenceMargin = 0.0,
        shannonEntropy = 1.0,
        isAbstentionRecommended = true,
        abstentionReason = "تعذر احتساب نقاط التصنيف الإحصائي",
        rankedProbabilities = emptyList()
      )
    }

    // Temperature Scaling
    val scaledLogScores = logScores.map { it.first to (it.second / temperature) }
    val maxLog = scaledLogScores.maxOf { it.second }

    // Softmax Normalization
    val expScores = scaledLogScores.map { it.first to exp(it.second - maxLog) }
    val sumExp = expScores.sumOf { it.second }.coerceAtLeast(1e-9)

    val ranked = expScores
      .map { it.first to ((it.second / sumExp * 1000).toInt() / 1000.0) }
      .sortedByDescending { it.second }

    val top = ranked.first()
    val second = if (ranked.size > 1) ranked[1] else null

    val topProb = top.second
    val secondProb = second?.second ?: 0.0
    val margin = ((topProb - secondProb) * 1000).toInt() / 1000.0

    // Shannon Entropy: H = - sum(p * ln(p)) / ln(N)
    val numClasses = ranked.size.coerceAtLeast(2)
    val rawEntropy = -ranked.sumOf { (_, p) ->
      if (p > 1e-9) p * ln(p) else 0.0
    }
    val normalizedEntropy = (rawEntropy / ln(numClasses.toDouble())).coerceIn(0.0, 1.0)
    val roundedEntropy = (normalizedEntropy * 1000).toInt() / 1000.0

    // Calibrated Abstention Evaluation
    var shouldAbstain = false
    var abstentionReason: String? = null

    if (topProb < minProbabilityThreshold) {
      shouldAbstain = true
      abstentionReason = "احتمال النية الأقرب (${(topProb * 100).toInt()}%) أقل من الحد الأدنى للثقة المعتمدة"
    } else if (margin < minMarginThreshold && normalizedEntropy > maxEntropyThreshold) {
      shouldAbstain = true
      abstentionReason = "تقارب شديد بين مقصدي '${top.first}' و '${second?.first}' مع تشتت إحصائي مرتفع (${(roundedEntropy * 100).toInt()}%)"
    }

    return CalibratedClassification(
      topIntent = top.first,
      topProbability = topProb,
      secondIntent = second?.first,
      secondProbability = secondProb,
      confidenceMargin = margin,
      shannonEntropy = roundedEntropy,
      isAbstentionRecommended = shouldAbstain,
      abstentionReason = abstentionReason,
      rankedProbabilities = ranked
    )
  }
}
