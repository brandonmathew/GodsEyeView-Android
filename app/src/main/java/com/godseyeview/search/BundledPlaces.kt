package com.godseyeview.search

/** Small offline gazetteer (plan Q15): well-known places that resolve
 *  with no network at all. Heights frame the place at a sensible zoom. */
object BundledPlaces {
    private val places = listOf(
        PlaceResult("Honolulu", "Hawaii, USA", -157.8581, 21.3099, 15_000.0),
        PlaceResult("Ewa Beach", "Hawaii, USA", -158.0072, 21.3156, 8_000.0),
        PlaceResult("Kailua", "Hawaii, USA", -157.7394, 21.4022, 8_000.0),
        PlaceResult("Pearl Harbor", "Hawaii, USA", -157.9772, 21.3670, 6_000.0),
        PlaceResult("Diamond Head", "Hawaii, USA", -157.8058, 21.2610, 3_000.0),
        PlaceResult("Tokyo", "Japan", 139.6917, 35.6895, 40_000.0),
        PlaceResult("Mount Fuji", "Japan", 138.7274, 35.3606, 15_000.0),
        PlaceResult("Sydney", "Australia", 151.2093, -33.8688, 30_000.0),
        PlaceResult("Sydney Opera House", "Australia", 151.2153, -33.8568, 2_500.0),
        PlaceResult("Auckland", "New Zealand", 174.7633, -36.8485, 25_000.0),
        PlaceResult("London", "United Kingdom", -0.1276, 51.5074, 35_000.0),
        PlaceResult("Paris", "France", 2.3522, 48.8566, 30_000.0),
        PlaceResult("Eiffel Tower", "Paris, France", 2.2945, 48.8584, 2_500.0),
        PlaceResult("Rome", "Italy", 12.4964, 41.9028, 30_000.0),
        PlaceResult("Colosseum", "Rome, Italy", 12.4922, 41.8902, 2_500.0),
        PlaceResult("Athens", "Greece", 23.7275, 37.9838, 30_000.0),
        PlaceResult("Cairo", "Egypt", 31.2357, 30.0444, 35_000.0),
        PlaceResult("Pyramids of Giza", "Egypt", 31.1342, 29.9792, 5_000.0),
        PlaceResult("Moscow", "Russia", 37.6173, 55.7558, 35_000.0),
        PlaceResult("Dubai", "UAE", 55.2708, 25.2048, 30_000.0),
        PlaceResult("Singapore", "Singapore", 103.8198, 1.3521, 25_000.0),
        PlaceResult("Beijing", "China", 116.4074, 39.9042, 40_000.0),
        PlaceResult("Reykjavik", "Iceland", -21.9426, 64.1466, 20_000.0),
        PlaceResult("Cape Town", "South Africa", 18.4241, -33.9249, 25_000.0),
        PlaceResult("Rio de Janeiro", "Brazil", -43.1729, -22.9068, 30_000.0),
        PlaceResult("New York City", "USA", -74.0060, 40.7128, 35_000.0),
        PlaceResult("Statue of Liberty", "New York, USA", -74.0445, 40.6892, 2_500.0),
        PlaceResult("Washington, D.C.", "USA", -77.0369, 38.9072, 25_000.0),
        PlaceResult("Chicago", "USA", -87.6298, 41.8781, 35_000.0),
        PlaceResult("Los Angeles", "USA", -118.2437, 34.0522, 40_000.0),
        PlaceResult("San Francisco", "USA", -122.4194, 37.7749, 30_000.0),
        PlaceResult("Golden Gate Bridge", "San Francisco, USA", -122.4783, 37.8199, 4_000.0),
        PlaceResult("Denver", "USA", -104.9903, 39.7392, 35_000.0),
        PlaceResult("Grand Canyon", "Arizona, USA", -112.1124, 36.1069, 45_000.0),
        PlaceResult("Yellowstone", "Wyoming, USA", -110.5885, 44.4280, 60_000.0),
        PlaceResult("Mount Everest", "Nepal", 86.9250, 27.9881, 18_000.0),
        PlaceResult("Mount Kilimanjaro", "Tanzania", 37.3556, -3.0674, 18_000.0),
    )

    fun search(query: String): List<PlaceResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return places.filter {
            it.name.lowercase().contains(needle) || it.detail.lowercase().contains(needle)
        }.take(5)
    }
}
