// =====================================================================
//  Haken fuer Weihnachts-LED-Lichterketten
//  - Dachrinnen-Haken (halbrunde Rinne mit Wulst / Bordel)
//  - Wandhaken zum Schrauben (Holz, Fassade mit Duebel)
//  - Wandhaken zum Kleben (Fassade, Fensterrahmen ...)
//
//  Parametrisch: Werte unten anpassen oder per Kommandozeile, z.B.
//    openscad -D 'teil="rinne"' -D wulst_d=20 -o haken.stl haken.scad
//  Alle Masse in mm.
// =====================================================================

/* [Auswahl] */
// Welches Teil soll erzeugt werden?
teil = "rinne"; // [rinne, schraube, kleben, alle]

/* [Dachrinnen-Haken] */
// Durchmesser des Rinnenwulstes (vorne an der Rinne) - mit Messschieber messen!
wulst_d   = 20;
// Spiel zwischen Wulst und Haken
spiel     = 0.6;
// Wie weit der Haken vorne unter den Wulst greift (Grad, mehr = fester)
untergriff = 40;
// Laenge des Beins, das innen in der Rinne haengt (unter Wulstmitte)
bein_innen = 14;
// Wie weit die Lichterkette unter dem Wulst haengt (unter Unterkante Wulst)
abhang     = 12;

/* [Wandhaken] */
// Groesse der Grundplatte (Breite x Hoehe)
platte_b  = 24;
platte_h  = 40;
// Dicke der Grundplatte
platte_t  = 4;
// Abstand Kabelauflage von der Wand
arm_l     = 14;
// Hoehe der vorderen Lippe ueber dem Arm
lippe_h   = 5;
// Schraubenloch (fuer 3,5-4 mm Holzschrauben bzw. 4 mm Schraube im Duebel)
loch_d    = 4.5;
// Senkkopf-Durchmesser
senk_d    = 8.6;

/* [Allgemein] */
// Materialstaerke der Haken
t         = 3;
// Breite (Druckhoehe) des Dachrinnen-Hakens
breite    = 10;
// Radius der Kabelaufnahme (3.5 = Kabel bis ca. 6 mm)
kabel_r   = 3.5;
// Winkel, bis zu dem sich die Kabelaufnahme schliesst (groesser = haelt fester)
haken_ende = 60;

$fn = 96;

// ---------------------------------------------------------------------
//  Hilfsfunktionen
// ---------------------------------------------------------------------
function bogen(r, a0, a1, n = 64) =
    [for (i = [0 : n]) let (a = a0 + (a1 - a0) * i / n) [r * cos(a), r * sin(a)]];

// Kreisring-Segment mit abgerundeten Enden
module ring_seg(c, ri, ro, a0, a1) {
    rm = (ri + ro) / 2;
    tw = ro - ri;
    translate(c) {
        polygon(concat(bogen(ro, a0, a1), bogen(ri, a1, a0)));
        for (a = [a0, a1]) translate([rm * cos(a), rm * sin(a)]) circle(d = tw);
    }
}

// J-foermige Kabelaufnahme; Rueckwand liegt bei x = x_wand, Grund bei y = y_grund
module kabel_haken(x_wand, y_mitte) {
    ring_seg([x_wand + kabel_r, y_mitte], kabel_r, kabel_r + t, 180, 360 + haken_ende);
}

// ---------------------------------------------------------------------
//  1) Dachrinnen-Haken  (Profil liegt flach auf dem Druckbett)
//     Ursprung = Mitte des Wulstes, +x = nach vorne (weg vom Haus)
// ---------------------------------------------------------------------
module rinne_profil() {
    R    = wulst_d / 2 + spiel;
    tief = R + abhang;                       // Hoehe Kabelaufnahme unter Wulstmitte
    union() {
        // Buegel ueber den Wulst: von innen (hinten) ueber oben nach vorne und darunter
        ring_seg([0, 0], R, R + t, -untergriff, 180);
        // Bein innen in der Rinne (stuetzt sich an der Rinnenwand ab -> kein Verdrehen)
        translate([-(R + t), -(R + bein_innen)]) square([t, R + bein_innen]);
        translate([-(R + t / 2), -(R + bein_innen)]) circle(d = t);
        // Steg vorne nach unten
        translate([R, -tief]) square([t, tief]);
        // Kabelaufnahme
        kabel_haken(R + t, -tief);
    }
}

module rinnen_haken() {
    linear_extrude(height = breite) rinne_profil();
}

// ---------------------------------------------------------------------
//  2/3) Wandhaken  (Grundplatte liegt flach auf dem Bett = glatte Seite
//       an die Wand, ideal fuer Klebeband)
//       Koordinaten: x = Breite, y = Hoehe an der Wand (oben = +y), z = von Wand weg
// ---------------------------------------------------------------------
module wandhaken(mit_loch) {
    l      = arm_l + t;                         // Arm-Laenge von Plattenoberseite
    arm_y  = mit_loch ? 6 : (platte_h - t) / 2; // Lage des Arms auf der Platte
    loch_y = platte_h - 9;
    difference() {
        union() {
            // Grundplatte mit abgerundeten Ecken
            linear_extrude(height = platte_t)
                offset(r = 3) offset(delta = -3) square([platte_b, platte_h]);

            // Arm + Lippe als extrudiertes Seitenprofil (in y-z-Ebene)
            translate([0, arm_y, platte_t])
                rotate([90, 0, 90]) linear_extrude(height = platte_b)
                    polygon([
                        // y (an der Wand nach oben), z (von der Wand weg)
                        [-2.5, 0],                    // Hohlkehle unten
                        [t + 2.5, 0],                 // Hohlkehle oben
                        [t, 2.5],
                        [t, l - t - lippe_h],         // Kabelauflage (Oberseite Arm)
                        [t + lippe_h, l - t],         // 45-Grad-Fase unter der Lippe
                        [t + lippe_h, l],             // Lippe vorne
                        [0, l],
                        [0, 2.5]
                    ]);
        }
        if (mit_loch) {
            // Durchgangsloch + Senkung (Senkung auf der Vorderseite = oben im Druck)
            translate([platte_b / 2, loch_y, -1]) cylinder(d = loch_d, h = platte_t + 2);
            translate([platte_b / 2, loch_y, platte_t - (senk_d - loch_d) / 2])
                cylinder(d1 = loch_d, d2 = senk_d + 0.01, h = (senk_d - loch_d) / 2 + 0.01);
        }
    }
}

// ---------------------------------------------------------------------
//  Ausgabe
// ---------------------------------------------------------------------
if (teil != "keins" && teil == "rinne")    rinnen_haken();
if (teil != "keins" && teil == "schraube") wandhaken(true);
if (teil != "keins" && teil == "kleben")   wandhaken(false);
if (teil != "keins" && teil == "alle") {
    rinnen_haken();
    translate([45, -20, 0]) wandhaken(true);
    translate([80, -20, 0]) wandhaken(false);
}
