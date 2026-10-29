package com.example.zahra.core

/**
 * التطبيع الموحد للنص المدخل في زهرة (عربي / مصري / فرانكو / إنجليزي / Mixed).
 *
 * عقدة [normalize]: أي تحويل يجرى على المدخلة يجب أن يكون محميًا باختبار
 * `normalize(input) == expected` — التطبيع لا يغيّر معنى السؤال.
 */
object ZahraNormalizer {

  private val TASHKEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
  private val ARABIC_INDIC_DIGITS = mapOf(
    '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
    '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
    '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
    '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
  )

  val ARABIC_STOPWORDS = setOf(
    "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
    "هو", "هي", "هم", "هن", "نحن", "أنا", "أنت", "كان", "كانت", "يكون", "أن", "إن",
    "ما", "لا", "لم", "لن", "هل", "قد", "ثم", "أو", "بل", "لكن", "كل", "بعض", "يا"
  )

  /**
   * كلمات محمية من التجذيع ([stemLight]) — إزالة الجذر عنها تحرف المعنى أو تمحو نفيًا / عقيدة / مصطلحًا تقنيًا.
   */
  val STEM_EXCEPTIONS = setOf(
    // نفي وحروف النفي (يجب أن تظل كما هي)
    "مش", "مفيش", "لا", "لم", "لن", "ما", "غير", "بدون", "دون",
    // أسماء الله والعبادات (عقيدة — لا تُجذّع)
    "الله", "الرحمن", "الرحيم", "القرآن", "الحديث", "الصلاة", "الزكاة", "الصيام", "الحج", "الإيمان", "الإسلام",
    // ضمائر ومسلمات لغوية قصيرة
    "أنا", "أنت", "هو", "هي", "نحن", "هذا", "هذه", "ذلك", "تلك", "الذي", "التي",
    // مصطلحات برمجية وتقنية (أسماء متغيرات ومفاتيح لغة)
    "كود", "برمجة", "دالة", "متغير", "حلقة", "بايثون", "جافا", "سكربت", "مكتبة", "واجهة", "كائن", "فئة",
    // كلمات جوهرية يخشى أن يفصلها التجذيع الخطأ عن نوايا زهرة
    "البرمجة", "الذكاء", "الاصطناعي", "بيانات", "نظام", "تطبيق", "موقع"
  )

  /**
   * عبارات فرانكو مركّبة تُترجم قبل التقطيع إلى مفردات (مستوحى من `FRANCO_PHRASES` في `reference/raqim-2.0.html`).
   * تُطبق longest-match أولًا بحيث تقرأ "3amel eh" كوحدة واحدة ولا تُجزّأ إلى "3" + "amel" + "eh".
   */
  val FRANCO_PHRASES = listOf(
    "3amel eh" to "عامل ايه",
    "3amal eh" to "عامل ايه",
    "how are you" to "كيف حالك",
    "thank you" to "شكرا",
    "good morning" to "صباح الخير",
    "good night" to "تصبح على خير",
    "what is your name" to "ما اسمك",
    "who are you" to "من أنت",
    "inshallah" to "ان شاء الله",
    "alhamdulillah" to "الحمد لله",
    "saba7 el kher" to "صباح الخير",
    "saba7 el noor" to "صباح النور",
    "mesa2 el kher" to "مساء الخير",
    "how old are you" to "كم عمرك",
    "what do you do" to "ماذا تعمل",
    "nice to meet you" to "تشرفت بمعرفتك",
    "see you later" to "أراك لاحقا",
    "take care" to "احفظ على نفسك"
  )

  /**
   * قاموس فرانكو–عرب (مصري أساسًا).
   * مدخلات مفيدة documenting إملاءات الفرانكو الحقيقية المتداولة (اللهجة المصرية تكتب شيفات متعددة لنفس الكلمة —
   * راجع المصادر في `evidence/phase-01/research.md`). المفاتيح لاتينية صغيرة؛ لا حشو لزيادة العدد.
   */
  val FRANCO_LEXICON: Map<String, String> = mapOf(
    // --- ضمائر ---
    "ana" to "انا", "enta" to "انت", "enti" to "انتي", "howa" to "هو", "heya" to "هي",
    "ehna" to "احنا", "homma" to "هم", "ento" to "انتم",
    // --- ملكية واتصال ---
    "beta3" to "بتاع", "betaa" to "بتاع", "3and" to "عند", "3ando" to "عنده",
    "3andak" to "عندك", "3andek" to "عندك", "3andaha" to "عندها", "3andhum" to "عندهم",
    // --- إشارة ---
    "da" to "ده", "dah" to "ده", "di" to "دي", "dol" to "دول",
    // --- استفهام ---
    "ezay" to "ازاي", "ezzay" to "ازاي", "izzay" to "ازاي", "azay" to "ازاي",
    "ezayak" to "ازيك", "ezayek" to "ازيك", "akhbarak" to "اخبارك", "akhbarek" to "اخبارك",
    "leh" to "ليه", "leeh" to "ليه", "fein" to "فين", "fen" to "فين", "emta" to "متى", "emteh" to "متى",
    "ma3na" to "معنى", "ma3nah" to "معناه", "ma3naha" to "معناها",
    // --- أدوات ربط وملء شائعة في الفرانكو (كانت تُترك لاتينية فتُشوّش المطابقة) ---
    // ⚠️ لا نضيف كلمات إنجليزية شائعة (we/bel…) إلى القاموس: أي مطابقة واحدة تجعل النص «فرانكو»
    // فتُستبدل كلمات إنجليزية سليمة. أُبقيت الأفعال والأدوات التي لا تلتبس بالإنجليزية.
    "fel" to "في", "zabt" to "ضبط", "2awy" to "قوي", "2awya" to "قوية",
    // --- أفعال ومفردات شائعة كانت ناقصة ---
    "aktb" to "اكتب", "ara2eb" to "ارتب", "awlawyate" to "اولوياتي", "mizaneya" to "ميزانية",
    "shahreya" to "شهرية", "ast3ed" to "استعد", "shoghl" to "شغل", "shoghlk" to "شغلك",
    "eh" to "ايه", "eih" to "ايه",
    // --- رغبة وحاجة ---
    "aez" to "عايز", "ayez" to "عايز", "ayeza" to "عايزه", "ayzeen" to "عايزين",
    "3ayez" to "عايز", "3ayza" to "عايزه", "3awz" to "عاوز", "3awza" to "عاوزة",
    "3ayzeen" to "عايزين", "3ayz" to "عايز", "a3mel" to "اعمل", "ana3mel" to "اعمل",
    "a3raf" to "اعرف", "3arif" to "عارف", "3aref" to "عارف", "a3rafsh" to "لا أعرف",
    // --- زمان ---
    "delwa2ty" to "دلوقتي", "delw2ty" to "دلوقتي", "delwaqty" to "دلوقتي",
    "dilwa2ti" to "دلوقتي", "dilwaqti" to "دلوقتي", "delwa2t" to "دلوقتي",
    "bokra" to "بكرة", "bokraa" to "بكرة", "embareh" to "امبارح", "ambareh" to "امبارح",
    "yomy" to "يومي", "yoman" to "يوميا", "sa3a" to "ساعة", "sa3at" to "ساعات",
    "yom" to "يوم", "ayyam" to "أيام", "leila" to "ليلة", "usbu3" to "أسبوع", "shahr" to "شهر", "sana" to "سنة",
    // --- تحيات واجتماعي ---
    "shokran" to "شكرا", "shukran" to "شكرا", "salam" to "سلام", "salam3lekom" to "السلام عليكم",
    "saba7" to "صباح", "masa2" to "مساء", "tamam" to "تمام", "mashy" to "تمام", "mashi" to "تمام",
    "yalla" to "يلا", "bye" to "مع السلامة", "yalla_bye" to "مع السلامة", "hi" to "مرحبا", "hello" to "مرحبا",
    "sorry" to "آسف", "asf" to "آسف", "please" to "من فضلك", "thanks" to "شكرا",
    "habibi" to "حبيبي", "habibti" to "حبيبتي", "aywa" to "نعم", "aiwa" to "نعم",
    // --- نفي وإثبات ---
    "mish" to "مش", "mesh" to "مش", "mafi4" to "مفيش", "mafeesh" to "مفيش", "mafiish" to "مفيش",
    "la" to "لا", "lam" to "لم", "lan" to "لن",
    // --- صفات وكثرة ---
    "helw" to "حلو", "helwa" to "حلوة", "gamed" to "جامد", "gamid" to "جامد",
    "kwais" to "كويس", "kwayes" to "كويس", "kwayyis" to "كويس", "we7sh" to "وحش",
    "kda" to "كده", "keda" to "كده", "kedah" to "كده", "awi" to "اوي", "awy" to "اوي",
    "bas" to "بس", "kaman" to "كمان", "bardo" to "برضو", "barado" to "برضو",
    "ya3ni" to "يعني", "y3ni" to "يعني", "law" to "لو", "a7san" to "احسن", "ahsan" to "احسن",
    "3ala" to "على", "3shan" to "عشان", "3ashan" to "عشان", "feh" to "فيه", "zay" to "زي", "zayy" to "زي",
    "7aga" to "حاجة", "7agat" to "حاجات", "haga" to "حاجه", "hagat" to "حاجات",
    // --- دين (إشارات عامة فقط؛ الأحكام من ReligionEngine) ---
    "inshallah" to "ان شاء الله", "alhamdulillah" to "الحمد لله", "mashallah" to "ما شاء الله",
    "allah" to "الله",
    // --- تقنية وبرمجة (محمية من التجذيع عبر STEM_EXCEPTIONS بعد التعريب) ---
    "code" to "كود", "programming" to "برمجة", "programing" to "برمجة", "debugging" to "تصحيح",
    "bug" to "باج", "python" to "بايثون", "javascript" to "جافاسكربت", "css" to "سي اس اس",
    "java" to "جافا", "php" to "بي إتش بي", "sql" to "إس كيو إل", "api" to "أيه بي أي",
    "app" to "تطبيق", "website" to "موقع", "database" to "قاعدة بيانات",
    "function" to "دالة", "variable" to "متغير", "loop" to "حلقة", "error" to "خطأ",
    // --- طقس ووقت ---
    "weather" to "الطقس", "time" to "الوقت", "date" to "التاريخ", "today" to "اليوم", "tomorrow" to "غدا",
    // --- أفعال عامية شائعة ---
    "rooh" to "روح", "roo7" to "روح", "gel" to "قال", "geit" to "قلت",
    "shof" to "شوف", "shuf" to "شوف", "kalam" to "كلام", "kallam" to "كلّم",
    "akol" to "أكل", "shrab" to "شرب", "3ml" to "عمل", "3ref" to "عرف", "fham" to "فهم",
    "sam3" to "سمع", "geb" to "أتى", "emshi" to "امشي", "ga3" to "قعد", "3adi" to "عادي",
    // --- أعداد مكتوبة بالفرانكو ---
    "wahad" to "واحد", "etnen" to "إثنين", "tlat" to "ثلاثة", "arba" to "أربعة",
    "khams" to "خمسة", "sitt" to "ستة", "sabaa" to "سبعة", "tman" to "ثمانية", "tisa" to "تسعة", "ashra" to "عشرة",
    // --- عائلة وطعام وأماكن ---
    "mama" to "ماما", "baba" to "بابا", "akh" to "أخي", "ukhti" to "أختي", "habiba" to "حبيبتي",
    "akl" to "أكل", "shay" to "شاي", "ahwa" to "قهوة", "kubs" to "خبز", "lahma" to "لحمة",
    "samak" to "سمك", "dajaj" to "دجاج", "roz" to "أرز", "beit" to "بيت", "beyt" to "بيت",
    "madina" to "مدينة", "masr" to "مصر", "qahira" to "القاهرة", "madrasa" to "مدرسة",
    // --- متغيرات إملائية حقيقية إضافية للفرانكو المصري ---
    "3amal" to "عمل", "3amla" to "عملت", "3amel" to "عامل",
    "delwa2t" to "دلوقتي", "dilwa2ti" to "دلوقتي", "dilwaqti" to "دلوقتي",
    "el kher" to "الخير", "el noor" to "النور",
    "marhaba" to "مرحبا", "ahlan" to "أهلًا", "ahlan w sahlan" to "أهلًا وسهلًا",
    "maalesh" to "معلش", "malesh" to "معلش", "tayyib" to "طيب", "taib" to "طيب",
    "kwayis" to "كويس", "mumkin" to "ممكن", "momkin" to "ممكن",
    "law samaht" to "لو سمحت", "min fadlak" to "من فضلك",
    "afwan" to "عفوًا", "tafadal" to "تفضل", "tafaddal" to "تفضل",
    "zo2" to "زوق", "zuk" to "زوق",
    "3andakum" to "عندكم", "3andena" to "عندنا",
    "kol" to "كل", "koll" to "كل", "kel" to "كل",
    "ba3d" to "بعض", "ba3da" to "بعد",
    "hena" to "هنا", "henak" to "هناك", "wakta" to "وقت",
    // --- أسماء وأفعال وأشكال إضافية موثقة في اللهجة المصرية ---
    "sayyara" to "سيارة", "arabiya" to "عربية", "dokkan" to "دكان",
    "madrasa" to "مدرسة", "gam3a" to "جامعة", "mustashfa" to "مستشفى",
    "saydalia" to "صيدلية", "souq" to "سوق", "bank" to "بنك",
    "naharda" to "نهاردة", "elnaharda" to "النهاردة", "elyoum" to "اليوم",
    "elbokra" to "البكرة", "elbaramg" to "البرنامج",
    "3arifa" to "عارفة", "fhimit" to "فهمت", "sam3it" to "سمعت",
    "shoftek" to "شوفتك",
    "kalamek" to "كلامك", "akalt" to "أكلت", "shrabt" to "شربت",
    "roo7ah" to "روحتها", "emshina" to "ماشينا", "ga3ad" to "قعدت",


    "umm" to "أم", "ab" to "أب",
    "kebir" to "كبير", "saghir" to "صغير", "gawi" to "قوي", "daif" to "ضعيف",
    "ghani" to "غني",
    "tawil" to "طويل", "qasir" to "قصير",
    "esma" to "اسم", "esmak" to "اسمك", "esmi" to "اسمي",
    "hob" to "حب", "mahabba" to "مودة",
    "beitak" to "بيتك", "beitek" to "بيتك",
    "masr" to "مصر",
    "sherk" to "شركة", "hukuma" to "حكومة",



    "fallah" to "فلاح", "tijara" to "تجارة",
    "mal" to "مال", "fulus" to "فلوس", "masari" to "مصاري",
    "shughl" to "شغل", "wazaifa" to "وظيفة",
    "marad" to "مرض", "sihha" to "صحة", "dawa" to "دواء",
    "salah" to "صلاة", "sawm" to "صوم", "zakat" to "زكاة",
    "imam" to "إمام", "masjid" to "مسجد", "kanisa" to "كنيسة",
    "quran" to "قرآن", "hadith" to "حديث", "sunna" to "سنة",
    "shahada" to "شهادة", "ilm" to "علم", "alim" to "عالم",
    "jahl" to "جهل", "aql" to "عقل", "qalb" to "قلب",
    "ruh" to "روح", "nafs" to "نفس", "hayat" to "حياة",
    "dunya" to "دنيا", "akhira" to "آخرة",
    "hubb" to "حب", "karaha" to "كره", "khaf" to "خاف",
    "amal" to "أمل", "yias" to "يأس", "sabr" to "صبر",
    "shukr" to "شكر", "sabr" to "صبر",
    "ana" to "انا", "entom" to "انتم"
  )

  private val FRANCO_PHRASE_REGEXES by lazy {
    FRANCO_PHRASES
      .sortedByDescending { it.first.length }
      .map { (from, to) ->
        Regex("""\b""" + Regex.escape(from) + """\b""", RegexOption.IGNORE_CASE) to to
      }
  }

  private val FRANCO_WORD_REGEXES by lazy {
    FRANCO_LEXICON.entries
      .sortedByDescending { it.key.length }
      .map { entry ->
        Regex("""\b""" + Regex.escape(entry.key) + """\b""", RegexOption.IGNORE_CASE) to entry.value
      }
  }

  /**
   * تحويل فرانكو إلى عربي: عبارات أولًا (longest match)، ثم مفردات القاموس على حدود الكلمة،
   * ثم تحويل الحروف الرقمية (2/3/5/7/8/9) المحاذية للحروف اللاتينية.
   */
  fun francoToArabic(input: String): String {
    var s = input.lowercase()
    // 1) العبارات المركبة أولًا (الأطول أولًا)
    for ((regex, arabic) in FRANCO_PHRASE_REGEXES) {
      s = s.replace(regex, arabic)
    }
    // 2) المفردات على حدود الكلمة
    for ((regex, arabic) in FRANCO_WORD_REGEXES) {
      s = s.replace(regex, arabic)
    }
    // 3) الحروف الرقمية sola pro latina
    s = s.replace(Regex("(?<=[a-z])3|3(?=[a-z])"), "ع")
      .replace(Regex("(?<=[a-z])7|7(?=[a-z])"), "ح")
      .replace(Regex("(?<=[a-z])2|2(?=[a-z])"), "ء")
      .replace(Regex("(?<=[a-z])5|5(?=[a-z])"), "خ")
      .replace(Regex("(?<=[a-z])8|8(?=[a-z])"), "غ")
      .replace(Regex("(?<=[a-z])9|9(?=[a-z])"), "ق")
    return s
  }

  /**
   * كشف franco مبني على الأدلة (ليست قيمة Boolean فقط):
   * - أنماط الأرقام المجاورَة للحروف اللاتينية (2=ء, 3=ع, 5=خ, 7=ح, 8=غ, 9=ق — راجع `evidence/phase-01/research.md` المصدر 5).
   * -iset hits من قاموس الفرانكو divided على عدد كلمات النص latino.
   * تُعاد درجة ثقة موثقة المصدر، لا قرار نعم/لا مجاني.
   */
  fun francoSignals(input: String): FrancoSignals {
    val text = input.lowercase()
    val latinWords = Regex("[a-zA-Z]+").findAll(text).map { it.value }.toList()
    val digitFranco = Regex("[a-zA-Z]*[235789][a-zA-Z]*|[235789]+").findAll(text).count()
    val lexiconHits = FRANCO_LEXICON.keys.count { word ->
      Regex("\\b$word\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    val phraseHits = FRANCO_PHRASES.count { (from, _) ->
      Regex("\\b${Regex.escape(from)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
    val totalFrancoHits = lexiconHits + phraseHits * 2
    val hitRatio = if (latinWords.isEmpty()) 0.0 else totalFrancoHits.toDouble() / latinWords.size
    val isFranco = digitFranco > 0 || totalFrancoHits > 0
    return FrancoSignals(
      isFranco = isFranco,
      digitPatterns = digitFranco,
      lexiconHits = lexiconHits,
      phraseHits = phraseHits,
      hitRatio = hitRatio.coerceIn(0.0, 1.0),
      confidence = francoConfidence(digitFranco, totalFrancoHits, hitRatio)
    )
  }

  private fun francoConfidence(digitPatterns: Int, hits: Int, ratio: Double): Double {
    // tesis موثق من مصادر الفرانكو (المصدر 5 في research.md): وجود نمط رقم-حرف واحد يكفي للاشتباه؛
    // كل مطابقة قاموسية ترفع الثقة حتى سقف 0.97. لا يُدعى أن الدرجة احتمال معاير.
    return when {
      digitPatterns > 0 || hits >= 3 -> (0.85 + (ratio * 0.12)).coerceAtMost(0.97)
      hits == 2 -> 0.78
      hits == 1 -> 0.70
      else -> 0.0
    }
  }

  /**
   * نتيجة تحليلك franco من [ZahraNormalizer.francoSignals].
   */
  data class FrancoSignals(
    val isFranco: Boolean,
    val digitPatterns: Int,
    val lexiconHits: Int,
    val phraseHits: Int,
    val hitRatio: Double,
    val confidence: Double
  )

  fun normalize(input: String?): String {
    if (input.isNullOrBlank()) return ""
    var text = input.trim()

    // إذا كان النص Franco-Arabic (يحتوي حروف لاتينية مع أنماط فرانكو)، حوّل إلى خط عربي أولًا
    val signals = francoSignals(text)
    if (signals.isFranco) {
      text = francoToArabic(text)
    }

    // 1. إزالة التشكيل والتطويل
    text = TASHKEEL_REGEX.replace(text, "")

    // 2. توحيد الألف: أ، إ، آ، ء -> ا
    text = text.replace(Regex("[أإآٱ]"), "ا")

    // 3. توحيد الياء: ى، ئ -> ي
    text = text.replace(Regex("[ىئ]"), "ي")

    // 4. توحيد التاء المربوطة: ة -> ه
    text = text.replace('ة', 'ه')

    // 5. توحيد الأرقام العربية-الهندية والفارسية إلى westlichen
    val sb = StringBuilder(text.length)
    for (ch in text) {
      sb.append(ARABIC_INDIC_DIGITS[ch] ?: ch)
    }
    text = sb.toString()

    // 6. تعديل الكلمات العامية الشائعة إلى صيغة مرجعية (لا يمس النفي "مش/مفيش/ما/لا")
    val colloquialMap = listOf(
      "عاوز" to "عايز",
      "محتاج" to "عايز",
      "اريد" to "عايز",
      "بدي" to "عايز",
      "دلوقتي" to "الان",
      "الحين" to "الان",
      "هسه" to "الان",
      "وبكره" to "وغدا",
      "بكره" to "غدا",
      "وامبارح" to "وامس",
      "امبارح" to "امس"
    )
    for ((from, to) in colloquialMap) {
      text = text.replace(from, to)
    }

    // 7. مسافات بيضاء واحدة
    return text.replace(Regex("\\s+"), " ")
  }

  /**
   * تجذيع عربي خفيف ومحافظ.
   * يُزال أداة التعريف والPrefixes/p suffixes الشائعة فقط عندما لا يؤدي التجزيع إلى Wort أقصر من 3 أحرف،
   * ولا تُمس الكلمات في [STEM_EXCEPTIONS] (نفي، عبادة، تقنية، ضمائر).
   * لا يُطبّق على الكلمات اللاتينية أو الأرقام أو التعبيرات الحسابية.
   */
  fun stemLight(word: String): String {
    if (word.isBlank()) return word
    if (word.any { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() }) return word
    if (word.length <= 3) return word
    if (STEM_EXCEPTIONS.contains(word)) return word

    var s = word
    // أداة التعريف
    if (s.startsWith("ال") && s.length > 4 && !STEM_EXCEPTIONS.contains(s.substring(2))) {
      s = s.substring(2)
    }
    // البادئات المفردة
    val prefixes = listOf("و", "ب", "ك", "ل", "ف")
    for (p in prefixes) {
      if (s.startsWith(p) && s.length - p.length >= 4 && !STEM_EXCEPTIONS.contains(s.substring(p.length))) {
        s = s.substring(p.length)
        break
      }
    }
    // اللواحق (الأطول أولًا)
    val suffixes = listOf("يات", "ياتي", "ونات", "ون", "ين", "ات", "ية", "ها", "كم", "نا", "هم", "هن")
    for (sf in suffixes) {
      if (s.endsWith(sf) && s.length - sf.length >= 4 && !STEM_EXCEPTIONS.contains(s.substring(0, s.length - sf.length))) {
        s = s.substring(0, s.length - sf.length)
        break
      }
    }
    return s
  }

  /**
   * تطبيع مع تجذيع كل كلمة على حدة — يُستخدَم عندما يُريدiezie المطابقة الضبابية وليس المعنى الحرفي.
   * لا يُستخدَم في مسارات تتطلب حفظ النفي أو الأرقام كما هي.
   */
  fun normalizeStems(text: String): String {
    return normalize(text).split(Regex("\\s+")).joinToString(" ") { stemLight(it) }
  }

  fun extractWords(text: String): List<String> {
    val norm = normalize(text)
    val wordRegex = Regex("[\\p{L}\\p{N}]+")
    return wordRegex.findAll(norm)
      .map { it.value.lowercase() }
      .filter { it.length > 1 && !ARABIC_STOPWORDS.contains(it) }
      .toList()
  }
}
