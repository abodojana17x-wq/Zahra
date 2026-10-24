# Zahra Phase 6 - Developer Guide
## Building, Testing, and Extending Zahra's Conversational Intelligence

---

## 1. OVERVIEW

This guide provides comprehensive instructions for developers working on Zahra's Phase 6 conversational intelligence transformation. It covers:

- Setting up the development environment
- Understanding the architecture
- Running and testing the application
- Extending the system
- Debugging and troubleshooting
- Best practices and conventions

---

## 2. QUICK START

### Prerequisites

| Requirement | Version | Installation |
|-------------|---------|-------------|
| Java | JDK 17 | [OpenJDK](https://adoptium.net/) |
| Android SDK | API 36 | Android Studio |
| Gradle | 9.3.1 | Comes with project |
| Kotlin | 2.2.10 | Comes with project |
| Git | Latest | [Git](https://git-scm.com/) |

### Setup Instructions

```bash
# 1. Clone the repository
git clone https://github.com/abodojana17x-wq/Zahra.git
cd Zahra

# 2. Install dependencies (handled by Gradle)
# No manual installation needed

# 3. Open in Android Studio
# File > Open > Select the Zahra directory

# 4. Sync Gradle
# Android Studio will prompt to sync, or:
./gradlew --refresh-dependencies

# 5. Build the project
./gradlew :app:assembleDebug
```

### Run the Application

```bash
# Install on connected device
./gradlew :app:installDebug

# Or run in emulator
# First, create an emulator in Android Studio
# Then:
./gradlew :app:installDebug
./gradlew :app:run
```

---

## 3. PROJECT STRUCTURE

```
Zahra/
├── app/                          # Main application
│   └── src/
│       ├── main/                 # Production code
│       │   ├── java/com/example/zahra/
│       │   │   ├── personality/          # NEW: Personality System
│       │   │   │   ├── PersonalityManager.kt      # Personality management
│       │   │   │   ├── PersonalityPreferences.kt    # Preference data model
│       │   │   │   ├── PersonalityStorage.kt        # Persistence
│       │   │   │   └── StyleApplier.kt              # Style application
│       │   │   │
│       │   │   ├── context/              # NEW: Context System
│       │   │   │   └── ConversationContext.kt      # Conversation state
│       │   │   │
│       │   │   ├── response/             # NEW: Response System
│       │   │   │   ├── ResponseBuilder.kt           # Response orchestration
│       │   │   │   ├── TemplateSelector.kt          # Template selection
│       │   │   │   ├── TemplateExpander.kt          # Template expansion
│       │   │   │   ├── ResponseRanker.kt            # Response ranking
│       │   │   │   └── ResponseValidator.kt         # Response validation
│       │   │   │
│       │   │   ├── islamic/              # NEW: Islamic Grounding
│       │   │   │   └── IslamicKnowledgeEngine.kt    # Islamic knowledge
│       │   │   │
│       │   │   ├── engines/              # Existing: Core engines
│       │   │   │   ├── ZahraRouter.kt               # MODIFIED: Main router
│       │   │   │   ├── MathEngine.kt                # Mathematics
│       │   │   │   ├── ScheduleEngine.kt            # Scheduling
│       │   │   │   ├── MemoryEngine.kt              # Memory
│       │   │   │   ├── TaskEngine.kt                # Tasks
│       │   │   │   ├── NoteEngine.kt                # Notes
│       │   │   │   ├── TranslationEngine.kt         # Translation
│       │   │   │   ├── ReligionEngine.kt             # Religion
│       │   │   │   └── LocalImageEngine.kt          # Images
│       │   │   │
│       │   │   ├── knowledge/            # Existing: Knowledge
│       │   │   │   ├── engine/
│       │   │   │   │   └── KnowledgeEngine.kt
│       │   │   │   └── model/
│       │   │   │
│       │   │   ├── reasoning/             # Existing: Reasoning
│       │   │   ├── understanding/         # Existing: Understanding
│       │   │   ├── intelligence/          # Existing: Intelligence
│       │   │   ├── core/                  # Existing: Core utilities
│       │   │   ├── entities/              # Existing: Entity extraction
│       │   │   ├── intent/                # Existing: Intent classification
│       │   │   ├── execution/             # Existing: Execution
│       │   │   └── model/                 # Existing: Data models
│       │   │
│       │   └── res/                      # Resources
│       │       ├── values/               # Strings, colors, etc.
│       │       ├── layout/               # UI layouts
│       │       └── assets/               # Asset files
│       │           ├── intent_corpus.json
│       │           ├── general_kb.json
│       │           ├── religion_kb.json
│       │           └── translation_kb.json
│       │
│       └── test/                    # Test code
│           └── java/com/example/
│               ├── personality/          # NEW: Personality tests
│               │   └── PersonalitySystemTest.kt
│               │
│               ├── context/              # NEW: Context tests
│               │   └── ContextManagerTest.kt
│               │
│               ├── zahra/                # NEW: Response & Islamic tests
│               │   ├── response/
│               │   │   └── ResponseSystemTest.kt
│               │   └── islamic/
│               │       └── IslamicKnowledgeEngineTest.kt
│               │
│               └── [Existing test files] # 178 existing tests
│
├── gradle/                       # Gradle configuration
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml         # Dependency versions
│
├── zahra-engine/                 # Legacy engine (if any)
│
├── docs/                         # Documentation
│   ├── ZAHRA_SPECIFICATION_V2.md
│   ├── PHASE_3_LOCAL_KNOWLEDGE_ENGINE.md
│   ├── PHASE_4_COMPLETION_REPORT.md
│   ├── PHASE_5_COMPLETION_REPORT.md
│   ├── AUDIT_REPORT_PHASE_6.md          # NEW
│   ├── TARGET_ARCHITECTURE_SPECIFICATION.md  # NEW
│   ├── BENCHMARK_SUITE_PHASE_6.md       # NEW
│   ├── TEST_REPORT_PHASE_6.md           # NEW
│   └── IMPLEMENTATION_PROGRESS_PHASE_6.md # NEW
│
├── build.gradle.kts              # Project build file
├── app/build.gradle.kts          # App build file
└── settings.gradle.kts           # Settings file
```

---

## 4. ARCHITECTURE OVERVIEW

### Phase 6 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Zahra Application                        │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                    ZahraRouter (Modified)                 │ │
│  │                                                           │ │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │ │
│  │  │ Personality  │  │  Context    │  │   Response      │  │ │
│  │  │ Manager      │  │  Manager    │  │   Builder       │  │ │
│  │  └─────────────┘  └─────────────┘  └─────────────────┘  │ │
│  │          ↑            ↑                  ↑              │ │
│  └─────────────────────────────────────────────────────────┘ │
│                          ↑    ↑          ↑                       │
│                          │    │          │                       │
│  ┌─────────────────┐ ┌───────────┐ ┌─────────────────┐  │
│  │ Personality     │ │ Context   │ │ Template        │  │
│  │ Preferences     │ │ State     │ │ Selector/       │  │
│  │                 │ │           │ │ Expander/       │  │
│  │                 │ │           │ │ Ranker/         │  │
│  │                 │ │           │ │ Validator       │  │
│  └─────────────────┘ └───────────┘ └─────────────────┘  │
│                                                          │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │                    Existing Engines                        │ │
│  │                                                           │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │ │
│  │  │ Math     │ │ Schedule │ │ Memory   │ │ Task     │    │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐    │ │
│  │  │ Religion │ │ Translate│ │ Note     │ │ Image    │    │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘    │ │
│  │  ┌─────────────────┐ ┌─────────────────┐               │ │
│  │  │ Knowledge Engine │ │ Understanding    │               │ │
│  │  └─────────────────┘ │ Reasoning        │               │ │
│  │                      └─────────────────┘               │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

```
User Input
     │
     ▼
┌─────────────────────┐
│ ZahraRouter.process()│
└──────────┬──────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│ Phase 6: Personality & Context Integration                │
│ 1. Check for personality change commands                  │
│ 2. Load current personality                              │
│ 3. Load conversation context                             │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│ Phase 4-5: Existing Processing                           │
│ - Dialect normalization                                   │
│ - Islamic integrity checks                                │
│ - Context and correction detection                        │
│ - Compound task planning                                  │
│ - Intent classification                                   │
│ - Entity extraction                                       │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│ Phase 6: Response Generation                             │
│ 1. Build response using ResponseBuilder                  │
│ 2. Apply personality styling                              │
│ 3. Validate response                                     │
│ 4. Record turn in context                                │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────┐
│ ZahraResponse         │
│ - text                │
│ - intent              │
│ - confidence          │
│ - confidenceTier      │
│ - outcomeState        │
│ - source              │
│ - evidenceTrace       │
│ - toolPayload         │
└─────────────────────┘
     │
     ▼
User Output
```

---

## 5. RUNNING AND TESTING

### Running the Application

#### In Android Studio

1. Open the project in Android Studio
2. Connect a device or start an emulator
3. Click the "Run" button (green triangle)
4. Select the `app` module and your device
5. Click "OK"

#### From Command Line

```bash
# Build and install
./gradlew :app:installDebug

# Run on connected device
./gradlew :app:run

# Or use adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Running Tests

#### Run All Tests

```bash
# Run all unit tests
./gradlew :app:testDebugUnitTest

# Run all tests including instrumented
./gradlew :app:test
```

#### Run Specific Tests

```bash
# Run personality tests
./gradlew :app:testDebugUnitTest --tests "com.example.personality.*"

# Run context tests
./gradlew :app:testDebugUnitTest --tests "com.example.context.*"

# Run response tests
./gradlew :app:testDebugUnitTest --tests "com.example.zahra.response.*"

# Run Islamic tests
./gradlew :app:testDebugUnitTest --tests "com.example.zahra.islamic.*"

# Run a single test
./gradlew :app:testDebugUnitTest --tests "com.example.personality.PersonalitySystemTest.testDefaultPersonality"
```

#### Run with Coverage

```bash
# Generate coverage report
./gradlew :app:testDebugUnitTestCoverage

# Open coverage report
# Located at: app/build/reports/coverage/debug/index.html
```

### Running Benchmarks

```bash
# Run all benchmarks (when implemented)
./gradlew :app:runBenchmarks

# Run specific benchmark
./gradlew :app:runBenchmark -Pbenchmark=IntentUnderstanding
```

---

## 6. DEVELOPING NEW FEATURES

### Adding a New Intent

1. **Add intent to corpus**:
   ```json
   // In app/src/main/assets/intent_corpus.json
   {
     "intents": {
       "existing_intent": [...],
       "new_intent": [
         "example phrase 1",
         "example phrase 2",
         "example phrase 3"
       ]
     }
   }
   ```

2. **Add intent handling in ZahraRouter**:
   ```kotlin
   // In ZahraRouter.kt, in the when statement
   "new_intent" -> {
       // Handle the new intent
       responseText = "Response for new intent"
       confidence = 0.95
       outcomeState = OutcomeState.SUPPORTED
   }
   ```

3. **Add templates (optional)**:
   ```kotlin
   // In TemplateSelector.kt
   "new_intent" -> listOf(
       ResponseTemplate(
           id = "new_intent_template_1",
           template = "Template response 1",
           intent = "new_intent",
           priority = 10
       ),
       ResponseTemplate(
           id = "new_intent_template_2",
           template = "Template response 2",
           intent = "new_intent",
           priority = 8
       )
   )
   ```

4. **Add tests**:
   ```kotlin
   // In a test file
   @Test
   fun testNewIntent() {
       val router = ZahraRouter(mockContext)
       val result = router.process("example phrase 1")
       assertEquals("new_intent", result.intent)
       assertEquals(OutcomeState.SUPPORTED, result.outcomeState)
   }
   ```

### Adding a New Personality Setting

1. **Add enum value**:
   ```kotlin
   // In PersonalityPreferences.kt
   enum class NewSetting {
       OPTION_1,
       OPTION_2,
       OPTION_3
   }
   ```

2. **Add to preferences data class**:
   ```kotlin
   // In PersonalityPreferences.kt
   data class PersonalityPreferences(
       // ... existing fields
       val newSetting: NewSetting = NewSetting.OPTION_1,
       // ...
   )
   ```

3. **Add commands**:
   ```kotlin
   // In PersonalityManager.kt
   private val PERSONALITY_COMMANDS = mapOf(
       // ... existing commands
       "use option 1" to PersonalityCommand.SetNewSetting(NewSetting.OPTION_1),
       "use option 2" to PersonalityCommand.SetNewSetting(NewSetting.OPTION_2),
       // ...
   )
   ```

4. **Add sealed class for command**:
   ```kotlin
   // In PersonalityManager.kt
   sealed class PersonalityCommand {
       // ... existing commands
       data class SetNewSetting(val setting: NewSetting) : PersonalityCommand()
   }
   ```

5. **Add handler**:
   ```kotlin
   // In PersonalityManager.kt
   fun handlePersonalityCommand(command: PersonalityCommand, ...) {
       when (command) {
           // ... existing cases
           is PersonalityCommand.SetNewSetting -> {
               setPersonality(currentPersonality.copy(
                   newSetting = command.setting
               ))
           }
       }
   }
   ```

6. **Add application logic**:
   ```kotlin
   // In StyleApplier.kt or ResponseBuilder.kt
   fun applyNewSetting(response: String, setting: NewSetting): String {
       return when (setting) {
           NewSetting.OPTION_1 -> response
           NewSetting.OPTION_2 -> response.uppercase()
           NewSetting.OPTION_3 -> "$response!"
       }
   }
   ```

7. **Add tests**:
   ```kotlin
   // In PersonalitySystemTest.kt
   @Test
   fun testNewSetting() {
       val prefs = PersonalityPreferences.getDefault().copy(
           newSetting = NewSetting.OPTION_2
       )
       val manager = PersonalityManager(mockContext)
       manager.setPersonality(prefs)
       
       val retrieved = manager.getCurrentPersonality()
       assertEquals(NewSetting.OPTION_2, retrieved.newSetting)
   }
   ```

### Adding a New Template

1. **Add template to selector**:
   ```kotlin
   // In TemplateSelector.kt
   "greet" -> listOf(
       // ... existing templates
       ResponseTemplate(
           id = "greet_new",
           template = "Hello {name}! Welcome to {app_name}.",
           intent = "greet",
           priority = 7,
           requiredEntities = listOf("name")
       )
   )
   ```

2. **Add slot extraction (if needed)**:
   ```kotlin
   // In TemplateExpander.kt
   override fun extractSlotsForTemplate(template: ResponseTemplate, ...) {
       val slots = super.extractSlotsForTemplate(template, entities, context)
       
       if (template.id == "greet_new") {
           slots["name"] = entities["user_name"]?.toString() ?: "there"
       }
       
       return slots
   }
   ```

---

## 7. DEBUGGING AND TROUBLESHOOTING

### Common Issues

#### Issue: Gson Not Found

**Symptom**: Compilation error about Gson not found

**Solution**:
```bash
# Check if gson is in libs.versions.toml
grep "gson" gradle/libs.versions.toml

# Should see: gson = "2.10.1"

# Check if gson is in app/build.gradle.kts
grep "gson" app/build.gradle.kts

# Should see: implementation(libs.gson)

# If missing, add them and sync
./gradlew --refresh-dependencies
```

#### Issue: Tests Don't Compile

**Symptom**: Compilation errors in test files

**Solution**:
```bash
# Run with detailed error output
./gradlew :app:compileDebugUnitTestSources --stacktrace

# Check for:
# - Missing imports
# - Type mismatches
# - API changes
# - Incorrect package names
```

#### Issue: Tests Fail

**Symptom**: Tests compile but fail at runtime

**Solution**:
```bash
# Run with info logging
./gradlew :app:testDebugUnitTest --info

# Run a specific failing test
./gradlew :app:testDebugUnitTest --tests "com.example.personality.PersonalitySystemTest.testDefaultPersonality" --info

# Check the test report
# Located at: app/build/reports/tests/testDebugUnitTest/index.html
```

#### Issue: App Crashes on Startup

**Symptom**: App crashes when launched

**Solution**:
```bash
# Check logcat for errors
adb logcat | grep -i "zahra\|error\|exception"

# Or in Android Studio:
# View > Tool Windows > Logcat
# Filter by your app package name

# Common causes:
# - Missing assets (intent_corpus.json, etc.)
# - Initialization errors in engines
# - Null pointer exceptions
```

#### Issue: Personality Not Persisting

**Symptom**: Personality changes don't persist across restarts

**Solution**:
```kotlin
# Check PersonalityStorage.kt
# Ensure savePersonality is called
# Check SharedPreferences configuration

# Verify in code:
val manager = PersonalityManager(context)
val prefs = manager.getCurrentPersonality()
manager.setPersonality(prefs.copy(tone = CommunicationTone.PLAYFUL))

# Restart app and check if personality persisted
```

#### Issue: Context Not Maintained

**Symptom**: Conversation context not maintained across turns

**Solution**:
```kotlin
# Check ContextManager.kt
# Ensure recordTurn is called in ZahraRouter

# Verify in code:
val manager = ContextManager()
manager.recordTurn("Hello", "hello", Sender.USER)
val context = manager.getCurrentConversation()

# Check if turn was recorded
assertEquals(1, context.turns.size)
```

### Debug Tools

#### Logging

```kotlin
// Add logging to any class
import android.util.Log

class MyClass {
    companion object {
        private const val TAG = "MyClass"
    }
    
    fun myMethod() {
        Log.d(TAG, "myMethod called")
        // ...
        Log.i(TAG, "Information message")
        Log.w(TAG, "Warning message")
        Log.e(TAG, "Error message")
    }
}

# View logs
adb logcat | grep -i "MyClass"
```

#### Breakpoints

In Android Studio:
1. Set breakpoints in code by clicking in the gutter
2. Run the app in debug mode
3. When breakpoint is hit, inspect variables
4. Step through code execution

#### Inspection

```kotlin
// Inspect objects in debug mode
val myObject = MyClass()
println("My object: $myObject")

// Or use Log
Log.d(TAG, "My object: $myObject")
```

---

## 8. BEST PRACTICES

### Code Style

1. **Follow existing conventions**:
   - Use camelCase for variables and functions
   - Use PascalCase for classes and enums
   - Use snake_case for constants
   - Use Kotlin idioms (val, when, etc.)

2. **Documentation**:
   - Add KDoc comments for public functions
   - Document complex logic
   - Document invariants and assumptions

3. **Error Handling**:
   - Use try-catch for operations that can fail
   - Return appropriate error states
   - Don't crash the app

### Testing

1. **Test Structure**:
   - One test class per component
   - One test method per scenario
   - Use descriptive test names

2. **Test Coverage**:
   - Test happy path
   - Test edge cases
   - Test error cases
   - Test invariants

3. **Test Quality**:
   - Tests should be deterministic
   - Tests should be fast
   - Tests should be isolated
   - Tests should be maintainable

### Performance

1. **Avoid blocking operations**:
   - Don't do heavy computation on main thread
   - Use coroutines for async operations

2. **Cache appropriately**:
   - Cache expensive operations
   - Don't cache what changes frequently

3. **Memory management**:
   - Clean up resources
   - Avoid memory leaks
   - Use appropriate data structures

### Security

1. **Data validation**:
   - Validate all inputs
   - Sanitize user data
   - Check permissions

2. **Storage**:
   - Use appropriate storage for sensitivity
   - Encrypt sensitive data
   - Use SharedPreferences for preferences

3. **Network**:
   - No network operations in Phase 6
   - All operations are offline

---

## 9. COMPONENT REFERENCE

### Personality System

| Component | Purpose | Key Methods |
|-----------|---------|-------------|
| `PersonalityPreferences` | Data model for preferences | getDefault(), copy() |
| `PersonalityManager` | Manage personality settings | getCurrentPersonality(), setPersonality(), detectPersonalityChange() |
| `PersonalityStorage` | Persist personality settings | savePersonality(), loadPersonality() |
| `StyleApplier` | Apply personality to responses | applyEmojiFrequency(), applyTone(), applyStyle() |

**Usage Example**:
```kotlin
val manager = PersonalityManager(context)

// Get current personality
val personality = manager.getCurrentPersonality()

// Change personality
manager.setPersonality(personality.copy(
    tone = CommunicationTone.FRIENDLY_WARM
))

// Detect command
val command = manager.detectPersonalityChange("Be more serious")
if (command != null) {
    manager.applyPersonalityCommand(command, "Be more serious")
}

// Apply to response
val styledResponse = StyleApplier.applyPersonalityToResponse(
    "Hello!", 
    personality
)
```

### Context System

| Component | Purpose | Key Methods |
|-----------|---------|-------------|
| `ConversationContext` | Data classes for conversation | ConversationTurn, ConversationState, EntityInfo |
| `ContextManager` | Manage conversation context | getCurrentConversation(), recordTurn(), resolveReference() |

**Usage Example**:
```kotlin
val manager = ContextManager()

// Record a turn
manager.recordTurn(
    text = "Hello",
    normalizedText = "hello",
    speaker = Sender.USER,
    intent = "greet"
)

// Get conversation
val conversation = manager.getCurrentConversation()

// Resolve reference
val entity = manager.resolveReference("it")
```

### Response System

| Component | Purpose | Key Methods |
|-----------|---------|-------------|
| `ResponseBuilder` | Orchestrate response generation | buildResponse(), tryBuildResponse() |
| `TemplateSelector` | Select appropriate templates | selectTemplates() |
| `TemplateExpander` | Expand templates with slots | expandTemplate(), extractSlotsForTemplate() |
| `ResponseRanker` | Rank response candidates | rankTemplates(), rankKnowledgeRecords() |
| `ResponseValidator` | Validate responses | validate() |

**Usage Example**:
```kotlin
val builder = ResponseBuilder(
    personalityManager = PersonalityManager(context),
    contextManager = ContextManager(),
    knowledgeEngine = KnowledgeEngine(context)
)

val response = builder.buildResponse(
    input = "Hello",
    intent = "greet",
    confidence = 0.95
)
```

### Islamic Grounding

| Component | Purpose | Key Methods |
|-----------|---------|-------------|
| `IslamicKnowledgeEngine` | Islamic knowledge with source hierarchy | queryIslamicKnowledge(), determineSourceLevel() |
| `IslamicResponse` | Data class for Islamic responses | All fields |

**Usage Example**:
```kotlin
val islamicEngine = IslamicKnowledgeEngine(context)

val response = islamicEngine.queryIslamicKnowledge(
    query = "What is the first pillar of Islam?",
    language = "ar"
)

// Check source level
if (response.sourceLevel == IslamicKnowledgeEngine.SourceLevel.QURAN) {
    // Highest authority
}
```

---

## 10. EXTENDING THE SYSTEM

### Adding a New Language

1. **Add to CommunicationLanguage enum**:
   ```kotlin
   enum class CommunicationLanguage {
       // ... existing
       FRENCH
   }
   ```

2. **Add commands**:
   ```kotlin
   "talk to me in french" to PersonalityCommand.SetLanguage(CommunicationLanguage.FRENCH)
   ```

3. **Add templates**:
   ```kotlin
   "greet" -> listOf(
       ResponseTemplate(
           id = "greet_french",
           template = "Bonjour ! Comment puis-je vous aider ?",
           intent = "greet",
           priority = 10,
           style = ResponseStyle.FORMAL
       )
   )
   ```

4. **Add knowledge**:
   - Add French entries to knowledge base JSON files

### Adding a New Response Type

1. **Add to ResponseBuilder**:
   ```kotlin
   private fun tryBuildResponse(...) {
       when {
           // ... existing cases
           intent == "new_intent" -> {
               buildNewIntentResponse(...)
           }
       }
   }
   
   private fun buildNewIntentResponse(...): ZahraResponse {
       // Build response for new intent
   }
   ```

2. **Add templates**:
   ```kotlin
   // In TemplateSelector
   "new_intent" -> listOf(...)
   ```

3. **Add to ZahraRouter**:
   ```kotlin
   "new_intent" -> {
       // Handle in router if needed
   }
   ```

### Adding a New Context Feature

1. **Add to ConversationState**:
   ```kotlin
   data class ConversationState(
       // ... existing
       val newFeature: NewFeatureType = NewFeatureType()
   )
   ```

2. **Add to ContextManager**:
   ```kotlin
   fun updateNewFeature(feature: NewFeatureType) {
       currentConversation.newFeature = feature
   }
   ```

3. **Use in ResponseBuilder**:
   ```kotlin
   val conversation = contextManager.getCurrentConversation()
   if (conversation.newFeature.enabled) {
       // Use new feature
   }
   ```

---

## 11. FREQUENTLY ASKED QUESTIONS

### Q: How do I change the personality?

**A**: Use natural language commands:
```
"Be more serious"
"Talk to me in Egyptian Arabic"
"Use fewer emojis"
"Explain like a patient teacher"
"Return to default"
```

Or programmatically:
```kotlin
val manager = PersonalityManager(context)
manager.setPersonality(personality.copy(
    tone = CommunicationTone.SERIOUS_DIRECT
))
```

### Q: How do I view the current conversation context?

**A**:
```kotlin
val manager = ContextManager()
val conversation = manager.getCurrentConversation()

// Print conversation summary
println("Turns: ${conversation.turns.size}")
println("Topic: ${conversation.currentTopic}")
println("Entities: ${conversation.activeEntities.keys}")
```

### Q: How do I add a new template?

**A**: Add to `TemplateSelector.kt`:
```kotlin
"greet" -> listOf(
    // ... existing templates
    ResponseTemplate(
        id = "my_new_template",
        template = "My template text with {slot}",
        intent = "greet",
        priority = 5
    )
)
```

### Q: How do I test my changes?

**A**:
```bash
# Run all tests
./gradlew :app:testDebugUnitTest

# Run specific test
./gradlew :app:testDebugUnitTest --tests "com.example.personality.PersonalitySystemTest"
```

### Q: Where do I add new knowledge?

**A**: Add to the appropriate JSON file in `app/src/main/assets/`:
- `general_kb.json` - General knowledge
- `religion_kb.json` - Religious knowledge
- `translation_kb.json` - Translation knowledge

Format:
```json
{
  "records": [
    {
      "id": "unique_id",
      "title": "Title",
      "canonicalContent": "The answer content",
      "originalSourceText": "Original source text",
      "category": "category_name",
      "language": "ar",
      "provenance": {
        "sourceTitle": "Source name",
        "sourceLocation": "Location",
        "origin": "Origin"
      },
      "verificationStatus": "VERIFIED"
    }
  ]
}
```

---

## 12. GLOSSARY

| Term | Definition |
|------|------------|
| **Personality** | User's communication preferences (tone, style, language, etc.) |
| **Context** | Conversation state including turns, entities, and topic |
| **Template** | Pre-defined response pattern with slots for customization |
| **Intent** | The user's purpose or goal (greet, query, calculate, etc.) |
| **Entity** | Extracted information from user input (names, dates, etc.) |
| **Turn** | A single user or Zahra message in a conversation |
| **Source Level** | Authority level of Islamic knowledge (Qur'an, Hadith, etc.) |
| **Invariant** | Property that must always hold true (e.g., personality doesn't change truth) |

---

## 13. ADDITIONAL RESOURCES

### Documentation
- [ZAHRA_SPECIFICATION_V2.md](ZAHRA_SPECIFICATION_V2.md) - Overall specification
- [PHASE_3_LOCAL_KNOWLEDGE_ENGINE.md](PHASE_3_LOCAL_KNOWLEDGE_ENGINE.md) - Knowledge engine
- [PHASE_4_COMPLETION_REPORT.md](PHASE_4_COMPLETION_REPORT.md) - Understanding and reasoning
- [PHASE_5_COMPLETION_REPORT.md](PHASE_5_COMPLETION_REPORT.md) - Intelligence engine
- [AUDIT_REPORT_PHASE_6.md](AUDIT_REPORT_PHASE_6.md) - Phase 6 audit
- [TARGET_ARCHITECTURE_SPECIFICATION.md](TARGET_ARCHITECTURE_SPECIFICATION.md) - Phase 6 architecture

### External Resources
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Android Documentation](https://developer.android.com/docs)
- [JUnit 4 Documentation](https://junit.org/junit4/)
- [Gradle Documentation](https://docs.gradle.org/)

---

**Document Version**: 1.0  
**Last Updated**: 2024-10-23  
**Author**: Vibe Code (AI Architect)  
**Maintainer**: Zahra Development Team

---

## QUICK REFERENCE

### Commands Cheat Sheet

```bash
# Build
./gradlew :app:assembleDebug

# Install
./gradlew :app:installDebug

# Run
./gradlew :app:run

# Test
./gradlew :app:testDebugUnitTest

# Clean
./gradlew clean

# Dependencies
./gradlew :app:dependencies

# Sync
./gradlew --refresh-dependencies
```

### File Locations

```
# Main files
app/src/main/java/com/example/zahra/engines/ZahraRouter.kt
app/src/main/java/com/example/zahra/personality/PersonalityManager.kt
app/src/main/java/com/example/zahra/context/ConversationContext.kt
app/src/main/java/com/example/zahra/response/ResponseBuilder.kt

# Test files
app/src/test/java/com/example/personality/PersonalitySystemTest.kt
app/src/test/java/com/example/context/ContextManagerTest.kt
app/src/test/java/com/example/zahra/response/ResponseSystemTest.kt
app/src/test/java/com/example/zahra/islamic/IslamicKnowledgeEngineTest.kt

# Configuration
gradle/libs.versions.toml
app/build.gradle.kts

# Assets
app/src/main/assets/intent_corpus.json
app/src/main/assets/general_kb.json
app/src/main/assets/religion_kb.json
```

### Important Classes

```kotlin
// Personality
PersonalityManager
PersonalityPreferences
PersonalityStorage
StyleApplier

// Context
ContextManager
ConversationState
ConversationTurn
EntityInfo

// Response
ResponseBuilder
TemplateSelector
TemplateExpander
ResponseRanker
ResponseValidator

// Islamic
IslamicKnowledgeEngine
IslamicResponse

// Existing
ZahraRouter
KnowledgeEngine
MathEngine
ReligionEngine
```
