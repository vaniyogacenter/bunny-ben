# Product Requirement Document (PRD)
## Project Name: Bunny Ben (The Interactive Talking Virtual Rabbit)
**Author:** AI Studio Platform Architect & Lead Developer  
**Status:** Approved & Implemented  
**Date:** May 31, 2026  
**Target Version:** v1.0.0 (Production Ready)

---

## 1. Executive Summary
**Bunny Ben** is an interactive, fully persistence-backed, talking virtual pet companion application built natively for Android. Inspired by classic virtual pet titles like *Talking Tom*, Bunny Ben elevates the genre by integrating **Google's Gemini AI** for open-ended, contextual real-time conversations. 

The application provides a responsive simulation of a pet's lifecycle metrics (Hunger, Happiness, Energy), customizable cosmetics (fuzzy classic looks to futuristic cyber visors), beautiful dynamic environments, a high-octane **Whack-A-Carrot** mini-game for core gameplay loops, and local data synchronization powered by **Jetpack Room Database**. 

---

## 2. Goals & Objectives
### 2.1 User Goals
*   **Active Companionship:** Engage in humorous, contextual conversations with a virtual pet rabbit who exhibits unique personality traits.
*   **Gamified Loop:** Feed, pet, and customize Ben by playing action-oriented mini-games to earn gold coins.
*   **Sense of Progress:** Level up Ben, unlock prestigious wardrobe additions, and customize his home environment.

### 2.2 Technical Goals
*   **Offline-First Stability:** Ensure all interactions, currency, settings, level points, and state metrics are fully persistent locally in SQLite using Room.
*   **Robust Edge-to-Edge Fluidity:** Achieve Material Design 3 compliance with responsive support for various device aspects and screen densities.
*   **Secure API Design:** Handle network-based LLM queries gracefully without blocking UI pipelines or throwing runtime frame dispatcher exceptions.

---

## 3. System Architecture & Tech Stack

```
   ┌─────────────────────────────────────────────────────────┐
   │                  Jetpack Compose UI                     │
   │               (BenScreen & Theme Views)                 │
   └────────────────────────────┬────────────────────────────┘
                                │ State Collection
                                ▼
   ┌─────────────────────────────────────────────────────────┐
   │                     BenViewModel                        │
   │            (StateFlow, Coroutine Game Loops)            │
   └───────────────┬─────────────────────────┬───────────────┘
                   │                         │
                   ▼ Database Sync           ▼ REST Service
   ┌───────────────────────────────┐ ┌───────────────────────┐
   │         BenRepository         │ │     GeminiService     │
   │  (Room Dao and SQL queries)   │ │  (AI Conversational)  │
   └───────────────┬───────────────┘ └───────────────────────┘
                   ▼
   ┌───────────────────────────────┐
   │      SQLite / BenDatabase     │
   └───────────────────────────────┘
```

*   **Language:** Kotlin 1.9+
*   **Architecture:** Model-View-ViewModel (MVVM) and Clean Repository Pattern.
*   **UI Framework:** Jetpack Compose (Declarative UI) with specialized Canvas renders for dynamic pet movements.
*   **Database:** Jetpack Room Database for ACID-compliant structured local storage.
*   **AI Integration:** REST-based lightweight asynchronous interface with Gemini API.

---

## 4. Functional Specification

### 4.1 Real-Time Vital State Engine
Bunny Ben is managed by three core biological metrics that auto-decay slightly over time:

| Metric | Start | Decay Interval | Description |
| :--- | :--- | :--- | :--- |
| **Hunger** | `1.0f` (100%) | -0.02f every 60s | Regenerated via Food Store menu items (Carrots, Cookies, Cupcakes). |
| **Happiness** | `1.0f` (100%) | -0.01f every 60s | Boosted by petting, tummy tickles, and winning mini-games. |
| **Energy** | `1.0f` (100%) | -0.01f every 60s | Replenished entirely when put into a sleep loop. Prevents mini-game access below `15%`. |

### 4.2 Interactive Core Animations (`BenAnimation`)
Ben's state is fully responsive to gestures and actions:
*   `IDLE`: Smooth nose twitching and blinking.
*   `HAPPY`: Flaps ears and jumps with sparkles when petted.
*   `EATING`: Munching animation with high-fidelity speech bubbles.
*   `TICKLED`: Giggles and thumps hind paw when tummy is tapped.
*   `SLEEPING`: Heavy snoring layout with a dark custom sleep overlay.
*   `THINKING`: Generative thinking state for AI requests.
*   `TALKING`: Animates lips/mouth while showing AI response text bubble.

### 4.3 Customization Wardrobe (Outfit Catalog)
Users buy and immediately apply outfits using collected gold coins:

*   **Classic White Fur** (Cost: `0 Coins`): Native soft rabbit design.
*   **Superhero Cape** (Cost: `80 Coins`): Grants Ben a visual cape.
*   **Fancy Gentleman** (Cost: `150 Coins`): Features a blue Top-hat and bowtie.
*   **Wizard Scholar** (Cost: `250 Coins`): Starry magic cap design.
*   **Cyber Rabbit** (Cost: `400 Coins`): Futuristic glowing neon visor.

### 4.4 Dynamic Environments (Backdrop Catalog)
Three scenic backdrops alter UI canvas rendering:
1.  **Sunny Meadow** (`#81C784`): Bright, lively grassy atmosphere.
2.  **Cozy Living Room** (`#FFB74D`): Warm, safe glowing hearth.
3.  **Neon Cyber Grid** (`#1F1A24`): High-intensity neon grid.

### 4.5 Core Whack-A-Carrot Mini-Game
An action-packed arcade mini-game built inside the Jetpack Compose loop:
*   **Game Rules:** A fast 20-second session. Carrots spawn randomly in dynamic locations inside the canvas.
*   **Item System:**
    *   `REGULAR_CARROT`: +5 Points (Standard target)
    *   `GOLDEN_CARROT`: +20 Points (Rare golden spawn)
    *   `TOXIC_WEED`: -10 Points (Risk factor to avoid)
*   **Rewards Integration:** Points are immediately converted into gold coins and level points upon completion, fully persisted in sqlite database.

### 4.6 Gemini AI Chat Integration
*   Ben leverages a server-side Gemini endpoint via `GeminiService` to create conversational replies.
*   Ben keeps in character, utilizing rabbit puns, squeaking, and reacting contextually based on his biological status (e.g. complains if hungry).

---

## 5. UI/UX Design System

### 5.1 Color Palette
*   **Primary Accent:** Warm Orange-Cream (`#FFB74D`) simulating freshly harvested premium carrots.
*   **Surfaces:** Translucent sheets over structural backdrops to prevent contrast clashes under adaptive themes.
*   **Dark Modes:** Complete M3 system layout compatibility with native theme switches.

### 5.2 Accessibility Compliance
*   **Touch Targets:** Every interactive icon, feeding selector, and drawer button complies with the minimum **48dp x 48dp** specification.
*   **Contrast:** High readability typographic pairings using standard system font layouts.

---

## 6. Future Expansion Roadmap
*   **Multiplayer Visits:** Enable visiting other virtual rabbit burrows via room sharing.
*   **More Actions:** Introduce gardening mini-games to plant and harvest different carrot varieties manually.
*   **Speech-to-Speech:** Native Android TTS (Text-to-Speech) API integration so Ben reads his text bubbles out loud.

---
*(End of Document. Copy this Markdown directly into Google Docs to automatically convert to standard documents formatting)*
