package com.bombardierline3.android;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bombardierline3.android.controller.MetroController;
import com.bombardierline3.android.model.Station;
import com.bombardierline3.android.utils.JsonLoader;
import com.bombardierline3.android.utils.RouteGraph;
import com.bombardierline3.android.view.LedDisplayView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private MetroController controller;
    private LedDisplayView ledDisplay;
    private LinearLayout controlPanel;

    private String[][] socialPool;
    private Map<String, Map<String, String>> announcements;
    private List<Station> allStations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ledDisplay = findViewById(R.id.led_display);
        controlPanel = findViewById(R.id.control_panel);
        
        Button btnSetup = findViewById(R.id.btn_setup);
        Button btnNext = findViewById(R.id.btn_trigger);
        Button btnBlackout = findViewById(R.id.btn_blackout);
        Button btnReverse = findViewById(R.id.btn_reverse);

        // Load Data
        socialPool = JsonLoader.loadSocialPool(this, "social_pool.json");
        announcements = JsonLoader.loadAnnouncements(this, "announcements.json");
        allStations = RouteGraph.getInstance(this).getAllUniqueStations();

        btnSetup.setOnClickListener(v -> showSetupDialog(btnSetup));

        btnNext.setOnClickListener(v -> {
            if (controller != null) {
                controller.triggerNextStation();
            }
        });

        btnBlackout.setOnClickListener(v -> {
            if (ledDisplay != null) {
                ledDisplay.clearDisplayForBlackout();
            }
        });

        btnReverse.setOnClickListener(v -> {
            if (controller != null) {
                controller.setDirection(!controller.isForwardDirection());
            }
        });
    }

    private void showSetupDialog(View setupBtn) {
        if (allStations == null || allStations.isEmpty()) {
            Toast.makeText(this, "Stations not loaded yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> stationNames = new ArrayList<>();
        for (Station s : allStations) {
            stationNames.add(s.nameEn);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Setup Route");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        Spinner sourceSpinner = new Spinner(this);
        Spinner destSpinner = new Spinner(this);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, stationNames);
        sourceSpinner.setAdapter(adapter);
        destSpinner.setAdapter(adapter);
        
        // Default to Dwarka Sec 21 (0) and Noida (50) or Vaishali (58)
        sourceSpinner.setSelection(0);
        if (stationNames.size() > 50) {
            destSpinner.setSelection(50);
        }

        layout.addView(sourceSpinner);
        layout.addView(destSpinner);

        builder.setView(layout);
        builder.setPositiveButton("Start Simulator", (dialog, which) -> {
            int srcIdx = sourceSpinner.getSelectedItemPosition();
            int destIdx = destSpinner.getSelectedItemPosition();
            
            Station srcStn = allStations.get(srcIdx);
            Station destStn = allStations.get(destIdx);

            Station[] path = RouteGraph.getInstance(this).getShortestPath(srcStn.nameEn, destStn.nameEn);
            if (path == null || path.length == 0) {
                Toast.makeText(this, "Invalid Route!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Start controller
            controller = new MetroController(ledDisplay, srcStn.nameEn, destStn.nameHi, destStn.nameEn, path, false, socialPool, announcements);
            
            setupBtn.setVisibility(View.GONE);
            controlPanel.setVisibility(View.VISIBLE);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
