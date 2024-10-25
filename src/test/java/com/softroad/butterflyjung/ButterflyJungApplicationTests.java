package com.softroad.butterflyjung;

import com.softroad.butterflyjung.layouts.ForceDirected;
import org.junit.jupiter.api.Test;

import static com.softroad.butterflyjung.layouts.ForceDirected.testGraph;



class ButterflyJungApplicationTests {

    @Test
    void contextLoads() {
        new ForceDirected(testGraph(), 1536, 1024).show();
    }

}
