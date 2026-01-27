package com.cappielloantonio.tempo.ui.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.view.MenuItem;

import androidx.core.content.ContextCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.interfaces.ScanCallback;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.DeleteDownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.DownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StreamingCacheStorageDialog;
import com.cappielloantonio.tempo.service.DesktopLyricsService;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.OverlayPermissionUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.SettingViewModel;

import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";

    private MainActivity activity;
    private SettingViewModel settingViewModel;

    private ActivityResultLauncher<Intent> someActivityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        View view = super.onCreateView(inflater, container, savedInstanceState);
        settingViewModel = new ViewModelProvider(requireActivity()).get(SettingViewModel.class);

        if (view != null) {
            getListView().setPadding(0, 0, 0, (int) getResources().getDimension(R.dimen.global_padding_bottom));
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(false);
        activity.setBottomSheetVisibility(false);
        
        // Add back button functionality to toolbar if it exists
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        checkEqualizer();
        checkCacheStorage();
        checkStorage();

        setStreamingCacheSize();
        setAppLanguage();
        setVersion();
        setupDesktopLyricsLockedSetting();

        actionLogout();
        actionScan();
        actionSyncStarredTracks();
        actionChangeStreamingCacheStorage();
        actionChangeDownloadStorage();
        actionDeleteDownloadStorage();
        actionKeepScreenOn();
        actionDesktopLyrics();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Navigate back to home fragment using NavController
            if (activity.navController != null) {
                // Navigate back to home fragment
                activity.navController.navigate(R.id.action_settingsFragment_to_homeFragment);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.global_preferences, rootKey);
        ListPreference themePreference = findPreference(Preferences.THEME);
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        String themeOption = (String) newValue;
                        ThemeHelper.applyTheme(themeOption);
                        return true;
                    });
        }
    }

    private void checkEqualizer() {
        Preference equalizer = findPreference("equalizer");

        if (equalizer == null) return;

        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);

        if ((intent.resolveActivity(requireActivity().getPackageManager()) != null)) {
            equalizer.setOnPreferenceClickListener(preference -> {
                someActivityResultLauncher.launch(intent);
                return true;
            });
        } else {
            equalizer.setVisible(false);
        }
    }

    private void checkCacheStorage() {
        Preference storage = findPreference("streaming_cache_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getDownloadStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkStorage() {
        Preference storage = findPreference("download_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getDownloadStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void setStreamingCacheSize() {
        ListPreference streamingCachePreference = findPreference("streaming_cache_size");

        if (streamingCachePreference != null) {
            streamingCachePreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    CharSequence entry = preference.getEntry();

                    if (entry == null) return null;

                    long currentSizeMb = DownloadUtil.getStreamingCacheSize(requireActivity()) / (1024 * 1024);

                    return getString(R.string.settings_summary_streaming_cache_size, entry, String.valueOf(currentSizeMb));
                }
            });
        }
    }

    private void setAppLanguage() {
        ListPreference localePref = (ListPreference) findPreference("language");

        Map<String, String> locales = UIUtil.getLangPreferenceDropdownEntries(requireContext());

        CharSequence[] entries = locales.keySet().toArray(new CharSequence[locales.size()]);
        CharSequence[] entryValues = locales.values().toArray(new CharSequence[locales.size()]);

        localePref.setEntries(entries);
        localePref.setEntryValues(entryValues);

        localePref.setDefaultValue(entryValues[0]);
        localePref.setSummary(Locale.forLanguageTag(localePref.getValue()).getDisplayLanguage());

        localePref.setOnPreferenceChangeListener((preference, newValue) -> {
            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags((String) newValue);
            AppCompatDelegate.setApplicationLocales(appLocale);
            return true;
        });
    }
    
    private void setupDesktopLyricsLockedSetting() {
        SwitchPreference lockedPreference = findPreference("desktop_lyrics_locked");
        if (lockedPreference != null) {
            lockedPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isLocked = (boolean) newValue;
                // Save the lock state
                Preferences.setDesktopLyricsLocked(isLocked);
                // Send intent to update the lock state in the service
                Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                requireContext().startService(intent);
                return true;
            });
        }
    }

    private void setVersion() {
        findPreference("version").setSummary(BuildConfig.VERSION_NAME);
    }

    private void actionLogout() {
        findPreference("logout").setOnPreferenceClickListener(preference -> {
            activity.quit();
            return true;
        });
    }

    private void actionScan() {
        findPreference("scan_library").setOnPreferenceClickListener(preference -> {
            settingViewModel.launchScan(new ScanCallback() {
                @Override
                public void onError(Exception exception) {
                    findPreference("scan_library").setSummary(exception.getMessage());
                }

                @Override
                public void onSuccess(boolean isScanning, long count) {
                    findPreference("scan_library").setSummary("Scanning: counting " + count + " tracks");
                    if (isScanning) getScanStatus();
                }
            });

            return true;
        });
    }

    private void actionSyncStarredTracks() {
        findPreference("sync_starred_tracks_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredSyncDialog dialog = new StarredSyncDialog();
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionChangeStreamingCacheStorage() {
        findPreference("streaming_cache_storage").setOnPreferenceClickListener(preference -> {
            StreamingCacheStorageDialog dialog = new StreamingCacheStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionChangeDownloadStorage() {
        findPreference("download_storage").setOnPreferenceClickListener(preference -> {
            DownloadStorageDialog dialog = new DownloadStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionDeleteDownloadStorage() {
        findPreference("delete_download_storage").setOnPreferenceClickListener(preference -> {
            DeleteDownloadStorageDialog dialog = new DeleteDownloadStorageDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void getScanStatus() {
        settingViewModel.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                findPreference("scan_library").setSummary(exception.getMessage());
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                findPreference("scan_library").setSummary("Scanning: counting " + count + " tracks");
                if (isScanning) getScanStatus();
            }
        });
    }

    private void actionKeepScreenOn() {
        findPreference("always_on_display").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
            return true;
        });
    }

    private void actionDesktopLyrics() {
        findPreference("desktop_lyrics_enabled").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                boolean enabled = (Boolean) newValue;
                Preferences.setDesktopLyricsEnabled(enabled);
                if (enabled) {
                    if (OverlayPermissionUtil.hasOverlayPermission(requireContext())) {
                        // Start the service if it's not already running
                        OverlayPermissionUtil.startDesktopLyricsService(requireContext());
                        // Also send an update settings intent to ensure it's properly enabled
                        Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                        intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                        requireContext().startService(intent);
                    } else {
                        OverlayPermissionUtil.requestOverlayPermission(activity, 1001);
                        return false;
                    }
                } else {
                    OverlayPermissionUtil.stopDesktopLyricsService(requireContext());
                    // Also send an update settings intent to ensure it's properly disabled
                    Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                    intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                    requireContext().startService(intent);
                }
            }
            return true;
        });

        findPreference("desktop_lyrics_font_size").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Integer) {
                int size = (Integer) newValue;
                // Convert seekbar value (0-20) to actual font size adjustment
                // 0 means use system default, then each increment adds 1sp
                float fontSize = size == 0 ? 0f : 12f + size;
                Preferences.setDesktopLyricsFontSize(fontSize);
                
                // Update desktop lyrics with new font size
                if (Preferences.isDesktopLyricsEnabled()) {
                    Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                    intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                    requireContext().startService(intent);
                }
            }
            return true;
        });

        findPreference("desktop_lyrics_opacity").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Integer) {
                int opacity = (Integer) newValue;
                // Convert seekbar value (0-100) to actual opacity (0.0-1.0)
                float opacityFloat = opacity / 100f;
                Preferences.setDesktopLyricsOpacity(opacityFloat);
                
                // Update desktop lyrics with new opacity
                if (Preferences.isDesktopLyricsEnabled()) {
                    Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                    intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                    requireContext().startService(intent);
                }
            }
            return true;
        });

        findPreference("desktop_lyrics_font_color").setOnPreferenceClickListener(preference -> {
            // Load color values from resources
            String[] colorValues = getResources().getStringArray(R.array.desktop_lyrics_font_color_values);
            String[] colorTitles = getResources().getStringArray(R.array.desktop_lyrics_font_color_titles);
            
            // Create alert dialog builder
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle(R.string.settings_desktop_lyrics_font_color);
            
            // Create grid view for color options
            GridView colorGridView = new GridView(requireContext());
            // Set number of columns
            colorGridView.setNumColumns(8);
            // Set spacing
            colorGridView.setHorizontalSpacing(6);
            colorGridView.setVerticalSpacing(6);
            // Set padding
            colorGridView.setPadding(6, 6, 6, 6);
            
            // Create adapter
            ColorGridAdapter adapter = new ColorGridAdapter(requireContext(), colorTitles, colorValues);
            colorGridView.setAdapter(adapter);
            
            // Create and show the dialog
            AlertDialog dialog = builder.create();
            dialog.setView(colorGridView);
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), 
                    (dialogInterface, which) -> dialogInterface.dismiss());
            
            // Set item click listener
            colorGridView.setOnItemClickListener((parent, view, position, id) -> {
                String selectedColor = colorValues[position];
                Preferences.setDesktopLyricsFontColor(selectedColor);
                
                // Update desktop lyrics with new color
                if (Preferences.isDesktopLyricsEnabled()) {
                    Intent intent = new Intent(requireContext(), DesktopLyricsService.class);
                    intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                    requireContext().startService(intent);
                }
                
                // Do not dismiss the dialog, allow user to test multiple colors
            });
            
            dialog.show();
            return true;
        });
    }

    private class ColorGridAdapter extends ArrayAdapter<String> {
        private String[] colorValues;

        public ColorGridAdapter(Context context, String[] colorTitles, String[] colorValues) {
            super(context, 0, colorTitles);
            this.colorValues = colorValues;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                // Create a new linear layout for the grid item
                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER);
                layout.setPadding(4, 4, 4, 4);
                
                // Create color circle
                ImageView colorCircle = new ImageView(getContext());
                // Convert dp to pixels for consistent sizing
                int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, 
                        getContext().getResources().getDisplayMetrics());
                // Set fixed width and height to ensure circle shape
                LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(size, size);
                colorCircle.setLayoutParams(circleParams);
                colorCircle.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                // Add views to layout
                layout.addView(colorCircle);
                
                // Create view holder
                holder = new ViewHolder();
                holder.colorCircle = colorCircle;
                layout.setTag(holder);
                
                convertView = layout;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            // Create a drawable from the shape resource
            Drawable circleDrawable = ContextCompat.getDrawable(getContext(), R.drawable.color_circle);
            if (circleDrawable != null) {
                // Mutate the drawable to avoid sharing state
                circleDrawable = circleDrawable.mutate();
                // Parse the color value
                int color = Color.parseColor(colorValues[position]);
                // Set the tint color
                circleDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                // Set as background
                holder.colorCircle.setBackground(circleDrawable);
            }
            
            return convertView;
        }
        
        private class ViewHolder {
            ImageView colorCircle;
        }
    }
}
