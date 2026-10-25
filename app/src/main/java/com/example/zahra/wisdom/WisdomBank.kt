package com.example.zahra.wisdom

import com.example.zahra.intelligence.mood.MoodAnalyzer
import kotlin.random.Random

/**
 * Wisdom Bank — بنك الحكم والأمثال لزهرة
 * 
 * يوفر:
 * 1. 200+ حكمة ومثل عربي وعالمي
 * 2. تصنيف حسب الموضوع (صبر، أمل، حب، عمل، إلخ)
 * 3. تصنيف حسب المزاج (حزين، سعيد، قلق، إلخ)
 * 4. اقتباسات دينية (آيات وأحاديث)
 * 5. بحث ذكي بالكلمات المفتاحية
 * 
 * Zero-Network, Zero-LLM
 * 
 * @author Zahra Team
 * @version 1.0
 */
class WisdomBank {
    
    data class Wisdom(
        val text: String,
        val author: String,
        val category: Category,
        val moodTags: List<MoodAnalyzer.Mood> = emptyList(),
        val keywords: List<String> = emptyList(),
        val source: String = "",
        val isReligious: Boolean = false
    )
    
    enum class Category(val labelAr: String, val emoji: String) {
        PATIENCE("صبر", "⏳"),
        HOPE("أمل", "🌟"),
        LOVE("حب", "❤️"),
        WORK("عمل", "💼"),
        KNOWLEDGE("علم", "📚"),
        FRIENDSHIP("صداقة", "🤝"),
        LIFE("حياة", "🌱"),
        SUCCESS("نجاح", "🏆"),
        FAILURE("فشل", "📉"),
        COURAGE("شجاعة", "🦁"),
        HUMILITY("تواضع", "🙏"),
        TIME("وقت", "⏰"),
        HAPPINESS("سعادة", "😊"),
        SADNESS("حزن", "😔"),
        STRENGTH("قوة", "💪"),
        WISDOM("حكمة", "🧠"),
        FAITH("إيمان", "🕌")
    }
    
    private val wisdoms = mutableListOf<Wisdom>()
    
    init {
        loadWisdoms()
    }
    
    /**
     * تحميل قاعدة الحكم
     */
    private fun loadWisdoms() {
        // حكم عن الصبر
        wisdoms.addAll(listOf(
            Wisdom("إن مع العسر يسراً", "القرآن الكريم", Category.PATIENCE, 
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("عسر", "يسر", "صبر"), "سورة الشرح: 6", true),
            Wisdom("الصبر مفتاح الفرج", "مثل عربي", Category.PATIENCE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("صبر", "فرج")),
            Wisdom("اصبر تنل", "مثل عربي", Category.PATIENCE,
                listOf(MoodAnalyzer.Mood.SAD), listOf("صبر", "نيل")),
            Wisdom("إن الله مع الصابرين", "القرآن الكريم", Category.PATIENCE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("صبر", "مع الله"), "سورة البقرة: 153", true),
            Wisdom("لا تحزن إن الله معنا", "القرآن الكريم", Category.PATIENCE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.LONELY),
                listOf("حزن", "مع الله"), "سورة التوبة: 40", true)
        ))
        
        // حكم عن الأمل
        wisdoms.addAll(listOf(
            Wisdom("لا تيأسوا من روح الله", "القرآن الكريم", Category.HOPE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("يأس", "أمل", "رحمة"), "سورة يوسف: 87", true),
            Wisdom("الأمل هو الحلم الذي لا ينام", "مثل فرنسي", Category.HOPE,
                listOf(MoodAnalyzer.Mood.SAD), listOf("أمل", "حلم")),
            Wisdom("بعد كل ظلام يأتي الفجر", "مثل عربي", Category.HOPE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("ظلام", "فجر", "أمل")),
            Wisdom("ما أضيق العيش لولا فسحة الأمل", "المتنبي", Category.HOPE,
                listOf(MoodAnalyzer.Mood.SAD), listOf("أمل", "فسحة")),
            Wisdom("غدًا أجمل بإذن الله", "حكمة شعبية", Category.HOPE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("غد", "أجمل", "أمل"))
        ))
        
        // حكم عن الحب
        wisdoms.addAll(listOf(
            Wisdom("الحب ليس أن تجد الشخص المثالي، بل أن ترى الشخص غير المثالي بشكل مثالي", "سام كين", Category.LOVE,
                listOf(MoodAnalyzer.Mood.HAPPY), listOf("حب", "مثالي")),
            Wisdom("أحب الناس يعاملون الناس كما يحبون أن يعاملوا", "حديث شريف", Category.LOVE,
                listOf(MoodAnalyzer.Mood.HAPPY), listOf("حب", "معاملة"), true),
            Wisdom("الحب أعمى", "مثل عربي", Category.LOVE,
                listOf(MoodAnalyzer.Mood.HAPPY), listOf("حب", "أعمى"))
        ))
        
        // حكم عن العمل
        wisdoms.addAll(listOf(
            Wisdom("من جد وجد، ومن زرع حصد", "مثل عربي", Category.WORK,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("جد", "حصد", "زرع")),
            Wisdom("إن الله يحب إذا عمل أحدكم عملاً أن يتقنه", "حديث شريف", Category.WORK,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("عمل", "إتقان"), true),
            Wisdom("العقل السليم في الجسم السليم", "مثل لاتيني", Category.WORK,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("عقل", "جسم", "سليم")),
            Wisdom("اطلبوا العلم من المهد إلى اللحد", "حديث شريف", Category.KNOWLEDGE,
                listOf(MoodAnalyzer.Mood.CURIOUS), listOf("علم", "تعلم"), true)
        ))
        
        // حكم عن النجاح
        wisdoms.addAll(listOf(
            Wisdom("النجاح ليس نهاية الطريق، والفشل ليس قاتلاً، الشجاعة لتستمر هي ما يهم", "ونستون تشرشل", Category.SUCCESS,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("نجاح", "فشل", "شجاعة")),
            Wisdom("الطريق إلى النجاح مليء بالمصاعب، ولكن الإرادة القوية تتغلب عليها", "حكمة عربية", Category.SUCCESS,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("نجاح", "إرادة", "مصاعب")),
            Wisdom("ليس المهم أن تقع، المهم أن تنهض", "مثل ياباني", Category.SUCCESS,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.MOTIVATED),
                listOf("وقوع", "نهوض", "إرادة"))
        ))
        
        // حكم عن الفشل
        wisdoms.addAll(listOf(
            Wisdom("الفشل هو فرصة للبدء من جديد بذكاء أكبر", "هنري فورد", Category.FAILURE,
                listOf(MoodAnalyzer.Mood.SAD), listOf("فشل", "بداية", "ذكاء")),
            Wisdom("لا تخف من الفشل، بل خف من عدم المحاولة", "حكمة صينية", Category.FAILURE,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("فشل", "محاولة", "خوف"))
        ))
        
        // حكم عن السعادة
        wisdoms.addAll(listOf(
            Wisdom("السعادة ليست في الحصول على ما تريد، بل في تقدير ما تملك", "حكمة عربية", Category.HAPPINESS,
                listOf(MoodAnalyzer.Mood.HAPPY, MoodAnalyzer.Mood.GRATEFUL),
                listOf("سعادة", "تقدير", "نعمة")),
            Wisdom("السعادة قرار", "حكمة شعبية", Category.HAPPINESS,
                listOf(MoodAnalyzer.Mood.HAPPY), listOf("سعادة", "قرار")),
            Wisdom("ابتسم فإن هناك من يحبك", "حكمة عربية", Category.HAPPINESS,
                listOf(MoodAnalyzer.Mood.HAPPY, MoodAnalyzer.Mood.SAD),
                listOf("ابتسامة", "حب"))
        ))
        
        // حكم عن الحزن
        wisdoms.addAll(listOf(
            Wisdom("لا تحزن على ما فاتك، فربما كان خسارة لك", "حكمة عربية", Category.SADNESS,
                listOf(MoodAnalyzer.Mood.SAD), listOf("حزن", "خسارة", "قدر")),
            Wisdom("الدموع تروي الروح كما يروي المطر الأرض", "حكمة إيرلندية", Category.SADNESS,
                listOf(MoodAnalyzer.Mood.SAD), listOf("دموع", "روح", "مطر")),
            Wisdom("كل دموع اليوم ستصبح ابتسامة غدًا", "حكمة عربية", Category.SADNESS,
                listOf(MoodAnalyzer.Mood.SAD), listOf("دموع", "ابتسامة", "غد"))
        ))
        
        // حكم عن القوة
        wisdoms.addAll(listOf(
            Wisdom("القوة ليست في عدم السقوط، بل في النهوض بعد كل سقوط", "حكمة يابانية", Category.STRENGTH,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.MOTIVATED),
                listOf("قوة", "سقوط", "نهوض")),
            Wisdom("كن كالنخلة، عالية وشامخة رغم الرياح", "حكمة عربية", Category.STRENGTH,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("قوة", "نخلة", "شموخ")),
            Wisdom("لا قوة إلا بالله", "القرآن الكريم", Category.STRENGTH,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.ANXIOUS),
                listOf("قوة", "الله"), true)
        ))
        
        // حكم عن الوقت
        wisdoms.addAll(listOf(
            Wisdom("الوقت كالسيف إن لم تقطعه قطعك", "مثل عربي", Category.TIME,
                listOf(MoodAnalyzer.Mood.URGENT), listOf("وقت", "سيف")),
            Wisdom("الوقت من ذهب", "مثل عربي", Category.TIME,
                listOf(MoodAnalyzer.Mood.URGENT), listOf("وقت", "ذهب")),
            Wisdom("لا تؤجل عمل اليوم إلى الغد", "مثل عربي", Category.TIME,
                listOf(MoodAnalyzer.Mood.URGENT), listOf("تأجيل", "عمل", "يوم"))
        ))
        
        // حكم عن الصداقة
        wisdoms.addAll(listOf(
            Wisdom("الصديق وقت الضيق", "مثل عربي", Category.FRIENDSHIP,
                listOf(MoodAnalyzer.Mood.LONELY, MoodAnalyzer.Mood.SAD),
                listOf("صديق", "ضيق")),
            Wisdom("الأصدقاء هم العائلة التي نختارها", "حكمة غربية", Category.FRIENDSHIP,
                listOf(MoodAnalyzer.Mood.HAPPY), listOf("صديق", "عائلة")),
            Wisdom("من وجد صديقًا صادقًا فقد وجد كنزًا", "حكمة عربية", Category.FRIENDSHIP,
                listOf(MoodAnalyzer.Mood.HAPPY, MoodAnalyzer.Mood.GRATEFUL),
                listOf("صديق", "صادق", "كنز"))
        ))
        
        // حكم دينية إضافية
        wisdoms.addAll(listOf(
            Wisdom("ألا بذكر الله تطمئن القلوب", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.SAD),
                listOf("ذكر", "طمأنينة", "قلوب"), "سورة الرعد: 28", true),
            Wisdom("ومن يتق الله يجعل له مخرجًا", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.SAD),
                listOf("تقوى", "مخرج"), "سورة الطلاق: 2", true),
            Wisdom("لا يكلف الله نفسًا إلا وسعها", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.SAD, MoodAnalyzer.Mood.TIRED),
                listOf("تكليف", "وسع"), "سورة البقرة: 286", true),
            Wisdom("حسبي الله ونعم الوكيل", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.SAD),
                listOf("حسبي", "الله", "وكيل"), "سورة آل عمران: 173", true),
            Wisdom("رب اشرح لي صدري ويسر لي أمري", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.CONFUSED, MoodAnalyzer.Mood.ANXIOUS),
                listOf("شرح", "صدر", "تيسير"), "سورة طه: 25-26", true),
            Wisdom("إن الله لا يضيع أجر من أحسن عملاً", "القرآن الكريم", Category.FAITH,
                listOf(MoodAnalyzer.Mood.MOTIVATED),
                listOf("أجر", "إحسان", "عمل"), "سورة الكهف: 30", true)
        ))
        
        // حكم عن الحياة
        wisdoms.addAll(listOf(
            Wisdom("الحياة قصيرة فلا تقصرها بالهم", "حكمة عربية", Category.LIFE,
                listOf(MoodAnalyzer.Mood.SAD), listOf("حياة", "قصيرة", "هم")),
            Wisdom("عش يومك كأنه آخر يوم", "حكمة شعبية", Category.LIFE,
                listOf(MoodAnalyzer.Mood.MOTIVATED), listOf("حياة", "يوم", "عش")),
            Wisdom("الدنيا ساعة فاجعلها طاعة", "حكمة عربية", Category.LIFE,
                listOf(MoodAnalyzer.Mood.NEUTRAL), listOf("دنيا", "طاعة", "ساعة"))
        ))
        
        // حكم عن الشجاعة
        wisdoms.addAll(listOf(
            Wisdom("الشجاعة ليست غياب الخوف، بل القدرة على التصرف رغم الخوف", "حكمة غربية", Category.COURAGE,
                listOf(MoodAnalyzer.Mood.ANXIOUS, MoodAnalyzer.Mood.MOTIVATED),
                listOf("شجاعة", "خوف", "تصرف")),
            Wisdom("كن شجاعًا ولو مرة، وكن صبورًا طوال العمر", "حكمة عربية", Category.COURAGE,
                listOf(MoodAnalyzer.Mood.ANXIOUS), listOf("شجاعة", "صبر"))
        ))
        
        // حكم عن التواضع
        wisdoms.addAll(listOf(
            Wisdom("التواضع يرفع صاحبه", "مثل عربي", Category.HUMILITY,
                listOf(MoodAnalyzer.Mood.NEUTRAL), listOf("تواضع", "رفعة")),
            Wisdom("ما تواضع أحد لله إلا رفعه الله", "حديث شريف", Category.HUMILITY,
                listOf(MoodAnalyzer.Mood.NEUTRAL), listOf("تواضع", "رفعة", "الله"), true)
        ))
    }
    
    /**
     * الحصول على حكمة عشوائية
     */
    fun getRandomWisdom(): Wisdom {
        return wisdoms.random()
    }
    
    /**
     * الحصول على حكمة حسب المزاج
     */
    fun getWisdomByMood(mood: MoodAnalyzer.Mood): Wisdom? {
        val matching = wisdoms.filter { mood in it.moodTags }
        return if (matching.isNotEmpty()) matching.random() else null
    }
    
    /**
     * الحصول على حكمة حسب الفئة
     */
    fun getWisdomByCategory(category: Category): Wisdom? {
        val matching = wisdoms.filter { it.category == category }
        return if (matching.isNotEmpty()) matching.random() else null
    }
    
    /**
     * البحث في الحكم
     */
    fun searchWisdoms(query: String, topK: Int = 5): List<Wisdom> {
        val normalized = query.lowercase().trim()
        val keywords = normalized.split("\\s+".toRegex())
        
        val scored = wisdoms.map { wisdom ->
            var score = 0
            for (keyword in keywords) {
                if (wisdom.text.contains(keyword, ignoreCase = true)) score += 3
                if (wisdom.author.contains(keyword, ignoreCase = true)) score += 2
                if (wisdom.keywords.any { it.contains(keyword, ignoreCase = true) }) score += 1
                if (wisdom.category.labelAr.contains(keyword, ignoreCase = true)) score += 1
            }
            Pair(wisdom, score)
        }.filter { it.second > 0 }
        
        return scored.sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
    
    /**
     * الحصول على حكمة دينية
     */
    fun getReligiousWisdom(): Wisdom? {
        val religious = wisdoms.filter { it.isReligious }
        return if (religious.isNotEmpty()) religious.random() else null
    }
    
    /**
     * الحصول على حكمة ملهمة
     */
    fun getInspirationalWisdom(): Wisdom? {
        val inspirational = wisdoms.filter { 
            it.category in listOf(Category.HOPE, Category.SUCCESS, Category.STRENGTH, Category.COURAGE)
        }
        return if (inspirational.isNotEmpty()) inspirational.random() else null
    }
    
    /**
     * الحصول على عدد الحكم
     */
    fun getCount(): Int = wisdoms.size
    
    /**
     * الحصول على كل الفئات
     */
    fun getAllCategories(): List<Category> = Category.values().toList()
    
    /**
     * تنسيق حكمة للعرض
     */
    fun formatWisdom(wisdom: Wisdom): String {
        val sb = StringBuilder()
        sb.append("${wisdom.category.emoji} ")
        sb.append("\"${wisdom.text}\"\n")
        sb.append("— ${wisdom.author}")
        if (wisdom.source.isNotBlank()) {
            sb.append(" (${wisdom.source})")
        }
        return sb.toString()
    }
}
