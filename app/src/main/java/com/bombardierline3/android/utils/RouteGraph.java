package com.bombardierline3.android.utils;

import android.content.Context;
import com.bombardierline3.android.model.Station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class RouteGraph {

    private static RouteGraph instance;

    public static RouteGraph getInstance(Context context) {
        if (instance == null) {
            instance = new RouteGraph();
            try {
                instance.addLine(JsonLoader.loadStations(context, "stations.json"), "main");
                instance.addLine(JsonLoader.loadStations(context, "stations_noida_electronic_city.json"), "noida");
                instance.addLine(JsonLoader.loadStations(context, "stations_vaishali.json"), "vaishali");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    private Map<String, Station> stationMap = new HashMap<>();
    private Map<String, Set<String>> adjList = new HashMap<>();
    private List<Station> allUniqueStations = new ArrayList<>();
    private Map<String, Map<String, Station>> nodeVersions = new HashMap<>();

    public void addLine(Station[] lineStations, String sourceId) {
        if (lineStations == null || lineStations.length == 0) return;

        for (int i = 0; i < lineStations.length; i++) {
            Station current = lineStations[i];
            String name = current.nameEn.toLowerCase().trim();

            if (!nodeVersions.containsKey(name)) {
                nodeVersions.put(name, new HashMap<>());
            }
            nodeVersions.get(name).put(sourceId, current);

            if (!stationMap.containsKey(name)) {
                stationMap.put(name, current);
                allUniqueStations.add(current);
                adjList.put(name, new HashSet<>());
            }

            if (i > 0) {
                String prev = lineStations[i - 1].nameEn.toLowerCase().trim();
                adjList.get(name).add(prev);
                adjList.get(prev).add(name);
            }
        }
    }

    public List<Station> getAllUniqueStations() {
        return allUniqueStations;
    }

    public Station getStation(String nameEn) {
        return stationMap.get(nameEn.toLowerCase().trim());
    }

    public boolean isTerminalStation(String nameEn) {
        String name = nameEn.toLowerCase().trim();
        if (!adjList.containsKey(name)) return false;
        return adjList.get(name).size() == 1;
    }

    public Station[] getShortestPath(String sourceEn, String destEn) {
        String start = sourceEn.toLowerCase().trim();
        String target = destEn.toLowerCase().trim();

        if (!adjList.containsKey(start) || !adjList.containsKey(target)) {
            return new Station[0];
        }

        Queue<String> queue = new LinkedList<>();
        Map<String, String> parentMap = new HashMap<>();
        Set<String> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        parentMap.put(start, null);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(target)) {
                break;
            }

            for (String neighbor : adjList.get(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!parentMap.containsKey(target) && !start.equals(target)) {
            return new Station[0]; // No path found
        }

        List<String> pathNames = new ArrayList<>();
        String curr = target;
        while (curr != null) {
            pathNames.add(curr);
            curr = parentMap.get(curr);
        }
        Collections.reverse(pathNames);

        String bestSourceId = "main";
        for (String node : pathNames) {
            Map<String, Station> versions = nodeVersions.get(node);
            if (versions != null) {
                if (versions.containsKey("vaishali") && !versions.containsKey("main")) {
                    bestSourceId = "vaishali";
                } else if (versions.containsKey("noida") && !versions.containsKey("main")) {
                    bestSourceId = "noida";
                }
            }
        }

        List<Station> path = new ArrayList<>();
        for (String node : pathNames) {
            Map<String, Station> versions = nodeVersions.get(node);
            if (versions != null) {
                if (versions.containsKey(bestSourceId)) {
                    path.add(versions.get(bestSourceId));
                } else if (versions.containsKey("main")) {
                    path.add(versions.get("main"));
                } else {
                    path.add(versions.values().iterator().next());
                }
            }
        }

        return path.toArray(new Station[0]);
    }

    public List<Station> getReachableTerminals(String currentStationEn, String previousStationEn) {
        String start = currentStationEn.toLowerCase().trim();
        String avoid = (previousStationEn != null) ? previousStationEn.toLowerCase().trim() : "";

        List<Station> terminals = new ArrayList<>();
        if (!adjList.containsKey(start)) return terminals;

        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        if (!avoid.isEmpty()) {
            visited.add(avoid);
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> neighbors = adjList.get(current);

            if (neighbors.size() == 1) {
                terminals.add(stationMap.get(current));
            }

            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return terminals;
    }
}
