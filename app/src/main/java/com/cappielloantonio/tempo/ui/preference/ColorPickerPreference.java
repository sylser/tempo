package com.cappielloantonio.tempo.ui.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.service.DesktopLyricsService;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.List;

public class ColorPickerPreference extends Preference {
    private List<String> colorValues = new ArrayList<>();
    private List<String> colorTitles = new ArrayList<>();
    private String selectedColor;

    public ColorPickerPreference(Context context) {
        super(context);
        init();
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        readAttributes(attrs);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        readAttributes(attrs);
    }

    private void init() {
        setLayoutResource(R.layout.preference_color_picker);
        // Load default color values from resources
        String[] defaultColorValues = getContext().getResources().getStringArray(R.array.desktop_lyrics_font_color_values);
        String[] defaultColorTitles = getContext().getResources().getStringArray(R.array.desktop_lyrics_font_color_titles);
        
        for (int i = 0; i < defaultColorValues.length; i++) {
            colorValues.add(defaultColorValues[i]);
            colorTitles.add(defaultColorTitles[i]);
        }
        
        // Set default selected color
        selectedColor = Preferences.getDesktopLyricsFontColor();
        // Set click listener to show color picker dialog
        setOnPreferenceClickListener(preference -> {
            showColorPickerDialog();
            return true;
        });
    }

    private void readAttributes(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference);
        a.recycle();
    }

    private void showColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.settings_desktop_lyrics_font_color);
        
        // Create a linear layout for the dialog content
        LinearLayout dialogLayout = new LinearLayout(getContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        
        // Create list view for color options
        ListView colorListView = new ListView(getContext());
        ColorAdapter adapter = new ColorAdapter(getContext(), colorTitles, colorValues);
        colorListView.setAdapter(adapter);
        
        // Add list view to dialog layout
        dialogLayout.addView(colorListView);
        
        // Create and show the dialog
        AlertDialog dialog = builder.create();
        
        // Set dialog content and buttons
        dialog.setView(dialogLayout);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getContext().getString(android.R.string.cancel), 
                (dialogInterface, which) -> dialogInterface.dismiss());
        
        // Set item click listener
        colorListView.setOnItemClickListener((parent, view, position, id) -> {
            selectedColor = colorValues.get(position);
            Preferences.setDesktopLyricsFontColor(selectedColor);
            notifyChanged();
            
            // Update desktop lyrics with new color
            if (Preferences.isDesktopLyricsEnabled()) {
                Intent intent = new Intent(getContext(), DesktopLyricsService.class);
                intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                getContext().startService(intent);
            }
            
            // Dismiss the dialog
            dialog.dismiss();
        });
        
        dialog.show();
    }

    private class ColorAdapter extends ArrayAdapter<String> {
        private List<String> colorValues;

        public ColorAdapter(Context context, List<String> colorTitles, List<String> colorValues) {
            super(context, 0, colorTitles);
            this.colorValues = colorValues;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                // Create a new linear layout for the list item
                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                layout.setPadding(16, 16, 16, 16);
                
                // Create color circle
                ImageView colorCircle = new ImageView(getContext());
                // Convert dp to pixels for consistent sizing
                int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, 
                        getContext().getResources().getDisplayMetrics());
                // Set fixed width and height to ensure circle shape
                LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(size, size);
                circleParams.setMargins(0, 0, 16, 0);
                colorCircle.setLayoutParams(circleParams);
                colorCircle.setScaleType(ImageView.ScaleType.CENTER_CROP);
                
                // Create color label
                TextView colorLabel = new TextView(getContext());
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                colorLabel.setLayoutParams(labelParams);
                
                // Add views to layout
                layout.addView(colorCircle);
                layout.addView(colorLabel);
                
                // Create view holder
                holder = new ViewHolder();
                holder.colorCircle = colorCircle;
                holder.colorLabel = colorLabel;
                layout.setTag(holder);
                
                convertView = layout;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            // Set color label text
            holder.colorLabel.setText(getItem(position));
            
            // Create a drawable from the shape resource
            Drawable circleDrawable = ContextCompat.getDrawable(getContext(), R.drawable.color_circle);
            if (circleDrawable != null) {
                // Mutate the drawable to avoid sharing state
                circleDrawable = circleDrawable.mutate();
                // Parse the color value
                int color = Color.parseColor(colorValues.get(position));
                // Set the tint color
                circleDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                // Set as background
                holder.colorCircle.setBackground(circleDrawable);
            }
            
            return convertView;
        }
        
        private class ViewHolder {
            ImageView colorCircle;
            TextView colorLabel;
        }
    }
}
