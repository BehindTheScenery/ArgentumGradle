package net.neoforged.gradle.neoform.runtime.tasks;

import net.neoforged.gradle.common.runtime.tasks.RuntimeArgumentsImpl;
import net.neoforged.gradle.common.runtime.tasks.RuntimeMultiArgumentsImpl;
import net.neoforged.gradle.common.services.caching.CachedExecutionService;
import net.neoforged.gradle.common.services.caching.jobs.ICacheableJob;
import net.neoforged.gradle.dsl.common.runtime.tasks.Runtime;
import net.neoforged.gradle.dsl.common.runtime.tasks.RuntimeArguments;
import net.neoforged.gradle.dsl.common.runtime.tasks.RuntimeMultiArguments;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.work.InputChanges;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Objects;

@CacheableTask
public abstract class RecompileSourceJar extends JavaCompile implements Runtime {

    private final Property<JavaLanguageVersion> javaVersion;
    private final Provider<JavaToolchainService> javaToolchainService;
    private final RuntimeArguments arguments;
    private final RuntimeMultiArguments multiArguments;

    public RecompileSourceJar() {
        super();

        arguments = getObjectFactory().newInstance(RuntimeArgumentsImpl.class, getProviderFactory());
        multiArguments = getObjectFactory().newInstance(RuntimeMultiArgumentsImpl.class, getProviderFactory());

        this.javaVersion = getProject().getObjects().property(JavaLanguageVersion.class);

        final JavaToolchainService service = getProject().getExtensions().getByType(JavaToolchainService.class);
        this.javaToolchainService = getProviderFactory().provider(() -> service);

        getStepsDirectory().convention(getRuntimeDirectory().dir("steps"));

        //And configure output default locations.
        getOutputDirectory().convention(getStepsDirectory().flatMap(d -> getStepName().map(d::dir)));
        getOutputFileName().convention(getArguments().getOrDefault("outputExtension", getProviderFactory().provider(() -> "jar")).map(extension -> String.format("output.%s", extension)));

        getJavaVersion().convention(getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion());
        getJavaLauncher().convention(getJavaToolChain().flatMap(toolChain -> {
            if (!getJavaVersion().isPresent()) {
                return toolChain.launcherFor(javaToolchainSpec -> javaToolchainSpec.getLanguageVersion().set(JavaLanguageVersion.of(Objects.requireNonNull(Jvm.current().getJavaVersion()).getMajorVersion())));
            }

            return toolChain.launcherFor(spec -> spec.getLanguageVersion().set(getJavaVersion()));
        }));

        setDescription("Recompiles an already existing decompiled java jar.");

        setClasspath(getCompileClasspath());
        getOptions().setAnnotationProcessorPath(getAnnotationProcessorPath());

        getOptions().getGeneratedSourceOutputDirectory().convention(getOutputDirectory().map(directory -> directory.dir("generated/sources/annotationProcessor")));
        getOptions().getHeaderOutputDirectory().convention(getOutputDirectory().map(directory -> directory.dir("generated/sources/headers")));

        final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType(JavaPluginExtension.class);

        getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
        getJavaCompiler().convention(getJavaVersion().flatMap(javaVersion -> service.compilerFor(javaToolchainSpec -> javaToolchainSpec.getLanguageVersion().set(javaVersion))));

        getDestinationDirectory().set(getOutputDirectory().map(directory -> directory.dir("classes")));

        getOptions().setWarnings(false);
        getOptions().setVerbose(false);
        getOptions().setDeprecation(false);
        getOptions().setFork(true);
        getOptions().setIncremental(true);
        getOptions().getIncrementalAfterFailure().set(true);
        getOptions().setSourcepath(getProject().files(getAdditionalInputFileRoot()));
    }

    @Override
    @Nested
    public RuntimeArguments getArguments() {
        return arguments;
    }

    @Override
    @Nested
    public RuntimeMultiArguments getMultiArguments() {
        return multiArguments;
    }

    @Override
    public String getGroup() {
        final String name = getRuntimeName().getOrElse("unknown");
        return String.format("NeoGradle/Runtime/%s", name);
    }

    @Internal
    public final Provider<JavaToolchainService> getJavaToolChain() {
        return javaToolchainService;
    }

    @Nested
    @Optional
    @Override
    public Property<JavaLanguageVersion> getJavaVersion() {
        return this.javaVersion;
    }

    @Internal
    public abstract ConfigurableFileCollection getAnnotationProcessorPath();

    @Internal
    public abstract ConfigurableFileCollection getCompileClasspath();

    @Inject
    @Override
    public abstract ObjectFactory getObjectFactory();

    @Inject
    @Override
    public abstract ProviderFactory getProviderFactory();

    @ServiceReference(CachedExecutionService.NAME)
    public abstract Property<CachedExecutionService> getCacheService();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getAdditionalInputFileRoot();

    @Override
    protected void compile(InputChanges inputs) {
        try {
            getCacheService().get()
                    .cached(
                            this,
                            ICacheableJob.Default.directory(
                                    getDestinationDirectory(),
                                    () -> {
                                        doCachedCompile(inputs);
                                    }
                            )
                    ).execute();
        } catch (IOException e) {
            throw new GradleException("Failed to recompile!", e);
        }
    }

    private void doCachedCompile(InputChanges inputs) {
        super.compile(inputs);
        final FileTree output = this.getDestinationDirectory().getAsFileTree();

        output.visit(details -> {
            if (details.isDirectory())
                return;

            final String relativePath = details.getRelativePath().getPathString();
            final String sourceFilePath;
            if (!relativePath.contains("$")) {
                sourceFilePath = relativePath.substring(0, relativePath.length() - ".class".length()) + ".java";
            } else {
                sourceFilePath = relativePath.substring(0, relativePath.indexOf('$')) + ".java";
            }

            if (!getAdditionalInputFileRoot().getAsFileTree().matching(pattern -> pattern.include(sourceFilePath))
                    .isEmpty()) {
                getLogger().debug("Deleting additional input file.");
                details.getFile().delete();
            }
        });
    }
}
