# A.E.G.I.S.
### Autonomous Edge-Guided Interface Sentinel

> **A Privacy-First, 100% Offline Standalone Android OS Agent powered by Google AI Edge Gemma 4 & LiteRT-LM.**

---

## 🏛️ System Architecture

AEGIS transforms an Android smartphone into an autonomous, voice-driven personal sentinel operating entirely on-device without cloud dependence or external telemetry.

```
                    ┌──────────────────────────────┐
                    │          USER VOICE          │
                    │   "Gemma, open Settings..."  │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │      OFFLINE WAKE-WORD       │
                    │         (Vosk 16kHz)         │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │     ANDROID STT & INTENT     │
                    │       SpeechRecognizer       │
                    └──────────────┬───────────────┘
                                   │
                                   ▼
                    ┌──────────────────────────────┐
                    │     AGENT ORCHESTRATOR       │
                    │  Room DB Cache (SkillDao)    │
                    └───────┬──────────────┬───────┘
          [Cache Miss]      │              │  [Cache Hit]
                            ▼              └───────────────┐
             ┌─────────────────────────────┐               │
             │   GEMMA 4 LITERTLM ENGINE   │               │
             │  (On-Device GPU Inference)  │               │
             └──────────────┬──────────────┘               │
                            │                              │
                            ▼                              ▼
             ┌─────────────────────────────┐ ┌─────────────────────────────┐
             │       RISK ASSESSOR         │ │     ACCESSIBILITY SERVICE   │
             │   (Biometric HITL Gate)     │─┤   GestureDispatcher + Node  │
             └─────────────────────────────┘ └──────────────┬──────────────┘
                                                            │
                                                            ▼
                                             ┌─────────────────────────────┐
                                             │     TARGET APPLICATION      │
                                             │  (WhatsApp, Settings, etc.) │
                                             └─────────────────────────────┘
```

---

## ⚡ Key Capabilities

* **100% On-Device Local Brain**: Powered by Google AI Edge LiteRT-LM (`gemma-4-E2B-it.litertlm` on GPU).
* **Dual-Engine Voice Pipeline**: Continuous offline 16kHz Vosk listener for *"Gemma"* wake-word with atomic hardware-level microphone handoff to Android native speech recognition.
* **Autonomous OS Navigation**: Direct view hierarchy parsing (`AccessibilityNodeInfo`) and programmatic touch gesture dispatch (`GestureDescription`).
* **Episodic Skill Memory**: Self-improving episodic memory layer powered by Room SQLite (`SkillDao`) that caches successful ReAct tool sequences for sub-second cached execution.
* **Biometric Human-In-The-Loop (HITL) Gate**: Zero-trust risk classification (`RiskAssessor`) that transparently halts critical actions (deletions, payments, credential inputs) behind Android Biometric Prompt verification.

---

## 🛠️ Tech Stack

* **Platform**: Android 14+ / 16 (Target SDK 35/36, ARM64-v8a)
* **LLM Engine**: `com.google.ai.edge.litertlm:litertlm-android:0.18.0`
* **Voice Stack**: Vosk 0.3.47 + Android SpeechRecognizer + TextToSpeech
* **UI**: Jetpack Compose (Material3 Dark Strategist HUD)
* **Local Persistence**: Room SQLite + Flow
* **Security**: AndroidX Biometric 1.2.0-alpha05

---

## 🚀 Building & Running

### Prerequisites
* Android Studio Ladybug / Koala or Android SDK Platform-Tools 35+
* Physical device with ARM64-v8a GPU (e.g. Samsung Galaxy M15)

### Installation
```bash
# Clone the repository
git clone https://github.com/KrishnaSrinivas-24/AEGIS.git
cd AEGIS

# Build and deploy debug APK
./gradlew installDebug
```

---

## 👤 Author & Architecture
**Krishna Srinivas Madhira**  
*Forward Deployed Engineer / AI Systems Architect*  
GitHub Community GITAM
