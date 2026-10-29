#!/usr/bin/env python3
"""توليد حزمة البرمجة والحياة اليومية للمرحلة 5 وفق قرار المالك D31.

يكتب مباشرة إلى packs/، ويعلّم المادة المولّدة `owner-waived-no-human-review`.
المراجع للاستدلال فقط؛ لا ينسخ نصوصها ولا يدعي مراجعة بشرية أو قانونية.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROGRAMMING = ROOT / "packs/seed-programming/pack.json"
DATE = "2026-10-29"
AUTHOR = "Zahra AI — مولّد بموافقة مالك المشروع وفق D31؛ دون مراجعة بشرية"

# Each example is a small executable reference and expected stdout/value, not a claim
# that the source code was run in an Android sandbox.
EXAMPLES = {
    "prog-closure": ("javascript", "const make = () => { let n = 0; return () => ++n; };\nconst next = make();\nconsole.log(next(), next());", "1 2"),
    "prog-var-let-const": ("javascript", "const name = 'Zahra';\nlet count = 1;\ncount += 1;\nconsole.log(name, count);", "Zahra 2"),
    "prog-api": ("http", "GET /users/7", "ردّ توضيحي: بيانات المستخدم 7 (شكل الاستجابة يعتمد على API)"),
    "prog-rest": ("http", "GET /users/7", "200 OK (مثال توضيحي؛ يتوقف على الخادم)"),
    "prog-git": ("shell", "git status --short", "يعرض الملفات المعدّلة (إن وُجدت)"),
    "prog-git-commit-push": ("shell", "git add app.py && git commit -m 'fix'", "إنشاء commit محلي؛ لا يرفع إلى remote"),
    "prog-sql-nosql": ("sql", "SELECT name FROM users WHERE active = 1;", "أسماء المستخدمين النشطين (بحسب البيانات)"),
    "prog-sql-index": ("sql", "CREATE INDEX idx_users_email ON users(email);", "إنشاء فهرس على email"),
    "prog-primary-key": ("sql", "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);", "جدول users مع id فريد"),
    "prog-http-status": ("http", "HTTP/1.1 200 OK", "200 OK"),
    "prog-css-center": ("css", ".parent { display: flex; justify-content: center; align-items: center; min-height: 100vh; }", "عنصر الابن يتوسط حاوية Flex على المحورين"),
    "prog-debounce": ("javascript", "let timer;\nfunction debounce(fn, ms) { return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); }; }", "تُنفذ fn مرة بعد ms من آخر استدعاء"),
    "prog-async-await": ("javascript", "async function value() { return 3; }\nvalue().then(console.log);", "3"),
    "prog-map-filter": ("javascript", "const xs = [1, 2, 3];\nconsole.log(xs.map(x => x * 2));\nconsole.log(xs.filter(x => x > 1));", "[2, 4, 6]\n[2, 3]"),
    "prog-oop-class": ("python", "class Box:\n    pass\n\nitem = Box()\nprint(type(item).__name__)", "Box"),
    "prog-python-list-tuple": ("python", "items = [1, 2]\nitems.append(3)\npoint = (4, 5)\nprint(items, point)", "[1, 2, 3] (4, 5)"),
    "prog-python-venv": ("shell", "python -m venv .venv", "ينشئ مجلد البيئة .venv (تفعيلها يختلف حسب نظام التشغيل)"),
    "prog-debug": ("python", "x = 2\nprint(x)", "2"),
    "prog-html-css-js": ("html", "<button id=\"go\">ابدأ</button>\n<script>document.querySelector('#go').onclick = () => alert('مرحبًا');</script>", "زر يعرض تنبيه مرحبًا عند النقر"),
    "prog-frontend-backend": ("http", "browser --request--> server --query--> database", "المتصفح يطلب؛ الخادم يعالج ويقرأ البيانات"),
    "prog-database": ("sql", "CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT);", "جدول notes بعمودين"),
    "prog-recursion": ("python", "def countdown(n):\n    if n == 0: return\n    print(n)\n    countdown(n - 1)\ncountdown(3)", "3\n2\n1"),
    "prog-big-o": ("python", "for item in items:  # n عناصر\n    process(item)", "زمن خطي O(n)"),
    "prog-json": ("json", "{\"name\": \"Mona\", \"active\": true}", "كائن JSON يحوي name وactive"),
    "prog-git-branch": ("shell", "git switch -c feature/login", "إنشاء فرع feature/login والانتقال إليه"),
}

# Exact prompts from the frozen coding evaluation and the competitor's documented
# failures are included as aliases only; eval/cases.jsonl remains untouched.
ALIASES = {
    "prog-api": ["ما هو الـ API؟", "يعني ايه API", "ما معنى واجهة برمجة التطبيقات؟", "what is an API", "API ya3ni eh"],
    "prog-var-let-const": ["إيه الفرق بين المتغير والثابت؟"],
    "prog-oop-class": ["إيه معنى البرمجة الكائنية OOP؟"],
    "prog-recursion": ["إيه هو الاستدعاء الذاتي recursion؟"],
    "prog-git": ["يعني إيه Git؟", "ما هو git", "what is git", "git ya3ni eh"],
    "prog-sql-nosql": ["إيه هو SQL؟", "ايه الفرق بين SQL و NoSQL", "إيه الفرق بين SQL و NoSQL؟"],
    "prog-http-status": ["إيه الفرق بين HTTP وHTTPS؟"],
    "prog-primary-key": ["إيه معنى المفتاح الأساسي؟"],
    "prog-python-list-tuple": ["إيه معنى mutable وimmutable؟"],
    "prog-html-css-js": ["اشرح الجافاسكريبت", "ايه هو HTML", "ما هو HTML؟"],
    "prog-css-center": ["ازاي اوسّط div في CSS", "how to center a div in CSS", "awsat div ezay"],
    "prog-debug": ["ازاي اصلح bug", "ما سبب SyntaxError في Python؟", "for i in range(10) print(i) — ليه مش شغال؟", "if x = 5: في بايثون — إيه الغلط؟", "طلع لي IndexError: list index out of range — إيه السبب؟", "طلع لي NameError: name 'x' is not defined — إيه الحل؟"],
    "prog-debounce": ["اكتبلي دالة debounce", "ما هي دالة debounce؟", "write a debounce function", "debounce function ezay"],
    "prog-database": ["ايه هي قاعدة البيانات", "ما هي قاعدة البيانات؟"],
    "prog-react": ["اشرح لي React", "ما هي React؟", "what is React", "react ya3ni eh"],
    "prog-python-read-file": ["اكتبلي كود بايثون يقرأ ملف", "اكتب كود بايثون يقرأ ملفًا نصيًا", "اكتب كود بايثون يقرأ محتوى ملف نصي", "read a text file in Python", "اقرأ فايل ببايثون"],
    "prog-python-add": ["اكتبلي دالة تجمع رقمين", "اكتب دالة بايثون تجمع رقمين", "write a Python function to add two numbers", "e3mel function tegma3 ra2meen"],
    "prog-python-loop": ["اكتب حلقة بايثون تطبع الأرقام من ١ إلى ١٠"],
    "prog-python-even": ["اكتب دالة تتحقق إذا كان الرقم زوجيًا"],
    "prog-sql-select": ["اكتب استعلام SQL يجيب كل المستخدمين من جدول users"],
    "prog-python-reverse": ["اكتب دالة تعكس نصًا في بايثون"],
    "prog-python-except": ["اكتب try/except للتعامل مع خطأ القسمة على صفر"],
    "prog-python-car": ["اكتب كلاس بسيط في بايثون باسم Car"],
    "prog-python-factorial": ["اكتب دالة تحسب مضروب عدد (factorial)"],
    "prog-python-json": ["إزاي تقرأ JSON في بايثون؟"],
    "prog-python-dict": ["إيه هو الديكشنري في بايثون؟"],
    "prog-loop": ["إيه هي الحلقة التكرارية (loop)؟", "اكتب حلقة بايثون تطبع الأرقام من ١ إلى ١٠"],
    "prog-http-https": ["ايه الفرق بين HTTP وHTTPS", "ما الفرق بين HTTP وHTTPS؟", "what is the difference between HTTP and HTTPS", "eh el far2 been http w https"],
    "prog-exception": ["إيه هو الاستثناء Exception؟"],
    "prog-binary-search": ["إزاي بيشتغل البحث الثنائي binary search؟"],
    "prog-stack-queue": ["إيه الفرق بين Stack وQueue؟"],
    "prog-compiler-interpreter": ["إيه الفرق بين compiler وinterpreter؟"],
    "prog-python-len": ["دالة len في بايثون بتعمل إيه؟"],
    "prog-python-syntax": ["الكود ده بيطلع خطأ SyntaxError: print('hi' — إيه المشكلة؟"],
    "prog-ai-learning": ["ازاي اتعلم الذكاء الاصطناعي"],
    "prog-site-roadmap": ["ازاي اعمل موقع"],
}

NEW_UNITS = [
    ("prog-loop", "ما هي الحلقة التكرارية (Loop)؟", "define_word", ["الحلقة تكرر كتلة أوامر وفق عدد مرات أو شرط، وتوقفها نقطة خروج واضحة.", "مثال Python يطبع من 1 إلى 10:\n```python\nfor i in range(1, 11):\n    print(i)\n```", "الناتج: الأعداد 1 إلى 10 كل رقم بسطر. خطأ شائع: استخدام range(1, 10) وتوقع ظهور 10؛ النهاية غير مشمولة."], ["python", "for i in range(1, 11):\n    print(i)", "1\n2\n3\n4\n5\n6\n7\n8\n9\n10"], ["loop", "for", "حلقة", "تكرار"]),
    ("prog-python-dict", "ما هو القاموس (dict) في Python؟", "define_word", ["القاموس بنية تخزن أزواج مفتاح وقيمة؛ الوصول يكون بالمفتاح لا برقم الموضع.", "مثال:\n```python\nuser = {\"name\": \"Mona\", \"age\": 20}\nprint(user[\"name\"])\n```", "الناتج: Mona. خطأ شائع: طلب مفتاح غير موجود مباشرة؛ استخدم get أو تحقق من وجود المفتاح."], ["python", "user = {\"name\": \"Mona\", \"age\": 20}\nprint(user[\"name\"])", "Mona"], ["python", "dict", "قاموس", "key", "value"]),
    ("prog-exception", "ما هو الاستثناء (Exception)؟", "define_word", ["الاستثناء إشارة وقت التشغيل إلى أن العملية لم تكتمل بالطريقة المعتادة؛ يمكن التعامل مع نوع متوقع منه.", "مثال:\n```python\ntry:\n    value = int(\"x\")\nexcept ValueError:\n    print(\"قيمة غير رقمية\")\n```", "الناتج: قيمة غير رقمية. خطأ شائع: except عام يخفي أخطاء غير متوقعة بدل معالجة الاستثناء المحدد."], ["python", "try:\n    value = int(\"x\")\nexcept ValueError:\n    print(\"قيمة غير رقمية\")", "قيمة غير رقمية"], ["python", "exception", "استثناء", "خطأ"]),
    ("prog-http-https", "ما الفرق بين HTTP وHTTPS؟", "define_word", ["HTTP بروتوكول طلب واستجابة للويب. HTTPS هو HTTP فوق اتصال TLS، فيحمي النقل ويتيح التحقق من هوية الخادم بالشهادة.", "مثال عنوان:\n```text\nhttps://example.com/\n```", "المتصفح يتفاوض على TLS قبل تبادل طلب HTTP. وجود HTTPS لا يثبت أن محتوى الموقع صادق أو آمن من كل تهديد."], ["text", "https://example.com/", "HTTPS scheme"], ["http", "https", "TLS", "شبكة"]),
    ("prog-binary-search", "كيف يعمل البحث الثنائي؟", "define_word", ["البحث الثنائي يجد قيمة في قائمة مرتبة بتقسيم مجال البحث إلى النصف كل مرة؛ زمنه O(log n).", "مثال Python:\n```python\ndef binary_search(a, target):\n    lo, hi = 0, len(a) - 1\n    while lo <= hi:\n        mid = (lo + hi) // 2\n        if a[mid] == target: return mid\n        if a[mid] < target: lo = mid + 1\n        else: hi = mid - 1\n    return -1\nprint(binary_search([1, 4, 7, 9], 7))\n```", "الناتج: 2. خطأ شائع: تشغيله على بيانات غير مرتبة أو تحديث الحد إلى mid بدل mid±1."], ["python", "def binary_search(a, target):\n    lo, hi = 0, len(a) - 1\n    while lo <= hi:\n        mid = (lo + hi) // 2\n        if a[mid] == target: return mid\n        if a[mid] < target: lo = mid + 1\n        else: hi = mid - 1\n    return -1\nprint(binary_search([1, 4, 7, 9], 7))", "2"], ["binary search", "بحث ثنائي", "خوارزمية", "ترتيب"]),
    ("prog-stack-queue", "ما الفرق بين Stack وQueue؟", "define_word", ["Stack يعمل بمبدأ آخر داخل أول خارج (LIFO)، أما Queue فيعمل بأول داخل أول خارج (FIFO).", "مثال Python:\n```python\nstack = [1, 2]; stack.append(3); print(stack.pop())\nfrom collections import deque\nq = deque([1, 2]); q.append(3); print(q.popleft())\n```", "الناتج: 3 ثم 1. خطأ شائع: الخلط بين pop من نهاية القائمة وpopleft من بداية الطابور."], ["python", "stack = [1, 2]\nstack.append(3)\nprint(stack.pop())\nfrom collections import deque\nq = deque([1, 2])\nq.append(3)\nprint(q.popleft())", "3\n1"], ["stack", "queue", "LIFO", "FIFO", "مكدس", "طابور"]),
    ("prog-compiler-interpreter", "ما الفرق بين Compiler وInterpreter؟", "define_word", ["الـ compiler يحول البرنامج أو أجزاء منه قبل تشغيلها إلى صيغة أخرى؛ والـ interpreter ينفذ التعليمات عبر بيئة تشغيل.", "مثال توضيحي:\n```text\nsource code → compiler → executable\nsource code → interpreter → execution\n```", "الواقع قد يجمع ترجمة مسبقة ووقت تشغيل افتراضيًا (مثل JVM)، لذلك لا ينطبق الفصل الثنائي على كل لغة."], ["text", "source code -> compiler -> executable\nsource code -> interpreter -> execution", "مساران توضيحيان؛ التنفيذ يختلف باختلاف اللغة"], ["compiler", "interpreter", "مترجم", "مفسر"]),
    ("prog-python-len", "ماذا تفعل الدالة len في Python؟", "define_word", ["len(x) تعيد عدد عناصر كائن له طول، مثل عناصر قائمة أو عدد محارف النص وفق وحدات Unicode في Python.", "مثال:\n```python\nprint(len([4, 5, 6]))\nprint(len(\"cat\"))\n```", "الناتج: 3 ثم 3. خطأ شائع: توقع عدد البايتات؛ للنص المرمز استخدم len(text.encode('utf-8'))."], ["python", "print(len([4, 5, 6]))\nprint(len(\"cat\"))", "3\n3"], ["python", "len", "طول", "قائمة", "نص"]),
    ("prog-python-add", "دالة Python لجمع رقمين", "request_code", ["الدالة تستقبل قيمتين وتعيد مجموعهما؛ لا تطبع النتيجة بدل إرجاعها إذا كان المطلوب إعادة قيمة.", "```python\ndef add(a, b):\n    return a + b\nprint(add(2, 3))\n```", "الناتج: 5. خطأ شائع: استخدام نصوص بدل أرقام؛ حوّل المدخلات إلى int أو float عند الحاجة."], ["python", "def add(a, b):\n    return a + b\nprint(add(2, 3))", "5"], ["python", "جمع", "دالة", "function", "addition"]),
    ("prog-python-even", "التحقق من زوجية عدد في Python", "request_code", ["العدد زوجي إذا كان باقي قسمته على 2 يساوي صفرًا؛ ويعمل العامل % مع الأعداد الصحيحة.", "```python\ndef is_even(n):\n    return n % 2 == 0\nprint(is_even(8))\n```", "الناتج: True. خطأ شائع: اختبار القسمة العشرية بدل باقي القسمة."], ["python", "def is_even(n):\n    return n % 2 == 0\nprint(is_even(8))", "True"], ["python", "زوجي", "even", "modulo"]),
    ("prog-python-read-file", "قراءة ملف نصي في Python", "request_code", ["افتح الملف داخل with لتضمن إغلاقه بعد القراءة؛ حدد الترميز إذا كان الملف نصًا عربيًا.", "```python\nfrom pathlib import Path\ntext = Path(\"notes.txt\").read_text(encoding=\"utf-8\")\nprint(text)\n```", "الناتج هو محتوى notes.txt سطرًا بسطر؛ يلزم وجود الملف. خطأ شائع: افتراض المسار الحالي أو ترميز النظام."], ["python", "from pathlib import Path\ntext = Path(\"notes.txt\").read_text(encoding=\"utf-8\")\nprint(text)", "محتوى الملف notes.txt (إذا كان موجودًا)"], ["python", "قراءة", "ملف", "read text file"]),
    ("prog-sql-select", "استعلام SQL لجلب كل المستخدمين", "request_code", ["SELECT * يطلب كل الأعمدة من الصفوف المحددة؛ لا يستخدم إلا إذا كنت تحتاج فعلًا كل الأعمدة.", "```sql\nSELECT * FROM users;\n```", "يعيد كل صفوف جدول users. خطأ شائع: نسيان شرط WHERE عندما تريد مجموعة فرعية."], ["sql", "SELECT * FROM users;", "كل صفوف جدول users (حسب بيانات قاعدة البيانات)"], ["sql", "SELECT", "users", "استعلام"]),
    ("prog-python-reverse", "عكس نص في Python", "request_code", ["التقطيع text[::-1] يعيد نسخة معكوسة من النص من منظور ترتيب نقاط Unicode في Python.", "```python\nprint(\"zahra\"[::-1])\n```", "الناتج: arhaz. قد لا يعكس هذا الحروف المركبة/الرموز التعبيرية كوحدات مرئية كاملة."], ["python", "print(\"zahra\"[::-1])", "arhaz"], ["python", "عكس", "نص", "reverse string"]),
    ("prog-python-except", "معالجة القسمة على صفر في Python", "request_code", ["التقط ZeroDivisionError حول العملية المحتمل فشلها، وأعد رسالة مفهومة بدل تجاهل الخطأ.", "```python\ntry:\n    print(10 / 0)\nexcept ZeroDivisionError:\n    print(\"لا يمكن القسمة على صفر\")\n```", "الناتج: لا يمكن القسمة على صفر. خطأ شائع: except: بلا نوع أو إخفاء الخطأ بصمت."], ["python", "try:\n    print(10 / 0)\nexcept ZeroDivisionError:\n    print(\"لا يمكن القسمة على صفر\")", "لا يمكن القسمة على صفر"], ["python", "try", "except", "قسمة", "خطأ"]),
    ("prog-python-car", "إنشاء Class باسم Car في Python", "request_code", ["class يعرّف نوعًا؛ __init__ يهيّئ خصائص كل نسخة، وself تشير للنسخة الحالية.", "```python\nclass Car:\n    def __init__(self, brand):\n        self.brand = brand\ncar = Car(\"Nile\")\nprint(car.brand)\n```", "الناتج: Nile. خطأ شائع: حذف self من تعريف method عادية."], ["python", "class Car:\n    def __init__(self, brand):\n        self.brand = brand\ncar = Car(\"Nile\")\nprint(car.brand)", "Nile"], ["python", "class", "Car", "كائن", "فئة"]),
    ("prog-python-factorial", "حساب المضروب (factorial) في Python", "request_code", ["مضروب n هو حاصل ضرب الأعداد الصحيحة من 1 إلى n، وتعريف 0! يساوي 1.", "```python\ndef factorial(n):\n    if n < 0: raise ValueError(\"n must be non-negative\")\n    result = 1\n    for k in range(2, n + 1): result *= k\n    return result\nprint(factorial(5))\n```", "الناتج: 120. خطأ شائع: نسيان حالة الصفر أو قبول عدد سالب."], ["python", "def factorial(n):\n    if n < 0: raise ValueError(\"n must be non-negative\")\n    result = 1\n    for k in range(2, n + 1): result *= k\n    return result\nprint(factorial(5))", "120"], ["python", "factorial", "مضروب", "loop"]),
    ("prog-python-json", "قراءة JSON في Python", "request_code", ["مكتبة json القياسية تحوّل JSON إلى أنواع Python؛ json.loads للنص وjson.load لملف مفتوح.", "```python\nimport json\nobj = json.loads('{\"name\": \"Mona\"}')\nprint(obj[\"name\"])\n```", "الناتج: Mona. خطأ شائع: استخدام eval بدل json.loads على مدخل غير موثوق."], ["python", "import json\nobj = json.loads('{\"name\": \"Mona\"}')\nprint(obj[\"name\"])", "Mona"], ["python", "json", "قراءة", "parse"]),
    ("prog-python-syntax", "تشخيص خطأ SyntaxError في Python", "request_code", ["SyntaxError يعني أن صياغة الشيفرة لا تطابق قواعد Python؛ افحص موضع المؤشر والأسطر القريبة.", "```python\nprint('hi')\n```", "الناتج: hi. في `print('hi'` ينقص قوس الإغلاق `)`؛ لا يكفي التخمين دون قراءة رسالة المفسر."], ["python", "print('hi')", "hi"], ["python", "SyntaxError", "syntax", "قوس", "debug"]),
    ("prog-react", "ما هي React؟", "define_word", ["React مكتبة JavaScript لبناء واجهات المستخدم من مكونات؛ يصف المطور الواجهة انطلاقًا من الحالة والخصائص.", "مثال مكوّن:\n```jsx\nfunction Greeting({ name }) {\n  return <h1>Hello, {name}</h1>;\n}\n```", "الاستخدام: تقسيم صفحة إلى مكونات قابلة لإعادة الاستخدام. خطأ شائع: تعديل state مباشرة بدل setter المخصص مثل useState."], ["jsx", "function Greeting({ name }) { return <h1>Hello, {name}</h1>; }", "المكوّن يعرض Hello, Mona عند تمرير name=\"Mona\""], ["react", "javascript", "واجهة", "component", "مكوّن"]),
    ("prog-ai-learning", "مسار عملي لتعلم الذكاء الاصطناعي", "query_knowledge", ["ابدأ بأساسيات Python والاحتمالات والجبر الخطي، ثم تعلم معالجة البيانات وتقييم نموذج بسيط قبل الانتقال إلى الشبكات العصبية.", "خطة مشروع صغير:\n```text\n1) حمّل بيانات عامة مرخصة\n2) افصل train/test\n3) درّب baseline\n4) قِس على test لم يُستخدم في الضبط\n```", "مثال أول: مصنف يفرز رسائلًا إلى فئتين باستخدام بيانات تعليمية مرخصة. لا تبدأ بنسخ بيانات أشخاص أو نشر نموذج قبل فهم الترخيص والخصوصية."], ["text", "train -> baseline -> held-out evaluation", "تقييم مستقل بعد ضبط النموذج"], ["ذكاء اصطناعي", "تعلم", "Python", "machine learning"]),
    ("prog-site-roadmap", "كيف أبدأ إنشاء موقع ويب؟", "query_knowledge", ["ابنِ صفحة صغيرة: HTML للمحتوى، CSS للتنسيق، JavaScript للتفاعل؛ ثم اختبرها في المتصفح وأضف احتياجات الخادم عند الحاجة.", "ملف HTML بسيط:\n```html\n<!doctype html>\n<html lang=\"ar\"><meta charset=\"utf-8\"><title>أول صفحة</title>\n<body><h1>مرحبًا</h1></body></html>\n```", "افتح الملف في متصفح محلي. خطأ شائع: إدخال كلمات مرور أو مفاتيح سرية في JavaScript الذي يصل إلى المتصفح."], ["html", "<!doctype html>\n<html lang=\"ar\"><meta charset=\"utf-8\"><title>أول صفحة</title>\n<body><h1>مرحبًا</h1></body></html>", "صفحة تعرض عنوان مرحبًا"], ["html", "css", "javascript", "موقع", "ويب"]),
]


def qset(unit: dict) -> list[str]:
    return unit.get("q", unit.get("questions", []))


def main() -> None:
    pack = json.loads(PROGRAMMING.read_text(encoding="utf-8"))
    units = {u["id"]: u for u in pack["units"]}
    for unit_id, prompts in ALIASES.items():
        if unit_id not in units:
            continue
        questions = qset(units[unit_id])
        if unit_id == "prog-html-css-js":
            questions[:] = [q for q in questions if q != "ازاي اعمل موقع"]
        for prompt in prompts:
            if prompt not in questions:
                questions.append(prompt)
        units[unit_id]["q"] = questions
        units[unit_id].pop("questions", None)
    for unit_id, (lang, code, expected) in EXAMPLES.items():
        if unit_id in units:
            units[unit_id]["examples"] = [{"lang": lang, "code": code, "expected": expected, "generatedBy": "Zahra AI / Arena", "reviewStatus": "owner-waived-no-human-review"}]
            units[unit_id]["examplesGeneratedBy"] = "Zahra AI / Arena"
            units[unit_id]["examplesReviewStatus"] = "owner-waived-no-human-review"
    sql_nosql = units.get("prog-sql-nosql")
    if sql_nosql:
        answers = sql_nosql.get("a", sql_nosql.get("answers", []))
        code_answer = "مثال SQL:\n`SELECT name FROM users WHERE active = 1;` — يسترجع أسماء الصفوف المطابقة."
        if not any("SELECT name FROM users WHERE active = 1" in answer for answer in answers):
            answers.append(code_answer)
        sql_nosql["a"] = answers
        sql_nosql.pop("answers", None)
    for unit_id, title, intent, answers, example, tags in NEW_UNITS:
        lang, code, expected = example
        questions = list(ALIASES[unit_id])
        topic = tags[0]
        for variant in (f"اشرح {title.rstrip('؟')}", f"ما المقصود بـ {title.rstrip('؟')}؟", f"what is {topic}", f"eih {topic} ya3ni"):
            if variant not in questions:
                questions.append(variant)
        source_info = None if unit_id == "prog-ai-learning" else {
            "name": "Python Documentation — reference only" if "python" in lang else "MDN Web Docs — reference only",
            "url": ("https://docs.python.org/3/tutorial/controlflow.html#for-statements" if unit_id == "prog-loop" else "https://docs.python.org/3/") if "python" in lang else ("https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview" if unit_id == "prog-http-https" else "https://developer.mozilla.org/en-US/docs/Learn_web_development" if unit_id == "prog-site-roadmap" else "https://developer.mozilla.org/"),
            "license": "PSF-2.0; reference only, no text copied" if "python" in lang else "CC-BY-SA-2.5-or-later; reference only, no text copied",
            "location": "Official documentation; generated explanation is not copied",
        }
        units[unit_id] = {
            "id": unit_id, "pack": pack["pack"], "title": title, "intent": intent,
            "tags": tags, "source": source_info,
            "authored": True, "author": AUTHOR, "generatedBy": "Zahra AI / Arena", "reviewStatus": "owner-waived-no-human-review", "contentLicense": "CC-BY-SA-4.0", "verifiedAt": DATE, "verifiedBy": "automatic schema/content-shape date only; facts not checked", "safety": "none", "weight": 1.0,
            "q": questions, "a": answers,
            "examples": [{"lang": lang, "code": code, "expected": expected, "generatedBy": "Zahra AI / Arena", "reviewStatus": "owner-waived-no-human-review"}],
        }
    pack["version"] = "1.1.0"
    pack["license"] = "mixed-see-unit-sources-and-contentLicense"
    pack["generatedBy"] = "Zahra AI / Arena"
    pack["reviewStatus"] = "owner-waived-no-human-review"
    pack["authors"] = sorted(set(pack.get("authors", []) + [AUTHOR]))
    pack["generatedAt"] = DATE
    pack["note"] = "محتوى المرحلة 5 يتضمن صياغات وأمثلة مولّدة آليًا وفق D31؛ لا توجد مراجعة بشرية. راجع contentLicense ومصدر كل وحدة. الشيفرات أمثلة تعليمية ساكنة وليست معزولة للتنفيذ داخل التطبيق."
    pack["units"] = list(units.values())
    PROGRAMMING.write_text(json.dumps(pack, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    daily_path = ROOT / "packs/daily-life/pack.json"
    daily_path.parent.mkdir(parents=True, exist_ok=True)
    daily = {
        "pack": "daily-life", "version": "1.0.0", "license": "CC-BY-SA-4.0",
        "generatedBy": "Zahra AI / Arena", "reviewStatus": "owner-waived-no-human-review",
        "authors": [AUTHOR], "sources": [], "generatedAt": DATE,
        "note": "محتوى مولّد آليًا بموافقة المالك وفق D31 ودون مراجعة بشرية؛ ترخيص المحتوى الأصلي CC BY-SA 4.0. ليس تشخيصًا نفسيًا ولا يدعي ذاكرة أو مشاعر بشرية.", "units": []
    }
    mood_units = [
        ("daily-study-stuck", "دعم عند صعوبة البدء بالمذاكرة", "مش قادر اذاكر", ["أفهم إن البداية ممكن تبقى تقيلة. اختار أصغر خطوة: افتح صفحة واحدة واضبط مؤقتًا لخمس دقائق، وبعدها قرر تكمل أو ترتاح.", "لو السبب إرهاق أو ضغط مستمر، احكي لشخص تثق به؛ مش لازم تحل كل شيء مرة واحدة."], ["مش قادر اذاكر", "مش قادرة أذاكر", "كيف أبدأ الدراسة وأنا متعب؟", "I can't get myself to study", "mesh 2ader azaker"], ["مذاكرة", "دعم", "خطوة صغيرة"]),
        ("daily-lonely", "مساندة عند الشعور بالوحدة", "حاسس اني وحيد", ["الإحساس بالوحدة مؤلم، ومش لازم تبرره. هل تحب تحكي إيه اللي خلاه أقوى النهارده؟ ممكن كمان تبعت رسالة بسيطة لشخص ترتاح له.", "أنا مساعد رقمي ولست بديلًا عن شخص قريب أو مختص. لو كنت في خطر فوري أو تفكر في إيذاء نفسك، تواصل الآن مع الطوارئ المحلية أو شخص بالغ تثق به."], ["حاسس اني وحيد", "حاسّة إني وحيدة", "أشعر بالوحدة، ماذا أفعل؟", "I feel lonely", "7ases eny wa7eed"], ["وحدة", "مشاعر", "دعم"]),
        ("daily-friend-argument", "مساندة بعد خلاف مع صديق", "اتخانقت مع صاحبي", ["واضح إن الخلاف مضايقك. خُد وقت تهدأ، وبعدها جرّب تقول له: أنا اتضايقت لما حصل كذا، وعايز أفهم وجهة نظرك. هل تحب نرتب كلامك قبل ما تبعته؟", "ما أعرفش تفاصيل الموقف ولا أفترض مين الغلطان؛ لو فيه تهديد أو أذى، خلّي سلامتك أولًا واستعن بشخص موثوق."], ["اتخانقت مع صاحبي", "اتخانقت مع صاحبتي", "كيف أتصرف بعد شجار مع صديق؟", "I argued with my friend", "et5ana2t ma3 sa7by"], ["صداقة", "خلاف", "تواصل"]),
        ("daily-happy", "مشاركة شعور الفرح", "انا فرحان النهارده", ["جميل إن يومك فيه حاجة مفرحة! إيه أكتر شيء فرّحك النهارده؟ لو تحب، احتفل بيه بطريقتك أو شارك الخبر مع شخص بتحبه.", "مبروك عليك اللحظة الحلوة؛ أنا مساعد رقمي وما عنديش مشاعر بشرية، لكن أقدر أسمعك وأساعدك تصيغ الخبر."], ["انا فرحان النهارده", "أنا فرحانة اليوم", "أشعر بالسعادة اليوم", "I am happy today", "ana far7an elnaharda"], ["فرح", "مشاعر", "احتفال"]),
        ("daily-bored", "أفكار بسيطة عند الملل", "انا زهقان", ["الزهق ساعات يكون إشارة إنك محتاج تغيير بسيط. اختار حاجة لعشر دقايق: تمشية قصيرة، أغنية، ترتيب ركن صغير، أو مكالمة مع حد بتحبه. تحب نشاط هادي ولا فيه حركة؟", "لو الملل مستمر ومعاه ضيق شديد أو فقدان رغبة لفترة، شارك ده مع شخص تثق به أو مختص."], ["انا زهقان", "أنا زهقانة", "أشعر بالملل، اقترح شيئًا", "I am bored", "ana zah2an"], ["ملل", "مشاعر", "نشاط"]),
        ("daily-love-question", "الرد الصادق على سؤال الحب", "هل تحبني", ["أنا برنامج مساعد، لذلك ما عنديش مشاعر حب بشرية أو علاقة شخصية. لكن أقدر أتعامل معك باحترام وأسمع اللي حابب تقوله. هل سؤالك بدافع فضول، ولا محتاج دعم دلوقتي؟"], ["هل تحبني", "بتحبني؟", "هل تملك مشاعر حب؟", "Do you love me?", "bet7ebny"], ["هوية", "مشاعر", "صدق"]),
    ]
    for unit_id, title, question, answers, questions, tags in mood_units:
        daily["units"].append({
            "id": unit_id, "pack": "daily-life", "title": title, "intent": "small_talk",
            "tags": tags, "authored": True, "author": AUTHOR, "generatedBy": "Zahra AI / Arena",
            "reviewStatus": "owner-waived-no-human-review", "contentLicense": "CC-BY-SA-4.0",
            "verifiedAt": DATE, "verifiedBy": "automatic schema/content-shape date only; facts not checked",
            "safety": "none", "weight": 1.0, "q": questions, "a": answers,
        })
    daily_path.write_text(json.dumps(daily, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
