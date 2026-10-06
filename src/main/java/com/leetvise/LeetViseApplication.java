package com.leetvise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.LinkedList;

/** LeetVise – revise the LeetCode problems you've already solved. Run it, then open http://localhost:8080 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LeetViseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetViseApplication.class, args);
    }
}
