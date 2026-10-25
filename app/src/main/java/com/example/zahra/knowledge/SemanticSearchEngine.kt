package com.example.zahra.knowledge

import com.example.zahra.core.ZahraNormalizer
import kotlin.math.sqrt

/**
 * Semantic Search Engine — محرك البحث الدلالي لزهرة
 * 
 * يبحث بالمعنى وليس الكلمات فقط باستخدام:
 * 1. TF-IDF Vectorization (خفيف offline)
 * 2. Cosine Similarity (قياس التشابه)
 * 3. Keyword Expansion (توسيع الكلمات المرادفة)
 * 4. Contextual Boosting (تعزيز حسب السياق)
 * 
 * Zero-LLM, Zero-Network, Pure Kotlin
 */
class SemanticSearchEngine {
    
    data class SearchDocument(
        val id: String,
        val title: String,
        val content: String,
        val category: String,
        val keywords: List<String> = emptyList(),
        val vector: MutableMap<String, Double> = mutableMapOf()
    )
    
    data class SearchResult(
        val document: SearchDocument,
        val score: Double,
        val matchedTerms: List<String>,
        val semanticMatch: Boolean = false
    )
    
    private val documents = mutableListOf<SearchDocument>()
    private val idfMap = mutableMapOf<String, Double>()
    private var isIdfComputed = false
    
    // قاموس المرادفات العربي (موسع)
    private val synonymsMap = mapOf(
        "سعادة" to listOf("فرح", "بهجة", "سرور", "فرحة", "مبسوط"),
        "حزن" to listOf("أسى", "كآبة", "زعل", "ضيق", "هم"),
        "قلق" to listOf("توتر", "خوف", "اضطراب", "توتر"),
        "غضب" to listOf("انفعال", "عصبية", "حنق", "ثورة"),
        "راحة" to listOf("استرخاء", "هدوء", "سكينة", "طمأنينة"),
        "نجاح" to listOf("فلاح", "إنجاز", "تفوق", "فوز"),
        "فشل" to listOf("خسارة", "إخفاق", "خيبة"),
        "حب" to listOf("عشق", "هوى", "ود", "مودة"),
        "كراهية" to listOf("بغض", "نقمة", "مقت"),
        "عمل" to listOf("شغل", "وظيفة", "مهنة", "كارير"),
        "دراسة" to listOf("تعلم", "تعليم", "مذاكرة", "تحصيل"),
        "صحة" to listOf("عافية", "سلامة", " wellbeing"),
        "مرض" to listOf("علة", "سقم", "وعكة"),
        "طعام" to listOf("أكل", "غذاء", "وجبة", "ماكولات"),
        "سفر" to listOf("رحلة", "تنقل", "سياحة"),
        "صلاة" to listOf("عبادة", "صلاة", "نسك"),
        "قرآن" to listOf("كتاب الله", "مصحف", "كلام الله"),
        "دعاء" to listOf("ابتهال", "تضرع", "مناجاة"),
        "توبة" to listOf("استغفار", "رجوع", "إنابة"),
        "صبر" to listOf("تحمل", "ثبات", "جلد"),
        "شكر" to listOf("حمد", "ثناء", "عرفان"),
        " مساعدة" to listOf("عون", "إسعاف", "نجدة", "دعم"),
        "نصيحة" to listOf("إرشاد", "توجيه", "وعظ"),
        "مشكلة" to listOf("صعوبة", "عقبة", "تحدي", "أزمة"),
        "حل" to listOf("مخرج", "علاج", "تسوية")
    )
    
    /**
     * إضافة وثيقة للفهرس
     */
    fun addDocument(doc: SearchDocument) {
        val normalized = ZahraNormalizer.normalize(doc.content)
        val tokens = tokenize(normalized)
        val tf = computeTF(tokens)
        doc.vector.putAll(tf)
        documents.add(doc)
        isIdfComputed = false
    }
    
    /**
     * إضافة عدة وثائق دفعة واحدة
     */
    fun addDocuments(docs: List<SearchDocument>) {
        docs.forEach { addDocument(it) }
    }
    
    /**
     * بحث دلالي
     */
    fun search(query: String, topK: Int = 5): List<SearchResult> {
        if (documents.isEmpty()) return emptyList()
        if (!isIdfComputed) computeIDF()
        
        val normalizedQuery = ZahraNormalizer.normalize(query)
        val queryTokens = tokenize(normalizedQuery)
        
        // توسيع الاستعلام بالمرادفات
        val expandedTokens = expandWithSynonyms(queryTokens)
        val queryTF = computeTF(expandedTokens)
        val queryVector = mutableMapOf<String, Double>()
        queryTF.forEach { (term, tf) ->
            queryVector[term] = tf * (idfMap[term] ?: 0.0)
        }
        
        val results = mutableListOf<SearchResult>()
        
        for (doc in documents) {
            val similarity = cosineSimilarity(queryVector, doc.vector)
            if (similarity > 0.1) { // threshold
                val matchedTerms = queryVector.keys.filter { doc.vector.containsKey(it) }
                val hasSemanticMatch = matchedTerms.any { it !in queryTokens }
                
                results.add(SearchResult(
                    document = doc,
                    score = similarity,
                    matchedTerms = matchedTerms,
                    semanticMatch = hasSemanticMatch
                ))
            }
        }
        
        return results.sortedByDescending { it.score }.take(topK)
    }
    
    /**
     * بحث سريع بالكلمات المفتاحية + دلالي
     */
    fun hybridSearch(query: String, topK: Int = 5): List<SearchResult> {
        val normalized = ZahraNormalizer.normalize(query).lowercase()
        val queryTokens = tokenize(normalized)
        
        val results = mutableListOf<SearchResult>()
        
        for (doc in documents) {
            var score = 0.0
            val matchedTerms = mutableListOf<String>()
            
            // Exact keyword match (وزن عالي)
            for (token in queryTokens) {
                if (doc.content.contains(token, ignoreCase = true) ||
                    doc.title.contains(token, ignoreCase = true) ||
                    doc.keywords.any { it.contains(token, ignoreCase = true) }) {
                    score += 0.3
                    matchedTerms.add(token)
                }
            }
            
            // Keyword in title (وزن إضافي)
            for (token in queryTokens) {
                if (doc.title.contains(token, ignoreCase = true)) {
                    score += 0.2
                }
            }
            
            // Keyword in keywords list (وزن إضافي)
            for (token in queryTokens) {
                if (doc.keywords.any { it.contains(token, ignoreCase = true) }) {
                    score += 0.15
                }
            }
            
            // Semantic match (بحث دلالي)
            if (isIdfComputed && doc.vector.isNotEmpty()) {
                val queryTF = computeTF(queryTokens)
                val queryVector = queryTF.mapValues { (term, tf) ->
                    tf * (idfMap[term] ?: 0.0)
                }
                val semanticScore = cosineSimilarity(queryVector, doc.vector)
                score += semanticScore * 0.5
            }
            
            if (score > 0.1) {
                results.add(SearchResult(
                    document = doc,
                    score = score,
                    matchedTerms = matchedTerms
                ))
            }
        }
        
        return results.sortedByDescending { it.score }.take(topK)
    }
    
    /**
     * تقسيم النص لكلمات
     */
    private fun tokenize(text: String): List<String> {
        return text.split(Regex("\\s+|[,;.!?،؛؟]+"))
            .filter { it.length > 2 }
            .map { it.trim() }
    }
    
    /**
     * حساب Term Frequency
     */
    private fun computeTF(tokens: List<String>): Map<String, Double> {
        val tf = mutableMapOf<String, Double>()
        val total = tokens.size.toDouble()
        
        for (token in tokens) {
            tf[token] = (tf[token] ?: 0.0) + 1.0
        }
        
        return tf.mapValues { it.value / total }
    }
    
    /**
     * حساب Inverse Document Frequency
     */
    private fun computeIDF() {
        val docCount = documents.size.toDouble()
        val termDocCount = mutableMapOf<String, Int>()
        
        for (doc in documents) {
            val terms = doc.vector.keys
            for (term in terms) {
                termDocCount[term] = (termDocCount[term] ?: 0) + 1
            }
        }
        
        idfMap.clear()
        for ((term, count) in termDocCount) {
            idfMap[term] = kotlin.math.ln(docCount / (1 + count))
        }
        
        isIdfComputed = true
    }
    
    /**
     * توسيع الكلمات بالمرادفات
     */
    private fun expandWithSynonyms(tokens: List<String>): List<String> {
        val expanded = tokens.toMutableList()
        
        for (token in tokens) {
            // ابحث عن مرادفات الكلمة
            synonymsMap[token]?.let { synonyms ->
                expanded.addAll(synonyms.take(2)) // أضف مرادفتين فقط
            }
            
            // ابحث إذا كانت الكلمة مرادفة لكلمة أخرى
            for ((key, synonyms) in synonymsMap) {
                if (token in synonyms) {
                    expanded.add(key)
                    break
                }
            }
        }
        
        return expanded.distinct()
    }
    
    /**
     * حساب Cosine Similarity
     */
    private fun cosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0
        
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        
        for ((term, value1) in vec1) {
            norm1 += value1 * value1
            vec2[term]?.let { value2 ->
                dotProduct += value1 * value2
            }
        }
        
        for ((_, value2) in vec2) {
            norm2 += value2 * value2
        }
        
        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }
    
    /**
     * مسح كل الوثائق
     */
    fun clear() {
        documents.clear()
        idfMap.clear()
        isIdfComputed = false
    }
    
    /**
     * عدد الوثائق المفهرسة
     */
    fun getDocumentCount(): Int = documents.size
}
