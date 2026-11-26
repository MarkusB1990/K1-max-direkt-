include <../haken.scad>
// Nur Vorschau: Rinnenwulst + Rinnenwand (grau) mit Haken (rot)
color("red") rinne_profil();
color("silver") translate([0,0,1]) difference(){ circle(d=wulst_d); circle(d=wulst_d-1.2); }
color("silver") translate([-wulst_d/2,-60,1]) square([0.8,60]);
color("blue") translate([wulst_d/2+spiel+t+kabel_r,-(wulst_d/2+spiel+abhang),1]) circle(r=2.5);
