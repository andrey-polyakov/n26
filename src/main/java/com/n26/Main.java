package com.n26;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;

/**
 * Launcher class
 */
@SpringBootApplication
public class Main extends SpringBootServletInitializer {

    public static void main(String[] args) {
        new Main().configure(new SpringApplicationBuilder(Main.class)).run(args);
    }

}
