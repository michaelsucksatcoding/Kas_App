# Class Fund (Uang Kas) App - AI Agent Roadmap

## Project Overview
- **App Name:** Class Fund App
- **Language:** Java (Android SDK)
- **UI Style:** Modern clean UI matching design references (`MaterialCardView`, rounded corners).
- **Theme Constraint:** Use project's existing theme colors (`?attr/colorPrimary`, `?attr/colorSurface`, etc.). Do NOT hardcode custom hex colors.
- **Storage:** Local SQLite database via `SQLiteOpenHelper`.

## Database Schema
- `table_students`: `id` (INTEGER PRIMARY KEY), `name` (TEXT)
- `table_payments`: `id` (INTEGER PRIMARY KEY), `student_id` (INTEGER), `month` (TEXT), `year` (INTEGER), `amount` (INTEGER)
- `table_ledger`: `id` (INTEGER PRIMARY KEY), `date` (TEXT), `description` (TEXT), `type` (TEXT - "credit" or "debit"), `amount` (INTEGER)

---

## Phase 1: Database, Layouts & Core Navigation
**Agent Goal:** Set up the database, initial UI layouts with vector icons, and core navigation.

1. **Database:** Create `DatabaseHelper.java` extending `SQLiteOpenHelper` to build the 3 tables defined above. Include a helper method to import `assets/students.csv`.

2. **Welcome Screen (`activity_welcome.xml` & `WelcomeActivity.java`):**
    - Centered large `ImageView` using `@drawable/welcome`.
    - Bold title and subtitle `TextView` below image.
    - Large, fully rounded `MaterialButton` labeled "Begin" anchored to the bottom.
    - On click, navigate to `MainActivity`.

3. **Main Menu Screen (`activity_main.xml` & `MainActivity.java`):**
    - Top Toolbar/Title.
    - **Card 1 (Fund Data):** `MaterialCardView` (corner radius `24dp`), title "Fund Data", subtitle, button labeled "Open", and right-aligned `ImageView` using `@drawable/data_fund`.
    - **Card 2 (Fund Report):** Identical layout style, title "Fund Report", right-aligned `ImageView` using `@drawable/report_fund`.
    - Set click listeners on both cards to open `FundDataActivity` and `FundReportActivity`.

4. **List Item Layouts:**
    - `item_student.xml`: Horizontal `MaterialCardView` with rounded corners (`16dp`), surface background, stroke border (`?attr/colorOutlineVariant`), student avatar icon, student name, and a right arrow action icon.
    - `item_ledger.xml`: Rounded card displaying date, description, and amount.

---

## Phase 2: Fund Data Screen (Student List)
**Agent Goal:** Build the screen displaying the list of students.
1. Create `FundDataActivity.java` and `activity_fund_data.xml` with a top Toolbar and `RecyclerView`.
2. Create `StudentAdapter.java` to populate the list using `table_students`.
3. Set up click listener interface passing `student_id` and `name` to `FundDataActivity`.

## Phase 3: Payment Management (BottomSheet)
**Agent Goal:** Build the pop-up interface for payment history and adding new payments.
1. Create `StudentPaymentBottomSheet.java` and `bottom_sheet_payment.xml`.
2. **Top Section:** `RecyclerView` displaying student's payment history from `table_payments`.
3. **Bottom Section:** Entry form with Month `Spinner` (Jan-Dec), Year `Spinner` (2026-2028), Amount `EditText`, and "Save Payment" button.
4. **Logic:** Insert new payment into `table_payments`, refresh history `RecyclerView`, and clear inputs.

## Phase 4: Ledger & Reporting
**Agent Goal:** Build the global financial report screen with CRUD functionality.
1. Create `FundReportActivity.java` and `activity_fund_report.xml` with Toolbar, `RecyclerView`, and a Floating Action Button (FAB).
2. Create `LedgerAdapter.java`. Dynamically format amount text color: **Green** for credit (income) and **Red** for debit (expense).
3. Implement `ItemTouchHelper` for swipe-left-to-delete functionality.
4. **FAB Logic:** Show `AlertDialog` with inputs for Date, Description, Credit/Debit RadioGroup, and Amount. Insert into `table_ledger` and refresh list.