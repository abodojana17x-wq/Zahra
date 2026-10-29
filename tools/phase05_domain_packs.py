#!/usr/bin/env python3
"""Create authored-by-AI phase-5 domain packs per owner decision D31.

No external corpus is downloaded or copied. Output is original content plus pinpoint
references; source rights are not claimed by merely citing a source.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATE = "2026-10-29"
GENERATED_BY = "Zahra AI / Arena"
AUTHOR = "Zahra AI — مولّد بموافقة مالك المشروع وفق D31؛ دون مراجعة بشرية"
CONTENT_LICENSE = "CC-BY-SA-4.0"


def make_unit(pack: str, unit_id: str, title: str, intent: str, questions: list[str], answers: list[str], tags: list[str], *, source: dict | None = None, safety: str = "none", example: dict | None = None) -> dict:
    unit = {
        "id": unit_id, "pack": pack, "title": title, "intent": intent,
        "q": questions, "a": answers, "tags": tags,
        "authored": True, "author": AUTHOR, "generatedBy": GENERATED_BY,
        "reviewStatus": "owner-waived-no-human-review", "contentLicense": CONTENT_LICENSE,
        "verifiedAt": DATE, "verifiedBy": "automatic schema/content-shape date only; facts not checked",
        "safety": safety, "weight": 1.0,
    }
    if source:
        unit["source"] = source
    if example:
        unit["examples"] = [dict(example, generatedBy=GENERATED_BY, reviewStatus="owner-waived-no-human-review")]
    return unit


def qs(ar: str, fusha: str, en: str, franco: str, extra: str | None = None) -> list[str]:
    out = [ar, fusha, en, franco]
    if extra:
        out.append(extra)
    return out


def ref(name: str, location: str, url: str = "") -> dict:
    data = {"name": name, "location": location, "license": "reference-only; no text reproduced; third-party rights not asserted"}
    if url:
        data["url"] = url
    return data


def make_pack(name: str, units: list[dict], note: str, sources: list[dict] | None = None) -> None:
    folder = ROOT / "packs" / name
    folder.mkdir(parents=True, exist_ok=True)
    data = {
        "pack": name, "version": "1.0.0", "license": CONTENT_LICENSE,
        "generatedBy": GENERATED_BY, "reviewStatus": "owner-waived-no-human-review",
        "authors": [AUTHOR], "sources": sources or [], "generatedAt": DATE,
        "note": note + " المحتوى مولّد آليًا؛ لا توجد مراجعة بشرية. نصوص المصادر الخارجية غير منقولة؛ ترخيصها لا يُستنتج من الاستشهاد.",
        "units": units,
    }
    (folder / "pack.json").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def study_pack() -> None:
    p = "study"
    units = [
        make_unit(p, "study-start-small", "بدء المذاكرة بخطوة قصيرة", "query_knowledge",
            qs("مش قادر أبدأ أذاكر", "كيف أبدأ المذاكرة عندما أشعر بالتشتت؟", "How can I start studying when I feel stuck?", "mesh 3aref abda2 azaker"),
            ["اختار مهمة صغيرة جدًا: افتح صفحة واحدة، اكتب سؤالًا عنها، وحاول الإجابة خمس دقائق. بعدها خذ استراحة قصيرة أو أكمل إن كان ذلك مناسبًا.", "هذه طريقة تنظيم عملية وليست علاجًا للتعب أو القلق؛ لو الصعوبة مستمرة، استعن بشخص تثق به أو مختص."], ["مذاكرة", "بدء", "تركيز"]),
        make_unit(p, "study-active-recall", "الاستدعاء النشط للمراجعة", "define_word",
            qs("يعني إيه استدعاء نشط؟", "ما المقصود بالاستدعاء النشط؟", "What is active recall?", "ya3ny eh active recall"),
            ["الاستدعاء النشط أن تحاول تذكّر الفكرة أو شرحها من الذاكرة قبل الرجوع إلى الكتاب.", "مثال: اقرأ مقطعًا قصيرًا، أغلقه، واكتب ثلاث نقاط تتذكرها، ثم قارنها بالمصدر وصحح ما فاتك. لا تعتمد على عدد ثابت يناسب الجميع."], ["مذاكرة", "استدعاء", "تعلم"], source=ref("Dunlosky et al., Psychological Science in the Public Interest", "2013, 14(1), practice testing", "https://doi.org/10.1111/j.1539-6053.2013.01044.x")),
        make_unit(p, "study-spaced-review", "المراجعة المتباعدة", "define_word",
            qs("إزاي أراجع على فترات؟", "ما معنى المراجعة المتباعدة؟", "What is spaced practice?", "ezay arage3 3ala fatrat"),
            ["المراجعة المتباعدة توزيع جلسات التذكر على أوقات مختلفة بدل وضعها كلها في جلسة واحدة.", "عمليًا: سجّل موعد المراجعة التالية بعد أن تختبر نفسك، وعدّل الفاصل بحسب صعوبة المادة والوقت المتاح؛ لا يوجد جدول واحد يناسب الجميع."], ["مذاكرة", "مراجعة", "تكرار"]),
        make_unit(p, "study-exam-plan", "تقسيم الاستعداد للامتحان", "query_knowledge",
            qs("أقسم مذاكرتي للامتحان إزاي؟", "كيف أضع خطة مراجعة للامتحان؟", "How do I plan exam revision?", "a2asem moraga3ty ezay"),
            ["اكتب المواد والموعد، ثم قسّم كل مادة إلى جلسات صغيرة مع وقت للمراجعة وحل الأسئلة والراحة.", "ابدأ بالأقرب موعدًا أو بالأجزاء التي تحتاج تدريبًا، واترك هامشًا للتأخير. الخطة أداة قابلة للتعديل وليست ضمانًا لنتيجة امتحان."], ["امتحان", "خطة", "دراسة"]),
        make_unit(p, "study-breaks", "تنظيم استراحات المذاكرة", "query_knowledge",
            qs("أرتاح قد إيه بين جلسات المذاكرة؟", "ما فائدة الاستراحات أثناء الدراسة؟", "How should I take study breaks?", "arata7 ad eh ben el sessions"),
            ["خذ استراحة قصيرة عند انتهاء جزء محدد أو عندما يقل تركيزك، وابتعد خلالها عن المهمة قليلًا ثم ارجع بخطوة واضحة.", "مدة الاستراحة المناسبة تختلف بين الأشخاص والمهام؛ لا تعتبر مؤقتًا أو نظامًا واحدًا قاعدة علمية عامة."], ["مذاكرة", "استراحة", "تركيز"]),
    ]
    make_pack(p, units, "مفاهيم دراسية عملية قصيرة؛ لا تعِد بنتيجة دراسية أو نفسية.")


def money_pack() -> None:
    p = "money"
    units = [
        make_unit(p, "money-budget-basics", "إنشاء ميزانية شهرية بسيطة", "query_knowledge",
            qs("أعمل ميزانية شهرية إزاي؟", "كيف أبدأ إعداد ميزانية؟", "How do I make a simple budget?", "a3mel budget shahria ezay"),
            ["سجّل الدخل المتاح، المصروفات الضرورية، الالتزامات، ثم ما يتبقى للأهداف والادخار. راجع الأرقام الفعلية وعدّل الخطة.", "مثال حسابي فقط: دخل 10,000 ومصروفات 7,500 يترك 2,500 قبل الالتزامات الأخرى. هذا ليس توصية بنسبة ثابتة أو استشارة شخصية."], ["ميزانية", "مصروف", "دخل"], source=ref("Consumer Financial Protection Bureau", "Budgeting tools and spending tracker", "https://www.consumerfinance.gov/consumer-tools/budgeting/")),
        make_unit(p, "money-percent", "حساب النسبة المئوية", "request_code",
            qs("أحسب نسبة من رقم إزاي؟", "كيف أحسب نسبة مئوية من قيمة؟", "How do I calculate a percentage?", "a7seb nesba men ra2m ezay"),
            ["لإيجاد p بالمئة من قيمة x: اضرب x في p ثم اقسم على 100.", "مثال: 15% من 240 = 240 × 15 ÷ 100 = 36. هذه عملية حسابية وليست نصيحة استثمار."], ["نسبة", "حساب", "رياضيات"], example={"lang": "text", "code": "240 * 15 / 100", "expected": "36"}),
        make_unit(p, "money-simple-interest", "حساب الفائدة البسيطة", "define_word",
            qs("إيه هي الفائدة البسيطة؟", "ما المقصود بالفائدة البسيطة؟", "What is simple interest?", "eh el fa2da el baseeta"),
            ["في نموذج الفائدة البسيطة، الفائدة = أصل المبلغ × المعدل السنوي × الزمن بالسنوات.", "مثال رياضي غير تعاقدي: 1,000 × 0.05 × 2 = 100. العقود الحقيقية قد تستخدم رسومًا وشروطًا مختلفة؛ راجعها قبل الالتزام."], ["فائدة", "رياضيات", "مال"], example={"lang": "text", "code": "1000 * 0.05 * 2", "expected": "100"}),
        make_unit(p, "money-exchange-rates", "التعامل مع أسعار العملات", "query_knowledge",
            qs("أعرف سعر الصرف منين؟", "كيف أتحقق من سعر تحويل العملة؟", "How can I check an exchange rate?", "a3raf se3r el sarf menen"),
            ["سعر الصرف يتغير بحسب الوقت ومزود الخدمة، وقد تضاف رسوم أو فروق سعرية. تحقّق من مصدر موثوق يذكر وقت التحديث والرسوم.", "هذه الحزمة لا تعرض سعرًا حيًا ولا توصي بتحويل محدد؛ لا تستخدم رقمًا بلا تاريخ ومصدر."], ["عملة", "سعر صرف", "مصدر"]),
        make_unit(p, "money-debt-checklist", "مراجعة التزامات مالية قبل القرار", "query_knowledge",
            qs("أراجع قرض قبل ما أوافق إزاي؟", "ما الذي أتحقق منه قبل قبول قرض؟", "What should I check before taking a loan?", "arage3 el qard abl ma awafe2 ezay"),
            ["اقرأ إجمالي المبلغ الواجب سداده، المعدل والرسوم، مواعيد الدفع، شروط التأخر، وإمكانية السداد المبكر. اطلب نسخة مكتوبة واسأل جهة مستقلة عند عدم الوضوح.", "هذه قائمة تثقيفية عامة وليست توصية مالية أو قانونية؛ القواعد تختلف حسب العقد والبلد."], ["قرض", "التزامات", "رسوم"]),
    ]
    make_pack(p, units, "حسابات وتثقيف مالي عام فقط؛ لا أسعار لحظية ولا مشورة شخصية.")


def health_pack() -> None:
    p = "health"
    disclaimer = "تنبيه صحي: هذه معلومات عامة لا تشخّص ولا تستبدل الطبيب. للحالة الطارئة اتصل بالطوارئ المحلية فورًا."
    units = [
        make_unit(p, "health-urgent-signs", "التعامل مع حالة صحية طارئة", "query_knowledge",
            qs("إيه أعمل لو الحالة طارئة؟", "ماذا أفعل عند خطر صحي فوري؟", "What should I do in a medical emergency?", "a3mel eh law 7ala tare2a"),
            ["إذا بدا أن الشخص في خطر فوري أو فقد الوعي أو يعاني صعوبة شديدة في التنفس، اتصل بخدمات الطوارئ المحلية فورًا واتبع تعليمات المجيب. لا تؤخر طلب المساعدة بحثًا عن تشخيص عبر التطبيق.", disclaimer], ["طوارئ", "سلامة", "طبيب"], safety="medical_disclaimer"),
        make_unit(p, "health-medication-label", "قراءة ملصق الدواء بأمان", "query_knowledge",
            qs("أستخدم الدوا إزاي بأمان؟", "ما الاحتياطات عند استخدام دواء؟", "How can I use medicine safely?", "astakhdem el dawa ezay"),
            ["اقرأ الملصق والنشرة واتبع تعليمات الطبيب أو الصيدلي، ولا تشارك دواءك مع شخص آخر. إذا لم تفهم الجرعة أو حدث تفاعل غير متوقع، اسأل مختصًا.", disclaimer], ["دواء", "صيدلي", "سلامة"], safety="medical_disclaimer"),
        make_unit(p, "health-symptom-log", "تسجيل الأعراض لمناقشتها مع مختص", "query_knowledge",
            qs("إيه أقول للدكتور عن الأعراض؟", "كيف أستعد لموعد طبي؟", "How should I prepare for a medical visit?", "a2ol lel doctor eh 3an el a3rad"),
            ["دوّن وقت بداية العرض، ما لاحظته، الأدوية المعروفة والحساسية، والأسئلة التي تريد طرحها. لا تستنتج التشخيص بنفسك من قائمة أعراض.", disclaimer], ["أعراض", "موعد طبي", "معلومات"], safety="medical_disclaimer"),
        make_unit(p, "health-sleep-general", "عادات عامة تساعد على انتظام النوم", "query_knowledge",
            qs("إزاي أظبط مواعيد نومي؟", "ما العادات العامة لنوم منتظم؟", "What habits support a regular sleep schedule?", "azabat mawa3ed nomy ezay"),
            ["حاول تثبيت وقت الاستيقاظ، واجعل مكان النوم هادئًا، وقلل المنبهات قرب وقت النوم إن كانت تؤثر عليك. الاحتياجات تختلف وقد تتأثر الصحة بأسباب أخرى؛ استشر مختصًا عند استمرار المشكلة.", disclaimer], ["نوم", "روتين", "صحة"], safety="medical_disclaimer"),
    ]
    make_pack(p, units, "معلومات صحية عامة محافظة؛ إخلاء المسؤولية موجود في كل إجابة ولا توجد تشخيصات أو جرعات.")


def facts_pack() -> None:
    p = "facts"
    units = [
        make_unit(p, "fact-light-speed", "سرعة الضوء في الفراغ", "query_knowledge",
            qs("سرعة الضوء كام؟", "ما قيمة سرعة الضوء في الفراغ؟", "What is the speed of light in vacuum?", "sor3et el do2 kam"),
            ["قيمة سرعة الضوء في الفراغ ثابتة بالتعريف: 299,792,458 مترًا في الثانية.", "المعلومة تخص الفراغ؛ سرعة انتشار الضوء في مادة مثل الماء أقل وتتوقف على خواص الوسط."], ["ضوء", "فيزياء", "ثابت"], source=ref("BIPM — The International System of Units", "SI Brochure, defining constants: speed of light c", "https://www.bipm.org/en/publications/si-brochure")),
        make_unit(p, "fact-water-boiling", "درجة غليان الماء تعتمد على الضغط", "query_knowledge",
            qs("المية بتغلي على كام درجة؟", "ما درجة غليان الماء؟", "At what temperature does water boil?", "el mayya betghely 3ala kam"),
            ["قرب الضغط الجوي القياسي يغلي الماء النقي عند نحو 100 درجة مئوية. تتغير درجة الغليان مع الضغط والارتفاع؛ لذلك ليست قيمة ثابتة لكل مكان."], ["ماء", "حرارة", "ضغط"], source=ref("NIST Chemistry WebBook — Water", "Phase-change data at stated pressure", "https://webbook.nist.gov/cgi/cbook.cgi?ID=C7732185&Mask=4")),
        make_unit(p, "fact-au-definition", "تعريف الوحدة الفلكية", "query_knowledge",
            qs("الوحدة الفلكية بتساوي إيه؟", "ما تعريف الوحدة الفلكية AU؟", "What is one astronomical unit?", "el wehda el falakeya betesawy eh"),
            ["الوحدة الفلكية وحدة طول تساوي 149,597,870,700 متر بالضبط، وتُستخدم لوصف المسافات داخل النظام الشمسي.", "هي وحدة قياس معرفة، وليست وعدًا بأن المسافة الفعلية بين الأرض والشمس ثابتة طوال مدار الأرض."], ["فلك", "وحدة", "مسافة"], source=ref("International Astronomical Union", "Resolution B2 (2012): definition of the astronomical unit")),
        make_unit(p, "fact-binary-place-value", "قيمة الخانات في النظام الثنائي", "define_word",
            qs("إزاي أقرأ الرقم الثنائي؟", "كيف تعمل خانات النظام الثنائي؟", "How do binary place values work?", "ezay aqra el ra2m el thona2y"),
            ["النظام الثنائي أساسه 2؛ قيمة الخانة من اليمين هي 1 ثم 2 ثم 4 ثم 8، وهكذا. الرقم 1011₂ يساوي 8+0+2+1 = 11₁₀.", "هذا تحويل حسابي مباشر، ولا يحتاج اتصالًا أو سعرًا متغيرًا."], ["ثنائي", "رياضيات", "حاسوب"], example={"lang": "text", "code": "1*8 + 0*4 + 1*2 + 1*1", "expected": "11"}),
    ]
    make_pack(p, units, "حقائق ثابتة محددة النطاق مع مصادر موضعية؛ لا لقطة ويكيبيديا ولا معلومات حية.")


def code_snippets_pack() -> None:
    p = "code-snippets"
    units = [
        make_unit(p, "snippet-python-unique", "إزالة التكرار مع الحفاظ على الترتيب في Python", "request_code",
            qs("شيل التكرار من الليست من غير ما أغير ترتيبها", "كيف أحذف المكرر مع الحفاظ على الترتيب؟", "How do I deduplicate while preserving order?", "asheيل el takrar men el list"),
            ["استخدم dict.fromkeys في Python الحديثة للحفاظ على أول ظهور لكل قيمة قابلة للهاش.", "```python\nvalues = [3, 1, 3, 2, 1]\nunique = list(dict.fromkeys(values))\nprint(unique)\n```\nالناتج: `[3, 1, 2]`. العناصر غير القابلة للهاش تحتاج طريقة مختلفة."], ["python", "قائمة", "تكرار"], example={"lang": "python", "code": "values = [3, 1, 3, 2, 1]\nprint(list(dict.fromkeys(values)))", "expected": "[3, 1, 2]"}),
        make_unit(p, "snippet-python-safe-int", "تحويل نص إلى عدد صحيح بأمان", "request_code",
            qs("حوّل نص لرقم في بايثون مع معالجة الخطأ", "كيف أتحقق من تحويل نص إلى عدد؟", "How can I safely parse an integer?", "a7awel text le ra2m python ezay"),
            ["جرّب التحويل وعالج ValueError عند المدخل غير الرقمي.", "```python\ndef parse_int(text):\n    try:\n        return int(text)\n    except ValueError:\n        return None\nprint(parse_int(\"42\"))\n```\nالناتج: `42`؛ ترجع None للنص غير الصالح."], ["python", "تحويل", "استثناء"], example={"lang": "python", "code": "int(\"42\")", "expected": "42"}),
        make_unit(p, "snippet-js-clamp", "تقييد رقم ضمن حدين في JavaScript", "request_code",
            qs("اكتب clamp في جافاسكريبت", "كيف أحصر قيمة بين حد أدنى وأعلى؟", "How do I clamp a number?", "a3mel clamp javascript ezay"),
            ["الدالة clamp تعيد أقرب قيمة داخل المجال المحدد.", "```javascript\nconst clamp = (x, min, max) => Math.min(max, Math.max(min, x));\nconsole.log(clamp(12, 0, 10));\n```\nالناتج: `10`. افترض أن min لا يتجاوز max."], ["javascript", "رياضيات", "نطاق"], example={"lang": "javascript", "code": "Math.min(10, Math.max(0, 12))", "expected": "10"}),
        make_unit(p, "snippet-css-grid-center", "توسيط محتوى باستخدام CSS Grid", "request_code",
            qs("وسّط عنصر بالـ grid في CSS", "كيف أوسّط المحتوى باستخدام CSS Grid؟", "How do I center an item with CSS Grid?", "awsat 3onsor bel grid ezay"),
            ["اجعل الحاوية grid واستخدم place-items لتوسيط عناصرها على المحورين.", "```css\n.container {\n  display: grid;\n  place-items: center;\n  min-height: 100vh;\n}\n```\nيتم التوسيط داخل أبعاد الحاوية؛ يجب أن تكون لها مساحة قابلة للعرض."], ["css", "grid", "توسيط"], example={"lang": "css", "code": ".container { display: grid; place-items: center; min-height: 100vh; }", "expected": "items centered in the container"}),
        make_unit(p, "snippet-sql-top", "استعلام SQL لأعلى ثلاثة سجلات", "request_code",
            qs("هات أعلى ٣ منتجات سعرًا بـ SQL", "كيف أسترجع أعلى ثلاثة أسعار؟", "How do I select the three highest prices in SQL?", "ageeb a3la talata as3ar SQL"),
            ["رتّب تنازليًا وحدد عدد الصفوف؛ صيغة تحديد الصفوف قد تختلف بين قواعد البيانات.", "```sql\nSELECT name, price\nFROM products\nORDER BY price DESC\nLIMIT 3;\n```\nهذه صيغة SQLite وPostgreSQL؛ راجع توثيق محركك قبل استخدامها في محرك آخر."], ["sql", "ترتيب", "حد"], example={"lang": "sql", "code": "SELECT name, price FROM products ORDER BY price DESC LIMIT 3;", "expected": "up to three rows ordered by descending price"}),
        make_unit(p, "snippet-python-json-file", "قراءة JSON من ملف Python", "request_code",
            qs("افتح ملف JSON ببايثون", "كيف أقرأ JSON من ملف؟", "How do I read JSON from a file in Python?", "a2ra json men file python ezay"),
            ["استخدم json.load على ملف مفتوح بترميز UTF-8؛ ستتحول البيانات إلى dict أو list وغيرها.", "```python\nimport json\nwith open(\"data.json\", encoding=\"utf-8\") as f:\n    data = json.load(f)\nprint(type(data).__name__)\n```\nالناتج يعتمد على البنية العليا في الملف. لا تستخدم eval لقراءة JSON."], ["python", "json", "ملف"], example={"lang": "python", "code": "json.loads('{\"x\": 1}')", "expected": "{'x': 1}"}),
    ]
    make_pack(p, units, "قصاصات تعليمية مكتوبة مع قيود السياق والناتج المتوقع؛ لا تنفذها زهرة تلقائيًا.")


def religion_pack() -> None:
    p = "religion"
    lessons = [
        ("fiqh_wudu", "الوضوء", "FIQH", "طهارة الوضوء مرتبطة بآية الوضوء في سورة المائدة. هذه بطاقة تعريفية لا تفصل أركان الوضوء أو نواقضه؛ ففي تفاصيلها مسائل فقهية، فاسأل عالمًا موثوقًا عن حالتك.", "القرآن الكريم، المائدة 5:6"),
        ("fiqh_prayer", "الصلاة", "FIQH", "الصلاة عبادة مركزية في الإسلام، ويذكر القرآن ارتباطها بأوقات معلومة. هذه نبذة تعليمية؛ تفاصيل الأداء والأعذار والمسائل تختلف، وليست فتوى.", "القرآن الكريم، النساء 4:103"),
        ("fiqh_zakat", "الزكاة", "FIQH", "الزكاة عبادة مالية، ويذكر القرآن مصارفها. حساب النصاب والمال الواجب وتفاصيل الأصول قد يختلف باختلاف الحال والاجتهاد؛ لا تعتمد هذه البطاقة لحساب التزامك، واسأل أهل العلم.", "القرآن الكريم، التوبة 9:60"),
        ("fiqh_fasting", "الصيام", "FIQH", "الصيام عبادة لها أحكام عامة في القرآن. الأعذار والمرض والسفر والقضاء مسائل تعتمد على الحالة والمذهب؛ هذه نبذة وليست فتوى أو بديلًا عن سؤال عالم.", "القرآن الكريم، البقرة 2:183–185"),
        ("fiqh_hajj", "الحج", "FIQH", "الحج من شعائر الإسلام، ويرتبط في القرآن بالاستطاعة. متطلبات السفر والصحة والنسك وتفاصيل المناسك تحتاج مصادر رسمية موثوقة وسؤال مختص؛ لا تُعد هذه البطاقة إرشادًا للنسك.", "القرآن الكريم، آل عمران 3:97"),
        ("seerah_birth", "مولد النبي ﷺ", "SEERAH", "تتناول كتب السيرة نشأة النبي محمد ﷺ في مكة وبدايات حياته. تختلف بعض الروايات التاريخية في تفاصيل التواريخ؛ لذلك نعرض الموضوع كسياق للسيرة ولا نجزم بتاريخ مختلف فيه.", "ابن هشام، السيرة النبوية، أخبار المولد والنشأة"),
        ("seerah_revelation", "بداية الوحي", "SEERAH", "تذكر المصادر الإسلامية بدء الوحي في غار حراء، وتفتتح سورة العلق بآيات القراءة. هذه خلاصة تعليمية موجزة، وللتفاصيل يُرجع إلى مصادر الحديث والسيرة.", "القرآن الكريم، العلق 96:1–5؛ صحيح البخاري، كتاب بدء الوحي"),
        ("seerah_hijrah", "الهجرة إلى المدينة", "SEERAH", "الهجرة حدث محوري في السيرة النبوية وانتقال لبناء مجتمع المدينة. يذكر القرآن قصة الغار في سياق التوكل؛ لا تُستنتج من هذه البطاقة تفاصيل تاريخية إضافية غير موثقة.", "القرآن الكريم، التوبة 9:40؛ ابن هشام، خبر الهجرة"),
        ("seerah_badr", "غزوة بدر", "SEERAH", "بدر حدث من أحداث السيرة في السنة الثانية للهجرة، ويذكر القرآن بدرًا في سياق تاريخي. اقرأ الحدث من مصدر سيرة موثق مع مراعاة سياقه وعدم اختزال الدروس في ادعاءات حديثة.", "القرآن الكريم، آل عمران 3:123–125؛ ابن هشام، غزوة بدر"),
        ("seerah_fath", "فتح مكة", "SEERAH", "فتح مكة محطة تاريخية في السيرة النبوية. هذه بطاقة تعريف بالموضوع فقط، ولا تسرد رواية تفصيلية أو حكمًا معاصرًا؛ تُراجع الوقائع في مصادر السيرة المعتمدة.", "ابن هشام، السيرة النبوية، خبر فتح مكة"),
        ("aqeedah_tawhid", "التوحيد", "AQEEDAH", "التوحيد هو إفراد الله بالعبادة في العقيدة الإسلامية، وسورة الإخلاص تعرض معاني التوحيد بإيجاز. هذه مقدمة وصفية وليست مناظرة أو حكمًا على أشخاص.", "القرآن الكريم، سورة الإخلاص 112:1–4"),
        ("aqeedah_pillars", "أركان الإيمان", "AQEEDAH", "تُعرض أركان الإيمان في حديث جبريل: الإيمان بالله وملائكته وكتبه ورسله واليوم الآخر والقدر. هذه صياغة تعليمية موجزة، وللنص والتخريج ارجع إلى المصدر المذكور.", "صحيح مسلم، كتاب الإيمان، حديث جبريل"),
        ("aqeedah_qadr", "الإيمان بالقدر", "AQEEDAH", "الإيمان بالقدر أصل من أصول العقيدة الإسلامية. فهم علاقته باختيار الإنسان ومسؤوليته له مباحث علمية دقيقة؛ نكتفي بالتعريف ولا نصدر حكمًا عقديًا على السائل.", "القرآن الكريم، القمر 54:49؛ صحيح مسلم، كتاب الإيمان، حديث جبريل"),
        ("aqeedah_day_of_judgment", "اليوم الآخر", "AQEEDAH", "الإيمان باليوم الآخر يشمل الإيمان بالبعث والحساب والجزاء في التصور الإسلامي. القرآن يذكر وزن الأعمال؛ هذه بطاقة تعريفية لا تفسر الغيب من دون مرجع.", "القرآن الكريم، الزلزلة 99:6–8"),
        ("tazkiyah_change", "مراجعة النفس والتغيير", "TAZKIYAH", "تدعو آية من سورة الرعد إلى مراجعة النفس والعمل، ويستفيد منها المسلم في التوبة والإصلاح. لا تعني أن كل ظرف قاسٍ سببه الشخص؛ ولا تغني عن مساعدة مختص عند الحاجة.", "القرآن الكريم، الرعد 13:11"),
        ("tazkiyah_three_axes", "محاسبة النفس والعمل", "TAZKIYAH", "من معاني التزكية أن يراجع الإنسان نيته وأعماله ويخطط لإصلاح ما يستطيع. هذا توجيه أخلاقي عام وليس وصفة نفسية أو لومًا لمن يواجه مرضًا أو ظرفًا خارج سيطرته.", "القرآن الكريم، الحشر 59:18"),
        ("tazkiyah_ubudiyyah", "العبودية لله", "TAZKIYAH", "العبودية لله مفهوم ديني يتصل بالعبادة والطاعة في الإسلام. يوضح القرآن غاية الخلق في سورة الذاريات؛ هذه خلاصة عامة وليست فتوى في عبادة بعينها.", "القرآن الكريم، الذاريات 51:56"),
        ("tazkiyah_asbab", "الأخذ بالأسباب والتوكل", "TAZKIYAH", "في السلوك الإسلامي يجتمع بذل السبب المتاح مع التوكل على الله، من غير ادعاء معرفة الغيب أو ضمان نتيجة. عند سؤال عملي أو حكم شرعي محدد، يُرجع لأهل العلم.", "القرآن الكريم، آل عمران 3:159؛ التوبة 9:51"),
        ("tazkiyah_dunya", "التوازن في التعامل مع الدنيا", "TAZKIYAH", "تتناول سورة الحديد زينة الحياة الدنيا في سياق تذكير أخلاقي؛ لا يلزم من ذلك ذم العمل أو الأسرة أو الممتلكات. المقصود هنا عرض موضوع روحي عام لا حكم على اختيارات الناس.", "القرآن الكريم، الحديد 57:20"),
        ("tazkiyah_quran", "القرآن والتدبر", "TAZKIYAH", "القرآن كتاب هداية وعبادة للمسلمين، وتدبره يكون بفهم النص وسياقه والاستعانة بتفسير موثوق. وصفه بالشفاء معنى ديني ولا يغني عن العلاج الطبي أو النفسي.", "القرآن الكريم، الإسراء 17:82؛ ص 38:29"),
        ("tazkiyah_jihad_nafs", "مجاهدة النفس على الخير", "TAZKIYAH", "يمكن استعمال هذا العنوان للدلالة على تدريب النفس على الصبر وترك السلوك الضار وفعل الخير. لا ننسب حديثًا غير موثق لعبارة مشهورة، ولا يعني المصطلح هنا إيذاء النفس أو الآخرين.", "القرآن الكريم، الشمس 91:9–10"),
    ]
    units = []
    for lesson_id, title, category, summary, location in lessons:
        ref_obj = ref("مرجع إسلامي — موضع محدد؛ صياغة تفسيرية أصلية دون نقل نص", location)
        ref_obj["reference"] = location
        units.append(make_unit(
            p, f"religion-{lesson_id.replace('_', '-')}", title, "religious_reference",
            qs(f"اشرح {title}", f"ما المقصود بـ{title}؟", f"What is {title} in Islamic studies?", f"eشرح {title} ya3ni eh", f"درس عن {title}"),
            [summary, "هذه مادة تثقيفية عامة وليست فتوى. المسائل الخلافية أو الشخصية تُحال إلى عالم مؤهل ومصدر موثوق؛ لم يُنقل نص من الطبعة المشار إليها."],
            ["دين", category.lower(), title], source=ref_obj, safety="religious_reference"))
    make_pack(p, units, "21 بطاقة تعريفية أصلية مرتبطة بموضوعات الدروس القائمة ومراجع موضعية. لا نصوص قرآنية/حديثية منقولة ولا أحكام فتوى.")


def main() -> None:
    study_pack()
    money_pack()
    health_pack()
    facts_pack()
    code_snippets_pack()
    religion_pack()
    print("Wrote study, money, health, facts, code-snippets, religion packs (D31; unreviewed AI content tagged).")


if __name__ == "__main__":
    main()
