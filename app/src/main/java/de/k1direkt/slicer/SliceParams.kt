package de.k1direkt.slicer

enum class Material(val label: String, val nozzleTemp: Int, val bedTemp: Int, val fanPercent: Int) {
    PLA("PLA", 220, 60, 100),
    PETG("PETG", 240, 75, 50),
    ABS("ABS", 250, 100, 0),
    ASA("ASA", 255, 100, 20),
    TPU("TPU", 230, 50, 100),
    PLA_CF("PLA-CF", 230, 60, 100),
}

enum class SupportMode(val label: String) {
    NONE("Keine"),
    BUILDPLATE("Nur Platte"),
    EVERYWHERE("Überall"),
}

data class SliceParams(
    val layerHeight: Float = 0.2f,
    val infillPercent: Int = 15,
    val support: SupportMode = SupportMode.NONE,
    val material: Material = Material.PLA,
    val brimWidth: Float = 0f,
    /** 0 = Standardwert des Materials */
    val nozzleTemp: Int = 0,
    /** 0 = Standardwert des Materials */
    val bedTemp: Int = 0,
    val perimeters: Int = 2,
    /** Geschwindigkeitsfaktor in Prozent (100 = Profil-Standard des K1 Max) */
    val speedPercent: Int = 100,
    val scalePercent: Int = 100,
    val rotateZ: Int = 0,
) {
    val effectiveNozzleTemp get() = if (nozzleTemp > 0) nozzleTemp else material.nozzleTemp
    val effectiveBedTemp get() = if (bedTemp > 0) bedTemp else material.bedTemp
}
