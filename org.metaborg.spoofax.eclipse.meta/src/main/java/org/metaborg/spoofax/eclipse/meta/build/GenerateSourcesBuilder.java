package org.metaborg.spoofax.eclipse.meta.build;

import java.util.Map;

import org.apache.commons.vfs2.FileObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.metaborg.spoofax.eclipse.meta.SpoofaxMetaPlugin;
import org.metaborg.spoofax.eclipse.resource.IEclipseResourceService;
import org.metaborg.spoofax.eclipse.util.StatusUtils;
import org.metaborg.spoofax.meta.core.MetaBuildInput;
import org.metaborg.spoofax.meta.core.SpoofaxMetaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;

public class GenerateSourcesBuilder extends IncrementalProjectBuilder {
    public static final String id = SpoofaxMetaPlugin.id + ".builder.generatesources";

    private static final Logger logger = LoggerFactory.getLogger(GenerateSourcesBuilder.class);

    private final IEclipseResourceService resourceService;

    private final SpoofaxMetaBuilder builder;
    private final MetaBuildInputGenerator inputGenerator;


    public GenerateSourcesBuilder() {
        final Injector injector = SpoofaxMetaPlugin.injector();
        this.resourceService = injector.getInstance(IEclipseResourceService.class);
        this.builder = injector.getInstance(SpoofaxMetaBuilder.class);
        this.inputGenerator = injector.getInstance(MetaBuildInputGenerator.class);
    }


    @Override protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
        throws CoreException {
        try {
            if(kind != AUTO_BUILD) {
                build(getProject(), monitor);
            }
        } catch(Exception e) {
            logger.error("Cannot build language project", e);
        } finally {
            // Always forget last build state to force a full build next time.
            forgetLastBuiltState();
        }
        return null;
    }

    private void build(IProject project, final IProgressMonitor monitor) throws Exception {
        final FileObject location = resourceService.resolve(project);
        final MetaBuildInput input = inputGenerator.buildInput(location);
        if(input == null) {
            return;
        }

        final IWorkspaceRunnable runnable = new IWorkspaceRunnable() {
            @Override public void run(IProgressMonitor workspaceMonitor) throws CoreException {
                try {
                    builder.generateSources(input);
                } catch(Exception e) {
                    throw new CoreException(StatusUtils.error(e));
                }
            }
        };
        ResourcesPlugin.getWorkspace().run(runnable, project, IWorkspace.AVOID_UPDATE, monitor);
    }
}
