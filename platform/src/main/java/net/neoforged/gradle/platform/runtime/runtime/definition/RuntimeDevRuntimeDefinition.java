package net.neoforged.gradle.platform.runtime.runtime.definition;

import net.neoforged.gradle.common.runs.run.RunImpl;
import net.neoforged.gradle.common.runtime.definition.CommonRuntimeDefinition;
import net.neoforged.gradle.common.runtime.definition.IDelegatingRuntimeDefinition;
import net.neoforged.gradle.common.runtime.tasks.DownloadAssets;
import net.neoforged.gradle.common.runtime.tasks.ExtractNatives;
import net.neoforged.gradle.dsl.common.runtime.definition.Definition;
import net.neoforged.gradle.dsl.common.tasks.ArtifactProvider;
import net.neoforged.gradle.dsl.common.tasks.WithOutput;
import net.neoforged.gradle.neoform.runtime.definition.NeoFormRuntimeDefinition;
import net.neoforged.gradle.platform.runtime.runtime.specification.RuntimeDevRuntimeSpecification;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents a configured and registered runtime for forges runtime development environment.
 */
//TODO: Create DSL for runtime
public final class RuntimeDevRuntimeDefinition extends CommonRuntimeDefinition<RuntimeDevRuntimeSpecification> implements IDelegatingRuntimeDefinition<RuntimeDevRuntimeSpecification> {
    private final NeoFormRuntimeDefinition joinedNeoFormRuntimeDefinition;
    private final TaskProvider<? extends WithOutput> patchBase;

    public RuntimeDevRuntimeDefinition(@NotNull RuntimeDevRuntimeSpecification specification, NeoFormRuntimeDefinition joinedNeoFormRuntimeDefinition, TaskProvider<? extends ArtifactProvider> sourcesProvider, TaskProvider<? extends WithOutput> patchBase) {
        super(specification, joinedNeoFormRuntimeDefinition.getTasks(), sourcesProvider, joinedNeoFormRuntimeDefinition.getRawJarTask(), joinedNeoFormRuntimeDefinition.getGameArtifactProvidingTasks(), joinedNeoFormRuntimeDefinition.getMinecraftDependenciesConfiguration(), joinedNeoFormRuntimeDefinition::configureAssociatedTask, joinedNeoFormRuntimeDefinition.getVersionJson());
        this.joinedNeoFormRuntimeDefinition = joinedNeoFormRuntimeDefinition;
        this.patchBase = patchBase;
    }
    
    public NeoFormRuntimeDefinition getJoinedNeoFormRuntimeDefinition() {
        return joinedNeoFormRuntimeDefinition;
    }

    @Override
    public @NotNull TaskProvider<DownloadAssets> getAssets() {
        return joinedNeoFormRuntimeDefinition.getAssets();
    }

    @Override
    public @NotNull TaskProvider<ExtractNatives> getNatives() {
        return joinedNeoFormRuntimeDefinition.getNatives();
    }

    @Override
    public @NotNull Map<String, String> getMappingVersionData() {
        return joinedNeoFormRuntimeDefinition.getMappingVersionData();
    }

    @NotNull
    @Override
    public TaskProvider<? extends WithOutput> getListLibrariesTaskProvider() {
        return joinedNeoFormRuntimeDefinition.getListLibrariesTaskProvider();
    }

    @Override
    protected void buildRunInterpolationData(RunImpl run, MapProperty<String, String> interpolationData) {
        joinedNeoFormRuntimeDefinition.buildRunInterpolationData(run, interpolationData);
    }

    public TaskProvider<? extends WithOutput> getPatchBase() {
        return patchBase;
    }

    @Override
    public Definition<?> getDelegate() {
        return joinedNeoFormRuntimeDefinition;
    }
}
