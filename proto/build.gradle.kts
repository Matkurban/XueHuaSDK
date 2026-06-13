plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.protokt)
}

protokt {
    codec {
        minimal()
    }
    generate {
        descriptors = false
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    enabled = false
}
