package com.example.kas_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.os.LocaleListCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String THEME_PREFS = "theme_prefs";
    private static final String KEY_DARK_MODE = "isDarkMode";
    private static final String LOCALE_PREFS = "locale_prefs";
    private static final String KEY_APP_LANG = "app_lang";
    private static final String LANG_INDONESIAN = "id";

    /** Applies the saved night mode. Safe to call before super.onCreate. */
    public static void applySavedTheme(Context context) {
        boolean isDarkMode = context.getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** Applies the saved locale (English/Indonesian). Safe to call before super.onCreate. */
    public static void applySavedLocale(Context context) {
        String lang = context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(KEY_APP_LANG, Locale.getDefault().getLanguage());
        if (!"en".equals(lang) && !LANG_INDONESIAN.equals(lang)) {
            lang = "en";
        }
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.create(Locale.forLanguageTag(lang)));
    }

    private static void saveDarkMode(Context context, boolean isDarkMode) {
        context.getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DARK_MODE, isDarkMode)
                .apply();
    }

    private static void saveLanguage(Context context, String lang) {
        context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_APP_LANG, lang)
                .apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Night mode is applied by KasApp before attachBaseContext.
        applySavedLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Belt and braces: XML android/app fontFamily covers the normal
        // inflation path, but some devices silently drop font resources there
        // and fall back to the theme font. Applying the Borel typeface from
        // code guarantees the handwriting look of the hero greeting.
        TextView tvGreeting = findViewById(R.id.tvGreeting);
        Typeface borel = ResourcesCompat.getFont(this, R.font.borel);
        if (borel != null) {
            tvGreeting.setTypeface(borel);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbarMain);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            // The compact ClassFund title is a custom toolbar child so its
            // alpha can follow the collapse offset. Hide the framework title.
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        ShapeableImageView btnProfile = findViewById(R.id.btnProfile);
        btnProfile.setOnClickListener(v -> showProfileSheet());

        setUpCollapsingHeader(toolbar, btnProfile);

        MaterialCardView cardFundData = findViewById(R.id.cardFundData);
        MaterialCardView cardFundReport = findViewById(R.id.cardFundReport);
        MaterialCardView cardCredits = findViewById(R.id.cardCredits);

        View.OnClickListener dataListener = v -> {
            Intent intent = new Intent(MainActivity.this, FundDataActivity.class);
            startActivity(intent);
        };

        cardFundData.setOnClickListener(dataListener);
        findViewById(R.id.btnOpenData).setOnClickListener(dataListener);

        View.OnClickListener reportListener = v -> {
            Intent intent = new Intent(MainActivity.this, FundReportActivity.class);
            startActivity(intent);
        };

        cardFundReport.setOnClickListener(reportListener);
        findViewById(R.id.btnOpenReport).setOnClickListener(reportListener);

        View.OnClickListener creditsListener = v -> {
            Intent intent = new Intent(MainActivity.this, CreditsActivity.class);
            startActivity(intent);
        };

        cardCredits.setOnClickListener(creditsListener);
        findViewById(R.id.btnOpenCredits).setOnClickListener(creditsListener);
    }

    /**
     * Keeps the hero content and the compact toolbar title in step with the
     * AppBarLayout collapse offset. The AppBarLayout itself handles the height
     * change and the surface-coloured content scrim, so no layout is recreated
     * while scrolling.
     */
    private void setUpCollapsingHeader(MaterialToolbar toolbar,
                                       ShapeableImageView profileButton) {
        AppBarLayout appBar = findViewById(R.id.appBarMain);
        View expandedContent = findViewById(R.id.headerExpandedContent);
        View compactTitle = findViewById(R.id.tvToolbarTitle);

        int onPrimary = MaterialColors.getColor(
                toolbar, com.google.android.material.R.attr.colorOnPrimary);
        int onSurface = MaterialColors.getColor(
                toolbar, com.google.android.material.R.attr.colorOnSurface);

        // Expanded state: the toolbar sits over the purple hero, so its action
        // icon uses onPrimary. It blends to onSurface as the scrim appears.
        profileButton.setImageTintList(ColorStateList.valueOf(onPrimary));
        compactTitle.setAlpha(0f);
        expandedContent.setAlpha(1f);

        appBar.addOnOffsetChangedListener((layout, verticalOffset) -> {
            int scrollRange = layout.getTotalScrollRange();
            float fraction = scrollRange == 0
                    ? 0f
                    : Math.min(1f, Math.abs(verticalOffset) / (float) scrollRange);

            expandedContent.setAlpha(1f - fraction);

            // Delay the compact title until the surface scrim has mostly faded
            // in; otherwise dark title text briefly sits on the purple hero.
            float titleFraction = Math.max(0f, (fraction - 0.35f) / 0.65f);
            compactTitle.setAlpha(titleFraction * titleFraction);

            profileButton.setImageTintList(ColorStateList.valueOf(
                    ColorUtils.blendARGB(onSurface, onPrimary, 1f - fraction)));
        });
    }

    private void showProfileSheet() {
        BottomSheetDialog dialog =
                new BottomSheetDialog(this, R.style.ThemeOverlay_Kas_App_ProfileSheet);
        View view = LayoutInflater.from(this).inflate(R.layout.activity_popup, null);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            // Lets the sheet's own rounded background show through.
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageButton btnClose = view.findViewById(R.id.btnClosePopup);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        MaterialButton btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, R.string.message_logged_out, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        MaterialSwitch switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchDarkMode.setChecked(isDarkModeEnabled());
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDarkMode(this, isChecked);
            dialog.dismiss();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });

        MaterialSwitch switchLanguage = view.findViewById(R.id.switchLanguage);
        switchLanguage.setChecked(isIndonesian());
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Off = EN, on = ID, matching the labels flanking the switch.
            String lang = isChecked ? LANG_INDONESIAN : "en";
            saveLanguage(this, lang);
            dialog.dismiss();
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.create(Locale.forLanguageTag(lang)));
        });

        dialog.show();
    }

    private boolean isDarkModeEnabled() {
        return getSharedPreferences(THEME_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
    }

    private boolean isIndonesian() {
        return LANG_INDONESIAN.equals(getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(KEY_APP_LANG, Locale.getDefault().getLanguage()));
    }
}
