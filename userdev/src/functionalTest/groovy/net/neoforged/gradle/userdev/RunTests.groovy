package net.neoforged.gradle.userdev


import net.neoforged.trainingwheels.gradle.functional.BuilderBasedTestSpecification
import org.gradle.testkit.runner.TaskOutcome

class RunTests extends BuilderBasedTestSpecification {

    @Override
    protected void configurePluginUnderTest() {
        pluginUnderTest = "net.neoforged.gradle.userdev";
        injectIntoAllProject = true;
    }

    def "a mod using a version library should be able to run the game"() {
        given:
        def project = create("version_libs_runnable", {
            it.file("gradle/libs.versions.toml",
                    """
                    [versions]
                    # Neoforge Settings
                    neoforge = "+"
                    
                    [libraries]
                    neoforge = { group = "net.neoforged", name = "neoforge", version.ref = "neoforge" }
                    """.trim())

            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
                        
            dependencies {
                implementation(libs.neoforge)
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':runClientData')
            //We are expecting this test to fail, since there is a mod without any files included so it is fine.
            it.shouldFail()
            it.stacktrace()
        }

        then:
        true
        run.task(':writeMinecraftClasspathClientData').outcome == TaskOutcome.SUCCESS
        run.output.contains("is not a valid mod file")
    }

    def "configuring of the configurations after the dependencies block should work"() {
        given:
        def project = create("runs_configuration_after_dependencies", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            sourceSets {
                modRun {
                    java.setSrcDirs(['src/main/mod'])
                    resources.setSrcDirs(['src/main/modResources'])
                }
            }
                        
            dependencies {
                implementation "net.neoforged:neoforge:+"
            }
            
            runs {
                clientData {
                    modSource project.sourceSets.main
                }
            }
            
            configurations {
                modRunImplementation.extendsFrom implementation
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':runClientData')
            //We are expecting this test to fail, since there is a mod without any files included so it is fine.
            it.shouldFail()
        }

        then:
        run.task(':writeMinecraftClasspathClientData').outcome == TaskOutcome.SUCCESS
        run.output.contains("is not a valid mod file")
    }

    def "runs can be declared before the dependencies block"() {
        given:
        def project = create("runs_before_dependencies", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            runs {
                client {
                    modSource project.sourceSets.main
                }
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':tasks')
        }

        then:
        run.task(':tasks').outcome == TaskOutcome.SUCCESS
        run.output.contains('runClient')
    }

    def "when referencing pom on run classpath, it should not list it on the LCP, but it should list its dependencies"() {
        given:
        def project = create("runs_support_poms", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                client {
                    dependencies {
                        runtime 'org.graalvm.polyglot:python:23.1.2'
                    }
                    
                    modSource project.sourceSets.main
                }
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':writeMinecraftClasspathClient')
        }

        then:
        run.task(':writeMinecraftClasspathClient').outcome == TaskOutcome.SUCCESS

        def neoformDir = run.file(".gradle/configuration/neoForm")
        def versionedNeoformDir = neoformDir.listFiles()[0]
        def stepsDir = new File(versionedNeoformDir, "steps")
        def stepDir = new File(stepsDir, "writeMinecraftClasspathClient")
        def classpathFile = new File(stepDir, "classpath.txt")

        classpathFile.exists()

        classpathFile.text.contains("org.graalvm.polyglot${File.separator}polyglot")
        !classpathFile.text.contains(".pom")
    }

    def "userdev supports custom run dependencies"() {
        given:
        def project = create("run_with_custom_dependencies", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                client {
                    dependencies {
                        runtime 'org.jgrapht:jgrapht-core:+'
                    }
                    
                    modSource project.sourceSets.main
                }
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':writeMinecraftClasspathClient')
        }

        then:
        run.task(':writeMinecraftClasspathClient').outcome == TaskOutcome.SUCCESS

        def neoformDir = run.file(".gradle/configuration/neoForm")
        def versionedNeoformDir = neoformDir.listFiles()[0]
        def stepsDir = new File(versionedNeoformDir, "steps")
        def stepDir = new File(stepsDir, "writeMinecraftClasspathClient")
        def classpathFile = new File(stepDir, "classpath.txt")

        classpathFile.exists()

        classpathFile.text.contains("org.jgrapht${File.separator}jgrapht-core")
    }

    def "userdev supports custom run dependencies from configuration"() {
        given:
        def project = create("run_with_custom_dependencies_from_configuration", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            configurations {
                runRuntime { }
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
                runRuntime 'org.jgrapht:jgrapht-core:+'
            }
            
            runs {
                client {
                    dependencies {
                        runtime project.configurations.runRuntime
                    }
                    
                    modSource project.sourceSets.main
                }
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':writeMinecraftClasspathClient')
            it.stacktrace()
            
        }

        then:
        run.task(':writeMinecraftClasspathClient').outcome == TaskOutcome.SUCCESS

        def neoformDir = run.file(".gradle/configuration/neoForm")
        def versionedNeoformDir = neoformDir.listFiles()[0]
        def stepsDir = new File(versionedNeoformDir, "steps")
        def stepDir = new File(stepsDir, "writeMinecraftClasspathClient")
        def classpathFile = new File(stepDir, "classpath.txt")

        classpathFile.exists()

        classpathFile.text.contains("org.jgrapht${File.separator}jgrapht-core")
    }

    def "userdev supports custom run dependencies from catalog"() {
        given:
        def project = create("run_with_custom_dependencies_from_configuration", {
            it.file("gradle/libs.versions.toml",
                    """
                    [libraries]
                    jgrapht = { group = "org.jgrapht", name = "jgrapht-core", version = "+" }
                    """.trim())

            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            configurations {
                runRuntime { }
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                client {
                    dependencies {
                        runtime libs.jgrapht
                    }
                    
                    modSource project.sourceSets.main
                }
            }
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':writeMinecraftClasspathClient')
        }

        then:
        run.task(':writeMinecraftClasspathClient').outcome == TaskOutcome.SUCCESS

        def neoformDir = run.file(".gradle/configuration/neoForm")
        def versionedNeoformDir = neoformDir.listFiles()[0]
        def stepsDir = new File(versionedNeoformDir, "steps")
        def stepDir = new File(stepsDir, "writeMinecraftClasspathClient")
        def classpathFile = new File(stepDir, "classpath.txt")

        classpathFile.exists()

        classpathFile.text.contains("org.jgrapht${File.separator}jgrapht-core")
    }

    @Override
    protected File getTestTempDirectory() {
        return new File("build", "unit-testing-2")
    }

    def "userdev supports unit testing"() {
        given:
        def project = create("userdev_supports_unit_tests", {
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            runs {
                junit {
                    modSource project.sourceSets.main
                    unitTestSource project.sourceSets.test
                }
            }
            
            test {
                useJUnitPlatform()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
                
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.8.1'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.1'
            }
            """)
            //We need to add a manifest.mf file to the test source set
            it.file("src/test/resources/META-INF/MANIFEST.MF", """Manifest-Version: 1.0
            |FMLModType: GAMELIBRARY
            |""".stripMargin())

            it.file("src/test/java/net/test/TestTest.java",
                    """
                    package net.test;
                    
                    import net.minecraft.server.MinecraftServer;
                    import net.minecraft.tags.ItemTags;
                    import net.minecraft.world.item.Items;
                    import net.minecraft.world.item.crafting.Ingredient;
                    import net.neoforged.testframework.junit.EphemeralTestServerProvider;
                    import org.junit.jupiter.api.Assertions;
                    import org.junit.jupiter.api.Test;
                    import org.junit.jupiter.api.extension.ExtendWith;
                    import net.minecraft.core.registries.Registries;
                    
                    @ExtendWith(EphemeralTestServerProvider.class)
                    public class TestTest {
                        @Test
                        public void testIngredient(MinecraftServer server) { // required to load tags
                            Assertions.assertTrue(
                                    Ingredient.of(server.registryAccess().lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.AXES)).test(Items.DIAMOND_AXE.getDefaultInstance())
                            );
                        }
                    }
                    """.trim())
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(":testJunit")
        }

        then:
        run.task(':testJunit').outcome == TaskOutcome.SUCCESS
    }

    def "runs with no modsource create problem"() {
        given:
        def project = create("runs_with_no_modsource_create_problem", {
            it.property('neogradle.subsystems.conventions.runs.enabled', 'false')
            it.property('neogradle.subsystems.conventions.sourcesets.enabled', 'false')
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                client {
                    // no modsource
                }
            }
            
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':dependencies')
            it.shouldFail()
        }

        then:
        run.getOutput().contains("(Run: client) The run: client has no source sets configured.")
    }

    def "runs can inherit from each other"() {
        given:
        def project = create("runs_can_inherit_from_each_other", {
            it.property('neogradle.subsystems.conventions.runs.enabled', 'false')
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                client {
                }
            
                clientTwo {
                    configure runs.client
                }
                
                clientThree {
                    run 'clientTwo'                
                }
            }
            
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':runs')
            it.stacktrace()
        }

        then:
        def lines = run.getOutput().split("\n");
        def firstRunIndex = lines.findIndexOf { line -> line.startsWith("Run: client")}
        def secondRunIndex = lines.findIndexOf { line -> line.startsWith("Run: clientTwo")}
        def thirdRunIndex = lines.findIndexOf { line -> line.startsWith("Run: clientThree")}
        def endIndex = lines.findIndexOf { line -> line.startsWith("BUILD SUCCESSFUL")}

        def indexes = [firstRunIndex + 1, secondRunIndex + 1, thirdRunIndex + 1, endIndex]
        indexes.sort()

        def firstSection = lines[indexes[0]..indexes[1] - 2]
        def secondSection = lines[indexes[1]..indexes[2] - 2]
        def thirdSection = lines[indexes[2]..indexes[3] - 2]

        //Check if all the runs are the same
        firstSection == secondSection
        secondSection == thirdSection
        thirdSection == firstSection
    }

    def "runs use a working directory named after them by default"() {
        given:
        def project = create("runs_have_configurable_working_directories_with_default", {
            it.property('neogradle.subsystems.conventions.runs.enabled', 'false')
            it.build("""
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'net.neoforged:neoforge:+'
            }
            
            runs {
                aClient {
                    runType 'client'
                }
            
                bClient {
                    runType 'client'
                }
                
                cClient {
                    runType 'client'
                    
                    workingDirectory project.file("clientThree")                
                }
            }
            
            """)
            it.withToolchains()
            it.withGlobalCacheDirectory(tempDir)
        })

        when:
        def run = project.run {
            it.tasks(':runs')
            it.stacktrace()
        }

        then:
        def lines = run.getOutput().split("\n");
        def firstRunIndex = lines.findIndexOf { line -> line.startsWith("Run: aClient")}
        def secondRunIndex = lines.findIndexOf { line -> line.startsWith("Run: bClient")}
        def thirdRunIndex = lines.findIndexOf { line -> line.startsWith("Run: cClient")}
        def endIndex = lines.findIndexOf { line -> line.startsWith("BUILD SUCCESSFUL")}

        def indexes = [firstRunIndex + 1, secondRunIndex + 1, thirdRunIndex + 1, endIndex]
        indexes.sort()

        def firstSection = lines[indexes[0]..indexes[1] - 2]
        def secondSection = lines[indexes[1]..indexes[2] - 2]
        def thirdSection = lines[indexes[2]..indexes[3] - 2]

        def prefix = "Working Directory:"

        firstSection.find { it.contains(prefix) }.toString().replace(prefix, "").trim().endsWith("runs_have_configurable_working_directories_with_default/runs/aClient".replace("/", File.separator))
        secondSection.find { it.contains(prefix) }.toString().replace(prefix, "").trim().endsWith("runs_have_configurable_working_directories_with_default/runs/bClient".replace("/", File.separator))
        thirdSection.find { it.contains(prefix) }.toString().replace(prefix, "").trim().endsWith("runs_have_configurable_working_directories_with_default/clientThree".replace("/", File.separator))
    }
}
