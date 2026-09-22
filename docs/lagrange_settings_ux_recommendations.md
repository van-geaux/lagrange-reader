# Lagrange Reader Settings UX Recommendations

## Purpose

This document consolidates the proposed UX improvements for Lagrange
Reader's two main configuration surfaces:

1.  **Main Settings** --- application-level configuration.
2.  **In-Reader Options** --- configuration of the current reading
    experience.

The central design principle is to keep these surfaces conceptually
separate:

> **Main Settings controls Lagrange as an application. In-Reader Options
> controls how the user reads or listens.**

This separation should reduce duplicated controls, make settings easier
to discover, and prevent the application from becoming increasingly
complicated as new features are added.

## Implementation status

The current implementation applies the Main Settings navigation portion
of these recommendations. The root contains Appearance, General,
Library, Downloads & Offline, and Network & Sync. Account & Server and
About are intentionally kept in the profile/menu destinations rather than
duplicated in Settings.

The Library category contains the existing per-library reader profile
editor. It exposes the selected library's EPUB, PDF, and comic settings
without introducing book-specific overrides. Active reader options use
the same persisted profile but show only controls for the format currently
being read.

The remaining reader progressive-disclosure, defaults/override, and
audiobook-hierarchy recommendations remain future work.

------------------------------------------------------------------------

# 1. Main Settings

## 1.1 Current UX Assessment

Lagrange's main settings have grown organically alongside the
application. This is normal during rapid development: a feature is
implemented, it requires a preference, and that preference is added to
the existing options screen.

Over time, however, this can cause Settings to reflect the application's
internal feature structure rather than the user's mental model.

Current and likely application-level preferences span several unrelated
concepts:

-   Application appearance
-   Startup behavior
-   Orientation
-   Downloads
-   Offline storage
-   Cache management
-   Network policies
-   Background activity
-   Synchronization
-   Authentication
-   Server configuration
-   Accessibility-related behavior
-   Application information and diagnostics

The problem is therefore no longer a lack of configuration. The problem
is **information architecture**.

### Design objective

Main Settings should answer:

> **How should Lagrange behave as an application?**

It should not become a second location for configuring the
EPUB/PDF/comic/audiobook reading experience.

------------------------------------------------------------------------

## 1.2 Boundary Between Main Settings and Reader Options

A strict boundary should be established before reorganizing either menu.

### Main Settings examples

-   App theme
-   Opening/startup screen
-   Cellular download policy
-   Background networking
-   Cache management
-   Download storage
-   Account
-   BookOrbit server
-   Reduce motion
-   Application orientation behavior
-   Version/update information

### Reader Options examples

-   EPUB font
-   Font size
-   Reader theme
-   Margins
-   Line spacing
-   Reading direction
-   Page/continuous mode
-   Reader-specific orientation
-   Keep screen awake while reading
-   Audiobook playback speed
-   Sleep timer

A useful rule is:

> If the option primarily changes Lagrange itself, it belongs in Main
> Settings.\
> If the option primarily changes the consumption of a publication, it
> belongs in the reader/player.

------------------------------------------------------------------------

## 1.3 Proposed Main Settings Architecture

The Settings root should become primarily a navigation surface rather
than one long list of every available preference.

Recommended structure:

``` text
Settings

Appearance
Theme and interface

General
Startup and app behavior

Library
Library browsing and reader configuration

Downloads & Offline
Downloads, storage and cache

Network & Sync
Connectivity and synchronization

Account & Server
BookOrbit connection and account

About
Version, privacy and licenses
```

This architecture provides room for future functionality without
continuously extending a single settings screen.

A dedicated **Accessibility** category can be introduced later if enough
accessibility-specific controls exist. It should not be created merely
to contain one setting.

------------------------------------------------------------------------

## 1.4 Appearance

Recommended scope:

``` text
Appearance
────────────────────────────

App theme
Midnight                                  >

Reduce motion                         ○
```

The category can remain deliberately small.

Do not fill categories merely to make them appear substantial.

### Important distinction

**Application theme and reader theme should remain separate concepts.**

A user may reasonably want:

-   Lagrange UI: Midnight
-   EPUB reader: Paper

Changing one should not unexpectedly change the other.

------------------------------------------------------------------------

## 1.5 General

General should contain application behavior that does not naturally
belong to another major category.

Example:

``` text
General
────────────────────────────

STARTUP

Opening screen
Home                                      >

DISPLAY

Screen orientation
Follow system                              >
```

Possible opening-screen choices:

``` text
Opening screen

○ Home
○ Libraries
○ Last opened screen
```

A specific-library startup target could be added later if there is
demonstrated demand, but should not be introduced simply because it is
technically possible.

### Principle

Avoid exposing options preemptively.

A configurable internal behavior does not automatically require a
user-facing setting.

------------------------------------------------------------------------

## 1.6 Downloads & Offline

Offline capability is one of Lagrange's strongest characteristics and
deserves a first-class configuration category.

Potential organization:

``` text
Downloads & Offline
────────────────────────────

DOWNLOADS

Download over cellular                ○

Concurrent downloads
2                                         >

STORAGE

Downloaded books
18.4 GB                                  >

Cache
742 MB                                   >

Clear cache                               >
```

Only controls supported by the application should be implemented; the
example represents the intended information hierarchy.

### Downloads and cache must remain conceptually separate

Users generally understand:

**Downloaded books**

as content they intentionally chose to retain.

They understand:

**Cache**

as temporary data that the application can recreate.

Avoid ambiguous actions such as:

``` text
Storage used: 19.1 GB
Clear storage
```

Prefer:

``` text
Downloaded books       18.4 GB
Cache                    742 MB

Clear cache
```

Destructive operations should communicate precisely what will and will
not be deleted.

------------------------------------------------------------------------

## 1.7 Network & Sync

Lagrange's synchronization system can be sophisticated internally
without exposing that sophistication to users.

Recommended presentation:

``` text
Network & Sync
────────────────────────────

BACKGROUND ACTIVITY

Sync in background                    ●

Use cellular data                     ●

STATUS

Last synchronized
Today, 10:32

Pending changes
3                                         >
```

Avoid exposing implementation concepts such as:

-   Progress upload throttle interval
-   Retry count
-   WorkManager constraints
-   Queue implementation
-   Catalog TTL
-   Session upload intervals

These should generally remain implementation details.

### Minimize unnecessary toggles

If synchronization is fundamental to the expected operation of Lagrange,
it may not need an enable/disable toggle at all.

A useful design principle is:

> **Defaults are UX.**

The application should make sensible decisions wherever user preference
does not materially improve the experience.

------------------------------------------------------------------------

## 1.8 Account & Server

Keep this category deliberately simple.

Example:

``` text
Account & Server
────────────────────────────

ACCOUNT

Username / profile

SERVER

BookOrbit
books.example.com                         >

Sign out
```

Server details could expose useful troubleshooting information:

``` text
Server

Address
https://...

BookOrbit version
1.x.x

Connection
Connected

Test connection
```

Authentication implementation details should remain invisible after
successful login whenever possible.

The user's mental model should simply be:

> **Which BookOrbit server am I connected to, and which account am I
> using?**

------------------------------------------------------------------------

## 1.9 About

Non-operational information should be consolidated here.

Example:

``` text
About Lagrange
────────────────────────────

Lagrange Reader
Version x.x.x

Check for updates

Privacy
Licenses
GitHub
Report an issue
```

Diagnostics can be added later if they provide real troubleshooting
value.

------------------------------------------------------------------------

## 1.10 Do Not Expose Every Internal Preference

An internal configuration value does not automatically deserve a UI
control.

Example:

  Configuration              User-facing?
  -------------------------- ----------------
  App theme                  Yes
  Opening screen             Yes
  Cellular downloads         Yes
  Reader font                Yes, in reader
  Reader margins             Yes, in reader
  Cache expiration           No
  Progress upload throttle   No
  Retry count                No
  Catalog refresh interval   No
  Queue implementation       No
  Download chunk size        No

Every preference imposes cognitive cost.

The objective should not be maximum configurability. It should be:

> **Expose choices where reasonable users genuinely have different
> preferences.**

------------------------------------------------------------------------

## 1.11 Avoid a Giant Settings Screen

Instead of showing all settings on one scrolling screen:

``` text
Settings
  Theme
  Reduce motion
  Opening screen
  Orientation
  Cellular downloads
  Cache
  Background sync
  Server
  ...
```

use a navigation-oriented root:

``` text
Settings

🎨 Appearance
   Theme and interface

⚙ General
   Startup and app behavior

↓ Downloads & Offline
   Downloads, storage and cache

↻ Network & Sync
   Connectivity and synchronization

👤 Account & Server
   BookOrbit connection and account

ⓘ About
   Version, privacy and licenses
```

This allows Settings to scale while making the application feel simpler.

### Settings search

Do not add Settings search at the current scale.

If users eventually require search to locate ordinary options, first
reassess whether too many controls are exposed or whether the hierarchy
has become unclear.

------------------------------------------------------------------------

## 1.12 Proposed Main Settings Hierarchy

``` text
SETTINGS
│
├── Appearance
│   ├── App theme
│   └── Reduce motion
│
├── General
│   ├── Opening screen
│   ├── Startup behavior
│   └── Orientation
│
├── Downloads & Offline
│   ├── Cellular downloads
│   ├── Download behavior
│   ├── Downloaded storage
│   ├── Cache
│   └── Clear cache
│
├── Network & Sync
│   ├── Background network
│   ├── Cellular network policy
│   ├── Sync status
│   └── Pending changes
│
├── Account & Server
│   ├── Account
│   ├── Server
│   ├── Connection status
│   └── Sign out
│
└── About
    ├── Version
    ├── Updates
    ├── Privacy
    ├── Licenses
    ├── GitHub
    └── Report issue
```

Reader configuration is intentionally absent from this hierarchy.

------------------------------------------------------------------------

# 2. In-Reader Options

## 2.1 Current UX Assessment

The in-reader configuration has a different problem from Main Settings.

Reader functionality naturally accumulates controls such as:

-   Font
-   Font size
-   Line spacing
-   Margins
-   Reader theme
-   Brightness
-   Reading direction
-   Page/continuous mode
-   Orientation
-   Keep awake
-   Custom fonts
-   Chapters
-   Navigation
-   Audiobook playback speed
-   Sleep timer

All of these can be useful, but they are used at very different
frequencies.

Giving every control equal prominence makes the reader appear more
complicated than it actually is.

### Design objective

Reader Options should answer:

> **How do I want to read or listen to this publication right now?**

The reader itself should remain visually subordinate to the content.

------------------------------------------------------------------------

## 2.2 Organize Controls by Frequency of Use

Reader controls should conceptually exist in three layers.

``` text
Layer 1 — Frequent
Theme, font size, chapters/navigation

Layer 2 — Occasional
Font, spacing, margins, layout

Layer 3 — Rare
Orientation, reading direction, advanced behavior
```

The interface should reflect this frequency hierarchy rather than
exposing all controls simultaneously.

------------------------------------------------------------------------

## 2.3 Normal Reader State

The normal state should contain as little UI as possible.

``` text
┌──────────────────────────────┐
│                              │
│                              │
│          BOOK TEXT           │
│                              │
│                              │
└──────────────────────────────┘
```

After tapping the page:

``` text
┌──────────────────────────────┐
│ ←   Chapter 14          ⋮    │
│                              │
│          BOOK TEXT           │
│                              │
│ Aa       48%       Chapters │
└──────────────────────────────┘
```

Recommended primary actions:

-   **Aa** --- appearance
-   **Progress/navigation**
-   **Chapters**
-   **⋮** --- less frequently used reader behavior

The exact icons can change; the hierarchy is more important than the
specific representation.

------------------------------------------------------------------------

## 2.4 Appearance as the Primary Configuration Surface

`Aa` should open a lightweight bottom sheet rather than a large settings
screen.

Example:

``` text
┌──────────────────────────────┐
│          Appearance          │
│ ──────────────────────────── │
│                              │
│        A−   100%   A+        │
│                              │
│  ○ Light  ● Paper  ○ Dark   │
│                              │
│ Font                         │
│ Bookerly                  >  │
│                              │
│ Layout                    >  │
│                              │
╰──────────────────────────────╯
```

This surface should prioritize controls that users plausibly change
during an active reading session.

------------------------------------------------------------------------

## 2.5 Font Size

Font size deserves direct access.

Prefer:

``` text
A−       100%       A+
```

over:

``` text
Font size
100% >
```

Changes should update the publication immediately behind the sheet.

Avoid:

-   Apply buttons
-   Save buttons
-   Confirmation dialogs

### General principle

> **Reader configuration should use live preview whenever possible.**

The publication itself is the preview.

------------------------------------------------------------------------

## 2.6 Reader Theme

Reader theme is another high-frequency setting and can be presented
directly.

Example:

``` text
Theme

○ Light    ● Paper    ○ Dark
```

Visual swatches can be preferable to a conventional dropdown because the
choice itself is visual.

Again, reader theme should remain independent from application theme.

------------------------------------------------------------------------

## 2.7 Font Selection

Selecting `Font` can open a dedicated picker:

``` text
Font
──────────────────────────────

Recommended

● Bookerly
○ Literata
○ Noto Serif
○ Atkinson Hyperlegible

System fonts
                         >

Custom fonts
                         >

Import font
```

Where practical, render font names or sample text using the font itself:

``` text
Bookerly
The quick brown fox...

Literata
The quick brown fox...

Atkinson Hyperlegible
The quick brown fox...
```

This makes the picker itself a preview rather than forcing users to
recognize typeface names.

------------------------------------------------------------------------

## 2.8 Layout

The Layout submenu should contain controls that matter but do not need
to remain visible during ordinary reading.

Example:

``` text
Layout
──────────────────────────────

Line spacing

  Compact   ● Normal   Relaxed

Margins

Horizontal
A little ─────●────── More

Vertical
A little ───●──────── More

Advanced                              >
```

### Prefer semantic presets where possible

Users generally think:

> "The text feels cramped."

rather than:

> "I need line spacing 1.42."

Therefore:

``` text
Compact | Normal | Relaxed
```

may be preferable to exposing numeric controls immediately.

Fine-grained values can remain available under Advanced.

------------------------------------------------------------------------

## 2.9 Advanced Layout

Power-user configuration does not need to be removed. It should simply
be placed deeper in the hierarchy.

Potential structure:

``` text
Advanced layout
──────────────────────────────

Horizontal margin
Vertical margin
Line spacing
Paragraph spacing
Text alignment
Hyphenation
```

Only expose settings that Lagrange actually supports and that provide
meaningful user value.

The desired architecture is:

``` text
Simple controls
      ↓
Advanced controls
```

not:

``` text
Every possible control at once
```

------------------------------------------------------------------------

## 2.10 Separate Appearance From Reader Behavior

Appearance controls answer:

> **What should the page look like?**

Behavior controls answer:

> **How should the reader behave?**

They should not occupy the same level.

The `⋮` menu is a suitable location for less frequently changed
behavior:

``` text
⋮

Book information
Reading direction
Screen orientation
Keep screen awake
Advanced reader options
```

Potential troubleshooting/reporting actions could also eventually live
here.

Common controls such as font size, reader theme and chapters should
**not** be hidden in this menu.

------------------------------------------------------------------------

## 2.11 Reader Information Is Not Reader Configuration

Do not use Reader Options as a dumping ground for every piece of
potentially useful information.

Keep these concepts separate:

### Configuration

-   Font size
-   Theme
-   Margin

### Navigation

-   Chapter
-   Table of contents

### Reading status

-   Progress
-   Page

### System information

-   Time
-   Battery
-   Connectivity

Lagrange should prioritize information specific to reading rather than
unnecessarily duplicating information Android already provides.

------------------------------------------------------------------------

# 3. Reader Preference Persistence

## 3.1 The Core Question

Whenever a user changes something such as:

``` text
Font → Literata
```

the application needs a predictable answer to:

> **What scope did this change affect?**

Possible scopes include:

-   Current book
-   Current library
-   Every book
-   Global reader default

The application should avoid asking users to make this decision for
every setting change.

Do **not** routinely show dialogs such as:

``` text
Apply font to:

○ This book
○ This library
○ All books

CANCEL       APPLY
```

This provides flexibility at the cost of unnecessary cognitive overhead.

------------------------------------------------------------------------

## 3.2 Recommended Model: Defaults + Book Overrides

Use two conceptual levels:

``` text
Reader defaults
      ↓
Book inherits defaults
      ↓
Book-specific override when needed
```

Example:

### Reader default

``` text
Font       Bookerly
Theme      Paper
Margin     Medium
```

### Book A

Inherits all defaults.

### Book B

``` text
Font       Bookerly   ← inherited
Theme      Paper      ← inherited
Margin     Narrow     ← override
```

The implementation can understand inheritance without constantly
exposing that mechanism to the user.

------------------------------------------------------------------------

## 3.3 Explicit Default Actions

The Appearance surface can provide two explicit actions:

``` text
Use these settings as default

Reset to defaults
```

Example:

``` text
Appearance
──────────────────────────────

A−       110%       A+

● Light   ○ Paper   ○ Dark

Font
Literata                        >

Layout
Custom                          >

──────────────────────────────

Use these settings as default

Reset to defaults
```

The mental model becomes:

> **I am adjusting this book. If I like this configuration, I can make
> it my normal reading setup.**

This avoids scope dialogs while preserving control.

------------------------------------------------------------------------

## 3.4 Avoid Library-Level Preference Inheritance for Now

A three-level hierarchy such as:

``` text
Global default
     ↓
Library default
     ↓
Book override
```

is technically powerful but can become difficult to understand.

Users may have difficulty determining why a book has a particular font,
theme or margin if multiple inheritance layers exist.

Unless there is demonstrated demand, prefer:

``` text
Reader default
       ↓
Book override
```

Two levels are significantly easier to reason about.

------------------------------------------------------------------------

## 3.5 Format-Specific Defaults Are Reasonable

Different publication formats have fundamentally different interaction
models.

Therefore it is reasonable to maintain independent defaults for:

``` text
EPUB defaults

PDF defaults

Comic defaults

Audiobook defaults
```

EPUB margins should not affect comics, and audiobook playback
preferences naturally belong to a different configuration domain.

------------------------------------------------------------------------

# 4. Audiobook Configuration

Audiobook settings should follow the same **hierarchy principle** as the
EPUB reader without copying its exact UI.

Frequently used controls deserve immediate access.

For example:

``` text
Playback speed

0.75×   1×   1.25×   1.5×   2×

Current
1.35×

──────●────────────────

Remember this speed             ●
```

This provides:

-   Fast presets for ordinary use
-   Fine-grained adjustment for power users
-   A clear mechanism for persistence

Other audiobook controls can include:

-   Chapters
-   Sleep timer
-   Playback history
-   Read-along controls where applicable

Avoid adding a large number of individual speed buttons merely to
support increasingly high or granular playback speeds.

------------------------------------------------------------------------

# 5. Combined Settings Architecture

The two configuration systems should ultimately have very little
conceptual overlap.

## Application Settings

``` text
APP SETTINGS
│
├── Appearance
├── General
├── Downloads & Offline
├── Network & Sync
├── Account & Server
└── About
```

## Reader Interaction

``` text
READER
│
│   Tap screen
│
├─────────────────────────────────
│ TOP BAR
│
│ ←        Chapter 14          ⋮
│
│
│              BOOK
│
│
│ Aa           48%        Chapters
│
└─────────────────────────────────
```

## Appearance

``` text
APPEARANCE
│
├── Font size
├── Theme
├── Font
├── Layout
│   │
│   ├── Line spacing
│   ├── Margins
│   └── Advanced
│
├── Use as reader default
└── Reset to defaults
```

## Navigation

``` text
NAVIGATION
│
├── Table of contents
└── Current chapter
```

## More

``` text
MORE
│
├── Book information
├── Reading direction
├── Orientation
├── Keep screen awake
└── Advanced reader options
```

------------------------------------------------------------------------

# 6. Recommended Interaction Hierarchy

The overall reader configuration strategy should be based on frequency.

### One tap

Controls commonly needed during a reading session:

-   Font size
-   Reader theme
-   Chapters
-   Progress/navigation

### Two taps

Controls changed occasionally:

-   Font family
-   Margins
-   Line spacing
-   Layout
-   Audiobook playback speed

### Three taps / Advanced

Controls changed rarely:

-   Fine-grained layout
-   Orientation
-   Reading direction
-   Unusual reader behavior

This allows Lagrange to retain extensive configurability without making
the reader appear complicated.

------------------------------------------------------------------------

# 7. Guiding Principles

## 7.1 Main Settings controls the application

Do not duplicate reader configuration in Main Settings simply because
those preferences persist.

## 7.2 Reader Options controls the reading experience

Keep controls close to the context in which users understand their
effects.

## 7.3 Prioritize by frequency

Frequently used controls should require fewer interactions.

## 7.4 Prefer live preview

Reading appearance changes should update immediately whenever
technically practical.

## 7.5 Hide complexity rather than removing capability

Advanced users can retain fine control without forcing every user to
confront every option.

## 7.6 Avoid unnecessary configurability

Not every internal value needs a user-facing preference.

## 7.7 Keep defaults predictable

Good defaults reduce the need for settings.

## 7.8 Avoid excessive inheritance

Prefer reader defaults plus book-specific overrides over global →
library → book hierarchies unless real user demand justifies the
complexity.

## 7.9 Keep content dominant

The ideal normal state of the reader is the publication itself, not
Lagrange's interface.

------------------------------------------------------------------------

# 8. Recommended Implementation Priority

A practical implementation sequence would be:

1.  **Define ownership of every existing preference**
    -   Main Settings
    -   EPUB/PDF/comic reader
    -   Audiobook player
    -   Internal/not user configurable
2.  **Reorganize Main Settings**
    -   Appearance
    -   General
    -   Downloads & Offline
    -   Network & Sync
    -   Account & Server
    -   About
3.  **Reduce the first-level reader controls**
    -   Appearance
    -   Progress/navigation
    -   Chapters
    -   More
4.  **Introduce progressive disclosure**
    -   Appearance → Font / Layout
    -   Layout → Advanced
5.  **Implement live preview**
    -   Font size
    -   Theme
    -   Font
    -   Layout where practical
6.  **Define preference persistence**
    -   Reader defaults
    -   Book-specific overrides
    -   Reset to defaults
    -   Use current configuration as default
7.  **Apply the same hierarchy philosophy to audiobooks**
    -   Immediate speed/chapter controls
    -   Secondary sleep/history controls
    -   Rare options deeper in the interface
8.  **Review every remaining setting**
    -   Remove or hide settings that expose implementation details or do
        not represent meaningful user choices.

------------------------------------------------------------------------

# 9. Intended Outcome

The objective is not to make Lagrange less configurable.

It is to make its complexity **proportional to the user's intent**.

A user who simply wants to read should encounter:

``` text
Open book → Read
```

A user who wants a different font should encounter:

``` text
Open book → Aa → Font
```

A user who wants detailed typography control should still be able to
reach:

``` text
Open book → Aa → Layout → Advanced
```

Likewise, someone who only wants to connect Lagrange to BookOrbit and
download books should never need to understand synchronization queues,
cache implementation, retry policies, or reader preference inheritance.

The long-term UX direction should therefore move from:

> **How do we expose every capability?**

toward:

> **How do we preserve every useful capability while exposing only what
> matters at the moment the user needs it?**

That approach allows Lagrange to continue becoming more capable without
making the application feel progressively more complicated.
