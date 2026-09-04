package com.bombardierline3.android.controller;

import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import com.bombardierline3.android.model.Station;
import com.bombardierline3.android.utils.JsonLoader;
import com.bombardierline3.android.utils.RouteGraph;
import com.bombardierline3.android.view.LedDisplayView;

public class MetroController {

        public enum DisplayState {
                NEXT_STATION,
                THIS_STATION,
                DOOR_CLOSING,
                DOOR_OPEN_STATIC
        }

        private static final int COLOR_YELLOW = Color.YELLOW;
        private static final int COLOR_GREEN = Color.GREEN;
        private static final int COLOR_RED = Color.RED;

        private String[][] socialPool;
        private List<Station> stationsList; 
        private Map<String, Map<String, String>> announcements;

        private int currentStationIndex = 0;
        private Handler handler = new Handler(Looper.getMainLooper());
        private Runnable delayRunnable = null;

        private final LedDisplayView ledWindow;

        private String targetDestHi = "";
        private String targetDestEn = "";
        private boolean isForwardDirection = true;
        private String initialSourceEn = "";
        
        private boolean terminateWithoutReverse = false;

        private String cachedFileEndHi = "";
        private String cachedFileEndEn = "";

        public MetroController(LedDisplayView ledWindow, String sourceEn, String destHi, String destEn, Station[] fullStationsArray, boolean terminateWithoutReverse, String[][] socialPool, Map<String, Map<String, String>> announcements) {
                this.ledWindow = ledWindow;
                this.targetDestHi = (destHi != null) ? destHi : "";
                this.targetDestEn = (destEn != null) ? destEn : "";
                this.initialSourceEn = (sourceEn != null) ? sourceEn : "";
                this.terminateWithoutReverse = terminateWithoutReverse;

                this.socialPool = socialPool;
                this.announcements = announcements;

                setupSubRoute(fullStationsArray, sourceEn, destEn);
                determineFileEndStation(destEn);

                updateDisplayToStatic();
        }

        private void determineFileEndStation(String destEn) {
                if (terminateWithoutReverse) {
                        cachedFileEndHi = targetDestHi;
                        cachedFileEndEn = targetDestEn;
                        return;
                }
                if (stationsList.size() >= 2) {
                        Station current = stationsList.get(0);
                        Station next = stationsList.get(1);
                        List<Station> allTerms = RouteGraph.getInstance().getReachableTerminals(next.nameEn, current.nameEn);
                        
                        List<Station> validTerms = new ArrayList<>();
                        List<Station> allUnique = RouteGraph.getInstance().getAllUniqueStations();
                        int currentIndex = allUnique.indexOf(current);
                        
                        for (Station term : allTerms) {
                                int termIndex = allUnique.indexOf(term);
                                if (isForwardDirection && termIndex > currentIndex) {
                                        validTerms.add(term);
                                } else if (!isForwardDirection && termIndex < currentIndex) {
                                        validTerms.add(term);
                                }
                        }

                        if (validTerms.isEmpty()) {
                                cachedFileEndHi = targetDestHi;
                                cachedFileEndEn = targetDestEn;
                        } else {
                                List<String> enNames = new ArrayList<>();
                                List<String> hiNames = new ArrayList<>();
                                for (Station s : validTerms) {
                                        enNames.add(s.nameEn);
                                        hiNames.add(s.nameHi);
                                }
                                cachedFileEndEn = String.join(" or ", enNames);
                                cachedFileEndHi = String.join(" या ", hiNames);
                        }
                } else {
                        cachedFileEndHi = targetDestHi;
                        cachedFileEndEn = targetDestEn;
                }
        }

        private void setupSubRoute(Station[] fullArray, String sourceEn, String destEn) {
                if (fullArray == null || fullArray.length == 0) {
                        stationsList = new ArrayList<>();
                        return;
                }
                
                stationsList = new ArrayList<>(Arrays.asList(fullArray));

                int srcIdx = RouteGraph.getInstance().getAllUniqueStations().indexOf(stationsList.get(0));
                int destIdx = RouteGraph.getInstance().getAllUniqueStations().indexOf(stationsList.get(stationsList.size() - 1));

                if (srcIdx <= destIdx) {
                        isForwardDirection = true;
                } else {
                        isForwardDirection = false;
                }

                currentStationIndex = 0;
                Station finalStn = stationsList.get(stationsList.size() - 1);
                if (terminateWithoutReverse) {
                        finalStn.doorSideEn = "Left";
                        finalStn.doorSideHi = "बाईं";
                } else {
                        finalStn.doorSideEn = "Right";
                        finalStn.doorSideHi = "दाईं";
                }
        }

        public void setDirection(boolean forward) {
                if (this.isForwardDirection != forward) {
                        this.isForwardDirection = forward;
                        if (delayRunnable != null) {
                                handler.removeCallbacks(delayRunnable);
                                delayRunnable = null;
                        }

                        if (stationsList != null && !stationsList.isEmpty()) {
                                Collections.reverse(stationsList);

                                Station finalStation = stationsList.get(stationsList.size() - 1);
                                
                                if (terminateWithoutReverse) {
                                        finalStation.doorSideEn = "Left";
                                        finalStation.doorSideHi = "बाईं";
                                } else if (!terminateWithoutReverse) {
                                        finalStation.doorSideEn = "Right";
                                        finalStation.doorSideHi = "दाईं";
                                }

                                targetDestEn = finalStation.nameEn;
                                targetDestHi = finalStation.nameHi;

                                determineFileEndStation(targetDestEn);
                        }

                        currentStationIndex = 0;
                        updateDisplayToStatic();
                }
        }

        public boolean isForwardDirection() {
                return isForwardDirection;
        }

        public void clearScreen() {
                if (delayRunnable != null) {
                        handler.removeCallbacks(delayRunnable);
                        delayRunnable = null;
                }
                if (ledWindow != null) {
                        ledWindow.applySystemDisplayPayload(DisplayState.DOOR_OPEN_STATIC, "", "");
                }
        }

        public void triggerNextStation() {
                if (delayRunnable != null) {
                        handler.removeCallbacks(delayRunnable);
                        delayRunnable = null;
                }

                final int nextIndex = Math.min(currentStationIndex + 1, stationsList.size() - 1);

                currentStationIndex = nextIndex;
                Station station = stationsList.get(nextIndex);

                StringBuilder hindiMsg = new StringBuilder();
                StringBuilder englishMsg = new StringBuilder();

                boolean isTerminationTrigger = false;
                boolean isUltimateDest = RouteGraph.getInstance().isTerminalStation(targetDestEn);
                
                if (stationsList.size() >= 2 && announcements.containsKey("termination")) {
                        if (!isUltimateDest) {
                                if (terminateWithoutReverse) {
                                        if (nextIndex >= stationsList.size() - 4 && nextIndex <= stationsList.size() - 1) {
                                                isTerminationTrigger = true;
                                        }
                                } else {
                                        if (nextIndex >= stationsList.size() - 4 && nextIndex <= stationsList.size() - 2) {
                                                isTerminationTrigger = true;
                                        }
                                }
                        }
                }

                int activeSocialIndex = isForwardDirection ? station.socialIndexForward : station.socialIndexBackward;

                if (isTerminationTrigger) {
                        String destHi = targetDestHi;
                        String destEn = targetDestEn;
                        
                        String lastSecHi;
                        String lastSecEn;
                        
                        if (terminateWithoutReverse) {
                                lastSecHi = targetDestHi;
                                lastSecEn = targetDestEn;
                        } else {
                                if (stationsList.size() >= 2) {
                                        Station lastSecondStation = stationsList.get(stationsList.size() - 2);
                                        lastSecHi = lastSecondStation.nameHi;
                                        lastSecEn = lastSecondStation.nameEn;
                                } else {
                                        lastSecHi = targetDestHi;
                                        lastSecEn = targetDestEn;
                                }
                        }

                        String endHi = cachedFileEndHi;
                        String endEn = cachedFileEndEn;

                        Map<String, String> termAnn = announcements.get("termination");
                        if (termAnn != null) {
                                String hindiTemplate = termAnn.get("hindi");
                                String englishTemplate = termAnn.get("english");

                                if (hindiTemplate != null) {
                                        hindiMsg.append(hindiTemplate
                                                .replace("{destinationStationHi}", destHi)
                                                .replace("{lastSecondStationHi}", lastSecHi)
                                                .replace("{endStationHi}", endHi));
                                }
                                if (englishTemplate != null) {
                                        englishMsg.append(englishTemplate
                                                .replace("{destinationStationEn}", destEn)
                                                .replace("{lastSecondStationEn}", lastSecEn)
                                                .replace("{endStationEn}", endEn));
                                }
                        }

                        if (ledWindow != null) {
                            ledWindow.applySystemDisplayPayload(DisplayState.NEXT_STATION, englishMsg.toString().trim(), hindiMsg.toString().trim());
                        }

                        delayRunnable = () -> {
                            if (currentStationIndex != nextIndex) return;

                            StringBuilder normalHindi = new StringBuilder();
                            StringBuilder normalEnglish = new StringBuilder();

                            Map<String, String> nextAnn = announcements.get("nextStation");
                            if (nextAnn != null) {
                                    String prefixHi = nextAnn.get("hindiPrefix");
                                    String stnStrHi = nextAnn.get("hindiStnStr");
                                    String suffixHi = nextAnn.get("hindiSuffix");
                                    String prefixEn = nextAnn.get("englishPrefix");
                                    String suffixEn = nextAnn.get("englishSuffix");

                                    if (prefixHi != null) normalHindi.append(prefixHi).append(" ");
                                    if (stnStrHi != null) normalHindi.append(stnStrHi).append(" ");
                                    if (station.nameHi != null) normalHindi.append(station.nameHi);
                                    if (suffixHi != null) normalHindi.append(" ").append(suffixHi);

                                    if (prefixEn != null) normalEnglish.append(prefixEn).append(" ");
                                    if (station.nameEn != null) normalEnglish.append(station.nameEn);
                                    if (suffixEn != null) normalEnglish.append(suffixEn);
                            }

                            if (activeSocialIndex >= 0 && socialPool != null && activeSocialIndex < socialPool.length) {
                                    normalHindi.append(" ").append(socialPool[activeSocialIndex][0]);
                                    normalEnglish.append(" ").append(socialPool[activeSocialIndex][1]);
                            }
                            if (ledWindow != null) {
                                ledWindow.applySystemDisplayPayload(DisplayState.NEXT_STATION, normalEnglish.toString().trim(), normalHindi.toString().trim());
                            }
                        };
                        handler.postDelayed(delayRunnable, 35000);

                } else {
                        Map<String, String> nextAnn = announcements.get("nextStation");
                        if (nextAnn != null) {
                                String prefixHi = nextAnn.get("hindiPrefix");
                                String stnStrHi = nextAnn.get("hindiStnStr");
                                String suffixHi = nextAnn.get("hindiSuffix");
                                String prefixEn = nextAnn.get("englishPrefix");
                                String suffixEn = nextAnn.get("englishSuffix");

                                if (prefixHi != null) hindiMsg.append(prefixHi).append(" ");
                                if (stnStrHi != null) hindiMsg.append(stnStrHi).append(" ");
                                if (station.nameHi != null) hindiMsg.append(station.nameHi);
                                if (suffixHi != null) hindiMsg.append(" ").append(suffixHi);

                                if (prefixEn != null) englishMsg.append(prefixEn).append(" ");
                                if (station.nameEn != null) englishMsg.append(station.nameEn);
                                if (suffixEn != null) englishMsg.append(suffixEn);
                        }

                        if (activeSocialIndex >= 0 && socialPool != null && activeSocialIndex < socialPool.length) {
                                hindiMsg.append(" ").append(socialPool[activeSocialIndex][0]);
                                englishMsg.append(" ").append(socialPool[activeSocialIndex][1]);
                        }

                        if (ledWindow != null) {
                            ledWindow.applySystemDisplayPayload(DisplayState.NEXT_STATION, englishMsg.toString().trim(), hindiMsg.toString().trim());
                        }
                }
        }

        public void triggerThisStation() {
                if (delayRunnable != null) {
                        handler.removeCallbacks(delayRunnable);
                        delayRunnable = null;
                }
                
                Station station = stationsList.get(currentStationIndex);
                Map<String, String> thisAnn = announcements.get("thisStation");
                Map<String, String> doorAnn = announcements.get("doorOpening");
                Map<String, String> gapAnn = announcements.get("gapWarning");

                StringBuilder hindiMsg = new StringBuilder();
                StringBuilder englishMsg = new StringBuilder();

                if (thisAnn != null) {
                        String stnStrHi = thisAnn.get("hindiStnStr");
                        String suffixHi = thisAnn.get("hindiSuffix");
                        String prefixEn = thisAnn.get("englishStnStr");
                        String suffixEn = thisAnn.get("englishSuffix");

                        if (station.nameHi != null) hindiMsg.append(station.nameHi);
                        if (stnStrHi != null) hindiMsg.append(" ").append(stnStrHi);
                        if (suffixHi != null) hindiMsg.append(suffixHi);

                        if (station.nameEn != null) englishMsg.append(station.nameEn);
                        if (prefixEn != null) englishMsg.append(" ").append(prefixEn);
                        if (suffixEn != null) englishMsg.append(suffixEn);
                }

                if (station.interchangeEn != null && !station.interchangeEn.isEmpty()) {
                        if (station.interchangeHi != null) hindiMsg.append(" ").append(station.interchangeHi);
                        englishMsg.append(" ").append(station.interchangeEn);
                }

                if (doorAnn != null) {
                        String doorHiTemplate = doorAnn.get("hindi");
                        String doorEnTemplate = doorAnn.get("english");

                        if (terminateWithoutReverse && currentStationIndex == stationsList.size() - 1) {
                                if (doorHiTemplate != null) hindiMsg.append(" ").append(doorHiTemplate.replace("{sideHi}", "बाईं"));
                                if (doorEnTemplate != null) englishMsg.append(" ").append(doorEnTemplate.replace("{sideEn}", "Left"));
                        } else {
                                if (doorHiTemplate != null && station.doorSideHi != null) {
                                        hindiMsg.append(" ").append(doorHiTemplate.replace("{sideHi}", station.doorSideHi));
                                }
                                if (doorEnTemplate != null && station.doorSideEn != null) {
                                        englishMsg.append(" ").append(doorEnTemplate.replace("{sideEn}", station.doorSideEn));
                                }
                        }
                }

                if (gapAnn != null) {
                        String gapHi = gapAnn.get("hindi");
                        String gapEn = gapAnn.get("english");

                        if (gapHi != null && !gapHi.isEmpty()) hindiMsg.append(" ").append(gapHi);
                        if (gapEn != null && !gapEn.isEmpty()) englishMsg.append(" ").append(gapEn);
                }

                if (ledWindow != null) {
                    ledWindow.applySystemDisplayPayload(DisplayState.THIS_STATION, englishMsg.toString().trim(), hindiMsg.toString().trim());
                }

                delayRunnable = () -> triggerCloseDoors();
                handler.postDelayed(delayRunnable, 10000);
        }

        public void triggerCloseDoors() {
                if (delayRunnable != null) {
                        handler.removeCallbacks(delayRunnable);
                        delayRunnable = null;
                }

                Map<String, String> closeAnn = announcements.get("closingDoor");
                String hindiMsg = "", englishMsg = "";
                if (closeAnn != null) {
                        if (closeAnn.get("hindi") != null) hindiMsg = closeAnn.get("hindi");
                        if (closeAnn.get("english") != null) englishMsg = closeAnn.get("english");
                }
                if (ledWindow != null) {
                    ledWindow.applySystemDisplayPayload(DisplayState.DOOR_CLOSING, englishMsg, hindiMsg);
                }
        }

        private void updateDisplayToStatic() {
                if (stationsList == null || stationsList.isEmpty()) return;
                Station station = stationsList.get(currentStationIndex);
                if (ledWindow != null) {
                    ledWindow.applySystemDisplayPayload(DisplayState.DOOR_OPEN_STATIC, station.nameEn, station.nameHi);
                }
        }
}