allprojects {

    repositories {
        jcenter()
    }

    group = "com.github.gmazzo"
    version = "1.2.0"

    project.plugins.withType(JavaPlugin::class.java) {
        dependencies.add("testImplementation", "junit:junit:4.12")
    }

}