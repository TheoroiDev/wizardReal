package com.theo.wizardreal.match;
import org.junit.jupiter.api.Test;
public class MetaphoneP {
    @Test void dump() {
        System.out.println("meta(ign)=" + Phonetics.metaphone("ign") + " meta(ignis)=" + Phonetics.metaphone("ignis"));
        System.out.println("score(ign, ignis)=" + Phonetics.score("ign", "ignis"));
        System.out.println("latinize(ign)=" + Phonetics.latinize("ign") + " latinize(ignis)=" + Phonetics.latinize("ignis"));
        System.out.println("score(huan dan, huan dan)=" + Phonetics.score("huan dan", "huan dan"));
    }
}
