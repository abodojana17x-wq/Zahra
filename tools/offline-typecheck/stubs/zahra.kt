package com.example.zahra.model

enum class ConfidenceTier { DIRECT, QUALIFIED, UNCERTAIN }
enum class OutcomeState { SUPPORTED, AMBIGUOUS, UNSUPPORTED, EXECUTION_ERROR }

class ZahraResponse(
  val text: String,
  val intent: String,
  val confidence: Double,
  val confidenceTier: ConfidenceTier,
  val outcomeState: OutcomeState = OutcomeState.SUPPORTED,
  val source: String? = null,
)
