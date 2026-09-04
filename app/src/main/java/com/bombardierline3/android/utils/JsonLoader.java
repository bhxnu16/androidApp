package com.bombardierline3.android.utils;

import android.content.Context;
import com.bombardierline3.android.model.Station;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Station[] loadStations(Context context, String filename) throws Exception {
        try (InputStream is = context.getAssets().open(filename)) {
            return mapper.readValue(is, Station[].class);
        }
    }

    public static String[][] loadSocialPool(Context context, String filename) {
        try (InputStream is = context.getAssets().open(filename)) {
            return mapper.readValue(is, String[][].class);
        } catch (Exception e) {
            e.printStackTrace();
            return new String[0][0];
        }
    }

    public static Map<String, Map<String, String>> loadAnnouncements(Context context, String filename) {
        try (InputStream is = context.getAssets().open(filename)) {
            return mapper.readValue(is, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
