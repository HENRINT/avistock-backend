plugins {
    java
    application
}

group = "com.avistock"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // 1. Servidor Web Ligero (Javalin) y Logs
    implementation("io.javalin:javalin:6.1.3")
    implementation("org.slf4j:slf4j-simple:2.0.12")

    // 2. Procesamiento de JSON (Jackson para Javalin)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    // NUEVO: sin esto, Jackson no puede convertir a JSON ningún campo LocalDateTime/LocalDate
    // (como VentasMostrador.fechaHora o Apartado.fechaRegistro) y truena con
    // "Java 8 date/time type not supported by default"
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")

    // 3. Persistencia y Base de Datos (JPA / Hibernate / MySQL)
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
    implementation("com.mysql:mysql-connector-j:8.3.0")

    // 4. Variables de Entorno (Archivo .env)
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // 5. Hash de contraseñas (bcrypt) — necesario antes de exponer el backend en internet
    implementation("at.favre.lib:bcrypt:0.10.2")

}

application {
    mainClass.set("com.avistock.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.avistock.Main"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from(provider {
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}