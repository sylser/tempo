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
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.service.DesktopLyricsService;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.List;

public class ColorPickerPreference extends Preference {
    private List<String> colorValues = new ArrayList<>();
    private List<String> colorTitles = new ArrayList<>();
    private String selectedColor;
    private int numColumns = 4;

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
    }

    private void readAttributes(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference);
        numColumns = a.getInt(R.styleable.ColorPickerPreference_numColumns, 4);
        a.recycle();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        // Get the color grid view from the layout
        GridLayout colorGrid = holder.itemView.findViewById(R.id.color_picker_grid);
        
        // Clear any existing views in the grid
        colorGrid.removeAllViews();
        
        // Setup grid layout
        colorGrid.setColumnCount(numColumns);
        colorGrid.setRowCount((int) Math.ceil((double) colorValues.size() / numColumns));
        
        // Add color options
        for (int i = 0; i < colorValues.size(); i++) {
            String colorValue = colorValues.get(i);
            String colorTitle = colorTitles.get(i);
            
            // Create color option view
            LinearLayout colorOption = new LinearLayout(getContext());
            colorOption.setOrientation(LinearLayout.VERTICAL);
            colorOption.setGravity(Gravity.CENTER);
            colorOption.setPadding(8, 8, 8, 8);
            
            // Create color circle
            ImageView colorCircle = new ImageView(getContext());
            // Convert dp to pixels for consistent sizing
            int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, 
                    getContext().getResources().getDisplayMetrics());
            // Set fixed width and height to ensure circle shape
            colorCircle.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            colorCircle.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            // Create a drawable from the shape resource
            Drawable circleDrawable = ContextCompat.getDrawable(getContext(), R.drawable.color_circle).mutate();
            if (circleDrawable != null) {
                // Parse the color value
                int color = Color.parseColor(colorValue);
                // Set the tint color
                circleDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                // Set as background
                colorCircle.setBackground(circleDrawable);
            }
            
            // Create color label
            TextView colorLabel = new TextView(getContext());
            colorLabel.setText(colorTitle);
            colorLabel.setTextSize(12);
            colorLabel.setGravity(Gravity.CENTER);
            colorLabel.setPadding(0, 4, 0, 0);
            
            // Add to color option
            colorOption.addView(colorCircle);
            colorOption.addView(colorLabel);
            
            // Add click listener
            final String finalColorValue = colorValue;
            colorOption.setOnClickListener(v -> {
                selectedColor = finalColorValue;
                Preferences.setDesktopLyricsFontColor(selectedColor);
                notifyChanged();
                
                // Update desktop lyrics with new color
                if (Preferences.isDesktopLyricsEnabled()) {
                    Intent intent = new Intent(getContext(), DesktopLyricsService.class);
                    intent.setAction(DesktopLyricsService.ACTION_UPDATE_SETTINGS);
                    getContext().startService(intent);
                }
            });
            
            // Add to grid
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            colorGrid.addView(colorOption, params);
        }
    }
}
