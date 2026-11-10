# ClassFund — UI/UX Audit and Google Stitch Design Specification

| Field | Value |
|---|---|
| Product | ClassFund |
| Package / application ID | `com.example.kas_app` |
| Platform | Native Android, Java + XML Views (no Jetpack Compose) |
| UI toolkit | Material Components for Android 3 (`Theme.Material3.DayNight.NoActionBar`) |
| SDK | `minSdk 24`, `compileSdk 35`, `targetSdk 35` |
| Architecture | Activities + `RecyclerView` adapters + `SQLiteOpenHelper`; no ViewModel/LiveData/Compose |
| Locales | English (`values/strings.xml`) and Indonesian (`values-in/strings.xml`) |
| Themes | Light and dark, selected by an in-app switch and persisted in `SharedPreferences` |
| Audit date | 2026-11-05 |
| Audit basis | Static inspection of layouts, Java UI classes, drawables, mipmaps, colors, themes, styles, and both string files |
| Visual verification | **Not performed.** No device was attached (`adb devices` empty) and no emulator binary was installed. Appearance-related conclusions are resource-level or computed; items needing a screenshot are marked as potential issues. |

> **Scope.** This is an inspection and design-specification document only. It does not change application code, layouts, resources, Gradle files, the database, or behavior. The document is intended for Google Stitch so that it improves the existing ClassFund visual language rather than replacing it with an unrelated template.

### Evidence labels used in this document

| Label | Meaning |
|---|---|
| **Verified** | Read directly from a layout, Java class, resource, or manifest. |
| **Computed** | Contrast ratio calculated from sRGB values using the WCAG 2.x relative-luminance formula. |
| **Potential** | Structurally suggested by the implementation, but actual visual impact needs a running device or screenshot. |
| **Unverified** | Depends on the platform theme, device rendering, font fallback, or a screen not captured. |

---

## 1. Project Overview

ClassFund is a class-fund manager for a treasurer or class administrator. It records students, monthly student payments, and a cash-book ledger of money in and money out, then derives the current balance. It is deliberately narrow: not a general accounting suite, not a school information system, and not a social app.

### 1.1 Verified product facts

| Item | Value | Source |
|---|---|---|
| App name | `ClassFund` | `app/src/main/res/values/strings.xml` |
| Package / namespace | `com.example.kas_app` | `app/build.gradle.kts`, `AndroidManifest.xml` |
| Version | `1.0` (`versionCode 1`) | `app/build.gradle.kts` |
| SDK | `minSdk 24`, `compileSdk 35`, `targetSdk 35` | `app/build.gradle.kts` |
| Language / UI | Java 11 source level, XML Views, Material 3 | `app/build.gradle.kts`, layouts |
| Launcher activity | `WelcomeActivity` with `Theme.Kas_App.Starting` | `AndroidManifest.xml` |
| Database | SQLite `kas_app.db`, version 3 | `DatabaseHelper.java` |
| Primary storage | `table_students`, `table_payments`, `table_ledger` | `DatabaseHelper.java` |
| Currency | Indonesian Rupiah, integer amounts, pattern `Rp #,##0` | `CurrencyFormatter.java` |
| Month vocabulary | English names `January`…`December`, persisted verbatim | `DatabaseHelper.PAYMENT_MONTHS` |

### 1.2 Primary user flow

```text
System splash (Theme.Kas_App.Starting)
        ↓
WelcomeActivity  ── Begin ──►  MainActivity
                                   ├── Fund Data card / Open ──► FundDataActivity
                                   │        ├── student row ──► StudentPaymentBottomSheet
                                   │        ├── overflow ──► edit / delete student
                                   │        └── FAB ──► Add Student dialog
                                   ├── Fund Report card / Open ──► FundReportActivity
                                   │        ├── row / overflow ──► edit / delete ledger
                                   │        ├── swipe left ──► delete ledger
                                   │        └── FAB ──► Add Ledger dialog
                                   ├── Credits card / Open ──► CreditsActivity
                                   └── profile avatar ──► profile/settings BottomSheetDialog
                                            ├── dark-mode switch
                                            ├── language switch (EN / ID)
                                            └── Log out ──► WelcomeActivity (cleared task)
```

There is **no** bottom navigation, navigation drawer, tab bar, search, filtering, onboarding carousel, login/authentication, notification centre, or settings screen beyond the profile bottom sheet. Do not invent any of these for the redesign.

### 1.3 Accounting contract that the UI expresses

| Concept | Stored / computed form | UI wording |
|---|---|---|
| Money entering the fund | `table_ledger.type = 'debit'` | Debit (Income) / Debit (Pemasukan) |
| Money leaving the fund | `table_ledger.type = 'credit'` | Credit (Expense) / Kredit (Pengeluaran) |
| Balance | `SUM(debit) − SUM(credit)` over `table_ledger` only | Current balance / Saldo saat ini |
| Student payment | Row in `table_payments`, mirrored by **one** `table_ledger` debit row linked through `payment_id` | Payment saved into the fund |

This is the opposite of what many non-accountants expect from the words “debit” and “credit”. The UI already clarifies this with `Credit (Expense)` and `Debit (Income)`; any redesign must keep that clarity and must not reverse the mapping.

### 1.4 Inspection coverage

| Area | Files inspected |
|---|---|
| Layouts | `activity_main.xml`, `activity_welcome.xml`, `activity_fund_data.xml`, `activity_fund_report.xml`, `activity_credits.xml`, `activity_popup.xml`, `bottom_sheet_payment.xml`, `dialog_add_ledger.xml`, `dialog_add_student.xml`, `item_ledger.xml`, `item_payment.xml`, `item_student.xml` |
| Activities / UI classes | `MainActivity.java`, `WelcomeActivity.java`, `FundDataActivity.java`, `FundReportActivity.java`, `CreditsActivity.java`, `StudentPaymentBottomSheet.java`, `StudentAdapter.java`, `PaymentAdapter.java`, `LedgerAdapter.java`, `KeyboardInsets.java`, `KasApp.java` |
| Data / formatting | `DatabaseHelper.java`, `CurrencyFormatter.java`, `IsoDate.java`, `AmountValidator.java`, `Student.java`, `Payment.java`, `LedgerEntry.java` |
| Visual resources | `res/drawable/*`, `res/drawable-night/*`, `res/mipmap-*/*`, `res/font/*`, launcher and Play Store images |
| Design tokens | `res/values/colors.xml` (all colors), `res/values/themes.xml`, `res/values-night/themes.xml` |
| Text | `res/values/strings.xml`, `res/values-in/strings.xml`, `AndroidManifest.xml` labels |
| Build / manifest | `app/build.gradle.kts`, `AndroidManifest.xml` |
| References | `design/illustrations/*.svg`, `ref/ClassFund_Finance_Illustration_Sheet.png` |

---

## 2. Current UI Design Summary

### 2.1 One-paragraph characterisation

ClassFund currently reads as a **friendly, student-made Material 3 finance app dipped in a purple/lavender palette**. It has a recognisable visual signature: curved or diamond-shaped purple headers, large rounded white cards, flat geometric purple illustrations, a handwritten Borel greeting, pill-shaped primary buttons, and lists that slide up over the coloured header with rounded top corners. The bones are Material 3, but the execution is assembled from inline values rather than a tokenised design system: three font families, six corner radii, five button treatments, two dialog families, fixed-height cards, and a mix of theme-aware and hard-coded colours. Light mode is coherent and attractive; dark mode is the weak point because the app uses the light-lavender dark `colorPrimary` as a large surface while continuing to paint white text and white toolbar icons on top of it.

### 2.2 Design DNA to preserve

| Signature | Current implementation | Why it matters |
|---|---|---|
| Purple/lavender identity | `#6750A4` light primary, `#D0BCFF` dark primary, lavender containers, purple illustrations | Immediate brand recognition |
| Curved / diamond headers | `bg_header_curved.xml` (400×250), `bg_header_diamond.xml` (400×180), night variants | Creates the app’s silhouette |
| Overlapping rounded list surface | `rounded_top_surface.xml` with `-32dp` list margin on Fund Data and Fund Report | Makes lists feel like sheets rising over the header |
| Large rounded cards | 24dp radius on home feature cards; 16dp on list rows | Soft, approachable tone |
| Flat geometric illustrations | Four fixed-palette vector scenes: coin emblem, token stack, trend chart, student group | Strongest ownable brand asset |
| Borel greeting | `borel.ttf`, 32sp, autosize 14–32sp on Home only | Human, class-project warmth |
| Pill primary action | Welcome `Begin`: 56dp high, 32dp radius, 16sp bold | Friendly call to action |
| Material 3 foundation | Explicit M3 colour roles, MaterialCardView, MaterialButton, TextInputLayout, MaterialSwitch | Keeps the redesign realistic in XML |

### 2.3 Navigation and screen inventory

| # | Screen / surface | Layout | Controller | Entry point | Purpose |
|---|---|---|---|---|---|
| 1 | System splash | `Theme.Kas_App.Starting` | platform | App launch | Brand moment while the process starts |
| 2 | Welcome | `activity_welcome.xml` | `WelcomeActivity` | Launcher | Brand introduction, CSV import, Begin |
| 3 | Home | `activity_main.xml` | `MainActivity` | Begin | Hub for the three sections and profile |
| 4 | Fund Data | `activity_fund_data.xml` | `FundDataActivity` | Home card / Open | Student list, add student, open payments |
| 5 | Fund Report | `activity_fund_report.xml` | `FundReportActivity` | Home card / Open | Balance, income/expense totals, ledger |
| 6 | Credits | `activity_credits.xml` | `CreditsActivity` | Home card / Open | Developer credits |
| 7 | Profile / settings sheet | `activity_popup.xml` | `BottomSheetDialog` in `MainActivity` | Toolbar avatar | Logout, dark mode, EN/ID |
| 8 | Student payment sheet | `bottom_sheet_payment.xml` | `StudentPaymentBottomSheet` | Student row | Payment history + add payment |
| 9 | Add / edit student dialog | `dialog_add_student.xml` | AppCompat `AlertDialog` | FAB / student overflow | One name field |
| 10 | Add / edit ledger dialog | `dialog_add_ledger.xml` | AppCompat `AlertDialog` | FAB / ledger overflow | Date, description, type, amount |
| 11 | Student action menu | Material dialog items | `FundDataActivity` | Student overflow | Edit name, delete |
| 12 | Ledger action menu | Material dialog items | `FundReportActivity` | Row tap / overflow | Edit, delete |
| 13 | Payment action menu | Material dialog items | `StudentPaymentBottomSheet` | Payment row / overflow | Edit, delete |
| 14 | Delete confirmations | `MaterialAlertDialogBuilder` | several | action menus / swipe | Confirm destructive action |
| 15 | Date picker | platform `DatePickerDialog` | `FundReportActivity` | date field | Choose ledger date |
| 16 | Toast feedback | platform toast | several | save/delete/errors | Transient confirmation |

### 2.4 What already works well

1. **Distinct silhouette.** The curved/diamond header plus overlapping rounded sheet is more memorable than a stock Material app.
2. **Consistent illustration family.** The four main vector scenes share a palette, line weight, rounded geometry, and flat style.
3. **Clear top-level navigation.** Three large, labelled cards on Home are easy to understand; each has both a card tap and an `Open` button.
4. **Sensible accounting wording.** “Credit (Expense)” and “Debit (Income)” prevent the classic debit/credit confusion, and ledger amounts carry both a sign and a colour.
5. **Keyboard work on the payment sheet.** `KeyboardInsets` and the edge-to-edge payment-sheet theme deliberately keep the focused amount field and Save button above the IME on Android 15.
6. **Accessibility basics exist.** Content descriptions are present for illustrations, the profile button, close, overflow actions, and student avatars; colour is not the only income/expense cue because `+` and `−` are also shown.
7. **Data safety is visible in behaviour.** Delete is always confirmed (or blocked when a student has payment history), and failed validation keeps the dialog open.

### 2.5 Main design weaknesses at a glance

| Theme | Summary |
|---|---|
| Dark theme | White toolbar text, white summary labels, and the white balance amount sit on light-lavender `#D0BCFF`, producing computed ratios of 1.15–1.70:1. |
| Colour semantics | Income/expense colours are hard-coded (`#2E7D32`, `Color.RED`) and fail AA on one theme each; payment history uses primary purple for the same money-in that the ledger shows in green. |
| Hierarchy | Fund Report places key financial totals at 12sp with 25% white, while the 30sp balance is the only strong element. |
| Localisation | Welcome, Credits, the payment sheet, the add-student dialog, month names, and manifest labels remain English in Indonesian mode. |
| States | No empty state exists for students, ledger, or payment history; a new user sees a blank list and may think data failed to load. |
| Lists | No bottom padding on either main list, so the FAB can cover the last row; touch targets for row actions are 32–40dp. |
| Consistency | Three toolbars, two dialog families, six corner radii, three button heights, and three font families, with only one shared text style (the toolbar title). |
| Responsiveness | No `layout-sw600dp`, `layout-land`, or dimension resources; several fixed heights and a 300dp Welcome illustration. |

---

## 3. Existing Design System

### 3.1 Theme layers

| Layer | File | Definition / role |
|---|---|---|
| Base light theme | `app/src/main/res/values/themes.xml` | `Base.Theme.Kas_App` extends `Theme.Material3.DayNight.NoActionBar`; explicitly maps 12 colour roles to the light palette; sets Plus Jakarta Sans and the global text-input style |
| Base dark theme | `app/src/main/res/values-night/themes.xml` | Same style name, re-maps the same 12 roles to the dark palette |
| App theme | `values/themes.xml` | `Theme.Kas_App` extends `Base.Theme.Kas_App`; used by `application` |
| Splash theme | `values/themes.xml` | `Theme.Kas_App.Starting` extends `Theme.SplashScreen`; fixed `#6650A3` background, `splash_emblem` icon, post-splash `Theme.Kas_App` |
| Toolbar title | `values/themes.xml` | `TextAppearance.Kas_App.ToolbarTitle` extends `TitleLarge`; bold, onSurface, Plus Jakarta Sans |
| Avatar mask | `values/themes.xml` | `ShapeAppearance.Kas_App.Circle`; 50% rounded |
| Profile sheet overlay | `values/themes.xml` | `ThemeOverlay.Kas_App.ProfileSheet` extends `ThemeOverlay.Material3.BottomSheetDialog`; empty body |
| Payment sheet overlay | `values/themes.xml` | `ThemeOverlay.Kas_App.PaymentSheet`; `enableEdgeToEdge=true`, transparent navigation bar |
| Text input | `values/themes.xml` | `Widget.App.TextInputLayout.OutlinedBox`; four 12dp corners; applied globally through `textInputStyle` |

### 3.2 Theme token mapping

| Theme attribute | Light resource / value | Dark resource / value |
|---|---|---|
| `colorPrimary` | `md_theme_light_primary` `#6750A4` | `md_theme_dark_primary` `#D0BCFF` |
| `colorOnPrimary` | `md_theme_light_onPrimary` `#FFFFFF` | `md_theme_dark_onPrimary` `#381E72` |
| `colorPrimaryContainer` | `md_theme_light_primaryContainer` `#EADDFF` | `md_theme_dark_primaryContainer` `#4F378B` |
| `colorOnPrimaryContainer` | `md_theme_light_onPrimaryContainer` `#21005D` | `md_theme_dark_onPrimaryContainer` `#EADDFF` |
| `colorSecondary` | `md_theme_light_secondary` `#625B71` | `md_theme_dark_secondary` `#CCC2DC` |
| `colorOnSecondary` | `md_theme_light_onSecondary` `#FFFFFF` | `md_theme_dark_onSecondary` `#332D41` |
| `colorSecondaryContainer` | `md_theme_light_secondaryContainer` `#E8DEF8` | `md_theme_dark_secondaryContainer` `#4A4458` |
| `colorOnSecondaryContainer` | `md_theme_light_onSecondaryContainer` `#1D192B` | `md_theme_dark_onSecondaryContainer` `#E8DEF8` |
| `colorSurface` | `md_theme_light_surface` `#FEF7FF` | `md_theme_dark_surface` `#141218` |
| `colorOnSurface` | `md_theme_light_onSurface` `#1D1B20` | `md_theme_dark_onSurface` `#E6E0E9` |
| `colorSurfaceVariant` | `md_theme_light_surfaceVariant` `#E7E0EC` | `md_theme_dark_surfaceVariant` `#49454F` |
| `colorOnSurfaceVariant` | `md_theme_light_onSurfaceVariant` `#49454F` | `md_theme_dark_onSurfaceVariant` `#CAC4D0` |

`colorOutlineVariant` is used by `item_ledger.xml`, `item_student.xml`, and the payment-sheet divider but is **not** declared in `colors.xml` or either theme. It therefore falls back to the Material 3 default outline-variant role. Any redesign must define it explicitly to avoid drifting outlines.

### 3.3 Typography resources

| Resource | File | Details |
|---|---|---|
| Plus Jakarta Sans family | `app/src/main/res/font/plus_jakarta_sans.xml` | Declares only weight 400 (`plus_jakarta_sans_regular.ttf`) and 700 (`plus_jakarta_sans_bold.ttf`) |
| Plus Jakarta Sans italic | `plus_jakarta_sans_italic.ttf` | Present in `res/font`, **not** referenced by the family XML |
| Borel | `borel.ttf` | Handwriting/script face, applied only to `tvGreeting` on Home (XML plus a code fallback in `MainActivity`) |
| Whitney | `whitney_regular.otf` | Applied only to the Welcome title; 7,416 bytes, a very small subset that must not be assumed to cover new copy |
| Global family | `values/themes.xml` | `fontFamily` and `android:fontFamily` set to `@font/plus_jakarta_sans`; all screens inherit it unless overridden |

### 3.4 Shape, elevation and component tokens

| Token | Current values | Where |
|---|---|---|
| List-row radius | 16dp | `item_student.xml`, `item_ledger.xml` |
| Profile card radius | 16dp | `activity_popup.xml` |
| Input radius | 12dp | global `textInputStyle`; also repeated inline in `dialog_add_ledger.xml`, `dialog_add_student.xml`, `bottom_sheet_payment.xml` |
| Rounded input background | 12dp | `bg_rounded_input.xml` for spinners |
| Home feature card radius | 24dp | `activity_main.xml` (three cards) |
| Credits card radius | 24dp | `activity_credits.xml` |
| Rising list surface radius | 24dp top corners | `rounded_top_surface.xml` |
| Profile sheet radius | 28dp top corners | `bg_popup_sheet.xml` |
| Welcome button radius | 32dp | `activity_welcome.xml` |
| Logout button radius | 20dp | `activity_popup.xml` |
| Payment Save radius | 12dp | `bottom_sheet_payment.xml` |
| Home card elevation | 4dp | `activity_main.xml` |
| Credits card elevation | 2dp | `activity_credits.xml` |
| List / profile card elevation | 0dp | `item_*.xml`, `activity_popup.xml` |
| Outline | 1dp `?attr/colorOutlineVariant` | list rows |
| Sheet divider | 1dp `?attr/colorOutlineVariant` | `bottom_sheet_payment.xml` |
| FAB | Material default (56dp), framework `ic_input_add` | Fund Data, Fund Report |
| Toolbar height | `?attr/actionBarSize` | all toolbars |

### 3.5 Layout containers

| Container | Used by | Notes |
|---|---|---|
| `CoordinatorLayout` + `AppBarLayout` + `MaterialToolbar` | Home | Only screen with `fitsSystemWindows="true"` on the AppBar |
| `NestedScrollView` + `ConstraintLayout` | Home content | `fillViewport="true"`, header/card overlap via negative margins |
| `ConstraintLayout` | Welcome, Fund Data, Fund Report, Credits | Absolute scaffolding with fixed header heights |
| `LinearLayout` | Credits card, profile sheet, dialogs, payment sheet | Vertical/horizontal stacks |
| `RecyclerView` + `LinearLayoutManager` | student list, ledger list, payment history | No `ItemDecoration`; spacing comes from item margins |
| `MaterialCardView` | rows, feature cards, credits, profile rows | Mixed default and explicit backgrounds |
| `NestedScrollView` | payment sheet | Deliberate keyboard accommodation |
| `BottomSheetDialog` / `BottomSheetDialogFragment` | profile sheet, payment sheet | Custom corner backgrounds, edge-to-edge payment overlay |

### 3.6 Design-token gaps

1. **No `dimens.xml`.** Every margin, padding, size, and radius is an inline literal.
2. **No `styles.xml`.** There is no shared `TextAppearance` for section labels, card titles, body, captions, or amounts.
3. **No semantic colour roles.** Income/expense colours live in Java; no `colorIncome` / `colorExpense` resources exist for either theme.
4. **No spacing scale.** Values jump between 2, 4, 8, 12, 16, 20, 24, 32, 48, 56, and 60dp without a named scale.
5. **No surface-container tokens.** Card backgrounds rely on Material defaults in some places and `colorSurface` in others, so hierarchy is not explicit.
6. **No illustration theme policy.** The shipped illustrations use a fixed palette; legacy `illustration_*` drawables use theme attributes but are unused.
7. **No responsive resources.** There is no `layout-land`, `layout-sw600dp`, `values-sw600dp`, or `values-dimens`.

---

## 4. Color Palette

### 4.1 Core Material 3 roles

| Role | Light | Dark | Primary current use |
|---|---|---|---|
| Primary | `#6750A4` | `#D0BCFF` | Header fills, Credits toolbar, links, primary buttons, selected controls |
| On primary | `#FFFFFF` | `#381E72` | Button labels, home greeting, dark-theme expected text on primary |
| Primary container | `#EADDFF` | `#4F378B` | Welcome gradient start |
| On primary container | `#21005D` | `#EADDFF` | Defined but barely used directly |
| Secondary | `#625B71` | `#CCC2DC` | Tonal-button container source in M3 |
| On secondary | `#FFFFFF` | `#332D41` | Tonal-button label |
| Secondary container | `#E8DEF8` | `#4A4458` | Tonal buttons (`Open`) |
| On secondary container | `#1D192B` | `#E8DEF8` | Tonal-button label |
| Surface | `#FEF7FF` | `#141218` | Screen background, list-row background, sheets |
| On surface | `#1D1B20` | `#E6E0E9` | Titles, body, row text, icons |
| Surface variant | `#E7E0EC` | `#49454F` | Profile cards, spinner fill |
| On surface variant | `#49454F` | `#CAC4D0` | Captions, dates, labels, overflow icons |

### 4.2 Additional and fixed colours

| Resource / literal | Value | Alpha | Usage | Adapts to theme? |
|---|---|---|---|---|
| `black` | `#FF000000` | 100% | Defined only | n/a |
| `white` | `#FFFFFFFF` | 100% | Toolbar titles/icons, report balance | No |
| `translucent_white` | `#40FFFFFF` | 25% | Welcome wave overlays, report summary labels | No |
| `translucent_purple` | `#806650A3` | 50% | **Unused** | No |
| `splash_background` | `#6650A3` | 100% | System splash background | No |
| `ic_launcher_background` | `#6650A3` | 100% | Adaptive launcher background | No |
| `profile_sheet_background` | `#FF121212` | 100% | **Unused** (profile palette) | No |
| `profile_card_background` | `#FF1E1E1E` | 100% | **Unused** | No |
| `profile_text_primary` | `#FFFFFFFF` | 100% | **Unused** | No |
| `profile_text_secondary` | `#B3FFFFFF` | 70% | **Unused** | No |
| `profile_accent` | `#03DAC6` | 100% | **Unused** (electric cyan) | No |
| `profile_on_accent` | `#FF000000` | 100% | **Unused** | No |
| Welcome title literal | `#E1BEE7` | 100% | `activity_welcome.xml` only | No |
| Welcome subtitle literal | `#3949AB` | 100% | `activity_welcome.xml` only | No |
| Income literal | `#2E7D32` | 100% | `LedgerAdapter.java` debit amounts | No |
| Expense literal | `Color.RED` = `#FFFF0000` | 100% | `LedgerAdapter.java` credit amounts | No |

The profile palette (`#121212`, `#1E1E1E`, white, 70% white, cyan) is defined in `colors.xml` but no layout or Java class references it. The profile sheet actually uses theme attributes `colorSurface` and `colorSurfaceVariant`. It is dead design residue and should not be treated as the current profile style.

### 4.3 Illustration palette

| Colour | Value | Role in illustrations |
|---|---|---|
| Deep plum | `#4E337D` | Outlines, darkest shapes, base bars, hair/body |
| Mid purple | `#8B6BCE` | Main coin/body fill, middle bars |
| Soft lavender | `#B59AE9` | Secondary fills, small coins, back profiles |
| Pale lavender | `#C9B6F3` | Lightest coins, bars, sparkles |
| Pale ring lavender | `#E8DCFF` | Second alpha ring of the splash emblem only |
| Near-white lavender | `#F1EBFF` | Inner coin disc, highlights, front face |
| White | `#FFFFFF` | Line details, plus/trend strokes, eye dots |

The four shipped illustration drawables use only these fixed colours (plus white), with no `?attr` references. `#E8DCFF` appears only in `splash_emblem.xml`; `fund_report.xml` does not use `#F1EBFF`, and `credits.xml` does not use white. They do **not** change in dark mode.

### 4.4 Computed contrast (WCAG 2.x)

Ratios below are computed from sRGB values; alpha colours are composited over the stated background first. AA requires 4.5:1 for normal text and 3:1 for large text (≥18pt regular or ≥14pt bold).

| Foreground / background | Ratio | AA normal | AA large | Where it matters |
|---|---:|---|---|---|
| `#FFFFFF` on `#6750A4` | **6.44:1** | Pass | Pass | Light toolbars, primary buttons |
| `#FFFFFF` on `#D0BCFF` | **1.70:1** | Fail | Fail | Dark Fund Data / Fund Report / Credits toolbar; Logout label |
| `#381E72` on `#D0BCFF` | **7.71:1** | Pass | Pass | Home greeting in dark mode; Material onPrimary |
| `#40FFFFFF` over `#6750A4` → `#8D7CBB` | **1.76:1** | Fail | Fail | Fund Report summary labels in light mode |
| `#40FFFFFF` over `#D0BCFF` → `#DCCDFF` | **1.15:1** | Fail | Fail | Fund Report summary labels in dark mode |
| `#FFFFFF` on `#6750A4` (30sp balance) | **6.44:1** | Pass | Pass | Fund Report balance, light |
| `#FFFFFF` on `#D0BCFF` (30sp balance) | **1.70:1** | Fail | Fail | Fund Report balance, dark |
| `#2E7D32` on `#FEF7FF` | **4.87:1** | Pass | Pass | Income amount, light |
| `#2E7D32` on `#141218` | **3.63:1** | Fail | Pass | Income amount, dark (16sp bold is below the “large” threshold) |
| `#FF0000` on `#FEF7FF` | **3.80:1** | Fail | Pass | Expense amount, light |
| `#FF0000` on `#141218` | **4.65:1** | Pass | Pass | Expense amount, dark |
| `#E1BEE7` on `#EADDFF` | **1.28:1** | Fail | Fail | Welcome title, light gradient start |
| `#E1BEE7` on `#6750A4` | **3.90:1** | Fail | Pass | Welcome title, light gradient end |
| `#E1BEE7` on `#4F378B` | **5.65:1** | Pass | Pass | Welcome title, dark gradient start |
| `#E1BEE7` on `#D0BCFF` | **1.03:1** | Fail | Fail | Welcome title, dark gradient end |
| `#3949AB` on `#EADDFF` | **6.00:1** | Pass | Pass | Welcome subtitle, light start |
| `#3949AB` on `#6750A4` | **1.20:1** | Fail | Fail | Welcome subtitle, light end |
| `#3949AB` on `#4F378B` | **1.21:1** | Fail | Fail | Welcome subtitle, dark start |
| `#3949AB` on `#D0BCFF` | **4.54:1** | Pass | Pass | Welcome subtitle, dark end |
| `#1D1B20` on `#FEF7FF` | **16.23:1** | Pass | Pass | Body text, light |
| `#E6E0E9` on `#141218` | **14.35:1** | Pass | Pass | Body text, dark |
| `#49454F` on `#E7E0EC` | **7.24:1** | Pass | Pass | Captions, light |
| `#CAC4D0` on `#49454F` | **5.48:1** | Pass | Pass | Captions, dark |

### 4.5 Recommended semantic colours (for the redesign, not current state)

These candidates meet AA on their stated surfaces and preserve the existing green/red reading:

| Token | Light | On light surface | Dark | On dark surface |
|---|---|---|---|---|
| Income / Pemasukan | `#1B5E20` | **7.48:1** on `#FEF7FF` | `#A5D6A7` | **11.31:1** on `#141218` |
| Expense / Pengeluaran | `#B3261E` | **6.21:1** on `#FEF7FF` | `#F2B8B5` | **10.89:1** on `#141218` |

Stitch may propose its own hues, but it must keep the semantic distinction, meet the same contrast thresholds, and retain `+` / `−` signs so meaning is not colour-only.

---

## 5. Typography

### 5.1 Families

| Family | Resource | Weights available | Current use | Notes |
|---|---|---|---|---|
| Plus Jakarta Sans | `@font/plus_jakarta_sans` | 400 regular, 700 bold | Global theme default: body, titles, buttons, dialogs, rows | Italic file exists but is not declared |
| Borel | `@font/borel` | single style | Home greeting `tvGreeting` only | Applied in XML and re-applied in `MainActivity` as a device fallback |
| Whitney | `@font/whitney_regular` | single regular | Welcome title only | 7.4 KB subset; do not reuse for arbitrary copy without checking glyph coverage |

### 5.2 Current hierarchy

| Role | Size | Weight / style | Colour | Font | Location |
|---|---:|---|---|---|---|
| Welcome brand title | 67sp | regular | `#E1BEE7` | Whitney | `activity_welcome.xml` |
| Welcome subtitle | 14sp | `textFontWeight=500` | `#3949AB` | theme (Plus Jakarta) | `activity_welcome.xml` |
| Begin button | 16sp | 700 | `?attr/colorOnPrimary` | theme | `activity_welcome.xml` |
| Home greeting | 32sp (autosize 14–32sp, 2sp steps) | Borel | `?attr/colorOnPrimary` | Borel | `activity_main.xml` |
| Home toolbar title | Material `TitleLarge`, bold | bold | `?attr/colorOnSurface` | Plus Jakarta | `TextAppearance.Kas_App.ToolbarTitle` |
| Home card title | 20sp | bold | inherited onSurface | Plus Jakarta | `activity_main.xml` |
| Home card subtitle | 14sp | regular | inherited onSurface | Plus Jakarta | `activity_main.xml` |
| Open button | Material 3 label | tonal default | onSecondaryContainer | Plus Jakarta | `Widget.Material3.Button.TonalButton` |
| Sub-screen toolbar title | Material toolbar default | default | `@color/white` | Plus Jakarta | Fund Data / Report / Credits |
| Report income/expense label | 12sp, letterSpacing 0.1 | regular | `@color/translucent_white` | Plus Jakarta | `activity_fund_report.xml` |
| Report balance label | 12sp, all caps, letterSpacing 0.1 | regular | `@color/translucent_white` | Plus Jakarta | `activity_fund_report.xml` |
| Report balance amount | 30sp | bold | `@color/white` | Plus Jakarta | `activity_fund_report.xml` |
| Ledger date | 12sp | regular | onSurfaceVariant | Plus Jakarta | `item_ledger.xml` |
| Ledger description | 16sp | regular | onSurface | Plus Jakarta | `item_ledger.xml` |
| Ledger amount | 16sp | bold | `#2E7D32` / `Color.RED` | Plus Jakarta | `item_ledger.xml`, `LedgerAdapter` |
| Student name | 18sp | regular | onSurface | Plus Jakarta | `item_student.xml` |
| Payment month/year | 16sp | regular | onSurface | Plus Jakarta | `item_payment.xml` |
| Payment amount | 16sp | bold | `?attr/colorPrimary` | Plus Jakarta | `item_payment.xml` |
| Sheet student title | 22sp | bold | onSurface | Plus Jakarta | `bottom_sheet_payment.xml` |
| Sheet section label | 14sp, all caps, letterSpacing 0.1 | bold | onSurfaceVariant | Plus Jakarta | `bottom_sheet_payment.xml` |
| Profile title | 20sp | bold | onSurface | Plus Jakarta | `activity_popup.xml` |
| Profile admin name | 18sp | bold | onSurface | Plus Jakarta | `activity_popup.xml` |
| Profile row label | 16sp | regular | onSurface | Plus Jakarta | `activity_popup.xml` |
| EN / ID labels | 14sp | bold | onSurfaceVariant | Plus Jakarta | `activity_popup.xml` |
| Credits heading | 20sp | bold | `?attr/colorPrimary` | Plus Jakarta | `activity_credits.xml` |
| Credits name | 18sp | regular | onSurface | Plus Jakarta | `activity_credits.xml` |
| Dialog title / content / buttons | platform AppCompat alert defaults | default | theme defaults | Plus Jakarta inherited | dialogs |

### 5.3 Typography findings

1. **The hierarchy is bottom-heavy.** Most UI text sits at 12–18sp. There is no 24sp or 28sp heading level, and the only large text is the 32sp Home greeting, the 30sp report balance, and the 67sp Welcome title. On Fund Report, the most important labels (income and expense) are the *smallest* text on the screen.
2. **Borel is used once, correctly.** It gives Home personality but is not an appropriate face for financial values. At its 14sp autosize floor, long Indonesian greetings may become hard to read; this needs visual verification.
3. **The weight system is binary.** Only 400 and 700 exist. `textFontWeight="500"` on the Welcome subtitle has no matching 500 face, so the result may be synthetic or ignored depending on API level. Italic is downloaded but not wired.
4. **Whitney is a tiny subset.** It renders “ClassFund” at 67sp; extending it to other copy is risky without glyph checks.
5. **Almost no shared text styles.** `TextAppearance.Kas_App.ToolbarTitle` is the only named text style; every other screen independently sets size, colour, weight, spacing, and caps. This is the root cause of the 12/14/16/18/20/22/30/32/67sp spread.
6. **Toolbar typography is inconsistent.** Home uses a purpose-built `TitleLarge` bold onSurface style; the three sub-screens use the Material toolbar default appearance and hard-coded white.
7. **Uppercase labels are used twice** (`Payment History`, `Add New Payment`, `Current balance`) with 0.1 letter spacing. This is coherent but should become a named style if kept.
8. **Numbers are not tabular.** The currency text uses the default proportional figures, so amounts in the ledger and payment history do not line up vertically. A tabular-figure treatment would improve scanability if the font supports it.

### 5.4 Proposed type scale for Stitch

| Token | Suggested spec | Use |
|---|---|---|
| `displayBrand` | Borel 32sp / 36 line, autosize minimum 20sp | Home greeting only |
| `welcomeTitle` | 56–64sp, tight line height | Welcome, Whitney only if glyphs verified; otherwise Plus Jakarta Sans Bold |
| `headline` | 28sp / 34, bold | Screen hero balance |
| `titleLarge` | 22sp / 28, bold | Sheet title, report section title |
| `title` | 20sp / 26, bold | Card titles, dialog titles |
| `subtitle` | 16sp / 22, semibold or bold | Row primary, emphasized labels |
| `body` | 16sp / 24, regular | Descriptions, names, dialog fields |
| `bodySmall` | 14sp / 20, regular | Supporting copy, captions |
| `label` | 12sp / 16, bold, 0.08–0.1 tracking, uppercase | Section labels only |
| `amount` | 16–20sp, bold, tabular figures | Ledger and payment amounts |
| `amountHero` | 32–40sp, bold, tabular figures | Current balance |

If a 500/600 weight is desired, add a real medium font file to the family rather than relying on synthesis.

---

## 6. Spacing and Layout Principles

### 6.1 Observed spacing values

There is no formal scale, but the implementation clusters around a 4dp grid with common stops at 4, 8, 12, 16, 20, 24, and 32dp.

| Context | Value(s) | Evidence |
|---|---|---|
| Screen gutter, Home / Welcome / Credits | 24dp | `layout_marginHorizontal="24dp"`, root `padding="24dp"`, card `layout_margin="24dp"` |
| Screen gutter, list rows | 16dp | `item_*.xml` `layout_marginHorizontal="16dp"` |
| Dialog / form padding | 20dp | `dialog_add_*.xml`, `activity_popup` |
| Payment sheet padding | 24dp | `bottom_sheet_payment.xml` |
| Home card internal padding | 20dp | `activity_main.xml` |
| List row internal padding | 16dp | `item_*.xml` |
| Credits card padding | 24dp | `activity_credits.xml` |
| Space between home cards | 20dp | `layout_marginTop="20dp"` |
| Space between list rows | 8dp vertical margin + intrinsic | `layout_marginVertical="8dp"` |
| Space between profile cards | 12dp | `layout_marginTop="12dp"` |
| Title-to-subtitle | 4dp | Home cards |
| Label-to-content | 8dp | Payment history, profile |
| Field-to-field | 12dp | Ledger dialog |
| Button-to-field | 24dp | Save Payment |
| List top breathing room | 24dp | `paddingTop="24dp"` |
| List bottom breathing room | **none** | No `paddingBottom`; `clipToPadding="false"` |
| Header/card overlap | −60dp (Home), −32dp (lists) | Negative top margins |
| Welcome bottom button margin | 16dp | `layout_marginBottom="16dp"` |

### 6.2 Component dimensions

| Component | Dimension | Evidence |
|---|---|---|
| Home feature card | 180dp tall, full width minus 48dp | `activity_main.xml` |
| Home header | 250dp tall | `viewHeaderBackground` |
| Fund Data header | 180dp tall | `bg_header_diamond` container |
| Fund Report header | 240dp tall | `viewHeaderBackground` |
| Welcome illustration | 300×300dp | `ivWelcome` |
| Splash emblem intrinsic | 264×264dp | `splash_emblem.xml` |
| Fund Data / Report header illustration | 48×48dp | toolbar `ImageView` |
| Home card illustration | 100×100dp | `ivDataFund`, `ivReportFund`, `ivCreditsImg` |
| Credits illustration | 80×80dp | `ivCreditsHeader` |
| Student avatar | 40×40dp container, 24dp visible icon (8dp padding) | `item_student.xml` |
| Profile avatar | 40×40dp | `btnProfile` |
| Toolbar height | `?attr/actionBarSize` (~56dp) | all toolbars |
| Welcome Begin button | match width × 56dp | `activity_welcome.xml` |
| Save Payment button | match width × 60dp | `bottom_sheet_payment.xml` |
| Spinner height | 48dp | `bottom_sheet_payment.xml` |
| Payment history list | 180dp fixed height | `rvPaymentHistory` |
| Ledger overflow action | 32×32dp | `ivLedgerActions` |
| Student / payment overflow | 40×40dp | `ivStudentActions`, `ivPaymentActions` |
| Profile close | 40×40dp | `btnClosePopup` |
| FAB | default 56dp, 16dp margin | both FABs |
| Divider | 1dp, 24dp vertical margin | payment sheet |

### 6.3 Screen scaffolding

| Screen | Header | Overlap | Scroll behaviour | Bottom treatment |
|---|---|---|---|---|
| Welcome | None (full gradient) | n/a | None | Begin button inside 24dp padding |
| Home | 250dp curved primary | First card `-60dp` | `NestedScrollView` | Last card 24dp bottom margin |
| Fund Data | 180dp diamond primary | List `-32dp` | `RecyclerView` only | No bottom padding; FAB overlays |
| Fund Report | 240dp diamond primary | List `-32dp` | `RecyclerView` only | No bottom padding; FAB overlays |
| Credits | Primary toolbar | n/a | None | Card 24dp margin |
| Profile sheet | 28dp rounded top | n/a | None | 20dp padding |
| Payment sheet | 28dp material sheet | n/a | `NestedScrollView` | 24dp padding; IME-aware |

### 6.4 Layout principles currently in force

1. **Big colour block, then content sheet.** Sub-screens use a tall coloured header and pull the content list upward to create a layered, tactile transition.
2. **Cards float on the surface.** Feature cards use elevation; list rows use a 1dp outline instead, so the app mixes “elevated card” and “outlined card” languages.
3. **Content is centred in heroes.** The Home greeting, Fund Report summary, and toolbar titles are horizontally centred. Lists and forms are left-aligned.
4. **Forms are top-aligned and compact.** Dialogs use 20dp padding and 12dp field gaps; there is no sectioning or helper text.
5. **Fixed heights are used for visual rhythm.** Feature cards and the payment history are hard-fixed at 180dp; this produces consistent screenshots but brittle text scaling.

### 6.5 Responsive and edge-to-edge observations

- No `layout-land` or `layout-sw600dp` variants exist.
- No `values-sw600dp` or `values-dimens` resources exist.
- `targetSdk 35` means edge-to-edge is enforced on Android 15+, but only the Home `AppBarLayout` declares `fitsSystemWindows="true"`.
- Fund Data, Fund Report, Credits, and Welcome place content at the parent top without an explicit status-bar inset; their toolbars or gradient may render under the status bar. This is **potential** until seen on a device.
- The two main RecyclerViews set `clipToPadding="false"` and `paddingTop` only; they do not reserve space for the FAB or the navigation bar.
- The profile bottom sheet has no scroll container and no explicit bottom inset; the payment sheet does handle the IME explicitly.

---

## 7. Reusable UI Components

| Component | Implementation | Current spec | Used on | Notes / risks |
|---|---|---|---|---|
| Curved header | `bg_header_curved.xml` / `drawable-night` | 400×250 vector, primary fill, bottom curve | Home | Scales to width; height fixed at 250dp |
| Diamond header | `bg_header_diamond.xml` / `drawable-night` | 400×180 vector, primary fill, pointed bottom | Fund Data, Fund Report | Stretches to 180/240dp; point becomes shallower on tall headers |
| Welcome gradient | `bg_welcome_wavy.xml` / `drawable-night` | 400×800 linear gradient + two 25% white waves | Welcome | Fixed endpoint colours, not theme attributes |
| Rising surface | `rounded_top_surface.xml` | `colorSurface`, 24dp top corners | Fund Data, Fund Report lists | Correctly adapts to theme |
| Feature card | `MaterialCardView` | 180dp tall, 24dp radius, 4dp elevation, 20dp padding, 100dp illustration | Home ×3 | Fixed height; default surface colour not explicit |
| List row card | `MaterialCardView` | 16dp radius, 0dp elevation, 1dp outline, 16dp margins, 8dp vertical | Student, ledger | Full-width minus 32dp; no bottom inset |
| Payment row | `LinearLayout` | transparent + selectable background, 12dp vertical / 4dp horizontal padding | Payment history | Not a card; different visual language from other rows |
| Credits card | `MaterialCardView` | 24dp radius, 2dp elevation, 24dp padding, 80dp illustration | Credits | No scroll container |
| Tonal button | `Widget.Material3.Button.TonalButton` | M3 default height and shape | Home `Open` ×3 | Container from secondaryContainer |
| Primary button | `MaterialButton` | Welcome 56dp/32dp radius; Save 60dp/12dp; Logout default/20dp | Welcome, payment, profile | Three competing primary-button treatments |
| FAB | `FloatingActionButton` | 56dp, primary container, framework plus icon | Fund Data, Fund Report | No extended label; overlaps list bottom |
| Text input | `TextInputLayout.OutlinedBox` | 12dp corners, hint, error via `setError` | Dialogs, payment sheet | Global style plus repeated inline overrides |
| Spinner | platform `Spinner` + `bg_rounded_input` | 48dp high, surfaceVariant, 12dp radius | Payment sheet month/year | No custom item layout; default text/padding may not match |
| Toolbar | `MaterialToolbar` | `actionBarSize`; Home transparent/onSurface; sub-screens transparent white or primary white | All screens | Three different treatments |
| Profile row | `MaterialCardView` + `LinearLayout` | surfaceVariant, 16dp radius, 16dp padding, 24dp icon | Profile sheet | Clear row pattern; switches at end |
| Switch | `MaterialSwitch` | M3 default | Profile sheet | No custom thumb/track colours |
| Divider | `View` | 1dp `colorOutlineVariant`, 24dp vertical margin | Payment sheet | Only explicit divider in the app |
| Section label | `TextView` | 14sp bold, all caps, 0.1 tracking, onSurfaceVariant | Payment sheet | Reusable in principle, not tokenised |
| Dialog | AppCompat `AlertDialog` + 20dp content layout | Platform title, message, text buttons | Add/edit student, add/edit ledger | Different family from confirmations |
| Confirmation | `MaterialAlertDialogBuilder` | M3 title/body/text buttons | Delete actions, action menus | Destructive buttons use primary colour |
| Bottom sheet | `BottomSheetDialog` / `BottomSheetDialogFragment` | 28dp top corners, surface, 20/24dp padding | Profile, payment | Profile has no scroll/inset handling |
| Empty state | **Does not exist** | — | — | Students, ledger, payment history all render blank |
| Loading state | **Does not exist** | — | — | No skeletons/progress; DB reads are synchronous |
| Snackbar | **Not used** | — | — | Feedback is Toast only |

### 7.1 Component-state coverage

| State | Current coverage |
|---|---|
| Pressed | Material ripple on cards, buttons, rows, and borderless icons |
| Disabled | Platform defaults only; no custom disabled colours |
| Error | `EditText.setError(...)` on text fields; toast on save failure |
| Empty | Missing on all lists |
| Loading | Missing |
| Success | Toast only |
| Destructive | Confirmation dialog, but no error-colour styling |
| Selected | Radio buttons and switches use platform/M3 defaults |
| Keyboard focus | Fields scroll into view only in the payment sheet; dialogs rely on platform behaviour |

---

## 8. Screen-by-Screen Design Analysis

Each subsection records the required purpose, components, hierarchy, layout, colour/type, icons/illustrations, interaction, light/dark behaviour, and weaknesses. “Confirmed” means the implementation itself; “potential” means the effect needs a screenshot or device.

### 8.1 System splash

| Aspect | Current implementation |
|---|---|
| Purpose | Brand moment while the process starts; hands off to Welcome. |
| Components | System-controlled splash window; no XML layout. Theme `Theme.Kas_App.Starting` (`parent="Theme.SplashScreen"`). |
| Hierarchy | Single centred emblem on a solid field. No text or actions. |
| Layout | System centres the icon; `windowSplashScreenAnimatedIcon=@drawable/splash_emblem`; `windowSplashScreenBackground=@color/splash_background`; `postSplashScreenTheme=@style/Theme.Kas_App`. |
| Colour / type | Background `#6650A3`; emblem uses fixed purple/lavender palette. No typography. |
| Icons / illustrations | `splash_emblem.xml`, intrinsic 264×264dp, coin with a “C” and sparkles. |
| Interaction | None; automatic transition. |
| Light / dark | Identical in both themes because the starting theme is a single fixed palette. It does not inherit the night palette. |
| Weaknesses | Brand continuity is good, but the splash is theme-independent: the same light-lavender emblem on `#6650A3` appears for both saved themes, so it does not reflect the user’s light/dark choice. The system may mask the 264dp artwork to a circular/rounded icon shape on Android 12+, potentially cropping outer sparkles (potential). The detail level is high for a splash mark (potential legibility issue). |

### 8.2 Welcome

| Aspect | Current implementation |
|---|---|
| Purpose | Introduce the app, import the bundled student CSV once, and send the user to Home. |
| Components | Root `ConstraintLayout` with gradient/wave background; 300dp emblem; 67sp title; 14sp subtitle; full-width 56dp `Begin` button. |
| Hierarchy | Emblem → brand title → subtitle → primary button. The title is by far the strongest element; the subtitle is small and low-contrast in parts of the gradient. |
| Layout | 24dp padding all round; vertical packed chain anchored top and bottom; 24dp image-to-title gap; 8dp title-to-subtitle; 16dp button bottom margin. No scroll container. |
| Colour / type | Background gradient `#EADDFF → #6750A4` (light) or `#4F378B → #D0BCFF` (dark), plus two 25% white wave shapes. Title `#E1BEE7` Whitney 67sp. Subtitle `#3949AB` 14sp, `textFontWeight=500`. Button `?attr/colorPrimary` with `?attr/colorOnPrimary`. |
| Icons / illustrations | `splash_emblem.xml` at 300dp, `fitCenter`; content description “ClassFund coin emblem”. |
| Interaction | `Begin` starts `MainActivity` and finishes Welcome. System back exits the app. No language or theme control on this screen. |
| Light / dark | Gradient has a night variant; title/subtitle literals do not. In light mode the title is 1.28:1 at the top of the gradient and 3.90:1 at the bottom. In dark mode it is 5.65:1 at the top and 1.03:1 at the bottom. Subtitle is 6.00:1 at the light top but 1.20:1 at the light bottom, and 1.21:1 at the dark top but 4.54:1 at the dark bottom. `Begin` itself adapts correctly. |
| Weaknesses | **Confirmed:** fixed colours cause severe contrast loss across the gradient (C-04); hard-coded English copy (C-06). **Potential:** 300dp image + 67sp title + button in a non-scrolling layout can clip on short/landscape screens (P-03). The title face is a 7.4KB Whitney subset. |

### 8.3 Main / Home

| Aspect | Current implementation |
|---|---|
| Purpose | Hub for Fund Data, Fund Report, Credits, and the profile/settings sheet. |
| Components | `CoordinatorLayout`; transparent `AppBarLayout` + `MaterialToolbar`; 40dp circular profile avatar; `NestedScrollView`; 250dp curved header; 32sp Borel greeting; three 180dp feature cards each with 20sp title, 14sp subtitle, tonal `Open` button, 100dp illustration. |
| Hierarchy | Greeting hero → three equal cards. Each card has a strong title and illustration; the subtitle is secondary; `Open` is a third-level action even though the whole card is clickable. |
| Layout | Toolbar `contentInsetStart=24dp`, `contentInsetEnd=16dp`, avatar 8dp from the end. Header 250dp; first card `-60dp` top margin; 20dp gaps; 24dp side margins and bottom margin. Card content is a `ConstraintLayout` with 20dp padding. |
| Colour / type | Header uses `bg_header_curved` (light `#6750A4`, dark `#D0BCFF`). Greeting uses `?attr/colorOnPrimary` (white light, `#381E72` dark; 7.71:1 in dark). Card title/subtitle inherit onSurface. Tonal `Open` uses secondary container. |
| Icons / illustrations | `ic_profile_person` in a 40dp circular `ShapeableImageView`; `fund_data.xml`, `fund_report.xml`, `credits.xml` at 100dp. |
| Interaction | Toolbar avatar opens the profile `BottomSheetDialog`. Card body and `Open` both launch the same activity. Screen scrolls vertically. |
| Light / dark | Header and greeting adapt; illustrations stay fixed purple. Card surfaces rely on Material defaults rather than explicit colours, so dark-mode card contrast is unverified. The purple artwork sits on a dark card; the lighter lavender shapes remain visible, but the deep plum `#4E337D` has low separation from `#141218` (potential). |
| Weaknesses | **Confirmed:** no explicit card surface token; duplicate click targets (card + button); no shared card text style. **Potential:** 180dp fixed height can crowd long Indonesian subtitles or large font scales (P-04); the Borel greeting at its 14sp autosize floor may be hard to read (P-12); illustration/card proportions feel sparse at wide widths (P-04). |

### 8.4 Fund Data

| Aspect | Current implementation |
|---|---|
| Purpose | Browse students, add a student, open a student’s payment sheet, or manage a student. |
| Components | 180dp diamond header; transparent `MaterialToolbar` with centred title, white back arrow, white title, 48dp `fund_data.xml` illustration at the end; `RecyclerView` with student cards; FAB. |
| Hierarchy | Toolbar title → list of students → FAB. There is no summary, count, search, or section label. Each row: avatar, 18sp name, overflow, chevron. |
| Layout | Header 180dp; list starts at 148dp (`-32dp` overlap) with `rounded_top_surface`, 24dp top padding, no bottom padding; rows have 16dp side margins, 8dp vertical margins, 16dp internal padding, 40dp avatar with 16dp end margin. FAB 16dp from bottom/end. |
| Colour / type | Header `?attr/colorPrimary` via `bg_header_diamond`; toolbar title/nav icon `@color/white`; rows `colorSurface` + 1dp outline; avatar `?attr/colorPrimary`; name onSurface. In dark mode the header becomes `#D0BCFF` while the white title/back arrow stays white: **1.70:1**. |
| Icons / illustrations | 48dp `fund_data.xml` in the toolbar; `ic_student` inside a 40dp avatar; `ic_more_vert` 40dp; `ic_chevron_right` 24dp; framework `ic_input_add` in the FAB. |
| Interaction | Row tap opens `StudentPaymentBottomSheet`; overflow opens a Material action menu (edit name, delete); FAB opens the add-student dialog. Add-student validation keeps the dialog open on empty input. |
| Light / dark | Header, rows, and avatar adapt. Toolbar white does not. The fixed-palette illustration is purple on purple, relying on its white detail lines for separation. |
| Weaknesses | **Confirmed:** dark toolbar contrast (C-01); no empty state (C-09); no bottom list padding / FAB overlap risk (C-10); 40dp overflow is below 48dp (C-11); English add dialog (C-06). **Potential:** the 180dp header contains only the toolbar, leaving a large empty diamond band (P-13); the 48dp illustration may lose its fine white linework (P-13); the centred title can collide with the 48dp end illustration on narrow screens (P-07). |

### 8.5 Fund Report

| Aspect | Current implementation |
|---|---|
| Purpose | Show the authoritative balance, total income, total expenses, and the ledger; add/edit/delete entries. |
| Components | 240dp diamond header; transparent toolbar with centred white title, white back arrow, 48dp `fund_report.xml`; three summary labels and the balance amount; `RecyclerView` of ledger cards; FAB. |
| Hierarchy | Toolbar → income/expense at 12sp → “CURRENT BALANCE” at 12sp → 30sp balance → ledger list. The balance is visually dominant; the supporting totals are almost captions. |
| Layout | Header 240dp; summary stack starts 4dp below the toolbar with 2–4dp gaps, centred horizontally; list starts at 208dp (`-32dp` overlap), 24dp top padding, no bottom padding; cards 16dp radius, 16dp padding, date above description, amount and 32dp overflow on the right. |
| Colour / type | Header `?attr/colorPrimary` (night `#D0BCFF`); summary labels `@color/translucent_white` 12sp with 0.1 tracking; balance white 30sp bold; ledger date onSurfaceVariant 12sp; description onSurface 16sp; amount 16sp bold with `+` green or `−` red. |
| Icons / illustrations | 48dp `fund_report.xml`; 32dp `ic_more_vert`; FAB framework plus. |
| Interaction | Row tap or overflow opens a Material menu (edit/delete); swipe left prompts delete; FAB opens the ledger dialog; date field opens a platform `DatePickerDialog`; deleting refreshes balance, totals, and list. |
| Light / dark | Header, list, and text adapt, but white labels/amount do not. Summary labels are 1.76:1 in light and 1.15:1 in dark; the balance is 1.70:1 in dark. Income green is 3.63:1 on dark surface; expense red is 3.80:1 on light surface. |
| Weaknesses | **Confirmed:** multiple contrast failures (C-02, C-03, C-05); critical totals at 12sp/25% alpha; no empty state; no bottom padding/FAB overlap; no legend for income/expense colours; English only in places. **Potential:** the 240dp header leaves a large empty purple area under the summary (P-13); long ID totals may wrap; a 30sp amount with `maxLines=1` and no ellipsize/autosize can clip (P-06); swipe-to-delete has no visible affordance (P-17). |

### 8.6 Credits

| Aspect | Current implementation |
|---|---|
| Purpose | List the four student developers. |
| Components | Primary-coloured `MaterialToolbar` with back arrow and title “Credits”; one `MaterialCardView` with 80dp illustration, “Developers” heading, four 18sp names. |
| Hierarchy | Toolbar → illustration → heading → names. Flat and centred; no roles or descriptions. |
| Layout | Root `colorSurface`; toolbar full width at top; card 24dp margins, 24dp radius, 2dp elevation, 24dp padding; illustration centered with 8dp bottom margin; heading 16dp below; names 12dp apart. No scroll. |
| Colour / type | Toolbar `?attr/colorPrimary` + `@color/white` title; card default surface; heading `?attr/colorPrimary` 20sp bold; names onSurface 18sp. |
| Icons / illustrations | 80dp `credits.xml`; back arrow from `?attr/homeAsUpIndicator`. |
| Interaction | Back arrow only. Names are not clickable. |
| Light / dark | Toolbar white-on-primary passes in light (6.44:1) and fails in dark (1.70:1). Card, heading, and names adapt. Illustration stays fixed. |
| Weaknesses | **Confirmed:** dark toolbar contrast (C-01); hard-coded toolbar title and “Developers” (C-06); toolbar is not centred like Fund Data/Report (C-14); no scroll container (P-05). **Potential:** sparse card with a lot of unused width on tablets (P-04); no grouping/roles, but adding roles would be new content and should not be invented. |

### 8.7 Profile / settings bottom sheet

| Aspect | Current implementation |
|---|---|
| Purpose | Account/settings surface for logout, dark mode, and language. |
| Components | `BottomSheetDialog` with `activity_popup.xml`: 20dp padding; title “ClassFund” + 40dp close; three `surfaceVariant` cards: Admin + Logout, Dark Mode switch, Language EN/switch/ID. |
| Hierarchy | Title → account/logout → dark mode → language. The destructive Logout is the most visually prominent button. |
| Layout | 28dp top corners; wrap content; cards 16dp radius, 16dp internal padding, 12dp gaps; header row with weighted title and close. No scroll, no drag handle, no explicit bottom inset. |
| Colour / type | Sheet `?attr/colorSurface`; cards `?attr/colorSurfaceVariant`; icons onSurface; labels 16sp; title 20sp bold; admin 18sp bold; Logout `?attr/colorPrimary` with hard-coded white text. |
| Icons / illustrations | `ic_close` 40dp, `ic_moon` 24dp, `ic_globe` 24dp. |
| Interaction | Close dismisses. Logout dismisses, shows a toast, and starts Welcome with `FLAG_ACTIVITY_NEW_TASK` and `FLAG_ACTIVITY_CLEAR_TASK`. Dark-mode switch saves and recreates the activity. Language switch (off = EN, on = ID) saves and recreates. |
| Light / dark | Cards and text adapt; Logout text stays white on `#D0BCFF` in dark (1.70:1). The unused `profile_*` palette is not involved. |
| Weaknesses | **Confirmed:** Logout contrast (C-01 family); no confirmation before logout; the single switch flanked by EN/ID is ambiguous about which side is active; 40dp close target; title says “ClassFund” rather than “Profile”/“Settings”; no scroll (P-05). **Potential:** bottom content may sit under the navigation bar (P-20). |

### 8.8 Student payment bottom sheet

| Aspect | Current implementation |
|---|---|
| Purpose | Show one student’s payment history and add a monthly payment. |
| Components | `BottomSheetDialogFragment`; `NestedScrollView`; 24dp padding; 22sp student name; “PAYMENT HISTORY” label; fixed 180dp `RecyclerView`; 1dp divider; “ADD NEW PAYMENT” label; month/year spinners; amount `TextInputLayout`; 60dp Save button. |
| Hierarchy | Student name → history → form. The student name is the sheet title; there is no explicit “Add payment” heading, only a small all-caps label. |
| Layout | Sheet wraps content; 24dp all-round; 24dp label top gap; 8dp list gap; 24dp divider margins; 16dp spinner gap; two equal-weight 48dp spinners with 12dp gap; 16dp amount gap; 24dp Save gap. IME handling via `KeyboardInsets`. |
| Colour / type | Surface background; title onSurface 22sp bold; labels onSurfaceVariant 14sp bold caps with 0.1 tracking; spinners `surfaceVariant` with 12dp corners; amount field outlined; Save default filled M3. |
| Icons / illustrations | No illustration. 40dp `ic_more_vert` per payment row. |
| Interaction | Payment row/overflow opens edit/delete. Month and year use platform spinners. Amount is digits-only. Save validates, writes payment + mirrored ledger debit in one transaction, refreshes history, clears the field, and toasts. Edit inflates the same layout inside an `AlertDialog` with history/form chrome hidden. |
| Light / dark | Surface, labels, fields, and button adapt. Spinner text/dropdown colours and the custom rounded background are unverified in dark. |
| Weaknesses | **Confirmed:** hard-coded English labels (C-06); no empty state (C-09); fixed 180dp history leaves a blank block with few payments (C-16); months remain English in ID (C-07). **Potential:** platform spinner text may be clipped or visually mismatched inside the 48dp rounded field (P-09); the near-full-height sheet plus keyboard may be dense on small phones (P-05); the edit dialog reuses a sheet layout with hidden sections, which may look oversized (P-10). |

### 8.9 Add / edit student dialog

| Aspect | Current implementation |
|---|---|
| Purpose | Add a student name or rename an existing student. |
| Components | AppCompat `AlertDialog`; `dialog_add_student.xml` with a single outlined `TextInputLayout`; title; Save/Add and Cancel text buttons. |
| Hierarchy | Dialog title → one field → actions. |
| Layout | 20dp content padding; 12dp field corners; no helper text or character count. |
| Colour / type | Platform dialog surface and title; outlined field uses theme colours; default dialog typography. |
| Icons / illustrations | None. |
| Interaction | Add path uses hard-coded “Add New Student”, “Add”, “Cancel”; edit path uses string resources (`title_edit_student`, `action_save`, `cancel`). Empty input calls `setError(...)` and keeps the dialog open. Renaming to the same value dismisses without a write. |
| Light / dark | Inherits the AppCompat/Material dialog theme; actual dark appearance is unverified. |
| Weaknesses | **Confirmed:** add path is English in ID mode while edit path is translated (C-06); AppCompat dialog differs in implementation from Material confirmations (C-12); hint is hard-coded. **Potential:** keyboard coverage on Android 15 edge-to-edge (P-02); no visible field label beyond the floating hint. |

### 8.10 Add / edit ledger dialog

| Aspect | Current implementation |
|---|---|
| Purpose | Add or edit a cash-book entry: date, description, income/expense type, amount. |
| Components | AppCompat `AlertDialog`; `dialog_add_ledger.xml`: date field (outlined, not typable, opens picker), description, “Transaction Type” label, horizontal radio group (Credit/Expense, Debit/Income), amount field; Save/Cancel. |
| Hierarchy | Date → description → type → amount → actions. The type control is a low-emphasis inline label + radios. |
| Layout | 20dp padding; 12dp gaps; radio group horizontal with 16dp between buttons; amount field 12dp below; no section grouping. |
| Colour / type | Default dialog text colours; outlined fields; default radio button tint; default text buttons. |
| Icons / illustrations | No icon for the date field; no end icon is configured, even though the code attaches an end-icon click listener. |
| Interaction | Date defaults to today and opens a `DatePickerDialog`; description is required; amount is digits-only and validated; Debit/Income is checked by default; insufficient balance is reported on the amount field; save refreshes the report. |
| Light / dark | Inherits dialog/date-picker themes; unverified. |
| Weaknesses | **Confirmed:** AppCompat dialog family (C-12); English/Indonesian radio labels are long in a horizontal group; no date affordance icon. **Potential:** the horizontal radio group with “Kredit (Pengeluaran)” + “Debit (Pemasukan)” can overflow or wrap awkwardly on narrow screens/large fonts (P-08); the ~330dp form can be covered by the IME on Android 15 because it lacks the payment sheet’s inset handling (P-02); raw digit input is hard to scan (improvement). |

### 8.11 Student action menu and delete confirmations

| Aspect | Current implementation |
|---|---|
| Purpose | Manage a student without accidentally opening their payment history. |
| Components | `MaterialAlertDialogBuilder` with title = student name; item list “Edit name” / “Delete”; Cancel. Delete confirmation uses `confirm_delete_student_no_history`; if payments exist, a blocked dialog explains the history is retained. |
| Hierarchy | Title → two actions → Cancel. |
| Layout | Platform Material dialog; no custom layout. |
| Colour / type | Default Material dialog typography and colours; destructive action uses the default primary text colour. |
| Icons / illustrations | None. |
| Interaction | Edit opens the student dialog; Delete opens a confirmation or the blocked explanation. Blocked dialog uses `android.R.string.ok`, which follows the system locale rather than the app-selected locale. |
| Light / dark | Material dialog theme adapts; unverified. |
| Weaknesses | **Confirmed:** destructive action is not styled as destructive; mixed app/system locale for the OK button; no icons. **Potential:** a very long student name produces an oversized title; the two-step delete is safe but visually heavy (P-10). |

### 8.12 Ledger action menu and delete confirmation

| Aspect | Current implementation |
|---|---|
| Purpose | Edit or delete a ledger row, including after a swipe. |
| Components | `MaterialAlertDialogBuilder` title “description - amount”; items Edit/Delete; Cancel; delete confirmation; swipe-to-delete with restore on cancel. |
| Hierarchy | Title (dynamic, potentially long) → actions → cancel. |
| Layout | Platform Material dialog. |
| Colour / type | Default Material dialog; destructive action not error-coloured. |
| Icons / illustrations | None. |
| Interaction | Row tap or overflow opens the menu. Swipe left opens the same delete confirmation. If the dialog is dismissed without deleting, `notifyItemChanged` restores the swiped row. |
| Light / dark | Adapts via Material dialog theme; unverified. |
| Weaknesses | **Confirmed:** dynamic title can be long and is not constrained; destructive action not styled; no swipe background/icon cue. **Potential:** title truncation or multi-line height shift (P-06); accidental swipe (P-17). |

### 8.13 Payment action menu, edit, and delete

| Aspect | Current implementation |
|---|---|
| Purpose | Edit or delete one monthly payment while keeping its mirrored ledger row in sync. |
| Components | `MaterialAlertDialogBuilder` title “Month Year - Rp amount”; Edit/Delete; Cancel; edit dialog inflates `bottom_sheet_payment.xml` with title/history/divider/add-label/save hidden; delete confirmation. |
| Hierarchy | Dynamic title → actions; edit dialog reduces to month/year/amount. |
| Layout | Edit dialog keeps the sheet’s 24dp padding and 48dp spinners; standard dialog buttons are added by the builder. |
| Colour / type | Default Material dialog; fields outlined; amount 16sp; no custom section heading after hidden views. |
| Icons / illustrations | None. |
| Interaction | Edit validates month/year/amount and updates the payment + ledger transactionally; delete removes both; history refreshes. |
| Light / dark | Dialog inherits theme; payment sheet layout inside the dialog may not match the dialog surface/insets. |
| Weaknesses | **Confirmed:** reusing a bottom-sheet layout inside an AlertDialog creates a visually different, oversized form (C-12); hard-coded labels remain in the reused layout (C-06). **Potential:** keyboard coverage (P-02); dialog width/padding makes the spinners cramped (P-09). |

### 8.14 Date picker and toast feedback

| Aspect | Current implementation |
|---|---|
| Purpose | Choose a ledger date; report success/failure. |
| Components | Platform `DatePickerDialog`; platform `Toast`. |
| Hierarchy | System modal / transient overlay. |
| Layout | Platform. |
| Colour / type | Platform theme; not explicitly styled to match ClassFund. |
| Icons / illustrations | Platform. |
| Interaction | Date picker writes `yyyy-MM-dd` into the field; cancel leaves the previous date. Toasts report saved/updated/deleted/error. |
| Light / dark | Follows platform dialog and toast theming; may not match the app’s Material 3 shapes and colours. |
| Weaknesses | **Confirmed:** no custom theming or date format localisation; feedback is transient and easy to miss. **Potential:** date picker/dialog visual mismatch (P-10); users may expect `dd/MM/yyyy` in Indonesian mode while the field shows ISO `yyyy-MM-dd` (localisation improvement). |

---

## 9. Illustrations and Iconography

### 9.1 The four shipped illustrations

| Asset | Path | Intrinsic size | Placements | Colours | Style |
|---|---|---|---:|---|---|
| Splash emblem | `app/src/main/res/drawable/splash_emblem.xml` | 264×264dp | System splash icon; Welcome hero at 300dp | `#F1EBFF`, `#E8DCFF`, `#C9B6F3`, `#B59AE9`, `#8B6BCE`, `#4E337D`, white | Coin with a “C”, concentric rings, two small coins, sparkles; soft outer glow represented by alpha rings |
| Fund Data token stack | `app/src/main/res/drawable/fund_data.xml` | 224×224dp | Home card at 100dp; Fund Data toolbar at 48dp | Shared palette; no `#E8DCFF` | Three stacked coin/token cylinders with a fourth coin bearing a plus sign; small floating dots |
| Fund Report trend chart | `app/src/main/res/drawable/fund_report.xml` | 220×220dp | Home card at 100dp; Fund Report toolbar at 48dp | Shared palette; no `#E8DCFF` or `#F1EBFF` | Four rounded bars increasing in height, a white trend line with arrow, baseline bar, two data dots |
| Credits student profiles | `app/src/main/res/drawable/credits.xml` | 224×224dp | Home card at 100dp; Credits screen at 80dp | Shared palette; no `#E8DCFF` or white | Three abstract student figures (two behind, one front) with round heads and torso shapes plus floating dots |

**Shared visual grammar**

- Flat vector, no gradients, no drop shadows (the source SVG shadows were deliberately removed during conversion).
- Rounded caps and joins; capsule/coin geometry rather than sharp corners.
- White outlines used as interior detail, especially on the coin and trend line.
- Faceless figures, simple geometry, friendly and neutral.
- Generous internal negative space; the scene occupies a roughly square safe area.
- Consistent purple ramp (`#4E337D` → `#8B6BCE` → `#B59AE9` → `#C9B6F3` → `#F1EBFF`, with `#E8DCFF` as an extra splash ring); no semantic income/expense colour in the artwork.
- Source SVGs live in `design/illustrations/`; `ref/ClassFund_Finance_Illustration_Sheet.png` is a conceptual reference sheet, not a runtime screenshot or a shipped asset.

### 9.2 How the illustrations integrate today

| Placement | Scale | Integration quality |
|---|---:|---|
| Splash | System-sized (264dp source) | Strong brand moment; may be masked by the platform (potential). |
| Welcome | 300dp | Dominant; makes the screen recognisably ClassFund, but contributes to vertical overflow risk. |
| Home cards | 100dp | Well balanced with the 180dp card; each section has a distinct visual. |
| Fund Data toolbar | 48dp | Small for the line detail; the artwork is purple on a purple header and relies on white lines. |
| Fund Report toolbar | 48dp | Same issue as Fund Data. |
| Credits | 80dp | Clear and readable at this size. |

The illustrations are **not** theme-aware. In dark mode they keep the same palette, which is acceptable for brand consistency but means the deep plum `#4E337D` can sit close to `#141218`; the lighter lavender shapes carry the silhouette. The unused `illustration_*.xml` files are 120dp theme-attribute versions of similar concepts and are **not referenced anywhere**; do not treat them as live assets.

### 9.3 Icon inventory

| Icon | Path | Intrinsic | Used at | Tint / colour | Role |
|---|---|---|---:|---|---|
| Profile person | `ic_profile_person.xml` | 24dp | 40dp avatar | `?attr/colorOnSurface`, plus `app:tint` | Open profile |
| Close | `ic_close.xml` | 24dp | 40dp button | `?attr/colorOnSurface` | Dismiss sheet |
| Moon | `ic_moon.xml` | 24dp | 24dp | `?attr/colorOnSurface` | Dark-mode row |
| Globe | `ic_globe.xml` | 24dp, stroke-based | 24dp | `?attr/colorOnSurface` | Language row |
| Student | `ic_student.xml` | 24dp | 40dp container with 8dp padding | `?attr/colorPrimary` + `app:tint` | Student row |
| More vertical | `ic_more_vert.xml` | 24dp | 32dp (ledger), 40dp (student/payment) | `?attr/colorOnSurfaceVariant` | Overflow actions |
| Chevron right | `ic_chevron_right.xml` | 24dp | 24dp | `?attr/colorOnSurfaceVariant` | Row affordance |
| Add | `@android:drawable/ic_input_add` | framework | FAB | FAB content colour | Add student/ledger |
| Back | `?attr/homeAsUpIndicator` | framework | Toolbar | `@color/white` on Fund Data/Report; default on Credits | Navigate up |

**Icon-style observations**

- Mostly 24dp Material-style filled/outline vectors, tinted at the usage site.
- The globe is a stroke-only custom icon, visually lighter than the filled moon/student/person icons.
- The FAB uses the framework plus rather than the custom vector family, so its stroke weight differs from the rest.
- Some vectors hard-code `#FFFFFFFF` or `#FF000000` fills and rely on tint; this works but is fragile if reused untinted.
- Content descriptions exist for meaningful icons; decorative moon/globe are `@null`, which is appropriate.
- The student avatar’s content description is simply “Student”; with the visible name beside it, TalkBack may announce redundant information (potential).

### 9.4 Launcher and store assets

| Asset | Path | Observation |
|---|---|---|
| Adaptive icon background | `res/values/ic_launcher_background.xml` | Solid `#6650A3` |
| Adaptive icon foreground | `mipmap-*/ic_launcher_foreground.webp` and `drawable/ic_launcher_foreground.xml` | A purple finance illustration showing a house/bank, receipt with a check, dollar coin, and person; it is **not** the in-app coin “C” emblem |
| Monochrome icon | `mipmap-*/ic_launcher_monochrome.webp` | Same artwork as the foreground |
| Round icon | `mipmap-anydpi-v26/ic_launcher_round.xml` | Adaptive icon wrapper with the same background/foreground |
| Play Store image | `app/src/main/ic_launcher-playstore.png` | Same purple finance illustration on a light background |

The launcher family is purple and finance-themed, so it is not off-brand, but it does **not** use the strongest in-app mark (the coin emblem). This weakens recognition between the launcher and the splash/Welcome. Unifying the launcher foreground with `splash_emblem` is a high-value, low-risk branding improvement.

### 9.5 Unused / legacy assets

| Asset | Status |
|---|---|
| `illustration_welcome.xml`, `illustration_fund_data.xml`, `illustration_fund_report.xml`, `illustration_credits.xml` | 120dp theme-attribute variants; no references in layouts or Java |
| `logo.png` | No references |
| `spreadsheet.png` | No references |
| `image-removebg-preview.png` (repo root) | No references |
| `translucent_purple` colour | No references |
| `profile_*` colours | No references |

These should not be presented to Stitch as part of the current UI. They are cleanup candidates after a visual confirmation.

### 9.6 Illustration and icon rules for the redesign

1. Keep the four scenes and their geometric vocabulary; refine line weights and safe areas rather than replacing them with generic stock art.
2. Keep the five-purple ramp as the illustration palette; allow a dark-mode variant only if it preserves the same silhouette and does not introduce a new style.
3. Do not introduce 3D, gradient meshes, photorealism, emoji, or a new mascot.
4. Use illustrations as **spot art**, not as full-screen backgrounds, except the intentional Welcome hero.
5. Give the 48dp header illustrations enough padding and a contrasting container so their white detail survives.
6. Standardise interface icons to one stroke/fill family at 24dp, with 48dp touch targets; use the same plus icon language as the rest.
7. Use the coin emblem for the launcher foreground and monochrome icon to create one recognisable brand mark across splash, Welcome, and launcher.

---

## 10. Light and Dark Theme Guidelines

### 10.1 How theming works today

| Mechanism | Implementation | Consequence |
|---|---|---|
| Base theme | `Theme.Material3.DayNight.NoActionBar` | Platform, dialog, and widget colours follow the light/night resource sets |
| Palette | Explicit 12-role mapping in `values/themes.xml` and `values-night/themes.xml` | Predictable brand colours, but no error/outline/surface-container tokens |
| Theme selection | `MainActivity` stores `isDarkMode` in `theme_prefs`; `KasApp.onCreate()` applies `MODE_NIGHT_YES/NO`; `WelcomeActivity.attachBaseContext` also applies it | The app ignores the system dark-mode setting and always uses the saved boolean; default is light |
| Toggle behaviour | Profile switch saves, dismisses the sheet, and calls `AppCompatDelegate.setDefaultNightMode` | The activity is recreated; current scroll/dialog state is lost |
| Locale selection | Saved in `locale_prefs`; applied by `MainActivity.onCreate` and `WelcomeActivity.onCreate` via `AppCompatDelegate.setApplicationLocales` | Language change also recreates the activity and dismisses the sheet |
| Splash | Fixed `#6650A3` background and fixed emblem | Same in both themes |
| Illustrations | Fixed purple palette | Same in both themes |
| `bg_header_*` | Separate light/night drawables with explicit palette references | Header adapts |
| `bg_welcome_wavy` | Separate light/night gradient | Background adapts, text does not |
| `rounded_top_surface`, `bg_popup_sheet`, `bg_rounded_input` | `?attr/colorSurface` / `colorSurfaceVariant` | Adapt correctly |

### 10.2 What adapts and what does not

| Element | Light | Dark | Adapts? |
|---|---|---|---|
| Screen and card surfaces | `#FEF7FF` / surface roles | `#141218` | Yes |
| Body and title text | `#1D1B20` | `#E6E0E9` | Yes |
| Header background | `#6750A4` | `#D0BCFF` | Yes, but the role inversion causes contrast failure |
| Home greeting | white | `#381E72` | Yes (7.71:1) |
| Sub-screen toolbar title/back | white | white | No (1.70:1 in dark) |
| Report summary labels | 25% white | 25% white | No (1.76 / 1.15) |
| Report balance | white | white | No (1.70 in dark) |
| Welcome title/subtitle | fixed `#E1BEE7` / `#3949AB` | same | No |
| Ledger income/expense | `#2E7D32` / red | same | No |
| Payment amount | `?attr/colorPrimary` | `#D0BCFF` | Yes, but semantically inconsistent with ledger green |
| Illustrations | fixed purple | fixed purple | No |
| Splash | fixed | fixed | No |
| Launcher | fixed | fixed | No |
| Profile cards | surfaceVariant | surfaceVariant | Yes |

### 10.3 Why dark mode fails

The dark palette follows Material 3’s convention: `colorPrimary` becomes a **light** lavender (`#D0BCFF`) intended for accents and text on dark surfaces, while `colorOnPrimary` becomes a deep aubergine (`#381E72`). The app instead uses `colorPrimary` as a **large header background** on Fund Data, Fund Report, and Credits, and then paints white text and white icons on it. That combination is 1.70:1. The same inversion affects the 30sp balance and the Logout button.

The home screen avoids the problem because its greeting uses `?attr/colorOnPrimary`, which becomes `#381E72` in dark mode. The fix is not to remove the lavender header; it is to use the correct foreground role (or a darker header container) consistently.

### 10.4 Theme guidelines for the redesign

1. **Never place white text on dark `colorPrimary`.** Use `colorOnPrimary`, `colorOnPrimaryContainer`, or a dedicated header container token.
2. **Keep the lavender identity** by using `#D0BCFF` as an accent, border, illustration highlight, or a *light* header only with aubergine text.
3. **Prefer `primaryContainer` + `onPrimaryContainer` for large dark-mode fills.** `#4F378B` + `#EADDFF` is 7.23:1 and still clearly purple.
4. **Define explicit surface containers** (`surface`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`) so cards and sheets have a deliberate dark hierarchy instead of relying on Material defaults.
5. **Define semantic income/expense tokens for both themes.** Do not compute them in Java at bind time.
6. **Make welcome text theme-aware.** Use `?attr/colorOnPrimary`/`colorOnPrimaryContainer` on the gradient, or change the gradient so the fixed colours have guaranteed contrast.
7. **Keep both themes complete.** No screen may be designed only in light mode; every illustration, icon, field, switch, and dialog needs a dark counterpart or a verified tint.
8. **Preserve the user’s saved choice.** Do not switch to system-following mode as part of a visual redesign unless that behaviour change is separately approved.
9. **Re-check contrast after Stitch output.** Ratios must be re-computed from the final hex values, not assumed from the palette.

### 10.5 Dark-mode acceptance criteria

- All text and icons on headers ≥4.5:1 (normal) or ≥3:1 (large).
- All body text on surfaces ≥4.5:1.
- Income/expense colours ≥4.5:1 on their card surfaces and accompanied by `+`/`−`.
- No pure white on `#D0BCFF` anywhere.
- Outlines visible against `#141218` (do not use a dark outline).
- Dialog, bottom sheet, date picker, spinner dropdown, and toast all match the theme.
- Splash and launcher remain legible and on-brand.

---

## 11. English and Indonesian Localization Considerations

### 11.1 Mechanism

- English lives in `app/src/main/res/values/strings.xml`; Indonesian in `app/src/main/res/values-in/strings.xml`.
- `MainActivity` stores `app_lang` in `locale_prefs` (`en` or `id`) and applies it with `AppCompatDelegate.setApplicationLocales`.
- If no preference exists, the device language is used; anything other than `en`/`id` falls back to `en`.
- The profile sheet uses a single `MaterialSwitch`: off = English, on = Indonesian, with `EN` and `ID` labels on either side.
- The app does not use a locale picker dialog, per-app language settings deep link, or a language list.
- Most app-owned strings are translated. The gaps below are the exceptions.

### 11.2 Confirmed hard-coded English visible to users

| Location | String | When visible |
|---|---|---|
| `activity_welcome.xml` | `Manage your class funds easily` | Always; no ID resource |
| `activity_welcome.xml` | `Begin` | Always |
| `activity_credits.xml` | `Developers` | Always |
| `activity_credits.xml` + `CreditsActivity` toolbar | `Credits` (`app:title`) | Always |
| `bottom_sheet_payment.xml` | `Payment History` | Always |
| `bottom_sheet_payment.xml` | `Add New Payment` | Always |
| `bottom_sheet_payment.xml` | `Amount (Rp)` | Always (hint) |
| `bottom_sheet_payment.xml` | `Save Payment` | Always |
| `dialog_add_student.xml` | `Student Name` | Add/edit student hint |
| `FundDataActivity.java` | `Add New Student`, `Add`, `Cancel` | Add-student dialog only |
| `AndroidManifest.xml` | `Fund Data`, `Fund Report`, `Credits` activity labels | Recents/task metadata |
| `DatabaseHelper.PAYMENT_MONTHS` | English month names | Spinner and payment history in both languages |

The following hard-coded texts are **placeholders overwritten at runtime** and normally never appear: `Student Name` in `item_student.xml` and in the payment-sheet title, `2024-01-01`, `Description`, `Rp 0` in `item_ledger.xml`, and `January 2026`, `Rp 50.000` in `item_payment.xml`. They still matter for layout previews and should be replaced with `tools:text` or real string resources.

### 11.3 Other localisation inconsistencies

| Issue | Detail | Impact |
|---|---|---|
| `error_insufficient_balance` | Defined as `Saldo tidak mencukupi` in **both** `values/strings.xml` and `values-in/strings.xml` | English users see an Indonesian sentence |
| Month storage | `PAYMENT_MONTHS` values are persisted in the database and used for ordering | Translating the stored values would corrupt data; design a display-only mapping instead |
| Date display | Ledger and date field show ISO `yyyy-MM-dd` | Indonesian users may expect `dd/MM/yyyy`; changing display must not change the stored format |
| Credits names | Names are proper nouns and do not need translation; “Developers” does | Names should stay as-is; translate only the heading |
| System OK | `android.R.string.ok` follows the system locale, not the app locale | An English app on an Indonesian device, or vice versa, can show a mismatched button |
| Toolbar/hint defaults | Some hard-coded labels are visually small; localisation would lengthen them further | Crowding risk in radio groups, toolbars, and fixed-height cards |

### 11.4 Actual EN/ID length comparison

| Key / surface | English | Indonesian | Observation |
|---|---|---|---|
| `welcome_back` | Welcome back! (13) | Selamat datang kembali! (23) | +77%; Borel autosize will shrink |
| `fund_data` | Fund Data (9) | Data Dana (9) | Equal |
| `fund_report` | Fund Report (11) | Laporan Dana (12) | Slightly longer |
| `fund_data_subtitle` | Manage students and payments (28) | Kelola siswa dan pembayaran (27) | Nearly equal |
| `fund_report_subtitle` | View financial ledger (22) | Lihat catatan keuangan (22) | Equal |
| `credits_subtitle` | Meet the team (13) | Tim pengembang (15) | Longer |
| `action_open` | Open (4) | Buka (4) | Equal |
| `balance_label` | Current balance (15) | Saldo saat ini (16) | Slightly longer |
| `total_income_label` | Total Income %1$s (19 + amount) | Total Pemasukan %1$s (21 + amount) | Longer; 12sp label |
| `total_expense_label` | Total Expenses %1$s (21 + amount) | Total Pengeluaran %1$s (22 + amount) | Longer; 12sp label |
| `title_add_ledger` | Add Ledger Entry (17) | Tambah Catatan Kas (19) | Longer dialog title |
| `title_edit_ledger` | Edit Ledger Entry (18) | Ubah Catatan Kas (18) | Equal |
| `label_credit` | Credit (Expense) (16) | Kredit (Pengeluaran) (20) | +25%; horizontal radio |
| `label_debit` | Debit (Income) (14) | Debit (Pemasukan) (18) | +29%; horizontal radio |
| `title_delete_blocked` | Student not deleted (18) | Siswa tidak dihapus (21) | Longer |
| `confirm_delete_student_no_history` | Long sentence with `%1$s` | Longer sentence with `%1$s` | Both need wrapping |
| `error_student_has_history` | Long plural explanation | Longer plural explanation | Dialog body; ensure scroll/wrap |

### 11.5 Localisation design requirements for Stitch

1. Design every screen with the **Indonesian** string lengths, not English, as the worst-case baseline.
2. Allow titles and labels to wrap to two lines; do not use single-line fixed-height text for localisable copy.
3. Give the ledger type control a vertical or segmented layout that accommodates `Kredit (Pengeluaran)` and `Debit (Pemasukan)` without clipping.
4. Keep money formatting `Rp 121.000` in both languages; do not localise the currency symbol or separators.
5. Localise month **display** names without changing stored English values. The payment history and spinner can show `Januari` while the database still stores `January`.
6. Prefer a labelled language control (“English / Bahasa Indonesia” or a segmented control) over a switch whose on/off meaning must be inferred.
7. Avoid text baked into images. The illustrations contain no words, which is good; keep it that way.
8. Keep the brand name “ClassFund” untranslated.
9. Add translated empty-state, loading, and error copy; none exists today.
10. Verify with font scale 1.0 and 1.3–2.0; the fixed 180dp cards and 48dp spinners are the first places to clip.

---

## 12. Confirmed UI/UX Issues

Confirmed issues are supported by the actual resource/code values, not by a guessed screenshot. Contrast ratios are computed; layout issues are confirmed structurally where the implementation makes the problem inevitable or highly likely. Severity is an audit judgement, not a runtime measurement.

| ID | Area | Confirmed issue | Evidence | Impact | Severity |
|---|---|---|---|---|---|
| C-01 | Dark theme | White toolbar titles sit on `#D0BCFF` (1.70:1) on Fund Data, Fund Report, and Credits; Fund Data/Report also set a white back arrow, and the Logout label has the same pair | `toolbar`/`toolbarReport` `titleTextColor=@color/white` + `navigationIconTint=@color/white`; Credits toolbar `?attr/colorPrimary` with `titleTextColor=@color/white` but no explicit arrow tint; `btnLogout` white text on primary; dark primary `#D0BCFF` | Key navigation/actions become hard to read in dark mode; the Credits back arrow is also low-contrast but its exact tint depends on the toolbar default (potential, P-26) | High |
| C-02 | Fund Report | The income/expense/balance labels are 12sp at 25% white on the header: 1.76:1 in light, 1.15:1 in dark | `translucent_white` + 12sp labels in `activity_fund_report.xml`; header `bg_header_diamond` | The financial context for the balance is effectively invisible | High |
| C-03 | Fund Report | The 30sp white balance is 1.70:1 on the dark header | `tvBalanceAmount` `@color/white`; dark header `#D0BCFF` | The most important number is unreadable in dark mode | High |
| C-04 | Welcome | Fixed title `#E1BEE7` and subtitle `#3949AB` do not adapt to the gradient; across the four gradient endpoints the title ranges 1.03–5.65:1 (failing at 1.28 light-start and 1.03 dark-end) and the subtitle falls to 1.20–1.21:1 at two endpoints | `activity_welcome.xml` literals + `bg_welcome_wavy` endpoints | Brand copy disappears in parts of both themes | High |
| C-05 | Ledger | Income/expense colours are hard-coded in Java: `#2E7D32` is 3.63:1 on dark surface, `#FF0000` is 3.80:1 on light surface; the payment history shows money-in in primary purple instead | `LedgerAdapter.java`; `item_payment.xml` `?attr/colorPrimary` | Amount readability fails AA on one theme; the same concept has two colours | High |
| C-06 | Localisation | User-visible English remains in Welcome, Credits, payment sheet, add-student dialog, and manifest labels | Hard-coded `android:text` / builder strings; see §11.2 | Indonesian mode is visibly mixed-language | High |
| C-07 | Localisation | Payment month names are English in the spinner and history even in Indonesian mode | `DatabaseHelper.PAYMENT_MONTHS`; `PaymentAdapter` prints the raw stored month | Monthly records look untranslated | Medium |
| C-08 | Localisation | `error_insufficient_balance` is Indonesian in the English resource file | `values/strings.xml` line for the error | English users see an Indonesian error | Medium |
| C-09 | Lists | No empty state exists for student list, ledger, or payment history | `StudentAdapter`, `LedgerAdapter`, `PaymentAdapter` return 0 items; no placeholder view in any layout | A new/cleared database shows a blank surface that looks broken | High |
| C-10 | Lists | No bottom padding on the fund lists; the FAB can cover the last row | RecyclerViews only `paddingTop=24dp`; `clipToPadding=false`; FAB 56dp + 16dp margin | Last row’s amount/actions can be obscured | High |
| C-11 | Accessibility | Row actions are below the 48dp touch-target recommendation: ledger overflow 32dp, student/payment overflow and profile close 40dp | `ivLedgerActions` 32dp; `ivStudentActions`, `ivPaymentActions`, `btnClosePopup` 40dp | Harder to tap, especially one-handed | Medium |
| C-12 | Components | Two dialog families are used: AppCompat `AlertDialog.Builder` for add/edit forms, `MaterialAlertDialogBuilder` for action/confirmation dialogs; add-student also mixes hard-coded and translated button text | `FundDataActivity`, `FundReportActivity`, `StudentPaymentBottomSheet` | Potential visual mismatch and inconsistent tone; confirmed implementation inconsistency | Medium |
| C-13 | Components | Primary buttons have three treatments: Welcome 56dp/32dp, Save 60dp/12dp, Logout default height/20dp | `activity_welcome.xml`, `bottom_sheet_payment.xml`, `activity_popup.xml` | The app lacks a clear primary-action hierarchy | Medium |
| C-14 | Navigation | Toolbars are inconsistent: Home uses custom TitleLarge onSurface over surface; Fund Data/Report use centred white titles over transparent headers; Credits uses a non-centred white title on a solid primary bar | the four layouts | Screens feel assembled from different apps | Medium |
| C-15 | Financial hierarchy | Fund Report gives the smallest type (12sp) to income/expense and the largest (30sp) to balance; income/expense amounts are part of the tiny labels, not separate values | `activity_fund_report.xml` | Users cannot quickly compare totals; the summary reads as captions | High |
| C-16 | Payment history | The list is fixed at 180dp; with zero or one payment it leaves a large blank block, with many it scrolls inside the sheet | `rvPaymentHistory` `layout_height="180dp"` | Wasted space and an unbalanced sheet | Medium |
| C-17 | Branding | The launcher foreground is a different purple finance illustration (house/receipt/coin/person), not the in-app coin emblem | `mipmap-*/ic_launcher_foreground.webp`, `ic_launcher-playstore.png` vs `splash_emblem.xml` | Launcher and splash do not share a mark | Medium |
| C-18 | Design system | No `dimens.xml` and no semantic colour resources; the only shared text style is the toolbar title appearance, so body, card, section-label, and amount typography is repeated inline across layouts | `res/values` contains only colours, strings, themes, launcher background; `TextAppearance.Kas_App.ToolbarTitle` is the sole text style | Every future screen drifts further; changes are expensive | Medium |
| C-19 | Design system | `profile_*` palette, `translucent_purple`, and four `illustration_*` drawables are defined but unused | resource search finds no references | Misleading design source and dead code | Low |
| C-20 | Accessibility | `android:supportsRtl="true"` but no RTL layout review or locale resources; the UI is LTR-only in practice | manifest + LTR-only layouts | Not currently broken for EN/ID, but the flag promises more than is tested | Low |
| C-21 | Feedback | All success/failure feedback is a Toast; there is no persistent inline confirmation or Snackbar | activities/sheet use `Toast.makeText` | Easy to miss, especially for financial writes | Low–Medium |

> C-10 says “can cover” because the exact overlap depends on the list length and device height. The structural cause is confirmed: no bottom space is reserved.

---

## 13. Potential UI/UX Issues

These require a running device or screenshot to confirm. They are ordered by likely impact.

| ID | Area | Hypothesis | Why it is plausible | How to verify |
|---|---|---|---|---|
| P-01 | Edge-to-edge | Welcome, Fund Data, Fund Report, and Credits content may draw under the status bar, because only Home’s AppBar has `fitsSystemWindows="true"` and `targetSdk=35` enforces edge-to-edge | Layouts place toolbars/content at parent top without inset handling | Run on Android 15; inspect status-bar area in both themes |
| P-02 | Keyboard | Add/edit student and add/edit ledger dialogs may be covered by the keyboard, because only the payment sheet applies `KeyboardInsets`; dialogs use `AlertDialog` without explicit IME padding | `KeyboardInsets` is referenced only from `StudentPaymentBottomSheet` | Open each dialog on Android 15, focus the last field, check Save visibility |
| P-03 | Welcome | 300dp image + 67sp title + 14sp subtitle + 56dp button do not fit on short screens or landscape; the layout has no scroll | Fixed sizes, packed chain, no `ScrollView` | Test 360×640, landscape, font scale 1.3–2.0 |
| P-04 | Home | Fixed 180dp cards can clip long Indonesian subtitles or illustration/text at large font scale | Card height fixed; text constrained to a shrinking column beside 100dp illustration | Test 320dp width, font scale 1.3–2.0, both locales |
| P-05 | Credits / profile | Credits and profile sheet have no scroll container; large fonts or short screens can clip content | `LinearLayout` inside card/sheet with wrap content; no `ScrollView` | Test landscape and 200% font scale |
| P-06 | Fund Report | Large balances or totals (e.g., `Rp 1.000.000.000`) may clip or wrap awkwardly; balance is `maxLines=1` with no ellipsize/autosize | 30sp fixed, wrap_content, long formatted string | Seed large values; test 320dp width |
| P-07 | Toolbars | Centred Fund Data/Report titles may collide with the 48dp end illustration, especially with longer Indonesian titles or large fonts | Title centred, navigation icon plus end image consume both sides | Test narrow screen + ID + 200% font |
| P-08 | Ledger dialog | The horizontal radio group may overflow or wrap awkwardly with `Kredit (Pengeluaran)` and `Debit (Pemasukan)` | Fixed horizontal `RadioGroup`, 16dp gap, no width constraint | Test 320dp width + ID + 200% font |
| P-09 | Spinners | Platform spinner text and dropdown may not match the rounded 48dp field; text may clip vertically | `Spinner` with default `simple_spinner_item` inside a 48dp custom background | Inspect both themes and both locales |
| P-10 | Dialogs | AppCompat dialogs, Material confirmations, and the platform date picker may look like three different systems | Mixed dialog families; date picker not themed | Screenshot add/edit vs delete vs date picker |
| P-11 | Lists | Exact FAB/list overlap may obscure the last row’s amount or overflow | No bottom padding; FAB floats at bottom-end | Fill the list; scroll to bottom; test both lists |
| P-12 | Home | The Borel greeting at its 14sp autosize floor may be hard to read for the long Indonesian greeting | Autosize down to 14sp, script face, max one line | Screenshot ID mode on 320dp width |
| P-13 | Headers | The 180dp Fund Data header and 240dp Fund Report header may feel empty under the toolbar/summary; 48dp illustrations may lose detail | Large fixed header, little content; detailed vector at 48dp | Visual review on device |
| P-14 | System bars | Status/navigation bar icon colour is not explicitly controlled; icons may be invisible on transparent/purple areas | No `windowLightStatusBar`/nav-bar icon settings in the app themes | Check both themes on Android 15 |
| P-15 | Profile | The single language switch with EN/ID labels may be ambiguous; users may not know whether “on” means Indonesian or “switch to English” | Off = EN, on = ID, labels flank the switch | Usability test / TalkBack |
| P-16 | Profile | Logout has no confirmation and immediately clears the task | `btnLogout` calls logout directly | Accidental tap test |
| P-17 | Ledger | Swipe-to-delete has no visible affordance, background, or icon; users may trigger it accidentally | `ItemTouchHelper` with only LEFT, no custom drawing | Swipe a row; observe discoverability |
| P-18 | Accessibility | Large font/display scaling can clip fixed-height cards, spinners, and single-line balance | Fixed dp sizes and `maxLines=1` | Font scale 2.0 pass |
| P-19 | Home | Card body and `Open` button are two targets for the same action; this may confuse affordance and TalkBack order | Both have click listeners to the same activity | TalkBack traversal and tap test |
| P-20 | Profile sheet | Bottom content may sit under the gesture navigation bar because the sheet has no bottom inset padding | `activity_popup` 20dp padding, no inset handling; overlay is not explicitly edge-to-edge | Android 15 gesture-nav device |
| P-21 | Dark surfaces | Card surfaces may not separate enough from `#141218` because home/credits cards rely on Material defaults rather than explicit surface-container tokens | Only `surface`/`surfaceVariant` mapped; no container roles | Screenshot dark home/credits |
| P-22 | Splash | Android 12+ may mask the 264dp emblem and crop its outer sparkles/rings | `windowSplashScreenAnimatedIcon` uses a square vector | Launch on Android 12+ and compare |
| P-23 | Dialog width | The edit-payment dialog reuses a 24dp-padded sheet layout inside an AlertDialog, so the two 48dp spinners may be cramped or the dialog may look oversized | `showEditPaymentDialog` inflates `bottom_sheet_payment.xml` | Screenshot edit-payment on 320dp |
| P-24 | Locale switch | Changing language recreates the activity and dismisses any open sheet/dialog; unsaved form text is lost | `setApplicationLocales` in the switch listener; sheet dismissed first | Type into a form, switch language via profile |
| P-25 | Date format | ISO `yyyy-MM-dd` may be unexpected for Indonesian users | `IsoDate` and field display use ISO | Usability check with ID users |
| P-26 | Credits toolbar | The Credits back arrow has no explicit tint, so in dark mode it inherits the default toolbar colour and may be low-contrast on `#D0BCFF`; the exact result depends on the resolved `homeAsUpIndicator`/toolbar default | `activity_credits.xml` sets `titleTextColor` only, unlike Fund Data/Report which tint the navigation icon white | Screenshot Credits in dark mode; inspect the resolved icon tint |

---

## 14. Recommended Design Improvements

These are design recommendations, not implemented changes. Each is intended to remain feasible with the existing Java/XML architecture and Material Components. Effort is a rough design/implementation estimate: **S** = styling/resource change, **M** = layout/adapter change, **L** = broader restructure of the visual layer without changing features.

### 14.1 Priority order

| Priority | Recommendation | Addresses | Effort | Risk |
|---|---|---|---|---|
| 1 | Fix dark-theme role pairing (headers, toolbars, balance, logout) | C-01, C-02, C-03 | S | Low |
| 2 | Make Welcome title/subtitle theme-aware or change the gradient | C-04 | S | Low |
| 3 | Introduce semantic income/expense tokens and unify ledger/payment colours | C-05 | S–M | Low |
| 4 | Add empty states for students, ledger, payments | C-09 | S | Low |
| 5 | Reserve list bottom space for FAB/nav bar | C-10 | S | Low |
| 6 | Complete EN/ID localisation and month display mapping | C-06, C-07, C-08 | M | Medium (stored month values must not change) |
| 7 | Rebuild the Fund Report summary hierarchy | C-15 | M | Low |
| 8 | Unify buttons, dialogs, toolbars, and card surfaces | C-12, C-13, C-14, C-18 | M | Medium |
| 9 | Enlarge touch targets to 48dp | C-11 | S | Low |
| 10 | Make fixed layouts responsive and scrollable | P-03, P-04, P-05, P-18 | M | Medium |
| 11 | Fix edge-to-edge insets and dialog/keyboard behaviour | P-01, P-02, P-20 | M | Medium (must preserve the working payment-sheet handling) |
| 12 | Unify launcher with the coin emblem | C-17 | S | Low |
| 13 | Replace Toasts with persistent/inline feedback where practical | C-21 | S–M | Low |
| 14 | Clean up legacy/unused assets after visual confirmation | C-19 | S | Low |

### 14.2 Colour and theme

1. **Define a header token pair.** Suggested:
   - Light header: `#6750A4` background, `#FFFFFF` foreground (6.44:1).
   - Dark header option A: `#4F378B` background, `#EADDFF` foreground (7.23:1) — keeps a rich purple.
   - Dark header option B: `#D0BCFF` background, `#381E72` foreground (7.71:1) — keeps the light lavender but requires every icon/text to switch.
   Do not mix white foreground with the light-lavender header.
2. **Add explicit roles**: `colorIncome`, `colorExpense`, `colorOnIncomeContainer`, `colorOnExpenseContainer`, `colorHeader`, `colorOnHeader`, `colorOutlineVariant`. Provide light and night values.
3. **Use container colours for dark elevation**: surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh. Avoid white cards on near-black with no border.
4. **Keep the illustration palette fixed**, but place illustrations on a consistent lavender container so the deep plum silhouette remains visible in dark mode.
5. **Restyle destructive actions** in `colorError`/`onErrorContainer` and place them away from the primary action.
6. **Add a light/dark preview of every screen** to the handoff, not only the main flow.

### 14.3 Typography

1. Create named text appearances: `TextAppearance.ClassFund.Display`, `.Headline`, `.Title`, `.Subtitle`, `.Body`, `.BodySmall`, `.Label`, `.Amount`, `.AmountHero`.
2. Keep Borel only for the Home greeting; do not use it for financial values or body copy.
3. Keep Whitney only if its glyph coverage is verified; otherwise use Plus Jakarta Sans Bold/ExtraBold for the Welcome wordmark.
4. If a medium weight is needed, add a real 500/600 font file; stop relying on `textFontWeight` with static fonts.
5. Make amounts tabular where the font supports it, and right-align them in lists.
6. Raise the Fund Report income/expense values to at least 16sp (ideally 18–20sp) and move the labels to 12–14sp above them.
7. Allow localisable text to wrap; do not use single-line constraints for copy that changes length.

### 14.4 Layout and spacing

1. Introduce `dimens.xml` with a 4/8/12/16/20/24/32 scale and use tokens for gutters, card padding, field gaps, and sheet padding.
2. Use 24dp screen gutters on large screens and 16dp on compact widths; keep list rows aligned to the same gutter as the header content.
3. Reserve list bottom space: `paddingBottom = 16dp (FAB margin) + 56dp (FAB) + 16dp (breathing)`, or use `clipToPadding=false` with a matching bottom inset.
4. Replace fixed 180dp card/list heights with `wrap_content` + `minHeight`, or use a ratio/constraint that can grow for font scale and Indonesian text.
5. Make Welcome and Credits scrollable; keep the Welcome hero but reduce the illustration to a responsive 240–280dp on short screens.
6. Add `layout-sw600dp` variants or a max-width content column for tablets; do not stretch a 180dp card across 800dp.
7. Handle window insets explicitly on every top-level screen; preserve the existing Home AppBar behaviour and extend equivalent handling to the other headers.
8. Keep the rising-list overlap, but make it a token (e.g., `-32dp`) and ensure the list surface still has a visible 24dp radius after inset changes.

### 14.5 Components

| Component | Recommendation |
|---|---|
| Feature card | Make height responsive; keep 24dp radius; use an explicit surface container; keep illustration 96–112dp but let the text column breathe |
| List row | Keep 16dp radius/outline; make overflow 48dp; add a semantic amount pill or icon; keep date as 12–14sp caption |
| Payment row | Use the same row language as the ledger (card or full-width row) instead of a bare transparent row |
| Primary button | One height (52–56dp), one radius (16dp or full pill), one label style; use tonal for secondary actions |
| FAB | Keep for add actions; use a custom plus icon matching the icon family; consider an extended FAB only if it does not hide list content |
| Input | Keep outlined 12dp; add a consistent 16dp gap; show helper/error text inside the layout; add a date end icon for the ledger |
| Spinner | Use a `TextInputLayout`-style exposed dropdown or a fully styled spinner with 16dp horizontal padding and matching typography |
| Dialog | Choose Material dialogs everywhere; use a custom title/content layout for forms; use full-screen or bottom-sheet forms on small screens if needed |
| Bottom sheet | Add a drag handle, 24dp horizontal padding, clear title, and bottom inset; keep the payment sheet’s IME behaviour |
| Empty state | Add an illustration or icon, one sentence, and a primary action (e.g., “Add student”) in EN/ID |
| Feedback | Prefer inline error + Snackbar for financial writes; keep Toasts only for low-risk confirmations |

### 14.6 Financial presentation

1. **Make the balance the hero.** Use a dedicated card/group with a 12–14sp label and a 32–40sp tabular amount; do not bury it between captions.
2. **Give income and expense their own values.** Layout: two equal columns or chips, each with a 12–14sp label, 18–20sp amount, semantic colour, and a `+`/`−` or arrow icon.
3. **Keep the accounting words.** Show “Income (Debit)” and “Expense (Credit)” as secondary clarification, not the primary labels. This preserves semantics while matching user mental models.
4. **Improve ledger rows.** Keep date, description, signed amount, and type; add a small income/expense chip or icon. Ensure long descriptions wrap without pushing the amount off-screen.
5. **Add a legend** near the ledger list or summary: green/positive = money in, red/negative = money out.
6. **Do not add totals that are not computed** (e.g., monthly averages, charts, projections). The app does not have that data.
7. **Keep the `Rp` formatting** exactly: no decimals, `.` grouping, space after `Rp`.
8. **Preserve the insufficient-balance rule** and show the error at the amount field, not only in a toast.

### 14.7 Localisation

1. Move all hard-coded copy into `strings.xml` and `values-in/strings.xml`.
2. Add a display-only month map for Indonesian; never write localised month names into the database.
3. Format dates for display (`dd MMM yyyy` in ID, `MMM d, yyyy` in EN) while keeping `yyyy-MM-dd` in the field/database contract; if the field must remain ISO, show a supporting hint.
4. Replace the language switch with a clearer control: segmented `EN | ID`, a labelled dropdown, or two radio rows. Keep the stored values `en`/`id`.
5. Use `android.R.string.ok` only if system-locale behaviour is acceptable; otherwise add app-localised `ok`.
6. Add EN/ID empty-state and error copy.
7. Test the longest strings in the radio group, report summary, dialog titles, and toolbar.

### 14.8 Accessibility

1. 48×48dp minimum touch targets for every icon button; keep the visual icon at 24dp inside.
2. Text contrast ≥4.5:1 (normal) and ≥3:1 (large); re-check after any palette change.
3. Avoid colour-only meaning: keep `+`/`−` and add icons/labels.
4. Provide meaningful content descriptions; mark decorative illustrations as such.
5. Support font scale 2.0 without clipping; avoid fixed heights on text containers.
6. Ensure logical TalkBack order on Home cards (title → subtitle → action), not card → button duplicate with no context.
7. Give the language control an accessible state description (e.g., “English selected”).
8. Keep the existing keyboard focus/scroll behaviour and extend it to dialogs.

### 14.9 Brand and assets

1. Use `splash_emblem` for the adaptive launcher foreground and monochrome icon.
2. Keep the four illustrations; refine rather than replace.
3. Standardise the icon family (filled vs stroke) and the FAB plus.
4. Make the header illustrations slightly larger or place them in a lavender circle at 48dp so the detail survives.
5. Remove the unused `illustration_*` and `profile_*` resources only after confirming nothing references them; do not delete user work without review.
6. Keep the app name “ClassFund” and the purple/lavender identity.

---

## 15. Google Stitch Design Instructions

### 15.1 Objective for Stitch

Redesign ClassFund as a **polished, trustworthy, friendly class-fund app** that still feels like the same product. Stitch should refine the existing purple/lavender identity, geometric finance illustrations, layered headers, rounded cards, and simple navigation. It should not replace them with a generic banking dashboard, a dark “fintech” template, or a completely different navigation model.

The output must be realistic for the existing **Java + XML Views + Material Components + SQLite** app. Every screen must be producible with XML layouts, existing drawables (or close vector refinements), RecyclerView adapters, Material dialogs/bottom sheets, and the current data model.

### 15.2 Deliverables to request from Stitch

| Deliverable | Requirement |
|---|---|
| Screen set | Welcome, Home, Fund Data, Fund Report, Credits, Profile/settings sheet, Student payment sheet, Add/Edit student dialog, Add/Edit ledger dialog, action menus/confirmations |
| Themes | Every screen in **light and dark** |
| Locales | Key screens in **English and Indonesian**, using the longest Indonesian copy |
| Sizes | Phone 360×800dp reference; also show 412×915dp and a tablet/landscape adaptation for Home, Fund Data, Fund Report, and Welcome |
| States | Populated, empty (students/ledger/payments), form error, keyboard-visible payment sheet, selected language/theme |
| Components | Buttons, cards, rows, inputs, spinners, dialogs, sheets, FAB, empty states, summary blocks |
| Handoff | Named colour/type/spacing tokens and per-screen notes; no isolated mockup without states |

### 15.3 Non-negotiable creative direction

1. **Purple/lavender, but disciplined.** Use the existing `#6750A4` / `#D0BCFF` family. In dark mode, lavender is an accent or a container, not a background for white text.
2. **Geometric finance illustration style.** Flat vectors, rounded capsules, coin/bar/people motifs, white line detail, five-purple ramp. No 3D, no photorealism, no generic corporate people.
3. **Layered headers, calmer.** Keep the curved/diamond header and rising list sheet, but reduce empty header height and make the title/subtitle/illustration composition deliberate.
4. **Rounded but not bubbly.** Use a clear radius hierarchy: 12dp fields, 16dp rows, 20–24dp feature cards/sheets, full pill only for the primary Welcome CTA.
5. **Financial clarity first.** The balance is the hero; income and expense are readable peer values; ledger rows are scannable; accounting semantics are explicit.
6. **Friendly academic tone.** This is a class fund, not a bank. Use warm, plain language and the Borel greeting sparingly.
7. **Bilingual by design.** Compose with Indonesian lengths; avoid single-line fixed labels.
8. **Accessible by default.** 4.5:1 text, 48dp targets, visible focus/pressed states, no colour-only meaning.

### 15.4 Master prompt for Stitch

```text
Design a complete mobile UI refresh for “ClassFund”, an Android class-fund manager built with Java and XML Views (Material 3). The app records students, monthly payments, and a cash-book ledger, and shows the current class-fund balance. Improve the existing design; do not replace it with an unrelated template.

BRAND & MOOD
- Preserve the purple/lavender identity. Light primary #6750A4; light surface #FEF7FF; dark surface #141218; dark primary #D0BCFF; dark primary container #4F378B.
- Friendly, trustworthy, organised, slightly playful, suitable for a school class treasurer. Not corporate banking, not a crypto app.
- Keep the four existing flat geometric illustrations: a coin “C” emblem, a token stack, a rising bar/trend chart, and three student figures. Use the same five-purple palette (#4E337D, #8B6BCE, #B59AE9, #C9B6F3, #F1EBFF) and white line details. No 3D, no stock photos, no new mascots.
- Use Plus Jakarta Sans for UI. Keep the handwritten Borel only for the Home greeting. Keep the Welcome wordmark large and confident.

SCREENS TO DESIGN
1. Welcome: brand hero, coin emblem, short subtitle, single “Begin” button. Must fit short screens and both themes.
2. Home: greeting, three feature cards (Fund Data, Fund Report, Credits), profile avatar. Keep the three destinations and the layered purple header.
3. Fund Data: student list with add FAB, student avatar/name, overflow actions; empty state.
4. Fund Report: current balance hero, income and expense summary, ledger list with date/description/signed amount/type; add FAB; empty state.
5. Credits: developer list with the student-group illustration.
6. Profile/settings bottom sheet: account label, logout, dark-mode switch, clear English/Indonesian language control.
7. Student payment bottom sheet: student name, payment history, add-payment form with month/year and amount, keyboard-safe.
8. Add/edit student dialog and add/edit ledger dialog (date, description, income/expense, amount).
9. Action menus and delete confirmations.

FINANCIAL SEMANTICS (MUST PRESERVE)
- Debit = money in = “Income” / “Pemasukan”.
- Credit = money out = “Expense” / “Pengeluaran”.
- Balance = SUM(debit) − SUM(credit) over the ledger.
- A student payment creates one mirrored ledger debit; editing/deleting it updates/removes that same row.
- Show amounts as Indonesian Rupiah: “Rp 121.000”, no decimals.
- Keep + and − signs as well as colour.

COLOUR & TYPE REQUIREMENTS
- Light theme: header #6750A4 with white text, or a lavender container with dark text. Body on #FEF7FF.
- Dark theme: use #4F378B with #EADDFF, or #D0BCFF with #381E72. Never white on #D0BCFF.
- Define income/expense colours for both themes; both must be ≥4.5:1 on their surfaces.
- Suggested scale: 32–40sp balance, 22–28sp screen/section titles, 18–20sp card titles, 16sp body, 14sp supporting, 12sp labels, 16–20sp amounts.
- Use tabular figures for money where possible.

LAYOUT REQUIREMENTS
- 360×800dp baseline, responsive to 320dp and tablets.
- 4/8/12/16/20/24/32dp spacing scale. Prefer 16dp compact gutters, 24dp regular gutters.
- Keep the rising-list overlap but cap header heights so the screen is not mostly empty colour.
- Replace fixed 180dp text containers with content-driven heights; keep a consistent minimum.
- Reserve bottom space for the FAB and gesture navigation.
- Make Welcome and Credits scrollable on short screens.
- Handle status/navigation bar insets on every top-level screen.
- Payment sheet must keep the focused amount field and Save button above the keyboard.

LOCALISATION
- Design with Indonesian lengths: “Selamat datang kembali!”, “Laporan Dana”, “Kelola siswa dan pembayaran”, “Kredit (Pengeluaran)”, “Debit (Pemasukan)”, “Saldo saat ini”.
- Do not use single-line fixed labels for localisable copy.
- Show months in the selected language while keeping stored data unchanged.
- Provide empty-state and error copy in both languages.

ACCESSIBILITY
- 48×48dp touch targets.
- AA contrast; no colour-only meaning.
- Visible pressed/focused/error states.
- Meaningful content descriptions; decorative art marked as decorative.
- Support 200% font scale without clipping.

DELIVERABLES
- All screens and states in light and dark.
- EN and ID versions of key screens.
- Component sheet with tokens and measurements.
- Notes explaining how each design maps to XML/Material components.
- Do not invent search, filters, charts, notifications, login, settings pages, or new tabs. Do not change navigation or data behaviour.
```

### 15.5 Per-screen briefs

| Screen | What to preserve | What to improve |
|---|---|---|
| Welcome | Coin emblem, purple gradient, single Begin CTA, brand name | Theme-aware title/subtitle, responsive illustration, fewer empty gaps, optional subtle wave/pattern that does not hurt contrast |
| Home | Three destinations, greeting, profile avatar, layered header | Balance the hero; responsive card heights; consistent illustration containers; one clear action per card; calmer header height |
| Fund Data | Student list, avatar, overflow, FAB | Empty state; 48dp actions; header title legibility; list bottom inset; count or section label only if it uses real data |
| Fund Report | Balance, income/expense, ledger, FAB | Proper summary hierarchy; semantic amount colours + signs; readable labels; empty state; date grouping/legend only if implementable without new data |
| Credits | Four names, student-group illustration | Consistent toolbar, translated heading, scroll, more balanced card composition; do not invent roles or links |
| Profile sheet | Logout, theme switch, language switch | Clear language control; destructive styling; bottom inset; drag handle; title “Profile” or “Settings” with ClassFund branding |
| Payment sheet | History + add form, keyboard handling | Dynamic history height/empty state; styled month/year pickers; amount prefix; clear sectioning; sticky Save |
| Student dialog | Single name field | Localised title/hint/buttons; Material dialog styling; inline error; keyboard-safe |
| Ledger dialog | Date, description, type, amount | Vertical/segmented type control; date icon; clearer income/expense wording; grouped fields; error placement |
| Confirmations | Two-step delete, blocked-history explanation | Destructive colour; concise copy; consistent Material dialog; localised OK |

### 15.6 Component briefs for Stitch

- **Header:** a purple field with a soft curve or shallow diamond, a 24dp title area, optional 1-line subtitle, and one illustration. Keep the header height 160–200dp, not 240dp unless it contains real content.
- **Rising surface:** 24dp top corners, surface colour, subtle top shadow or 1dp outline, 16–24dp top padding.
- **Summary card:** label 12–14sp, value 18–20sp, icon 20dp; two columns for income/expense; balance in its own larger block.
- **List row:** 16dp radius, 16dp padding, 1dp outline, 48dp trailing action, amount right-aligned, date as caption, optional type chip.
- **Primary button:** 52–56dp, 16dp radius or pill, 16sp semibold, full width on forms.
- **Secondary button:** tonal, same height/radius, no competing fill.
- **Input:** outlined, 12dp radius, 56dp minimum height, 16dp label, 12–16dp gaps, inline error.
- **Picker:** exposed-dropdown look with 16dp horizontal padding and a trailing chevron; do not leave a raw platform spinner.
- **Bottom sheet:** 28dp top corners, drag handle, 24dp padding, title 22sp, scrollable content, bottom inset.
- **Empty state:** simple line illustration or icon in a lavender circle, 18–20sp title, 14sp body, primary action.
- **Profile row:** surfaceContainer, 16dp radius, 48dp icon container, 16sp label, trailing switch/chevron.

### 15.7 What Stitch must not do

- Do not add a bottom navigation bar, drawer, tabs, search, filters, notifications, login, onboarding carousel, or new settings screens.
- Do not add charts, forecasts, budgets, categories, or student photos; the data model does not support them.
- Do not rename debit/credit semantics or reverse income/expense.
- Do not change the SQLite schema, stored month names, payment mirroring, or balance formula.
- Do not remove the two-step delete, blocked-delete rule, or validation behaviour.
- Do not replace the Java/XML architecture with Compose, Flutter, React Native, or a web view.
- Do not present only a light-mode hero screen; dark mode and empty states are required.
- Do not use white text on `#D0BCFF`, 25% white body text, or colour-only meaning.
- Do not remove the Borel greeting or the four illustrations; refine them instead.

### 15.8 Suggested Stitch prompt sequence

1. Generate a **style tile**: palette, type scale, radii, spacing, four illustration placements, light/dark header treatments.
2. Generate **Home and Welcome** first; lock the brand language.
3. Generate **Fund Data and Fund Report** with populated + empty states; validate the summary hierarchy.
4. Generate **Credits and Profile sheet**; validate dark mode and localisation.
5. Generate **Payment sheet and the two form dialogs**, including keyboard-visible and error states.
6. Generate **confirmation menus** and a component sheet.
7. Re-run the darkest and longest-string variants; check contrast and clipping before handoff.

---

## 16. Design Preservation Requirements

Any future redesign, including Stitch output, must preserve the following. These are contracts, not suggestions.

### 16.1 Functional and navigational preservation

| Requirement | What must remain |
|---|---|
| Screen set | Welcome, Home, Fund Data, Fund Report, Credits, profile/settings sheet, payment sheet, student dialog, ledger dialog, action menus/confirmations |
| Navigation | System splash → Welcome → Home; Home → Fund Data / Fund Report / Credits; student row → payment sheet; profile avatar → sheet; logout → Welcome with cleared task |
| Back behaviour | Toolbar up and system back return to the previous screen; Welcome is the launcher |
| Add/edit/delete flows | Student add/rename/delete; payment add/edit/delete; ledger add/edit/delete; swipe-to-delete with restore on cancel |
| Validation | Empty description/name, non-numeric/zero/too-large amount, insufficient balance, month/year selection; failed validation keeps the dialog open |
| Delete safety | Confirmation for every destructive action; a student with payment history cannot be deleted; the blocked explanation must remain |
| Locale switching | English/Indonesian switching from the profile sheet, persisted across launches |
| Theme switching | Light/dark switching from the profile sheet, persisted across launches |
| Keyboard behaviour | Payment sheet amount field and Save button remain visible above the IME on Android 15+ |
| CSV import | `WelcomeActivity` imports bundled students; do not remove or move this behind a new flow |

### 16.2 Accounting and database preservation

| Invariant | Detail |
|---|---|
| Tables | `table_students`, `table_payments`, `table_ledger`; database `kas_app.db`, version 3 |
| Student columns | `id`, `name` |
| Payment columns | `id`, `student_id`, `month`, `year`, `amount` |
| Ledger columns | `id`, `date`, `description`, `type`, `amount`, `payment_id` |
| Type values | `debit` = Pemasukan/income; `credit` = Pengeluaran/expense |
| Balance | `SUM(debit) − SUM(credit)` over `table_ledger` only; payments are not added again |
| Payment mirroring | Every Fund Data payment owns exactly one linked ledger debit through `payment_id`; edit/delete updates/removes that same row in one transaction |
| Month storage | English month names from `PAYMENT_MONTHS`; never overwrite them with translated display names |
| Date storage | `yyyy-MM-dd`; display formatting may change but stored values must not |
| Amount storage | Integer Rupiah; display `Rp #,##0`, no decimals |
| Insufficient balance | Credits cannot exceed the available balance; the error is shown on the amount field |
| Student deletion | Blocked when payment history exists; history is never deleted |
| Ordering | Ledger newest date first; payments chronological by month/year |

### 16.3 Brand preservation

| Element | Must remain |
|---|---|
| Name | “ClassFund” (not translated, not abbreviated) |
| Purple/lavender identity | `#6750A4` / `#D0BCFF` family, purple headers and accents |
| Illustrations | The four custom geometric scenes and their five-purple palette |
| Home greeting | Borel handwriting, or a deliberately approved replacement with the same warmth |
| Tone | Friendly, academic, approachable; not corporate or luxury |
| Launcher | Eventually unified with the coin emblem; do not replace with an unrelated logo |

### 16.4 Architecture and implementation preservation

- Native Android, Java 11, XML Views, Material Components 3.
- `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- Activities, `RecyclerView` adapters, `SQLiteOpenHelper`; no Compose, no new navigation framework, no web view.
- `KeyboardInsets` and the payment-sheet edge-to-edge theme must not be removed; they solve a real Android 15 keyboard problem.
- AppCompat locale APIs and the existing `locale_prefs`/`theme_prefs` keys should continue to work.
- Existing drawables may be refined, but replacing them requires an explicit visual review; do not silently drop the illustrations.
- New visual assets should be vector XML where possible to match the existing approach and keep APK size reasonable.

### 16.5 Explicit non-goals

The redesign must **not**:

- Add authentication, user accounts, roles, or permissions.
- Add a backend, sync, cloud storage, or analytics.
- Add search, filters, sorting UI, categories, budgets, charts, or reports not derived from existing data.
- Change the database schema or migrate stored data for visual convenience.
- Replace SQLite with Room or another persistence layer as part of a visual task.
- Introduce a new design system that cannot be expressed with Material Components and XML.
- Delete the Borel greeting or the four illustrations without an approved alternative.
- Remove the two-step delete, the insufficient-balance rule, or the student-history protection.

---

## 17. Design Validation Checklist

Use this checklist for every Stitch iteration and before any implementation. A box is only “done” when verified in the running app, in both themes and both languages.

### 17.1 Brand and visual language

- [ ] The app still reads as ClassFund: purple/lavender, rounded, friendly, academic.
- [ ] The four geometric illustrations are present and share one palette/line style.
- [ ] The Home greeting retains Borel or an approved equivalent.
- [ ] The launcher, splash, and Welcome share a recognisable brand mark.
- [ ] No unrelated template, stock photography, 3D art, or new mascot has been introduced.
- [ ] The layered header + rising card/list surface is retained in a controlled form.

### 17.2 Colour and contrast

- [ ] No white text/icons on `#D0BCFF`.
- [ ] Header text meets ≥4.5:1 (normal) or ≥3:1 (large) in both themes.
- [ ] Report summary labels and balance meet AA in both themes.
- [ ] Income/expense colours meet AA on their actual surfaces in both themes.
- [ ] Body text on surface meets ≥4.5:1.
- [ ] Outlines/dividers are visible in dark mode.
- [ ] Colour is never the only income/expense cue; `+`/`−` or icons remain.
- [ ] Dialog, sheet, spinner dropdown, date picker, and toast match the theme.
- [ ] Welcome title/subtitle remain legible across the entire gradient.

### 17.3 Typography

- [ ] One clear hierarchy: hero balance > screen title > section title > body > caption.
- [ ] Income and expense values are at least 16sp and visually peer to each other.
- [ ] The balance is the strongest number on Fund Report.
- [ ] Long Indonesian copy wraps without clipping.
- [ ] Borel is used only for the greeting, not for financial values.
- [ ] Whitney (if retained) covers all glyphs used.
- [ ] Amounts use consistent formatting and, where possible, tabular figures.
- [ ] Font scale 2.0 does not clip text or hide actions.

### 17.4 Spacing and layout

- [ ] A consistent 4/8/12/16/20/24/32 spacing rhythm is visible.
- [ ] Gutters align between header, cards, and list rows.
- [ ] Corner radii follow a clear hierarchy (inputs, rows, cards, sheets).
- [ ] No screen is mostly empty header colour.
- [ ] Lists reserve bottom space for the FAB and gesture navigation.
- [ ] Welcome and Credits scroll on short screens.
- [ ] Feature cards grow with content instead of clipping.
- [ ] Tablet/landscape layouts do not stretch phone cards across the full width.
- [ ] Status/navigation bar insets are handled on every top-level screen.
- [ ] The payment sheet still lifts above the keyboard.

### 17.5 Components and states

- [ ] One primary-button treatment; secondary actions are tonal/text.
- [ ] Dialogs use one consistent family and styling.
- [ ] Destructive actions use error styling and remain confirmed.
- [ ] Inputs show inline errors and keep the dialog open on invalid input.
- [ ] Spinners/dropdowns match the field styling.
- [ ] Empty states exist for students, ledger, and payment history.
- [ ] Loading/disabled states are considered where relevant.
- [ ] Touch targets are ≥48×48dp.
- [ ] Profile language control clearly communicates the active language.
- [ ] The FAB does not obscure the last row.

### 17.6 Screen-by-screen

- [ ] Splash: emblem not awkwardly cropped; brand continuity with Welcome.
- [ ] Welcome: one clear CTA; text legible; fits short screens.
- [ ] Home: three destinations obvious; greeting readable; cards balanced.
- [ ] Fund Data: title/back readable; list scannable; add action obvious; empty state helpful.
- [ ] Fund Report: balance hero; income/expense readable; ledger scannable; delete safe.
- [ ] Credits: heading translated; names readable; scrolls if needed.
- [ ] Profile: logout safe; theme switch clear; language switch unambiguous; bottom inset respected.
- [ ] Payment sheet: history + form hierarchy clear; month/year readable; Save visible with keyboard.
- [ ] Student dialog: translated; one clear field; error visible.
- [ ] Ledger dialog: type choice clear; date affordance obvious; amount error visible.
- [ ] Confirmations: destructive intent clear; copy translated.

### 17.7 Localisation and accessibility

- [ ] English and Indonesian both complete; no mixed-language screen.
- [ ] Months display in the selected language without changing stored values.
- [ ] Dates are understandable in both locales; storage remains ISO.
- [ ] `Rp` formatting is identical in both languages.
- [ ] All hard-coded user-facing text has been moved to resources (design handoff must specify the keys).
- [ ] Content descriptions are present for meaningful icons/illustrations.
- [ ] TalkBack order is logical and does not duplicate the same action.
- [ ] Contrast and touch targets pass with the final palette.
- [ ] No RTL regression if the app is ever localised further.

### 17.8 Preservation and feasibility

- [ ] All existing screens and navigation paths still exist.
- [ ] No feature has been removed or invented.
- [ ] Debit/credit semantics and balance formula are unchanged.
- [ ] SQLite tables, columns, version, and stored month names are unchanged.
- [ ] Payment ↔ ledger mirroring is unchanged.
- [ ] Student-history delete protection is unchanged.
- [ ] Insufficient-balance behaviour is unchanged.
- [ ] English/Indonesian switching and persistence are unchanged.
- [ ] Light/dark switching and persistence are unchanged.
- [ ] The design is implementable with Java/XML/Material Components.
- [ ] `KeyboardInsets` behaviour is preserved.
- [ ] The four illustrations and Borel greeting are preserved or explicitly approved replacements.

### 17.9 Visual verification log

| Check | Status | Notes |
|---|---|---|
| Android 15 phone, light, EN | Not run | No device/emulator available during audit |
| Android 15 phone, light, ID | Not run | No device/emulator available |
| Android 15 phone, dark, EN | Not run | No device/emulator available |
| Android 15 phone, dark, ID | Not run | No device/emulator available |
| 320dp width / large font | Not run | No device/emulator available |
| Tablet / landscape | Not run | No device/emulator available |
| Keyboard-visible payment sheet | Not run | Code handling verified statically |
| Keyboard-visible dialogs | Not run | Potential issue P-02 |
| TalkBack traversal | Not run | Content descriptions inspected statically |
| Splash on Android 12+ | Not run | Masking risk P-22 |
| Launcher icon on device | Not run | Asset mismatch C-17 verified statically |

> **Audit limitation:** no runtime screenshots, emulator, or physical device were available. Contrast findings are computed from resource hex values. Layout, typography rendering, platform dialog styling, spinner appearance, status-bar behaviour, keyboard overlap, and illustration legibility at small sizes must be visually verified before the redesign is considered final.

---

*End of specification.*
